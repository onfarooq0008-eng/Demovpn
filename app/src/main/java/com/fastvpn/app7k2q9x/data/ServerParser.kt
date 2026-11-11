package com.fastvpn.app7k2q9x.data

import kotlinx.serialization.json.*
import java.net.URI

data class ParseResult(val servers: List<Server>, val updated: String?, val rejected: Int)

/** Pure JVM parser for the GitHub servers.json (unit-testable). */
object ServerParser {
    private val ipv4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
    private val ipv6 = Regex("""^[0-9a-fA-F:]{3,45}$""")
    val allowedHosts = setOf("raw.githubusercontent.com", "github.com", "objects.githubusercontent.com")

    /** Throws if the document is malformed as a whole; individual bad entries are counted in `rejected`. */
    fun parse(text: String): ParseResult {
        val root = Json.parseToJsonElement(text).jsonObject
        val updated = root["updated"]?.jsonPrimitive?.contentOrNull
        val arr = root["servers"]?.jsonArray ?: throw IllegalArgumentException("Missing servers array")
        val seen = HashSet<String>()
        val out = ArrayList<Server>()
        var rejected = 0
        for (el in arr) {
            val s = try { parseOne(el.jsonObject) } catch (e: Exception) { null }
            if (s == null || !seen.add(s.ip)) { rejected++; continue }   // invalid or duplicate
            out.add(s)
        }
        return ParseResult(out, updated, rejected)
    }

    private fun parseOne(o: JsonObject): Server? {
        fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val name = str("name"); val ip = str("ip"); val country = str("country"); val url = str("url")
        if (name.isEmpty() || country.isEmpty()) return null
        if (!ipv4.matches(ip) && !(ip.contains(':') && ipv6.matches(ip))) return null
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host !in allowedHosts || !uri.path.endsWith(".ovpn")) return null
        val speed = (o["speed_mbps"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
        if (speed < 0) return null
        val ping = (o["ping_ms"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }
        val connect = (o["connect_seconds"] as? JsonPrimitive)?.doubleOrNull
        return Server(name, str("source").ifEmpty { "unknown" }, ip, country, speed, ping, connect, url)
    }
}
