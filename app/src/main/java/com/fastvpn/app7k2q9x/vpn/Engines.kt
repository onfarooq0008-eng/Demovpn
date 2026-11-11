package com.fastvpn.app7k2q9x.vpn

import android.content.Context
import com.fastvpn.app7k2q9x.data.Settings

/**
 * Two interchangeable OpenVPN engines: ics-openvpn (if ./ics-openvpn is present at build time) and the
 * OpenVPN 3 core (if ./third_party is present). Only one runs at a time; the one that started a tunnel stops it.
 */
object Engines {
    private val ovpn3: VpnEngine = EngineImpl
    private val ics: VpnEngine? = IcsEngineFactory.create()
    @Volatile private var started: VpnEngine? = null

    fun init(c: Context) { ovpn3.init(c); ics?.init(c) }

    /** Settings.engine: 0 = Auto (ics-openvpn first), 1 = ics-openvpn, 2 = OpenVPN 3 core. */
    val active: VpnEngine get() = when (Settings.engine.value) {
        1 -> ics ?: ovpn3
        2 -> ovpn3
        else -> if (ics?.available == true) ics else ovpn3
    }
    val available: Boolean get() = active.available
    fun forStart(): VpnEngine = active.also { started = it }
    fun forStop(): VpnEngine = started ?: active
    fun isActive(e: VpnEngine) = (started ?: active) === e
    val icsPresent get() = ics != null
    val ovpn3Present get() = ovpn3.available
}
