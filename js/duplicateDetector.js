/**
 * Smart Cross-Statement Duplicate Detector & Merging Engine
 * Accurately detects identical transactions between UPI apps (GPay, PhonePe, Paytm)
 * and Bank Statements (HDFC, SBI, ICICI, etc.) with automated & manual reconciliation.
 */

class DuplicateDetector {
  constructor() {}

  /**
   * Run full cross-statement duplicate scan across all transactions in IndexedDB
   */
  async scanDatabase() {
    const transactions = await window.db.getAll('transactions');
    if (!transactions || transactions.length < 2) {
      return { duplicatesFound: 0, pairs: [] };
    }

    const detectedPairs = [];
    const updatedTransactions = [...transactions];

    // Reset duplicate flags first (unless explicitly merged or dismissed)
    updatedTransactions.forEach(t => {
      if (t.duplicateStatus !== 'merged' && t.duplicateStatus !== 'dismissed') {
        t.isDuplicate = false;
        t.duplicateWithId = null;
        t.duplicateStatus = 'none';
        t.duplicateConfidence = 0;
        t.duplicateReason = '';
      }
    });

    // Group transactions by amount bucket for ultra-fast O(1) candidate lookup
    const amountMap = new Map();
    const activeCandidates = [];

    for (let i = 0; i < updatedTransactions.length; i++) {
      const t = updatedTransactions[i];
      if (t.duplicateStatus === 'merged' || t.duplicateStatus === 'dismissed') continue;

      const amtKey = Math.round(Math.abs(parseFloat(t.amount) || 0) * 100);
      if (!amountMap.has(amtKey)) {
        amountMap.set(amtKey, []);
      }
      amountMap.get(amtKey).push(t);
    }

    const matchedIds = new Set();

    const deletedIds = new Set();

    // Clean up any legacy merged records so only one record is retained
    for (const t of transactions) {
      if (t.duplicateStatus === 'merged') {
        await window.db.delete('transactions', t.id);
        deletedIds.add(t.id);
      }
    }

    // Iterate through buckets with 2+ candidates
    for (const [amtKey, bucket] of amountMap.entries()) {
      if (bucket.length < 2) continue;

      for (let i = 0; i < bucket.length; i++) {
        const t1 = bucket[i];
        if (matchedIds.has(t1.id) || deletedIds.has(t1.id)) continue;

        for (let j = i + 1; j < bucket.length; j++) {
          const t2 = bucket[j];
          if (matchedIds.has(t2.id) || deletedIds.has(t2.id)) continue;

          // Compare t1 and t2
          const matchResult = this.compareTransactions(t1, t2);
          if (matchResult.isMatch) {
            matchedIds.add(t1.id);
            matchedIds.add(t2.id);

            if (matchResult.confidence >= 99) {
              // 99%+ Confidence: Auto-merge & enrich, completely deleting the redundant record
              const bestDescription = t1.description.length >= t2.description.length ? t1.description : t2.description;
              const bestCategory = t1.category !== 'Miscellaneous' && t1.category !== 'Uncategorized' ? t1.category : t2.category;

              t1.description = bestDescription;
              t1.category = bestCategory;
              t1.notes = (t1.notes ? t1.notes + ' | ' : '') + `Auto-merged with cross-statement record from ${t2.sourceFile || 'statement'}`;
              t1.isDuplicate = false;
              t1.duplicateStatus = 'none';
              t1.duplicateWithId = null;
              t1.duplicateConfidence = 0;
              t1.duplicateReason = '';

              // Completely delete t2 from DB
              await window.db.delete('transactions', t2.id);
              deletedIds.add(t2.id);
            } else {
              // < 99% Confidence: Give to user in Duplicate Resolver for manual review
              t1.isDuplicate = true;
              t1.duplicateWithId = t2.id;
              t1.duplicateStatus = 'pending_review';
              t1.duplicateConfidence = matchResult.confidence;
              t1.duplicateReason = matchResult.reason;

              t2.isDuplicate = true;
              t2.duplicateWithId = t1.id;
              t2.duplicateStatus = 'pending_review';
              t2.duplicateConfidence = matchResult.confidence;
              t2.duplicateReason = matchResult.reason;

              detectedPairs.push({
                tx1: t1,
                tx2: t2,
                confidence: matchResult.confidence,
                reason: matchResult.reason
              });
            }
            break;
          }
        }
      }
    }

    // Save active updated transactions back to DB (excluding deleted records)
    const finalTransactions = updatedTransactions.filter(t => !deletedIds.has(t.id));
    await window.db.putBatch('transactions', finalTransactions);
    return {
      duplicatesFound: detectedPairs.length,
      pairs: detectedPairs
    };
  }

  /**
   * Compare two transactions and calculate duplicate probability
   * Strict Rule: Only detect as duplicate if Amount, Merchant, and Date ALL 3 MATCH.
   */
  compareTransactions(t1, t2) {
    if (t1.id === t2.id) return { isMatch: false, confidence: 0 };

    // 1. Date Check: MUST be on the exact same calendar day (YYYY-MM-DD)
    if (!this.isSameDate(t1.date, t2.date)) {
      return { isMatch: false, confidence: 0 };
    }

    // 2. Amount Check: MUST have identical amount
    const amt1 = Math.abs(parseFloat(t1.amount) || 0);
    const amt2 = Math.abs(parseFloat(t2.amount) || 0);
    if (Math.abs(amt1 - amt2) > 0.01) {
      return { isMatch: false, confidence: 0 };
    }

    // 3. Merchant / Payee Check: MUST match merchant or UTR reference
    const ref1 = (t1.referenceNo || '').trim().toLowerCase();
    const ref2 = (t2.referenceNo || '').trim().toLowerCase();
    const narr1 = (t1.rawNarration || '').toLowerCase();
    const narr2 = (t2.rawNarration || '').toLowerCase();

    const isUtrMatch = (ref1 && ref2 && ref1.length > 6 && (ref1 === ref2 || ref1.includes(ref2) || ref2.includes(ref1))) ||
                       (ref1 && ref1.length > 6 && narr2.includes(ref1)) ||
                       (ref2 && ref2.length > 6 && narr1.includes(ref2));

    const isMerchantMatch = this.isSameMerchant(t1, t2);

    if (isUtrMatch) {
      return {
        isMatch: true,
        confidence: 99,
        reason: `Identical UTR / Reference No on same date: ${t1.referenceNo}`
      };
    }

    if (isMerchantMatch) {
      return {
        isMatch: true,
        confidence: 95,
        reason: `Exact match: Same Date (${t1.date}), Amount (₹${amt1.toFixed(2)}), and Merchant (${t1.description})`
      };
    }

    return { isMatch: false, confidence: 0 };
  }

  isSameDate(d1Str, d2Str) {
    if (!d1Str || !d2Str) return false;
    // Normalize to YYYY-MM-DD
    const normalize = (s) => {
      if (typeof s === 'string' && /^\d{4}-\d{2}-\d{2}/.test(s)) return s.slice(0, 10);
      const d = new Date(s);
      if (!isNaN(d.getTime())) return d.toISOString().slice(0, 10);
      return String(s).trim();
    };
    return normalize(d1Str) === normalize(d2Str);
  }

  isSameMerchant(t1, t2) {
    const desc1 = (t1.description || '').trim().toLowerCase();
    const desc2 = (t2.description || '').trim().toLowerCase();
    if (desc1 && desc2 && desc1 === desc2) return true;

    const narr1 = (t1.rawNarration || '').toLowerCase();
    const narr2 = (t2.rawNarration || '').toLowerCase();
    const tokens1 = this.tokenizeText(desc1 + ' ' + narr1);
    const tokens2 = this.tokenizeText(desc2 + ' ' + narr2);

    const commonTokens = tokens1.filter(token => tokens2.includes(token));
    const ignoreWords = ['bank', 'transfer', 'payment', 'paid', 'imps', 'upi', 'neft', 'dr', 'cr', 'online', 'transaction', 'statement', 'account'];
    const significant = commonTokens.filter(tok => tok.length >= 3 && !ignoreWords.includes(tok));
    return significant.length > 0;
  }


  tokenizeText(text) {
    return (text || '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, ' ')
      .split(/\s+/)
      .filter(t => t.length >= 3);
  }

  /**
   * Check if two transactions are exact duplicates (identical date, amount, transaction ID/reference, and name)
   */
  isExactDuplicate(t1, t2) {
    // 1. Amount MUST match exactly
    const amt1 = Math.abs(parseFloat(t1.amount) || 0);
    const amt2 = Math.abs(parseFloat(t2.amount) || 0);
    if (Math.abs(amt1 - amt2) > 0.001) return false;

    // 2. Date MUST match on calendar day
    if (!this.isSameDate(t1.date, t2.date)) return false;

    // 3. Type / Direction check (income vs expense vs transfer)
    if (t1.type && t2.type && t1.type !== t2.type) return false;

    // 4. Ref / Transaction ID Check
    const ref1 = (t1.referenceNo || '').trim().toLowerCase();
    const ref2 = (t2.referenceNo || '').trim().toLowerCase();
    const hasRef1 = ref1 && !ref1.startsWith('ref_') && ref1.length >= 4;
    const hasRef2 = ref2 && !ref2.startsWith('ref_') && ref2.length >= 4;

    const narr1 = (t1.rawNarration || t1.description || '').trim().toLowerCase();
    const narr2 = (t2.rawNarration || t2.description || '').trim().toLowerCase();
    const desc1 = (t1.description || '').trim().toLowerCase();
    const desc2 = (t2.description || '').trim().toLowerCase();

    // If both have real reference IDs, check if they match
    if (hasRef1 && hasRef2) {
      if (ref1 === ref2 || ref1.includes(ref2) || ref2.includes(ref1)) {
        return true;
      }
    } else if (hasRef1 && (narr2.includes(ref1) || desc2.includes(ref1))) {
      return true;
    } else if (hasRef2 && (narr1.includes(ref2) || desc1.includes(ref2))) {
      return true;
    }

    // 5. Name / Description / Raw Narration Match
    const isNameMatch = (desc1 && desc1 === desc2) || (narr1 && narr1 === narr2) || this.isSameMerchant(t1, t2);
    if (isNameMatch) {
      if (!hasRef1 && !hasRef2) return true;
      if (hasRef1 === hasRef2 && ref1 === ref2) return true;
    }

    return false;
  }

  /**
   * Filter out exact duplicates during upload process
   * Compares incoming transactions against both existing DB records and current batch.
   */
  filterExactDuplicates(incomingTransactions = [], existingTransactions = []) {
    const accepted = [];
    let exactDuplicatesCount = 0;

    for (const item of incomingTransactions) {
      const matchesDb = existingTransactions.some(dbItem => this.isExactDuplicate(item, dbItem));
      const matchesBatch = accepted.some(batchItem => this.isExactDuplicate(item, batchItem));
      if (matchesDb || matchesBatch) {
        exactDuplicatesCount++;
      } else {
        accepted.push(item);
      }
    }

    return {
      filteredTransactions: accepted,
      exactDuplicatesCount
    };
  }

  /**
   * Action 1: Merge & Enrich (Keeps the best merchant details + links to actual bank account)
   */
  async mergeAndEnrich(primaryId, duplicateId) {
    const primary = await window.db.getById('transactions', primaryId);
    const duplicate = await window.db.getById('transactions', duplicateId);

    if (!primary) {
      if (duplicate) await window.db.delete('transactions', duplicateId);
      return false;
    }
    if (!duplicate) {
      primary.isDuplicate = false;
      primary.duplicateStatus = 'none';
      primary.duplicateWithId = null;
      await window.db.put('transactions', primary);
      return true;
    }

    // Pick the most informative description
    const bestDescription = primary.description.length >= duplicate.description.length 
      ? primary.description 
      : duplicate.description;

    // Pick the most specific category (not 'Miscellaneous' or 'Uncategorized' if other has specific)
    const bestCategory = primary.category !== 'Miscellaneous' && primary.category !== 'Uncategorized' 
      ? primary.category 
      : duplicate.category;

    // Update primary
    primary.description = bestDescription;
    primary.category = bestCategory;
    primary.notes = (primary.notes ? primary.notes + ' | ' : '') + `Merged with cross-statement record from ${duplicate.sourceFile || 'statement'}`;
    primary.isDuplicate = false;
    primary.duplicateStatus = 'none';
    primary.duplicateWithId = null;
    primary.duplicateConfidence = 0;
    primary.duplicateReason = '';

    // Save primary and completely delete duplicate from DB so only one record is retained
    await window.db.put('transactions', primary);
    await window.db.delete('transactions', duplicateId);
    return true;
  }

  /**
   * Action 2: Delete Duplicate
   */
  async deleteDuplicate(duplicateId, primaryId = null) {
    await window.db.delete('transactions', duplicateId);
    if (primaryId) {
      const primary = await window.db.getById('transactions', primaryId);
      if (primary) {
        primary.isDuplicate = false;
        primary.duplicateStatus = 'none';
        primary.duplicateWithId = null;
        await window.db.put('transactions', primary);
      }
    }
    return true;
  }

  /**
   * Action 3: Mark as Separate (Dismiss match)
   */
  async markAsSeparate(id1, id2) {
    const t1 = await window.db.getById('transactions', id1);
    const t2 = await window.db.getById('transactions', id2);

    if (t1) {
      t1.isDuplicate = false;
      t1.duplicateStatus = 'dismissed';
      t1.duplicateWithId = null;
      await window.db.put('transactions', t1);
    }
    if (t2) {
      t2.isDuplicate = false;
      t2.duplicateStatus = 'dismissed';
      t2.duplicateWithId = null;
      await window.db.put('transactions', t2);
    }
    return true;
  }
}

// Global instance
window.duplicateDetector = new DuplicateDetector();
