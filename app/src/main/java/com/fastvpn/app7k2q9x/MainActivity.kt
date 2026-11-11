package com.fastvpn.app7k2q9x

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fastvpn.app7k2q9x.data.ServerRepository
import com.fastvpn.app7k2q9x.data.Settings
import com.fastvpn.app7k2q9x.ui.*
import com.fastvpn.app7k2q9x.vpn.VpnController
import com.fastvpn.app7k2q9x.vpn.VpnSession
import com.fastvpn.app7k2q9x.vpn.VpnState

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var autoConnectDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by Settings.theme.collectAsState()
            FastVpnTheme(theme) { App() }
        }
    }

    @Composable
    private fun App() {
        val nav = rememberNavController()

        val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }

        // Android's one-time VPN consent dialog, requested by VpnController.
        val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            VpnController.onPermissionResult(it.resultCode == Activity.RESULT_OK)
        }
        val permIntent by VpnSession.permissionIntent.collectAsState()
        LaunchedEffect(permIntent) { permIntent?.let { vpnPermission.launch(it) } }

        // Optional "connect on app launch" (only once the server list is available).
        val list by ServerRepository.state.collectAsState()
        val onLaunch by Settings.connectOnLaunch.collectAsState()
        LaunchedEffect(list.servers.isNotEmpty(), onLaunch) {
            if (!autoConnectDone && onLaunch && list.servers.isNotEmpty() && VpnSession.state.value == VpnState.DISCONNECTED) {
                autoConnectDone = true; vm.start()
            }
        }

        NavHost(nav, startDestination = "home") {
            composable("home") { HomeScreen(vm, nav) }
            composable("locations") { LocationsScreen(vm) { nav.popBackStack() } }
            composable("favorites") { FavoritesScreen(vm) { nav.popBackStack() } }
            composable("details") { DetailsScreen { nav.popBackStack() } }
            composable("history") { HistoryScreen { nav.popBackStack() } }
            composable("settings") { SettingsScreen(nav) { nav.popBackStack() } }
            composable("diagnostics") { DiagnosticsScreen { nav.popBackStack() } }
            composable("about") { AboutScreen { nav.popBackStack() } }
        }
    }
}
