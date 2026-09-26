package com.wnoicew.expensetracker.data.engine

import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import java.util.Locale
import kotlin.math.abs

enum class FriendBalanceStatus {
    OWES_YOU, // Positive net balance: user sent more money than received ("You get")
    YOU_OWE,  // Negative net balance: user received more money than sent ("You owe")
    SETTLED   // Zero balance ("Settled up")
}

data class FriendProfile(
    val id: String,
    val displayName: String,
    val extractedName: String,
    val isCustomName: Boolean,
    val upiId: String,
    val initials: String,
    val gradientIndex: Int,
    val totalOutgoing: Double,
    val totalIncoming: Double,
    val netBalance: Double,
    val status: FriendBalanceStatus,
    val statusText: String,
    val currency: String,
    val transactionCount: Int,
    val lastTransactionDate: Long,
    val transactions: List<TransactionEntity>
)

object FriendsEngine {

    private val UPI_REGEX = Regex("""([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
    private val PAID_TO_REGEX = Regex("""^(?:Paid\s+to|Paid\s+for|Received\s+from|Transfer\s+to|Transfer\s+from)\s+([^—–\[/\n]+)""", RegexOption.IGNORE_CASE)
    private val IMPS_REGEX = Regex("""(?:IMPS|NEFT)[\/*\s:-]+[0-9]*[\/*\s:-]*([a-zA-Z\s]{3,30})""", RegexOption.IGNORE_CASE)
    private val INCOMING_INDICATOR_REGEX = Regex("""\b(cr|credit|received|deposit)\b""", RegexOption.IGNORE_CASE)

    private val GENERIC_WORDS = setOf(
        "transaction", "expense", "income", "transfer", "friend", "friend transaction",
        "uncategorized", "unknown", "manual entry", "payment", "upi payment", "upi transfer"
    )

    fun extractUpiId(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val match = UPI_REGEX.find(text)
        return match?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: ""
    }

    fun isGenericWord(str: String?): Boolean {
        val s = (str ?: "").trim().lowercase(Locale.ROOT)
        return s.length < 2 || GENERIC_WORDS.contains(s)
    }

    fun formatName(str: String?): String {
        if (str.isNullOrBlank()) return ""
        return str
            .replace(Regex("""\b\d{8,}\s+AT\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bAT\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(dr|cr|upi|imps|neft|payment|paid|received|refund|transfer|p2p|p2a)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[_\-]+"""), " ")
            .trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
            .take(35)
    }

    fun extractFriendName(txn: TransactionEntity?): String {
        if (txn == null) return "Friend"

        val raw = txn.rawNarration.trim()
        val desc = txn.description.trim()

        if (raw.isNotBlank()) {
            // 1. Navi / GPay / PhonePe narration: "Paid to NAME — Note" or "Received from NAME"
            val pMatch = PAID_TO_REGEX.find(raw)
            if (pMatch != null) {
                val candidate = pMatch.groupValues[1].trim()
                if (candidate.length > 1) {
                    val cleaned = formatName(candidate)
                    if (cleaned.isNotBlank() && !isGenericWord(cleaned)) return cleaned
                }
            }

            // 2. UPI standard slash pattern: UPI/DR/<ref>/<NAME>/<BANK>/<VPA>/... or UPI/<NAME>/...
            if (raw.contains("UPI/", ignoreCase = true) || raw.contains("UPI /", ignoreCase = true)) {
                val parts = raw.split("/").map { it.trim() }.filter { it.isNotBlank() }
                val upiIdx = parts.indexOfFirst { it.equals("UPI", ignoreCase = true) }
                var payee = ""
                if (upiIdx != -1) {
                    if (parts.size > upiIdx + 3 && (parts[upiIdx + 1].equals("DR", ignoreCase = true) || parts[upiIdx + 1].equals("CR", ignoreCase = true))) {
                        payee = parts[upiIdx + 3]
                    } else if (parts.size > upiIdx + 1) {
                        payee = parts[upiIdx + 1]
                    }
                }
                if (payee.isNotBlank()) {
                    val cleaned = formatName(payee)
                    if (cleaned.isNotBlank() && !isGenericWord(cleaned)) return cleaned
                }
            }

            // 3. IMPS / NEFT / ACH payee pattern
            val impsMatch = IMPS_REGEX.find(raw)
            if (impsMatch != null) {
                val candidate = impsMatch.groupValues[1].trim()
                if (candidate.length > 2) {
                    val cleaned = formatName(candidate)
                    if (cleaned.isNotBlank() && !isGenericWord(cleaned)) return cleaned
                }
            }
        }

        // 4. Fallback to clean description if available and not generic
        if (desc.isNotBlank() && !isGenericWord(desc)) {
            val withoutNotes = desc.replace(Regex("""\s*\([^)]*\)$"""), "").trim()
            val cleaned = formatName(if (withoutNotes.isNotBlank()) withoutNotes else desc)
            if (cleaned.isNotBlank() && !isGenericWord(cleaned)) return cleaned
        }

        // 5. If UPI ID is present, format name from username part (e.g. rahul.sharma@okaxis -> Rahul Sharma)
        val upi = extractUpiId(txn.referenceNo).ifBlank {
            extractUpiId(raw).ifBlank {
                extractUpiId(desc)
            }
        }
        if (upi.isNotBlank()) {
            val handle = upi.substringBefore("@").replace(Regex("""[0-9._-]+"""), " ").trim()
            if (handle.length >= 2) {
                return formatName(handle)
            }
            return upi
        }

        // 6. Last resort
        return desc.ifBlank { "Friend" }
    }

    fun getFriendKey(txn: TransactionEntity?): String {
        if (txn == null) return "friend_unknown"

        // 1. UPI ID if present
        val upi = extractUpiId(txn.referenceNo).ifBlank {
            extractUpiId(txn.rawNarration).ifBlank {
                extractUpiId(txn.description)
            }
        }
        if (upi.isNotBlank()) {
            return "upi:$upi"
        }

        // 2. Account info / Last 4 if present in note or rawNarration
        val accMatch = Regex("""[Xx*•]{2,}(\d{2,4})""").find(txn.rawNarration)
            ?: Regex("""[Xx*•]{2,}(\d{2,4})""").find(txn.note)
        if (accMatch != null) {
            return "acc:${accMatch.groupValues[1]}"
        }

        // 3. Normalized Name key
        val name = extractFriendName(txn)
        val slug = name.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "_").replace(Regex("""_+"""), "_").take(40)
        return "name:${slug.ifBlank { "friend" }}"
    }

    fun getInitials(name: String): String {
        if (name.isBlank()) return "F"
        val parts = name.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        return if (parts.size == 1) {
            parts[0].take(2).uppercase(Locale.ROOT)
        } else {
            (parts[0].take(1) + parts.last().take(1)).uppercase(Locale.ROOT)
        }
    }

    fun computeFriendProfiles(
        transactions: List<TransactionEntity>,
        customNames: Map<String, String>,
        primaryCurrency: String = CurrencyEngine.DEFAULT_CURRENCY
    ): List<FriendProfile> {
        val friendTxns = transactions.filter { txn ->
            txn.duplicateStatus != "merged" && txn.category.trim().equals("Friend", ignoreCase = true)
        }

        val groups = friendTxns.groupBy { getFriendKey(it) }
        val profiles = mutableListOf<FriendProfile>()

        for ((key, txns) in groups) {
            val sorted = txns.sortedByDescending { it.date }
            val sampleTxn = sorted.first()
            val extracted = extractFriendName(sampleTxn)
            val upiId = extractUpiId(sampleTxn.referenceNo).ifBlank {
                extractUpiId(sampleTxn.rawNarration).ifBlank {
                    extractUpiId(sampleTxn.description).ifBlank {
                        if (key.startsWith("upi:")) key.removePrefix("upi:") else ""
                    }
                }
            }

            val customName = customNames[key]?.trim()
            val displayName = if (!customName.isNullOrBlank()) customName else extracted
            val isCustom = !customName.isNullOrBlank()

            var totalOutgoing = 0.0
            var totalIncoming = 0.0

            for (t in sorted) {
                val rawAmt = abs(t.amount)
                val amt = CurrencyEngine.convert(rawAmt, t.currency.ifBlank { primaryCurrency }, primaryCurrency)
                val isIncoming = t.type == TransactionType.INCOME ||
                        t.type == TransactionType.REFUND ||
                        (t.type == TransactionType.TRANSFER && INCOMING_INDICATOR_REGEX.containsMatchIn(t.rawNarration))

                if (isIncoming) {
                    totalIncoming += amt
                } else {
                    totalOutgoing += amt
                }
            }

            val netBalance = totalOutgoing - totalIncoming
            val status = when {
                netBalance > 0.009 -> FriendBalanceStatus.OWES_YOU
                netBalance < -0.009 -> FriendBalanceStatus.YOU_OWE
                else -> FriendBalanceStatus.SETTLED
            }
            val statusText = when (status) {
                FriendBalanceStatus.OWES_YOU -> "You get"
                FriendBalanceStatus.YOU_OWE -> "You owe"
                FriendBalanceStatus.SETTLED -> "Settled up"
            }

            val hash = abs(key.hashCode())
            val gradientIndex = hash % 6

            profiles.add(
                FriendProfile(
                    id = key,
                    displayName = displayName,
                    extractedName = extracted,
                    isCustomName = isCustom,
                    upiId = upiId,
                    initials = getInitials(displayName),
                    gradientIndex = gradientIndex,
                    totalOutgoing = totalOutgoing,
                    totalIncoming = totalIncoming,
                    netBalance = netBalance,
                    status = status,
                    statusText = statusText,
                    currency = primaryCurrency,
                    transactionCount = sorted.size,
                    lastTransactionDate = sorted.firstOrNull()?.date ?: 0L,
                    transactions = sorted
                )
            )
        }

        // Sort: unsettled balances first (by largest absolute net balance), then settled by newest date
        profiles.sortWith { a, b ->
            val unsettledA = if (abs(a.netBalance) > 0.01) 1 else 0
            val unsettledB = if (abs(b.netBalance) > 0.01) 1 else 0
            if (unsettledA != unsettledB) {
                unsettledB - unsettledA
            } else if (abs(b.netBalance) != abs(a.netBalance)) {
                abs(b.netBalance).compareTo(abs(a.netBalance))
            } else {
                b.lastTransactionDate.compareTo(a.lastTransactionDate)
            }
        }

        return profiles
    }
}
