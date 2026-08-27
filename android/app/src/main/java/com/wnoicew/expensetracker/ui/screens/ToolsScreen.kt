package com.wnoicew.expensetracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.model.DuplicatePair
import com.wnoicew.expensetracker.data.model.RuleEntity
import com.wnoicew.expensetracker.data.model.StatementParseResult
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val rules by viewModel.rules.collectAsState()
    val duplicatePairs = viewModel.duplicatePairs
    val accounts by viewModel.accounts.collectAsState()

    var selectedToolIndex by remember { mutableIntStateOf(0) } // 0: Import, 1: Duplicates, 2: Rules, 3: Backup
    var parseResultToPreview by remember { mutableStateOf<StatementParseResult?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    var showAddRuleSheet by remember { mutableStateOf(false) }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    // CSV File Picker
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                isImporting = true
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val lines = reader.readLines()
                    inputStream.close()

                    val fileName = uri.lastPathSegment ?: "statement.csv"
                    val parsed = viewModel.parseCsvStatement(lines, fileName)
                    parseResultToPreview = parsed
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isImporting = false
            }
        }
    }

    // JSON Restore File Picker
    val jsonRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val jsonStr = inputStream.bufferedReader().use { it.readText() }
                    viewModel.restoreJsonBackup(jsonStr) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Financial Tools",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Offline statement ingestion, duplicate reconciliation & rule engine",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Tools Navigation Segmented Control
        item {
            HigSegmentedControl(
                items = listOf("Import", "Duplicates (${duplicatePairs.size})", "Rules (${rules.size})", "Backup"),
                selectedIndex = selectedToolIndex,
                onItemSelected = { selectedToolIndex = it }
            )
        }

        when (selectedToolIndex) {
            0 -> {
                // PANEL 1: BANK STATEMENT IMPORT
                item {
                    HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Universal Statement Importer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Import Bank or UPI CSV statements (HDFC, SBI, ICICI, Axis, GPay, PhonePe, Paytm, CRED) with 100% on-device auto-categorization.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { csvPickerLauncher.launch("text/*") },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Statement CSV File", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Supported Financial Profiles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item {
                    HigInsetGroup {
                        val supported = listOf(
                            "Google Pay & PhonePe Statement CSVs",
                            "HDFC Bank & SBI NetBanking Exports",
                            "ICICI Bank & Axis Bank Statements",
                            "CRED Credit Card Consolidated Records",
                            "Generic Financial CSV & Spreadsheet Exports"
                        )
                        supported.forEachIndexed { index, name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(16.dp))
                                Text(text = name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (index < supported.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            1 -> {
                // PANEL 2: DUPLICATE RESOLVER
                if (duplicatePairs.isEmpty()) {
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
                                    text = "All Statements Reconciled",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Zero cross-statement duplicate entries found.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "Detected Duplicate Pairs (${duplicatePairs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(duplicatePairs) { pair ->
                        DuplicatePairCard(
                            pair = pair,
                            currencyFormat = currencyFormat,
                            onMerge = { viewModel.mergeDuplicatePair(pair) },
                            onSeparate = { viewModel.markDuplicateAsSeparate(pair) }
                        )
                    }
                }
            }

            2 -> {
                // PANEL 3: SMART RULE MANAGER
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Categorization Rules (${rules.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Button(
                            onClick = { showAddRuleSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Rule", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (rules.isEmpty()) {
                    item {
                        HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No custom learned rules yet",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Rules are automatically learned when you reclassify transactions, or you can add custom keyword patterns manually.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    item {
                        HigInsetGroup {
                            rules.forEachIndexed { index, r ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "\"${r.pattern}\"",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Auto-assigns to ${r.category} (${r.type.name})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(onClick = { viewModel.deleteRule(r) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (index < rules.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // PANEL 4: DATA EXPORT & BACKUP
                item {
                    HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Export & Financial Backup Suite",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Export your entire financial database as clean CSV for spreadsheets or backup encrypted JSON for safe profile migration.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val csv = viewModel.exportCsvString()
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, csv)
                                            putExtra(Intent.EXTRA_TITLE, "Money_Tracker_Ledger.csv")
                                            type = "text/csv"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Transactions CSV"))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Transactions CSV", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val json = viewModel.exportJsonBackupString()
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, json)
                                            putExtra(Intent.EXTRA_TITLE, "MoneyTracker_Backup.json")
                                            type = "application/json"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Full JSON Backup"))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Full JSON Profile Backup", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }

                            OutlinedButton(
                                onClick = { jsonRestoreLauncher.launch("application/json") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore from JSON Backup File", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Statement CSV Parsed Preview Dialog
    parseResultToPreview?.let { parsed ->
        ModalBottomSheet(
            onDismissRequest = { parseResultToPreview = null },
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
                    text = "Import Statement Preview",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${parsed.detectedProfile} · ${parsed.transactions.size} records extracted",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("TOTAL INFLOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                            Text(currencyFormat.format(parsed.totalInflow), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("TOTAL OUTFLOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ExpenseRose)
                            Text(currencyFormat.format(parsed.totalOutflow), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ExpenseRose)
                        }
                    }
                }

                Text("Sample Entries (First 5):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HigInsetGroup {
                    parsed.transactions.take(5).forEachIndexed { index, txn ->
                        TransactionRowItem(
                            transaction = txn,
                            currencyFormat = currencyFormat,
                            showDivider = index < parsed.transactions.take(5).size - 1
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.commitBatchTransactions(parsed.transactions)
                        Toast.makeText(context, "Successfully imported ${parsed.transactions.size} transactions!", Toast.LENGTH_SHORT).show()
                        parseResultToPreview = null
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Commit & Ingest ${parsed.transactions.size} Transactions", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Add Rule Bottom Sheet
    if (showAddRuleSheet) {
        var pattern by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(ALL_CATEGORIES.first()) }
        var typeIndex by remember { mutableIntStateOf(0) }

        ModalBottomSheet(
            onDismissRequest = { showAddRuleSheet = false },
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
                    text = "Add Custom Categorization Rule",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Keyword Pattern / UPI ID") },
                    placeholder = { Text("e.g. chai point, merchant@upi") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category exposed dropdown
                var catExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category,
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
                                    category = cat
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (pattern.isNotBlank()) {
                            val type = if (typeIndex == 0) TransactionType.EXPENSE else TransactionType.INCOME
                            viewModel.learnRuleAndReclassify(pattern, category, type)
                            showAddRuleSheet = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Save & Apply Rule", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DuplicatePairCard(
    pair: DuplicatePair,
    currencyFormat: NumberFormat,
    onMerge: () -> Unit,
    onSeparate: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${pair.confidence}% Match",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = currencyFormat.format(pair.primaryTxn.amount),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = ExpenseRose
                )
            }

            Text(
                text = pair.reason,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Comparison Rows
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "1. ${pair.primaryTxn.description} (${pair.primaryTxn.sourceFile})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "2. ${pair.candidateTxn.description} (${pair.candidateTxn.sourceFile})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onMerge,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text("Merge & Enrich", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSeparate,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text("Keep Separate", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
