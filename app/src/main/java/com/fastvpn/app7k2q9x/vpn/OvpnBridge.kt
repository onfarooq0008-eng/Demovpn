package com.fastvpn.app7k2q9x.vpn

import androidx.annotation.Keep
import com.fastvpn.app7k2q9x.util.DiagLog

/** Static callbacks invoked from native code (names/signatures are referenced in jni_client.cpp). */
@Keep
object OvpnBridge {
    private val svc get() = FastVpnService.instance

    @JvmStatic fun tunNew(): Boolean = svc?.tunNew() ?: false
    @JvmStatic fun tunAddAddress(addr: String, prefix: Int, ipv6: Boolean): Boolean = svc?.tunAddAddress(addr, prefix, ipv6) ?: false
    @JvmStatic fun tunReroute(v4: Boolean, v6: Boolean): Boolean = svc?.tunReroute(v4, v6) ?: false
    @JvmStatic fun tunAddRoute(addr: String, prefix: Int, ipv6: Boolean): Boolean = svc?.tunAddRoute(addr, prefix) ?: false
    @JvmStatic fun tunAddDns(addr: String): Boolean = svc?.tunAddDns(addr) ?: false
    @JvmStatic fun tunAddSearch(domain: String): Boolean = svc?.tunAddSearch(domain) ?: false
    @JvmStatic fun tunSetMtu(mtu: Int): Boolean = svc?.tunSetMtu(mtu) ?: false
    @JvmStatic fun tunSetSession(name: String): Boolean = true
    @JvmStatic fun tunEstablish(): Int = svc?.tunEstablish() ?: -1
    @JvmStatic fun tunTeardown() { svc?.tunTeardown() }
    @JvmStatic fun protect(fd: Int): Boolean = svc?.protect(fd) ?: false

    @JvmStatic fun onEvent(gen: Int, name: String, info: String, error: Boolean, fatal: Boolean) {
        if (gen == EngineImpl.gen) EngineImpl.onEvent(name, info, error, fatal)
    }
    @JvmStatic fun onConnected(gen: Int, vpnIp4: String, proto: String) {
        if (gen == EngineImpl.gen) EngineImpl.onConnected(vpnIp4, proto)
    }
    @JvmStatic fun onFinished(gen: Int, err: String) {
        if (gen == EngineImpl.gen) EngineImpl.onFinished(err)
    }
    @JvmStatic fun log(msg: String) { DiagLog.d("Core", msg) }
}
