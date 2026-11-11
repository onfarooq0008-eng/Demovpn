package com.fastvpn.app7k2q9x.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.fastvpn.app7k2q9x.data.Server
import com.fastvpn.app7k2q9x.util.DiagLog

/**
 * OpenVPN engine = the OpenVPN 3 core (native) + FastVpnService. Only real core events change VpnSession.
 * `gen` identifies the current start so late callbacks from a previous tunnel are ignored.
 */
object EngineImpl : VpnEngine {
    override val available: Boolean get() = NativeOvpn.loaded
    @Volatile var gen = 0
        private set

    override fun init(context: Context) {}

    override fun start(context: Context, server: Server, config: String) {
        gen++
        VpnSession.stats.value = VpnStats()
        FastVpnService.pendingConfig = config                       // in memory only, cleared once consumed
        val svc = FastVpnService.instance
        if (svc != null) svc.begin(gen)                              // service already alive (reconnect/switch)
        else ContextCompat.startForegroundService(
            context, Intent(context, FastVpnService::class.java).setAction(FastVpnService.ACTION_START).putExtra("gen", gen)
        )
    }

    override fun stop(context: Context) { NativeOvpn.stop() }

    override fun release(context: Context) { FastVpnService.instance?.release() }

    // ---- core -> VpnSession ----
    fun onEvent(name: String, info: String, error: Boolean, fatal: Boolean) {
        DiagLog.d("Core", "event=$name${if (error) " ERROR" else ""}${if (fatal) " FATAL" else ""}")
        val cur = VpnSession.state.value
        when {
            name == "AUTH_FAILED" -> fail("Authentication failed")
            name == "TUN_SETUP_FAILED" -> fail("Could not set up the VPN interface")
            name == "CONNECTION_TIMEOUT" -> fail("Connection timed out")
            error && fatal -> fail("Connection error: $name")
            name == "CONNECTED" -> {
                VpnSession.info.value = VpnSession.info.value.copy(connectedAt = VpnSession.info.value.connectedAt.takeIf { it > 0 } ?: System.currentTimeMillis())
                VpnSession.state.value = VpnState.CONNECTED
            }
            name == "RECONNECTING" -> if (cur == VpnState.CONNECTED || cur == VpnState.NO_INTERNET) VpnSession.state.value = VpnState.RECONNECTING
            name == "AUTH" -> if (cur != VpnState.RECONNECTING) VpnSession.state.value = VpnState.AUTHENTICATING
            name in setOf("GET_CONFIG", "ASSIGN_IP", "ADD_ROUTES") -> if (cur != VpnState.RECONNECTING) VpnSession.state.value = VpnState.WAITING_FOR_TUNNEL
            name in setOf("CONNECTING", "WAIT", "RESOLVE", "TCP_CONNECT") -> if (cur == VpnState.DISCONNECTED || cur == VpnState.FAILED) VpnSession.state.value = VpnState.CONNECTING
        }
    }

    fun onConnected(vpnIp4: String, proto: String) {
        val transport = if (proto.uppercase().startsWith("TCP")) "TCP" else if (proto.isNotEmpty()) "UDP" else null
        VpnSession.info.value = VpnSession.info.value.copy(assignedIp = vpnIp4.ifEmpty { null }, transport = transport ?: VpnSession.info.value.transport)
    }

    fun onFinished(err: String) {
        DiagLog.d("Core", "finished ${if (err.isEmpty()) "ok" else "error"}")
        val cur = VpnSession.state.value
        if (err.isNotEmpty() && cur != VpnState.DISCONNECTING && cur != VpnState.FAILED) {
            VpnSession.error.value = VpnSession.error.value ?: friendly(err)
            VpnSession.state.value = VpnState.FAILED
        } else if (cur != VpnState.FAILED) VpnSession.state.value = VpnState.DISCONNECTED
    }

    private fun fail(msg: String) { VpnSession.error.value = msg; VpnSession.state.value = VpnState.FAILED }

    private fun friendly(err: String) = when {
        err.contains("AUTH", true) -> "Authentication failed"
        err.contains("CERT", true) || err.contains("TLS", true) -> "TLS negotiation failed"
        err.contains("TIMEOUT", true) -> "Connection timed out"
        else -> "Connection failed"
    }
}
