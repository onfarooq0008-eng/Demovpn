package com.fastvpn.app7k2q9x.vpn

import android.content.Intent
import com.fastvpn.app7k2q9x.data.Server
import kotlinx.coroutines.flow.MutableStateFlow

enum class VpnState {
    DISCONNECTED, CONNECTING, AUTHENTICATING, WAITING_FOR_TUNNEL, CONNECTED, DISCONNECTING,
    RECONNECTING, FAILED, NO_INTERNET, PERMISSION_REQUIRED
}

data class VpnStats(val downBytes: Long = 0, val upBytes: Long = 0, val downRate: Long = 0, val upRate: Long = 0)
data class SessionInfo(val server: Server? = null, val assignedIp: String? = null, val transport: String? = null, val connectedAt: Long = 0)

/** Single source of truth observed by every screen. Written only by VpnController and the engine. */
object VpnSession {
    val state = MutableStateFlow(VpnState.DISCONNECTED)
    val error = MutableStateFlow<String?>(null)
    val progress = MutableStateFlow<String?>(null)
    val stats = MutableStateFlow(VpnStats())
    val info = MutableStateFlow(SessionInfo())
    val permissionIntent = MutableStateFlow<Intent?>(null)

    val isBusy get() = state.value in setOf(
        VpnState.CONNECTING, VpnState.AUTHENTICATING, VpnState.WAITING_FOR_TUNNEL, VpnState.DISCONNECTING, VpnState.RECONNECTING
    )
}
