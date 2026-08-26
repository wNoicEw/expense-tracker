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

    // Iterate through buckets with 2+ candidates
    for (const [amtKey, bucket] of amountMap.entries()) {
      if (bucket.length < 2) continue;

      for (let i = 0; i < bucket.length; i++) {
        const t1 = bucket[i];
        if (matchedIds.has(t1.id)) continue;

        for (let j = i + 1; j < bucket.length; j++) {
          const t2 = bucket[j];
          if (matchedIds.has(t2.id)) continue;

          // Compare t1 and t2
          const matchResult = this.compareTransactions(t1, t2);
          if (matchResult.isMatch) {
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

            matchedIds.add(t1.id);
            matchedIds.add(t2.id);

            detectedPairs.push({
              tx1: t1,
              tx2: t2,
              confidence: matchResult.confidence,
              reason: matchResult.reason
            });
            break;
          }
        }
      }
    }

    // Save updated transactions back to DB
    await window.db.putBatch('transactions', updatedTransactions);
    return {
      duplicatesFound: detectedPairs.length,
      pairs: detectedPairs
    };
  }

  /**
   * Compare two transactions and calculate duplicate probability
   */
  compareTransactions(t1, t2) {
    if (t1.id === t2.id) return { isMatch: false, confidence: 0 };

    // 1. Amount Check: Must have identical amount
    const amt1 = Math.abs(parseFloat(t1.amount) || 0);
    const amt2 = Math.abs(parseFloat(t2.amount) || 0);
    if (Math.abs(amt1 - amt2) > 0.01) {
      return { isMatch: false, confidence: 0 };
    }

    // 2. Exact Reference / UTR Match (High Confidence)
    const ref1 = (t1.referenceNo || '').trim().toLowerCase();
    const ref2 = (t2.referenceNo || '').trim().toLowerCase();
    
    // Check if reference numbers are non-trivial (> 6 chars) and match
    if (ref1 && ref2 && ref1.length > 6 && (ref1 === ref2 || ref1.includes(ref2) || ref2.includes(ref1))) {
      return {
        isMatch: true,
        confidence: 99,
        reason: `Identical UTR / Reference No: ${t1.referenceNo}`
      };
    }

    // Also check if t1's narration contains t2's reference number or vice-versa
    const narr1 = (t1.rawNarration || '').toLowerCase();
    const narr2 = (t2.rawNarration || '').toLowerCase();
    if (ref1 && ref1.length > 6 && narr2.includes(ref1)) {
      return {
        isMatch: true,
        confidence: 98,
        reason: `Bank statement contains UPI UTR reference: ${ref1}`
      };
    }
    if (ref2 && ref2.length > 6 && narr1.includes(ref2)) {
      return {
        isMatch: true,
        confidence: 98,
        reason: `UPI statement contains Bank UTR reference: ${ref2}`
      };
    }

    // 3. Date Proximity Check (Within ±24 hours / 1 day)
    const d1 = new Date(t1.date);
    const d2 = new Date(t2.date);
    const diffDays = Math.abs((d1 - d2) / (1000 * 60 * 60 * 24));

    if (diffDays <= 1) {
      // Check Merchant Similarity or Shared Brand Tokens
      const tokens1 = this.tokenizeText(t1.description + ' ' + narr1);
      const tokens2 = this.tokenizeText(t2.description + ' ' + narr2);
      
      const commonTokens = tokens1.filter(token => tokens2.includes(token));
      const hasSignificantMerchantMatch = commonTokens.some(tok => tok.length > 3 && !['bank', 'transfer', 'payment', 'paid', 'imps', 'upi', 'neft'].includes(tok));

      if (hasSignificantMerchantMatch) {
        return {
          isMatch: true,
          confidence: 90,
          reason: `Exact amount (₹${amt1}) on ${t1.date} with matching merchant (${commonTokens.join(', ')})`
        };
      }

      // If from different source files (e.g. Bank PDF vs UPI PDF) with exact amount on same date
      if (t1.sourceFile && t2.sourceFile && t1.sourceFile !== t2.sourceFile && diffDays === 0) {
        return {
          isMatch: true,
          confidence: 80,
          reason: `Cross-file match: ₹${amt1} on ${t1.date} between ${t1.sourceFile} and ${t2.sourceFile}`
        };
      }
    }

    return { isMatch: false, confidence: 0 };
  }

  tokenizeText(text) {
    return (text || '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, ' ')
      .split(/\s+/)
      .filter(t => t.length >= 3);
  }

  /**
   * Action 1: Merge & Enrich (Keeps the best merchant details + links to actual bank account)
   */
  async mergeAndEnrich(primaryId, duplicateId) {
    const primary = await window.db.getById('transactions', primaryId);
    const duplicate = await window.db.getById('transactions', duplicateId);

    if (!primary || !duplicate) return false;

    // Pick the most informative description
    const bestDescription = primary.description.length >= duplicate.description.length 
      ? primary.description 
      : duplicate.description;

    // Pick the most specific category (not 'Miscellaneous' if other has specific)
    const bestCategory = primary.category !== 'Miscellaneous' ? primary.category : duplicate.category;

    // Update primary
    primary.description = bestDescription;
    primary.category = bestCategory;
    primary.notes = (primary.notes ? primary.notes + ' | ' : '') + `Merged with cross-statement record from ${duplicate.sourceFile}`;
    primary.isDuplicate = false;
    primary.duplicateStatus = 'merged_primary';
    primary.mergedWith = duplicate.id;

    // Update duplicate as merged (so it doesn't count towards analytics / total expenses)
    duplicate.isDuplicate = false;
    duplicate.duplicateStatus = 'merged';
    duplicate.type = 'transfer'; // Neutralized so it doesn't double-count
    duplicate.notes = `Merged into ${primary.id} (Original Source: ${duplicate.sourceFile})`;

    await window.db.put('transactions', primary);
    await window.db.put('transactions', duplicate);
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
