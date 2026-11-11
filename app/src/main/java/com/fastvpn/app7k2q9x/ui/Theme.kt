package com.fastvpn.app7k2q9x.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val Blue = Color(0xFF2F6BFF); val Cyan = Color(0xFF26D0FF); val Green = Color(0xFF2EE59D)
    val Gold = Color(0xFFFFC857); val Red = Color(0xFFFF5C5C)
    val Bg @Composable get() = MaterialTheme.colorScheme.background
    val Card @Composable get() = MaterialTheme.colorScheme.surface
    val Text @Composable get() = MaterialTheme.colorScheme.onBackground
    val Muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Top @Composable get() = MaterialTheme.colorScheme.surfaceVariant
}

private val Dark = darkColorScheme(
    primary = C.Blue, background = Color(0xFF0B1026), surface = Color(0xFF161D3F), surfaceVariant = Color(0xFF111A47),
    onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFF8A93B8),
)
private val Light = lightColorScheme(
    primary = C.Blue, background = Color(0xFFF3F5FC), surface = Color.White, surfaceVariant = Color(0xFFE3E9FF),
    onBackground = Color(0xFF0B1026), onSurface = Color(0xFF0B1026), onSurfaceVariant = Color(0xFF5A6384),
)

@Composable
fun FastVpnTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
