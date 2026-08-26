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
    val isDuplicate: Boolean = false,
    val needsReview: Boolean = false
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String = "Bank Account", // "Bank Account", "Credit Card", "Wallet", "Savings"
    val balance: Double = 0.0,
    val creditLimit: Double = 0.0,
    val gradientIndex: Int = 0,
    val lastFour: String = ""
)
