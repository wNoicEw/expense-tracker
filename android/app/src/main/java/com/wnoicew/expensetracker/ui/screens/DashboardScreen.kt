package com.wnoicew.expensetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.model.AccountEntity
import com.wnoicew.expensetracker.data.model.CategoryBreakdownItem
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.components.HigInsetGroup
import com.wnoicew.expensetracker.ui.components.HigSegmentedControl
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.TransferViolet
import com.wnoicew.expensetracker.ui.theme.AccentCyan
import com.wnoicew.expensetracker.ui.theme.WarningAmber
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

val CategoryChartColors = listOf(
    Color(0xFFF59E0B), // Amber (Food & Dining)
    Color(0xFF10B981), // Emerald (Groceries)
    Color(0xFFEC4899), // Pink (Shopping)
    Color(0xFF06B6D4), // Cyan (Travel)
    Color(0xFF8B5CF6), // Purple (Bills & Utilities)
    Color(0xFFEF4444), // Red (Health)
    Color(0xFF14B8A6), // Teal (Investments)
    Color(0xFF3B82F6), // Blue (Rent)
    Color(0xFF6366F1), // Indigo (Subscriptions)
    Color(0xFF84CC16), // Lime (Transfers)
    Color(0xFF64748B)  // Slate (Uncategorized / Other)
)

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onOpenProfileManager: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onOpenAddTransaction: () -> Unit
) {
    val activeProfile by viewModel.activeProfile
    val isDarkMode by viewModel.isDarkMode
    val netWorth by viewModel.totalNetWorth.collectAsState()
    val inflow by viewModel.totalInflow30D.collectAsState()
    val outflow by viewModel.totalOutflow30D.collectAsState()
    val savingsRate by viewModel.netSavingsRate.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val needsReviewCount by viewModel.needsReviewCount.collectAsState()

    var chartModeIndex by remember { mutableIntStateOf(0) } // 0: Cumulative, 1: Unified
    var chartRangeIndex by remember { mutableIntStateOf(1) } // 0: 7D, 1: 30D, 2: 90D

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header Bar with Profile Pill, Dark/Light Mode Toggle, and Quick Add
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Financial Dashboard",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Private offline financial intelligence",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dark / Light Mode Toggle Button (Matching Web app #btnThemeToggle)
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightlightRound,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkMode) WarningAmber else PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Profile Switcher Pill
                    activeProfile?.let { profile ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.clickable(onClick = onOpenProfileManager)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(profile.toBrush()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profile.initial,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = profile.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Interactive Review Banner (Appears when undetected expenses exist)
        if (needsReviewCount > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = WarningAmber.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(WarningAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "$needsReviewCount Undetected Expenses Need Classification",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Classify once, and Money Tracker will memorize the UPI ID / Account!",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToReview,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Review & Teach AI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }
        }

        // 3. Bento Hero Net Worth Card
        item {
            HigGlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "LIVE RECONCILED PORTFOLIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(onClick = onNavigateToAccounts, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Accounts",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "TOTAL NET WORTH / BALANCE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = currencyFormat.format(netWorth),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Inflow vs Outflow Mini Flow Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Inflow: ${currencyFormat.format(inflow)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IncomeGreen
                    )
                    Text(
                        text = "Outflow: ${currencyFormat.format(outflow)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ExpenseRose
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val totalFlow = (inflow + outflow).coerceAtLeast(1.0)
                val inflowRatio = (inflow / totalFlow).toFloat().coerceIn(0.05f, 0.95f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(inflowRatio)
                            .fillMaxHeight()
                            .background(IncomeGreen)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f - inflowRatio)
                            .fillMaxHeight()
                            .background(ExpenseRose)
                    )
                }
            }
        }

        // 4. 3 Quick KPI Stat Tiles (Matching Web App bento-stats-column)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiStatTile(
                    title = "TOTAL INFLOW (30D)",
                    subtitle = "Salary & Credits",
                    value = currencyFormat.format(inflow),
                    color = IncomeGreen,
                    icon = Icons.Default.ArrowDownward,
                    modifier = Modifier.weight(1f)
                )
                KpiStatTile(
                    title = "TOTAL OUTFLOW (30D)",
                    subtitle = "Excl. Duplicates",
                    value = currencyFormat.format(outflow),
                    color = ExpenseRose,
                    icon = Icons.Default.ArrowUpward,
                    modifier = Modifier.weight(1f)
                )
                KpiStatTile(
                    title = "NET SAVINGS RATE",
                    subtitle = "Of Cashflow Saved",
                    value = "${savingsRate.toInt()}%",
                    color = AccentCyan,
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 5. Cash Flow Chart Section (Cumulative Flow vs Unified Flow, 7D/30D/90D)
        item {
            HigGlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Cash Flow",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (inflow >= outflow) IncomeGreen.copy(alpha = 0.15f) else ExpenseRose.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (inflow >= outflow) "+${currencyFormat.format(inflow - outflow)}" else "-${currencyFormat.format(outflow - inflow)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (inflow >= outflow) IncomeGreen else ExpenseRose,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chart Mode Segmented Control (Cumulative Flow vs Unified Flow)
                HigSegmentedControl(
                    items = listOf("Cumulative Flow", "Unified Flow"),
                    selectedIndex = chartModeIndex,
                    onItemSelected = { chartModeIndex = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Range Buttons (7D, 30D, 90D)
                HigSegmentedControl(
                    items = listOf("7D", "30D", "90D"),
                    selectedIndex = chartRangeIndex,
                    onItemSelected = { chartRangeIndex = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                CashflowCanvas(
                    isCumulative = chartModeIndex == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        }

        // 6. Expense Breakdown by Category (Donut Pie Chart & Category List - Matching Web App #chartCategoryDonut)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Expense Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        item {
            HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                val totalExpense = categoryBreakdown.sumOf { it.amount }

                // Category Donut / Pie Chart (72% cutout matching web app)
                CategoryDonutChart(
                    items = categoryBreakdown,
                    totalExpense = totalExpense,
                    currencyFormat = currencyFormat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (categoryBreakdown.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No expenses recorded yet",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Category donut chart will populate as you add expenses.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        categoryBreakdown.forEachIndexed { index, cat ->
                            val color = CategoryChartColors[index % CategoryChartColors.size]
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Text(
                                            text = cat.categoryName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${currencyFormat.format(cat.amount)} (${cat.percentage}%)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val progress = (cat.percentage / 100f).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = color,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Mini Connected Accounts Snapshot
        if (accounts.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Accounts & Cards",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToAccounts) {
                        Text("Manage", color = PrimaryBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            item {
                HigInsetGroup {
                    accounts.take(3).forEachIndexed { index, acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector = if (acc.type.contains("Credit", ignoreCase = true)) Icons.Default.CreditCard else Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(text = acc.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text(text = acc.type, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Text(
                                text = currencyFormat.format(acc.balance),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (acc.balance >= 0) IncomeGreen else ExpenseRose
                            )
                        }
                        if (index < accounts.take(3).size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }

        // 8. Recent Transactions Inset Group
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                TextButton(onClick = onNavigateToTransactions) {
                    Text("View All Ledger", color = PrimaryBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        if (transactions.isEmpty()) {
            item {
                HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transactions yet",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Upload bank statements or tap Add Transaction.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                HigInsetGroup {
                    transactions.take(5).forEachIndexed { index, txn ->
                        TransactionRowItem(
                            transaction = txn,
                            currencyFormat = currencyFormat,
                            showDivider = index < transactions.take(5).size - 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    items: List<CategoryBreakdownItem>,
    totalExpense: Double,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 26.dp.toPx()
            val diameter = (size.minDimension - strokeWidth * 1.5f).coerceAtLeast(10f)
            val radius = diameter / 2
            val centerOffset = Offset(size.width / 2, size.height / 2)
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)

            if (items.isEmpty() || totalExpense <= 0) {
                drawArc(
                    color = Color.Gray.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                var currentStartAngle = -90f
                items.forEachIndexed { index, cat ->
                    val sweepAngle = ((cat.amount / totalExpense) * 360f).toFloat()
                    val color = CategoryChartColors[index % CategoryChartColors.size]

                    drawArc(
                        color = color,
                        startAngle = currentStartAngle,
                        sweepAngle = (sweepAngle - 2.5f).coerceAtLeast(1.5f), // subtle gap between slices
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    currentStartAngle += sweepAngle
                }
            }
        }

        // Center Donut Display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TOTAL EXPENSE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currencyFormat.format(totalExpense),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun KpiStatTile(
    title: String,
    subtitle: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )

            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TransactionRowItem(
    transaction: TransactionEntity,
    currencyFormat: NumberFormat,
    showDivider: Boolean = true
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val amountColor = if (isIncome) IncomeGreen else if (isTransfer) TransferViolet else ExpenseRose
    val prefix = if (isIncome) "+" else if (isTransfer) "" else "-"

    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(amountColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isIncome) Icons.Default.ArrowDownward else if (isTransfer) Icons.Default.SyncAlt else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = transaction.description,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (transaction.needsReview) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = WarningAmber.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "REVIEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${dateFormat.format(Date(transaction.date))} · ${transaction.category} · ${transaction.paymentMode}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "$prefix${currencyFormat.format(transaction.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = amountColor
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun CashflowCanvas(
    isCumulative: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = PrimaryBlue
    val gradientFill = Brush.verticalGradient(
        listOf(PrimaryBlue.copy(alpha = 0.35f), Color.Transparent)
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val points = if (isCumulative) {
            listOf(
                Offset(0f, height * 0.85f),
                Offset(width * 0.2f, height * 0.7f),
                Offset(width * 0.4f, height * 0.6f),
                Offset(width * 0.6f, height * 0.45f),
                Offset(width * 0.8f, height * 0.35f),
                Offset(width, height * 0.2f)
            )
        } else {
            listOf(
                Offset(0f, height * 0.7f),
                Offset(width * 0.2f, height * 0.3f),
                Offset(width * 0.4f, height * 0.8f),
                Offset(width * 0.6f, height * 0.35f),
                Offset(width * 0.8f, height * 0.6f),
                Offset(width, height * 0.25f)
            )
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val midX = (p0.x + p1.x) / 2
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(fillPath, brush = gradientFill)
        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
    }
}
