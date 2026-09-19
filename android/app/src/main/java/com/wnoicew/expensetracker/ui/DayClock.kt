package com.wnoicew.expensetracker.ui

import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.ZoneId

object DayClock {
    // Polling rather than one delay-until-midnight: delay() is uptime-based on Android and would
    // overshoot midnight after the device has been asleep.
    fun today(zone: ZoneId = ZoneId.systemDefault()): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(zone))
            delay(30_000)
        }
    }.distinctUntilChanged()
}

enum class KpiRange(val label: String, val days: Int) {
    D7("7D", 7),
    D30("30D", 30),
    M3("3M", 90),
    M6("6M", 180),
    Y1("1Y", 365),
    ALL("ALL", -1)
}

data class KpiTotals(val inflow: Double, val outflow: Double) {
    val savingsRatePercent: Double
        get() = if (inflow > 0) (((inflow - outflow) / inflow) * 100).coerceAtLeast(0.0) else 0.0
}

object KpiMath {
    /** Window is the last [days] local calendar days including [today], matching the dashboard graph buckets. */
    fun cutoffMillis(range: KpiRange, today: LocalDate, zone: ZoneId): Long =
        if (range.days <= 0) Long.MIN_VALUE
        else today.minusDays(range.days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()

    fun totals(
        transactions: List<TransactionEntity>,
        range: KpiRange,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        targetCurrency: String = "INR"
    ): KpiTotals {
        val cutoff = cutoffMillis(range, today, zone)
        var inflow = 0.0
        var outflow = 0.0
        for (t in transactions) {
            if (t.duplicateStatus == "merged" || t.date < cutoff) continue
            val convertedAmount = CurrencyEngine.convert(t.amount, t.currency, targetCurrency)
            when (t.type) {
                TransactionType.INCOME -> inflow += convertedAmount
                TransactionType.REFUND -> inflow += convertedAmount
                TransactionType.EXPENSE -> outflow += convertedAmount
                TransactionType.TRANSFER -> {}
            }
        }
        return KpiTotals(inflow, outflow)
    }
}
