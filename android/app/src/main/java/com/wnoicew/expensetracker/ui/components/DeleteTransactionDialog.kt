package com.wnoicew.expensetracker.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wnoicew.expensetracker.data.model.TransactionEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Destructive-action alert: the consequence is named, Cancel is the safe default on the leading
 * side, and only the Delete label carries the destructive color.
 */
@Composable
fun DeleteTransactionDialog(
    transaction: TransactionEntity,
    currencyFormat: NumberFormat,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val date = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(transaction.date))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Transaction?") },
        text = {
            Text(
                "“${transaction.description}”, ${currencyFormat.format(transaction.amount)} on $date, " +
                    "will be permanently removed. This can’t be undone."
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    )
}
