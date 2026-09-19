package com.wnoicew.expensetracker.ui.components

import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle

data class DayTransactionSummary(
    val date: LocalDate,
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val count: Int = 0,
    val transactions: List<TransactionEntity> = emptyList()
)

data class MonthAggregate(
    val days: Map<Int, DayTransactionSummary>,
    val inflow: Double,
    val outflow: Double
) {
    val net: Double get() = inflow - outflow
}

object CalendarAggregator {

    /** [month] is 1..12. Transfers are counted per day but excluded from income/expense totals. */
    fun aggregate(
        transactions: List<TransactionEntity>,
        year: Int,
        month: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        targetCurrency: String = "INR"
    ): MonthAggregate {
        val target = YearMonth.of(year, month)
        val byDay = sortedMapOf<Int, MutableList<TransactionEntity>>()
        var inflow = 0.0
        var outflow = 0.0

        for (t in transactions) {
            if (t.duplicateStatus == "merged") continue
            val d = Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate()
            if (YearMonth.from(d) != target) continue
            byDay.getOrPut(d.dayOfMonth) { mutableListOf() }.add(t)
            val converted = CurrencyEngine.convert(t.amount, t.currency, targetCurrency)
            when (t.type) {
                TransactionType.INCOME -> inflow += converted
                TransactionType.REFUND -> inflow += converted
                TransactionType.EXPENSE -> outflow += converted
                TransactionType.TRANSFER -> {}
            }
        }

        val days = byDay.mapValues { (day, list) ->
            DayTransactionSummary(
                date = target.atDay(day),
                income = list.filter { it.type == TransactionType.INCOME || it.type == TransactionType.REFUND }.sumOf { CurrencyEngine.convert(it.amount, it.currency, targetCurrency) },
                expense = list.filter { it.type == TransactionType.EXPENSE }.sumOf { CurrencyEngine.convert(it.amount, it.currency, targetCurrency) },
                count = list.size,
                transactions = list
            )
        }
        return MonthAggregate(days, inflow, outflow)
    }

    /** Keeps the day-of-month when possible so stepping months never leaves the ledger on a date outside the grid. */
    fun clampToMonth(previous: LocalDate?, year: Int, month: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        val day = (previous?.dayOfMonth ?: 1).coerceAtMost(ym.lengthOfMonth())
        return ym.atDay(day)
    }

    fun dateWithCurrentTime(date: LocalDate, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val time: LocalTime = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime()
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    /** 0..6 with Sunday first, the number of blank cells before day 1. */
    fun leadingBlankCells(year: Int, month: Int): Int =
        YearMonth.of(year, month).atDay(1).dayOfWeek.value % 7

    fun describeDay(
        date: LocalDate,
        summary: DayTransactionSummary?,
        isToday: Boolean,
        isSelected: Boolean,
        locale: java.util.Locale = java.util.Locale.getDefault()
    ): String {
        val parts = mutableListOf("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, locale)}")
        if (isToday) parts += "today"
        if (summary == null || summary.count == 0) {
            parts += "no transactions"
        } else {
            fun label(type: TransactionType, singular: String, plural: String) {
                val n = summary.transactions.count { it.type == type }
                if (n > 0) parts += "$n ${if (n == 1) singular else plural}"
            }
            label(TransactionType.EXPENSE, "expense", "expenses")
            label(TransactionType.INCOME, "income", "income")
            label(TransactionType.TRANSFER, "transfer", "transfers")
            label(TransactionType.REFUND, "refund", "refunds")
        }
        if (isSelected) parts += "selected"
        return parts.joinToString(", ")
    }

    fun compactAmount(amount: Double): String {
        val a = kotlin.math.abs(amount)
        fun trim(v: Double): String {
            val s = String.format(java.util.Locale.ROOT, "%.1f", v)
            return if (s.endsWith(".0")) s.dropLast(2) else s
        }
        return when {
            a >= 10_000_000 -> trim(a / 10_000_000) + "Cr"
            a >= 100_000 -> trim(a / 100_000) + "L"
            a >= 1_000 -> trim(a / 1_000) + "k"
            else -> kotlin.math.round(a).toLong().toString()
        }
    }
}
