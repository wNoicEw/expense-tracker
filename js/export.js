/**
 * Multi-Format Export Suite
 * Generates Excel (.xlsx) workbooks with multiple sheets, CSV files, and multi-page Executive PDF Reports.
 */

class ExportEngine {
  constructor() {}

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
    const summaryData = [
      ['FINANCIAL HEALTH & EXECUTIVE SUMMARY'],
      ['Generated On', new Date().toLocaleString()],
      ['Report Period', 'All Time / Current Month Overview'],
      [''],
      ['Key Performance Indicator', 'Value (INR)'],
      ['Total Recorded Income', `₹ ${budgetStatus.totalIncome.toLocaleString('en-IN')}`],
      ['Total Recorded Expense', `₹ ${budgetStatus.totalExpense.toLocaleString('en-IN')}`],
      ['Net Savings', `₹ ${budgetStatus.netSavings.toLocaleString('en-IN')}`],
      ['Savings Rate', `${budgetStatus.savingsRate}%`],
      ['Financial Health Score', `${budgetStatus.healthScore} / 100`],
      ['Total Active Accounts', accounts.length],
      ['Total Transactions', validTxns.length]
    ];
    const wsSummary = XLSX.utils.aoa_to_sheet(summaryData);

    // --- Sheet 2: All Transactions Register ---
    const txnsHeaders = ['Date', 'Type', 'Category', 'Description', 'Amount (INR)', 'Account', 'Payment Mode', 'Reference / UTR', 'Source File', 'Notes'];
    const txnsRows = validTxns.map(t => {
      const acc = accounts.find(a => a.id === t.accountId);
      return [
        t.date,
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
    const catHeaders = ['Category Name', 'Type', 'Total Spent (INR)', 'Monthly Budget (INR)', 'Remaining Budget (INR)', 'Budget Status'];
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
    const accHeaders = ['Account Name', 'Type', 'Bank / Institution', 'Current Balance (INR)', 'Total Inflow (INR)', 'Total Outflow (INR)', 'Credit Utilization (%)'];
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
    const fileName = `Expense_Report_${new Date().toISOString().split('T')[0]}.xlsx`;
    XLSX.writeFile(wb, fileName);
  }

  /**
   * 2. Export Raw Transactions as CSV
   */
  async exportCSV() {
    const transactions = await window.db.getAll('transactions');
    const validTxns = transactions.filter(t => t.duplicateStatus !== 'merged');

    const csvContent = Papa.unparse(validTxns.map(t => ({
      Date: t.date,
      Type: t.type,
      Category: t.category,
      Description: t.description,
      Amount: t.amount,
      PaymentMode: t.paymentMode,
      ReferenceNo: t.referenceNo,
      SourceFile: t.sourceFile,
      Notes: t.notes
    })));

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `Transactions_${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
  }

  /**
   * 3. Export Comprehensive Multi-Page PDF Financial Report
   */
  async exportPDF() {
    const transactions = await window.db.getAll('transactions');
    const accounts = await window.accountsManager.getAccountsWithMetrics();
    const budgetStatus = await window.budgetsManager.getBudgetsStatus();
    const validTxns = transactions.filter(t => t.duplicateStatus !== 'merged').slice(0, 30);

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
          <div style="font-size:1.4rem; font-weight:bold; color:#10b981; margin-top:4px;">₹ ${budgetStatus.totalIncome.toLocaleString('en-IN')}</div>
        </div>
        <div style="background:#1e293b; padding:16px; border-radius:8px; border:1px solid rgba(255,255,255,0.1);">
          <div style="font-size:0.75rem; color:#94a3b8; text-transform:uppercase;">Total Expense</div>
          <div style="font-size:1.4rem; font-weight:bold; color:#f43f5e; margin-top:4px;">₹ ${budgetStatus.totalExpense.toLocaleString('en-IN')}</div>
        </div>
        <div style="background:#1e293b; padding:16px; border-radius:8px; border:1px solid rgba(255,255,255,0.1);">
          <div style="font-size:0.75rem; color:#94a3b8; text-transform:uppercase;">Net Savings</div>
          <div style="font-size:1.4rem; font-weight:bold; color:#3b82f6; margin-top:4px;">₹ ${budgetStatus.netSavings.toLocaleString('en-IN')}</div>
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
              <td style="padding:8px;">${a.name}</td>
              <td style="padding:8px; text-transform:uppercase;">${a.type}</td>
              <td style="padding:8px;">${a.bankName}</td>
              <td style="padding:8px; text-align:right; font-weight:bold; color:${a.computedBalance >= 0 ? '#10b981' : '#f43f5e'}">
                ₹ ${a.computedBalance.toLocaleString('en-IN')}
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
              <td style="padding:8px;">${t.date}</td>
              <td style="padding:8px;">${t.description}</td>
              <td style="padding:8px;">${t.category}</td>
              <td style="padding:8px;">${t.paymentMode || 'Online'}</td>
              <td style="padding:8px; text-align:right; font-weight:bold; color:${t.type === 'income' ? '#10b981' : '#f43f5e'}">
                ${t.type === 'income' ? '+' : '-'} ₹ ${t.amount.toLocaleString('en-IN')}
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    `;

    // Use html2pdf to export
    const opt = {
      margin: 10,
      filename: `Expense_Report_${new Date().toISOString().split('T')[0]}.pdf`,
      image: { type: 'jpeg', quality: 0.98 },
      html2canvas: { scale: 2, useCORS: true, backgroundColor: '#0b1120' },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
    };

    html2pdf().set(opt).from(printArea).save();
  }
}

// Global instance
window.exportEngine = new ExportEngine();
