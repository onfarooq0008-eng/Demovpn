package com.fastvpn.app7k2q9x

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastvpn.app7k2q9x.data.*
import com.fastvpn.app7k2q9x.vpn.VpnController
import com.fastvpn.app7k2q9x.vpn.VpnSession
import com.fastvpn.app7k2q9x.vpn.VpnState
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    init { refresh(false) }

    fun refresh(force: Boolean) { viewModelScope.launch { ServerRepository.refresh(force) } }

    fun selectedServer(): Server? = Settings.selected.value.let { n -> ServerRepository.state.value.servers.firstOrNull { it.name == n } }

    /** Connect / Disconnect / Retry button. */
    fun onMainButton() {
        when (VpnSession.state.value) {
            VpnState.DISCONNECTED, VpnState.FAILED, VpnState.NO_INTERNET, VpnState.PERMISSION_REQUIRED -> start()
            VpnState.CONNECTED, VpnState.RECONNECTING -> VpnController.disconnect()
            else -> {}   // busy: taps are ignored
        }
    }

    fun start() { selectedServer()?.let { VpnController.connect(it) } ?: VpnController.connectFastest() }
    fun cancel() = VpnController.disconnect()

    fun select(server: Server) {
        Settings.setSelected(server.name)
        if (VpnSession.state.value == VpnState.CONNECTED) VpnController.connect(server)   // switch server
    }

    fun selectFastest() {
        Settings.setSelected("")
        if (VpnSession.state.value == VpnState.CONNECTED) VpnController.connectFastest()
    }
}
