package com.fastvpnn.app.data

/**
 * Combines the WireGuard list (own backend) and the OpenVPN list (public repo). Either source may
 * fail on its own: the other one is still shown, and the failed protocol falls back to whatever was
 * cached for it. Only when BOTH fail does the caller see an error.
 */
object ServerMerge {
    fun merge(
        wireguard: Result<List<Server>>,
        openVpn: Result<List<Server>>,
        cached: List<Server>?
    ): List<Server> {
        if (wireguard.isFailure && openVpn.isFailure) {
            throw wireguard.exceptionOrNull() ?: java.io.IOException("No server source reachable")
        }
        val wg = wireguard.getOrNull() ?: cached.orEmpty().filter { !it.isOpenVpn }
        val ov = openVpn.getOrNull() ?: cached.orEmpty().filter { it.isOpenVpn }
        return wg + ov
    }
}
