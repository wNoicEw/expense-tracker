package com.wnoicew.expensetracker.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import com.wnoicew.expensetracker.ui.ALL_CATEGORIES
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.components.HigInsetGroup
import com.wnoicew.expensetracker.ui.components.HigSegmentedControl
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val transactions by viewModel.transactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val needsReviewCount by viewModel.needsReviewCount.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterTypeIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Expenses, 2: Income, 3: Transfers, 4: Review
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var selectedAccountFilter by remember { mutableStateOf<String?>(null) }

    var showAddSheet by remember { mutableStateOf(false) }
    var selectedTxnForDetail by remember { mutableStateOf<TransactionEntity?>(null) }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val filteredList = remember(transactions, searchQuery, filterTypeIndex, selectedCategoryFilter, selectedAccountFilter) {
        transactions.filter { txn ->
            val matchesSearch = txn.description.contains(searchQuery, ignoreCase = true) ||
                    txn.category.contains(searchQuery, ignoreCase = true) ||
                    txn.accountName.contains(searchQuery, ignoreCase = true) ||
                    txn.referenceNo.contains(searchQuery, ignoreCase = true)
            val matchesType = when (filterTypeIndex) {
                1 -> txn.type == TransactionType.EXPENSE
                2 -> txn.type == TransactionType.INCOME
                3 -> txn.type == TransactionType.TRANSFER
                4 -> txn.needsReview || txn.category == "Uncategorized" || txn.duplicateStatus == "pending_review"
                else -> true
            }
            val matchesCat = if (selectedCategoryFilter != null) txn.category == selectedCategoryFilter else true
            val matchesAcc = if (selectedAccountFilter != null) txn.accountName == selectedAccountFilter || txn.accountId == selectedAccountFilter else true
            matchesSearch && matchesType && matchesCat && matchesAcc
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
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
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val csv = viewModel.exportCsvString()
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                    putExtra(Intent.EXTRA_TITLE, "Money_Tracker_Ledger.csv")
                                    type = "text/csv"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export CSV"))
                            }
                        }
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

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

            // Type Filter Segmented Control (All, Expenses, Income, Transfers, Review)
            item {
                HigSegmentedControl(
                    items = listOf("All", "Expenses", "Income", "Transfers", "Review (${needsReviewCount})"),
                    selectedIndex = filterTypeIndex,
                    onItemSelected = { filterTypeIndex = it }
                )
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
                item {
                    HigInsetGroup {
                        filteredList.forEachIndexed { index, txn ->
                            Box(modifier = Modifier.clickable { selectedTxnForDetail = txn }) {
                                TransactionRowItem(
                                    transaction = txn,
                                    currencyFormat = currencyFormat,
                                    showDivider = index < filteredList.size - 1
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
                onDismiss = { showAddSheet = false },
                onAdd = { desc, amount, type, category, accountName, mode, notes ->
                    viewModel.addTransaction(
                        description = desc,
                        amount = amount,
                        type = type,
                        category = category,
                        accountName = accountName,
                        paymentMode = mode,
                        notes = notes
                    )
                    showAddSheet = false
                }
            )
        }

        // Transaction Detail & Reclassification Sheet
        selectedTxnForDetail?.let { txn ->
            TransactionDetailBottomSheet(
                transaction = txn,
                currencyFormat = currencyFormat,
                onDismiss = { selectedTxnForDetail = null },
                onUpdateCategory = { newCat, learnRule ->
                    val updated = txn.copy(category = newCat, needsReview = false)
                    viewModel.updateTransaction(updated)
                    if (learnRule) {
                        viewModel.learnRuleAndReclassify(txn.description, newCat, txn.type)
                    }
                    selectedTxnForDetail = null
                },
                onDelete = {
                    viewModel.deleteTransaction(txn)
                    selectedTxnForDetail = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionBottomSheet(
    accounts: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, Double, TransactionType, String, String, String, String) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedTypeIndex by remember { mutableIntStateOf(0) } // 0: Expense, 1: Income, 2: Transfer
    var selectedCategory by remember { mutableStateOf(ALL_CATEGORIES.first()) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull() ?: "Main Account") }
    var paymentMode by remember { mutableStateOf("UPI") }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .safeDrawingPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Record Transaction",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            HigSegmentedControl(
                items = listOf("Expense", "Income", "Transfer"),
                selectedIndex = selectedTypeIndex,
                onItemSelected = { selectedTypeIndex = it }
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount (₹)") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

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
                        else -> TransactionType.TRANSFER
                    }
                    onAdd(description.trim(), amount, type, selectedCategory, selectedAccount, paymentMode, notes.trim())
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (selectedTypeIndex) {
                        0 -> ExpenseRose
                        1 -> IncomeGreen
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
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onUpdateCategory: (String, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var selectedCat by remember { mutableStateOf(transaction.category) }
    var learnAsRule by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .safeDrawingPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                        text = dateFormat.format(Date(transaction.date)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = currencyFormat.format(transaction.amount),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (transaction.type == TransactionType.INCOME) IncomeGreen else ExpenseRose
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (transaction.referenceNo.isNotBlank()) {
                    Text(text = "UTR / Ref: ${transaction.referenceNo}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (transaction.sourceFile.isNotBlank()) {
                    Text(text = "Source: ${transaction.sourceFile}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text = "Payment Mode: ${transaction.paymentMode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
