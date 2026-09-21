package com.wnoicew.expensetracker.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wnoicew.expensetracker.data.ProfileManager
import com.wnoicew.expensetracker.data.UserProfile
import com.wnoicew.expensetracker.data.db.ExpenseTrackerDatabase
import com.wnoicew.expensetracker.data.engine.BackupFormatException
import com.wnoicew.expensetracker.data.engine.BackupReminderPolicy
import com.wnoicew.expensetracker.data.engine.CategorizerEngine
import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.engine.DuplicateDetectorEngine
import com.wnoicew.expensetracker.data.engine.ExportEngine
import com.wnoicew.expensetracker.data.engine.StatementParserEngine
import com.wnoicew.expensetracker.data.model.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

enum class ExportKind(val mimeType: String, val extension: String, val baseName: String) {
    CSV("text/csv", "csv", "Money_Tracker_Transactions"),
    JSON("application/json", "json", "MoneyTracker_Profile_Backup");

    fun defaultFileName(today: LocalDate = LocalDate.now()) = "${baseName}_$today.$extension"
}

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

    // Backup Reminder State & Persistence (Matching Web app's shouldShowBackupReminder / dismissBackupReminder)
    private val backupPrefs = application.getSharedPreferences("backup_reminder_prefs", Context.MODE_PRIVATE)
    private val backupStateVersion = MutableStateFlow(0)

    fun dismissBackupReminder() {
        val profileId = activeProfile.value?.id ?: return
        backupPrefs.edit().putLong("backupReminderDismissedUntil_$profileId", System.currentTimeMillis() + BackupReminderPolicy.SNOOZE_MS).apply()
        backupStateVersion.value++
    }

    private fun recordBackupCompleted(profileId: String) {
        backupPrefs.edit().putLong("lastBackupAt_$profileId", System.currentTimeMillis()).apply()
        backupStateVersion.value++
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

    val shouldShowBackupReminder: StateFlow<Boolean> = combine(
        transactions,
        snapshotFlow { profileManager.activeProfile.value },
        backupStateVersion
    ) { txnList, profile, _ ->
        if (profile == null) {
            false
        } else {
            BackupReminderPolicy.shouldShow(
                txnCount = txnList.count { it.duplicateStatus != "merged" },
                lastBackupAt = backupPrefs.getLong("lastBackupAt_${profile.id}", 0L),
                snoozedUntil = backupPrefs.getLong("backupReminderDismissedUntil_${profile.id}", 0L),
                now = System.currentTimeMillis()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<AccountEntity>> = activeDb
        .flatMapLatest { db: ExpenseTrackerDatabase? ->
            if (db != null) db.accountDao().getAllAccounts() else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enriched Accounts with live calculated metrics (balance, spend, dues, transaction count)
    val accountsWithMetrics: StateFlow<List<AccountWithMetrics>> = combine(
        accounts,
        transactions
    ) { accList, txnList ->
        accList.map { acc ->
            // Match transactions by account ID or fallback to account name / bank name
            val accTxns = txnList.filter { t ->
                t.duplicateStatus != "merged" && (
                    (t.accountId.isNotBlank() && t.accountId == acc.id) ||
                    (t.accountId.isBlank() && t.accountName.isNotBlank() && t.accountName.equals(acc.name, ignoreCase = true)) ||
                    (t.accountId.isBlank() && acc.bankName.isNotBlank() && t.accountName.contains(acc.bankName, ignoreCase = true)) ||
                    (t.accountId.isBlank() && acc.bankName.isNotBlank() && t.rawNarration.contains(acc.bankName, ignoreCase = true))
                )
            }

            var income = 0.0
            var expense = 0.0
            var transfersIn = 0.0
            var transfersOut = 0.0

            accTxns.forEach { t ->
                val convertedAmt = if (t.currency.isNotBlank() && !t.currency.equals(acc.currency, ignoreCase = true)) {
                    CurrencyEngine.convert(t.amount, t.currency, acc.currency)
                } else {
                    t.amount
                }
                val amt = kotlin.math.abs(convertedAmt)
                when (t.type) {
                    TransactionType.INCOME -> income += amt
                    TransactionType.REFUND -> income += amt
                    TransactionType.EXPENSE -> expense += amt
                    TransactionType.TRANSFER -> {
                        val isIncoming = t.rawNarration.contains(Regex("""\b(cr|credit|received|deposit)\b""", RegexOption.IGNORE_CASE))
                        if (isIncoming) transfersIn += amt else transfersOut += amt
                    }
                }
            }

            val isCreditCard = acc.type.equals("Credit Card", ignoreCase = true)
            val outstandingDues = if (isCreditCard) {
                maxOf(0.0, expense - (income + transfersIn))
            } else {
                0.0
            }

            val computedBalance = if (isCreditCard) {
                if (outstandingDues > 0) outstandingDues else expense
            } else {
                acc.balance + income - expense - transfersOut + transfersIn
            }

            AccountWithMetrics(
                account = acc,
                computedBalance = computedBalance,
                totalIncome = income,
                totalExpense = expense,
                outstandingDues = outstandingDues,
                transactionCount = accTxns.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // Currency State Version to trigger UI recomposition when rates change
    val currencyStateVersion = mutableStateOf(0)
    val isSyncingCurrency = mutableStateOf(false)

    // Financial KPI calculations
    val totalNetWorth = combine(
        accountsWithMetrics,
        snapshotFlow { profileManager.activeProfile.value },
        snapshotFlow { currencyStateVersion.value }
    ) { list, profile, _ ->
        val targetCur = profile?.currency ?: "INR"
        list.sumOf {
            val net = if (it.account.type.equals("Credit Card", ignoreCase = true)) -it.outstandingDues else it.computedBalance
            CurrencyEngine.convert(net, it.account.currency, targetCur)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val totals30D: Flow<KpiTotals> = combine(
        transactions,
        DayClock.today(),
        snapshotFlow { profileManager.activeProfile.value },
        snapshotFlow { currencyStateVersion.value }
    ) { list, today, profile, _ ->
        val targetCur = profile?.currency ?: "INR"
        KpiMath.totals(list, KpiRange.D30, today, ZoneId.systemDefault(), targetCur)
    }

    val totalInflow30D = totals30D.map { it.inflow }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalOutflow30D = totals30D.map { it.outflow }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Expense by Category Breakdown (matching web app donut breakdown)
    val categoryBreakdown: StateFlow<List<CategoryBreakdownItem>> = combine(
        transactions,
        snapshotFlow { profileManager.activeProfile.value },
        snapshotFlow { currencyStateVersion.value }
    ) { txns, profile, _ ->
        val targetCur = profile?.currency ?: "INR"
        val expenseTxns = txns.filter { it.type == TransactionType.EXPENSE && it.duplicateStatus != "merged" }
        val totalExpense = expenseTxns.sumOf { CurrencyEngine.convert(it.amount, it.currency, targetCur) }.coerceAtLeast(1.0)

        expenseTxns.groupBy { it.category }
            .map { (cat, list) ->
                val amount = list.sumOf { CurrencyEngine.convert(it.amount, it.currency, targetCur) }
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
        CurrencyEngine.init(application)
        viewModelScope.launch(Dispatchers.IO) {
            val updated = CurrencyEngine.checkAndFetchDailyRates(application)
            if (updated) {
                withContext(Dispatchers.Main) {
                    currencyStateVersion.value++
                }
            }
        }

        viewModelScope.launch {
            transactions
                .map { DuplicateDetectorEngine.scanDuplicates(it) }
                .flowOn(Dispatchers.Default)
                .collect { detected ->
                    duplicatePairs.clear()
                    duplicatePairs.addAll(detected)
                }
        }
    }

    fun forceSyncCurrencyRates(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        if (isSyncingCurrency.value) return
        isSyncingCurrency.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val success = CurrencyEngine.forceFetchRates(getApplication())
            withContext(Dispatchers.Main) {
                isSyncingCurrency.value = false
                if (success) {
                    currencyStateVersion.value++
                    onComplete(true, "Exchange rates updated successfully from API!")
                } else {
                    onComplete(false, "Failed to connect to exchange rate API. Check internet connection.")
                }
            }
        }
    }

    fun updateManualCurrencyRate(
        code: String,
        rateAgainstBase: Double,
        baseCurrency: String = activeProfile.value?.currency ?: CurrencyEngine.DEFAULT_CURRENCY
    ) {
        CurrencyEngine.updateManualRateAgainstBase(getApplication(), code, baseCurrency, rateAgainstBase)
        currencyStateVersion.value++
    }

    fun resetCurrencyRateToApi(
        code: String,
        baseCurrency: String = activeProfile.value?.currency ?: CurrencyEngine.DEFAULT_CURRENCY
    ) {
        CurrencyEngine.resetRateToApiAgainstBase(getApplication(), code, baseCurrency)
        currencyStateVersion.value++
    }

    fun resetAllCurrencyRatesToApi() {
        CurrencyEngine.resetAllRatesToApi(getApplication())
        currencyStateVersion.value++
    }

    fun getLastCurrencyFetchTimestamp(): String {
        val dummy = currencyStateVersion.value
        return CurrencyEngine.getLastFetchTimestamp(getApplication())
    }

    fun rescanDuplicates() {
        viewModelScope.launch {
            val detected = withContext(Dispatchers.Default) { DuplicateDetectorEngine.scanDuplicates(transactions.value) }
            duplicatePairs.clear()
            duplicatePairs.addAll(detected)
        }
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
        notes: String = "",
        date: Long = System.currentTimeMillis(),
        currency: String = activeProfile.value?.currency ?: "INR"
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
                    date = date,
                    description = description.trim(),
                    amount = amount,
                    type = finalType,
                    category = finalCat,
                    accountId = accountId,
                    accountName = accountName,
                    paymentMode = paymentMode,
                    note = notes,
                    rawNarration = description,
                    needsReview = finalCat == "Uncategorized",
                    currency = currency
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

    fun deleteTransactions(transactions: List<TransactionEntity>) {
        val profile = activeProfile.value ?: return
        if (transactions.isEmpty()) return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.transactionDao().deleteTransactions(transactions)
        }
    }

    fun deleteTransactionsByIds(ids: List<String>) {
        val profile = activeProfile.value ?: return
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.transactionDao().deleteTransactionsByIds(ids)
        }
    }

    // Accounts
    fun addAccount(
        name: String,
        type: String,
        balance: Double,
        limit: Double,
        gradientIndex: Int,
        lastFour: String,
        bankName: String = "",
        currency: String = activeProfile.value?.currency ?: "INR"
    ) {
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
                    bankName = bankName.trim(),
                    currency = currency
                )
            )
        }
    }

    fun deleteAccount(account: AccountEntity) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            db.withTransaction {
                db.transactionDao().unassignAccountFromTransactions(account.id)
                db.accountDao().deleteAccount(account)
            }
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
            val (p, _) = DuplicateDetectorEngine.mergeTransactions(pair.primaryTxn, pair.candidateTxn)
            db.withTransaction {
                db.transactionDao().updateTransaction(p)
                // Completely delete candidate duplicate from the database
                db.transactionDao().deleteTransaction(pair.candidateTxn)
            }
            duplicatePairs.remove(pair)
        }
    }

    fun markDuplicateAsSeparate(pair: DuplicatePair) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            val (t1, t2) = DuplicateDetectorEngine.markSeparate(pair.primaryTxn, pair.candidateTxn)
            db.withTransaction {
                db.transactionDao().updateTransaction(t1)
                db.transactionDao().updateTransaction(t2)
            }
            duplicatePairs.remove(pair)
        }
    }

    // Statement Parser (PDF & CSV) & Batch Commit
    fun parseStatementStream(
        inputStream: java.io.InputStream,
        fileName: String,
        accountId: String = "",
        accountName: String = "",
        password: String? = null
    ): StatementParseResult {
        if (activeProfile.value == null) throw IllegalStateException("No active profile")
        val customRules = rules.value
        return StatementParserEngine.parseStatementStream(inputStream, fileName, accountId, accountName, customRules, password)
    }

    fun parseCsvStatement(lines: List<String>, fileName: String, accountId: String = "", accountName: String = ""): StatementParseResult {
        if (activeProfile.value == null) return StatementParseResult("Unknown", emptyList(), 0.0, 0.0)
        val customRules = rules.value
        return StatementParserEngine.parseCsvLines(lines, fileName, accountId, accountName, customRules)
    }


    fun commitBatchTransactions(
        fileName: String,
        detectedBank: String,
        accountMetadata: AccountMetadata?,
        transactionsToInsert: List<TransactionEntity>,
        rowAccounts: List<AccountMetadata?> = emptyList()
    ) {
        val profile = activeProfile.value ?: return
        viewModelScope.launch {
            val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
            val existingAccounts = db.accountDao().getAllAccountsSnapshot().toMutableList()

            // Helper function to dynamically resolve or create an account
            // strict: the last 4 digits came from the row itself, so a different account of the same bank must not absorb it
            suspend fun getOrCreateAccount(meta: AccountMetadata, strict: Boolean = false): AccountEntity {
                // 1. Match by last 4 digits and matching type
                var match = existingAccounts.find { a ->
                    a.type.equals(meta.type, ignoreCase = true) &&
                    a.lastFour.isNotBlank() &&
                    meta.lastFour.isNotBlank() &&
                    a.lastFour.equals(meta.lastFour, ignoreCase = true) &&
                    meta.lastFour != "0000" && meta.lastFour != "UPI"
                }

                // 2. Match by exact bank name and type
                if (match == null && !strict) {
                    match = existingAccounts.find { a ->
                        a.bankName.isNotBlank() &&
                        meta.bankName.isNotBlank() &&
                        a.bankName.equals(meta.bankName, ignoreCase = true) &&
                        a.type.equals(meta.type, ignoreCase = true)
                    }
                }

                // 3. Match by name
                if (match == null) {
                    match = existingAccounts.find { a ->
                        a.name.equals(meta.name, ignoreCase = true)
                    }
                }

                if (match != null) {
                    return match
                }

                // Auto-create new Account from Statement metadata
                val newAcc = AccountEntity(
                    id = "acc_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().take(6),
                    name = meta.name,
                    type = meta.type,
                    balance = 0.0,
                    creditLimit = meta.creditLimit,
                    gradientIndex = meta.gradientIndex,
                    lastFour = meta.lastFour,
                    bankName = meta.bankName
                )
                db.accountDao().insertAccount(newAcc)
                existingAccounts.add(newAcc)
                return newAcc
            }

            // File-level account, created only if some row actually falls back to it (a Navi file whose rows all name their own account never needs the wallet)
            var mainAccountCache: AccountEntity? = null
            suspend fun mainAccount(): AccountEntity = mainAccountCache ?: run {
                if (accountMetadata != null) {
                    getOrCreateAccount(accountMetadata)
                } else {
                    existingAccounts.firstOrNull() ?: getOrCreateAccount(
                        AccountMetadata(
                            bankName = detectedBank,
                            type = "Bank Account",
                            lastFour = "0000",
                            name = "$detectedBank Account"
                        )
                    )
                }
            }.also { mainAccountCache = it }

            // Per-row hints are positional; a length mismatch means the list is stale, so ignore it rather than misassign
            val hints = if (rowAccounts.size == transactionsToInsert.size) rowAccounts else emptyList()

            // Link each transaction to the account that paid it: the row's own account (bank / RuPay card), else the file-level one
            val resolvedTransactions = transactionsToInsert.mapIndexed { i, txn ->
                val rowMeta = StatementParserEngine.rowAccountFor(txn, hints.getOrNull(i))
                val targetAcc = if (rowMeta != null) {
                    getOrCreateAccount(rowMeta, strict = rowMeta.type == "Bank Account" && rowMeta.lastFour.length == 4 && rowMeta.lastFour != "0000")
                } else {
                    mainAccount()
                }

                txn.copy(
                    accountId = targetAcc.id,
                    accountName = targetAcc.name
                )
            }

            // Filter exact duplicates against database and current batch
            val existingTxns = db.transactionDao().getAllTransactionsSnapshot()
            val deduplication = DuplicateDetectorEngine.filterExactDuplicates(resolvedTransactions, existingTxns)
            val toInsert = deduplication.filteredTransactions

            if (toInsert.isNotEmpty()) {
                db.transactionDao().insertTransactions(toInsert)
                db.statementUploadDao().insertUpload(
                    StatementUploadEntity(
                        fileName = fileName,
                        detectedBank = detectedBank,
                        transactionCount = toInsert.size
                    )
                )
            }
        }
    }

    // Export & Backup
    // Reads DAO snapshots rather than the WhileSubscribed StateFlows, which are empty when nothing is collecting.
    fun saveExport(uri: Uri, kind: ExportKind, onComplete: (Boolean, String) -> Unit) {
        val profile = activeProfile.value ?: run {
            onComplete(false, "No active profile selected")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
                    val txns = db.transactionDao().getAllTransactionsSnapshot()
                    val accs = db.accountDao().getAllAccountsSnapshot()
                    val text = when (kind) {
                        ExportKind.CSV -> ExportEngine.generateCsv(txns, accs)
                        ExportKind.JSON -> ExportEngine.generateJsonBackup(profile.name, txns, accs, db.ruleDao().getAllRulesSnapshot())
                    }
                    val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Could not open the destination file")
                    out.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                recordBackupCompleted(profile.id)
                onComplete(true, if (kind == ExportKind.JSON) "Backup saved" else "CSV exported")
            } catch (e: Exception) {
                onComplete(false, "Export failed: ${e.message ?: "could not write the file"}")
            }
        }
    }

    fun restoreFromUri(uri: Uri, onComplete: (Boolean, String) -> Unit) {
        val profile = activeProfile.value ?: run {
            onComplete(false, "No active profile selected")
            return
        }
        viewModelScope.launch {
            val result = try {
                val data = withContext(Dispatchers.IO) {
                    val json = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IOException("Could not open the selected file")
                    val parsed = ExportEngine.parseJsonBackup(json)
                    val db = ExpenseTrackerDatabase.getDatabase(getApplication(), profile.id)
                    db.withTransaction {
                        if (parsed.transactions.isNotEmpty()) db.transactionDao().insertTransactions(parsed.transactions)
                        if (parsed.accounts.isNotEmpty()) db.accountDao().insertAccounts(parsed.accounts)
                        if (parsed.rules.isNotEmpty()) db.ruleDao().insertRules(parsed.rules)
                    }
                    parsed
                }
                true to "Restored ${data.transactions.size} transactions, ${data.accounts.size} accounts, and ${data.rules.size} rules."
            } catch (e: BackupFormatException) {
                false to (e.message ?: "Invalid backup file")
            } catch (e: Exception) {
                false to "Restore failed: ${e.message ?: "unreadable backup file"}"
            }
            onComplete(result.first, result.second)
        }
    }
}
