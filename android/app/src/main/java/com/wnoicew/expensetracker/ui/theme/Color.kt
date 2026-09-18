package com.wnoicew.expensetracker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// OLED Dark Palette
val BgPrimaryDark = Color(0xFF090D16)
val BgSecondaryDark = Color(0xFF0F172A)
val BgCardDark = Color(0xFF131B2E)
val BgElevatedDark = Color(0xFF1E2945)
val GlassBorderDark = Color(0x1FFFFFFF)
val GlassBorderGlowDark = Color(0x4D3B82F6)

// Luxury Light Palette
val BgPrimaryLight = Color(0xFFF8FAFC)
val BgSecondaryLight = Color(0xFFFFFFFF)
val BgCardLight = Color(0xFFFFFFFF)
val BgElevatedLight = Color(0xFFF1F5F9)
val GlassBorderLight = Color(0xFFE2E8F0)
val GlassBorderGlowLight = Color(0x332563EB)

// Financial Action Colors (Semantic)
// Light-theme variants are darkened so small text and white-on-fill labels reach 4.5:1 on light
// surfaces (#FFFFFF..#F1F5F9); dark theme keeps the original saturated colors.
val IncomeGreenDarkTheme = Color(0xFF10B981)
val IncomeGreenLightTheme = Color(0xFF05664A)
val ExpenseRoseDarkTheme = Color(0xFFF43F5E)
val ExpenseRoseLightTheme = Color(0xFFBE123C)
val TransferVioletDarkTheme = Color(0xFF8B5CF6)
val TransferVioletLightTheme = Color(0xFF6D28D9)
val WarningAmberDarkTheme = Color(0xFFF59E0B)
val WarningAmberLightTheme = Color(0xFF92400E)
val PrimaryBlueDarkTheme = Color(0xFF3B82F6)
val PrimaryBlueLightTheme = Color(0xFF1D4ED8)

val IncomeGreenGlow = Color(0x4010B981)
val ExpenseRoseGlow = Color(0x40F43F5E)
val TransferVioletGlow = Color(0x408B5CF6)
val AccentCyan = Color(0xFF06B6D4)

// Amber fill that carries black text (9.8:1); the text-safe WarningAmber is too dark for that in light theme.
val WarningAmberFill = Color(0xFFF59E0B)

val LocalDarkTheme = staticCompositionLocalOf { true }

val IncomeGreen: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) IncomeGreenDarkTheme else IncomeGreenLightTheme
val ExpenseRose: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) ExpenseRoseDarkTheme else ExpenseRoseLightTheme
val TransferViolet: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) TransferVioletDarkTheme else TransferVioletLightTheme
val WarningAmber: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) WarningAmberDarkTheme else WarningAmberLightTheme
val PrimaryBlue: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) PrimaryBlueDarkTheme else PrimaryBlueLightTheme

// Backgrounds behind white text (selected calendar day, month picker): #3B82F6 gives only 3.7:1 with white.
val PrimaryBlueFill: Color
    @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFF2563EB) else PrimaryBlueLightTheme

// Text Colors
val TextMainDark = Color(0xFFF8FAFC)
val TextMutedDark = Color(0xFF94A3B8)
val TextDimDark = Color(0xFF64748B)

val TextMainLight = Color(0xFF0F172A)
val TextMutedLight = Color(0xFF475569)
val TextDimLight = Color(0xFF64748B)
