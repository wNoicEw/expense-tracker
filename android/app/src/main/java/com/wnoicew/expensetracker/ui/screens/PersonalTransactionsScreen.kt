package com.wnoicew.expensetracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.engine.FriendBalanceStatus
import com.wnoicew.expensetracker.data.engine.FriendProfile
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.DeleteTransactionDialog
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class PersonalFilter(val label: String) {
    ALL("All"),
    OWES_YOU("You Get"),
    YOU_OWE("You Owe"),
    SETTLED("Settled")
}

private val FriendAvatarGradients = listOf(
    listOf(Color(0xFFA855F7), Color(0xFF6366F1)), // Purple -> Indigo
    listOf(Color(0xFF3B82F6), Color(0xFF06B6D4)), // Blue -> Cyan
    listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)), // Pink -> Purple
    listOf(Color(0xFF10B981), Color(0xFF059669)), // Emerald -> Green
    listOf(Color(0xFFF59E0B), Color(0xFFD97706)), // Amber -> Orange
    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))  // Cyan -> Blue
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalTransactionsScreen(
    viewModel: MainViewModel,
    onOpenAddFriendTxn: (prefillName: String?) -> Unit = {}
) {
    val profiles by viewModel.friendProfiles.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val activeProfile by viewModel.activeProfile
    val primaryCurrency = activeProfile?.currency ?: CurrencyEngine.DEFAULT_CURRENCY

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(PersonalFilter.ALL) }
    var activeFriendKey by rememberSaveable { mutableStateOf<String?>(null) }

    // Dialog States
    var renameTarget by remember { mutableStateOf<FriendProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<FriendProfile?>(null) }
    var pendingDeleteTxn by remember { mutableStateOf<TransactionEntity?>(null) }

    val currencyFormat = remember(primaryCurrency) {
        CurrencyEngine.getFormat(primaryCurrency)
    }

    // Active detail profile if open
    val activeFriend = remember(profiles, activeFriendKey) {
        activeFriendKey?.let { key -> profiles.find { it.id == key } }
    }

    // Filtered profiles for main grid
    val filteredProfiles = remember(profiles, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        profiles.filter { p ->
            val matchesFilter = when (selectedFilter) {
                PersonalFilter.ALL -> true
                PersonalFilter.OWES_YOU -> p.status == FriendBalanceStatus.OWES_YOU
                PersonalFilter.YOU_OWE -> p.status == FriendBalanceStatus.YOU_OWE
                PersonalFilter.SETTLED -> p.status == FriendBalanceStatus.SETTLED
            }
            val matchesSearch = q.isBlank() ||
                    p.displayName.lowercase(Locale.ROOT).contains(q) ||
                    p.extractedName.lowercase(Locale.ROOT).contains(q) ||
                    p.upiId.lowercase(Locale.ROOT).contains(q) ||
                    p.transactions.any { it.description.lowercase(Locale.ROOT).contains(q) || it.rawNarration.lowercase(Locale.ROOT).contains(q) }

            matchesFilter && matchesSearch
        }
    }

    // KPI Metrics across all friends
    val totalFriends = profiles.size
    val totalToReceive = remember(profiles) {
        profiles.filter { it.status == FriendBalanceStatus.OWES_YOU }.sumOf { it.netBalance }
    }
    val totalToPay = remember(profiles) {
        profiles.filter { it.status == FriendBalanceStatus.YOU_OWE }.sumOf { abs(it.netBalance) }
    }
    val netPosition = totalToReceive - totalToPay

    // Modals
    renameTarget?.let { friend ->
        RenameFriendDialog(
            friend = friend,
            onSave = { newName ->
                viewModel.renameFriend(friend.id, newName)
                renameTarget = null
            },
            onReset = if (friend.isCustomName) {
                {
                    viewModel.resetFriendName(friend.id)
                    renameTarget = null
                }
            } else null,
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { friend ->
        DeleteFriendProfileDialog(
            friend = friend,
            onUntag = {
                viewModel.untagFriendTransactions(friend)
                if (activeFriendKey == friend.id) activeFriendKey = null
                deleteTarget = null
            },
            onDeleteAll = {
                viewModel.deleteFriendProfileAndTransactions(friend)
                if (activeFriendKey == friend.id) activeFriendKey = null
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    pendingDeleteTxn?.let { txn ->
        DeleteTransactionDialog(
            transaction = txn,
            currencyFormat = currencyFormat,
            onConfirm = {
                viewModel.deleteTransaction(txn)
                pendingDeleteTxn = null
            },
            onDismiss = { pendingDeleteTxn = null }
        )
    }

    // If friend detail view is active
    if (activeFriend != null) {
        FriendDetailView(
            friend = activeFriend,
            currencyFormat = currencyFormat,
            onBack = { activeFriendKey = null },
            onRename = { renameTarget = activeFriend },
            onDelete = { deleteTarget = activeFriend },
            onAddTxn = { onOpenAddFriendTxn(activeFriend.displayName) },
            onDeleteTxn = { txn -> pendingDeleteTxn = txn }
        )
    } else {
        // Main Personal Transactions Hub
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Personal Transactions",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Friends, UPI IDs & Peer-to-Peer Balances",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { onOpenAddFriendTxn(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Txn", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Bento KPI Grid (4 Cards: Friends, You Get, You Owe, Net Position)
            item {
                PersonalKpiGrid(
                    totalFriends = totalFriends,
                    totalToReceive = totalToReceive,
                    totalToPay = totalToPay,
                    netPosition = netPosition,
                    currencyFormat = currencyFormat
                )
            }

            // Search Bar & Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by name, UPI ID or note...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA855F7),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PersonalFilter.values().forEach { filter ->
                            val selected = selectedFilter == filter
                            val count = when (filter) {
                                PersonalFilter.ALL -> profiles.size
                                PersonalFilter.OWES_YOU -> profiles.count { it.status == FriendBalanceStatus.OWES_YOU }
                                PersonalFilter.YOU_OWE -> profiles.count { it.status == FriendBalanceStatus.YOU_OWE }
                                PersonalFilter.SETTLED -> profiles.count { it.status == FriendBalanceStatus.SETTLED }
                            }

                            FilterChip(
                                selected = selected,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        text = "${filter.label} ($count)",
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFA855F7).copy(alpha = 0.16f),
                                    selectedLabelColor = Color(0xFFA855F7)
                                )
                            )
                        }
                    }
                }
            }

            // Friends Profile List
            if (filteredProfiles.isEmpty()) {
                item {
                    EmptyFriendsCard(searchQuery = searchQuery)
                }
            } else {
                items(filteredProfiles, key = { it.id }) { friend ->
                    FriendCard(
                        friend = friend,
                        currencyFormat = currencyFormat,
                        onClick = { activeFriendKey = friend.id },
                        onRename = { renameTarget = friend },
                        onDelete = { deleteTarget = friend }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalKpiGrid(
    totalFriends: Int,
    totalToReceive: Double,
    totalToPay: Double,
    netPosition: Double,
    currencyFormat: java.text.NumberFormat
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Friends Tracked
            KpiCard(
                title = "Friends Tracked",
                value = "$totalFriends",
                subtitle = "Categorized profiles",
                accentColor = Color(0xFFA855F7),
                icon = Icons.Default.People,
                modifier = Modifier.weight(1f)
            )

            // 2. You Get (To Receive)
            KpiCard(
                title = "You Get",
                value = currencyFormat.format(totalToReceive),
                subtitle = "Friends owe you",
                accentColor = IncomeGreen,
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 3. You Owe (To Pay)
            KpiCard(
                title = "You Owe",
                value = currencyFormat.format(totalToPay),
                subtitle = "You owe friends",
                accentColor = ExpenseRose,
                icon = Icons.Default.ArrowUpward,
                modifier = Modifier.weight(1f)
            )

            // 4. Net Position
            val netColor = if (netPosition >= 0) IncomeGreen else ExpenseRose
            KpiCard(
                title = "Net Position",
                value = currencyFormat.format(abs(netPosition)),
                subtitle = if (netPosition >= 0) "Overall surplus" else "Overall deficit",
                accentColor = netColor,
                icon = if (netPosition >= 0) Icons.Default.Check else Icons.Default.Warning,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                }
            }

            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FriendCard(
    friend: FriendProfile,
    currencyFormat: java.text.NumberFormat,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val gradientColors = FriendAvatarGradients[friend.gradientIndex % FriendAvatarGradients.size]

    val balanceColor = when (friend.status) {
        FriendBalanceStatus.OWES_YOU -> IncomeGreen
        FriendBalanceStatus.YOU_OWE -> ExpenseRose
        FriendBalanceStatus.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Avatar, Name, UPI ID, and Net Balance Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar Circle with gradient
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = friend.initials,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = friend.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (friend.isCustomName) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFA855F7).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Custom",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA855F7),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (friend.upiId.isNotBlank()) {
                            Text(
                                text = friend.upiId,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Balance Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = balanceColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, balanceColor.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = friend.statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = balanceColor
                        )
                        Text(
                            text = if (friend.status == FriendBalanceStatus.SETTLED) currencyFormat.format(0) else currencyFormat.format(abs(friend.netBalance)),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = balanceColor
                        )
                    }
                }
            }

            // Breakdown (Sent & Received)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sent: ${currencyFormat.format(friend.totalOutgoing)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Received: ${currencyFormat.format(friend.totalIncoming)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bottom Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${friend.transactionCount} txn${if (friend.transactionCount == 1) "" else "s"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRose, modifier = Modifier.size(15.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable(onClick = onClick)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Ledger", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(2.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFriendsCard(searchQuery: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFA855F7).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(28.dp))
            }

            Text(
                text = if (searchQuery.isNotBlank()) "No Matching Friends" else "No Friend Profiles Yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (searchQuery.isNotBlank()) {
                    "No friend profiles matched \"$searchQuery\". Try a different search term or clear the filter."
                } else {
                    "Categorize any transaction as \"Friend\" to automatically extract payees, group their UPI IDs, and track your peer-to-peer balance here."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun FriendDetailView(
    friend: FriendProfile,
    currencyFormat: java.text.NumberFormat,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onAddTxn: () -> Unit,
    onDeleteTxn: (TransactionEntity) -> Unit
) {
    val gradientColors = FriendAvatarGradients[friend.gradientIndex % FriendAvatarGradients.size]
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val balanceColor = when (friend.status) {
        FriendBalanceStatus.OWES_YOU -> IncomeGreen
        FriendBalanceStatus.YOU_OWE -> ExpenseRose
        FriendBalanceStatus.SETTLED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Back Navigation Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.clickable(onClick = onBack),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                    Text("All Friends", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRose, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Friend Header Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(gradientColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = friend.initials,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }

                            Column {
                                Text(
                                    text = friend.displayName,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (friend.upiId.isNotBlank()) {
                                    Text(
                                        text = friend.upiId,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Big Net Status Banner
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = balanceColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, balanceColor.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = friend.statusText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = balanceColor
                                )
                                Text(
                                    text = if (friend.status == FriendBalanceStatus.SETTLED) currencyFormat.format(0) else currencyFormat.format(abs(friend.netBalance)),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = balanceColor
                                )
                            }
                        }
                    }

                    // 3 Metric Stat Blocks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Sent
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("TOTAL SENT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text(currencyFormat.format(friend.totalOutgoing), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        // Total Received
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("TOTAL RECEIVED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text(currencyFormat.format(friend.totalIncoming), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        // Total Count
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("TRANSACTIONS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Text("${friend.transactionCount}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }

                    // Add Txn Button for this Friend
                    Button(
                        onClick = onAddTxn,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Transaction with ${friend.displayName}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Ledger List Header
        item {
            Text(
                text = "Transaction History (${friend.transactionCount})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Transactions
        items(friend.transactions, key = { it.id }) { txn ->
            val isIncoming = txn.type == TransactionType.INCOME ||
                    txn.type == TransactionType.REFUND ||
                    (txn.type == TransactionType.TRANSFER && txn.rawNarration.contains(Regex("""\b(cr|credit|received|deposit)\b""", RegexOption.IGNORE_CASE)))

            val flowColor = if (isIncoming) IncomeGreen else ExpenseRose
            val flowLabel = if (isIncoming) "Received" else "Sent"

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = flowColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = flowLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = flowColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = dateFormat.format(Date(txn.date)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = txn.description.ifBlank { txn.rawNarration.ifBlank { "Friend Transaction" } },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (txn.paymentMode.isNotBlank() || txn.referenceNo.isNotBlank()) {
                            Text(
                                text = "${txn.paymentMode}${if (txn.referenceNo.isNotBlank()) " · ${txn.referenceNo}" else ""}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${if (isIncoming) "+" else "-"}${currencyFormat.format(abs(txn.amount))}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = flowColor
                        )

                        IconButton(
                            onClick = { onDeleteTxn(txn) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ExpenseRose, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameFriendDialog(
    friend: FriendProfile,
    onSave: (String) -> Unit,
    onReset: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var nameInput by rememberSaveable { mutableStateOf(friend.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Friend Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Change the display name for this friend profile. Original extracted name: \"${friend.extractedName}\"",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Friend Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (onReset != null) {
                    TextButton(onClick = onReset) {
                        Text("Reset to original (${friend.extractedName})", fontSize = 11.sp, color = Color(0xFFA855F7))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nameInput.isNotBlank()) onSave(nameInput) },
                enabled = nameInput.isNotBlank()
            ) {
                Text("Save")
            }
        }
    )
}

@Composable
private fun DeleteFriendProfileDialog(
    friend: FriendProfile,
    onUntag: () -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Friend Profile?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "How would you like to handle ${friend.transactionCount} transaction(s) associated with “${friend.displayName}”?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onUntag)
                        .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Untag Transactions (Recommended)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFA855F7))
                        Text(
                            "Removes the Friend tag without deleting records. Your bank statement history and main balances stay completely safe.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ExpenseRose.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDeleteAll)
                        .border(1.dp, ExpenseRose.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Delete All Transactions", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ExpenseRose)
                        Text(
                            "Permanently deletes all ${friend.transactionCount} transactions from the database.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {}
    )
}
