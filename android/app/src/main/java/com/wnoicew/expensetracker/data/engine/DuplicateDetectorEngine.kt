package com.wnoicew.expensetracker.data.engine

import com.wnoicew.expensetracker.data.model.DuplicatePair
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object DuplicateDetectorEngine {

    data class MatchResult(
        val isMatch: Boolean,
        val confidence: Int,
        val reason: String
    )

    fun scanDuplicates(transactions: List<TransactionEntity>): List<DuplicatePair> {
        if (transactions.size < 2) return emptyList()

        val detectedPairs = mutableListOf<DuplicatePair>()
        val matchedIds = mutableSetOf<String>()

        // Group by amount (in paise / cents) for fast O(1) candidate lookup
        val amountMap = mutableMapOf<Long, MutableList<TransactionEntity>>()

        for (t in transactions) {
            if (t.duplicateStatus == "merged" || t.duplicateStatus == "dismissed") continue
            val amtKey = (abs(t.amount) * 100).toLong()
            amountMap.getOrPut(amtKey) { mutableListOf() }.add(t)
        }

        for ((_, bucket) in amountMap) {
            if (bucket.size < 2) continue

            for (i in 0 until bucket.size) {
                val t1 = bucket[i]
                if (matchedIds.contains(t1.id)) continue

                for (j in i + 1 until bucket.size) {
                    val t2 = bucket[j]
                    if (matchedIds.contains(t2.id)) continue

                    val matchResult = compareTransactions(t1, t2)
                    if (matchResult.isMatch) {
                        matchedIds.add(t1.id)
                        matchedIds.add(t2.id)

                        // If less than 99% confidence, give it to the user in duplicate resolver for review
                        if (matchResult.confidence < 99) {
                            val pair = DuplicatePair(
                                primaryTxn = t1.copy(
                                    isDuplicate = true,
                                    duplicateWithId = t2.id,
                                    duplicateStatus = "pending_review",
                                    duplicateConfidence = matchResult.confidence,
                                    duplicateReason = matchResult.reason
                                ),
                                candidateTxn = t2.copy(
                                    isDuplicate = true,
                                    duplicateWithId = t1.id,
                                    duplicateStatus = "pending_review",
                                    duplicateConfidence = matchResult.confidence,
                                    duplicateReason = matchResult.reason
                                ),
                                confidence = matchResult.confidence,
                                reason = matchResult.reason
                            )
                            detectedPairs.add(pair)
                        }
                        break
                    }
                }
            }
        }

        return detectedPairs
    }

    fun compareTransactions(t1: TransactionEntity, t2: TransactionEntity): MatchResult {
        if (t1.id == t2.id) return MatchResult(false, 0, "")

        // 1. Date Check: MUST be on the exact same calendar day
        if (!isSameDay(t1.date, t2.date)) {
            return MatchResult(false, 0, "")
        }

        // 2. Amount Check: MUST have identical amount
        if (abs(t1.amount - t2.amount) > 0.01) {
            return MatchResult(false, 0, "")
        }

        // 3. Merchant / Payee Check: MUST match merchant or UTR reference
        val ref1 = t1.referenceNo.trim().lowercase()
        val ref2 = t2.referenceNo.trim().lowercase()
        val narr1 = (t1.rawNarration + " " + t1.description).lowercase()
        val narr2 = (t2.rawNarration + " " + t2.description).lowercase()

        val isUtrMatch = (ref1.length > 6 && ref2.length > 6 && (ref1 == ref2 || ref1.contains(ref2) || ref2.contains(ref1))) ||
                (ref1.length > 6 && narr2.contains(ref1)) ||
                (ref2.length > 6 && narr1.contains(ref2))

        val isMerchantMatch = isSameMerchant(t1, t2)

        if (isUtrMatch) {
            return MatchResult(true, 99, "Identical UTR / Reference on same date: ${t1.referenceNo}")
        }

        if (isMerchantMatch) {
            return MatchResult(
                true,
                95,
                "Exact match: Same Date, Amount (₹${t1.amount}), and Merchant: ${t1.description}"
            )
        }

        return MatchResult(false, 0, "")
    }

    private fun isSameDay(t1Ms: Long, t2Ms: Long): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        return sdf.format(Date(t1Ms)) == sdf.format(Date(t2Ms))
    }

    private fun isSameMerchant(t1: TransactionEntity, t2: TransactionEntity): Boolean {
        val desc1 = t1.description.trim().lowercase()
        val desc2 = t2.description.trim().lowercase()
        if (desc1.isNotBlank() && desc2.isNotBlank() && desc1 == desc2) return true

        val narr1 = (t1.rawNarration + " " + t1.description).lowercase()
        val narr2 = (t2.rawNarration + " " + t2.description).lowercase()
        val tokens1 = tokenizeText(narr1)
        val tokens2 = tokenizeText(narr2)

        val commonTokens = tokens1.intersect(tokens2)
        val ignoreWords = setOf("bank", "transfer", "payment", "paid", "imps", "upi", "neft", "dr", "cr", "online", "transaction", "statement", "account")
        return commonTokens.any { it.length >= 3 && !ignoreWords.contains(it) }
    }

    private fun tokenizeText(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("""[^a-z0-9]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length >= 3 }
            .toSet()
    }


    fun isExactDuplicate(incoming: TransactionEntity, existing: TransactionEntity): Boolean {
        // 1. Exact amount match
        if (abs(incoming.amount - existing.amount) > 0.001) return false

        // 2. Exact same calendar day
        if (!isSameDay(incoming.date, existing.date)) return false

        // 3. Same type (Income vs Expense)
        if (incoming.type != existing.type) return false

        val ref1 = incoming.referenceNo.trim().lowercase()
        val ref2 = existing.referenceNo.trim().lowercase()
        val hasRef1 = ref1.isNotBlank() && !ref1.startsWith("ref_") && ref1.length >= 4
        val hasRef2 = ref2.isNotBlank() && !ref2.startsWith("ref_") && ref2.length >= 4

        val narr1 = incoming.rawNarration.trim().lowercase()
        val narr2 = existing.rawNarration.trim().lowercase()
        val desc1 = incoming.description.trim().lowercase()
        val desc2 = existing.description.trim().lowercase()

        // 4. Ref / Transaction ID Match
        if (hasRef1 && hasRef2) {
            if (ref1 == ref2 || ref1.contains(ref2) || ref2.contains(ref1)) return true
        } else if (hasRef1 && (narr2.contains(ref1) || desc2.contains(ref1))) {
            return true
        } else if (hasRef2 && (narr1.contains(ref2) || desc1.contains(ref2))) {
            return true
        }

        // 5. Name / Description / Raw Narration Match
        val isNameMatch = (desc1.isNotBlank() && desc1 == desc2) || (narr1.isNotBlank() && narr1 == narr2) || isSameMerchant(incoming, existing)
        if (isNameMatch) {
            if (!hasRef1 && !hasRef2) return true
            if (hasRef1 == hasRef2 && ref1 == ref2) return true
        }

        return false
    }

    data class DeduplicationResult(
        val filteredTransactions: List<TransactionEntity>,
        val exactDuplicatesCount: Int
    )

    fun filterExactDuplicates(
        incoming: List<TransactionEntity>,
        existing: List<TransactionEntity>
    ): DeduplicationResult {
        val accepted = mutableListOf<TransactionEntity>()
        var exactDuplicatesCount = 0

        for (item in incoming) {
            val matchesDb = existing.any { isExactDuplicate(item, it) }
            val matchesBatch = accepted.any { isExactDuplicate(item, it) }
            if (matchesDb || matchesBatch) {
                exactDuplicatesCount++
            } else {
                accepted.add(item)
            }
        }
        return DeduplicationResult(accepted, exactDuplicatesCount)
    }

    fun mergeTransactions(primary: TransactionEntity, duplicate: TransactionEntity): Pair<TransactionEntity, TransactionEntity> {
        val bestDesc = if (primary.description.length >= duplicate.description.length) primary.description else duplicate.description
        val bestCat = if (primary.category != "Uncategorized" && primary.category != "Miscellaneous") primary.category else duplicate.category

        val updatedPrimary = primary.copy(
            description = bestDesc,
            category = bestCat,
            isDuplicate = false,
            duplicateStatus = "none",
            duplicateWithId = null,
            note = (primary.note + " | Merged with cross-statement record from ${duplicate.sourceFile}").trim(' ', '|')
        )

        val updatedDuplicate = duplicate.copy(
            isDuplicate = false,
            duplicateStatus = "deleted",
            type = TransactionType.TRANSFER,
            note = "Deleted redundant duplicate of ${primary.id}"
        )

        return Pair(updatedPrimary, updatedDuplicate)
    }

    fun markSeparate(t1: TransactionEntity, t2: TransactionEntity): Pair<TransactionEntity, TransactionEntity> {
        return Pair(
            t1.copy(isDuplicate = false, duplicateStatus = "dismissed", duplicateWithId = null),
            t2.copy(isDuplicate = false, duplicateStatus = "dismissed", duplicateWithId = null)
        )
    }
}
