package com.wnoicew.expensetracker.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.model.AccountEntity
import com.wnoicew.expensetracker.data.model.AccountWithMetrics
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.components.HigInsetGroup
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import java.text.NumberFormat
import java.util.*

val CARD_GRADIENTS = listOf(
    listOf(0xFF1E3A8A, 0xFF3B82F6), // Royal Navy
    listOf(0xFF065F46, 0xFF10B981), // Emerald Green
    listOf(0xFF831843, 0xFFEC4899), // Crimson Burgundy
    listOf(0xFF171717, 0xFF404040), // Stealth Onyx Black
    listOf(0xFF854D0E, 0xFFF59E0B)  // Amber Gold
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: MainViewModel
) {
    val accountsWithMetrics by viewModel.accountsWithMetrics.collectAsState()
    var showAddAccountSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = showAddAccountSheet) {
        showAddAccountSheet = false
    }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddAccountSheet = true },
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.AddCard, contentDescription = "Add Account")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Cards & Accounts",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Manage your linked bank accounts, credit cards, and wallets",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Cards Carousel (Apple Wallet style)
            if (accountsWithMetrics.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(accountsWithMetrics, key = { it.account.id }) { item ->
                            LuxuryCardItem(
                                item = item,
                                currencyFormat = currencyFormat
                            )
                        }
                    }
                }
            }

            // Inset Group Account List
            item {
                Text(
                    text = "All Accounts (${accountsWithMetrics.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (accountsWithMetrics.isEmpty()) {
                item {
                    HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No cards or accounts added yet",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Upload a statement or tap the + button below to link your first account.",
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
                        accountsWithMetrics.forEachIndexed { index, item ->
                            AccountRowItem(
                                item = item,
                                currencyFormat = currencyFormat,
                                onDelete = { viewModel.deleteAccount(item.account) },
                                showDivider = index < accountsWithMetrics.size - 1
                            )
                        }
                    }
                }
            }
        }

        if (showAddAccountSheet) {
            AddAccountBottomSheet(
                onDismiss = { showAddAccountSheet = false },
                onAdd = { name, type, balance, limit, gradIdx, lastFour, bankName ->
                    viewModel.addAccount(name, type, balance, limit, gradIdx, lastFour, bankName)
                    showAddAccountSheet = false
                }
            )
        }
    }
}

@Composable
private fun LuxuryCardItem(
    item: AccountWithMetrics,
    currencyFormat: NumberFormat
) {
    val account = item.account
    val isCreditCard = account.type.equals("Credit Card", ignoreCase = true)
    val gradColors = CARD_GRADIENTS[account.gradientIndex % CARD_GRADIENTS.size].map { Color(it) }

    Box(
        modifier = Modifier
            .width(285.dp)
            .height(174.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = gradColors.first().copy(alpha = 0.5f))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(gradColors))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Bank Name on left (with proper ellipsis/wrapping), Pill Badge on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.22f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.35f))
                ) {
                    val badgeText = when {
                        isCreditCard && (account.name.contains("RuPay", ignoreCase = true) || account.bankName.contains("RuPay", ignoreCase = true)) -> "RUPAY CC"
                        isCreditCard -> "CREDIT CARD"
                        account.type.contains("Wallet", ignoreCase = true) -> "WALLET"
                        account.type.contains("Cash", ignoreCase = true) -> "CASH"
                        else -> "BANK A/C"
                    }
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // Middle: Metallic EMV Chip + Contactless Wave Graphic
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Golden EMV Chip
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFDF7A), Color(0xFFC69C36))))
                        .border(0.5.dp, Color(0xFF8C6B1B), RoundedCornerShape(5.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(0.5.dp, Color(0xFF8C6B1B).copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                    )
                }

                Icon(
                    imageVector = Icons.Default.Contactless,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Bottom Section: Balance/Spend & Card Details
            Column {
                val label = if (isCreditCard) {
                    "TOTAL OUTSTANDING / SPEND"
                } else if (item.totalIncome > 0) {
                    "NET BALANCE"
                } else {
                    "TOTAL SPENT"
                }

                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.75f),
                    letterSpacing = 0.6.sp
                )

                Text(
                    text = currencyFormat.format(item.computedBalance),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 1.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.transactionCount} record${if (item.transactionCount != 1) "s" else ""}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    if (account.lastFour.isNotBlank() && account.lastFour != "0000" && account.lastFour != "UPI") {
                        Text(
                            text = "•••• ${account.lastFour}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.95f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRowItem(
    item: AccountWithMetrics,
    currencyFormat: NumberFormat,
    onDelete: () -> Unit,
    showDivider: Boolean
) {
    val account = item.account
    val isCreditCard = account.type.equals("Credit Card", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isCreditCard -> Icons.Default.CreditCard
                            account.type.contains("Wallet", ignoreCase = true) -> Icons.Default.AccountBalanceWallet
                            account.type.contains("Cash", ignoreCase = true) -> Icons.Default.Payments
                            else -> Icons.Default.AccountBalance
                        },
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = account.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(account.type)
                            if (account.lastFour.isNotBlank() && account.lastFour != "0000" && account.lastFour != "UPI") {
                                append(" · •••• ${account.lastFour}")
                            }
                            append(" · ${item.transactionCount} txn${if (item.transactionCount != 1) "s" else ""}")
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = currencyFormat.format(item.computedBalance),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isCreditCard || (item.totalIncome == 0.0 && item.totalExpense > 0.0)) ExpenseRose else if (item.computedBalance >= 0) IncomeGreen else ExpenseRose
                    )
                    Text(
                        text = if (isCreditCard) "Due / Spent" else if (item.totalIncome > 0) "Balance" else "Spent",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).padding(start = 4.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String, Double, Double, Int, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Bank Account") }
    var balanceText by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("") }
    var lastFour by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var selectedGradIndex by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val accountTypes = listOf("Bank Account", "Credit Card", "Digital Wallet", "Savings")

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
                text = "Add Account or Card",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Account Name") },
                placeholder = { Text("e.g. HDFC Salary, Amex Platinum") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Type Dropdown
            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it }
            ) {
                OutlinedTextField(
                    value = type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    accountTypes.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t) },
                            onClick = {
                                type = t
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Balance (₹)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = lastFour,
                    onValueChange = { if (it.length <= 4) lastFour = it },
                    label = { Text("Last 4 digits") },
                    placeholder = { Text("1234") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            // Gradient Theme Selector
            Text(
                text = "Card Theme Gradient",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CARD_GRADIENTS.forEachIndexed { index, colors ->
                    val isSelected = selectedGradIndex == index
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(colors.map { Color(it) }))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) PrimaryBlue else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedGradIndex = index }
                    )
                }
            }

            if (errorMessage != null) {
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        errorMessage = "Please enter an account name"
                        return@Button
                    }
                    val balance = balanceText.toDoubleOrNull() ?: 0.0
                    val limit = limitText.toDoubleOrNull() ?: 0.0
                    onAdd(name.trim(), type, balance, limit, selectedGradIndex, lastFour.trim(), bankName.trim())
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
