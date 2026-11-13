package com.fastvpnn.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OvpnServerClientTest {
    private val list = "https://raw.githubusercontent.com/o/r/refs/heads/main/servers.json"
    private val ok = "https://raw.githubusercontent.com/o/r/main/servers/KR-1-2-3-4.ovpn"

    private fun json(vararg entries: String) = """{"updated":"x","servers":[${entries.joinToString(",")}]}"""
    private fun entry(name: String, ip: String = "1.2.3.4", country: String = "Korea Republic of",
                      ping: String = "29", url: String = ok) =
        """{"name":"$name","source":"vpngate","ip":"$ip","country":"$country","speed_mbps":838.4,"ping_ms":"$ping","url":"$url"}"""

    @Test fun parsesEntryIntoOpenVpnServer() {
        val s = OvpnServerClient.parseServers(json(entry("KR-1-2-3-4")), list).single()
        assertEquals("ovpn-KR-1-2-3-4", s.id)
        assertEquals("South Korea #1", s.name)
        assertEquals("KR", s.countryCode)
        assertEquals("1.2.3.4", s.endpointHost)
        assertEquals(29, s.pingMs)
        assertTrue(s.isOpenVpn)
        assertEquals(ok, s.ovpnUrl)
    }

    @Test fun numbersServersPerCountry() {
        val names = OvpnServerClient.parseServers(
            json(entry("JP-1", country = "Japan"), entry("KR-1"), entry("JP-2", country = "Japan")), list
        ).map { it.name }
        assertEquals(listOf("Japan #1", "South Korea #1", "Japan #2"), names)
    }

    @Test fun skipsEntriesWithForeignHostOrHttp() {
        val res = OvpnServerClient.parseServers(
            json(
                entry("A-1", url = "https://evil.example.com/a.ovpn"),
                entry("B-1", url = "http://raw.githubusercontent.com/o/r/main/b.ovpn"),
                entry("C-1")
            ), list
        )
        assertEquals(listOf("ovpn-C-1"), res.map { it.id })
    }

    @Test fun badPingBecomesUntested() {
        assertEquals(-1, OvpnServerClient.parseServers(json(entry("KR-1", ping = "n/a")), list).single().pingMs)
    }

    @Test fun missingServersArrayIsEmpty() =
        assertTrue(OvpnServerClient.parseServers("""{"updated":"x"}""", list).isEmpty())

    @Test fun urlHostCheck() {
        assertTrue(OvpnServerClient.isAllowedUrl(ok, list))
        assertFalse(OvpnServerClient.isAllowedUrl("https://other.host/x.ovpn", list))
        assertFalse(OvpnServerClient.isAllowedUrl("not a url", list))
    }
}
