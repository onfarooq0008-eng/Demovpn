package com.fastvpn.app7k2q9x.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object Settings {
    private lateinit var p: SharedPreferences
    val connectOnLaunch = MutableStateFlow(false)
    val reconnect = MutableStateFlow(true)
    val theme = MutableStateFlow(0)          // 0 system, 1 dark, 2 light
    val engine = MutableStateFlow(0)         // 0 auto, 1 ics-openvpn, 2 OpenVPN 3 core
    val selected = MutableStateFlow("")      // "" = Fastest server (auto)

    fun init(c: Context) {
        p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        connectOnLaunch.value = p.getBoolean("launch", false)
        reconnect.value = p.getBoolean("reconnect", true)
        theme.value = p.getInt("theme", 0)
        selected.value = p.getString("selected", "") ?: ""
        engine.value = p.getInt("engine", 0)
    }
    fun setConnectOnLaunch(v: Boolean) { connectOnLaunch.value = v; p.edit().putBoolean("launch", v).apply() }
    fun setReconnect(v: Boolean) { reconnect.value = v; p.edit().putBoolean("reconnect", v).apply() }
    fun setTheme(v: Int) { theme.value = v; p.edit().putInt("theme", v).apply() }
    fun setEngine(v: Int) { engine.value = v; p.edit().putInt("engine", v).apply() }
    fun setSelected(v: String) { selected.value = v; p.edit().putString("selected", v).apply() }
}
