package com.wnoicew.expensetracker

import com.wnoicew.expensetracker.data.PRESET_GRADIENTS
import com.wnoicew.expensetracker.data.UserProfile
import com.wnoicew.expensetracker.data.model.AccountEntity
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class LogicTests {

    @Test
    fun testUserProfileCreationAndInitials() {
        val profile = UserProfile(
            id = UUID.randomUUID().toString(),
            name = "Sarah Jenkins",
            initial = "S",
            gradientColors = PRESET_GRADIENTS[0]
        )
        assertEquals("Sarah Jenkins", profile.name)
        assertEquals("S", profile.initial)
        assertTrue(profile.gradientColors.isNotEmpty())
    }

    @Test
    fun testTransactionEntityDefaults() {
        val txn = TransactionEntity(
            description = "Starbucks",
            amount = 350.0,
            type = TransactionType.EXPENSE,
            category = "Food & Dining"
        )
        assertEquals("Starbucks", txn.description)
        assertEquals(350.0, txn.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, txn.type)
        assertEquals("Food & Dining", txn.category)
        assertTrue(txn.date > 0)
    }

    @Test
    fun testFinancialKPIsCalculation() {
        val now = System.currentTimeMillis()
        val txns = listOf(
            TransactionEntity(description = "Salary", amount = 100000.0, type = TransactionType.INCOME, category = "Salary", date = now),
            TransactionEntity(description = "Rent", amount = 25000.0, type = TransactionType.EXPENSE, category = "Bills & Utilities", date = now),
            TransactionEntity(description = "Groceries", amount = 5000.0, type = TransactionType.EXPENSE, category = "Groceries", date = now)
        )

        val totalInflow = txns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalOutflow = txns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val savingsRate = if (totalInflow > 0) {
            val saved = totalInflow - totalOutflow
            ((saved / totalInflow) * 100).coerceAtLeast(0.0)
        } else 0.0

        assertEquals(100000.0, totalInflow, 0.001)
        assertEquals(30000.0, totalOutflow, 0.001)
        assertEquals(70.0, savingsRate, 0.001)
    }

    @Test
    fun testNetWorthCalculationAcrossAccounts() {
        val accounts = listOf(
            AccountEntity(name = "HDFC Bank", type = "Bank Account", balance = 50000.0),
            AccountEntity(name = "SBI Savings", type = "Savings", balance = 75000.0),
            AccountEntity(name = "Amex Platinum", type = "Credit Card", balance = -12000.0)
        )

        val netWorth = accounts.sumOf { it.balance }
        assertEquals(113000.0, netWorth, 0.001)
    }

    @Test
    fun testZeroInflowSavingsRateSafety() {
        val totalInflow = 0.0
        val totalOutflow = 5000.0
        val savingsRate = if (totalInflow > 0) {
            val saved = totalInflow - totalOutflow
            ((saved / totalInflow) * 100).coerceAtLeast(0.0)
        } else 0.0

        assertEquals(0.0, savingsRate, 0.001)
    }
}
