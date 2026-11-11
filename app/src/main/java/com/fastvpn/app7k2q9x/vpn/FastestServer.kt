package com.fastvpn.app7k2q9x.vpn

import com.fastvpn.app7k2q9x.data.Server
import com.fastvpn.app7k2q9x.data.ServerStore
import com.fastvpn.app7k2q9x.util.DiagLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetAddress
import java.util.Locale

/** Multi-factor ranking. Reported speed/ping are hints only; reachability, history and failures count too. */
object FastestServer {
    private const val PROBE_LIMIT = 16
    private const val PROBE_CONCURRENCY = 6
    private const val PROBE_TIMEOUT_MS = 1500

    suspend fun rank(servers: List<Server>): List<Server> {
        val pre = servers.filter { it.isAvailable }.sortedByDescending { score(it, null) }.take(PROBE_LIMIT)
        val sem = Semaphore(PROBE_CONCURRENCY)
        val probed = coroutineScope {
            pre.map { s -> async(Dispatchers.IO) { sem.withPermit { s to probe(s.ip) } } }.awaitAll()
        }
        val ranked = probed.sortedByDescending { (s, rtt) -> score(s, rtt) }.map { it.first }
        DiagLog.d("Fastest", "Probed ${probed.size}, reachable=${probed.count { it.second != null }}, top=${ranked.firstOrNull()?.name}")
        return ranked
    }

    /** ICMP/TCP-echo reachability. A miss is a penalty, not proof of failure (many hosts drop ICMP). */
    private fun probe(ip: String): Int? = try {
        val t0 = System.nanoTime()
        if (InetAddress.getByName(ip).isReachable(PROBE_TIMEOUT_MS)) ((System.nanoTime() - t0) / 1_000_000).toInt() else null
    } catch (e: Exception) { null }

    fun score(s: Server, measuredRtt: Int?): Double {
        val ping = (measuredRtt ?: s.pingMs ?: 250).coerceAtMost(300)
        val pingScore = 100 - ping / 3.0                                   // 0..100
        val speedScore = minOf(s.speedMbps, 500.0) / 5.0                   // 0..100
        val st = ServerStore.stats[s.name]
        val successRate = if (st == null) 0.5 else (st.ok + 1.0) / (st.ok + st.fail + 2.0)
        val failPenalty = (st?.consecutiveFail ?: 0) * 20.0
        val reach = if (measuredRtt != null) 10.0 else 0.0
        val home = if (s.countryCode.equals(Locale.getDefault().country, true)) 5.0 else 0.0
        return 0.45 * pingScore + 0.25 * speedScore + 30.0 * successRate + reach + home - failPenalty
    }
}
