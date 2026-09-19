package com.wnoicew.expensetracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import com.wnoicew.expensetracker.ui.ALL_CATEGORIES
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.rememberExportLaunchers
import com.wnoicew.expensetracker.ui.components.BacklitCurrencySelector
import com.wnoicew.expensetracker.ui.components.CalendarMonthView
import com.wnoicew.expensetracker.ui.components.DeleteTransactionDialog
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.components.HigInsetGroup
import com.wnoicew.expensetracker.ui.components.HigSegmentedControl
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: MainViewModel
) {
    val launchers = rememberExportLaunchers(viewModel)

    val primaryCurrency = viewModel.activeProfile.value?.currency ?: CurrencyEngine.DEFAULT_CURRENCY

    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val needsReviewCount by viewModel.needsReviewCount.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedSearchQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(200)
        debouncedSearchQuery = searchQuery
    }
    var filterTypeIndex by rememberSaveable { mutableIntStateOf(0) } // 0: All, 1: Expenses, 2: Income, 3: Refunds, 4: Transfers, 5: Review
    var selectedCategoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAccountFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCurrencyFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var viewModeIndex by rememberSaveable { mutableIntStateOf(0) } // 0: List, 1: Calendar
    var prefillDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    val haptic = LocalHapticFeedback.current

    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var selectedTxnId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailStartsEditing by rememberSaveable { mutableStateOf(false) }
    val selectedTxnForDetail = selectedTxnId?.let { id -> transactions.firstOrNull { it.id == id } }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    fun openDetail(txn: TransactionEntity, editing: Boolean) {
        detailStartsEditing = editing
        selectedTxnId = txn.id
    }

    BackHandler(enabled = showAddSheet || selectedTxnForDetail != null) {
        if (showAddSheet) {
            showAddSheet = false
            prefillDateMillis = null
        }
        if (selectedTxnForDetail != null) selectedTxnId = null
    }

    val currencyFormat = remember(primaryCurrency) {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            currency = java.util.Currency.getInstance(primaryCurrency)
            maximumFractionDigits = 2
        }
    }

    val fullDateFormat = remember {
        SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault())
    }

    pendingDelete?.let { txn ->
        DeleteTransactionDialog(
            transaction = txn,
            currencyFormat = currencyFormat,
            onConfirm = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.deleteTransaction(txn)
                if (selectedTxnId == txn.id) selectedTxnId = null
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    val filteredList = remember(transactions, debouncedSearchQuery, filterTypeIndex, selectedCategoryFilter, selectedAccountFilter, selectedCurrencyFilter) {
        transactions.filter { txn ->
            val matchesSearch = txn.description.contains(debouncedSearchQuery, ignoreCase = true) ||
                    txn.category.contains(debouncedSearchQuery, ignoreCase = true) ||
                    txn.accountName.contains(debouncedSearchQuery, ignoreCase = true) ||
                    txn.referenceNo.contains(debouncedSearchQuery, ignoreCase = true)
            val matchesType = when (filterTypeIndex) {
                1 -> txn.type == TransactionType.EXPENSE
                2 -> txn.type == TransactionType.INCOME
                3 -> txn.type == TransactionType.REFUND
                4 -> txn.type == TransactionType.TRANSFER
                5 -> txn.needsReview || txn.category == "Uncategorized" || txn.duplicateStatus == "pending_review"
                else -> true
            }
            val matchesCat = if (selectedCategoryFilter != null) txn.category == selectedCategoryFilter else true
            val matchesAcc = if (selectedAccountFilter != null) txn.accountName == selectedAccountFilter || txn.accountId == selectedAccountFilter else true
            val matchesCur = if (selectedCurrencyFilter != null) {
                val c = txn.currency.ifBlank { primaryCurrency }.uppercase()
                c == selectedCurrencyFilter?.uppercase()
            } else true
            matchesSearch && matchesType && matchesCat && matchesAcc && matchesCur
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    prefillDateMillis = null
                    showAddSheet = true
                },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Financial Ledger",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Complete record of categorized transactions (${filteredList.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Export CSV Button (Matching Web App)
                    IconButton(onClick = launchers.exportCsv) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // View Mode Switcher (List View vs Calendar Month)
            item {
                HigSegmentedControl(
                    items = listOf("List View", "Calendar Month"),
                    selectedIndex = viewModeIndex,
                    onItemSelected = { viewModeIndex = it }
                )
            }

            if (viewModeIndex == 1) {
                // Calendar Month-View and Interactive Day-Ledger
                item {
                    CalendarMonthView(
                        transactions = transactions,
                        currency = primaryCurrency,
                        currencyFormat = currencyFormat,
                        onSelectTransaction = { openDetail(it, editing = false) },
                        onEditTransaction = { openDetail(it, editing = true) },
                        onDeleteTransaction = { pendingDelete = it },
                        onAddTransactionForDate = { millis ->
                            prefillDateMillis = millis
                            showAddSheet = true
                        }
                    )
                }
            } else {
                // Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by merchant, UTR, narration, notes...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Type Filter Scrollable Chips Row (All, Expenses, Income, Refunds, Transfers, Review)
                item {
                    val typeItems = listOf(
                        "All",
                        "Expenses",
                        "Income",
                        "Refunds",
                        "Transfers",
                        "Review ($needsReviewCount)"
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        itemsIndexed(typeItems) { index, label ->
                            FilterChip(
                                selected = filterTypeIndex == index,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    filterTypeIndex = index
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (filterTypeIndex == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (index == 5 && needsReviewCount > 0) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = com.wnoicew.expensetracker.ui.theme.WarningAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                // Currency Filter Scrollable Chips Row
                item {
                    val supportedCurrencies = listOf("INR", "USD", "EUR", "GBP", "CHF", "JPY")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCurrencyFilter == null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCurrencyFilter = null
                                },
                                label = { Text("All Currencies") }
                            )
                        }
                        items(supportedCurrencies) { code ->
                            val symbol = CurrencyEngine.getSymbol(code)
                            FilterChip(
                                selected = selectedCurrencyFilter == code,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCurrencyFilter = if (selectedCurrencyFilter == code) null else code
                                },
                                label = { Text("$symbol $code") }
                            )
                        }
                    }
                }

                // Category Chips Row
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategoryFilter == null,
                                onClick = { selectedCategoryFilter = null },
                                label = { Text("All Categories") }
                            )
                        }
                        items(ALL_CATEGORIES) { cat ->
                            FilterChip(
                                selected = selectedCategoryFilter == cat,
                                onClick = { selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                // Account Filter Chips (if accounts exist)
                if (accounts.isNotEmpty()) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedAccountFilter == null,
                                    onClick = { selectedAccountFilter = null },
                                    label = { Text("All Accounts") }
                                )
                            }
                            items(accounts) { acc ->
                                FilterChip(
                                    selected = selectedAccountFilter == acc.name,
                                    onClick = { selectedAccountFilter = if (selectedAccountFilter == acc.name) null else acc.name },
                                    label = { Text(acc.name) }
                                )
                            }
                        }
                    }
                }

                if (filteredList.isEmpty()) {
                    item {
                        HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) "No transactions found" else "No matching transactions",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Upload statements in Upload tab or tap + to record manually.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredList,
                        key = { _, txn -> txn.id },
                        contentType = { _, txn -> txn.type }
                    ) { index, txn ->
                        val shape = when {
                            filteredList.size == 1 -> RoundedCornerShape(16.dp)
                            index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                            index == filteredList.lastIndex -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = shape,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(modifier = Modifier.clickable { openDetail(txn, editing = false) }) {
                                TransactionRowItem(
                                    transaction = txn,
                                    currencyFormat = currencyFormat,
                                    showDivider = index < filteredList.size - 1,
                                    primaryCurrency = primaryCurrency
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Transaction Modal Bottom Sheet
        if (showAddSheet) {
            AddTransactionBottomSheet(
                accounts = accounts.map { it.name },
                defaultCurrency = primaryCurrency,
                initialDateMillis = prefillDateMillis,
                onDismiss = {
                    showAddSheet = false
                    prefillDateMillis = null
                },
                onAdd = { desc, amount, type, category, accountName, mode, notes, date, cur ->
                    viewModel.addTransaction(
                        description = desc,
                        amount = amount,
                        type = type,
                        category = category,
                        accountName = accountName,
                        paymentMode = mode,
                        notes = notes,
                        date = date,
                        currency = cur
                    )
                    showAddSheet = false
                    prefillDateMillis = null
                }
            )
        }

        // Transaction Detail & Reclassification Sheet
        selectedTxnForDetail?.let { txn ->
            TransactionDetailBottomSheet(
                transaction = txn,
                accounts = accounts.map { it.name },
                currencyFormat = currencyFormat,
                primaryCurrency = primaryCurrency,
                startInEditMode = detailStartsEditing,
                onDismiss = { selectedTxnId = null },
                onUpdateCategory = { newCat, learnRule ->
                    val updated = txn.copy(category = newCat, needsReview = false)
                    viewModel.updateTransaction(updated)
                    if (learnRule) {
                        viewModel.learnRuleAndReclassify(txn.description, newCat, txn.type)
                    }
                    selectedTxnId = null
                },
                onUpdateTransaction = { updated ->
                    viewModel.updateTransaction(updated)
                    selectedTxnId = null
                },
                onDelete = { pendingDelete = txn }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionBottomSheet(
    accounts: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, Double, TransactionType, String, String, String, String, Long, String) -> Unit,
    initialDateMillis: Long? = null,
    defaultCurrency: String = CurrencyEngine.DEFAULT_CURRENCY
) {
    val context = LocalContext.current
    var description by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedCurrency by rememberSaveable { mutableStateOf(defaultCurrency) }
    var selectedTypeIndex by rememberSaveable { mutableIntStateOf(0) } // 0: Expense, 1: Income, 2: Transfer
    var selectedCategory by rememberSaveable { mutableStateOf(ALL_CATEGORIES.first()) }
    var selectedAccount by rememberSaveable { mutableStateOf(accounts.firstOrNull() ?: "Main Account") }
    var paymentMode by rememberSaveable { mutableStateOf("UPI") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selectedTimestamp by rememberSaveable(initialDateMillis) {
        mutableLongStateOf(initialDateMillis ?: System.currentTimeMillis())
    }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val dateDisplayFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeDisplayFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Record Transaction",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Backlit Currency Selector
            BacklitCurrencySelector(
                selectedCurrency = selectedCurrency,
                onCurrencySelected = { selectedCurrency = it },
                label = "TRANSACTION CURRENCY"
            )

            HigSegmentedControl(
                items = listOf("Expense", "Income", "Transfer", "Refund"),
                selectedIndex = selectedTypeIndex,
                onItemSelected = { selectedTypeIndex = it }
            )

            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (${CurrencyEngine.getSymbol(selectedCurrency)})") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                val enteredAmt = amountText.toDoubleOrNull()
                if (enteredAmt != null && enteredAmt > 0 && !selectedCurrency.equals(defaultCurrency, ignoreCase = true)) {
                    val converted = CurrencyEngine.convert(enteredAmt, selectedCurrency, defaultCurrency)
                    Text(
                        text = "≈ ${CurrencyEngine.format(converted, defaultCurrency)} ($defaultCurrency primary equivalent)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description / Merchant") },
                placeholder = { Text("e.g. Swiggy, Starbucks, Salary") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Category Selector
            var catExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = catExpanded,
                onExpandedChange = { catExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = catExpanded,
                    onDismissRequest = { catExpanded = false }
                ) {
                    ALL_CATEGORIES.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                selectedCategory = cat
                                catExpanded = false
                            }
                        )
                    }
                }
            }

            // Account Selector (if multiple accounts available)
            if (accounts.isNotEmpty()) {
                var accExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = accExpanded,
                    onExpandedChange = { accExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedAccount,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = accExpanded,
                        onDismissRequest = { accExpanded = false }
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc) },
                                onClick = {
                                    selectedAccount = acc
                                    accExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Apple HIG Date & Time Group
            HigInsetGroup {
                // Date Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val c = Calendar.getInstance().apply {
                                        timeInMillis = selectedTimestamp
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    selectedTimestamp = c.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Text("Date", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = dateDisplayFormat.format(Date(selectedTimestamp)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Time Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = selectedTimestamp }
                            android.app.TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    val c = Calendar.getInstance().apply {
                                        timeInMillis = selectedTimestamp
                                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        set(Calendar.MINUTE, minute)
                                        set(Calendar.SECOND, 0)
                                    }
                                    selectedTimestamp = c.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                false
                            ).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Text("Time", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = timeDisplayFormat.format(Date(selectedTimestamp)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Payment Mode Selector
            val paymentModes = listOf(
                "UPI",
                "Debit Card",
                "Credit Card",
                "Bank Transfer",
                "NEFT",
                "IMPS / RTGS",
                "Net Banking",
                "Cash",
                "Cheque",
                "Digital Wallet",
                "Other"
            )
            var modeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = it }
            ) {
                OutlinedTextField(
                    value = paymentMode,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false }
                ) {
                    paymentModes.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode) },
                            onClick = {
                                paymentMode = mode
                                modeExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Tags (Optional)") },
                placeholder = { Text("e.g. #personal, Dinner with family") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        errorMessage = "Please enter a valid positive amount"
                        return@Button
                    }
                    if (description.isBlank()) {
                        errorMessage = "Please enter a description"
                        return@Button
                    }
                    val type = when (selectedTypeIndex) {
                        0 -> TransactionType.EXPENSE
                        1 -> TransactionType.INCOME
                        2 -> TransactionType.TRANSFER
                        3 -> TransactionType.REFUND
                        else -> TransactionType.EXPENSE
                    }
                    onAdd(description.trim(), amount, type, selectedCategory, selectedAccount, paymentMode.trim(), notes.trim(), selectedTimestamp, selectedCurrency)
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (selectedTypeIndex) {
                        0 -> ExpenseRose
                        1 -> IncomeGreen
                        2 -> PrimaryBlue
                        3 -> Color(0xFF06B6D4)
                        else -> PrimaryBlue
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save Transaction", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailBottomSheet(
    transaction: TransactionEntity,
    accounts: List<String>,
    currencyFormat: NumberFormat,
    primaryCurrency: String = CurrencyEngine.DEFAULT_CURRENCY,
    onDismiss: () -> Unit,
    onUpdateCategory: (String, Boolean) -> Unit,
    onUpdateTransaction: (TransactionEntity) -> Unit,
    onDelete: () -> Unit,
    startInEditMode: Boolean = false
) {
    val context = LocalContext.current
    var isEditing by remember { mutableStateOf(startInEditMode) }

    // Edit states
    var editCurrency by rememberSaveable(transaction.id) { mutableStateOf(transaction.currency.ifBlank { primaryCurrency }) }
    var editDescription by remember(transaction) { mutableStateOf(transaction.description) }
    var editAmountText by remember(transaction) {
        mutableStateOf(if (transaction.amount % 1.0 == 0.0) transaction.amount.toLong().toString() else transaction.amount.toString())
    }
    var editTypeIndex by remember(transaction) {
        mutableIntStateOf(
            when (transaction.type) {
                TransactionType.EXPENSE -> 0
                TransactionType.INCOME -> 1
                TransactionType.TRANSFER -> 2
                TransactionType.REFUND -> 3
            }
        )
    }
    var editCategory by remember(transaction) { mutableStateOf(transaction.category) }
    var editAccount by remember(transaction) { mutableStateOf(transaction.accountName.ifBlank { accounts.firstOrNull() ?: "Main Account" }) }
    var editTimestamp by remember(transaction) { mutableLongStateOf(transaction.date) }
    var editMode by remember(transaction) { mutableStateOf(transaction.paymentMode) }
    var editRef by remember(transaction) { mutableStateOf(transaction.referenceNo) }
    var editNotes by remember(transaction) { mutableStateOf(transaction.note) }
    var editError by remember { mutableStateOf<String?>(null) }

    // Non-edit states
    var selectedCat by remember(transaction) { mutableStateOf(transaction.category) }
    var learnAsRule by remember { mutableStateOf(false) }

    val fullDateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy · hh:mm a", Locale.getDefault()) }
    val dateDisplayFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeDisplayFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Title & Edit toggle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Transaction" else "Transaction Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                    onClick = {
                        isEditing = !isEditing
                        editError = null
                    }
                ) {
                    if (isEditing) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                            Text("Edit", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (isEditing) {
                // EDIT MODE
                BacklitCurrencySelector(
                    selectedCurrency = editCurrency,
                    onCurrencySelected = { editCurrency = it },
                    label = "TRANSACTION CURRENCY"
                )

                HigSegmentedControl(
                    items = listOf("Expense", "Income", "Transfer", "Refund"),
                    selectedIndex = editTypeIndex,
                    onItemSelected = { editTypeIndex = it }
                )

                Column {
                    OutlinedTextField(
                        value = editAmountText,
                        onValueChange = { editAmountText = it },
                        label = { Text("Amount (${CurrencyEngine.getSymbol(editCurrency)})") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val amtDouble = editAmountText.toDoubleOrNull()
                    if (amtDouble != null && amtDouble > 0 && !editCurrency.equals(primaryCurrency, ignoreCase = true)) {
                        val converted = CurrencyEngine.convert(amtDouble, editCurrency, primaryCurrency)
                        Text(
                            text = "≈ ${CurrencyEngine.format(converted, primaryCurrency)} ($primaryCurrency primary equivalent)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text("Description / Merchant") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selector
                var catExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it }
                ) {
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
                    ) {
                        ALL_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    editCategory = cat
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }

                // Account Selector
                if (accounts.isNotEmpty()) {
                    var accExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = accExpanded,
                        onExpandedChange = { accExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = editAccount,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accExpanded) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = accExpanded,
                            onDismissRequest = { accExpanded = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc) },
                                    onClick = {
                                        editAccount = acc
                                        accExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Apple HIG Date & Time Group for Editing
                HigInsetGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = editTimestamp }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val c = Calendar.getInstance().apply {
                                            timeInMillis = editTimestamp
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        }
                                        editTimestamp = c.timeInMillis
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Text("Date", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = dateDisplayFormat.format(Date(editTimestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = editTimestamp }
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val c = Calendar.getInstance().apply {
                                            timeInMillis = editTimestamp
                                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                        }
                                        editTimestamp = c.timeInMillis
                                    },
                                    cal.get(Calendar.HOUR_OF_DAY),
                                    cal.get(Calendar.MINUTE),
                                    false
                                ).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Text("Time", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = timeDisplayFormat.format(Date(editTimestamp)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Payment Mode Selector
                val paymentModes = listOf(
                    "UPI",
                    "Debit Card",
                    "Credit Card",
                    "Bank Transfer",
                    "NEFT",
                    "IMPS / RTGS",
                    "Net Banking",
                    "Cash",
                    "Cheque",
                    "Digital Wallet",
                    "Other"
                )
                var editModeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = editModeExpanded,
                    onExpandedChange = { editModeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = editMode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editModeExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = editModeExpanded,
                        onDismissRequest = { editModeExpanded = false }
                    ) {
                        paymentModes.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode) },
                                onClick = {
                                    editMode = mode
                                    editModeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = editRef,
                    onValueChange = { editRef = it },
                    label = { Text("Reference / UTR No") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editNotes,
                    onValueChange = { editNotes = it },
                    label = { Text("Notes / Tags") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (editError != null) {
                    Text(
                        text = editError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = {
                        val amt = editAmountText.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            editError = "Please enter a valid positive amount"
                            return@Button
                        }
                        if (editDescription.isBlank()) {
                            editError = "Please enter a description"
                            return@Button
                        }
                        val finalType = when (editTypeIndex) {
                            0 -> TransactionType.EXPENSE
                            1 -> TransactionType.INCOME
                            2 -> TransactionType.TRANSFER
                            3 -> TransactionType.REFUND
                            else -> TransactionType.EXPENSE
                        }
                        val updated = transaction.copy(
                            description = editDescription.trim(),
                            amount = amt,
                            type = finalType,
                            category = editCategory,
                            accountName = editAccount,
                            date = editTimestamp,
                            paymentMode = editMode.trim().ifBlank { "Online" },
                            referenceNo = editRef.trim(),
                            note = editNotes.trim(),
                            needsReview = false,
                            currency = editCurrency
                        )
                        onUpdateTransaction(updated)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                // VIEW DETAILS & QUICK RECLASSIFY MODE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transaction.description,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = fullDateFormat.format(Date(transaction.date)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        val txnCur = transaction.currency.ifBlank { CurrencyEngine.DEFAULT_CURRENCY }
                        Text(
                            text = CurrencyEngine.format(transaction.amount, txnCur),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when (transaction.type) {
                                TransactionType.INCOME -> IncomeGreen
                                TransactionType.REFUND -> Color(0xFF06B6D4)
                                else -> ExpenseRose
                            }
                        )
                        if (!txnCur.equals(primaryCurrency, ignoreCase = true)) {
                            val converted = CurrencyEngine.convert(transaction.amount, txnCur, primaryCurrency)
                            Text(
                                text = "≈ ${CurrencyEngine.format(converted, primaryCurrency)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (transaction.accountName.isNotBlank()) {
                        Text(text = "Account: ${transaction.accountName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (transaction.referenceNo.isNotBlank()) {
                        Text(text = "UTR / Ref: ${transaction.referenceNo}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (transaction.sourceFile.isNotBlank()) {
                        Text(text = "Source: ${transaction.sourceFile}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(text = "Payment Mode: ${transaction.paymentMode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (transaction.note.isNotBlank()) {
                        Text(text = "Notes: ${transaction.note}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    text = "Reclassify Category",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                var catExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCat,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
                    ) {
                        ALL_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCat = cat
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = learnAsRule, onCheckedChange = { learnAsRule = it })
                    Text(
                        text = "Always auto-categorize '${transaction.description}' entries",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onUpdateCategory(selectedCat, learnAsRule) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Update Category", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Transaction", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
