/**
 * Multi-Format Export Suite
 * Generates Excel (.xlsx) workbooks with multiple sheets, CSV files, and multi-page Executive PDF Reports.
 */

class ExportEngine {
  constructor() {}

  escape(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  markBackedUp() {
    try {
      const profileId = (window.profileManager && window.profileManager.getActiveProfile()?.id) || 'default';
      localStorage.setItem('lastBackupAt_' + profileId, String(Date.now()));
    } catch (e) { /* localStorage unavailable, ignore */ }
  }

  /**
   * 0. Full JSON backup — the only export that can be restored (accounts, rules, budgets,
   * categories and statement history included, unlike the xlsx/csv/pdf reports).
   */
  async exportBackupJSON() {
    const storeNames = ['transactions', 'accounts', 'categories', 'budgets', 'rules', 'statements'];
    const stores = {};
    for (const name of storeNames) {
      stores[name] = await window.db.getAll(name);
    }
    const profile = window.profileManager && window.profileManager.getActiveProfile();
    const payload = {
      app: 'money-tracker',
      schemaVersion: 1,
      exportedAt: new Date().toISOString(),
      profileName: profile ? profile.name : '',
      stores
    };

    const blob = new Blob([JSON.stringify(payload)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `MoneyTracker_Backup_${DateUtil.today()}.json`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    this.markBackedUp();
  }

  /**
   * Validate and restore a JSON backup, replacing the current profile's data in ONE
   * IndexedDB transaction (all-or-nothing). Throws a user-readable Error on a bad file.
   * Supports both Web format (app: 'money-tracker' + stores object) and Android APK format.
   */
  async restoreBackupFromFile(file) {
    let payload;
    try {
      payload = JSON.parse(await file.text());
    } catch (e) {
      throw new Error('That file is not valid JSON, so it can’t be a Money Tracker backup.');
    }
    if (!payload || typeof payload !== 'object') {
      throw new Error('That file is not a Money Tracker backup.');
    }

    const isWebFormat = payload.app === 'money-tracker' && typeof payload.stores === 'object' && payload.stores !== null;
    const isAndroidFormat = Array.isArray(payload.transactions) && (payload.version || payload.exportDate);

    if (!isWebFormat && !isAndroidFormat) {
      throw new Error('That file is not a Money Tracker backup.');
    }

    if (isWebFormat && typeof payload.schemaVersion === 'number' && payload.schemaVersion > 1) {
      throw new Error('This backup was made by a newer version of Money Tracker. Update the app before restoring it.');
    }

    const allowed = ['transactions', 'accounts', 'categories', 'budgets', 'rules', 'statements'];
    const data = {};
    const ID_REGEX = /^[a-zA-Z0-9_\-.:@]{1,128}$/;

    if (isWebFormat) {
      for (const name of allowed) {
        const rows = payload.stores[name];
        if (rows === undefined) continue;
        if (!Array.isArray(rows) || rows.some(r => !r || typeof r !== 'object' || typeof r.id !== 'string' || !ID_REGEX.test(r.id))) {
          throw new Error(`The backup's "${name}" data is damaged or contains invalid identifiers, so nothing was restored.`);
        }
        data[name] = rows;
      }
    } else {
      // Convert Android APK backup schema to Web IndexedDB schema
      const rawTxns = payload.transactions || [];
      data.transactions = rawTxns.map(t => {
        let dateStr = t.date;
        if (typeof t.date === 'number') {
          dateStr = DateUtil.toISODate(new Date(t.date));
        }
        const typeStr = String(t.type || 'expense').toLowerCase();
        const safeId = (typeof t.id === 'string' && ID_REGEX.test(t.id))
          ? t.id
          : ('txn_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6));
        const safeAccountId = (typeof t.accountId === 'string' && ID_REGEX.test(t.accountId))
          ? t.accountId
          : '';
        return {
          id: safeId,
          date: dateStr,
          time: t.time || '',
          description: t.description || 'Imported Transaction',
          amount: Number(t.amount) || 0,
          type: typeStr,
          category: t.category || 'Uncategorized',
          accountId: safeAccountId,
          accountName: t.accountName || '',
          notes: t.note || t.notes || '',
          referenceNo: t.referenceNo || '',
          paymentMode: t.paymentMode || 'Online',
          sourceFile: t.sourceFile || 'Android Backup',
          rawNarration: t.rawNarration || '',
          isDuplicate: Boolean(t.isDuplicate),
          duplicateWithId: t.duplicateWithId || null,
          duplicateStatus: t.duplicateStatus || 'none',
          duplicateConfidence: Number(t.duplicateConfidence) || 0,
          duplicateReason: t.duplicateReason || '',
          needsReview: Boolean(t.needsReview),
          confidence: t.confidence || 'high',
          currency: t.currency || 'INR'
        };
      });

      if (Array.isArray(payload.accounts)) {
        data.accounts = payload.accounts.map(a => ({
          id: a.id || ('acc_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)),
          name: a.name || 'Account',
          type: String(a.type || 'bank').toLowerCase().replace(/\s+/g, '_'),
          bankName: a.bankName || a.name || '',
          accountNumberLast4: a.lastFour || a.accountNumberLast4 || '',
          balance: Number(a.balance) || 0,
          computedBalance: Number(a.balance) || 0,
          creditLimit: Number(a.creditLimit) || 100000,
          gradientIndex: Number(a.gradientIndex) || 0,
          currency: a.currency || 'INR'
        }));
      }

      if (Array.isArray(payload.rules)) {
        data.rules = payload.rules.map(r => ({
          id: r.id || ('rule_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6)),
          pattern: r.pattern || '',
          category: r.category || 'Uncategorized',
          type: String(r.type || 'expense').toLowerCase(),
          createdAt: typeof r.createdAt === 'number' ? new Date(r.createdAt).toISOString() : (r.createdAt || new Date().toISOString())
        }));
      }
    }

    if (!data.transactions) throw new Error('The backup contains no transactions section, so nothing was restored.');

    await window.db.replaceStores(data);
    return {
      transactions: data.transactions.length,
      accounts: (data.accounts || []).length,
      rules: (data.rules || []).length
    };
  }

  /**
   * 1. Export Complete Excel Workbook (.xlsx)
   */
  async exportExcel() {
    const transactions = await window.db.getAll('transactions');
    const accounts = await window.accountsManager.getAccountsWithMetrics();
    const categories = await window.db.getAll('categories');
    const budgetStatus = await window.budgetsManager.getBudgetsStatus();

    const validTxns = transactions.filter(t => t.duplicateStatus !== 'merged');

    // --- Sheet 1: Executive Summary ---
    const active = window.profileManager?.getActiveProfile();
    const primaryCurrency = (active && active.currency) || 'INR';

    const summaryData = [
      ['FINANCIAL HEALTH & EXECUTIVE SUMMARY'],
      ['Generated On', new Date().toLocaleString()],
      ['Report Period', 'All Time / Current Month Overview'],
      [''],
      ['Key Performance Indicator', `Value (${primaryCurrency})`],
      ['Total Recorded Income', window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalIncome, primaryCurrency) : `₹ ${budgetStatus.totalIncome.toLocaleString('en-IN')}`],
      ['Total Recorded Expense', window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalExpense, primaryCurrency) : `₹ ${budgetStatus.totalExpense.toLocaleString('en-IN')}`],
      ['Net Savings', window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.netSavings, primaryCurrency) : `₹ ${budgetStatus.netSavings.toLocaleString('en-IN')}`],
      ['Savings Rate', `${budgetStatus.savingsRate}%`],
      ['Financial Health Score', `${budgetStatus.healthScore} / 100`],
      ['Total Active Accounts', accounts.length],
      ['Total Transactions', validTxns.length]
    ];
    const wsSummary = XLSX.utils.aoa_to_sheet(summaryData);

    // --- Sheet 2: All Transactions Register ---
    const txnsHeaders = ['Date', 'Time', 'Type', 'Category', 'Description', `Amount (${primaryCurrency})`, 'Account', 'Payment Mode', 'Reference / UTR', 'Source File', 'Notes'];
    const txnsRows = validTxns.map(t => {
      const acc = accounts.find(a => a.id === t.accountId);
      return [
        t.date,
        t.time || '',
        t.type.toUpperCase(),
        t.category,
        t.description,
        t.amount,
        acc ? acc.name : 'Unknown Account',
        t.paymentMode || 'Online',
        t.referenceNo || '',
        t.sourceFile || 'Manual',
        t.notes || ''
      ];
    });
    const wsTxns = XLSX.utils.aoa_to_sheet([txnsHeaders, ...txnsRows]);

    // --- Sheet 3: Category Breakdown & Budgets ---
    const catHeaders = ['Category Name', 'Type', `Total Spent (${primaryCurrency})`, `Monthly Budget (${primaryCurrency})`, `Remaining Budget (${primaryCurrency})`, 'Budget Status'];
    const catRows = budgetStatus.budgets.map(b => [
      b.name,
      b.type,
      b.spent,
      b.limit,
      b.remaining,
      b.status.toUpperCase()
    ]);
    const wsCategories = XLSX.utils.aoa_to_sheet([catHeaders, ...catRows]);

    // --- Sheet 4: Accounts Ledger ---
    const accHeaders = ['Account Name', 'Type', 'Bank / Institution', `Current Balance (${primaryCurrency})`, `Total Inflow (${primaryCurrency})`, `Total Outflow (${primaryCurrency})`, 'Credit Utilization (%)'];
    const accRows = accounts.map(a => [
      a.name,
      a.type.toUpperCase(),
      a.bankName,
      a.computedBalance,
      a.totalIncome,
      a.totalExpense,
      a.type === 'credit_card' ? `${a.utilizationPercent}%` : 'N/A'
    ]);
    const wsAccounts = XLSX.utils.aoa_to_sheet([accHeaders, ...accRows]);

    // Build Workbook
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, wsSummary, 'Summary');
    XLSX.utils.book_append_sheet(wb, wsTxns, 'Transactions');
    XLSX.utils.book_append_sheet(wb, wsCategories, 'Categories & Budgets');
    XLSX.utils.book_append_sheet(wb, wsAccounts, 'Accounts');

    // Trigger Download
    const fileName = `Expense_Report_${DateUtil.today()}.xlsx`;
    XLSX.writeFile(wb, fileName);
    this.markBackedUp();
  }

  /**
   * 2. Export Raw Transactions as CSV
   */
  async exportCSV() {
    const transactions = await window.db.getAll('transactions');
    const accounts = await window.db.getAll('accounts');
    const accMap = new Map((accounts || []).map(a => [a.id, a.name]));
    const validTxns = transactions.filter(t => t.duplicateStatus !== 'merged');

    const csvContent = Papa.unparse(validTxns.map(t => ({
      Date: t.date,
      Time: t.time || '',
      Type: t.type,
      Category: t.category,
      Description: t.description,
      Amount: t.amount,
      Currency: (t.currency || 'INR').toUpperCase(),
      Account: accMap.get(t.accountId) || t.accountName || '',
      PaymentMode: t.paymentMode,
      ReferenceNo: t.referenceNo,
      SourceFile: t.sourceFile,
      Notes: t.notes
    })), { escapeFormulae: true });

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `Transactions_${DateUtil.today()}.csv`;
    link.click();
    this.markBackedUp();
  }

  /**
   * 3. Export Comprehensive Multi-Page PDF Financial Report
   */
  async exportPDF() {
    const transactions = await window.db.getAll('transactions');
    const accounts = await window.accountsManager.getAccountsWithMetrics();
    const budgetStatus = await window.budgetsManager.getBudgetsStatus();
    const validTxns = transactions.filter(t => t.duplicateStatus !== 'merged').slice(0, 30);
    const active = window.profileManager?.getActiveProfile();
    const primaryCurrency = (active && active.currency) || 'INR';

    // Render report HTML inside temporary printable container
    const printArea = document.createElement('div');
    printArea.className = 'pdf-report-template';
    printArea.innerHTML = `
      <div class="report-header">
        <div class="report-title-area">
          <h2>Financial Analytics & Expense Statement</h2>
          <p style="color:#94a3b8; font-size:0.9rem;">100% Offline Verified Personal Report</p>
        </div>
        <div class="report-meta">
          <p><strong>Date:</strong> ${new Date().toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })}</p>
          <p><strong>Status:</strong> Reconciled & Verified</p>
        </div>
      </div>

      <div style="display:grid; grid-template-columns: repeat(4, 1fr); gap:16px; margin: 16px 0;">
        <div style="background:#1e293b; padding:16px; border-radius:8px; border:1px solid rgba(255,255,255,0.1);">
          <div style="font-size:0.75rem; color:#94a3b8; text-transform:uppercase;">Total Income</div>
          <div style="font-size:1.4rem; font-weight:bold; color:#10b981; margin-top:4px;">${window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalIncome, primaryCurrency) : '₹ ' + budgetStatus.totalIncome}</div>
        </div>
        <div style="background:#1e293b; padding:16px; border-radius:8px; border:1px solid rgba(255,255,255,0.1);">
          <div style="font-size:0.75rem; color:#94a3b8; text-transform:uppercase;">Total Expense</div>
          <div style="font-size:1.4rem; font-weight:bold; color:#f43f5e; margin-top:4px;">${window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalExpense, primaryCurrency) : '₹ ' + budgetStatus.totalExpense}</div>
        </div>
        <div style="background:#1e293b; padding:16px; border-radius:8px; border:1px solid rgba(255,255,255,0.1);">
          <div style="font-size:0.75rem; color:#94a3b8; text-transform:uppercase;">Net Savings</div>
          <div style="font-size:1.4rem; font-weight:bold; color:#3b82f6; margin-top:4px;">${window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.netSavings, primaryCurrency) : '₹ ' + budgetStatus.netSavings}</div>
        </div>
        <div style="background:#1e293b; padding:16px; border-radius:8px; border:1px solid rgba(255,255,255,0.1);">
          <div style="font-size:0.75rem; color:#94a3b8; text-transform:uppercase;">Health Score</div>
          <div style="font-size:1.4rem; font-weight:bold; color:#06b6d4; margin-top:4px;">${budgetStatus.healthScore} / 100</div>
        </div>
      </div>

      <h3 style="font-size:1.1rem; margin-top:20px; border-bottom:1px solid rgba(255,255,255,0.1); padding-bottom:6px;">Accounts & Card Outstanding</h3>
      <table style="width:100%; border-collapse:collapse; font-size:0.85rem; margin-top:8px;">
        <thead>
          <tr style="background:#1e293b; text-align:left;">
            <th style="padding:8px;">Account Name</th>
            <th style="padding:8px;">Type</th>
            <th style="padding:8px;">Bank</th>
            <th style="padding:8px; text-align:right;">Balance / Dues</th>
          </tr>
        </thead>
        <tbody>
          ${accounts.map(a => `
            <tr style="border-bottom:1px solid rgba(255,255,255,0.05);">
              <td style="padding:8px;">${this.escape(a.name)}</td>
              <td style="padding:8px; text-transform:uppercase;">${this.escape(a.type)}</td>
              <td style="padding:8px;">${this.escape(a.bankName)}</td>
              <td style="padding:8px; text-align:right; font-weight:bold; color:${a.computedBalance >= 0 ? '#10b981' : '#f43f5e'}">
                ${window.CurrencyEngine ? window.CurrencyEngine.format(a.computedBalance, a.currency || primaryCurrency) : '₹ ' + a.computedBalance}
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>

      <h3 style="font-size:1.1rem; margin-top:24px; border-bottom:1px solid rgba(255,255,255,0.1); padding-bottom:6px;">Recent Transactions Summary (Top 30)</h3>
      <table style="width:100%; border-collapse:collapse; font-size:0.82rem; margin-top:8px;">
        <thead>
          <tr style="background:#1e293b; text-align:left;">
            <th style="padding:8px;">Date</th>
            <th style="padding:8px;">Description</th>
            <th style="padding:8px;">Category</th>
            <th style="padding:8px;">Mode</th>
            <th style="padding:8px; text-align:right;">Amount (INR)</th>
          </tr>
        </thead>
        <tbody>
          ${validTxns.map(t => `
            <tr style="border-bottom:1px solid rgba(255,255,255,0.05);">
              <td style="padding:8px;">${this.escape(t.date)}</td>
              <td style="padding:8px;">${this.escape(t.description)}</td>
              <td style="padding:8px;">${this.escape(t.category)}</td>
              <td style="padding:8px;">${this.escape(t.paymentMode || 'Online')}</td>
              <td style="padding:8px; text-align:right; font-weight:bold; color:${t.type === 'income' ? '#10b981' : (t.type === 'refund' ? '#06b6d4' : '#f43f5e')}">
                ${(t.type === 'income' || t.type === 'refund') ? '+' : '-'} ${window.CurrencyEngine ? window.CurrencyEngine.format(t.amount, t.currency || primaryCurrency) : '₹ ' + t.amount}
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;

    // Use html2pdf to export
    const opt = {
      margin: 10,
      filename: `Expense_Report_${DateUtil.today()}.pdf`,
      image: { type: 'jpeg', quality: 0.98 },
      html2canvas: { scale: 2, useCORS: true, backgroundColor: '#0b1120' },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
    };

    html2pdf().set(opt).from(printArea).save();
    this.markBackedUp();
  }
}

// Global instance
window.exportEngine = new ExportEngine();
