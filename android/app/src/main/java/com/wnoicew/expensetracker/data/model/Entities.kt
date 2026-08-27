package com.wnoicew.expensetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val description: String,
    val amount: Double,
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "Uncategorized",
    val accountId: String = "",
    val accountName: String = "",
    val note: String = "",
    val referenceNo: String = "",
    val paymentMode: String = "Online",
    val sourceFile: String = "Manual Entry",
    val rawNarration: String = "",
    val isDuplicate: Boolean = false,
    val duplicateWithId: String? = null,
    val duplicateStatus: String = "none", // "none", "pending_review", "merged", "merged_primary", "dismissed"
    val duplicateConfidence: Int = 0,
    val duplicateReason: String = "",
    val needsReview: Boolean = false,
    val confidence: String = "high" // "high", "learned", "low"
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String = "Bank Account", // "Bank Account", "Credit Card", "Digital Wallet", "Savings"
    val balance: Double = 0.0,
    val creditLimit: Double = 0.0,
    val gradientIndex: Int = 0,
    val lastFour: String = "",
    val bankName: String = ""
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val categoryName: String,
    val monthlyBudget: Double = 5000.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey
    val id: String = "rule_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(4),
    val pattern: String,
    val category: String,
    val type: TransactionType = TransactionType.EXPENSE,
    val createdAt: Long = System.currentTimeMillis()
)

// UI & Analytics Presentation Models
data class CategoryBudgetStatus(
    val categoryName: String,
    val monthlyBudget: Double,
    val spent: Double,
    val remaining: Double,
    val percentage: Int,
    val isExceeded: Boolean
)

data class DuplicatePair(
    val primaryTxn: TransactionEntity,
    val candidateTxn: TransactionEntity,
    val confidence: Int,
    val reason: String
)

data class StatementParseResult(
    val detectedProfile: String,
    val transactions: List<TransactionEntity>,
    val totalInflow: Double,
    val totalOutflow: Double
)
