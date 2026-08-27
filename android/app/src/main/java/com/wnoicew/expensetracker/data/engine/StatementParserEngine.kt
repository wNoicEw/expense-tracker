package com.wnoicew.expensetracker.data.engine

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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

class StatementParsingException(
    val title: String,
    val detail: String,
    val detectedProfile: String? = null,
    val isDetectionFailure: Boolean = true,
    val isPasswordProtected: Boolean = false,
    val isIncorrectPassword: Boolean = false
) : Exception("$title: $detail")

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
        "dd MMM, yyyy",
        "yyyy-MM-dd HH:mm:ss",
        "dd/MM/yyyy HH:mm:ss",
        "yyyy/MM/dd"
    )

    // Regex patterns for date, amount, type
    private val dateRegex = Regex("""(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4})|(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*,?\s+\d{2,4})|((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{1,2},?\s+\d{4})""", RegexOption.IGNORE_CASE)
    private val amountRegex = Regex("""(?:[₹Rs.]|INR)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2}))\b""")

    fun parseStatementStream(
        inputStream: InputStream,
        fileName: String,
        accountId: String = "",
        accountName: String = "",
        customRules: List<RuleEntity> = emptyList(),
        password: String? = null
    ): StatementParseResult {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext == "pdf") {
            parsePdfStream(inputStream, fileName, accountId, accountName, customRules, password)
        } else {
            parseCsvStream(inputStream, fileName, accountId, accountName, customRules)
        }
    }

    // --- PDF PARSER ---
    fun parsePdfStream(
        inputStream: InputStream,
        fileName: String,
        accountId: String = "",
        accountName: String = "",
        customRules: List<RuleEntity> = emptyList(),
        password: String? = null
    ): StatementParseResult {
        val document: PDDocument
        try {
            document = if (!password.isNullOrEmpty()) {
                PDDocument.load(inputStream, password)
            } else {
                PDDocument.load(inputStream)
            }
        } catch (e: Exception) {
            val isPwError = e is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException ||
                    e.javaClass.name.contains("Password", ignoreCase = true) ||
                    (e.message ?: "").contains("password", ignoreCase = true)

            if (isPwError) {
                val isWrong = !password.isNullOrEmpty()
                throw StatementParsingException(
                    title = if (isWrong) "Incorrect PDF Password" else "Password-Protected PDF",
                    detail = if (isWrong) "The password entered is incorrect. Please check and try again." else "This PDF statement is password-protected by your bank or app (e.g. DOB, Account No, PAN).",
                    isDetectionFailure = true,
                    isPasswordProtected = true,
                    isIncorrectPassword = isWrong
                )
            } else {
                throw StatementParsingException(
                    title = "Unable to open PDF",
                    detail = "The PDF could not be opened (${e.message ?: "corrupted or unsupported"}). Please check the file.",
                    isDetectionFailure = true
                )
            }
        }

        val fullText: String
        try {
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }
            fullText = stripper.getText(document)
            document.close()
        } catch (_: Exception) {
            try { document.close() } catch (_: Exception) {}
            throw StatementParsingException(
                title = "Failed to extract text from PDF",
                detail = "Could not read text layers from this PDF. It might be a scanned image or protected.",
                isDetectionFailure = true
            )
        }

        if (fullText.isBlank()) {
            throw StatementParsingException(
                title = "Empty or Scanned PDF",
                detail = "No selectable text was found in this PDF. Scanned image PDFs are not supported in offline mode.",
                isDetectionFailure = true
            )
        }

        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val ft = fullText.lowercase()
        val fname = fileName.lowercase()

        val detectedBank = detectBankFromText(fullText, fileName)

        // Route to specialized parsers (SBI and UPI apps prioritized over generic matching)
        val result = when {
            ft.contains("paid via navi") || (ft.contains("upi txn id") && (ft.contains("navi") || fname.contains("navi"))) -> {
                parseNaviPdf(lines, fileName, accountId, accountName, customRules)
            }
            ft.contains("state bank of india") || ft.contains("sbi") || fname.contains("sbi") || ft.contains("wdl tfr") || ft.contains("dep tfr") || detectedBank.contains("sbi", ignoreCase = true) -> {
                parseSbiPdf(lines, fileName, accountId, accountName, customRules)
            }
            ft.contains("phonepe") || fname.contains("phonepe") -> {
                parsePhonePePdf(lines, fileName, accountId, accountName, customRules)
            }
            ft.contains("paytm") || fname.contains("paytm") -> {
                parsePaytmPdf(lines, fileName, accountId, accountName, customRules)
            }
            (ft.contains("credit card statement") || ft.contains("total amount due") || ft.contains("minimum amount due") || ft.contains("card statement")) && !ft.contains("wdl tfr") && !ft.contains("dep tfr") -> {
                parseCreditCardPdf(lines, fileName, detectedBank, accountId, accountName, customRules)
            }
            else -> {
                val generic = parseGenericTablePdf(lines, fileName, detectedBank, accountId, accountName, customRules)
                if (generic.transactions.isNotEmpty()) generic else {
                    parseFallbackLines(lines, fileName, detectedBank, accountId, accountName, customRules)
                }
            }
        }


        if (result.transactions.isEmpty()) {
            throw StatementParsingException(
                title = if (detectedBank != "Generic Statement") "Detected: \"$detectedBank\" — but no transactions found" else "Failed to detect statement format",
                detail = if (detectedBank != "Generic Statement") "The PDF format was recognized as $detectedBank, but no transactions could be extracted. The statement may be in an unsupported layout or password-locked." else "Could not identify financial transactions in this document. Please ensure it is an official bank, UPI, or credit card PDF.",
                detectedProfile = detectedBank,
                isDetectionFailure = true
            )
        }

        return result
    }

    // --- NAVI UPI PDF PARSER ---
    private fun parseNaviPdf(
        lines: List<String>,
        fileName: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        val anchorRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*\s+\d{4})\s+(Paid\s+to|Paid\s+for|Received\s+from|Refund\s+from)\s+(.+?)\s+[₹Rs.]*\s*([\d,]+(?:\.\d{1,2})?)$""", RegexOption.IGNORE_CASE)
        val list = mutableListOf<TransactionEntity>()
        var inflow = 0.0
        var outflow = 0.0

        for (i in lines.indices) {
            val m = anchorRe.find(lines[i]) ?: continue
            val rawDate = m.groupValues[1]
            val direction = m.groupValues[2].lowercase()
            val payee = m.groupValues[3].trim()
            val amt = cleanAmount(m.groupValues[4])
            if (amt <= 0) continue

            val isIncome = direction.contains("received") || direction.contains("refund")
            val explicitType = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE

            var bankInstrument = ""
            var txnId = ""
            var note = ""

            for (j in (i + 1)..minOf(i + 4, lines.lastIndex)) {
                val next = lines[j]
                if (Regex("""^\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)""", RegexOption.IGNORE_CASE).containsMatchIn(next)) break
                if (next.contains("UPI txn ID", ignoreCase = true)) {
                    val tid = Regex("""UPI\s+txn\s+ID[:\s]*(\d+)""", RegexOption.IGNORE_CASE).find(next)?.groupValues?.getOrNull(1)
                    if (tid != null) txnId = tid
                } else if (next.startsWith("Note:", ignoreCase = true)) {
                    note = next.replace(Regex("""^Note:\s*""", RegexOption.IGNORE_CASE), "").trim()
                } else if (bankInstrument.isBlank() && next.length in 3..50) {
                    bankInstrument = next
                }
            }

            val narration = (if (isIncome) "Received from " else "Paid to ") + payee + (if (note.isNotBlank()) " — $note" else "") + (if (bankInstrument.isNotBlank()) " [$bankInstrument]" else "")
            val cat = CategorizerEngine.categorize(narration, amt, customRules)
            val date = parseDate(rawDate) ?: System.currentTimeMillis()

            list.add(
                TransactionEntity(
                    date = date,
                    description = cat.cleanTitle,
                    amount = amt,
                    type = explicitType,
                    category = cat.category,
                    accountId = accountId,
                    accountName = accountName.ifBlank { "Navi UPI" },
                    referenceNo = txnId,
                    paymentMode = if (bankInstrument.contains("Credit Card", ignoreCase = true)) "UPI (RuPay Credit Card)" else "UPI",
                    sourceFile = fileName,
                    rawNarration = narration,
                    needsReview = cat.needsReview,
                    confidence = cat.confidence
                )
            )

            if (explicitType == TransactionType.INCOME) inflow += amt else outflow += amt
        }

        return StatementParseResult("Navi UPI Statement", list, inflow, outflow)
    }

    // --- PHONEPE STATEMENT PDF PARSER ---
    private fun parsePhonePePdf(
        lines: List<String>,
        fileName: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        val list = mutableListOf<TransactionEntity>()
        var inflow = 0.0
        var outflow = 0.0

        val typeRe = Regex("""\b(DEBIT|CREDIT|DR|CR)\b""", RegexOption.IGNORE_CASE)
        val txnIdRe = Regex("""(?:Transaction\s*ID|Txn\s*ID|T\d{20,})[:\s]*([T\w]{10,30})""", RegexOption.IGNORE_CASE)
        val utrRe = Regex("""UTR\s*No\.?\s*[:\s]*(\d{10,15})""", RegexOption.IGNORE_CASE)

        for (i in lines.indices) {
            val line = lines[i]
            val dateM = dateRegex.find(line)
            val amtM = amountRegex.find(line)
            val typeM = typeRe.find(line)

            if (dateM == null || amtM == null) continue
            val amt = cleanAmount(amtM.groupValues[1])
            if (amt <= 0) continue

            val isCredit = typeM?.value?.contains("CREDIT", ignoreCase = true) == true || typeM?.value?.contains("CR", ignoreCase = true) == true
            val explicitType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE

            var narration = line.replace(dateM.value, "").replace(amtM.value, "").replace(typeRe, "").trim()
            var refNo = ""

            for (j in (i + 1)..minOf(i + 3, lines.lastIndex)) {
                val next = lines[j]
                if (dateRegex.containsMatchIn(next) && amountRegex.containsMatchIn(next)) break
                val tid = txnIdRe.find(next)?.groupValues?.getOrNull(1)
                val utr = utrRe.find(next)?.groupValues?.getOrNull(1)
                if (utr != null) refNo = utr else if (tid != null && refNo.isBlank()) refNo = tid
                if (!next.matches(Regex("""^\d{1,2}:\d{2}.*""")) && tid == null && utr == null && next.length in 4..80) {
                    narration += " $next"
                }
            }

            val cat = CategorizerEngine.categorize(narration, amt, customRules)
            val date = parseDate(dateM.value) ?: System.currentTimeMillis()

            list.add(
                TransactionEntity(
                    date = date,
                    description = cat.cleanTitle,
                    amount = amt,
                    type = explicitType,
                    category = cat.category,
                    accountId = accountId,
                    accountName = accountName.ifBlank { "PhonePe Statement" },
                    referenceNo = refNo,
                    paymentMode = "UPI",
                    sourceFile = fileName,
                    rawNarration = narration,
                    needsReview = cat.needsReview,
                    confidence = cat.confidence
                )
            )

            if (explicitType == TransactionType.INCOME) inflow += amt else outflow += amt
        }

        return StatementParseResult("PhonePe UPI Statement", list, inflow, outflow)
    }

    // --- PAYTM PDF PARSER ---
    private fun parsePaytmPdf(
        lines: List<String>,
        fileName: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        val generic = parseGenericTablePdf(lines, fileName, "Paytm Statement", accountId, accountName, customRules)
        if (generic.transactions.isNotEmpty()) return generic
        return parseFallbackLines(lines, fileName, "Paytm Statement", accountId, accountName, customRules)
    }

    // --- SBI STATEMENT PDF PARSER ---
    private fun parseSbiPdf(
        lines: List<String>,
        fileName: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        val list = mutableListOf<TransactionEntity>()
        var inflow = 0.0
        var outflow = 0.0

        val sbiDateLineRe = Regex("""^(\d{2}/\d{2}/\d{4})(?:\s+\d{2}/\d{2}/\d{4})?""")
        val sbiAmountLineRe = Regex("""(?:^|\s)-?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{2}))\s+-?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{2}))""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val dateM = sbiDateLineRe.find(line)

            if (dateM != null) {
                val rawDate = dateM.groupValues[1]
                var txnType = TransactionType.EXPENSE
                val narrationParts = mutableListOf<String>()
                var txnAmount = 0.0

                // Scan forward to gather type, narration, and amount for this transaction
                var j = i + 1
                while (j < minOf(i + 8, lines.size)) {
                    val nextLine = lines[j]
                    if (sbiDateLineRe.containsMatchIn(nextLine) && j > i + 1) {
                        break // Next transaction reached
                    }

                    if (nextLine.contains("WDL TFR", ignoreCase = true) || nextLine.contains("WDL CLG", ignoreCase = true) || nextLine.contains("DEBIT", ignoreCase = true)) {
                        txnType = TransactionType.EXPENSE
                    } else if (nextLine.contains("DEP TFR", ignoreCase = true) || nextLine.contains("DEP CLG", ignoreCase = true) || nextLine.contains("CREDIT", ignoreCase = true) || nextLine.contains("INTEREST CREDIT", ignoreCase = true)) {
                        txnType = TransactionType.INCOME
                    }

                    // Check for amount line: "- 33,000.00 - 1,59,287.01" or "- - 400.00 1,71,775.73" or "236.00 - 1,78,119.03"
                    val amtMatches = Regex("""([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{2}))""").findAll(nextLine).toList()
                    if (amtMatches.isNotEmpty()) {
                        // The transaction amount is the first amount (last is the running balance)
                        val candidateAmt = cleanAmount(amtMatches[0].groupValues[1])
                        if (candidateAmt > 0) {
                            txnAmount = candidateAmt
                            // If the line starts with "- -", it's a deposit (credit column)
                            if (nextLine.trim().startsWith("- -") || nextLine.contains("DEP", ignoreCase = true)) {
                                txnType = TransactionType.INCOME
                            }
                        }
                        j++
                        break
                    } else if (!nextLine.contains("Page no", ignoreCase = true) && !nextLine.contains("Statement Summary", ignoreCase = true)) {
                        narrationParts.add(nextLine)
                    }
                    j++
                }

                if (txnAmount > 0) {
                    val rawNarration = (line.replace(dateM.value, "").trim() + " " + narrationParts.joinToString(" ")).trim()
                    val cleanNarration = rawNarration.replace(Regex("""AT\s+\d+.*""", RegexOption.IGNORE_CASE), "").replace(Regex("""\s+"""), " ").trim()
                    val ref = Regex("""(?:UPI|UTR|IMPS|NEFT|REF)[/\s:-]*([0-9A-Za-z]{8,18})""", RegexOption.IGNORE_CASE).find(rawNarration)?.groupValues?.getOrNull(1) ?: ""
                    val cat = CategorizerEngine.categorize(cleanNarration.ifBlank { "SBI Transaction" }, txnAmount, customRules)
                    val timestamp = parseDate(rawDate) ?: System.currentTimeMillis()

                    list.add(
                        TransactionEntity(
                            date = timestamp,
                            description = cat.cleanTitle,
                            amount = txnAmount,
                            type = txnType,
                            category = cat.category,
                            accountId = accountId,
                            accountName = accountName.ifBlank { "State Bank of India" },
                            referenceNo = ref,
                            paymentMode = if (cleanNarration.contains("upi", ignoreCase = true)) "UPI" else "Online / Bank Transfer",
                            sourceFile = fileName,
                            rawNarration = cleanNarration,
                            needsReview = cat.needsReview,
                            confidence = cat.confidence
                        )
                    )

                    if (txnType == TransactionType.INCOME) inflow += txnAmount else outflow += txnAmount
                }
                i = j
            } else {
                i++
            }
        }

        return StatementParseResult("State Bank of India (SBI) Statement", list, inflow, outflow)
    }


    // --- UNIVERSAL CREDIT CARD PDF PARSER ---
    private fun parseCreditCardPdf(
        lines: List<String>,
        fileName: String,
        detectedBank: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        val list = mutableListOf<TransactionEntity>()
        var inflow = 0.0
        var outflow = 0.0

        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("Total", ignoreCase = true) || line.startsWith("Opening", ignoreCase = true) || line.startsWith("Payment Due", ignoreCase = true)) continue

            val dateM = dateRegex.find(line)
            val amtM = amountRegex.find(line)

            if (dateM != null && amtM != null) {
                val amt = cleanAmount(amtM.groupValues[1])
                if (amt <= 0) continue

                val isCredit = line.contains(" CR", ignoreCase = true) || line.contains("Payment Received", ignoreCase = true) || line.contains("Refund", ignoreCase = true)
                val explicitType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE

                val narration = line.replace(dateM.value, "").replace(amtM.value, "").replace(" CR", "", ignoreCase = true).trim()
                val cat = CategorizerEngine.categorize(narration, amt, customRules)
                val date = parseDate(dateM.value) ?: System.currentTimeMillis()

                list.add(
                    TransactionEntity(
                        date = date,
                        description = cat.cleanTitle,
                        amount = amt,
                        type = explicitType,
                        category = cat.category,
                        accountId = accountId,
                        accountName = accountName.ifBlank { detectedBank },
                        referenceNo = "",
                        paymentMode = "Credit Card",
                        sourceFile = fileName,
                        rawNarration = narration,
                        needsReview = cat.needsReview,
                        confidence = cat.confidence
                    )
                )

                if (explicitType == TransactionType.INCOME) inflow += amt else outflow += amt
            }
        }

        return StatementParseResult("$detectedBank Credit Card Statement", list, inflow, outflow)
    }

    // --- GENERIC TABLE PDF PARSER ---
    private fun parseGenericTablePdf(
        lines: List<String>,
        fileName: String,
        detectedBank: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        return parseFallbackLines(lines, fileName, detectedBank, accountId, accountName, customRules)
    }

    // --- FALLBACK LINE-BY-LINE PARSER ---
    private fun parseFallbackLines(
        lines: List<String>,
        fileName: String,
        detectedBank: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        val list = mutableListOf<TransactionEntity>()
        var inflow = 0.0
        var outflow = 0.0

        for (i in lines.indices) {
            val line = lines[i]
            val dateM = dateRegex.find(line)
            val amtM = amountRegex.find(line)

            if (dateM != null && amtM != null) {
                val amt = cleanAmount(amtM.groupValues[1])
                if (amt <= 0) continue

                val isCredit = line.contains("credit", ignoreCase = true) || line.contains("deposit", ignoreCase = true) ||
                        line.contains("received", ignoreCase = true) || line.contains("refund", ignoreCase = true) ||
                        line.contains("salary", ignoreCase = true) || (line.contains("cr", ignoreCase = true) && !line.contains("debit", ignoreCase = true))

                val explicitType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE
                var narration = line.replace(dateM.value, "").replace(amtM.value, "").trim()

                if (i + 1 < lines.size && !dateRegex.containsMatchIn(lines[i + 1]) && lines[i + 1].length in 4..100) {
                    narration += " " + lines[i + 1]
                }

                val cat = CategorizerEngine.categorize(narration, amt, customRules)
                val date = parseDate(dateM.value) ?: System.currentTimeMillis()

                list.add(
                    TransactionEntity(
                        date = date,
                        description = cat.cleanTitle,
                        amount = amt,
                        type = explicitType,
                        category = cat.category,
                        accountId = accountId,
                        accountName = accountName.ifBlank { detectedBank },
                        referenceNo = Regex("""(?:UPI|UTR|IMPS|REF)[/\s:-]*([0-9A-Za-z]{8,18})""", RegexOption.IGNORE_CASE).find(narration)?.groupValues?.getOrNull(1) ?: "",
                        paymentMode = if (narration.contains("upi", ignoreCase = true)) "UPI" else "Bank Transfer / Online",
                        sourceFile = fileName,
                        rawNarration = narration,
                        needsReview = cat.needsReview,
                        confidence = cat.confidence
                    )
                )

                if (explicitType == TransactionType.INCOME) inflow += amt else outflow += amt
            }
        }

        return StatementParseResult(detectedBank, list, inflow, outflow)
    }

    // --- CSV PARSING ENGINE ---
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
            throw StatementParsingException("Empty Statement", "The uploaded CSV file is empty.")
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
            headerRowIndex = 0
            headerCols = splitCsvLine(lines[0]).map { it.trim().lowercase() }
        }

        val dateIdx = headerCols.indexOfFirst { it.contains("date") }
        val descIdx = headerCols.indexOfFirst {
            it.contains("description") || it.contains("narration") || it.contains("particulars") ||
                    it.contains("remarks") || it.contains("details") || it.contains("payee")
        }
        val amountIdx = headerCols.indexOfFirst { it == "amount" || it.contains("amount (inr)") || it.contains("txn amount") }
        val debitIdx = headerCols.indexOfFirst { it.contains("debit") || it.contains("withdrawal") || it.contains("dr") }
        val creditIdx = headerCols.indexOfFirst { it.contains("credit") || it.contains("deposit") || it.contains("cr") }
        val refIdx = headerCols.indexOfFirst { it.contains("ref") || it.contains("utr") || it.contains("chq") || it.contains("reference") }
        val typeIdx = headerCols.indexOfFirst { it == "type" || it.contains("txn type") || it.contains("cr/dr") }

        val detectedProfile = detectBankFromText(lines.take(10).joinToString(" "), fileName)
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

        if (parsedList.isEmpty()) {
            throw StatementParsingException("No Transactions Extracted", "Could not extract valid transactions from this CSV.", detectedProfile)
        }

        return StatementParseResult(
            detectedProfile = detectedProfile,
            transactions = parsedList,
            totalInflow = totalInflow,
            totalOutflow = totalOutflow
        )
    }

    private fun detectBankFromText(text: String, fileName: String): String {
        val f = fileName.lowercase()
        val header = text.take(1500).lowercase()
        val t = text.lowercase()
        return when {
            f.contains("sbi") || f.contains("state bank") || header.contains("state bank of india") || header.contains("sbin") || header.contains("wdl tfr") || header.contains("dep tfr") -> "State Bank of India (SBI)"
            f.contains("navi") || header.contains("paid via navi") || header.contains("navi technologies") -> "Navi UPI"
            f.contains("phonepe") || header.contains("phonepe") -> "PhonePe"
            f.contains("paytm") || header.contains("paytm") -> "Paytm"
            f.contains("google pay") || f.contains("gpay") || header.contains("google pay") -> "Google Pay"
            f.contains("hdfc") || header.contains("hdfc bank") || header.contains("www.hdfcbank.com") -> "HDFC Bank"
            f.contains("icici") || header.contains("icici bank") -> "ICICI Bank"
            f.contains("axis") || header.contains("axis bank") -> "Axis Bank"
            f.contains("kotak") || header.contains("kotak mahindra") -> "Kotak Mahindra Bank"
            f.contains("punjab national") || f.contains("pnb") -> "Punjab National Bank (PNB)"
            f.contains("bank of baroda") || f.contains("bob") -> "Bank of Baroda"
            f.contains("canara") -> "Canara Bank"
            f.contains("cred") || header.contains("cred") -> "CRED"
            header.contains("amazon pay") -> "Amazon Pay"
            t.contains("state bank of india") || t.contains("sbi") -> "State Bank of India (SBI)"
            t.contains("hdfc bank") -> "HDFC Bank"
            else -> "Generic Statement"
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

