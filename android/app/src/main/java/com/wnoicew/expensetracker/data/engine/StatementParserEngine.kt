package com.wnoicew.expensetracker.data.engine

import com.wnoicew.expensetracker.data.model.RuleEntity
import com.wnoicew.expensetracker.data.model.StatementParseResult
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

object StatementParserEngine {

    private val supportedDateFormats = listOf(
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd/MM/yy",
        "dd-MM-yy",
        "dd-MMM-yyyy",
        "dd MMM yyyy",
        "MMM dd, yyyy",
        "yyyy-MM-dd HH:mm:ss",
        "dd/MM/yyyy HH:mm:ss",
        "yyyy/MM/dd"
    )

    fun parseCsvStream(
        inputStream: InputStream,
        fileName: String,
        accountId: String = "",
        accountName: String = "",
        customRules: List<RuleEntity> = emptyList()
    ): StatementParseResult {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines()
        return parseCsvLines(lines, fileName, accountId, accountName, customRules)
    }

    fun parseCsvLines(
        lines: List<String>,
        fileName: String,
        accountId: String = "",
        accountName: String = "",
        customRules: List<RuleEntity> = emptyList()
    ): StatementParseResult {
        if (lines.isEmpty()) {
            return StatementParseResult("Empty Statement", emptyList(), 0.0, 0.0)
        }

        var headerRowIndex = -1
        var headerCols = emptyList<String>()

        for (i in 0 until minOf(lines.size, 15)) {
            val cols = splitCsvLine(lines[i]).map { it.trim().lowercase() }
            val hasDate = cols.any { it.contains("date") || it.contains("txn date") || it.contains("trans date") }
            val hasAmountOrDesc = cols.any {
                it.contains("amount") || it.contains("debit") || it.contains("credit") ||
                        it.contains("description") || it.contains("narration") || it.contains("particulars")
            }
            if (hasDate && hasAmountOrDesc) {
                headerRowIndex = i
                headerCols = cols
                break
            }
        }

        if (headerRowIndex == -1) {
            // Fallback: assume row 0 is header
            headerRowIndex = 0
            headerCols = splitCsvLine(lines[0]).map { it.trim().lowercase() }
        }

        // Map column indices
        var dateIdx = headerCols.indexOfFirst { it.contains("date") }
        val descIdx = headerCols.indexOfFirst {
            it.contains("description") || it.contains("narration") || it.contains("particulars") ||
                    it.contains("remarks") || it.contains("details") || it.contains("payee")
        }
        val amountIdx = headerCols.indexOfFirst { it == "amount" || it.contains("amount (inr)") || it.contains("txn amount") }
        val debitIdx = headerCols.indexOfFirst { it.contains("debit") || it.contains("withdrawal") || it.contains("dr") }
        val creditIdx = headerCols.indexOfFirst { it.contains("credit") || it.contains("deposit") || it.contains("cr") }
        val refIdx = headerCols.indexOfFirst { it.contains("ref") || it.contains("utr") || it.contains("chq") || it.contains("reference") }
        val typeIdx = headerCols.indexOfFirst { it == "type" || it.contains("txn type") || it.contains("cr/dr") }

        val detectedProfile = detectProfile(fileName, headerCols)
        val parsedList = mutableListOf<TransactionEntity>()
        var totalInflow = 0.0
        var totalOutflow = 0.0

        for (i in (headerRowIndex + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue

            val cols = splitCsvLine(line)
            if (cols.size <= maxOf(dateIdx, descIdx, amountIdx, debitIdx, creditIdx)) continue

            val rawDate = if (dateIdx >= 0 && dateIdx < cols.size) cols[dateIdx] else ""
            val rawDesc = if (descIdx >= 0 && descIdx < cols.size) cols[descIdx] else "Transaction"
            val rawRef = if (refIdx >= 0 && refIdx < cols.size) cols[refIdx] else ""

            val timestamp = parseDate(rawDate) ?: System.currentTimeMillis()

            var amount = 0.0
            var explicitType: TransactionType? = null

            if (debitIdx >= 0 && debitIdx < cols.size && cleanAmount(cols[debitIdx]) > 0) {
                amount = cleanAmount(cols[debitIdx])
                explicitType = TransactionType.EXPENSE
            } else if (creditIdx >= 0 && creditIdx < cols.size && cleanAmount(cols[creditIdx]) > 0) {
                amount = cleanAmount(cols[creditIdx])
                explicitType = TransactionType.INCOME
            } else if (amountIdx >= 0 && amountIdx < cols.size) {
                val rawAmt = cols[amountIdx]
                val amtVal = cleanAmount(rawAmt)
                amount = abs(amtVal)

                if (typeIdx >= 0 && typeIdx < cols.size) {
                    val typeStr = cols[typeIdx].lowercase()
                    if (typeStr.contains("cr") || typeStr.contains("credit") || typeStr.contains("income")) {
                        explicitType = TransactionType.INCOME
                    } else if (typeStr.contains("dr") || typeStr.contains("debit") || typeStr.contains("expense")) {
                        explicitType = TransactionType.EXPENSE
                    }
                } else if (rawAmt.contains("-") || rawAmt.lowercase().contains("dr")) {
                    explicitType = TransactionType.EXPENSE
                } else if (rawAmt.contains("+") || rawAmt.lowercase().contains("cr")) {
                    explicitType = TransactionType.INCOME
                }
            }

            if (amount <= 0) continue

            // Run categorization engine
            val catResult = CategorizerEngine.categorize(rawDesc, amount, customRules)
            val finalType = explicitType ?: catResult.type

            val entity = TransactionEntity(
                date = timestamp,
                description = catResult.cleanTitle,
                amount = amount,
                type = finalType,
                category = catResult.category,
                accountId = accountId,
                accountName = accountName.ifBlank { detectedProfile },
                referenceNo = rawRef.trim(),
                paymentMode = if (rawDesc.contains("upi", ignoreCase = true)) "UPI" else "Online / Card",
                sourceFile = fileName,
                rawNarration = rawDesc,
                needsReview = catResult.needsReview,
                confidence = catResult.confidence
            )

            parsedList.add(entity)

            if (finalType == TransactionType.INCOME) {
                totalInflow += amount
            } else if (finalType == TransactionType.EXPENSE) {
                totalOutflow += amount
            }
        }

        return StatementParseResult(
            detectedProfile = detectedProfile,
            transactions = parsedList,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow
        )
    }

    private fun detectProfile(fileName: String, headers: List<String>): String {
        val f = fileName.lowercase()
        return when {
            f.contains("hdfc") -> "HDFC Bank Statement"
            f.contains("sbi") -> "State Bank of India Statement"
            f.contains("icici") -> "ICICI Bank Statement"
            f.contains("axis") -> "Axis Bank Statement"
            f.contains("gpay") || f.contains("googlepay") -> "Google Pay Statement"
            f.contains("phonepe") -> "PhonePe Statement"
            f.contains("paytm") -> "Paytm Statement"
            f.contains("cred") -> "CRED Statement"
            else -> "CSV Statement Import"
        }
    }

    private fun cleanAmount(raw: String): Double {
        val cleaned = raw.replace(Regex("""[^\d.-]"""), "")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    private fun parseDate(dateStr: String): Long? {
        val clean = dateStr.trim().replace(Regex("""\s+"""), " ")
        if (clean.isBlank()) return null

        for (pattern in supportedDateFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = true }
                val d = sdf.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString().trim(' ', '\"'))
                cur = StringBuilder()
            } else {
                cur.append(ch)
            }
        }
        result.add(cur.toString().trim(' ', '\"'))
        return result
    }
}
