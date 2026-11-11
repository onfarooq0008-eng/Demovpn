package com.fastvpn.app7k2q9x.vpn

import android.content.Context
import com.fastvpn.app7k2q9x.data.Server

/** Contract for the OpenVPN engine. Exactly one implementation (EngineImpl) is compiled per build. */
interface VpnEngine {
    /** false when the OpenVPN engine module is not part of this build. */
    val available: Boolean
    fun init(context: Context)
    /** Imports the (already validated) .ovpn text and starts the tunnel. Progress is reported through VpnSession. */
    fun start(context: Context, server: Server, config: String)
    /** Stops the tunnel. The service stays alive so a retry/switch can start without a new foreground-service launch. */
    fun stop(context: Context)
    /** Stops the foreground service once no further attempt is pending. */
    fun release(context: Context)
}
