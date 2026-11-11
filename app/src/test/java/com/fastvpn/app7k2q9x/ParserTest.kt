package com.fastvpn.app7k2q9x

import com.fastvpn.app7k2q9x.data.OvpnValidator
import com.fastvpn.app7k2q9x.data.ServerParser
import org.junit.Assert.*
import org.junit.Test

class ParserTest {
    private fun entry(name: String, ip: String, url: String = "https://raw.githubusercontent.com/x/y/main/servers/$name.ovpn") =
        """{"name":"$name","source":"vpngate","ip":"$ip","country":"Japan","speed_mbps":100.5,"ping_ms":"11","connect_seconds":3.0,"url":"$url"}"""

    @Test fun parsesValidAndReadsUpdated() {
        val r = ServerParser.parse("""{"updated":"2026-11-10","servers":[${entry("JP-1-1-1-1", "1.1.1.1")}]}""")
        assertEquals(1, r.servers.size); assertEquals("2026-11-10", r.updated)
        assertEquals(11, r.servers[0].pingMs); assertEquals("JP", r.servers[0].countryCode)
    }
    @Test fun rejectsInvalidAndDuplicates() {
        val r = ServerParser.parse("""{"servers":[${entry("A", "1.1.1.1")},${entry("B", "1.1.1.1")},${entry("C", "999.1.1.1")},${entry("D", "2.2.2.2", "http://evil.com/a.ovpn")},{"name":""}]}""")
        assertEquals(1, r.servers.size); assertEquals(4, r.rejected)
    }
    @Test(expected = Exception::class) fun malformedThrows() { ServerParser.parse("not json") }

    private val good = "client\ndev tun\nproto udp\nremote 1.2.3.4 1194\n<ca>\nABC\n</ca>\n"
    @Test fun acceptsGoodConfig() { val v = OvpnValidator.validate(good); assertTrue(v.reason, v.ok); assertEquals("UDP", v.transport) }
    @Test fun detectsTcp() { assertEquals("TCP", OvpnValidator.validate(good.replace("udp", "tcp")).transport) }
    @Test fun rejectsScripts() { assertFalse(OvpnValidator.validate(good + "up /bin/evil.sh\n").ok) }
    @Test fun rejectsMissingRemoteAndEmpty() { assertFalse(OvpnValidator.validate("client\n<ca>\nA\n</ca>").ok); assertFalse(OvpnValidator.validate("").ok) }
    @Test fun rejectsExternalFiles() { assertFalse(OvpnValidator.validate("client\nremote 1.2.3.4 1194\nca ca.crt\n").ok) }
    @Test fun rejectsCredentialsRequired() { assertFalse(OvpnValidator.validate(good + "auth-user-pass\n").ok) }
}
