package com.fastvpnn.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fastvpnn.app.data.OvpnConfigPrep
import com.fastvpnn.app.ui.MainActivity
import de.blinkt.openvpn.OpenVpnApi
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.OpenVPNThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [OpenVpnEngine] on top of the vendored ics-openvpn library (/vpnLib).
 *
 * The library starts OpenVPNService (a foreground VpnService) and reports over LocalBroadcastManager
 * ("connectionState": `state`, `byteIn`, `byteOut`). This class turns that into a suspending connect()
 * that returns once the tunnel is CONNECTED (or failed / timed out), and tracks "is the tunnel up".
 *
 * The VPN permission dialog (VpnService.prepare) is handled by MainActivity before any connect.
 */
class IcsOpenVpnEngine(context: Context) : OpenVpnEngine {

    private companion object {
        const val TAG = "FastVPN-OpenVPN"
        const val ACTION_STATE = "connectionState"
        const val CONNECT_TIMEOUT_MS = 30_000L
        // Terminal states are ignored very briefly after start: a late broadcast of the PREVIOUS session
        // shutting down must not fail the new attempt.
        const val FAIL_GRACE_MS = 1_500L
        const val DISCONNECT_WAIT_MS = 4_000L
        val FAILED = setOf("NOPROCESS", "EXITING", "DISCONNECTED", "AUTH_FAILED")
    }

    private val appContext = context.applicationContext

    @Volatile private var lastState = "NOPROCESS"
    @Volatile private var rx = 0L
    @Volatile private var tx = 0L
    @Volatile private var active = false

    override val isAvailable = true
    override var onTunnelDown: (() -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            intent.getStringExtra("state")?.let { onState(it) }
            intent.getStringExtra("byteIn")?.trim()?.toLongOrNull()?.let { rx = it }
            intent.getStringExtra("byteOut")?.trim()?.toLongOrNull()?.let { tx = it }
        }
    }

    init {
        LocalBroadcastManager.getInstance(appContext).registerReceiver(receiver, IntentFilter(ACTION_STATE))
    }

    private fun onState(state: String) {
        lastState = state
        // A tunnel that was up and now reports a terminal state went down by itself (server dropped,
        // notification Disconnect, system revoked the VPN). Transient states (RECONNECTING, NONETWORK...) are not "down".
        if (active && state in FAILED) {
            Log.d(TAG, "tunnel went down: $state")
            active = false
            onTunnelDown?.invoke()
        }
    }

    override suspend fun connect(
        config: String,
        label: String,
        excludedPackages: Set<String>,
        dnsOverride: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            active = false
            rx = 0L
            tx = 0L
            lastState = "STARTING"
            OpenVPNService.setNotificationActivityClass(MainActivity::class.java)
            // VPNGate-style relays use no password, but the library wants non-empty credentials when the
            // profile contains `auth-user-pass`; "vpn"/"vpn" is what those relays accept.
            OpenVpnApi.startVpn(
                appContext,
                OvpnConfigPrep.prepare(config, dnsOverride),
                label,
                "vpn",
                "vpn",
                excludedPackages.toList()
            )
            awaitConnected()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "connect failed", e)
            stopQuietly()
            Result.failure(e)
        }
    }

    private suspend fun awaitConnected(): Result<Unit> {
        val startedAt = SystemClock.elapsedRealtime()
        while (true) {
            val state = lastState
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (state == "CONNECTED") {
                active = true
                return Result.success(Unit)
            }
            if (elapsed > FAIL_GRACE_MS && state in FAILED) {
                stopQuietly()
                val reason = if (state == "AUTH_FAILED") "authentication failed" else "server unreachable ($state)"
                return Result.failure(IOException("OpenVPN: $reason"))
            }
            if (elapsed > CONNECT_TIMEOUT_MS) {
                stopQuietly()
                return Result.failure(IOException("OpenVPN: connection timed out"))
            }
            delay(200)
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Mark down FIRST so the shutdown broadcast is not reported as the tunnel dropping by itself.
            active = false
            OpenVPNThread.stop()
            val startedAt = SystemClock.elapsedRealtime()
            while (lastState !in FAILED && SystemClock.elapsedRealtime() - startedAt < DISCONNECT_WAIT_MS) {
                delay(100)
            }
            OpenVPNService.setDefaultStatus()
            lastState = "NOPROCESS"
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "disconnect failed", e)
            Result.failure(e)
        }
    }

    private fun stopQuietly() {
        active = false
        runCatching { OpenVPNThread.stop() }
    }

    override fun isRunning(): Boolean = active

    override fun traffic(): TrafficStats? = if (active) TrafficStats(rx, tx) else null
}
