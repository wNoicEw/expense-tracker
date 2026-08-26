package com.wnoicew.expensetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wnoicew.expensetracker.data.ProfileManager
import com.wnoicew.expensetracker.data.UserProfile
import com.wnoicew.expensetracker.data.db.ExpenseTrackerDatabase
import com.wnoicew.expensetracker.data.model.AccountEntity
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val profileManager = ProfileManager(application)

    // Active Profile State
    val activeProfile = profileManager.activeProfile

    // Dynamic Database DAO based on active profile
    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeDb = snapshotFlow { profileManager.activeProfile.value }
        .map { profile ->
            if (profile != null) {
                ExpenseTrackerDatabase.getDatabase(application, profile.id)
            } else null
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionEntity>> = activeDb
        .flatMapLatest { db ->
            db?.transactionDao()?.getAllTransactions() ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = activeDb
        .flatMapLatest { db ->
            db?.accountDao()?.getAllAccounts() ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Financial KPI calculations
    val totalNetWorth = combine(accounts, transactions) { accs, _ ->
        accs.sumOf { it.balance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalInflow30D = transactions.map { list ->
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        list.filter { it.date >= thirtyDaysAgo && it.type == TransactionType.INCOME }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalOutflow30D = transactions.map { list ->
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        list.filter { it.date >= thirtyDaysAgo && it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netSavingsRate = combine(totalInflow30D, totalOutflow30D) { inflow, outflow ->
        if (inflow > 0) {
            val saved = inflow - outflow
            ((saved / inflow) * 100).coerceAtLeast(0.0)
        } else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Transaction Actions
    fun addTransaction(
        description: String,
        amount: Double,
        type: TransactionType,
        category: String,
        accountId: String = "",
        accountName: String = ""
    ) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.transactionDao().insertTransaction(
                TransactionEntity(
                    description = description,
                    amount = amount,
                    type = type,
                    category = category,
                    accountId = accountId,
                    accountName = accountName
                )
            )
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.transactionDao().deleteTransaction(transaction)
        }
    }

    // Account Actions
    fun addAccount(name: String, type: String, balance: Double, limit: Double, gradientIndex: Int, lastFour: String) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.accountDao().insertAccount(
                AccountEntity(
                    name = name,
                    type = type,
                    balance = balance,
                    creditLimit = limit,
                    gradientIndex = gradientIndex,
                    lastFour = lastFour
                )
            )
        }
    }

    fun deleteAccount(account: AccountEntity) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.accountDao().deleteAccount(account)
        }
    }
}
