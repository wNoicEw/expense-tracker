package com.wnoicew.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import com.wnoicew.expensetracker.ui.ALL_CATEGORIES
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.DeleteTransactionDialog
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.WarningAmber
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedsReviewScreen(
    viewModel: MainViewModel
) {
    val reviewTransactions by viewModel.needsReviewTransactions.collectAsState()
    val primaryCurrency = viewModel.activeProfile.value?.currency ?: CurrencyEngine.DEFAULT_CURRENCY

    val currencyFormat = remember(primaryCurrency) {
        CurrencyEngine.getFormat(primaryCurrency)
    }

    val dateFormat = remember { SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    pendingDelete?.let { txn ->
        DeleteTransactionDialog(
            transaction = txn,
            currencyFormat = currencyFormat,
            onConfirm = {
                viewModel.deleteTransaction(txn)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Needs Review",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Undetected Expenses & AI Classification Learning",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = WarningAmber.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${reviewTransactions.size} Pending",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarningAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        item {
            HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "When a transaction cannot be classified with high confidence, it appears here. When you select a category, Money Tracker memorizes the merchant / UPI ID so all future (and past) transactions to that same account will be classified automatically!",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (reviewTransactions.isEmpty()) {
            item {
                HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "All Transactions Classified!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zero unclassified or ambiguous transactions pending review.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(reviewTransactions, key = { it.id }) { txn ->
                ReviewTransactionCard(
                    transaction = txn,
                    primaryCurrency = primaryCurrency,
                    dateFormat = dateFormat,
                    onClassify = { selectedCat, txnType, pattern ->
                        val updated = txn.copy(
                            category = selectedCat,
                            type = txnType,
                            needsReview = false
                        )
                        viewModel.updateTransaction(updated)
                        viewModel.learnRuleAndReclassify(pattern.ifBlank { txn.description }, selectedCat, txnType)
                    },
                    onDelete = { pendingDelete = txn }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewTransactionCard(
    transaction: TransactionEntity,
    primaryCurrency: String,
    dateFormat: SimpleDateFormat,
    onClassify: (String, TransactionType, String) -> Unit,
    onDelete: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(if (transaction.category != "Uncategorized") transaction.category else ALL_CATEGORIES.first()) }
    var selectedType by remember { mutableStateOf(transaction.type) }
    var customPattern by remember { mutableStateOf(transaction.description) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val typeOptions = listOf("Expense", "Income", "Refund", "Transfer")
    val selectedTypeIndex = when (selectedType) {
        TransactionType.EXPENSE -> 0
        TransactionType.INCOME -> 1
        TransactionType.REFUND -> 2
        TransactionType.TRANSFER -> 3
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.description,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${dateFormat.format(Date(transaction.date))} · ${transaction.sourceFile}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = CurrencyEngine.format(transaction.amount, transaction.currency.ifBlank { primaryCurrency }),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = when (selectedType) {
                        TransactionType.INCOME -> IncomeGreen
                        TransactionType.REFUND -> Color(0xFF06B6D4)
                        TransactionType.TRANSFER -> Color(0xFF8B5CF6)
                        else -> ExpenseRose
                    }
                )
            }

            if (transaction.rawNarration.isNotBlank() && transaction.rawNarration != transaction.description) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Raw: ${transaction.rawNarration}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Transaction Type Reclassification Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Transaction Type",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                com.wnoicew.expensetracker.ui.components.HigSegmentedControl(
                    items = typeOptions,
                    selectedIndex = selectedTypeIndex,
                    onItemSelected = { idx ->
                        selectedType = when (idx) {
                            0 -> TransactionType.EXPENSE
                            1 -> TransactionType.INCOME
                            2 -> TransactionType.REFUND
                            else -> TransactionType.TRANSFER
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                )
            }

            // Category Selection Dropdown
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Assign Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    ALL_CATEGORIES.filter { it != "Uncategorized" }.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                selectedCategory = cat
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Editable Pattern for Learned Rule
            OutlinedTextField(
                value = customPattern,
                onValueChange = { customPattern = it },
                label = { Text("Learned Rule Pattern") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onClassify(selectedCategory, selectedType, customPattern)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Teach AI & Classify", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onDelete()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
