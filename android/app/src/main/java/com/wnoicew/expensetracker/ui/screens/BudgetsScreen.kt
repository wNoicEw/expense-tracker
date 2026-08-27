package com.wnoicew.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.model.CategoryBudgetStatus
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.components.HigInsetGroup
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: MainViewModel
) {
    val categoryBudgets by viewModel.categoryBudgetsStatus.collectAsState()
    val healthScore by viewModel.financialHealthScore.collectAsState()
    val savingsRate by viewModel.netSavingsRate.collectAsState()
    val inflow by viewModel.totalInflow30D.collectAsState()
    val outflow by viewModel.totalOutflow30D.collectAsState()

    var editingBudgetCategory by remember { mutableStateOf<CategoryBudgetStatus?>(null) }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val totalBudgetLimit = categoryBudgets.sumOf { it.monthlyBudget }
    val totalBudgetSpent = categoryBudgets.sumOf { it.spent }
    val totalRemaining = (totalBudgetLimit - totalBudgetSpent).coerceAtLeast(0.0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Budgets & Intelligence",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Monthly category limits & on-device financial health",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Financial Health Score Bento Card
        item {
            HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FINANCIAL HEALTH SCORE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val scoreText = when {
                            healthScore >= 80 -> "Excellent Health"
                            healthScore >= 60 -> "Good Standing"
                            healthScore >= 40 -> "Moderate Discipline"
                            else -> "Needs Attention"
                        }
                        Text(
                            text = scoreText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Savings rate: ${savingsRate.toInt()}% · Outflow: ${currencyFormat.format(outflow)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                if (healthScore >= 75) IncomeGreen.copy(alpha = 0.15f)
                                else if (healthScore >= 50) Color(0xFFF59E0B).copy(alpha = 0.15f)
                                else ExpenseRose.copy(alpha = 0.15f)
                            )
                            .border(
                                width = 3.dp,
                                color = if (healthScore >= 75) IncomeGreen else if (healthScore >= 50) Color(0xFFF59E0B) else ExpenseRose,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$healthScore",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (healthScore >= 75) IncomeGreen else if (healthScore >= 50) Color(0xFFF59E0B) else ExpenseRose
                        )
                    }
                }
            }
        }

        // 2. Aggregate Monthly Budget Overview
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("TOTAL BUDGET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currencyFormat.format(totalBudgetLimit), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("MONTHLY SPENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currencyFormat.format(totalBudgetSpent), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ExpenseRose)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("REMAINING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currencyFormat.format(totalRemaining), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                    }
                }
            }
        }

        // 3. Category Budgets Inset Group
        item {
            Text(
                text = "Category Allowances (${categoryBudgets.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(categoryBudgets, key = { it.categoryName }) { budget ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { editingBudgetCategory = budget },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PrimaryBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = budget.categoryName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Limit: ${currencyFormat.format(budget.monthlyBudget)} · Tap to edit",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = currencyFormat.format(budget.spent),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = if (budget.isExceeded) ExpenseRose else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${budget.percentage}% spent",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (budget.isExceeded) ExpenseRose else if (budget.percentage >= 80) Color(0xFFF59E0B) else IncomeGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val progress = (budget.percentage / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (budget.isExceeded) ExpenseRose else if (budget.percentage >= 80) Color(0xFFF59E0B) else IncomeGreen,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (budget.isExceeded) "Exceeded by ${currencyFormat.format(budget.spent - budget.monthlyBudget)}" else "Remaining: ${currencyFormat.format(budget.remaining)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (budget.isExceeded) ExpenseRose else IncomeGreen
                        )
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // Quick Edit Monthly Budget Sheet
    editingBudgetCategory?.let { budget ->
        var newLimitText by remember { mutableStateOf(budget.monthlyBudget.toInt().toString()) }

        ModalBottomSheet(
            onDismissRequest = { editingBudgetCategory = null },
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
                Text(
                    text = "Edit ${budget.categoryName} Budget",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Set monthly target spending allowance for this category.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = newLimitText,
                    onValueChange = { newLimitText = it },
                    label = { Text("Monthly Budget Limit (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val limit = newLimitText.toDoubleOrNull() ?: 5000.0
                        viewModel.updateBudget(budget.categoryName, limit)
                        editingBudgetCategory = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Save Budget Limit", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
