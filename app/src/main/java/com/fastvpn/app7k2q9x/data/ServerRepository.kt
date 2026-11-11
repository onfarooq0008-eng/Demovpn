package com.fastvpn.app7k2q9x.data

import android.content.Context
import com.fastvpn.app7k2q9x.util.DiagLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ServerList(
    val servers: List<Server> = emptyList(),
    val updated: String? = null,
    val fetchedAt: Long = 0,
    val error: String? = null,
    val loading: Boolean = false,
)

object ServerRepository {
    const val SERVERS_URL = "https://raw.githubusercontent.com/onfarooq0008-eng/Working-Ovpn/refs/heads/main/servers.json"
    private const val MIN_INTERVAL_MS = 15 * 60_000L
    private const val MANUAL_DEBOUNCE_MS = 5_000L

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    val state = MutableStateFlow(ServerList())

    private lateinit var dir: File
    private val mutex = Mutex()
    private var lastAttempt = 0L

    /** Loads the cached list (if any). There is no bundled server list: servers always come from GitHub. */
    fun init(context: Context) {
        dir = context.filesDir
        val cache = File(dir, "servers.json")
        if (cache.exists()) runCatching {
            val r = ServerParser.parse(cache.readText())
            state.value = ServerList(applyStats(r.servers), r.updated, cache.lastModified())
            DiagLog.d("Servers", "Loaded ${r.servers.size} servers from cache")
        }
    }

    /** Re-applies availability from local connection history (e.g. after a failure). */
    fun refreshAvailability() { state.value = state.value.copy(servers = applyStats(state.value.servers)) }

    private fun applyStats(list: List<Server>) = list.map {
        it.copy(isAvailable = ServerStore.isAvailable(it.name), lastChecked = ServerStore.stats[it.name]?.lastTried?.takeIf { t -> t > 0 })
    }

    suspend fun refresh(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - state.value.fetchedAt < MIN_INTERVAL_MS) return
        if (force && now - lastAttempt < MANUAL_DEBOUNCE_MS) return
        if (!mutex.tryLock()) return
        lastAttempt = now
        try {
            state.value = state.value.copy(loading = true, error = null)
            val result = withContext(Dispatchers.IO) { download() }
            state.value = result
        } finally { mutex.unlock() }
    }

    private fun download(): ServerList {
        val old = state.value
        val cache = File(dir, "servers.json")
        val etagFile = File(dir, "servers.etag")
        fun fail(msg: String): ServerList { DiagLog.d("Servers", "Refresh failed: $msg"); return old.copy(loading = false, error = msg) }
        return try {
            val req = Request.Builder().url(SERVERS_URL).apply {
                if (cache.exists() && etagFile.exists()) header("If-None-Match", etagFile.readText())
            }.build()
            http.newCall(req).execute().use { resp ->
                when {
                    resp.code == 304 -> { cache.setLastModified(System.currentTimeMillis()); old.copy(loading = false, fetchedAt = System.currentTimeMillis(), error = null) }
                    resp.code == 403 || resp.code == 429 -> fail("GitHub rate limit reached. Try again later.")
                    !resp.isSuccessful -> fail("Server list unavailable (HTTP ${resp.code})")
                    else -> {
                        val body = resp.body?.string().orEmpty()
                        val parsed = try { ServerParser.parse(body) } catch (e: Exception) { return fail("Server list is malformed") }
                        if (parsed.servers.isEmpty()) return fail("Server list contained no valid servers")
                        cache.writeText(body)                         // only after a fully valid parse
                        resp.header("ETag")?.let { etagFile.writeText(it) }
                        DiagLog.d("Servers", "Fetched ${parsed.servers.size} servers, rejected ${parsed.rejected}, updated=${parsed.updated}")
                        ServerList(applyStats(parsed.servers), parsed.updated, System.currentTimeMillis())
                    }
                }
            }
        } catch (e: IOException) {
            fail(if (old.servers.isEmpty()) "No internet connection" else "Offline – showing cached servers")
        }
    }

    /** Downloads one .ovpn (untrusted input). Host is restricted to GitHub; size is capped. */
    suspend fun fetchConfig(server: Server): String = withContext(Dispatchers.IO) {
        val u = java.net.URI(server.configUrl)
        require(u.scheme == "https" && u.host in ServerParser.allowedHosts) { "Blocked profile host" }
        http.newCall(Request.Builder().url(server.configUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.peekBody(256 * 1024L + 1).string()
        }
    }

    suspend fun httpGetText(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string().orEmpty().trim() }
    }
}
