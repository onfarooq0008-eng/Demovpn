package com.fastvpnn.app.vpn

import android.content.Context

/** Bytes moved through the tunnel so far (protocol independent, used by the stats row). */
data class TrafficStats(val rxBytes: Long, val txBytes: Long)

/**
 * The one seam between the app and an embedded OpenVPN client (ics-openvpn). Everything else in
 * the app (server list, failover, notification, state sync) only talks to this interface through
 * [VpnTunnelManager], so wiring the real engine means implementing this ONE class.
 */
interface OpenVpnEngine {
    /** False while no OpenVPN client library is linked into the build. */
    val isAvailable: Boolean

    /** Starts the tunnel with an already sanitized .ovpn text. [dnsOverride] is null = keep the server's DNS. */
    suspend fun connect(
        config: String,
        label: String,
        excludedPackages: Set<String>,
        dnsOverride: String?
    ): Result<Unit>

    suspend fun disconnect(): Result<Unit>

    /** True while the engine's VPN is really up. Must be safe to call from any thread. */
    fun isRunning(): Boolean

    fun traffic(): TrafficStats?

    /** Called when the engine's tunnel goes down by itself (server dropped, system revoked VPN). */
    var onTunnelDown: (() -> Unit)?
}

/** Fallback / test double: every OpenVPN connect fails with a clear message. */
class UnavailableOpenVpnEngine : OpenVpnEngine {
    override val isAvailable = false
    override var onTunnelDown: (() -> Unit)? = null
    override suspend fun connect(
        config: String, label: String, excludedPackages: Set<String>, dnsOverride: String?
    ): Result<Unit> = Result.failure(
        IllegalStateException("OpenVPN engine is not linked into this build yet")
    )
    override suspend fun disconnect(): Result<Unit> = Result.success(Unit)
    override fun isRunning() = false
    override fun traffic(): TrafficStats? = null
}

/** The single place that decides which engine the app uses (ics-openvpn based, see IcsOpenVpnEngine). */
object OpenVpnEngineProvider {
    fun create(context: Context): OpenVpnEngine = IcsOpenVpnEngine(context)
}
