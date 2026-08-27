package com.wnoicew.expensetracker.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wnoicew.expensetracker.data.model.StatementParseResult
import com.wnoicew.expensetracker.ui.MainViewModel
import com.wnoicew.expensetracker.ui.components.HigGlassCard
import com.wnoicew.expensetracker.ui.components.HigInsetGroup
import com.wnoicew.expensetracker.ui.theme.IncomeGreen
import com.wnoicew.expensetracker.ui.theme.ExpenseRose
import com.wnoicew.expensetracker.ui.theme.PrimaryBlue
import com.wnoicew.expensetracker.ui.theme.WarningAmber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val uploads by viewModel.statementUploads.collectAsState()
    var parseResultToPreview by remember { mutableStateOf<StatementParseResult?>(null) }
    var currentImportFileName by remember { mutableStateOf("") }
    var isReadingFile by remember { mutableStateOf(false) }

    var parsingError by remember { mutableStateOf<com.wnoicew.expensetracker.data.engine.StatementParsingException?>(null) }
    var genericError by remember { mutableStateOf<String?>(null) }
    var currentProcessingFile by remember { mutableStateOf<String?>(null) }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault()) }

    BackHandler(enabled = parseResultToPreview != null) {
        parseResultToPreview = null
    }

    // Statement File Picker (PDF, CSV, Excel, TXT)
    val statementPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            parsingError = null
            genericError = null
            try {
                isReadingFile = true
                var fileName = "statement"
                // Try query filename from content resolver
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                } catch (_: Exception) {
                    fileName = uri.lastPathSegment ?: "statement.pdf"
                }

                currentImportFileName = fileName
                currentProcessingFile = fileName

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val parsed = viewModel.parseStatementStream(inputStream, fileName)
                    inputStream.close()
                    parseResultToPreview = parsed
                } else {
                    genericError = "Could not open file stream."
                }
            } catch (e: com.wnoicew.expensetracker.data.engine.StatementParsingException) {
                parsingError = e
            } catch (e: Exception) {
                genericError = e.message ?: "Failed to process statement file."
            } finally {
                isReadingFile = false
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
                text = "Upload Statements",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Universal offline bank, UPI & credit card statement ingestion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Upload Dropzone Card
        item {
            HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Upload PDF, CSV or Excel Statement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Upload PDF bank statements, UPI history & credit card bills. Parsed 100% offline in on-device memory.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Format Pills (Matching Web App)
                    val formats = listOf(
                        "PDF (All Banks)" to PrimaryBlue,
                        "Navi UPI" to PrimaryBlue.copy(alpha = 0.7f),
                        "PhonePe" to PrimaryBlue.copy(alpha = 0.7f),
                        "Paytm" to PrimaryBlue.copy(alpha = 0.7f),
                        "Google Pay" to PrimaryBlue.copy(alpha = 0.7f),
                        "SBI Bank" to IncomeGreen,
                        "HDFC Bank" to IncomeGreen,
                        "ICICI Bank" to IncomeGreen,
                        "Axis Bank" to IncomeGreen,
                        "Kotak Bank" to IncomeGreen,
                        "Credit Cards ✦" to ExpenseRose,
                        "Excel (.xlsx)" to MaterialTheme.colorScheme.outline,
                        "CSV" to MaterialTheme.colorScheme.outline
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(formats) { (fmt, color) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = color.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = fmt,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (color == MaterialTheme.colorScheme.outline) MaterialTheme.colorScheme.onSurfaceVariant else color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isReadingFile) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = PrimaryBlue,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Parsing ${currentProcessingFile ?: "statement"} offline...",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = PrimaryBlue
                                )
                                Text(
                                    text = "① Extracting text → ② Auto-detecting format → ③ Categorising",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { statementPickerLauncher.launch("*/*") },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select PDF or CSV Statement", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Detection Error State Card (if error occurred)
        if (parsingError != null || genericError != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ExpenseRose.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ExpenseRose.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚠️", fontSize = 20.sp)
                            Text(
                                text = parsingError?.title ?: "Failed to process statement",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ExpenseRose
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = parsingError?.detail ?: genericError ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = WarningAmber.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "💡 Helpful Tips",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningAmber
                                )
                                Text(
                                    text = "• If password-protected: open in viewer, save without password & upload.\n• Scanned image PDFs without text layer cannot be parsed offline.\n• Supports official statements from SBI, HDFC, ICICI, Axis, Kotak, Navi, PhonePe, Paytm & Credit Cards.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { statementPickerLauncher.launch("*/*") },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Try Another File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    parsingError = null
                                    genericError = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Dismiss", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 2. Feature Cards (Matching Web App)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeaturePillCard(
                    title = "Auto-Classifier",
                    subtitle = "Learns UPI IDs",
                    icon = Icons.Default.Bolt,
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
                FeaturePillCard(
                    title = "15+ Banks & Apps",
                    subtitle = "SBI, HDFC, Navi…",
                    icon = Icons.Default.AccountBalance,
                    color = IncomeGreen,
                    modifier = Modifier.weight(1f)
                )
                FeaturePillCard(
                    title = "Credit Cards",
                    subtitle = "All major cards",
                    icon = Icons.Default.CreditCard,
                    color = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                FeaturePillCard(
                    title = "Smart PDF Engine",
                    subtitle = "Multi-line support",
                    icon = Icons.Default.ContentCopy,
                    color = ExpenseRose,
                    modifier = Modifier.weight(1f)
                )
            }
        }


        // 3. Uploaded Statement History
        item {
            Text(
                text = "Uploaded Statement History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (uploads.isEmpty()) {
            item {
                HigGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No statements uploaded yet",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Uploaded statement logs will appear here.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                HigInsetGroup {
                    uploads.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = item.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${item.detectedBank} · ${dateFormat.format(Date(item.importDate))}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = IncomeGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${item.transactionCount} records",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (index < uploads.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }

    // Statement Parsed Preview Sheet
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
                    text = "Statement Import Preview",
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
                        viewModel.commitBatchTransactions(currentImportFileName, parsed.detectedProfile, parsed.transactions)
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
}

@Composable
private fun FeaturePillCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
