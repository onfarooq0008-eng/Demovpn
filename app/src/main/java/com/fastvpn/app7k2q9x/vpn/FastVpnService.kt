package com.fastvpn.app7k2q9x.vpn

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.fastvpn.app7k2q9x.MainActivity
import com.fastvpn.app7k2q9x.util.DiagLog
import kotlinx.coroutines.*

/**
 * The one and only VpnService. It builds the single TUN from the core's TunBuilder calls, runs the
 * foreground notification (driven by the real VpnSession state), reports byte counters, and tells
 * the core when the underlying network changes.
 */
class FastVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.fastvpn.app7k2q9x.START"
        const val ACTION_STOP = "com.fastvpn.app7k2q9x.STOP"
        private const val CHANNEL = "vpn"
        private const val NOTIF_ID = 1
        @Volatile var instance: FastVpnService? = null
        @Volatile var pendingConfig: String? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var builder: Builder? = null
    private var v4Default = false
    private var v6Default = false
    private var dnsCount = 0
    private val cm by lazy { getSystemService(ConnectivityManager::class.java) }
    private var net: Network? = null
    private var needReconnect = false

    // ---------- lifecycle ----------
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { VpnController.disconnect(); return START_NOT_STICKY }   // notification "Disconnect"
            ACTION_START -> {
                instance = this
                createChannel()
                ServiceCompat.startForeground(this, NOTIF_ID, notification(VpnSession.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                watchState(); watchNetwork(); pollStats()
                begin(intent.getIntExtra("gen", EngineImpl.gen))
            }
        }
        return START_NOT_STICKY       // a killed process loses the tunnel; state then correctly reads Disconnected
    }

    fun begin(gen: Int) {
        val cfg = pendingConfig ?: run { release(); return }
        pendingConfig = null
        NativeOvpn.start(cfg, gen)
    }

    /** Stops the foreground service after the engine has been stopped. */
    fun release() {
        runCatching { cm.unregisterNetworkCallback(netCb) }
        scope.coroutineContext.cancelChildren()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { VpnController.disconnect(); super.onRevoke() }

    override fun onDestroy() {
        NativeOvpn.stop()
        runCatching { cm.unregisterNetworkCallback(netCb) }
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    // ---------- TunBuilder (called from native threads) ----------
    fun tunNew(): Boolean {
        builder = Builder().setSession("FastVPN").setBlocking(false)
            .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        v4Default = false; v6Default = false; dnsCount = 0
        return true
    }
    fun tunAddAddress(a: String, p: Int, v6: Boolean) = op { builder!!.addAddress(a, p) }
    fun tunReroute(v4: Boolean, v6: Boolean) = op {
        if (v4) { builder!!.addRoute("0.0.0.0", 0); v4Default = true }
        if (v6) { builder!!.addRoute("::", 0); v6Default = true }
    }
    fun tunAddRoute(a: String, p: Int) = op { builder!!.addRoute(a, p) }
    fun tunAddDns(a: String) = op { builder!!.addDnsServer(a); dnsCount++ }
    fun tunAddSearch(d: String) = op { builder!!.addSearchDomain(d) }
    fun tunSetMtu(m: Int) = op { builder!!.setMtu(m) }

    fun tunEstablish(): Int {
        val b = builder ?: return -1
        // IPv6 leak guard: if all IPv4 goes through the tunnel but the profile doesn't carry IPv6,
        // route ::/0 into the tunnel so IPv6 is dropped there instead of bypassing the VPN.
        if (v4Default && !v6Default) runCatching { b.addRoute("::", 0) }
        if (dnsCount == 0) DiagLog.d("Tunnel", "Profile pushed no DNS server; DNS may use the underlying network")
        val pfd = try { b.establish() } catch (e: Exception) { null } ?: return -1
        net?.let { setUnderlyingNetworks(arrayOf(it)) }
        return pfd.detachFd()          // the core owns and closes the fd
    }
    fun tunTeardown() { builder = null }

    private inline fun op(f: () -> Unit): Boolean = try { f(); true } catch (e: Exception) { DiagLog.d("Tunnel", "builder error ${e.javaClass.simpleName}"); false }

    // ---------- network changes ----------
    private val netCb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) {
            if (net == null) {
                net = n; setUnderlyingNetworks(arrayOf(n))
                if (needReconnect) { needReconnect = false; DiagLog.d("Net", "network back, reconnecting"); NativeOvpn.reconnect() }
            }
        }
        override fun onLost(n: Network) {
            if (n != net) return
            net = null; setUnderlyingNetworks(null)
            val alt = cm.allNetworks.firstOrNull {
                it != n && cm.getNetworkCapabilities(it)?.let { c ->
                    c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                } == true
            }
            if (alt != null) { net = alt; setUnderlyingNetworks(arrayOf(alt)); DiagLog.d("Net", "switched network, reconnecting"); NativeOvpn.reconnect() }
            else {
                needReconnect = true
                if (VpnSession.state.value == VpnState.CONNECTED || VpnSession.state.value == VpnState.RECONNECTING) VpnSession.state.value = VpnState.NO_INTERNET
            }
        }
    }
    private fun watchNetwork() {
        val req = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        runCatching { cm.registerNetworkCallback(req, netCb) }
    }

    // ---------- notification + stats ----------
    private fun createChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "VPN status", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(s: VpnState): Notification {
        val text = when (s) {
            VpnState.CONNECTED -> "Connected" + (VpnSession.info.value.server?.let { " · ${it.country}" } ?: "")
            VpnState.DISCONNECTED, VpnState.FAILED -> "Not connected"
            VpnState.NO_INTERNET -> "Waiting for network"
            else -> "Connecting…"
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, FastVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("FastVPN").setContentText(text)
            .setOngoing(true).setContentIntent(open).addAction(0, "Disconnect", stop).build()
    }

    private fun watchState() = scope.launch {
        VpnSession.state.collect { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(it)) }
    }

    private fun pollStats() = scope.launch {
        var lastIn = 0L; var lastOut = 0L; var t = System.currentTimeMillis()
        while (isActive) {
            delay(2000)
            if (VpnSession.state.value != VpnState.CONNECTED) { lastIn = 0; lastOut = 0; t = System.currentTimeMillis(); continue }
            val b = NativeOvpn.bytes(); val now = System.currentTimeMillis()
            val dt = ((now - t) / 1000.0).coerceAtLeast(0.5)
            VpnSession.stats.value = VpnStats(b[0], b[1], ((b[0] - lastIn).coerceAtLeast(0) / dt).toLong(), ((b[1] - lastOut).coerceAtLeast(0) / dt).toLong())
            lastIn = b[0]; lastOut = b[1]; t = now
        }
    }
}
