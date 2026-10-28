package com.myrtilles.blackjackcounter

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 🎰 Casino / Blackjack Palette
val CasinoGreen = Color(0xFF0B6B3A)      // Main felt green
val CasinoGreenDark = Color(0xFF064E29)  // Deep green
val CasinoGreenLight = Color(0xFF1B8A4E) // Bright green
val CasinoGold = Color(0xFFD4AF37)       // Accent gold

val LightCasinoColors = lightColorScheme(
    primary = CasinoGreen,
    onPrimary = Color.White,
    primaryContainer = CasinoGreenLight,
    onPrimaryContainer = Color.White,
    secondary = CasinoGold,
    onSecondary = Color.Black,
    background = Color(0xFFF5F5F0),
    surface = Color.White,
)

val DarkCasinoColors = darkColorScheme(
    primary = CasinoGreenLight,
    onPrimary = Color.White,
    primaryContainer = CasinoGreenDark,
    onPrimaryContainer = Color.White,
    secondary = CasinoGold,
    onSecondary = Color.Black,
    background = Color(0xFF0F1815),
    surface = Color(0xFF1A2620),
)
