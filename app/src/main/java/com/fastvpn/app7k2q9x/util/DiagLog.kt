package com.fastvpn.app7k2q9x.util

import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.*

/** In-memory ring buffer of sanitized diagnostic lines. Never receives config content. */
object DiagLog {
    private const val MAX = 500
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val lines = MutableStateFlow<List<String>>(emptyList())
    private val pem = Regex("-----BEGIN [A-Z ]+-----[\\s\\S]*?-----END [A-Z ]+-----")
    private val secret = Regex("(?i)(password|passwd|pass|secret|token|key)\\s*[=:]\\s*\\S+")

    fun sanitize(s: String) = s.replace(pem, "[redacted]").replace(secret) { it.groupValues[1] + "=[redacted]" }

    @Synchronized fun d(tag: String, msg: String) {
        val l = "${fmt.format(Date())} [$tag] ${sanitize(msg)}"
        lines.value = (lines.value + l).takeLast(MAX)
    }
    fun clear() { lines.value = emptyList() }
    fun export(): String = "FastVPN diagnostics\n" + lines.value.joinToString("\n")
}
