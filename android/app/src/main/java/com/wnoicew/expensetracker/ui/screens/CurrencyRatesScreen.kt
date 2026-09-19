package com.wnoicew.expensetracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.engine.CurrencyInfo
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyRatesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    // Observe version to force recomposition on currency state changes
    val currencyVersion by viewModel.currencyStateVersion
    val isSyncing by viewModel.isSyncingCurrency
    val activeProfile by viewModel.activeProfile
    val primaryCurrency = activeProfile?.currency ?: CurrencyEngine.DEFAULT_CURRENCY

    val currencies = remember(currencyVersion) { CurrencyEngine.getSupportedCurrencies() }
    val ratesSnapshot = remember(currencyVersion) { CurrencyEngine.getRatesSnapshot() }
    val lastSyncTime = remember(currencyVersion) { viewModel.getLastCurrencyFetchTimestamp() }
    val hasOverrides = remember(currencyVersion) { CurrencyEngine.hasAnyManualOverride() }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    var editingCurrency by remember { mutableStateOf<CurrencyInfo?>(null) }
    var editRateInput by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "syncSpinTransition")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncRotation"
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp),
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Title & Description
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Currency & Exchange Rates",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real-time rates, manual overrides & automatic multi-currency conversion across all statements.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Header: Ambient Hero Summary Banner
            item {
                HigGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = PrimaryBlue.copy(alpha = 0.4f),
                    backlightColor = PrimaryBlue.copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.15f))
                                    .border(1.dp, PrimaryBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CurrencyExchange,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Exchange Rates Hub",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (lastSyncTime.contains("Offline")) WarningAmber else IncomeGreen)
                                    )
                                    Text(
                                        text = lastSyncTime,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.forceSyncCurrencyRates { success, msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            },
                            enabled = !isSyncing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                modifier = Modifier
                                    .size(16.dp)
                                    .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSyncing) "Syncing..." else "Force Sync API",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (hasOverrides) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.resetAllCurrencyRatesToApi()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("All custom rates reset to latest API values.")
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = WarningAmber
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset All",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reset All",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Section Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayCount = currencies.count { !it.code.equals(primaryCurrency, ignoreCase = true) }
                    Text(
                        text = "Supported Currencies vs $primaryCurrency ($displayCount)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Base: $primaryCurrency",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                }
            }

            // Currency Cards (Primary currency excluded: 1 EUR = 1 EUR has no point showing)
            val displayCurrencies = currencies.filter { !it.code.equals(primaryCurrency, ignoreCase = true) }
            items(displayCurrencies, key = { it.code }) { info ->
                val glowColor = remember(info.code) {
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

                val currentRate = remember(currencyVersion, info.code, primaryCurrency) {
                    CurrencyEngine.getRateAgainstBase(info.code, primaryCurrency)
                }
                val apiRate = remember(currencyVersion, info.code, primaryCurrency) {
                    CurrencyEngine.getApiRateAgainstBase(info.code, primaryCurrency)
                }
                val isOverridden = remember(currencyVersion, info.code) { CurrencyEngine.isManualOverride(info.code) }
                val inverseRate = remember(currentRate) {
                    if (currentRate > 0.0) 1.0 / currentRate else 0.0
                }

                HigGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backlightColor = glowColor.copy(alpha = if (isOverridden) 0.45f else 0.28f),
                    borderColor = if (isOverridden) WarningAmber.copy(alpha = 0.6f) else glowColor.copy(alpha = 0.35f)
                ) {
                    // Header Row: Flag + Code + Name + Status Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Circular flag badge
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(glowColor.copy(alpha = 0.15f))
                                    .border(1.dp, glowColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = info.flag,
                                    fontSize = 20.sp
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = info.code,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "(${info.symbol})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = glowColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = info.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Badges (Custom Override)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isOverridden) {
                                Surface(
                                    color = WarningAmber.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Custom",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WarningAmber
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Conversion Metrics: Dynamic against primaryCurrency
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "RATE VS $primaryCurrency",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "1 $primaryCurrency = ${formatRateValue(currentRate)} ${info.code}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "INVERSE EQUIVALENT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "1 ${info.code} = ${CurrencyEngine.format(inverseRate, primaryCurrency)}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Comparison if overridden
                    if (isOverridden) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Latest API Rate: 1 $primaryCurrency = ${formatRateValue(apiRate)} ${info.code}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Card Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOverridden) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.resetCurrencyRateToApi(info.code, primaryCurrency)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Reset ${info.code} rate to API value (${formatRateValue(apiRate)}).")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset to API",
                                    modifier = Modifier.size(16.dp),
                                    tint = WarningAmber
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reset to API",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WarningAmber
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        FilledTonalButton(
                            onClick = {
                                editingCurrency = info
                                editRateInput = formatRateValue(currentRate)
                                editError = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Edit Rate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal BottomSheet for Editing Rate relative to primaryCurrency
    if (editingCurrency != null) {
        val target = editingCurrency!!
        val parsedInput = editRateInput.toDoubleOrNull()
        val calculatedInverse = remember(editRateInput) {
            if (parsedInput != null && parsedInput > 0) {
                1.0 / parsedInput
            } else null
        }

        ModalBottomSheet(
            onDismissRequest = { editingCurrency = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 24.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = target.flag,
                        fontSize = 28.sp
                    )
                    Column {
                        Text(
                            text = "Edit ${target.name} (${target.code}) Rate",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Custom exchange rate relative to 1 $primaryCurrency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = editRateInput,
                    onValueChange = {
                        editRateInput = it
                        editError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("1 $primaryCurrency in ${target.code}") },
                    prefix = {
                        Text(
                            text = "1 $primaryCurrency = ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    suffix = {
                        Text(
                            text = target.code,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = editError != null,
                    supportingText = {
                        if (editError != null) {
                            Text(text = editError!!, color = MaterialTheme.colorScheme.error)
                        } else if (calculatedInverse != null) {
                            Text(
                                text = "Inverse: 1 ${target.code} ≈ ${CurrencyEngine.format(calculatedInverse, primaryCurrency)}",
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { editingCurrency = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val rateVal = editRateInput.trim().toDoubleOrNull()
                            if (rateVal == null || rateVal <= 0.0) {
                                editError = "Enter a valid positive conversion rate"
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateManualCurrencyRate(target.code, rateVal, primaryCurrency)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Updated custom exchange rate for ${target.code}.")
                                }
                                editingCurrency = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Save Rate", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun formatRateValue(rate: Double): String {
    return if (rate >= 100) {
        String.format(java.util.Locale.US, "%.2f", rate)
    } else if (rate >= 1) {
        String.format(java.util.Locale.US, "%.4f", rate)
    } else {
        String.format(java.util.Locale.US, "%.6f", rate)
    }
}
