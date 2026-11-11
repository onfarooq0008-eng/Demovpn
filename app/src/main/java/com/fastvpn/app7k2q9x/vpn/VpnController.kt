package com.fastvpn.app7k2q9x.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import com.fastvpn.app7k2q9x.data.*
import com.fastvpn.app7k2q9x.util.DiagLog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Owns the connection lifecycle. One job at a time: any new request cancels the previous one.
 * "Connected" is only ever set by the engine callback (EngineImpl), never here.
 */
object VpnController {
    private const val CONNECT_TIMEOUT_MS = 45_000L
    private const val MAX_CANDIDATES = 5
    private const val MAX_RETRIES = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var app: Context
    private var job: Job? = null
    private var userWants = false
    private var connectedOnce = false
    private var current: Server? = null
    private var sessionStart = 0L
    private var engineStarted = false

    fun init(context: Context) {
        app = context.applicationContext
        Engines.init(app)
        scope.launch {
            VpnSession.state.collect { s ->
                if (s == VpnState.CONNECTED) { connectedOnce = true; if (sessionStart == 0L) sessionStart = System.currentTimeMillis() }
                // Dropped after having been connected, and nobody is already handling it -> recover.
                if ((s == VpnState.FAILED || s == VpnState.DISCONNECTED) && connectedOnce && userWants && job?.isActive != true) onDropped()
            }
        }
    }

    // ---------------- public API ----------------
    fun connect(server: Server) = launchChecked {
        userWants = true; connectedOnce = false
        if (!attempt(server)) giveUp()
    }

    fun connectFastest() = launchChecked {
        userWants = true; connectedOnce = false
        VpnSession.error.value = null
        VpnSession.state.value = VpnState.CONNECTING
        VpnSession.progress.value = "Finding the fastest server…"
        if (ServerRepository.state.value.servers.isEmpty()) ServerRepository.refresh(force = true)
        val ranked = FastestServer.rank(ServerRepository.state.value.servers).take(MAX_CANDIDATES)
        if (ranked.isEmpty()) { VpnSession.error.value = "No servers available. Check your internet and refresh."; giveUp(); return@launchChecked }
        for ((i, s) in ranked.withIndex()) {
            VpnSession.progress.value = "Trying ${s.country} (${i + 1}/${ranked.size})"
            if (attempt(s)) { VpnSession.progress.value = null; return@launchChecked }
            ensureActive()
        }
        VpnSession.error.value = "All ${ranked.size} candidate servers failed. Try again later or pick another server."
        giveUp()
    }

    /** Also used as "cancel" while connecting. */
    fun disconnect() {
        userWants = false; connectedOnce = false
        job?.cancel(); pending = null
        if (VpnSession.state.value == VpnState.DISCONNECTED && !engineStarted) return
        finishSession("Disconnected by user")
        VpnSession.state.value = VpnState.DISCONNECTING
        VpnSession.progress.value = null
        job = scope.launch {
            engineStarted = false
            runCatching { Engines.forStop().stop(app) }
            withTimeoutOrNull(6_000) { VpnSession.state.first { it == VpnState.DISCONNECTED } }
            runCatching { Engines.forStop().release(app) }
            VpnSession.state.value = VpnState.DISCONNECTED
            VpnSession.stats.value = VpnStats(); VpnSession.info.value = SessionInfo()
        }
    }

    private var pending: (() -> Unit)? = null
    fun onPermissionResult(granted: Boolean) {
        VpnSession.permissionIntent.value = null
        val p = pending; pending = null
        if (granted && p != null) p() else {
            VpnSession.error.value = "VPN permission was denied"
            VpnSession.state.value = VpnState.DISCONNECTED
        }
    }

    // ---------------- internals ----------------
    private fun launchChecked(block: suspend CoroutineScope.() -> Unit) {
        if (!Engines.available) {
            VpnSession.error.value = "No OpenVPN engine in this build (or the selected one is missing). Run scripts/fetch_engine.sh --ics and/or --ovpn3, then rebuild. See README."
            VpnSession.state.value = VpnState.FAILED; DiagLog.d("Connect", "Engine missing"); return
        }
        val intent = VpnService.prepare(app)
        if (intent != null) {
            pending = { launchChecked(block) }
            VpnSession.state.value = VpnState.PERMISSION_REQUIRED
            VpnSession.permissionIntent.value = intent
            return
        }
        job?.cancel()
        job = scope.launch(block = block)
    }

    private suspend fun attempt(server: Server): Boolean {
        current = server
        VpnSession.info.value = SessionInfo(server = server)
        VpnSession.error.value = null
        if (engineStarted) { stopEngine(); delay(800) }            // never two tunnels
        VpnSession.state.value = VpnState.CONNECTING
        DiagLog.d("Connect", "Attempt ${server.name}")

        val cfg = try { ServerRepository.fetchConfig(server) } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return failServer(server, "Could not download profile (${e.message})") }
        val v = OvpnValidator.validate(cfg)
        if (!v.ok) return failServer(server, "Invalid profile: ${v.reason}")
        VpnSession.info.value = VpnSession.info.value.copy(transport = v.transport)

        try { engineStarted = true; Engines.forStart().start(app, server, cfg) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { return failServer(server, "Engine could not start this profile (${e.javaClass.simpleName})") }

        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            VpnSession.state.first { it == VpnState.CONNECTED || it == VpnState.FAILED || it == VpnState.NO_INTERNET }
        }
        return when (result) {
            VpnState.CONNECTED -> { ServerStore.recordSuccess(server.name); sessionStart = System.currentTimeMillis(); DiagLog.d("Connect", "Connected ${server.name}"); true }
            null -> failServer(server, "Connection timed out")
            else -> failServer(server, VpnSession.error.value ?: "Connection failed")
        }
    }

    private suspend fun failServer(server: Server, msg: String): Boolean {
        DiagLog.d("Connect", "${server.name}: $msg")
        ServerStore.recordFailure(server.name)
        ServerRepository.refreshAvailability()
        VpnSession.error.value = msg
        withContext(NonCancellable) { stopEngine() }
        return false
    }

    private fun giveUp() {
        userWants = false
        runCatching { Engines.forStop().release(app) }
        VpnSession.progress.value = null
        VpnSession.state.value = VpnState.FAILED
        current?.let { addHistory(it, 0, "Failed: ${VpnSession.error.value}") }
    }

    private fun stopEngine() {
        if (!engineStarted) return
        engineStarted = false
        runCatching { Engines.forStop().stop(app) }.onFailure { DiagLog.d("Engine", "stop error ${it.javaClass.simpleName}") }
    }

    private fun onDropped() {
        val s = current ?: return
        finishSession("Connection dropped")
        if (!Settings.reconnect.value) { userWants = false; connectedOnce = false; return }
        job = scope.launch {
            for (i in 1..MAX_RETRIES) {
                VpnSession.state.value = VpnState.RECONNECTING
                VpnSession.progress.value = "Reconnecting ($i/$MAX_RETRIES)"
                delay(minOf(30_000L, 1000L shl i))
                if (!hasInternet()) { VpnSession.state.value = VpnState.NO_INTERNET; continue }
                if (attempt(s)) { VpnSession.progress.value = null; return@launch }
            }
            VpnSession.error.value = "Could not reconnect after $MAX_RETRIES attempts"
            connectedOnce = false
            giveUp()
        }
    }

    private fun finishSession(result: String) {
        val s = current ?: return
        if (sessionStart > 0) addHistory(s, (System.currentTimeMillis() - sessionStart) / 1000, result)
        sessionStart = 0
    }

    private fun addHistory(s: Server, dur: Long, result: String) =
        ServerStore.addHistory(HistoryEntry(s.name, s.country, System.currentTimeMillis() - dur * 1000, dur, result))

    /** True if any non-VPN network has internet capability. */
    private fun hasInternet(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.any {
            val c = cm.getNetworkCapabilities(it)
            c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }
}
