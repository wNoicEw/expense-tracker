package com.wnoicew.expensetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.engine.CurrencyInfo
import com.wnoicew.expensetracker.ui.theme.LocalDarkTheme
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue

/**
 * Currency Selector inspired by Apple HIG & modern fintech aesthetics.
 * Single unified surface with clean, non-nested cards, crisp borders,
 * soft tinted glow on selection, and zero double-box visual clutter in light & dark modes.
 */
@Composable
fun BacklitCurrencySelector(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "SELECT PRIMARY CURRENCY"
) {
    val currencies = remember { CurrencyEngine.getSupportedCurrencies() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (label.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Selected: ${CurrencyEngine.getSymbol(selectedCurrency)} $selectedCurrency",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(currencies, key = { it.code }) { info ->
                val isSelected = info.code.equals(selectedCurrency, ignoreCase = true)
                BacklitCurrencyCard(
                    info = info,
                    isSelected = isSelected,
                    onClick = { onCurrencySelected(info.code) }
                )
            }
        }
    }
}

@Composable
fun BacklitCurrencyCard(
    info: CurrencyInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val haptic = LocalHapticFeedback.current

    // Signature Accent Color Palette tailored per currency
    val accentColor = remember(info.code) {
        when (info.code) {
            "INR" -> Color(0xFF10B981) // Emerald Green
            "USD" -> Color(0xFF3B82F6) // Electric Blue
            "EUR" -> Color(0xFF8B5CF6) // Royal Violet
            "GBP" -> Color(0xFFE11D48) // Rose Crimson
            "CHF" -> Color(0xFF06B6D4) // Alpine Cyan
            "JPY" -> Color(0xFFF59E0B) // Golden Amber
            else -> Color(0xFF3B82F6)
        }
    }

    // Clean, single-surface background:
    // In Light mode: Pure crisp white (#FFFFFF) when unselected, gentle 8% accent tint when selected.
    // In Dark mode: Deep card dark (#131B2E) when unselected, 16% accent tint when selected.
    val animatedBgColor by animateColorAsState(
        targetValue = when {
            isSelected && isDark -> accentColor.copy(alpha = 0.16f)
            isSelected && !isDark -> accentColor.copy(alpha = 0.08f)
            isDark -> Color(0xFF131B2E)
            else -> Color.White
        },
        animationSpec = tween(200),
        label = "currencyCardBg"
    )

    // Crisp 1dp border when unselected, prominent 2dp accent border when selected
    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor
            isDark -> Color(0x22FFFFFF)
            else -> Color(0xFFE2E8F0)
        },
        animationSpec = tween(200),
        label = "currencyCardBorder"
    )

    val borderWidth = if (isSelected) 2.dp else 1.dp
    val cardShape = RoundedCornerShape(14.dp)

    // Unified single-layer card: No nested box, no double border, no shadow clipping bug
    Box(
        modifier = modifier
            .width(74.dp)
            .height(82.dp)
            .clip(cardShape)
            .background(animatedBgColor)
            .border(borderWidth, animatedBorderColor, cardShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 7.dp, vertical = 7.dp)
    ) {
        // Top row: Flag emoji on left, crisp checkmark badge on right when selected
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = info.flag,
                fontSize = 15.sp,
                lineHeight = 15.sp
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }

        // Center & Bottom: Symbol, Code, and Short Name
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = info.symbol,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = info.code,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = info.name.split(" ").firstOrNull() ?: info.code,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = if (isSelected) accentColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
