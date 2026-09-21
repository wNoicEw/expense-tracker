package com.wnoicew.expensetracker.data.engine

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.wnoicew.expensetracker.data.model.AccountMetadata
import com.wnoicew.expensetracker.data.model.RuleEntity
import com.wnoicew.expensetracker.data.model.StatementParseResult
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.ParsePosition
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

    // Two-digit-year patterns precede four-digit ones and every parse must consume the whole string;
    // otherwise "05/01/25" would be read as year 0025 by the yyyy pattern.
    private val supportedDateFormats = listOf(
        // Datetime with seconds & AM/PM
        "yyyy-MM-dd hh:mm:ss a",
        "yyyy-MM-dd HH:mm:ss",
        "dd/MM/yyyy hh:mm:ss a",
        "dd/MM/yyyy HH:mm:ss",
        "dd-MM-yyyy hh:mm:ss a",
        "dd-MM-yyyy HH:mm:ss",
        "dd.MM.yyyy hh:mm:ss a",
        "dd.MM.yyyy HH:mm:ss",
        "dd-MMM-yyyy hh:mm:ss a",
        "dd MMM yyyy hh:mm:ss a",
        "dd MMM, yyyy hh:mm:ss a",
        "MMM dd, yyyy hh:mm:ss a",
        "dd MMMM yyyy hh:mm:ss a",
        "dd MMMM, yyyy hh:mm:ss a",

        // Datetime without seconds (hh:mm a or HH:mm)
        "yyyy-MM-dd hh:mm a",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm",
        "dd/MM/yyyy hh:mm a",
        "dd/MM/yyyy HH:mm",
        "dd-MM-yyyy hh:mm a",
        "dd-MM-yyyy HH:mm",
        "dd.MM.yyyy hh:mm a",
        "dd.MM.yyyy HH:mm",
        "dd-MMM-yyyy hh:mm a",
        "dd MMM yyyy hh:mm a",
        "dd MMM, yyyy hh:mm a",
        "MMM dd, yyyy hh:mm a",
        "dd MMMM yyyy hh:mm a",
        "dd MMMM, yyyy hh:mm a",
        "dd MMM yy hh:mm a",
        "dd/MM/yy hh:mm a",
        "dd-MM-yy hh:mm a",

        // Pure dates
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "dd/MM/yy",
        "dd-MM-yy",
        "dd.MM.yy",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd.MM.yyyy",
        "dd-MMM-yy",
        "dd-MMM-yyyy",
        "dd MMM yy",
        "dd MMM yyyy",
        "MMM dd, yyyy",
        "dd MMM, yyyy",
        "dd MMMM yyyy",
        "dd MMMM, yyyy"
    )

    private const val MIN_VALID_YEAR = 1990

    // Month-first fallbacks, tried only when the day-first parse failed AND the first number can't be a day
    // ("01/15/2025"). "05/06/2025" stays day-first: that is the Indian convention and guessing would misdate it.
    private val monthFirstFormats = listOf(
        "MM/dd/yyyy hh:mm:ss a",
        "MM/dd/yyyy hh:mm a",
        "MM/dd/yyyy HH:mm:ss",
        "MM/dd/yyyy HH:mm",
        "MM-dd-yyyy hh:mm:ss a",
        "MM-dd-yyyy hh:mm a",
        "MM-dd-yyyy HH:mm:ss",
        "MM-dd-yyyy HH:mm",
        "MM/dd/yyyy",
        "MM-dd-yyyy",
        "MM/dd/yy",
        "MM-dd-yy"
    )
    private val monthFirstShape = Regex("""^(\d{1,2})[/\-](\d{1,2})[/\-](\d{2}|\d{4})(?:\s|$)""")

    fun extractTime(text: String): String? {
        if (text.isBlank()) return null
        val amPmMatch = Regex("""\b((?:0?[1-9]|1[0-2]):[0-5]\d(?::[0-5]\d)?\s*[AP]M)\b""", RegexOption.IGNORE_CASE).find(text)
        if (amPmMatch != null) {
            return amPmMatch.groupValues[1].replace(Regex("""(?i)(\d)(am|pm)"""), "$1 $2").trim().uppercase()
        }
        val h24Match = Regex("""\b([01]?\d|2[0-3]):[0-5]\d(?::[0-5]\d)?\b""").find(text)
        if (h24Match != null) {
            return h24Match.value.trim()
        }
        return null
    }

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
        val bytes = inputStream.readBytes()
        val isPdfMagic = bytes.size >= 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val isPdf = isPdfMagic || ext == "pdf" || fileName.contains("pdf", ignoreCase = true)

        return if (isPdf) {
            parsePdfStream(java.io.ByteArrayInputStream(bytes), fileName, accountId, accountName, customRules, password)
        } else {
            parseCsvStream(java.io.ByteArrayInputStream(bytes), fileName, accountId, accountName, customRules)
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
        val header = fullText.take(1500).lowercase()

        val accountMetadata = extractAccountMetadata(fullText, fileName)
        val detectedBank = accountMetadata.bankName
        val effectiveAccountName = accountName.ifBlank { accountMetadata.name }

        // Route to specialized parsers (UPI apps and SBI prioritized over generic matching)
        val baseResult = when {
            ft.contains("paid via navi") || (ft.contains("upi txn id") && (ft.contains("navi") || fname.contains("navi"))) -> {
                parseNaviPdf(lines, fileName, accountId, effectiveAccountName, customRules)
            }
            detectedBank == "Google Pay" || header.contains("google pay") || fname.contains("gpay") || fname.contains("google pay") || ft.contains("google pay app") -> {
                val gpay = parseGooglePayLines(lines, fileName, accountId, effectiveAccountName, customRules)
                if (gpay.transactions.isNotEmpty()) gpay else {
                    parseFallbackLines(lines, fileName, detectedBank, accountId, effectiveAccountName, customRules)
                }
            }
            detectedBank == "PhonePe" || ft.contains("phonepe") || fname.contains("phonepe") -> {
                parsePhonePePdf(lines, fileName, accountId, effectiveAccountName, customRules)
            }
            detectedBank == "Paytm" || ft.contains("paytm") || fname.contains("paytm") -> {
                parsePaytmPdf(lines, fileName, accountId, effectiveAccountName, customRules)
            }
            ft.contains("state bank of india") || ft.contains("sbi") || fname.contains("sbi") || ft.contains("wdl tfr") || ft.contains("dep tfr") || detectedBank.contains("sbi", ignoreCase = true) -> {
                val sbi = parseSbiPdf(lines, fileName, accountId, effectiveAccountName, customRules)
                if (sbi.transactions.isNotEmpty()) sbi else {
                    val generic = parseGenericTablePdf(lines, fileName, detectedBank, accountId, effectiveAccountName, customRules)
                    if (generic.transactions.isNotEmpty()) generic else {
                        parseFallbackLines(lines, fileName, detectedBank, accountId, effectiveAccountName, customRules)
                    }
                }
            }
            (accountMetadata.type == "Credit Card" || ft.contains("credit card statement") || ft.contains("total amount due") || ft.contains("minimum amount due") || ft.contains("card statement")) && !ft.contains("wdl tfr") && !ft.contains("dep tfr") -> {
                parseCreditCardPdf(lines, fileName, detectedBank, accountId, effectiveAccountName, customRules)
            }
            else -> {
                val generic = parseGenericTablePdf(lines, fileName, detectedBank, accountId, effectiveAccountName, customRules)
                if (generic.transactions.isNotEmpty()) generic else {
                    parseFallbackLines(lines, fileName, detectedBank, accountId, effectiveAccountName, customRules)
                }
            }
        }

        if (baseResult.transactions.isEmpty()) {
            throw StatementParsingException(
                title = if (detectedBank != "Generic Statement") "Detected: \"$detectedBank\" — but no transactions found" else "Failed to detect statement format",
                detail = if (detectedBank != "Generic Statement") "The PDF format was recognized as $detectedBank, but no transactions could be extracted. The statement may be in an unsupported layout or password-locked." else "Could not identify financial transactions in this document. Please ensure it is an official bank, UPI, or credit card PDF.",
                detectedProfile = detectedBank,
                isDetectionFailure = true
            )
        }

        return baseResult.copy(
            detectedProfile = detectedBank + (if (accountMetadata.type == "Credit Card") " Card" else " Statement"),
            accountMetadata = accountMetadata
        )
    }

    // --- PER-ROW ACCOUNT HINTS (UPI histories that mix several paying accounts) ---
    // Bank names as printed by UPI apps: "State Bank of India", "HDFC Bank", "Union Bank of India". "Bank" stays
    private const val BANK_NAME = """State\s+Bank\s+[Oo]f\s+India|Kotak\s+Mahindra\s+Bank|Punjab\s+National\s+Bank|Bank\s+[Oo]f\s+[A-Za-z]+|Union\s+Bank\s+[Oo]f\s+India|Central\s+Bank\s+[Oo]f\s+India|IDFC\s+FIRST\s+Bank|Standard\s+Chartered\s+Bank|Airtel\s+Payments\s+Bank|Paytm\s+Payments\s+Bank|[A-Za-z&.]+\s+Bank|SBI|HDFC|ICICI|Axis|Kotak|PNB|BOB"""
    private val sbiNameRe = Regex("""state\s*bank|\bsbi\b""", RegexOption.IGNORE_CASE)
    private val knownBankRe = Regex("""\bbank\b|^(?:sbi|hdfc|icici|axis|kotak|pnb|idfc|bob|rbl|yes)\b""", RegexOption.IGNORE_CASE)
    private val cardWordRe = Regex("""rupay|credit\s*card""", RegexOption.IGNORE_CASE)
    private val instrumentMaskedRe = Regex("""^(?:paid\s+by\s+)?[xX*•]{2,}\s*(\d{2,4})$""", RegexOption.IGNORE_CASE)
    private val instrumentBankRe = Regex("""^(?:paid\s+(?:by|to)\s+|credited\s+to\s+)?([A-Za-z][A-Za-z&.\s]*?)\s*(?:-|–|—|a/c|acct?\.?|account|ending(?:\s+in)?|no\.?)?\s*[xX*•]*\s*(\d{2,4})$""", RegexOption.IGNORE_CASE)
    private val bankBeforeSuffixRe = Regex("""($BANK_NAME)\s*(?:-|–|—)\s*[xX*]*(\d{2,4})\b""")
    private val naviBankWithSuffixRe = Regex("""^(.*?)\s+($BANK_NAME)\s*(?:-|–|—|a/c|acct?\.?|account|ending(?:\s+in)?|no\.?)?\s*[xX*•]*(\d{2,4})\s*$""", RegexOption.IGNORE_CASE)
    private val naviCardFragRe = Regex("""credit\s*card\s*-?\s*[xX*]*\d{2,4}""", RegexOption.IGNORE_CASE)
    private val naviRupayTailRe = Regex("""^(.*?)\s+($BANK_NAME|[A-Za-z&.]+)\s+(?i:rupay)$""")
    private val naviBankTailRe = Regex("""^(.*?)\s+($BANK_NAME)$""")
    private val naviBankSuffixRe = Regex("""(?:^|\s)(?:-|–|—)?\s*(\d{2,4})\s*$""")
    private val cardBillRe = Regex("""bill\s*payment\s*of\s+.*credit\s*card|credit\s*card\s*(?:bill\s*)?payment|\bcc\s*(?:bill|payment)\b""", RegexOption.IGNORE_CASE)
    private val timeOnlyRe = Regex("""^\d{1,2}:\d{2}\s*[AP]M$""", RegexOption.IGNORE_CASE)
    // Currency-prefixed so whole-rupee amounts ("₹185") match; amountRegex insists on decimals
    private val upiAmountRe = Regex("""(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    // "State Bank of India - 1234" / "HDFC Bank 4321" / "Paid by XXXXXXXX3863" / "HDFC Bank RuPay Credit Card - XX99"
    // -> the account that paid. Cards become RuPay credit-card accounts, bank rows a bank account keyed by type + last4
    // (the same key an imported bank statement uses, so both files land on one account). Unrecognised text -> null,
    // and the caller falls back to the file-level account.
    fun accountHintFromInstrument(text: String): AccountMetadata? {
        val t = text.trim()
        if (t.isBlank()) return null
        if (cardWordRe.containsMatchIn(t)) return detectRuPayCC("UPI $t")
        instrumentMaskedRe.find(t)?.let { m ->
            val last4 = m.groupValues[1]
            return AccountMetadata(bankName = "", type = "Bank Account", lastFour = last4, name = "Bank Account (•••• $last4)")
        }
        val m = instrumentBankRe.find(t) ?: return null
        val bank = m.groupValues[1].trim()
        if (!knownBankRe.containsMatchIn(bank)) return null
        val last4 = m.groupValues[2]
        val isSbi = sbiNameRe.containsMatchIn(bank)
        val bankName = when {
            isSbi -> "State Bank of India (SBI)"
            bank.equals("hdfc", ignoreCase = true) -> "HDFC Bank"
            bank.equals("icici", ignoreCase = true) -> "ICICI Bank"
            bank.equals("axis", ignoreCase = true) -> "Axis Bank"
            bank.equals("kotak", ignoreCase = true) -> "Kotak Mahindra Bank"
            bank.equals("pnb", ignoreCase = true) -> "Punjab National Bank (PNB)"
            bank.equals("bob", ignoreCase = true) -> "Bank of Baroda"
            else -> bank
        }
        return AccountMetadata(
            bankName = bankName,
            type = "Bank Account",
            lastFour = last4,
            name = "$bankName Account (•••• $last4)",
            gradientIndex = if (isSbi) 1 else 0
        )
    }

    // First "<Bank> - 1234" found in free text (Paytm narrations that carry the account column)
    fun bankAccountInText(text: String): AccountMetadata? {
        val m = bankBeforeSuffixRe.find(text) ?: return null
        return accountHintFromInstrument("${m.groupValues[1]} - ${m.groupValues[2]}")
    }

    // Which account a row lands on: its own hint first; otherwise the RuPay-on-UPI narration detection, except for
    // credit-card bill payments (a payment OF a card is not spending WITH one, so no phantom card account).
    fun rowAccountFor(txn: TransactionEntity, hint: AccountMetadata?): AccountMetadata? {
        if (hint != null) return hint
        if (txn.type == TransactionType.TRANSFER || cardBillRe.containsMatchIn(txn.rawNarration)) return null
        return detectRuPayCC(txn.rawNarration)
    }

    class NaviRowAccount(val payee: String, val account: AccountMetadata?)

    // Navi's Account column reads "State Bank of India" + "- 1234", or "HDFC Bank RuPay" + "Credit Card - XX99". In flattened
    // text the first part is glued to the end of the payee and the rest sits on the time / txn-ID line or the one after,
    // so the fragments are searched across every follow-up line rather than in a fixed position.
    fun splitNaviAccount(payeeRaw: String, followLines: List<String>): NaviRowAccount {
        var payee = payeeRaw.trim()
        val frags = followLines.filterNot { it.startsWith("Note:", ignoreCase = true) }

        // 1. RuPay Credit Card
        val cardInPayee = naviCardFragRe.find(payee)
        if (cardInPayee != null) payee = payee.replace(cardInPayee.value, "").trim()
        val card = cardInPayee?.value ?: frags.firstNotNullOfOrNull { naviCardFragRe.find(it)?.value }
        val rupay = naviRupayTailRe.find(payee)
        if (card != null || rupay != null) {
            if (rupay != null) payee = rupay.groupValues[1]
            val account = detectRuPayCC("${rupay?.groupValues?.get(2).orEmpty()} RuPay ${card ?: "Credit Card"}")
            return NaviRowAccount(payee.ifBlank { payeeRaw.trim() }, account)
        }

        // 2. Bank Name + Last 2-4 digits glued directly to the payee (e.g. "SAMPLE BENEFICIARY HDFC Bank - 1234")
        val bankWithSuffix = naviBankWithSuffixRe.find(payee)
        if (bankWithSuffix != null) {
            val cleanPayee = bankWithSuffix.groupValues[1].trim()
            val bank = bankWithSuffix.groupValues[2].trim()
            val suffix = bankWithSuffix.groupValues[3].trim()
            val account = accountHintFromInstrument("$bank - $suffix")
            if (account != null) {
                return NaviRowAccount(cleanPayee.ifBlank { payeeRaw.trim() }, account)
            }
        }

        // 3. Bank in Payee, Suffix on follow lines; OR Bank and Suffix both on follow lines
        val suffix = frags.firstNotNullOfOrNull { naviBankSuffixRe.find(it)?.groupValues?.get(1) }
        val tail = naviBankTailRe.find(payee)
        val bank = tail?.groupValues?.get(2)
            ?: suffix?.let { frags.firstNotNullOfOrNull { f -> bankBeforeSuffixRe.find(f)?.groupValues?.get(1) } }
            ?: frags.firstNotNullOfOrNull { f ->
                bankBeforeSuffixRe.find(f)?.let { m ->
                    val acc = accountHintFromInstrument("${m.groupValues[1]} - ${m.groupValues[2]}")
                    if (acc != null) return NaviRowAccount(payee.ifBlank { payeeRaw.trim() }, acc)
                    null
                }
            }

        // "State Bank of India" is Navi's own column text; any other trailing "... Bank" is only trusted with a "- 1234" beside it
        if (tail != null && (suffix != null || sbiNameRe.containsMatchIn(tail.groupValues[2]))) payee = tail.groupValues[1]
        val account = if (bank != null && suffix != null) accountHintFromInstrument("$bank - $suffix") else null
        return NaviRowAccount(payee.ifBlank { payeeRaw.trim() }, account)
    }

    // --- NAVI UPI PDF PARSER ---
    internal fun parseNaviPdf(
        lines: List<String>,
        fileName: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        // Direction word is part of the row text; "Bill payment of …" / "Recharge of …" rows have no Paid-to prefix
        val anchorRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*\s+\d{4})\s+(Paid\s+to|Paid\s+for|Received\s+from|Refund\s+from|Bill\s+payment\s+of|Recharge\s+of|Payment\s+(?:of|to))\s+(.+?)\s+[₹Rs.]*\s*([\d,]+(?:\.\d{1,2})?)$""", RegexOption.IGNORE_CASE)
        val ccBillRe = Regex("""^bill\s+payment\s+of\s+.*credit\s*card""", RegexOption.IGNORE_CASE)
        val rowStartRe = Regex("""^\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)""", RegexOption.IGNORE_CASE)
        val list = mutableListOf<TransactionEntity>()
        val rowAccounts = mutableListOf<AccountMetadata?>()
        var inflow = 0.0
        var outflow = 0.0

        for (i in lines.indices) {
            val m = anchorRe.find(lines[i]) ?: continue
            val rawDate = m.groupValues[1]
            val direction = m.groupValues[2].lowercase()
            val amt = cleanAmount(m.groupValues[4])
            if (amt <= 0) continue

            val isRefund = direction.contains("refund")
            val isIncome = direction.contains("received")
            val explicitType = when {
                isRefund -> TransactionType.REFUND
                isIncome -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }

            // Everything up to the next dated row belongs to this one
            val follow = mutableListOf<String>()
            for (j in (i + 1)..minOf(i + 5, lines.lastIndex)) {
                if (rowStartRe.containsMatchIn(lines[j])) break
                follow.add(lines[j])
            }

            var bankInstrument = ""
            var txnId = ""
            var note = ""
            for (next in follow) {
                if (next.contains("UPI txn ID", ignoreCase = true)) {
                    val tid = Regex("""UPI\s+txn\s+ID[:\s]*(\d+)""", RegexOption.IGNORE_CASE).find(next)?.groupValues?.getOrNull(1)
                    if (tid != null) txnId = tid
                } else if (next.startsWith("Note:", ignoreCase = true)) {
                    note = next.replace(Regex("""^Note:\s*""", RegexOption.IGNORE_CASE), "").trim()
                } else if (bankInstrument.isBlank() && next.length in 3..50 && !timeOnlyRe.matches(next)) {
                    bankInstrument = next
                }
            }

            val split = splitNaviAccount(m.groupValues[3], follow)
            val payee = split.payee
            val isCard = split.account?.isRuPay == true

            val prefix = when {
                isRefund -> "Refund from "
                isIncome -> "Received from "
                direction.startsWith("paid") -> "Paid to "
                else -> m.groupValues[2].trim().replaceFirstChar { it.uppercase() } + " " // "Bill payment of "
            }
            // A parsed account is carried on the row; the raw column text is only kept in the narration when nothing was understood
            val instrumentTag = if (split.account == null && bankInstrument.isNotBlank()) " [$bankInstrument]" else ""
            val narration = prefix + payee + (if (note.isNotBlank()) " — $note" else "") + instrumentTag
            // Paying a credit-card bill is not spending: the purchases are already on the card itself
            val isCcBillPayment = ccBillRe.containsMatchIn(prefix + payee) && !isCard && !bankInstrument.contains("Credit Card", ignoreCase = true)
            val cat = CategorizerEngine.categorize(narration, amt, customRules)
            val timeStr = follow.firstNotNullOfOrNull { extractTime(it) } ?: extractTime(lines[i])
            val parsedDate = parseDate(rawDate, timeStr)

            list.add(
                TransactionEntity(
                    date = parsedDate ?: System.currentTimeMillis(),
                    description = cat.cleanTitle,
                    amount = amt,
                    type = if (isCcBillPayment) TransactionType.TRANSFER else explicitType,
                    category = if (isCcBillPayment) "Transfers & CC Bill" else cat.category,
                    accountId = accountId,
                    accountName = accountName.ifBlank { "Navi UPI" },
                    note = if (parsedDate == null) undatedNote(rawDate) else "",
                    referenceNo = txnId,
                    paymentMode = if (isCard || bankInstrument.contains("Credit Card", ignoreCase = true)) "UPI (RuPay Credit Card)" else "UPI",
                    sourceFile = fileName,
                    rawNarration = narration,
                    needsReview = cat.needsReview || parsedDate == null,
                    confidence = if (parsedDate == null) "low" else cat.confidence
                )
            )
            rowAccounts.add(split.account)

            if (explicitType == TransactionType.INCOME || explicitType == TransactionType.REFUND) inflow += amt else outflow += amt
        }

        return StatementParseResult("Navi UPI Statement", list, inflow, outflow, rowAccounts = rowAccounts)
    }

    // --- PHONEPE STATEMENT PDF PARSER ---
    internal fun parsePhonePePdf(
        lines: List<String>,
        fileName: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        val list = mutableListOf<TransactionEntity>()
        val rowAccounts = mutableListOf<AccountMetadata?>()
        var inflow = 0.0
        var outflow = 0.0

        val typeRe = Regex("""\b(DEBIT|CREDIT|DR|CR)\b""", RegexOption.IGNORE_CASE)
        val txnIdRe = Regex("""(?:Transaction\s*ID|Txn\s*ID|T\d{20,})[:\s]*([T\w]{10,30})""", RegexOption.IGNORE_CASE)
        val utrRe = Regex("""UTR\s*No\.?\s*[:\s]*(\d{10,15})""", RegexOption.IGNORE_CASE)
        val paidByRe = Regex("""^Paid\s+by\b""", RegexOption.IGNORE_CASE)

        for (i in lines.indices) {
            val line = lines[i]
            val dateM = dateRegex.find(line)
            val amtM = upiAmountRe.find(line) ?: amountRegex.find(line)
            val typeM = typeRe.find(line)

            if (dateM == null || amtM == null) continue
            val amt = cleanAmount(amtM.groupValues[1])
            if (amt <= 0) continue

            val isCredit = typeM?.value?.contains("CREDIT", ignoreCase = true) == true || typeM?.value?.contains("CR", ignoreCase = true) == true
            val explicitType = if (isCredit) TransactionType.INCOME else TransactionType.EXPENSE

            var narration = line.replace(dateM.value, "").replace(amtM.value, "").replace(typeRe, "").trim()
            var refNo = ""
            var hint: AccountMetadata? = null
            var timeStr = extractTime(line)

            for (j in (i + 1)..minOf(i + 3, lines.lastIndex)) {
                val next = lines[j]
                if (dateRegex.containsMatchIn(next) && (upiAmountRe.containsMatchIn(next) || amountRegex.containsMatchIn(next))) break
                if (timeStr == null) {
                    timeStr = extractTime(next)
                }
                // "Paid by XXXXXXXX3863" names the paying account; a missing or unreadable line just leaves the file-level account
                if (hint == null && paidByRe.containsMatchIn(next)) {
                    hint = accountHintFromInstrument(next)
                    if (hint != null) continue
                }
                val tid = txnIdRe.find(next)?.groupValues?.getOrNull(1)
                val utr = utrRe.find(next)?.groupValues?.getOrNull(1)
                if (utr != null) refNo = utr else if (tid != null && refNo.isBlank()) refNo = tid
                if (!next.matches(Regex("""^\d{1,2}:\d{2}.*""")) && tid == null && utr == null && next.length in 4..80) {
                    narration += " $next"
                }
            }

            val cat = CategorizerEngine.categorize(narration, amt, customRules)
            val date = parseDate(dateM.value, timeStr) ?: continue

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
                    paymentMode = if (hint?.isRuPay == true) "UPI (RuPay Credit Card)" else "UPI",
                    sourceFile = fileName,
                    rawNarration = narration,
                    needsReview = cat.needsReview,
                    confidence = cat.confidence
                )
            )
            rowAccounts.add(hint)

            if (explicitType == TransactionType.INCOME) inflow += amt else outflow += amt
        }

        return StatementParseResult("PhonePe UPI Statement", list, inflow, outflow, rowAccounts = rowAccounts)
    }

    // --- GOOGLE PAY (INDIA) TRANSACTION STATEMENT PDF PARSER ---
    // Rows are 3-line groups: "01 Jan," / "2025" date cell (year may wrap), "Paid to X" / "UPI Transaction ID: n" / "Paid by <Bank> 1234"
    // details cell, "₹1,234" amount cell. Grouped by date-start line and read from joined text, so column glue order is irrelevant.
    fun parseGooglePayLines(
        lines: List<String>,
        fileName: String,
        accountId: String = "",
        accountName: String = "",
        customRules: List<RuleEntity> = emptyList()
    ): StatementParseResult {
        val startRe = Regex("""^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*),?(?:\s+(\d{4}))?(?:\s|$)""", RegexOption.IGNORE_CASE)
        val detailsRe = Regex("""(Paid\s+to|Received\s+from|Self\s+transfer\s+to)\s+(.+?)(?=\s+(?:UPI\s+Transaction\s+ID|Paid\s+by|Paid\s+to|₹|Rs\.?|INR|\d{1,2}:\d{2}\s*[AP]M)|$)""", RegexOption.IGNORE_CASE)
        val idRe = Regex("""UPI\s+Transaction\s+ID[:\s]*(\d+)""", RegexOption.IGNORE_CASE)
        val paidByRe = Regex("""Paid\s+by\s+(.+?)(?:\s+(?:₹|Rs\.?|INR)\s*[\d,.]+|\s+\d{1,2}:\d{2}\s*[AP]M)?$""", RegexOption.IGNORE_CASE)
        val paidToAccountRe = Regex("""Paid\s+to\s+(.+?)(?:\s+(?:₹|Rs\.?|INR)\s*[\d,.]+|\s+\d{1,2}:\d{2}\s*[AP]M)?$""", RegexOption.IGNORE_CASE)
        val yearOnlyRe = Regex("""^(20\d{2})\b""")

        val starts = lines.indices.filter { startRe.containsMatchIn(lines[it]) }
        val list = mutableListOf<TransactionEntity>()
        val rowAccounts = mutableListOf<AccountMetadata?>()
        var inflow = 0.0
        var outflow = 0.0

        for ((n, s) in starts.withIndex()) {
            val block = lines.subList(s, minOf(starts.getOrElse(n + 1) { lines.size }, s + 8))
            val text = block.joinToString(" ")
            val details = detailsRe.find(text) ?: continue
            val amt = upiAmountRe.find(text)?.let { cleanAmount(it.groupValues[1]) } ?: continue
            if (amt <= 0) continue

            val startM = startRe.find(block[0])!!
            val year = startM.groupValues[2].ifBlank { block.drop(1).firstNotNullOfOrNull { yearOnlyRe.find(it)?.groupValues?.get(1) }.orEmpty() }
            val rawDate = (startM.groupValues[1] + " " + year).trim()
            val timeStr = block.firstNotNullOfOrNull { extractTime(it) }
            val parsedDate = parseDate(rawDate, timeStr)

            val dirStr = details.groupValues[1].lowercase()
            val isIncome = dirStr.startsWith("received")
            val isTransfer = dirStr.startsWith("self transfer")
            val payee = details.groupValues[2].trim()
            val txnId = block.firstNotNullOfOrNull { idRe.find(it)?.groupValues?.get(1) }.orEmpty()
            val hint = if (isIncome) {
                block.firstNotNullOfOrNull { paidToAccountRe.find(it)?.groupValues?.get(1) }?.let { accountHintFromInstrument(it) }
                    ?: block.firstNotNullOfOrNull { paidByRe.find(it)?.groupValues?.get(1) }?.let { accountHintFromInstrument(it) }
            } else {
                block.firstNotNullOfOrNull { paidByRe.find(it)?.groupValues?.get(1) }?.let { accountHintFromInstrument(it) }
            }

            val prefix = when {
                isTransfer -> "Self transfer to "
                isIncome -> "Received from "
                else -> "Paid to "
            }
            val narration = prefix + payee
            val cat = CategorizerEngine.categorize(narration, amt, customRules)
            val explicitType = when {
                isTransfer -> TransactionType.TRANSFER
                isIncome -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }

            list.add(
                TransactionEntity(
                    date = parsedDate ?: System.currentTimeMillis(),
                    description = cat.cleanTitle,
                    amount = amt,
                    type = explicitType,
                    category = if (isTransfer) "Transfer" else cat.category,
                    accountId = accountId,
                    accountName = accountName.ifBlank { "Google Pay" },
                    note = if (parsedDate == null) undatedNote(rawDate) else "",
                    referenceNo = txnId,
                    paymentMode = if (hint?.isRuPay == true) "UPI (RuPay Credit Card)" else "UPI",
                    sourceFile = fileName,
                    rawNarration = narration,
                    needsReview = cat.needsReview || parsedDate == null,
                    confidence = if (parsedDate == null) "low" else cat.confidence
                )
            )
            rowAccounts.add(hint)

            if (isIncome) inflow += amt else if (!isTransfer) outflow += amt
        }

        return StatementParseResult("Google Pay UPI Statement", list, inflow, outflow, rowAccounts = rowAccounts)
    }

    // --- PAYTM PDF PARSER ---
    private fun parsePaytmPdf(
        lines: List<String>,
        fileName: String,
        accountId: String,
        accountName: String,
        customRules: List<RuleEntity>
    ): StatementParseResult {
        var result = parseGenericTablePdf(lines, fileName, "Paytm Statement", accountId, accountName, customRules)
        if (result.transactions.isEmpty()) {
            result = parseFallbackLines(lines, fileName, "Paytm Statement", accountId, accountName, customRules)
        }
        // The "Your Account" column ("State Bank Of India - 1234") ends up in the row text when it shares the line; layout unverified, so best effort
        val hints = result.transactions.map { bankAccountInText(it.rawNarration) }
        return if (hints.any { it != null }) result.copy(rowAccounts = hints) else result
    }

    // --- SBI STATEMENT PDF PARSER ---
    fun parseSbiPdf(
        lines: List<String>,
        fileName: String,
        accountId: String = "",
        accountName: String = "",
        customRules: List<RuleEntity> = emptyList()
    ): StatementParseResult {
        val list = mutableListOf<TransactionEntity>()
        var inflow = 0.0
        var outflow = 0.0

        val sbiDateLineRe = Regex(
            """^(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{1,2}[/\-.](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[/\-.]\d{2,4}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*,?\s+\d{2,4})(?:\s+(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{1,2}[/\-.](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[/\-.]\d{2,4}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*,?\s+\d{2,4}))?""",
            RegexOption.IGNORE_CASE
        )
        val sbiSingleDateRe = Regex(
            """^(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{1,2}[/\-.](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[/\-.]\d{2,4}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*,?\s+\d{2,4})$""",
            RegexOption.IGNORE_CASE
        )
        val amtRe = Regex("""^-?[0-9]{1,3}(?:,[0-9]{2,3})*\.[0-9]{2}$""")
        val skipPatterns = Regex("""^(page\s*no\.?|\d+$|balance|statement\s*summary|account\s*summary|welcome:?|statement\s*from|clear\s*balance|uncleared|drawing\s*power|interest\s*rate|branch\s*name|cif\s*number|account\s*number|ifsc\s*code|micr\s*code)""", RegexOption.IGNORE_CASE)

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            val dateM = sbiDateLineRe.find(line)

            if (dateM != null) {
                val rawDate = dateM.groupValues[1]
                var j = i + 1
                // If immediately next line is Value Date (also a single date), skip it
                if (j < lines.size && sbiSingleDateRe.matches(lines[j].trim())) {
                    j++
                }

                val chunk = mutableListOf<String>()
                while (j < lines.size) {
                    val nextLine = lines[j].trim()
                    if (sbiDateLineRe.containsMatchIn(nextLine)) {
                        break
                    }
                    if (!skipPatterns.containsMatchIn(nextLine)) {
                        chunk.add(nextLine)
                    }
                    j++
                }

                var typeMarker = TransactionType.EXPENSE
                val narrationLines = mutableListOf<String>()
                val tokens = mutableListOf<String>()

                for (cl in chunk) {
                    if (Regex("""\b(DEP\s*TFR|DEP\s*CLG|CREDIT|INTEREST\s*CREDIT|CEMTEX\s*DEP|BY\s+TRANSFER|CR\b|UPI/CR)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cl)) {
                        typeMarker = TransactionType.INCOME
                    } else if (Regex("""\b(WDL\s*TFR|WDL\s*CLG|DEBIT|MANDATE\s*DEBIT|TO\s+TRANSFER|DR\b|UPI/DR)\b""", RegexOption.IGNORE_CASE).containsMatchIn(cl)) {
                        typeMarker = TransactionType.EXPENSE
                    }

                    if (amtRe.matches(cl) || cl == "-") {
                        tokens.add(cl)
                    } else {
                        narrationLines.add(cl)
                    }
                }

                val currencyTokens = tokens.filter { amtRe.matches(it) }
                var txnAmount = 0.0
                var finalType = typeMarker

                if (tokens.size >= 3) {
                    val candDebit = tokens[tokens.size - 3]
                    val candCredit = tokens[tokens.size - 2]

                    if (candCredit != "-" && amtRe.matches(candCredit)) {
                        val amt = cleanAmount(candCredit)
                        if (amt > 0) {
                            txnAmount = amt
                            finalType = TransactionType.INCOME
                        }
                    } else if (candDebit != "-" && amtRe.matches(candDebit)) {
                        val amt = cleanAmount(candDebit)
                        if (amt > 0) {
                            txnAmount = amt
                            finalType = TransactionType.EXPENSE
                        }
                    }
                } else if (currencyTokens.size >= 2) {
                    val amt = cleanAmount(currencyTokens[currencyTokens.size - 2])
                    if (amt > 0) txnAmount = amt
                } else if (currencyTokens.size == 1) {
                    val amt = cleanAmount(currencyTokens[0])
                    if (amt > 0) txnAmount = amt
                }

                if (txnAmount > 0) {
                    val rawNarration = (line.replace(dateM.value, "").trim() + " " + narrationLines.joinToString(" ")).trim()
                    val cleanNarration = rawNarration.replace(Regex("""AT\s+\d+.*""", RegexOption.IGNORE_CASE), "").replace(Regex("""\s+"""), " ").trim()
                    val ref = Regex("""(?:UPI|UTR|IMPS|NEFT|REF)[/\s:-]*([0-9A-Za-z]{8,18})""", RegexOption.IGNORE_CASE).find(rawNarration)?.groupValues?.getOrNull(1) ?: ""
                    val cat = CategorizerEngine.categorize(cleanNarration.ifBlank { "SBI Transaction" }, txnAmount, customRules)
                    val timeStr = extractTime(line) ?: chunk.firstNotNullOfOrNull { extractTime(it) }
                    val parsedDate = parseDate(rawDate, timeStr)

                    list.add(
                        TransactionEntity(
                            date = parsedDate ?: System.currentTimeMillis(),
                            description = cat.cleanTitle,
                            amount = txnAmount,
                            type = finalType,
                            category = cat.category,
                            accountId = accountId,
                            accountName = accountName.ifBlank { "State Bank of India" },
                            note = if (parsedDate == null) undatedNote(rawDate) else "",
                            referenceNo = ref,
                            paymentMode = if (cleanNarration.contains("upi", ignoreCase = true)) "UPI" else "Online / Bank Transfer",
                            sourceFile = fileName,
                            rawNarration = cleanNarration,
                            needsReview = cat.needsReview || parsedDate == null,
                            confidence = if (parsedDate == null) "low" else cat.confidence
                        )
                    )

                    if (finalType == TransactionType.INCOME) inflow += txnAmount else outflow += txnAmount
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

                val isRefund = line.contains("Refund", ignoreCase = true) || line.contains("Reversal", ignoreCase = true)
                val isCredit = line.contains(" CR", ignoreCase = true) || line.contains("Payment Received", ignoreCase = true) || isRefund
                val explicitType = when {
                    isRefund -> TransactionType.REFUND
                    isCredit -> TransactionType.INCOME
                    else -> TransactionType.EXPENSE
                }

                val narration = line.replace(dateM.value, "").replace(amtM.value, "").replace(" CR", "", ignoreCase = true).trim()
                val cat = CategorizerEngine.categorize(narration, amt, customRules)
                val timeStr = extractTime(line)
                val date = parseDate(dateM.value, timeStr) ?: continue

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

                if (explicitType == TransactionType.INCOME || explicitType == TransactionType.REFUND) inflow += amt else outflow += amt
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

                val isRefund = line.contains("refund", ignoreCase = true) || line.contains("reversal", ignoreCase = true)
                val isCredit = line.contains("credit", ignoreCase = true) || line.contains("deposit", ignoreCase = true) ||
                        line.contains("received", ignoreCase = true) || isRefund ||
                        line.contains("salary", ignoreCase = true) || (line.contains("cr", ignoreCase = true) && !line.contains("debit", ignoreCase = true))

                val explicitType = when {
                    isRefund -> TransactionType.REFUND
                    isCredit -> TransactionType.INCOME
                    else -> TransactionType.EXPENSE
                }
                var narration = line.replace(dateM.value, "").replace(amtM.value, "").trim()

                if (i + 1 < lines.size && !dateRegex.containsMatchIn(lines[i + 1]) && lines[i + 1].length in 4..100) {
                    narration += " " + lines[i + 1]
                }

                val cat = CategorizerEngine.categorize(narration, amt, customRules)
                val timeStr = extractTime(line) ?: (if (i + 1 < lines.size) extractTime(lines[i + 1]) else null)
                val date = parseDate(dateM.value, timeStr) ?: continue

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

                if (explicitType == TransactionType.INCOME || explicitType == TransactionType.REFUND) inflow += amt else outflow += amt
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
        val timeIdx = headerCols.indexOfFirst { it == "time" || it.contains("txn time") || it.contains("trans time") || it.contains("time of txn") }

        val fullText = lines.joinToString(" ")
        val accountMetadata = extractAccountMetadata(fullText, fileName)
        val detectedProfile = accountMetadata.bankName + (if (accountMetadata.type == "Credit Card") " Card CSV" else " Statement CSV")
        val effectiveAccountName = accountName.ifBlank { accountMetadata.name }
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
            val rawTime = if (timeIdx >= 0 && timeIdx < cols.size) cols[timeIdx] else extractTime(rawDate)

            val parsedDate = parseDate(rawDate, rawTime)

            var amount = 0.0
            var explicitType: TransactionType? = null
            val isRefund = rawDesc.contains("refund", ignoreCase = true) || rawDesc.contains("reversal", ignoreCase = true)

            if (debitIdx >= 0 && debitIdx < cols.size && cleanAmount(cols[debitIdx]) > 0) {
                amount = cleanAmount(cols[debitIdx])
                explicitType = TransactionType.EXPENSE
            } else if (creditIdx >= 0 && creditIdx < cols.size && cleanAmount(cols[creditIdx]) > 0) {
                amount = cleanAmount(cols[creditIdx])
                explicitType = if (isRefund) TransactionType.REFUND else TransactionType.INCOME
            } else if (amountIdx >= 0 && amountIdx < cols.size) {
                val rawAmt = cols[amountIdx]
                val amtVal = cleanAmount(rawAmt)
                amount = abs(amtVal)

                if (typeIdx >= 0 && typeIdx < cols.size) {
                    val typeStr = cols[typeIdx].lowercase()
                    if (typeStr.contains("refund") || typeStr.contains("reversal") || isRefund) {
                        explicitType = TransactionType.REFUND
                    } else if (typeStr.contains("cr") || typeStr.contains("credit") || typeStr.contains("income")) {
                        explicitType = TransactionType.INCOME
                    } else if (typeStr.contains("dr") || typeStr.contains("debit") || typeStr.contains("expense")) {
                        explicitType = TransactionType.EXPENSE
                    }
                } else if (rawAmt.contains("-") || rawAmt.lowercase().contains("dr")) {
                    explicitType = TransactionType.EXPENSE
                } else if (rawAmt.contains("+") || rawAmt.lowercase().contains("cr")) {
                    explicitType = if (isRefund) TransactionType.REFUND else TransactionType.INCOME
                } else if (isRefund) {
                    explicitType = TransactionType.REFUND
                }
            }

            if (amount <= 0) continue

            val catResult = CategorizerEngine.categorize(rawDesc, amount, customRules)
            val finalType = explicitType ?: (if (isRefund) TransactionType.REFUND else catResult.type)

            val ruPayMeta = detectRuPayCC(rawDesc)
            val txnAccountName = ruPayMeta?.name ?: effectiveAccountName
            val paymentMode = if (ruPayMeta != null) "UPI (RuPay Credit Card)" else if (rawDesc.contains("upi", ignoreCase = true)) "UPI" else if (accountMetadata.type == "Credit Card") "Credit Card" else "Bank Transfer / Online"

            val entity = TransactionEntity(
                date = parsedDate ?: System.currentTimeMillis(),
                description = catResult.cleanTitle,
                amount = amount,
                type = finalType,
                category = catResult.category,
                accountId = accountId,
                accountName = txnAccountName,
                note = if (parsedDate == null) undatedNote(rawDate) else "",
                referenceNo = rawRef.trim(),
                paymentMode = paymentMode,
                sourceFile = fileName,
                rawNarration = rawDesc,
                needsReview = catResult.needsReview || parsedDate == null,
                confidence = if (parsedDate == null) "low" else catResult.confidence
            )

            parsedList.add(entity)

            if (finalType == TransactionType.INCOME || finalType == TransactionType.REFUND) {
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
            totalOutflow = totalOutflow,
            accountMetadata = accountMetadata
        )
    }

    fun extractAccountMetadata(fullText: String, fileName: String): AccountMetadata {
        val fn = fileName.lowercase()
        val header = fullText.take(1500).lowercase()
        val text = (fullText + " " + fileName).lowercase()

        // 1. Bank Issuer Name & Color Theme Gradient Index
        // 0: Royal Navy, 1: Emerald Green, 2: Crimson Burgundy, 3: Stealth Black, 4: Amber Gold
        var bankName = "Primary Bank"
        var gradientIndex = 0

        // UPI apps first: their transaction lists or account columns repeat underlying bank names (e.g. State Bank of India) in the header, which must not claim the whole file
        if (fn.contains("navi") || header.contains("paid via navi") || header.contains("navi technologies")) {
            bankName = "Navi UPI"
            gradientIndex = 0
        } else if (fn.contains("google pay") || fn.contains("gpay") || header.contains("google pay app") || header.contains("payments made by you on the google pay")) {
            bankName = "Google Pay"
            gradientIndex = 0
        } else if (fn.contains("phonepe") || header.contains("phonepe statement") || header.contains("phonepe private limited")) {
            bankName = "PhonePe"
            gradientIndex = 2
        } else if (fn.contains("paytm") || header.contains("paytm payments bank") || header.contains("paytm wallet") || header.contains("paytm transaction statement")) {
            bankName = "Paytm"
            gradientIndex = 0
        } else if (fn.contains("sbi") || fn.contains("state bank") || header.contains("state bank of india") || Regex("""\bsbin\b""", RegexOption.IGNORE_CASE).containsMatchIn(header) || header.contains("wdl tfr") || header.contains("dep tfr")) {
            bankName = "State Bank of India (SBI)"
            gradientIndex = 1
        } else if (header.contains("phonepe")) {
            bankName = "PhonePe"
            gradientIndex = 2
        } else if (header.contains("paytm")) {
            bankName = "Paytm"
            gradientIndex = 0
        } else if (header.contains("google pay") || Regex("""\bgpay\b""", RegexOption.IGNORE_CASE).containsMatchIn(header)) {
            bankName = "Google Pay"
            gradientIndex = 0
        } else if (fn.contains("hdfc") || header.contains("hdfc bank") || header.contains("www.hdfcbank.com") || header.contains("hdfc card")) {
            bankName = "HDFC Bank"
            gradientIndex = 0
        } else if (fn.contains("icici") || header.contains("icici bank") || header.contains("icici card")) {
            bankName = "ICICI Bank"
            gradientIndex = 4
        } else if (fn.contains("axis") || header.contains("axis bank") || header.contains("axis card")) {
            bankName = "Axis Bank"
            gradientIndex = 2
        } else if (fn.contains("kotak") || header.contains("kotak mahindra") || header.contains("kotak card")) {
            bankName = "Kotak Mahindra Bank"
            gradientIndex = 2
        } else if (fn.contains("pnb") || fn.contains("punjab national") || header.contains("punjab national") || Regex("""\bpnb\b""", RegexOption.IGNORE_CASE).containsMatchIn(header)) {
            bankName = "Punjab National Bank (PNB)"
            gradientIndex = 4
        } else if (fn.contains("baroda") || header.contains("bank of baroda") || Regex("""\bbob\b""", RegexOption.IGNORE_CASE).containsMatchIn(fn)) {
            bankName = "Bank of Baroda"
            gradientIndex = 4
        } else if (fn.contains("canara") || header.contains("canara bank")) {
            bankName = "Canara Bank"
            gradientIndex = 0
        } else if (fn.contains("union") || header.contains("union bank")) {
            bankName = "Union Bank of India"
            gradientIndex = 1
        } else if (Regex("""\bcred\b""", RegexOption.IGNORE_CASE).containsMatchIn(fn) || Regex("""\bcred\b""", RegexOption.IGNORE_CASE).containsMatchIn(header)) {
            bankName = "CRED"
            gradientIndex = 3
        } else if (header.contains("amazon pay")) {
            bankName = "Amazon Pay"
            gradientIndex = 4
        } else if (text.contains("hdfc bank") && !text.contains("wdl tfr")) {
            bankName = "HDFC Bank"
            gradientIndex = 0
        } else if (text.contains("state bank") || text.contains("sbi")) {
            bankName = "State Bank of India (SBI)"
            gradientIndex = 1
        } else {
            val cleanName = fileName.substringBeforeLast('.').replace(Regex("""[_-]"""), " ").trim()
            bankName = if (cleanName.length > 2) cleanName else "Personal Account"
            gradientIndex = 0
        }

        // 2. Detect Instrument Type
        val isBankStatement = text.contains("wdl tfr") || text.contains("dep tfr") || text.contains("savings account") || text.contains("current account") || text.contains("clear balance")
        val type = when {
            !isBankStatement && (text.contains("credit card statement") || text.contains("minimum amount due") || text.contains("total amount due") || text.contains("card statement") || text.contains("card ending in")) -> "Credit Card"
            // a bank statement's narrations often mention gpay/phonepe/paytm; that never makes the account a wallet
            !isBankStatement && (bankName == "Navi UPI" || text.contains("google pay") || text.contains("gpay") || text.contains("phonepe") || text.contains("paytm wallet") || text.contains("amazon pay wallet") || text.contains("upi history") || text.contains("upi statement") || text.contains("navi upi")) -> "Digital Wallet"
            text.contains("cash in hand") || text.contains("cash statement") -> "Cash"
            else -> "Bank Account"
        }

        // 3. Detect Account / Card Number Last 4 digits
        var last4 = ""
        val keywordMatch = Regex("""(?:account(?:\s*number|\s*no\.?)?|a/c(?:\s*number|\s*no\.?)?|acct|card(?:\s*number|\s*no\.?)?|ending in|ending with|xx|[*X]{4,12})[\s#:-]*([0-9]{4,18})""", RegexOption.IGNORE_CASE).find(fullText)
        if (keywordMatch != null) {
            val numStr = keywordMatch.groupValues[1].trim()
            if (numStr.length >= 4) {
                last4 = numStr.takeLast(4)
            }
        }

        if (last4.isEmpty() && (bankName.contains("SBI", ignoreCase = true) || bankName.contains("State Bank", ignoreCase = true))) {
            // pdf text lists the header labels first and the values after, in the same order: CIF, then Account Number
            val nums = Regex("""\b(\d{11})\b""").findAll(fullText).map { it.groupValues[1] }.toList()
            val cifAt = Regex("""cif\s*(?:number|no)""", RegexOption.IGNORE_CASE).find(fullText)?.range?.first ?: -1
            val accAt = Regex("""account\s*(?:number|no)""", RegexOption.IGNORE_CASE).find(fullText)?.range?.first ?: -1
            val pick = if (cifAt >= 0 && accAt > cifAt && nums.size > 1) nums[1] else nums.firstOrNull()
            if (pick != null) last4 = pick.takeLast(4)
        }

        if (last4.isEmpty()) {
            val cardPattern = Regex("""\b\d{4}\s*\d{4}\s*\d{4}\s*(\d{4})\b""").find(fullText)
            if (cardPattern != null) last4 = cardPattern.groupValues[1]
        }

        if (last4.isEmpty()) {
            val maskedPattern = Regex("""\b[X*]{4,12}\s*(\d{4})\b""").find(fullText)
            if (maskedPattern != null) last4 = maskedPattern.groupValues[1]
        }

        if (last4.isEmpty()) {
            val fnDigits = Regex("""\b(\d{4})\b""").find(fileName)
            if (fnDigits != null) {
                val num = fnDigits.groupValues[1].toIntOrNull() ?: 0
                if (num < 2020 || num > 2035) {
                    last4 = fnDigits.groupValues[1]
                }
            }
        }

        if (last4.isEmpty()) {
            val longAccMatch = Regex("""\b(\d{11,18})\b""").find(fullText)
            if (longAccMatch != null) {
                last4 = longAccMatch.groupValues[1].takeLast(4)
            }
        }

        // 4. Generate user-friendly Account Name
        val name = when (type) {
            "Credit Card" -> "$bankName Credit Card" + (if (last4.isNotBlank()) " (•••• $last4)" else "")
            "Digital Wallet" -> if (bankName.contains("UPI", ignoreCase = true)) "$bankName Wallet" else if (bankName.contains("Wallet", ignoreCase = true)) bankName else "$bankName UPI Wallet"
            "Cash" -> "Cash in Hand"
            else -> "$bankName Account" + (if (last4.isNotBlank()) " (•••• $last4)" else "")
        }

        // 5. Credit limit detection
        var creditLimit = 100000.0
        if (type == "Credit Card") {
            val limitMatch = Regex("""credit\s*limit[:\s]*(?:₹|rs\.?|inr)?\s*([0-9,]+)""", RegexOption.IGNORE_CASE).find(fullText)
            if (limitMatch != null) {
                val parsedLimit = limitMatch.groupValues[1].replace(",", "").toDoubleOrNull()
                if (parsedLimit != null && parsedLimit > 0) creditLimit = parsedLimit
            }
        }

        return AccountMetadata(
            bankName = bankName,
            type = type,
            lastFour = last4.ifBlank { if (type == "Digital Wallet") "UPI" else "0000" },
            name = name,
            creditLimit = creditLimit,
            gradientIndex = gradientIndex,
            isRuPay = false
        )
    }

    fun detectRuPayCC(text: String): AccountMetadata? {
        if (text.isBlank()) return null
        val lower = text.lowercase()

        val hasRuPay = Regex("""\b(rupay|rupay\s*cc|rupay\s*credit\s*card|rupay\s*card|upi-rupay|rupay-cc)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        val hasCreditCardUPI = Regex("""\b(credit\s*card|linked\s*card|cc\s*on\s*upi|card\s*ending)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower) &&
                Regex("""\b(upi|paid\s*via|debited\s*from|instrument|gpay|phonepe|paytm|cred)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)

        if (!hasRuPay && !hasCreditCardUPI) return null

        var bankName = "RuPay"
        var gradientIndex = 1

        if (text.contains("hdfc", ignoreCase = true)) { bankName = "HDFC Bank"; gradientIndex = 0 }
        else if (text.contains("icici", ignoreCase = true)) { bankName = "ICICI Bank"; gradientIndex = 4 }
        else if (text.contains("sbi", ignoreCase = true) || text.contains("state bank", ignoreCase = true)) { bankName = "SBI Card"; gradientIndex = 1 }
        else if (text.contains("axis", ignoreCase = true)) { bankName = "Axis Bank"; gradientIndex = 2 }
        else if (text.contains("kotak", ignoreCase = true)) { bankName = "Kotak Bank"; gradientIndex = 2 }
        else if (text.contains("pnb", ignoreCase = true) || text.contains("punjab", ignoreCase = true)) { bankName = "PNB"; gradientIndex = 4 }
        else if (text.contains("baroda", ignoreCase = true)) { bankName = "Bank of Baroda"; gradientIndex = 4 }
        else if (text.contains("canara", ignoreCase = true)) { bankName = "Canara Bank"; gradientIndex = 0 }
        else if (text.contains("union", ignoreCase = true)) { bankName = "Union Bank"; gradientIndex = 1 }

        var last4 = ""
        val numMatch = Regex("""(?:card|rupay|cc|ending(?:\s*in)?|xx|[*X]{2,12}|a/c)[\s#:-]*([0-9]{2,4})\b""", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("""\b[xX*]{2,}\s*([0-9]{2,4})\b""").find(text)
        if (numMatch != null) {
            last4 = numMatch.groupValues[1]
        } else {
            val standAlone4 = Regex("""\b([0-9]{4})\b""").find(text)
            if (standAlone4 != null) {
                val num = standAlone4.groupValues[1].toIntOrNull() ?: 0
                if (num < 2020 || num > 2035) {
                    last4 = standAlone4.groupValues[1]
                }
            }
        }

        val cardName = "$bankName RuPay Credit Card" + (if (last4.isNotBlank()) " (•••• $last4)" else "")

        return AccountMetadata(
            bankName = bankName,
            type = "Credit Card",
            lastFour = last4.ifBlank { "0000" },
            name = cardName,
            creditLimit = 100000.0,
            gradientIndex = gradientIndex,
            isRuPay = true
        )
    }

    private fun detectBankFromText(text: String, fileName: String): String {
        return extractAccountMetadata(text, fileName).bankName
    }


    private fun cleanAmount(raw: String): Double {
        val cleaned = raw.replace(Regex("""[^\d.-]"""), "")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun parseDate(dateStr: String, timeStr: String? = null, nowMs: Long = System.currentTimeMillis()): Long? {
        val cleanDate = dateStr.trim().replace(Regex("""\s+"""), " ")
        if (cleanDate.isBlank()) return null

        val cleanTime = timeStr?.trim()?.replace(Regex("""\s+"""), " ")?.replace(Regex("""(?i)(\d)(am|pm)"""), "$1 $2")?.uppercase()
        if (!cleanTime.isNullOrBlank()) {
            val combined = "$cleanDate $cleanTime"
            val maxYear = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.YEAR) + 1
            for (pattern in supportedDateFormats) {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
                val pos = ParsePosition(0)
                val d = sdf.parse(combined, pos) ?: continue
                if (pos.index != combined.length) continue
                val year = Calendar.getInstance().apply { time = d }.get(Calendar.YEAR)
                if (year in MIN_VALID_YEAR..maxYear) return d.time
            }
        }

        val maxYear = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.YEAR) + 1
        for (pattern in supportedDateFormats) {
            val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
            val pos = ParsePosition(0)
            val d = sdf.parse(cleanDate, pos) ?: continue
            if (pos.index != cleanDate.length) continue
            val year = Calendar.getInstance().apply { time = d }.get(Calendar.YEAR)
            if (year in MIN_VALID_YEAR..maxYear) return d.time
        }

        val shape = monthFirstShape.find(cleanDate) ?: return null
        val first = shape.groupValues[1].toInt()
        val second = shape.groupValues[2].toInt()
        if (second !in 13..31 || first !in 1..12) return null // ambiguous or not a date: leave it flagged
        for (pattern in monthFirstFormats) {
            val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
            val pos = ParsePosition(0)
            val d = sdf.parse(cleanDate, pos) ?: continue
            if (pos.index != cleanDate.length) continue
            val year = Calendar.getInstance().apply { time = d }.get(Calendar.YEAR)
            if (year in MIN_VALID_YEAR..maxYear) return d.time
        }
        return null
    }

    fun parseDate(dateStr: String, nowMs: Long): Long? = parseDate(dateStr, null, nowMs)

    private fun undatedNote(raw: String) = "Date \"$raw\" not recognised; set to import date, please correct."

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    // RFC 4180 escaped quote ""
                    cur.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(cur.toString().trim(' ', '\"'))
                cur.clear()
            } else {
                cur.append(ch)
            }
            i++
        }
        result.add(cur.toString().trim(' ', '\"'))
        return result
    }
}

