package com.wnoicew.expensetracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.data.model.AccountEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import com.wnoicew.expensetracker.ui.ALL_CATEGORIES
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.WarningAmber

data class LedgerFilterState(
    val selectedTypes: Set<TransactionType> = emptySet(),
    val selectedCategories: Set<String> = emptySet(),
    val selectedAccounts: Set<String> = emptySet(),
    val selectedCurrencies: Set<String> = emptySet(),
    val onlyNeedsReview: Boolean = false
) {
    val totalCount: Int
        get() = selectedTypes.size + selectedCategories.size + selectedAccounts.size + selectedCurrencies.size + (if (onlyNeedsReview) 1 else 0)

    val activeCount: Int
        get() = totalCount

    val isEmpty: Boolean
        get() = selectedTypes.isEmpty() && selectedCategories.isEmpty() && selectedAccounts.isEmpty() && selectedCurrencies.isEmpty() && !onlyNeedsReview
}

private enum class FilterTab(val label: String) {
    TYPE("Type"),
    CATEGORY("Category"),
    ACCOUNT("Account"),
    CURRENCY("Currency"),
    STATUS("Status")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerFilterSheet(
    initialState: LedgerFilterState,
    accounts: List<AccountEntity>,
    matchingCount: Int,
    onApply: (LedgerFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf(initialState) }
    var activeTab by remember { mutableStateOf(FilterTab.TYPE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
        ) {
            // Header: Title, Clear All, and Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Filters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.totalCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = state.totalCount.toString(),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { state = LedgerFilterState() },
                        enabled = !state.isEmpty
                    ) {
                        Text(
                            text = "Clear all",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!state.isEmpty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Body: Split layout (Left Rail for Categories, Right Pane for Options)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Left Navigation Rail
                Column(
                    modifier = Modifier
                        .width(135.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    FilterTab.entries.forEach { tab ->
                        val isSelected = tab == activeTab
                        val tabCount = when (tab) {
                            FilterTab.TYPE -> state.selectedTypes.size
                            FilterTab.CATEGORY -> state.selectedCategories.size
                            FilterTab.ACCOUNT -> state.selectedAccounts.size
                            FilterTab.CURRENCY -> state.selectedCurrencies.size
                            FilterTab.STATUS -> if (state.onlyNeedsReview) 1 else 0
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeTab = tab }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                                )
                                .padding(horizontal = 14.dp, vertical = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tab.label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface
                                )
                                if (tabCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(PrimaryBlue, CircleShape)
                                    )
                                }
                            }

                            // Active indicator line on the left
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(3.dp)
                                        .height(24.dp)
                                        .background(PrimaryBlue, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Right Content Pane
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    when (activeTab) {
                        FilterTab.TYPE -> {
                            val types = listOf(
                                TransactionType.EXPENSE to "Expenses",
                                TransactionType.INCOME to "Income",
                                TransactionType.REFUND to "Refunds",
                                TransactionType.TRANSFER to "Transfers"
                            )
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(types) { (type, label) ->
                                    val isChecked = type in state.selectedTypes
                                    FilterCheckboxRow(
                                        title = label,
                                        isChecked = isChecked,
                                        onToggle = {
                                            state = state.copy(
                                                selectedTypes = if (isChecked) state.selectedTypes - type else state.selectedTypes + type
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        FilterTab.CATEGORY -> {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(ALL_CATEGORIES) { cat ->
                                    val isChecked = cat in state.selectedCategories
                                    FilterCheckboxRow(
                                        title = cat,
                                        isChecked = isChecked,
                                        onToggle = {
                                            state = state.copy(
                                                selectedCategories = if (isChecked) state.selectedCategories - cat else state.selectedCategories + cat
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        FilterTab.ACCOUNT -> {
                            val accountNames = remember(accounts) {
                                accounts.map { it.name }.distinct()
                            }
                            if (accountNames.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No accounts available",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(accountNames) { accName ->
                                        val isChecked = accName in state.selectedAccounts
                                        FilterCheckboxRow(
                                            title = accName,
                                            isChecked = isChecked,
                                            onToggle = {
                                                state = state.copy(
                                                    selectedAccounts = if (isChecked) state.selectedAccounts - accName else state.selectedAccounts + accName
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        FilterTab.CURRENCY -> {
                            val currencies = listOf("INR", "USD", "EUR", "GBP", "CHF", "JPY")
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(currencies) { code ->
                                    val isChecked = code in state.selectedCurrencies
                                    val symbol = CurrencyEngine.getSymbol(code)
                                    FilterCheckboxRow(
                                        title = "$symbol $code",
                                        isChecked = isChecked,
                                        onToggle = {
                                            state = state.copy(
                                                selectedCurrencies = if (isChecked) state.selectedCurrencies - code else state.selectedCurrencies + code
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        FilterTab.STATUS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterCheckboxRow(
                                    title = "Needs Review Only",
                                    subtitle = "Flagged, uncategorized, or duplicate transactions",
                                    isChecked = state.onlyNeedsReview,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = WarningAmber,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onToggle = {
                                        state = state.copy(onlyNeedsReview = !state.onlyNeedsReview)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Sticky Bottom CTA
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (matchingCount == 1) "1 transaction matches" else "$matchingCount transactions match",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.totalCount > 0) {
                            Text(
                                text = "${state.totalCount} filter${if (state.totalCount > 1) "s" else ""} applied",
                                fontSize = 11.sp,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onApply(state)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (matchingCount > 0) "View $matchingCount Transactions" else "Apply Filters",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterCheckboxRow(
    title: String,
    subtitle: String? = null,
    isChecked: Boolean,
    leadingIcon: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = PrimaryBlue,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        )

        leadingIcon?.invoke()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
