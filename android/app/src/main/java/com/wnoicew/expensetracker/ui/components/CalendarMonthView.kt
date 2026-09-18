package com.wnoicew.expensetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.ui.DayClock
import com.wnoicew.expensetracker.ui.screens.TransactionRowItem
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.PrimaryBlueFill
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month-view calendar with a day-ledger. Selection, month and year survive rotation and process
 * death; the "today" highlight follows the local date rolling over.
 */
@Composable
fun CalendarMonthView(
    transactions: List<TransactionEntity>,
    currencyFormat: NumberFormat,
    onSelectTransaction: (TransactionEntity) -> Unit,
    onEditTransaction: (TransactionEntity) -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onAddTransactionForDate: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = remember { Locale.getDefault() }
    val today by produceState(LocalDate.now()) { DayClock.today().collect { value = it } }

    var currentYear by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var currentMonth by rememberSaveable { mutableIntStateOf(LocalDate.now().monthValue) } // 1..12
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(LocalDate.now().toEpochDay()) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }

    val selectedDate = selectedEpochDay?.let { LocalDate.ofEpochDay(it) }
        ?.takeIf { YearMonth.from(it) == YearMonth.of(currentYear, currentMonth) }

    fun goToMonth(year: Int, month: Int) {
        currentYear = year
        currentMonth = month
        if (selectedEpochDay != null) {
            selectedEpochDay = CalendarAggregator.clampToMonth(selectedDate, year, month).toEpochDay()
        }
    }

    val aggregate = remember(transactions, currentYear, currentMonth) {
        CalendarAggregator.aggregate(transactions, currentYear, currentMonth)
    }

    val leadEmptyCells = remember(currentYear, currentMonth) { CalendarAggregator.leadingBlankCells(currentYear, currentMonth) }
    val daysInMonth = remember(currentYear, currentMonth) { YearMonth.of(currentYear, currentMonth).lengthOfMonth() }
    val monthTitle = "${Month.of(currentMonth).getDisplayName(TextStyle.FULL_STANDALONE, locale)} $currentYear"
    val weekdayHeaders = remember(locale) {
        (0L..6L).map { DayOfWeek.SUNDAY.plus(it).getDisplayName(TextStyle.SHORT, locale) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HigGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "Choose month and year") { showMonthPicker = true }
                        .semantics { contentDescription = "$monthTitle, choose month and year" }
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp)
                            .clearAndSetSemantics {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = monthTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(onClick = {
                        val ym = YearMonth.of(currentYear, currentMonth).minusMonths(1)
                        goToMonth(ym.year, ym.monthValue)
                    }) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextButton(
                        onClick = {
                            currentYear = today.year
                            currentMonth = today.monthValue
                            selectedEpochDay = today.toEpochDay()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Today",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = PrimaryBlue
                        )
                    }

                    IconButton(onClick = {
                        val ym = YearMonth.of(currentYear, currentMonth).plusMonths(1)
                        goToMonth(ym.year, ym.monthValue)
                    }) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next month",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryPill(
                    label = "INFLOW",
                    value = "+${currencyFormat.format(aggregate.inflow)}",
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f)
                )
                SummaryPill(
                    label = "OUTFLOW",
                    value = "-${currencyFormat.format(aggregate.outflow)}",
                    color = ExpenseRose,
                    modifier = Modifier.weight(1f)
                )
                SummaryPill(
                    label = "NET FLOW",
                    value = (if (aggregate.net >= 0) "+" else "-") + currencyFormat.format(kotlin.math.abs(aggregate.net)),
                    color = if (aggregate.net >= 0) IncomeGreen else ExpenseRose,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HigGlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weekdayHeaders.forEach { dayName ->
                    Text(
                        text = dayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val rowCount = (leadEmptyCells + daysInMonth + 6) / 7

            for (rowIndex in 0 until rowCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (colIndex in 0..6) {
                        val dayNum = rowIndex * 7 + colIndex - leadEmptyCells + 1

                        if (dayNum in 1..daysInMonth) {
                            val date = LocalDate.of(currentYear, currentMonth, dayNum)
                            val summary = aggregate.days[dayNum]
                            val isToday = date == today
                            val isSelected = date == selectedDate

                            CalendarDayCell(
                                dayNumber = dayNum,
                                isToday = isToday,
                                isSelected = isSelected,
                                summary = summary,
                                description = CalendarAggregator.describeDay(date, summary, isToday, isSelected, locale),
                                onClick = { selectedEpochDay = date.toEpochDay() },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        selectedDate?.let { date ->
            val daySummary = aggregate.days[date.dayOfMonth]
            val dayTxns = daySummary?.transactions ?: emptyList()
            val prettyDateTitle = remember(date, locale) {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)
            }

            HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prettyDateTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (dayTxns.isNotEmpty()) "${dayTxns.size} transaction${if (dayTxns.size > 1) "s" else ""}" else "No transactions",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onAddTransactionForDate(CalendarAggregator.dateWithCurrentTime(date, System.currentTimeMillis())) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueFill, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Add transaction for $prettyDateTitle" }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = { selectedEpochDay = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close day ledger",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (daySummary != null && daySummary.count > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (daySummary.income > 0) {
                            Text(
                                text = "In: +${currencyFormat.format(daySummary.income)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                        }
                        if (daySummary.expense > 0) {
                            Text(
                                text = "Out: -${currencyFormat.format(daySummary.expense)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRose
                            )
                        }
                        val dayNet = daySummary.income - daySummary.expense
                        Text(
                            text = "Net: " + (if (dayNet >= 0) "+" else "-") + currencyFormat.format(kotlin.math.abs(dayNet)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dayNet >= 0) IncomeGreen else ExpenseRose
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClickLabel = "Show details") { onSelectTransaction(txn) }
                                ) {
                                    TransactionRowItem(
                                        transaction = txn,
                                        currencyFormat = currencyFormat,
                                        showDivider = false
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { onEditTransaction(txn) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .semantics { contentDescription = "Edit ${txn.description}" }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Edit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    TextButton(
                                        onClick = { onDeleteTransaction(txn) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .semantics { contentDescription = "Delete ${txn.description}" }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (index < dayTxns.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYear = currentYear,
            initialMonth = currentMonth,
            locale = locale,
            onDismiss = { showMonthPicker = false },
            onConfirm = { year, month ->
                goToMonth(year, month)
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
    summary: DayTransactionSummary?,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasTxns = (summary?.count ?: 0) > 0
    val selectedBg = PrimaryBlueFill
    val todayTint = PrimaryBlue
    val hasTxnBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val animatedBg by animateColorAsState(
        targetValue = when {
            isSelected -> selectedBg
            isToday -> todayTint.copy(alpha = 0.12f)
            hasTxns -> hasTxnBg
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "cell_bg"
    )

    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val dayColor = when {
        isSelected -> Color.White
        isToday -> todayTint
        else -> onSurface
    }
    val expenseColor = if (isSelected) Color.White else ExpenseRose
    val incomeColor = if (isSelected) Color.White else IncomeGreen

    Box(
        modifier = modifier
            .padding(1.5.dp)
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(animatedBg)
            .then(
                if (isToday && !isSelected) Modifier.border(1.dp, todayTint, RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                selected = isSelected
            }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {}
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayNumber.toString(),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                    color = dayColor,
                    textAlign = TextAlign.Center
                )
                if (summary != null && summary.count > 1) {
                    Text(
                        text = " ·${summary.count}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        color = if (isSelected) Color.White else onSurfaceVariant
                    )
                }
            }

            if (summary != null && summary.expense > 0) {
                DayAmountChip("-₹${CalendarAggregator.compactAmount(summary.expense)}", expenseColor)
            }
            if (summary != null && summary.income > 0) {
                DayAmountChip("+₹${CalendarAggregator.compactAmount(summary.income)}", incomeColor)
            }
        }
    }
}

@Composable
private fun DayAmountChip(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
}

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
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
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

@Composable
private fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonth: Int,
    locale: Locale,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var year by rememberSaveable { mutableIntStateOf(initialYear) }
    var month by rememberSaveable { mutableIntStateOf(initialMonth) } // 1..12

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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { year-- }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year")
                        }
                        Text(
                            text = year.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = PrimaryBlue
                        )
                        IconButton(onClick = { year++ }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next year")
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in 0..3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0..2) {
                                val monthNumber = row * 3 + col + 1
                                val isSelected = monthNumber == month
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) PrimaryBlueFill else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .selectable(selected = isSelected, role = Role.RadioButton) { month = monthNumber }
                                        .semantics { contentDescription = Month.of(monthNumber).getDisplayName(TextStyle.FULL_STANDALONE, locale) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = Month.of(monthNumber).getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.clearAndSetSemantics {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(year, month) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlueFill, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
