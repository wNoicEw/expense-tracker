package com.wnoicew.expensetracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import com.wnoicew.expensetracker.ui.screens.TransactionRowItem
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val WeekDayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

data class DayTransactionSummary(
    val dateStr: String,
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val count: Int = 0,
    val transactions: List<TransactionEntity> = emptyList()
)

/**
 * Apple HIG-compliant Financial Calendar Month-View and Interactive Day-Ledger.
 * Features:
 * - Monthly Financial Header with Inflow, Outflow, Net Cashflow cards
 * - 7-Column month calendar grid with touch targets >= 44dp
 * - Day badges for income, expenses, and transaction counts
 * - Interactive Day Ledger card showing transactions for the selected day
 * - Quick 'Add for this Date' action
 * - Quick Month/Year selector dialog
 */
@Composable
fun CalendarMonthView(
    transactions: List<TransactionEntity>,
    currencyFormat: NumberFormat,
    onSelectTransaction: (TransactionEntity) -> Unit,
    onAddTransactionForDate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayCal = remember { Calendar.getInstance() }
    val todayDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(todayCal.time)
    }

    var currentYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) } // 0..11
    var selectedDateStr by remember { mutableStateOf<String?>(todayDateStr) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val monthPrefix = remember(currentYear, currentMonth) {
        String.format(Locale.ENGLISH, "%04d-%02d", currentYear, currentMonth + 1)
    }

    // Aggregate transactions by date for the viewed month
    val (txnsByDate, monthInflow, monthOutflow) = remember(transactions, monthPrefix) {
        val map = mutableMapOf<String, MutableList<TransactionEntity>>()
        var inflow = 0.0
        var outflow = 0.0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

        transactions.forEach { txn ->
            if (txn.duplicateStatus != "merged") {
                val dStr = sdf.format(Date(txn.date))
                if (dStr.startsWith(monthPrefix)) {
                    map.getOrPut(dStr) { mutableListOf() }.add(txn)
                    when (txn.type) {
                        TransactionType.INCOME -> inflow += txn.amount
                        TransactionType.EXPENSE -> outflow += txn.amount
                        TransactionType.TRANSFER -> {}
                    }
                }
            }
        }

        val summaryMap = map.mapValues { (dStr, list) ->
            var inc = 0.0
            var exp = 0.0
            list.forEach { t ->
                if (t.type == TransactionType.INCOME) inc += t.amount
                else if (t.type == TransactionType.EXPENSE) exp += t.amount
            }
            DayTransactionSummary(
                dateStr = dStr,
                income = inc,
                expense = exp,
                count = list.size,
                transactions = list
            )
        }

        Triple(summaryMap, inflow, outflow)
    }

    val netCashflow = monthInflow - monthOutflow

    // Calendar grid calculations
    val (firstDayOfWeek, daysInMonth) = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val firstDay = cal.get(Calendar.DAY_OF_WEEK) // 1: Sunday, 7: Saturday
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        Pair(firstDay, days)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Monthly Financial Header Card
        HigGlassCard(modifier = Modifier.fillMaxWidth()) {
            // Month navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clickable Month & Year Header (Opens Month Picker)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clickable { showMonthPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${MonthNames[currentMonth]} $currentYear",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Month",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Month Nav Arrows
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear--
                            } else {
                                currentMonth--
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous Month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = {
                            val now = Calendar.getInstance()
                            currentYear = now.get(Calendar.YEAR)
                            currentMonth = now.get(Calendar.MONTH)
                            selectedDateStr = todayDateStr
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "Today",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = PrimaryBlue
                        )
                    }

                    IconButton(
                        onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear++
                            } else {
                                currentMonth++
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next Month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3 Summary Cards/Pills (Inflow, Outflow, Net Cash Flow)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total Inflow
                SummaryPill(
                    label = "TOTAL INFLOW",
                    value = "+${currencyFormat.format(monthInflow)}",
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f)
                )

                // Total Outflow
                SummaryPill(
                    label = "TOTAL OUTFLOW",
                    value = "-${currencyFormat.format(monthOutflow)}",
                    color = ExpenseRose,
                    modifier = Modifier.weight(1f)
                )

                // Net Cash Flow
                SummaryPill(
                    label = "NET CASH FLOW",
                    value = (if (netCashflow >= 0) "+" else "") + currencyFormat.format(netCashflow),
                    color = if (netCashflow >= 0) IncomeGreen else ExpenseRose,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 2. Main 7-Column Calendar Grid Card
        HigGlassCard(modifier = Modifier.fillMaxWidth()) {
            // Weekday Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                WeekDayNames.forEach { dayName ->
                    Text(
                        text = dayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Calculate grid rows
            val leadEmptyCells = firstDayOfWeek - 1
            val totalCells = leadEmptyCells + daysInMonth
            val rowCount = (totalCells + 6) / 7

            for (rowIndex in 0 until rowCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (colIndex in 0..6) {
                        val cellIndex = rowIndex * 7 + colIndex
                        val dayNum = cellIndex - leadEmptyCells + 1

                        if (dayNum in 1..daysInMonth) {
                            val dayDateStr = String.format(
                                Locale.ENGLISH,
                                "%04d-%02d-%02d",
                                currentYear,
                                currentMonth + 1,
                                dayNum
                            )
                            val isToday = (dayDateStr == todayDateStr)
                            val isSelected = (dayDateStr == selectedDateStr)
                            val daySummary = txnsByDate[dayDateStr]

                            CalendarDayCell(
                                dayNumber = dayNum,
                                isToday = isToday,
                                isSelected = isSelected,
                                summary = daySummary,
                                onClick = { selectedDateStr = dayDateStr },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            // Empty spacer cell
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 3. Interactive Day-Ledger Section
        selectedDateStr?.let { dateStr ->
            val daySummary = txnsByDate[dateStr]
            val dayTxns = daySummary?.transactions ?: emptyList()

            val prettyDateTitle = remember(dateStr) {
                try {
                    val d = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(dateStr)
                    if (d != null) {
                        SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(d)
                    } else dateStr
                } catch (e: Exception) {
                    dateStr
                }
            }

            HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = prettyDateTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (dayTxns.isNotEmpty()) "${dayTxns.size} transaction${if (dayTxns.size > 1) "s" else ""}" else "No transactions",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Add for this Date Button
                    Button(
                        onClick = { onAddTransactionForDate(dateStr) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Day totals if transactions exist
                if (daySummary != null && daySummary.count > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (daySummary.income > 0) {
                            Text(
                                text = "In: +${currencyFormat.format(daySummary.income)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }
                        if (daySummary.expense > 0) {
                            Text(
                                text = "Out: -${currencyFormat.format(daySummary.expense)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRose
                            )
                        }
                        val dayNet = daySummary.income - daySummary.expense
                        Text(
                            text = "Net: " + (if (dayNet >= 0) "+" else "") + currencyFormat.format(dayNet),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dayNet >= 0) IncomeGreen else ExpenseRose
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Transaction list or empty state
                if (dayTxns.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No transactions on this day",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    HigInsetGroup(modifier = Modifier.fillMaxWidth()) {
                        dayTxns.forEachIndexed { index, txn ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTransaction(txn) }
                            ) {
                                TransactionRowItem(
                                    transaction = txn,
                                    currencyFormat = currencyFormat,
                                    showDivider = index < dayTxns.size - 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Month & Year Picker Dialog
    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYear = currentYear,
            initialMonth = currentMonth,
            onDismiss = { showMonthPicker = false },
            onConfirm = { year, month ->
                currentYear = year
                currentMonth = month
                showMonthPicker = false
            }
        )
    }
}

/**
 * Individual Day Cell in the 7-column calendar grid.
 * Apple HIG touch target: >= 44dp height.
 */
@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
    summary: DayTransactionSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = when {
            isSelected -> PrimaryBlue
            isToday -> PrimaryBlue.copy(alpha = 0.12f)
            (summary?.count ?: 0) > 0 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "cell_bg"
    )

    val textColor = when {
        isSelected -> Color.White
        isToday -> PrimaryBlue
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .padding(1.5.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(animatedBg)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, PrimaryBlue, RoundedCornerShape(10.dp))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Day Number
            Text(
                text = dayNumber.toString(),
                fontSize = 12.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center
            )

            // Indicators row for transactions
            if (summary != null && summary.count > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    if (summary.expense > 0) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else ExpenseRose)
                        )
                    }
                    if (summary.income > 0) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else IncomeGreen)
                        )
                    }
                    if (summary.count > 2) {
                        Text(
                            text = "${summary.count}",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}

/**
 * Top Summary Pill showing Inflow, Outflow, or Net Cashflow with Apple HIG styling.
 */
@Composable
private fun SummaryPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Sleek Month & Year Picker Dialog in Apple iOS Modal Style.
 */
@Composable
private fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonth) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with Year Stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Month",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = { year-- }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Year")
                        }
                        Text(
                            text = year.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = PrimaryBlue
                        )
                        IconButton(onClick = { year++ }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Year")
                        }
                    }
                }

                // 12-Month Grid (4 rows x 3 cols)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0..2) {
                                val mIdx = row * 3 + col
                                val isSelected = (mIdx == month)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { month = mIdx }
                                ) {
                                    Text(
                                        text = MonthNames[mIdx].take(3),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(year, month) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
