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
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.screens.*
import com.wnoicew.expensetracker.ui.theme.MoneyTrackerTheme

enum class BottomTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TRANSACTIONS("Transactions", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    ACCOUNTS("Accounts", Icons.Filled.CreditCard, Icons.Outlined.CreditCard)
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

@Composable
fun MainAppRoot(viewModel: MainViewModel) {
    val activeProfile by viewModel.activeProfile
    var selectedTab by remember { mutableStateOf(BottomTab.DASHBOARD) }
    var showProfileManagerSheet by remember { mutableStateOf(false) }

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
                        tonalElevation = 8.dp
                    ) {
                        BottomTab.values().forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                },
                                label = { Text(tab.title) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
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
                            onNavigateToAccounts = { selectedTab = BottomTab.ACCOUNTS }
                        )
                        BottomTab.TRANSACTIONS -> TransactionsScreen(
                            viewModel = viewModel
                        )
                        BottomTab.ACCOUNTS -> AccountsScreen(
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
