package com.fastvpnn.app.data

import com.fastvpnn.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Loads the public OpenVPN server list (servers.json, see BuildConfig.OPENVPN_SERVERS_URL) and the
 * individual .ovpn files it points to. These servers need no backend registration: the list entry
 * is enough to connect.
 *
 * Safety: the list and the configs come from a third-party repository, so
 *  - only https URLs on the SAME host as the list are accepted,
 *  - downloads are size-capped,
 *  - every config goes through [OvpnConfigSanitizer] (strips script / plugin directives).
 */
object OvpnServerClient {

    private const val MAX_LIST_BYTES = 2L * 1024 * 1024
    private const val MAX_CONFIG_BYTES = 256L * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchServers(listUrl: String = BuildConfig.OPENVPN_SERVERS_URL): List<Server> =
        withContext(Dispatchers.IO) {
            parseServers(download(listUrl, MAX_LIST_BYTES), listUrl)
        }

    /** Downloads one .ovpn file and returns it sanitized. */
    suspend fun fetchConfig(ovpnUrl: String, listUrl: String = BuildConfig.OPENVPN_SERVERS_URL): String =
        withContext(Dispatchers.IO) {
            if (!isAllowedUrl(ovpnUrl, listUrl)) throw IOException("Blocked config URL")
            val text = OvpnConfigSanitizer.sanitize(download(ovpnUrl, MAX_CONFIG_BYTES))
            if (!text.contains("remote ")) throw IOException("Invalid OpenVPN config")
            text
        }

    private fun download(url: String, maxBytes: Long): String {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val source = body.source()
            source.request(maxBytes + 1)
            if (source.buffer.size > maxBytes) throw IOException("Response too large")
            return source.readUtf8()
        }
    }

    internal fun isAllowedUrl(url: String, listUrl: String): Boolean {
        val u = url.toHttpUrlOrNull() ?: return false
        val l = listUrl.toHttpUrlOrNull() ?: return false
        return u.isHttps && u.host == l.host
    }

    private val COUNTRY_NAMES = mapOf(
        "Korea Republic of" to "South Korea",
        "Russian Federation" to "Russia",
        "Viet Nam" to "Vietnam",
        "Iran Islamic Republic of" to "Iran",
        "Taiwan Province of China" to "Taiwan"
    )

    /** Pure parser (unit-tested). Entries without an ip or an allowed https url are skipped. */
    internal fun parseServers(json: String, listUrl: String = BuildConfig.OPENVPN_SERVERS_URL): List<Server> {
        val array = JSONObject(json).optJSONArray("servers") ?: return emptyList()
        val perCountry = HashMap<String, Int>()
        val result = ArrayList<Server>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name")
            val ip = o.optString("ip")
            val url = o.optString("url")
            if (name.isBlank() || ip.isBlank() || !isAllowedUrl(url, listUrl)) continue
            val rawCountry = o.optString("country").trim()
            val country = COUNTRY_NAMES[rawCountry] ?: rawCountry.ifBlank { "Unknown" }
            val prefix = name.substringBefore('-')
            val code = if (prefix.length == 2 && prefix.all { it in 'A'..'Z' }) prefix else ""
            val n = (perCountry[country] ?: 0) + 1
            perCountry[country] = n
            result.add(
                Server(
                    id = "ovpn-$name",
                    name = "$country #$n",
                    countryName = country,
                    countryCode = code,
                    endpointHost = ip,
                    endpointPort = 0,
                    protocol = Server.PROTOCOL_OPENVPN,
                    ovpnUrl = url,
                    speedMbps = o.optDouble("speed_mbps", 0.0),
                    // The list already carries a measured ping, so these servers are never TCP-probed.
                    pingMs = o.optString("ping_ms").toIntOrNull() ?: -1
                )
            )
        }
        return result
    }
}
