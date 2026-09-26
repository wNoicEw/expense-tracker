/**
 * Personal Transactions & Friend Profiles Manager
 * 1. Groups transactions categorized as "Friend" by their UPI ID, account, or payee details.
 * 2. Collects and cleans friend names from raw transaction details / UPI narrations.
 * 3. Calculates peer-to-peer balance (Outgoing sent vs Incoming received) for each friend profile.
 * 4. Supports custom name renaming, profile deletion (untag or full purge), and individual txn removal.
 * 5. Visualizes personal friend ledgers without altering main financial calculations.
 */

class FriendsManager {
  constructor() {
    this.storagePrefix = 'expense_tracker_friends_';
  }

  getActiveProfileId() {
    return window.profileManager?.getActiveProfile()?.id || 'default';
  }

  getStorageKey() {
    return `${this.storagePrefix}${this.getActiveProfileId()}`;
  }

  getCustomProfiles() {
    try {
      const raw = localStorage.getItem(this.getStorageKey());
      return raw ? JSON.parse(raw) : {};
    } catch (e) {
      console.warn('Could not read friends from localStorage:', e);
      return {};
    }
  }

  saveCustomProfiles(profiles) {
    try {
      localStorage.setItem(this.getStorageKey(), JSON.stringify(profiles || {}));
    } catch (e) {
      console.warn('Could not save friends to localStorage:', e);
    }
  }

  getCustomProfile(friendKey) {
    const all = this.getCustomProfiles();
    return all[friendKey] || null;
  }

  setFriendCustomName(friendKey, newName) {
    if (!friendKey || !newName || !newName.trim()) return false;
    const all = this.getCustomProfiles();
    const existing = all[friendKey] || {};
    all[friendKey] = {
      ...existing,
      id: friendKey,
      name: newName.trim(),
      customName: newName.trim(),
      updatedAt: new Date().toISOString()
    };
    this.saveCustomProfiles(all);
    return true;
  }

  deleteCustomProfile(friendKey) {
    const all = this.getCustomProfiles();
    if (all[friendKey]) {
      delete all[friendKey];
      this.saveCustomProfiles(all);
    }
  }

  /**
   * Extract UPI ID or identifier from raw narration, identifier, or description
   */
  extractUpiId(text) {
    if (!text) return '';
    const match = String(text).match(/([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)/i);
    return match ? match[1].toLowerCase() : '';
  }

  /**
   * Extract clean person/friend name from transaction details
   */
  extractFriendName(txn) {
    if (!txn) return 'Friend';

    const raw = (txn.rawNarration || '').trim();
    const desc = (txn.description || '').trim();

    // 1. Navi / GPay / PhonePe narration: "Paid to NAME — Note" or "Received from NAME"
    if (raw) {
      const pToMatch = raw.match(/^(?:Paid\s+to|Paid\s+for|Received\s+from|Transfer\s+to|Transfer\s+from)\s+([^—–\[/\n]+)/i);
      if (pToMatch && pToMatch[1].trim().length > 1) {
        const cleaned = this.formatName(pToMatch[1].trim());
        if (cleaned && !this.isGenericWord(cleaned)) return cleaned;
      }

      // 2. UPI standard slash pattern: UPI/DR/<ref>/<NAME>/<BANK>/<VPA>/... or UPI/<NAME>/...
      if (/UPI\s*\//i.test(raw)) {
        const parts = raw.split('/').map(p => p.trim()).filter(Boolean);
        const upiIdx = parts.findIndex(p => /UPI/i.test(p));
        let payee = '';
        if (upiIdx !== -1) {
          if (parts.length > upiIdx + 3 && (/^DR$/i.test(parts[upiIdx + 1]) || /^CR$/i.test(parts[upiIdx + 1]))) {
            payee = parts[upiIdx + 3];
          } else if (parts.length > upiIdx + 1) {
            payee = parts[upiIdx + 1];
          }
        }
        if (payee) {
          const cleaned = this.formatName(payee);
          if (cleaned && !this.isGenericWord(cleaned)) return cleaned;
        }
      }

      // 3. IMPS / NEFT / ACH payee pattern: IMPS*...*<NAME> or IMPS/.../<NAME>
      const impsMatch = raw.match(/(?:IMPS|NEFT)[\/*\s:-]+[0-9]*[\/*\s:-]*([a-zA-Z\s]{3,30})/i);
      if (impsMatch && impsMatch[1].trim().length > 2) {
        const cleaned = this.formatName(impsMatch[1].trim());
        if (cleaned && !this.isGenericWord(cleaned)) return cleaned;
      }
    }

    // 4. Fallback to clean description if available and not generic
    if (desc && !this.isGenericWord(desc)) {
      // Remove trailing notes like "(Received)", "(Dinner)", etc. if we just want the person's name
      const withoutNotes = desc.replace(/\s*\([^)]*\)$/, '').trim();
      const cleaned = this.formatName(withoutNotes || desc);
      if (cleaned && !this.isGenericWord(cleaned)) return cleaned;
    }

    // 5. If UPI ID is present, format name from username part (e.g. rahul.sharma@okaxis -> Rahul Sharma)
    const upi = this.extractUpiId(txn.identifier) || this.extractUpiId(raw) || this.extractUpiId(desc);
    if (upi) {
      const handle = upi.split('@')[0].replace(/[0-9._-]+/g, ' ').trim();
      if (handle.length >= 2) {
        return this.formatName(handle);
      }
      return upi;
    }

    // 6. Last resort
    return desc || 'Friend';
  }

  formatName(str) {
    if (!str) return '';
    return str
      .replace(/\b\d{8,}\s+AT\s+\d+.*$/i, '')
      .replace(/\bAT\s+\d+.*$/i, '')
      .replace(/\b(dr|cr|upi|imps|neft|payment|paid|received|refund|transfer|p2p|p2a)\b/gi, '')
      .replace(/[_\-]+/g, ' ')
      .trim()
      .split(/\s+/)
      .filter(w => w.length > 0)
      .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .join(' ')
      .slice(0, 35);
  }

  isGenericWord(str) {
    const s = (str || '').toLowerCase().trim();
    const generic = [
      'transaction', 'expense', 'income', 'transfer', 'friend', 'friend transaction',
      'uncategorized', 'unknown', 'manual entry', 'payment', 'upi payment', 'upi transfer'
    ];
    return generic.includes(s) || s.length < 2;
  }

  /**
   * Determine deterministic unique key for a friend profile
   */
  getFriendKey(txn) {
    if (!txn) return 'friend_unknown';

    // Explicit friendId override
    if (txn.friendId) return txn.friendId;

    // 1. UPI ID if present
    const upi = this.extractUpiId(txn.identifier) || this.extractUpiId(txn.rawNarration) || this.extractUpiId(txn.description);
    if (upi) {
      return `upi:${upi}`;
    }

    // 2. Account info / Last 4 if clearly dedicated
    const acc = txn.accountInfo || '';
    const accMatch = acc.match(/[Xx*•]{2,}(\d{2,4})/);
    if (accMatch) {
      return `acc:${accMatch[1]}`;
    }

    // 3. Normalized Name key
    const name = this.extractFriendName(txn);
    const slug = name.toLowerCase().replace(/[^a-z0-9]/g, '_').replace(/_+/g, '_').slice(0, 40);
    return `name:${slug || 'friend'}`;
  }

  /**
   * Process transactions and compute friend profiles with incoming/outgoing metrics
   */
  getFriendProfiles(transactions = []) {
    const active = window.profileManager?.getActiveProfile();
    const primaryCurrency = (active && active.currency) || 'INR';
    const customProfiles = this.getCustomProfiles();

    // Filter transactions whose category is 'Friend' (case-insensitive)
    const friendTxns = (transactions || []).filter(t => 
      t &&
      t.duplicateStatus !== 'merged' &&
      t.category &&
      t.category.trim().toLowerCase() === 'friend'
    );

    const groups = new Map();

    for (const t of friendTxns) {
      const key = this.getFriendKey(t);
      if (!groups.has(key)) {
        groups.set(key, []);
      }
      groups.get(key).push(t);
    }

    const profiles = [];

    for (const [key, txns] of groups.entries()) {
      // Sort newest first
      const sorted = [...txns].sort((a, b) => {
        if (window.DateUtil?.compareTxnDesc) {
          return window.DateUtil.compareTxnDesc(a, b);
        }
        return String(b.date || '').localeCompare(String(a.date || ''));
      });

      const sampleTxn = sorted[0];
      const extractedName = this.extractFriendName(sampleTxn);
      const upiId = this.extractUpiId(sampleTxn.identifier) || this.extractUpiId(sampleTxn.rawNarration) || this.extractUpiId(sampleTxn.description);
      
      const custom = customProfiles[key];
      const displayName = (custom && custom.name) ? custom.name : extractedName;

      let totalOutgoing = 0; // You paid / sent to friend
      let totalIncoming = 0; // Friend paid / sent to you

      for (const t of sorted) {
        const rawAmt = Math.abs(parseFloat(t.amount) || 0);
        const amt = window.CurrencyEngine
          ? window.CurrencyEngine.convert(rawAmt, t.currency || primaryCurrency, primaryCurrency)
          : rawAmt;

        const isIncoming = t.type === 'income' || t.type === 'refund' ||
          (t.type === 'transfer' && (t.explicitType === 'income' || /\b(cr|credit|received|deposit)\b/i.test(t.rawNarration || '')));

        if (isIncoming) {
          totalIncoming += amt;
        } else {
          totalOutgoing += amt;
        }
      }

      // Net Balance = Outgoing (what they owe you) - Incoming (what you owe them)
      // Positive = Friend owes you (You get)
      // Negative = You owe friend (You owe)
      // Zero = Settled up
      const netBalance = totalOutgoing - totalIncoming;

      let status = 'settled';
      let statusText = 'Settled up';
      if (netBalance > 0.009) {
        status = 'owes_you';
        statusText = 'You get';
      } else if (netBalance < -0.009) {
        status = 'you_owe';
        statusText = 'You owe';
      }

      // Generate stable avatar gradient
      const initials = this.getInitials(displayName);
      const gradient = this.getGradientForString(key);

      profiles.push({
        id: key,
        displayName: displayName,
        extractedName: extractedName,
        isCustomName: Boolean(custom && custom.name),
        upiId: upiId || (key.startsWith('upi:') ? key.replace('upi:', '') : ''),
        initials: initials,
        gradient: gradient,
        totalOutgoing: totalOutgoing,
        totalIncoming: totalIncoming,
        netBalance: netBalance,
        status: status,
        statusText: statusText,
        currency: primaryCurrency,
        transactionCount: sorted.length,
        lastTransactionDate: sorted[0]?.date || '',
        transactions: sorted
      });
    }

    // Sort profiles: Unsettled balances first (by largest absolute net balance), then settled
    profiles.sort((a, b) => {
      const unsettledA = Math.abs(a.netBalance) > 0.01 ? 1 : 0;
      const unsettledB = Math.abs(b.netBalance) > 0.01 ? 1 : 0;
      if (unsettledA !== unsettledB) return unsettledB - unsettledA;
      if (Math.abs(b.netBalance) !== Math.abs(a.netBalance)) {
        return Math.abs(b.netBalance) - Math.abs(a.netBalance);
      }
      return String(b.lastTransactionDate).localeCompare(String(a.lastTransactionDate));
    });

    return profiles;
  }

  getInitials(name) {
    if (!name) return 'F';
    const parts = name.trim().split(/\s+/).filter(Boolean);
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  getGradientForString(str) {
    const gradients = [
      'linear-gradient(135deg, #a855f7 0%, #6366f1 100%)',
      'linear-gradient(135deg, #3b82f6 0%, #06b6d4 100%)',
      'linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%)',
      'linear-gradient(135deg, #10b981 0%, #059669 100%)',
      'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
      'linear-gradient(135deg, #06b6d4 0%, #3b82f6 100%)'
    ];
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = (hash << 5) - hash + str.charCodeAt(i);
      hash |= 0;
    }
    const idx = Math.abs(hash) % gradients.length;
    return gradients[idx];
  }
}

// Attach globally
if (typeof window !== 'undefined') {
  window.friendsManager = new FriendsManager();
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = FriendsManager;
}
