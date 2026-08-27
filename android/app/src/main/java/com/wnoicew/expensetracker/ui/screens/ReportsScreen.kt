package com.wnoicew.expensetracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

@Composable
fun ReportsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val inflow by viewModel.totalInflow30D.collectAsState()
    val outflow by viewModel.totalOutflow30D.collectAsState()

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    // JSON Restore Launcher
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
                text = "Reports & Exports",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Comprehensive Multi-Format Export Center",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Current Reconciled Snapshot Metrics (Matching Web App)
        item {
            HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "CURRENT RECONCILED SNAPSHOT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOTAL INFLOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currencyFormat.format(inflow), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = IncomeGreen)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOTAL OUTFLOW", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currencyFormat.format(outflow), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ExpenseRose)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("NET SAVINGS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val retained = (inflow - outflow).coerceAtLeast(0.0)
                        Text(currencyFormat.format(retained), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            }
        }

        // 2. Export Option Cards
        item {
            Text(
                text = "Export Formats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // CSV Export Card
        item {
            ExportOptionCard(
                title = "Raw Transactions (CSV)",
                description = "Export cleaned, deduplicated, RFC4180 standard CSV file for spreadsheets and accounting software.",
                icon = Icons.Default.Description,
                buttonText = "Share / Download CSV",
                color = PrimaryBlue,
                onClick = {
                    coroutineScope.launch {
                        val csv = viewModel.exportCsvString()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, csv)
                            putExtra(Intent.EXTRA_TITLE, "Money_Tracker_Transactions.csv")
                            type = "text/csv"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Transactions CSV"))
                    }
                }
            )
        }

        // JSON Full Profile Backup Card
        item {
            ExportOptionCard(
                title = "Full Profile Backup (JSON)",
                description = "Export a complete structured JSON database backup containing all transactions, connected accounts, and learned classification rules.",
                icon = Icons.Default.Backup,
                buttonText = "Share Full JSON Backup",
                color = IncomeGreen,
                onClick = {
                    coroutineScope.launch {
                        val json = viewModel.exportJsonBackupString()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_TITLE, "MoneyTracker_Profile_Backup.json")
                            type = "application/json"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share JSON Backup"))
                    }
                }
            )
        }

        // Restore Backup Card
        item {
            ExportOptionCard(
                title = "Restore Profile Backup",
                description = "Restore transactions, accounts, and learned AI rules from a previously exported Money Tracker JSON backup file.",
                icon = Icons.Default.Restore,
                buttonText = "Select JSON Backup File",
                color = ExpenseRose,
                onClick = {
                    jsonRestoreLauncher.launch("application/json")
                }
            )
        }
    }
}

@Composable
private fun ExportOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    buttonText: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
