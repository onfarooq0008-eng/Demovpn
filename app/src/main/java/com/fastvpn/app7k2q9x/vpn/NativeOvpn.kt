package com.fastvpn.app7k2q9x.vpn

/** JNI entry points into libfastvpn_ovpn3.so (OpenVPN 3 core). `loaded` is false if the engine wasn't built in. */
object NativeOvpn {
    val loaded: Boolean = try { System.loadLibrary("fastvpn_ovpn3"); true } catch (e: UnsatisfiedLinkError) { false }

    @JvmStatic external fun start(config: String, gen: Int)
    @JvmStatic external fun stop()
    @JvmStatic external fun reconnect()
    @JvmStatic external fun bytes(): LongArray
}
