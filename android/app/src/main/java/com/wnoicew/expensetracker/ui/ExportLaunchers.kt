package com.wnoicew.expensetracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class ExportLaunchers(
    val exportCsv: () -> Unit,
    val exportBackup: () -> Unit,
    val restoreBackup: () -> Unit
)

@Composable
fun rememberExportLaunchers(viewModel: MainViewModel): ExportLaunchers {
    val context = LocalContext.current
    val onDone: (Boolean, String) -> Unit = { _, message ->
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportKind.CSV.mimeType)
    ) { uri -> if (uri != null) viewModel.saveExport(uri, ExportKind.CSV, onDone) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportKind.JSON.mimeType)
    ) { uri -> if (uri != null) viewModel.saveExport(uri, ExportKind.JSON, onDone) }

    // OpenDocument + permissive MIME types: some apps save backups as text/plain or octet-stream,
    // which a strict "application/json" GetContent filter greys out. Content is validated on restore.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.restoreFromUri(uri, onDone) }

    return remember(csvLauncher, jsonLauncher, restoreLauncher) {
        ExportLaunchers(
            exportCsv = { csvLauncher.launch(ExportKind.CSV.defaultFileName()) },
            exportBackup = { jsonLauncher.launch(ExportKind.JSON.defaultFileName()) },
            restoreBackup = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        )
    }
}
