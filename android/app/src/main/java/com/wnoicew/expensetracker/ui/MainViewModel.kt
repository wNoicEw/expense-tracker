package com.wnoicew.expensetracker.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wnoicew.expensetracker.data.ProfileManager
import com.wnoicew.expensetracker.data.UserProfile
import com.wnoicew.expensetracker.data.db.ExpenseTrackerDatabase
import com.wnoicew.expensetracker.data.engine.CategorizerEngine
import com.wnoicew.expensetracker.data.engine.DuplicateDetectorEngine
import com.wnoicew.expensetracker.data.engine.ExportEngine
import com.wnoicew.expensetracker.data.engine.StatementParserEngine
import com.wnoicew.expensetracker.data.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

val ALL_CATEGORIES = listOf(
    "Food & Dining",
    "Groceries & Mart",
    "Shopping & E-Comm",
    "Travel & Commute",
    "Bills & Utilities",
    "Subscriptions & OTT",
    "Health & Pharmacy",
    "Investments & SIP",
    "Rent & Housing",
    "Transfers & CC Bill",
    "Salary & Professional",
    "Freelance & Side Hustle",
    "Uncategorized"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val profileManager = ProfileManager(application)

    // Active Profile State
    val activeProfile = profileManager.activeProfile

    // Dark / Light Theme Preference State (Matching Web app's app.toggleTheme())
    private val prefs = application.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE)
    val isDarkMode = mutableStateOf(prefs.getBoolean("is_dark_mode", true)) // default Dark Mode

    fun toggleTheme() {
        val newTheme = !isDarkMode.value
        isDarkMode.value = newTheme
        prefs.edit().putBoolean("is_dark_mode", newTheme).apply()
    }

    // Dynamic Database DAO based on active profile
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeDb: Flow<ExpenseTrackerDatabase?> = snapshotFlow { profileManager.activeProfile.value }
        .map { profile: UserProfile? ->
            if (profile != null) {
                ExpenseTrackerDatabase.getDatabase(application, profile.id)
            } else null
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionEntity>> = activeDb
        .flatMapLatest { db: ExpenseTrackerDatabase? ->
            if (db != null) db.transactionDao().getAllTransactions() else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = activeDb
        .flatMapLatest { db: ExpenseTrackerDatabase? ->
            if (db != null) db.accountDao().getAllAccounts() else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val rules: StateFlow<List<RuleEntity>> = activeDb
        .flatMapLatest { db: ExpenseTrackerDatabase? ->
            if (db != null) db.ruleDao().getAllRules() else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val statementUploads: StateFlow<List<StatementUploadEntity>> = activeDb
        .flatMapLatest { db: ExpenseTrackerDatabase? ->
            if (db != null) db.statementUploadDao().getAllUploads() else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Needs Review Unclassified / Low Confidence expenses
    val needsReviewTransactions: StateFlow<List<TransactionEntity>> = transactions.map { list ->
        list.filter { it.needsReview || it.category == "Uncategorized" || it.duplicateStatus == "pending_review" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val needsReviewCount: StateFlow<Int> = needsReviewTransactions.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Financial KPI calculations
    val totalNetWorth = combine(accounts, transactions) { accs, _ ->
        accs.sumOf { it.balance }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalInflow30D = transactions.map { list ->
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        list.filter { it.date >= thirtyDaysAgo && it.type == TransactionType.INCOME && it.duplicateStatus != "merged" }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalOutflow30D = transactions.map { list ->
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        list.filter { it.date >= thirtyDaysAgo && it.type == TransactionType.EXPENSE && it.duplicateStatus != "merged" }
            .sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netSavingsRate = combine(totalInflow30D, totalOutflow30D) { inflow, outflow ->
        if (inflow > 0) {
            val saved = inflow - outflow
            ((saved / inflow) * 100).coerceAtLeast(0.0)
        } else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Expense by Category Breakdown (matching web app donut breakdown)
    val categoryBreakdown: StateFlow<List<CategoryBreakdownItem>> = transactions.map { txns ->
        val expenseTxns = txns.filter { it.type == TransactionType.EXPENSE && it.duplicateStatus != "merged" }
        val totalExpense = expenseTxns.sumOf { it.amount }.coerceAtLeast(1.0)

        expenseTxns.groupBy { it.category }
            .map { (cat, list) ->
                val amount = list.sumOf { it.amount }
                val pct = ((amount / totalExpense) * 100).toInt()
                CategoryBreakdownItem(
                    categoryName = cat,
                    amount = amount,
                    percentage = pct,
                    transactionCount = list.size
                )
            }.sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Detected Duplicates
    val duplicatePairs = mutableStateListOf<DuplicatePair>()

    init {
        viewModelScope.launch {
            transactions.collect { list ->
                val detected = DuplicateDetectorEngine.scanDuplicates(list)
                duplicatePairs.clear()
                duplicatePairs.addAll(detected)
            }
        }
    }

    fun rescanDuplicates() {
        val detected = DuplicateDetectorEngine.scanDuplicates(transactions.value)
        duplicatePairs.clear()
        duplicatePairs.addAll(detected)
    }

    // ==========================================
    // ACTIONS
    // ==========================================

    fun addTransaction(
        description: String,
        amount: Double,
        type: TransactionType,
        category: String,
        accountId: String = "",
        accountName: String = "",
        paymentMode: String = "Online",
        notes: String = ""
    ) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            val customRules = db.ruleDao().getAllRulesSnapshot()
            val catResult = CategorizerEngine.categorize(description, amount, customRules)

            val finalCat = if (category == "Uncategorized" || category.isBlank()) catResult.category else category
            val finalType = if (type == TransactionType.EXPENSE && catResult.type == TransactionType.INCOME && category.isBlank()) {
                TransactionType.INCOME
            } else type

            db.transactionDao().insertTransaction(
                TransactionEntity(
                    description = description.trim(),
                    amount = amount,
                    type = finalType,
                    category = finalCat,
                    accountId = accountId,
                    accountName = accountName,
                    paymentMode = paymentMode,
                    note = notes,
                    rawNarration = description,
                    needsReview = finalCat == "Uncategorized"
                )
            )
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.transactionDao().updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.transactionDao().deleteTransaction(transaction)
        }
    }

    // Accounts
    fun addAccount(name: String, type: String, balance: Double, limit: Double, gradientIndex: Int, lastFour: String, bankName: String = "") {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.accountDao().insertAccount(
                AccountEntity(
                    name = name.trim(),
                    type = type,
                    balance = balance,
                    creditLimit = limit,
                    gradientIndex = gradientIndex,
                    lastFour = lastFour.trim(),
                    bankName = bankName.trim()
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

    // Smart Rule Engine & Retroactive Reclassification
    fun learnRuleAndReclassify(pattern: String, category: String, type: TransactionType) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            val cleanPattern = pattern.trim().lowercase()
            if (cleanPattern.isBlank()) return@launch

            // 1. Insert Rule
            db.ruleDao().insertRule(RuleEntity(pattern = cleanPattern, category = category, type = type))

            // 2. Retroactively update all matching transactions
            val allTxns = db.transactionDao().getAllTransactionsSnapshot()
            val updated = mutableListOf<TransactionEntity>()

            for (t in allTxns) {
                val narr = (t.rawNarration + " " + t.description).lowercase()
                if (narr.contains(cleanPattern)) {
                    updated.add(
                        t.copy(
                            category = category,
                            type = type,
                            needsReview = false,
                            confidence = "learned"
                        )
                    )
                }
            }

            if (updated.isNotEmpty()) {
                db.transactionDao().updateTransactions(updated)
            }
        }
    }

    fun deleteRule(rule: RuleEntity) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.ruleDao().deleteRule(rule)
        }
    }

    // Duplicate Actions
    fun mergeDuplicatePair(pair: DuplicatePair) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            val (p, d) = DuplicateDetectorEngine.mergeTransactions(pair.primaryTxn, pair.candidateTxn)
            db.transactionDao().updateTransaction(p)
            db.transactionDao().updateTransaction(d)
            duplicatePairs.remove(pair)
        }
    }

    fun markDuplicateAsSeparate(pair: DuplicatePair) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            val (t1, t2) = DuplicateDetectorEngine.markSeparate(pair.primaryTxn, pair.candidateTxn)
            db.transactionDao().updateTransaction(t1)
            db.transactionDao().updateTransaction(t2)
            duplicatePairs.remove(pair)
        }
    }

    // Statement CSV Parser & Batch Commit
    fun parseCsvStatement(lines: List<String>, fileName: String, accountId: String = "", accountName: String = ""): StatementParseResult {
        val profile = activeProfile.value ?: return StatementParseResult("Unknown", emptyList(), 0.0, 0.0)
        val customRules = rules.value
        return StatementParserEngine.parseCsvLines(lines, fileName, accountId, accountName, customRules)
    }

    fun commitBatchTransactions(fileName: String, detectedBank: String, transactionsToInsert: List<TransactionEntity>) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.transactionDao().insertTransactions(transactionsToInsert)
            db.statementUploadDao().insertUpload(
                StatementUploadEntity(
                    fileName = fileName,
                    detectedBank = detectedBank,
                    transactionCount = transactionsToInsert.size
                )
            )
        }
    }

    // Export & Backup
    suspend fun exportCsvString(): String {
        val txns = transactions.value
        val accs = accounts.value
        return ExportEngine.generateCsv(txns, accs)
    }

    suspend fun exportJsonBackupString(): String {
        val profile = activeProfile.value ?: return "{}"
        val txns = transactions.value
        val accs = accounts.value
        val r = rules.value
        return ExportEngine.generateJsonBackup(profile.name, txns, accs, r)
    }

    fun restoreJsonBackup(jsonStr: String, onComplete: (Boolean, String) -> Unit) {
        val profile = activeProfile.value ?: run {
            onComplete(false, "No active profile selected")
            return
        }
        viewModelScope.launch {
            try {
                val data = ExportEngine.parseJsonBackup(jsonStr)
                val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
                if (data.transactions.isNotEmpty()) db.transactionDao().insertTransactions(data.transactions)
                if (data.accounts.isNotEmpty()) db.accountDao().insertAccounts(data.accounts)
                if (data.rules.isNotEmpty()) db.ruleDao().insertRules(data.rules)
                onComplete(true, "Restored ${data.transactions.size} transactions, ${data.accounts.size} accounts, and ${data.rules.size} rules.")
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Failed to parse backup JSON")
            }
        }
    }
}
