package com.fastvpnn.app.data

/**
 * Last tweaks to an already sanitized .ovpn before it is handed to the engine.
 *
 *  - OpenVPN 2.6 no longer negotiates the legacy `cipher` line by itself: servers that only list
 *    `cipher AES-128-CBC` (most public VPNGate relays) need `data-ciphers-fallback`, otherwise the
 *    tunnel comes up and then fails with "no usable data channel cipher".
 *  - A DNS override from Settings is injected as `dhcp-option DNS` lines (the engine then ignores the
 *    DNS pushed by the server).
 */
object OvpnConfigPrep {
    private const val MODERN_CIPHERS = "AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305"
    private val CIPHER_LINE = Regex("^\\s*cipher\\s+(\\S+)", RegexOption.IGNORE_CASE)

    fun prepare(config: String, dnsOverride: String?): String {
        val lines = config.lines()
        val out = StringBuilder()

        val cipher = lines.firstNotNullOfOrNull { CIPHER_LINE.find(it)?.groupValues?.get(1) }
        val hasDataCiphers = lines.any { it.trimStart().startsWith("data-ciphers", ignoreCase = true) }
        if (cipher != null && !hasDataCiphers) {
            out.append("data-ciphers-fallback ").append(cipher).append('\n')
            out.append("data-ciphers ").append(MODERN_CIPHERS)
            if (!MODERN_CIPHERS.split(':').contains(cipher.uppercase())) out.append(':').append(cipher)
            out.append('\n')
        }

        dnsOverride?.split(',', ' ', ';')?.map { it.trim() }
            ?.filter { it.isNotEmpty() && it.all { c -> c.isDigit() || c == '.' || c == ':' || c.isLetter() } }
            ?.take(2)
            ?.forEach { out.append("dhcp-option DNS ").append(it).append('\n') }

        out.append(config)
        if (!config.endsWith("\n")) out.append('\n')
        return out.toString()
    }
}
