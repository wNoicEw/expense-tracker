package com.wnoicew.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.screens.*
import com.wnoicew.expensetracker.ui.theme.MoneyTrackerTheme
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue

enum class BottomTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TRANSACTIONS("Ledger", Icons.Filled.Receipt, Icons.Outlined.Receipt),
    BUDGETS("Budgets", Icons.Filled.PieChart, Icons.Outlined.PieChart),
    ACCOUNTS("Cards", Icons.Filled.CreditCard, Icons.Outlined.CreditCard),
    TOOLS("Tools", Icons.Filled.Tune, Icons.Outlined.Tune)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MoneyTrackerTheme {
                MainAppRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppRoot(viewModel: MainViewModel) {
    val activeProfile by viewModel.activeProfile
    var selectedTab by remember { mutableStateOf(BottomTab.DASHBOARD) }
    var showProfileManagerSheet by remember { mutableStateOf(false) }
    val needsReviewCount by viewModel.needsReviewCount.collectAsState()
    val duplicatePairs = viewModel.duplicatePairs

    AnimatedContent(
        targetState = activeProfile != null,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "profile_gate"
    ) { hasActiveProfile ->
        if (!hasActiveProfile) {
            // Profile Gate: Show Full Screen Profile Chooser
            ProfileChooserScreen(
                profileManager = viewModel.profileManager,
                onProfileSelected = { profile ->
                    viewModel.profileManager.setActiveProfile(profile.id)
                }
            )
        } else {
            // Main App Navigation
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        BottomTab.values().forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab == BottomTab.TRANSACTIONS && needsReviewCount > 0) {
                                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                                    Text(needsReviewCount.toString())
                                                }
                                            } else if (tab == BottomTab.TOOLS && duplicatePairs.isNotEmpty()) {
                                                Badge(containerColor = PrimaryBlue) {
                                                    Text(duplicatePairs.size.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title
                                        )
                                    }
                                },
                                label = { Text(tab.title, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryBlue,
                                    selectedTextColor = PrimaryBlue,
                                    indicatorColor = PrimaryBlue.copy(alpha = 0.12f)
                                )
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        BottomTab.DASHBOARD -> DashboardScreen(
                            viewModel = viewModel,
                            onOpenProfileManager = { showProfileManagerSheet = true },
                            onNavigateToTransactions = { selectedTab = BottomTab.TRANSACTIONS },
                            onNavigateToAccounts = { selectedTab = BottomTab.ACCOUNTS },
                            onNavigateToBudgets = { selectedTab = BottomTab.BUDGETS },
                            onNavigateToTools = { selectedTab = BottomTab.TOOLS }
                        )
                        BottomTab.TRANSACTIONS -> TransactionsScreen(
                            viewModel = viewModel
                        )
                        BottomTab.BUDGETS -> BudgetsScreen(
                            viewModel = viewModel
                        )
                        BottomTab.ACCOUNTS -> AccountsScreen(
                            viewModel = viewModel
                        )
                        BottomTab.TOOLS -> ToolsScreen(
                            viewModel = viewModel
                        )
                    }
                }
            }

            // Profile Manager Sheet
            if (showProfileManagerSheet) {
                ProfileManagerSheet(
                    profileManager = viewModel.profileManager,
                    onDismiss = { showProfileManagerSheet = false }
                )
            }
        }
    }
}
