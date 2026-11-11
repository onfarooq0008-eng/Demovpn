package com.fastvpn.app7k2q9x.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.fastvpn.app7k2q9x.data.Server
import com.fastvpn.app7k2q9x.util.DiagLog
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import java.io.StringReader

object IcsEngineFactory { fun create(): VpnEngine? = IcsEngine }

/**
 * Engine backed by "OpenVPN for Android" (ics-openvpn). It owns its own VpnService, notification, routing,
 * DNS and network-change handling. This adapter only feeds its real callbacks into VpnSession.
 */
object IcsEngine : VpnEngine, VpnStatus.StateListener, VpnStatus.ByteCountListener {
    override val available = true
    private var lastTs = 0L

    override fun init(context: Context) {
        VpnStatus.addStateListener(this)
        VpnStatus.addByteCountListener(this)
    }

    override fun start(context: Context, server: Server, config: String) {
        VpnSession.stats.value = VpnStats()
        val parser = ConfigParser()
        parser.parseConfig(StringReader(config))
        val profile = parser.convertProfile()
        profile.mName = server.name
        ProfileManager.setTemporaryProfile(context, profile)       // not persisted
        VPNLaunchHelper.startOpenVpn(profile, context, "FastVPN", true)
    }

    override fun stop(context: Context) {
        ProfileManager.setConntectedVpnProfileDisconnected(context)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
                try { IOpenVPNServiceInternal.Stub.asInterface(b).stopVPN(false) } catch (e: Exception) { DiagLog.d("ICS", "stopVPN failed") }
                context.unbindService(this)
            }
            override fun onServiceDisconnected(n: ComponentName?) {}
        }
        context.bindService(Intent(context, OpenVPNService::class.java).apply { action = OpenVPNService.START_SERVICE }, conn, Context.BIND_AUTO_CREATE)
    }

    override fun release(context: Context) {}

    override fun updateState(state: String, logmessage: String?, localizedResId: Int, level: ConnectionStatus, intent: Intent?) {
        if (!Engines.isActive(this)) return
        DiagLog.d("ICS", "state=$state level=$level")
        val cur = VpnSession.state.value
        val next: VpnState? = when {
            level == ConnectionStatus.LEVEL_AUTH_FAILED || state == "AUTH_FAILED" -> { VpnSession.error.value = "Authentication failed"; VpnState.FAILED }
            level == ConnectionStatus.LEVEL_CONNECTED -> {
                logmessage?.split(',')?.getOrNull(1)?.takeIf { it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) }?.let {
                    VpnSession.info.value = VpnSession.info.value.copy(assignedIp = it)
                }
                VpnSession.info.value = VpnSession.info.value.copy(connectedAt = VpnSession.info.value.connectedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
                VpnState.CONNECTED
            }
            level == ConnectionStatus.LEVEL_NONETWORK || state == "NONETWORK" -> VpnState.NO_INTERNET
            state == "RECONNECTING" -> VpnState.RECONNECTING
            state == "AUTH" -> VpnState.AUTHENTICATING
            state in setOf("GET_CONFIG", "ASSIGN_IP", "ADD_ROUTES") -> VpnState.WAITING_FOR_TUNNEL
            state in setOf("CONNECTING", "WAIT", "RESOLVE", "TCP_CONNECT", "CONNECTRETRY", "VPN_GENERATE_CONFIG") -> VpnState.CONNECTING
            state == "NOPROCESS" || state == "EXITING" || level == ConnectionStatus.LEVEL_NOTCONNECTED ->
                if (cur in setOf(VpnState.CONNECTING, VpnState.DISCONNECTED, VpnState.FAILED, VpnState.PERMISSION_REQUIRED)) null else VpnState.DISCONNECTED
            else -> null
        }
        if (next != null && next != cur) VpnSession.state.value = next
    }

    override fun setConnectedVPN(uuid: String?) {}

    override fun updateByteCount(inBytes: Long, outBytes: Long, diffIn: Long, diffOut: Long) {
        if (!Engines.isActive(this)) return
        val now = System.currentTimeMillis()
        val dt = (now - lastTs).coerceAtLeast(500) / 1000.0
        lastTs = now
        VpnSession.stats.value = VpnStats(inBytes, outBytes, (diffIn / dt).toLong(), (diffOut / dt).toLong())
    }
}
