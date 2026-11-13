package com.fastvpnn.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OvpnConfigSanitizerTest {
    @Test fun stripsScriptAndPluginDirectives() {
        val out = OvpnConfigSanitizer.sanitize(
            "client\nremote 1.2.3.4 443\nscript-security 2\nup /tmp/x.sh\nplugin foo.so\n--route-up evil\nproto tcp\n"
        )
        assertTrue(out.contains("remote 1.2.3.4 443"))
        assertTrue(out.contains("proto tcp"))
        assertFalse(out.contains("script-security"))
        assertFalse(out.contains("up /tmp"))
        assertFalse(out.contains("plugin"))
        assertFalse(out.contains("route-up"))
    }

    @Test fun keepsInlineBlocksIntact() {
        val cfg = "client\n<ca>\n-----BEGIN CERTIFICATE-----\nup\n-----END CERTIFICATE-----\n</ca>\n"
        assertTrue(OvpnConfigSanitizer.sanitize(cfg).contains("\nup\n"))
    }

    @Test fun doesNotTouchSimilarlyNamedDirectives() {
        val out = OvpnConfigSanitizer.sanitize("route 10.0.0.0 255.0.0.0\nupdown-ok 1\n")
        assertTrue(out.contains("route 10.0.0.0"))
        assertTrue(out.contains("updown-ok"))
    }
}
