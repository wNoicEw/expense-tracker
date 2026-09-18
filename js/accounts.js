/**
 * Accounts & Credit Cards Intelligence Manager
 * Calculates live balances, credit card utilization, dues, and account-specific ledgers.
 * Supports dynamic auto-detection from statement metadata and full manual CRUD.
 */

class AccountsManager {
  constructor() {}

  /**
   * Get all accounts enriched with computed live balances and spend analytics
   */
  async getAccountsWithMetrics() {
    const accounts = await window.db.getAll('accounts');
    const transactions = await window.db.getAll('transactions');

    return accounts.map(acc => {
      // Filter transactions for this account (support direct accountId or smart fallback matching)
      const accTxns = transactions.filter(t => 
        t.duplicateStatus !== 'merged' && (
          (t.accountId && t.accountId === acc.id) ||
          (!t.accountId && t.accountName && t.accountName.toLowerCase() === acc.name.toLowerCase()) ||
          (!t.accountId && acc.bankName && t.accountName && t.accountName.toLowerCase().includes(acc.bankName.toLowerCase())) ||
          (!t.accountId && acc.bankName && t.rawNarration && t.rawNarration.toLowerCase().includes(acc.bankName.toLowerCase()))
        )
      );
      
      let totalIncome = 0;
      let totalExpense = 0;
      let totalTransfersOut = 0;
      let totalTransfersIn = 0;

      accTxns.forEach(t => {
        const amt = Math.abs(parseFloat(t.amount) || 0);
        if (t.type === 'income') totalIncome += amt;
        else if (t.type === 'expense') totalExpense += amt;
        else if (t.type === 'transfer') {
          if (t.explicitType === 'income' || (t.rawNarration && /\b(cr|credit|received|payment received|deposit)\b/i.test(t.rawNarration))) {
            totalTransfersIn += amt;
          } else {
            totalTransfersOut += amt;
          }
        }
      });

      // Calculate effective balance / dues
      let calculatedBalance = acc.balance || 0;
      let utilizationPercent = 0;
      let availableCredit = 0;
      let outstandingDues = 0;

      if (acc.type === 'credit_card') {
        const totalPaymentsReceived = totalIncome + totalTransfersIn;
        outstandingDues = Math.max(0, totalExpense - totalPaymentsReceived);
        const limit = acc.creditLimit || 100000;
        calculatedBalance = outstandingDues > 0 ? outstandingDues : totalExpense;
        availableCredit = Math.max(0, limit - outstandingDues);
        utilizationPercent = Math.min(100, Math.round((outstandingDues / limit) * 100));
      } else {
        calculatedBalance = (acc.balance || 0) + totalIncome - totalExpense - totalTransfersOut + totalTransfersIn;
      }

      return {
        ...acc,
        computedBalance: calculatedBalance,
        totalIncome,
        totalExpense,
        totalTransfersOut,
        totalTransfersIn,
        outstandingDues,
        transactionCount: accTxns.length,
        utilizationPercent,
        availableCredit
      };
    });
  }

  /**
   * Dynamic Account Resolver: Finds existing account matching statement or creates one on the fly
   */
  async getOrCreateAccountFromStatement(meta) {
    if (!meta) return 'acc_cash_default';

    const accounts = await window.db.getAll('accounts');
    
    // 1. Match by last 4 digits and matching type (and RuPay status if specified)
    let match = accounts.find(a => 
      a.type === meta.type && 
      a.accountNumberLast4 && 
      meta.last4 && 
      a.accountNumberLast4.toLowerCase() === meta.last4.toLowerCase() &&
      meta.last4 !== '0000' &&
      (!meta.isRuPay || a.isRuPay === meta.isRuPay)
    );

    // 2. Match by exact bank / app name and type. Two accounts (or cards) at one bank stay separate when both
    //    carry a real, different last-4; the loose fallback is only for accounts without a usable last-4.
    const specific = (v) => !!v && !['0000', 'UPI', 'CASH', 'RUPAY'].includes(String(v).toUpperCase());
    if (!match) {
      match = accounts.find(a => 
        a.bankName && 
        meta.bankName && 
        a.bankName.toLowerCase() === meta.bankName.toLowerCase() &&
        a.type === meta.type &&
        (!meta.isRuPay || a.isRuPay === meta.isRuPay) &&
        !(specific(meta.last4) && specific(a.accountNumberLast4) &&
          String(a.accountNumberLast4).toLowerCase() !== String(meta.last4).toLowerCase())
      );
    }

    // 3. Match by exact name
    if (!match) {
      match = accounts.find(a => a.name && meta.name && a.name.toLowerCase() === meta.name.toLowerCase());
    }

    if (match) {
      return match.id;
    }

    // Auto-create new Account from Statement metadata
    const newAccount = {
      id: 'acc_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6),
      name: meta.name || `${meta.bankName || 'Bank'} Account`,
      type: meta.type || 'bank',
      bankName: meta.bankName || 'Personal Account',
      accountNumberLast4: meta.last4 || '0000',
      balance: 0.00,
      creditLimit: meta.creditLimit || 100000.00,
      billingDay: meta.billingDay || 15,
      currency: '₹',
      color: meta.color || (meta.isRuPay ? '#0f766e' : '#1e3a8a'),
      isAutoDetected: true,
      isRuPay: meta.isRuPay || false,
      createdAt: new Date().toISOString()
    };

    await window.db.put('accounts', newAccount);
    return newAccount.id;
  }

  /**
   * Get single account by ID
   */
  async getAccount(accountId) {
    return await window.db.get('accounts', accountId);
  }

  /**
   * Create or update an account manually
   */
  async saveAccount(accountData) {
    if (!accountData.id) {
      accountData.id = 'acc_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6);
      accountData.createdAt = new Date().toISOString();
    } else {
      // Editing: keep fields the form doesn't own (createdAt, isAutoDetected, …)
      const existing = await window.db.getById('accounts', accountData.id);
      const defined = Object.fromEntries(Object.entries(accountData).filter(([, v]) => v !== undefined));
      accountData = { ...(existing || {}), ...defined };
      if (accountData.type !== 'credit_card') { delete accountData.creditLimit; delete accountData.billingDay; }
    }
    await window.db.put('accounts', accountData);
    return accountData;
  }

  /**
   * Delete account and reassign its transactions to another active account or cash default
   */
  async deleteAccount(accountId) {
    const accounts = await window.db.getAll('accounts');
    let fallback = accounts.find(a => a.id === 'acc_cash_default' && a.id !== accountId);
    if (!fallback) {
      fallback = {
        id: accountId === 'acc_cash_default' ? 'acc_cash_default_' + Date.now() : 'acc_cash_default',
        name: 'Unassigned',
        type: 'cash',
        bankName: 'Cash / Unassigned',
        accountNumberLast4: '0000',
        balance: 0,
        color: '#64748b',
        createdAt: new Date().toISOString()
      };
      await window.db.put('accounts', fallback);
    }

    const txns = await window.db.getAll('transactions');
    const affectedTxns = txns.filter(t => t.accountId === accountId).map(t => ({ ...t, accountId: fallback.id }));

    if (affectedTxns.length > 0) {
      await window.db.putBatch('transactions', affectedTxns);
    }
    await window.db.delete('accounts', accountId);
    return true;
  }
}

// Global instance
window.accountsManager = new AccountsManager();

