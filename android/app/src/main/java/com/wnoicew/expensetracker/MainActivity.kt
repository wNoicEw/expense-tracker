package com.wnoicew.expensetracker

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.engine.CurrencyEngine
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.components.HigInsetGroup
import com.wnoicew.expensetracker.ui.screens.*
import com.wnoicew.expensetracker.ui.theme.MoneyTrackerTheme
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.WarningAmber
import com.wnoicew.expensetracker.ui.theme.WarningAmberFill
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose

enum class BottomTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    TRANSACTIONS("Ledger", Icons.Filled.Receipt, Icons.Outlined.Receipt),
    UPLOAD("Upload", Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload),
    REVIEW("Review", Icons.AutoMirrored.Filled.HelpOutline, Icons.AutoMirrored.Outlined.HelpOutline),
    MORE("More", Icons.Filled.GridView, Icons.Outlined.GridView)
}

enum class SubScreen {
    NONE,
    ACCOUNTS,
    DUPLICATES,
    RULES,
    REPORTS,
    CURRENCY_RATES,
    PERSONAL_TRANSACTIONS
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupHighRefreshRate()
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Exception) {}
        enableEdgeToEdge()

        setContent {
            val isDarkMode by viewModel.isDarkMode
            MoneyTrackerTheme(darkTheme = isDarkMode) {
                MainAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupHighRefreshRate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupHighRefreshRate()
        }
    }

    /**
     * Explicitly requests the display's maximum supported refresh rate (e.g. 144Hz, 120Hz, 90Hz).
     * Bypasses OEM vendor display driver policies that throttle non-gaming apps to 60Hz.
     */
    private fun setupHighRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }
                val modes = currentDisplay?.supportedModes
                val maxMode = modes?.maxByOrNull { it.refreshRate }
                if (maxMode != null && maxMode.refreshRate > 60f) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        lp.preferredRefreshRate = maxMode.refreshRate
                    }
                    window.attributes = lp
                }
            }
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppRoot(viewModel: MainViewModel) {
    val activeProfile by viewModel.activeProfile
    val isDarkMode by viewModel.isDarkMode
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.DASHBOARD) }
    val tabBackStack = rememberSaveable(
        saver = listSaver(
            save = { stack -> stack.map { it.name } },
            restore = { names -> names.map { BottomTab.valueOf(it) }.toMutableStateList() }
        )
    ) { mutableStateListOf(BottomTab.DASHBOARD) }
    var activeSubScreen by rememberSaveable { mutableStateOf(SubScreen.NONE) }
    var showProfileManagerSheet by rememberSaveable { mutableStateOf(false) }
    var showAddTxnSheet by rememberSaveable { mutableStateOf(false) }
    var addTxnPrefillCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var addTxnPrefillDesc by rememberSaveable { mutableStateOf<String?>(null) }

    fun navigateToTab(tab: BottomTab) {
        activeSubScreen = SubScreen.NONE
        if (selectedTab != tab) {
            selectedTab = tab
            if (tabBackStack.isEmpty() || tabBackStack.last() != tab) {
                tabBackStack.add(tab)
            }
        }
    }

    // Native Back Button Handling
    val canHandleBack = showAddTxnSheet || showProfileManagerSheet || activeSubScreen != SubScreen.NONE || tabBackStack.size > 1 || selectedTab != BottomTab.DASHBOARD

    BackHandler(enabled = canHandleBack) {
        when {
            showAddTxnSheet -> showAddTxnSheet = false
            showProfileManagerSheet -> showProfileManagerSheet = false
            activeSubScreen != SubScreen.NONE -> activeSubScreen = SubScreen.NONE
            tabBackStack.size > 1 -> {
                tabBackStack.removeAt(tabBackStack.lastIndex)
                selectedTab = tabBackStack.last()
            }
            selectedTab != BottomTab.DASHBOARD -> {
                selectedTab = BottomTab.DASHBOARD
                tabBackStack.clear()
                tabBackStack.add(BottomTab.DASHBOARD)
            }
        }
    }

    val needsReviewCount by viewModel.needsReviewCount.collectAsState()
    val duplicatePairs = viewModel.duplicatePairs
    val accounts by viewModel.accounts.collectAsState()

    AnimatedContent(
        targetState = activeProfile != null,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "profile_gate"
    ) { hasActiveProfile ->
        if (!hasActiveProfile) {
            // Full Screen Profile Chooser Screen (Matching Web App)
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
                            val selected = selectedTab == tab && activeSubScreen == SubScreen.NONE
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navigateToTab(tab)
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (tab == BottomTab.REVIEW && needsReviewCount > 0) {
                                                Badge(containerColor = WarningAmberFill, contentColor = Color.Black) {
                                                    Text(needsReviewCount.toString(), fontWeight = FontWeight.Bold)
                                                }
                                            } else if (tab == BottomTab.MORE && duplicatePairs.isNotEmpty()) {
                                                Badge(containerColor = PrimaryBlue, contentColor = Color.White) {
                                                    Text(duplicatePairs.size.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = when {
                                                tab == BottomTab.REVIEW && needsReviewCount > 0 -> "${tab.title}, $needsReviewCount pending"
                                                tab == BottomTab.MORE && duplicatePairs.isNotEmpty() -> "${tab.title}, ${duplicatePairs.size} duplicates to resolve"
                                                else -> tab.title
                                            }
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
                    if (activeSubScreen != SubScreen.NONE) {
                        // Sub-screens Navigation with Top Back Bar
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { activeSubScreen = SubScreen.NONE }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Text(
                                    text = when (activeSubScreen) {
                                        SubScreen.ACCOUNTS -> "Cards & Accounts"
                                        SubScreen.DUPLICATES -> "Duplicate Resolver"
                                        SubScreen.RULES -> "Learned Rules"
                                        SubScreen.REPORTS -> "Reports & Exports"
                                        SubScreen.CURRENCY_RATES -> "Currency & Rates"
                                        SubScreen.PERSONAL_TRANSACTIONS -> "Personal Transactions"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            when (activeSubScreen) {
                                SubScreen.ACCOUNTS -> AccountsScreen(viewModel = viewModel)
                                SubScreen.DUPLICATES -> DuplicateResolverScreen(viewModel = viewModel)
                                SubScreen.RULES -> LearnedRulesScreen(viewModel = viewModel)
                                SubScreen.REPORTS -> ReportsScreen(viewModel = viewModel)
                                SubScreen.CURRENCY_RATES -> CurrencyRatesScreen(viewModel = viewModel)
                                SubScreen.PERSONAL_TRANSACTIONS -> PersonalTransactionsScreen(
                                    viewModel = viewModel,
                                    onOpenAddFriendTxn = { prefillName ->
                                        addTxnPrefillCategory = "Friend"
                                        addTxnPrefillDesc = prefillName ?: ""
                                        showAddTxnSheet = true
                                    }
                                )
                                else -> {}
                            }
                        }
                    } else {
                        when (selectedTab) {
                            BottomTab.DASHBOARD -> DashboardScreen(
                                viewModel = viewModel,
                                onOpenProfileManager = { showProfileManagerSheet = true },
                                onNavigateToTransactions = { navigateToTab(BottomTab.TRANSACTIONS) },
                                onNavigateToAccounts = { activeSubScreen = SubScreen.ACCOUNTS },
                                onNavigateToReview = { navigateToTab(BottomTab.REVIEW) },
                                onNavigateToUpload = { navigateToTab(BottomTab.UPLOAD) },
                                onOpenAddTransaction = { showAddTxnSheet = true }
                            )
                            BottomTab.TRANSACTIONS -> TransactionsScreen(
                                viewModel = viewModel
                            )
                            BottomTab.UPLOAD -> UploadScreen(
                                viewModel = viewModel
                            )
                            BottomTab.REVIEW -> NeedsReviewScreen(
                                viewModel = viewModel
                            )
                            BottomTab.MORE -> MoreMenuScreen(
                                viewModel = viewModel,
                                onNavigateToAccounts = { activeSubScreen = SubScreen.ACCOUNTS },
                                onNavigateToPersonalTransactions = { activeSubScreen = SubScreen.PERSONAL_TRANSACTIONS },
                                onNavigateToDuplicates = { activeSubScreen = SubScreen.DUPLICATES },
                                onNavigateToRules = { activeSubScreen = SubScreen.RULES },
                                onNavigateToReports = { activeSubScreen = SubScreen.REPORTS },
                                onNavigateToCurrencyRates = { activeSubScreen = SubScreen.CURRENCY_RATES },
                                onOpenProfileManager = { showProfileManagerSheet = true }
                            )
                        }
                    }
                }
            }

            // Profile Manager Modal Sheet
            if (showProfileManagerSheet) {
                ProfileManagerSheet(
                    profileManager = viewModel.profileManager,
                    onDismiss = { showProfileManagerSheet = false }
                )
            }

            // Add Transaction Modal Sheet
            if (showAddTxnSheet) {
                val profileCur = activeProfile?.currency ?: CurrencyEngine.DEFAULT_CURRENCY
                AddTransactionBottomSheet(
                    accounts = accounts.map { it.name },
                    defaultCurrency = profileCur,
                    initialCategory = addTxnPrefillCategory,
                    initialDescription = addTxnPrefillDesc,
                    onDismiss = {
                        showAddTxnSheet = false
                        addTxnPrefillCategory = null
                        addTxnPrefillDesc = null
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
                        showAddTxnSheet = false
                        addTxnPrefillCategory = null
                        addTxnPrefillDesc = null
                    }
                )
            }
        }
    }
}

@Composable
fun MoreMenuScreen(
    viewModel: MainViewModel,
    onNavigateToAccounts: () -> Unit,
    onNavigateToPersonalTransactions: () -> Unit,
    onNavigateToDuplicates: () -> Unit,
    onNavigateToRules: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToCurrencyRates: () -> Unit,
    onOpenProfileManager: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode
    val duplicatePairs = viewModel.duplicatePairs
    val rules by viewModel.rules.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val friendsCount by viewModel.totalFriendsCount.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "More Features",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Cards, Deduplication, AI Memory & Theme Settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Theme Toggle Row (Matching Web App Dark / Light Switch)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { viewModel.toggleTheme() },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDarkMode) WarningAmber.copy(alpha = 0.15f) else PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightlightRound,
                                contentDescription = null,
                                tint = if (isDarkMode) WarningAmber else PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isDarkMode) "Dark Mode (Active)" else "Light Mode (Active)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap to switch to ${if (isDarkMode) "Light" else "Dark"} Mode",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.toggleTheme() }
                    )
                }
            }
        }

        // Menu Items Inset Group
        item {
            HigInsetGroup {
                // 1. Cards & Accounts
                MoreMenuRow(
                    title = "Cards & Accounts",
                    subtitle = "${accounts.size} connected cards & wallets",
                    icon = Icons.Default.CreditCard,
                    iconColor = PrimaryBlue,
                    badgeText = null,
                    onClick = onNavigateToAccounts,
                    showDivider = true
                )

                // 2. Personal Transactions (Friends & P2P Balances)
                MoreMenuRow(
                    title = "Personal Transactions",
                    subtitle = "Friends, UPI IDs & peer-to-peer balances",
                    icon = Icons.Default.People,
                    iconColor = Color(0xFFA855F7),
                    badgeText = if (friendsCount > 0) "$friendsCount Friends" else null,
                    onClick = onNavigateToPersonalTransactions,
                    showDivider = true
                )

                // 3. Duplicate Resolver
                MoreMenuRow(
                    title = "Duplicate Resolver",
                    subtitle = "Cross-statement transaction matching",
                    icon = Icons.Default.CopyAll,
                    iconColor = ExpenseRose,
                    badgeText = if (duplicatePairs.isNotEmpty()) "${duplicatePairs.size} Pending" else null,
                    onClick = onNavigateToDuplicates,
                    showDivider = true
                )

                // 3. Learned Rules
                MoreMenuRow(
                    title = "Learned Rules",
                    subtitle = "AI auto-classification memory (${rules.size})",
                    icon = Icons.Default.Psychology,
                    iconColor = IncomeGreen,
                    badgeText = null,
                    onClick = onNavigateToRules,
                    showDivider = true
                )

                // 4. Reports & Exports
                MoreMenuRow(
                    title = "Export Reports",
                    subtitle = "CSV, JSON backup & recovery",
                    icon = Icons.Default.FileDownload,
                    iconColor = PrimaryBlue,
                    badgeText = null,
                    onClick = onNavigateToReports,
                    showDivider = true
                )

                // 5. Currency & Exchange Rates
                MoreMenuRow(
                    title = "Currency & Exchange Rates",
                    subtitle = "Real-time rates, manual overrides & API sync",
                    icon = Icons.Default.CurrencyExchange,
                    iconColor = WarningAmber,
                    badgeText = null,
                    onClick = onNavigateToCurrencyRates,
                    showDivider = true
                )

                // 6. Profiles Manager
                MoreMenuRow(
                    title = "Manage Profiles",
                    subtitle = "Switch, rename, or create user memory profiles",
                    icon = Icons.Default.People,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    badgeText = null,
                    onClick = onOpenProfileManager,
                    showDivider = false
                )
            }
        }
    }
}

@Composable
private fun MoreMenuRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    badgeText: String?,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }

                Column {
                    Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 14.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
        }
    }
}
