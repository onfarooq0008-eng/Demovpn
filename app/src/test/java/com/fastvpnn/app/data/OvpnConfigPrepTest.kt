package com.fastvpnn.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OvpnConfigPrepTest {
    @Test fun addsCipherFallbackForLegacyCipher() {
        val out = OvpnConfigPrep.prepare("client\ncipher AES-128-CBC\nremote 1.2.3.4 443\n", null)
        assertTrue(out.contains("data-ciphers-fallback AES-128-CBC"))
        assertTrue(out.contains("data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC"))
        assertTrue(out.contains("remote 1.2.3.4 443"))
    }

    @Test fun leavesConfigWithDataCiphersAlone() {
        val cfg = "client\ncipher AES-128-CBC\ndata-ciphers AES-128-GCM\n"
        assertFalse(OvpnConfigPrep.prepare(cfg, null).contains("data-ciphers-fallback"))
    }

    @Test fun noCipherLineNoCipherInjection() =
        assertFalse(OvpnConfigPrep.prepare("client\nremote 1.2.3.4 443\n", null).contains("data-ciphers"))

    @Test fun injectsDnsOverride() {
        val out = OvpnConfigPrep.prepare("client\n", "1.1.1.1, 1.0.0.1, 9.9.9.9")
        assertTrue(out.contains("dhcp-option DNS 1.1.1.1\n"))
        assertTrue(out.contains("dhcp-option DNS 1.0.0.1\n"))
        assertFalse(out.contains("9.9.9.9")) // at most two resolvers
    }

    @Test fun dropsDnsEntriesWithOddCharacters() {
        assertFalse(OvpnConfigPrep.prepare("client\n", "1.1.1.1\nup /x").contains("up /x"))
    }

    @Test fun alwaysEndsWithNewline() = assertTrue(OvpnConfigPrep.prepare("client", null).endsWith("\n"))
}
