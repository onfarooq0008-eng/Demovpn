package com.fastvpnn.app.data

/**
 * The .ovpn files come from a third-party repository. OpenVPN configs can run programs or load
 * plugins (up/down scripts, plugin, tls-verify ...), so those directives are removed before the
 * config is handed to the engine. Anything inside <inline> blocks (certificates, keys) is untouched.
 */
object OvpnConfigSanitizer {

    private val BLOCKED = setOf(
        "script-security", "up", "down", "up-restart", "route-up", "route-pre-down", "down-pre",
        "ipchange", "tls-verify", "client-connect", "client-disconnect", "learn-address",
        "auth-user-pass-verify", "plugin", "management", "management-client", "management-query-passwords",
        "management-hold", "daemon", "chroot", "cd", "log", "log-append", "writepid", "status",
        "config", "dev-node", "setenv", "setenv-safe", "echo", "askpass"
    )

    fun sanitize(config: String): String {
        val out = StringBuilder()
        var inBlock = false
        for (raw in config.lineSequence()) {
            val line = raw.trim()
            if (inBlock) {
                out.append(raw).append('\n')
                if (line.startsWith("</")) inBlock = false
                continue
            }
            if (line.startsWith("<") && !line.startsWith("</")) {
                inBlock = true
                out.append(raw).append('\n')
                continue
            }
            val directive = line.trimStart('-').substringBefore(' ').substringBefore('\t').lowercase()
            if (directive in BLOCKED) continue
            out.append(raw).append('\n')
        }
        return out.toString()
    }
}
