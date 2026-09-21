package com.wnoicew.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.model.TransactionType
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.WarningAmber

/**
 * Modern compact filter and sort toolbar inspired by financial apps like Groww.
 * Provides quick access to multi-category filter sheet, sort sheet, and active filter dismiss pills.
 */
@Composable
fun LedgerFilterSortBar(
    filterCount: Int,
    sortOption: LedgerSortOption,
    onOpenFilter: () -> Unit,
    onOpenSort: () -> Unit,
    filterState: LedgerFilterState,
    onRemoveType: (TransactionType) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onRemoveCurrency: (String) -> Unit,
    onClearReview: () -> Unit,
    onClearAll: () -> Unit,
    needsReviewCount: Int = 0,
    onToggleReview: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        // Filter Button (Groww style)
        item {
            val hasFilters = filterCount > 0
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenFilter),
                shape = RoundedCornerShape(12.dp),
                color = if (hasFilters) PrimaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (hasFilters) PrimaryBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filters",
                        tint = if (hasFilters) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "Filter",
                        fontWeight = if (hasFilters) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (hasFilters) PrimaryBlue else MaterialTheme.colorScheme.onSurface
                    )
                    if (hasFilters) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$filterCount",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sort Button (Groww style)
        item {
            val isCustomSort = sortOption != LedgerSortOption.DATE_DESC
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSort),
                shape = RoundedCornerShape(12.dp),
                color = if (isCustomSort) PrimaryBlue.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isCustomSort) PrimaryBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = if (isCustomSort) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isCustomSort) sortOption.label else "Sort by",
                        fontWeight = if (isCustomSort) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (isCustomSort) PrimaryBlue else MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = if (isCustomSort) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Active filter pills
        if (filterState.onlyNeedsReview) {
            item {
                ActiveFilterChip(
                    label = "Needs Review",
                    onRemove = onClearReview
                )
            }
        }
        items(filterState.selectedTypes.toList()) { type ->
            ActiveFilterChip(
                label = type.name.lowercase().replaceFirstChar { it.uppercase() },
                onRemove = { onRemoveType(type) }
            )
        }
        items(filterState.selectedCategories.toList()) { cat ->
            ActiveFilterChip(
                label = cat,
                onRemove = { onRemoveCategory(cat) }
            )
        }
        items(filterState.selectedAccounts.toList()) { acc ->
            ActiveFilterChip(
                label = acc,
                onRemove = { onRemoveAccount(acc) }
            )
        }
        items(filterState.selectedCurrencies.toList()) { cur ->
            ActiveFilterChip(
                label = cur,
                onRemove = { onRemoveCurrency(cur) }
            )
        }

        if (filterCount > 1) {
            item {
                TextButton(
                    onClick = onClearAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "Clear all",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else if (filterCount == 0 && needsReviewCount > 0) {
            item {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleReview),
                    shape = RoundedCornerShape(12.dp),
                    color = WarningAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Review ($needsReviewCount)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WarningAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
