package com.wnoicew.expensetracker.data.engine

import com.wnoicew.expensetracker.data.model.DuplicatePair
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
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
                        break
                    }
                }
            }
        }

        return detectedPairs
    }

    fun compareTransactions(t1: TransactionEntity, t2: TransactionEntity): MatchResult {
        if (t1.id == t2.id) return MatchResult(false, 0, "")

        // 1. Amount Check
        if (abs(t1.amount - t2.amount) > 0.01) {
            return MatchResult(false, 0, "")
        }

        // 2. Exact Reference / UTR Match (High Confidence)
        val ref1 = t1.referenceNo.trim().lowercase()
        val ref2 = t2.referenceNo.trim().lowercase()

        if (ref1.length > 6 && ref2.length > 6 && (ref1 == ref2 || ref1.contains(ref2) || ref2.contains(ref1))) {
            return MatchResult(true, 99, "Identical UTR / Reference: ${t1.referenceNo}")
        }

        val narr1 = (t1.rawNarration + " " + t1.description).lowercase()
        val narr2 = (t2.rawNarration + " " + t2.description).lowercase()

        if (ref1.length > 6 && narr2.contains(ref1)) {
            return MatchResult(true, 98, "Statement contains UTR reference: $ref1")
        }
        if (ref2.length > 6 && narr1.contains(ref2)) {
            return MatchResult(true, 98, "Statement contains UTR reference: $ref2")
        }

        // 3. Date Proximity Check (Within ±24 hours / 1 day)
        val diffMs = abs(t1.date - t2.date)
        val diffDays = diffMs / (1000 * 60 * 60 * 24)

        if (diffDays <= 1) {
            val tokens1 = tokenizeText(narr1)
            val tokens2 = tokenizeText(narr2)

            val commonTokens = tokens1.intersect(tokens2)
            val ignoreWords = setOf("bank", "transfer", "payment", "paid", "imps", "upi", "neft", "dr", "cr", "online")
            val hasSignificantMatch = commonTokens.any { it.length > 3 && !ignoreWords.contains(it) }

            if (hasSignificantMatch) {
                return MatchResult(
                    true,
                    90,
                    "Exact amount on same date with matching merchant: ${commonTokens.filter { !ignoreWords.contains(it) }.joinToString(", ")}"
                )
            }

            if (t1.sourceFile.isNotBlank() && t2.sourceFile.isNotBlank() && t1.sourceFile != t2.sourceFile && diffDays == 0L) {
                return MatchResult(
                    true,
                    80,
                    "Cross-statement match between ${t1.sourceFile} and ${t2.sourceFile}"
                )
            }
        }

        return MatchResult(false, 0, "")
    }

    private fun tokenizeText(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("""[^a-z0-9]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length >= 3 }
            .toSet()
    }

    fun mergeTransactions(primary: TransactionEntity, duplicate: TransactionEntity): Pair<TransactionEntity, TransactionEntity> {
        val bestDesc = if (primary.description.length >= duplicate.description.length) primary.description else duplicate.description
        val bestCat = if (primary.category != "Uncategorized") primary.category else duplicate.category

        val updatedPrimary = primary.copy(
            description = bestDesc,
            category = bestCat,
            isDuplicate = false,
            duplicateStatus = "merged_primary",
            duplicateWithId = duplicate.id,
            note = (primary.note + " | Merged with cross-statement record from ${duplicate.sourceFile}").trim(' ', '|')
        )

        val updatedDuplicate = duplicate.copy(
            isDuplicate = false,
            duplicateStatus = "merged",
            type = TransactionType.TRANSFER, // Neutralized from expenses
            note = "Merged into ${primary.id} (Original Source: ${duplicate.sourceFile})"
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
