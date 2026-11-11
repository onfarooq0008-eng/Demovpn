package com.fastvpn.app7k2q9x.data

data class ValidationResult(val ok: Boolean, val reason: String? = null, val transport: String? = null)

/**
 * Validates an untrusted .ovpn file. It never modifies the config; it only accepts or rejects it.
 * Never logs file content.
 */
object OvpnValidator {
    private const val MAX_BYTES = 256 * 1024
    private val forbidden = setOf(
        "up", "down", "route-up", "route-pre-down", "tls-verify", "ipchange", "auth-user-pass-verify",
        "client-connect", "client-disconnect", "learn-address", "plugin", "management", "up-restart",
    )
    private val fileDirectives = setOf("ca", "cert", "key", "pkcs12", "tls-auth", "tls-crypt", "tls-crypt-v2", "secret", "extra-certs")

    fun validate(config: String): ValidationResult {
        if (config.isBlank()) return bad("empty file")
        if (config.length > MAX_BYTES) return bad("file too large")
        val blocks = HashSet<String>()
        var inBlock: String? = null
        var blockHasContent = false
        var hasRemote = false
        var client = false
        var authUserPass = false
        var authFile = false
        var transport = "UDP"

        for (raw in config.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (inBlock != null) {
                if (line.equals("</$inBlock>", true)) {
                    if (blockHasContent) blocks.add(inBlock)
                    inBlock = null
                } else blockHasContent = true
                continue
            }
            if (line.startsWith("#") || line.startsWith(";")) continue
            if (line.startsWith("<") && line.endsWith(">") && !line.startsWith("</")) {
                inBlock = line.substring(1, line.length - 1).lowercase(); blockHasContent = false; continue
            }
            val t = line.split(Regex("\\s+"))
            val d = t[0].lowercase()
            when {
                d in forbidden -> return bad("directive '$d' is not allowed")
                d == "remote" -> {
                    if (t.size < 2 || t[1].isBlank()) return bad("malformed remote")
                    val port = t.getOrNull(2)?.toIntOrNull()
                    if (t.size > 2 && (port == null || port !in 1..65535)) return bad("invalid remote port")
                    hasRemote = true
                    if (t.getOrNull(3)?.lowercase()?.startsWith("tcp") == true) transport = "TCP"
                }
                d == "proto" -> if (t.getOrNull(1)?.lowercase()?.startsWith("tcp") == true) transport = "TCP"
                d == "client" || d == "tls-client" || d == "pull" -> client = true
                d == "auth-user-pass" -> if (t.size > 1) authFile = true else authUserPass = true
                d in fileDirectives && t.size > 1 && t[1] != "[inline]" -> return bad("references external file for '$d'")
            }
        }
        if (inBlock != null) return bad("unterminated <$inBlock> block")
        if (!hasRemote) return bad("no remote endpoint")
        if (!client) return bad("not a client configuration")
        if (authFile) return bad("references external credentials file")
        if (authUserPass && "auth-user-pass" !in blocks) return bad("requires username/password (none provided)")
        if ("ca" !in blocks) return bad("no inline CA certificate")
        if ("key" in blocks && "cert" !in blocks) return bad("private key without certificate")
        return ValidationResult(true, transport = transport)
    }

    private fun bad(r: String) = ValidationResult(false, r)
}
