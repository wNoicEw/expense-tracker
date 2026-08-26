/**
 * Application Main Controller & UI Coordinator
 * Handles tab navigation, UI updates, user interactions, AI rule learning, modals, and toasts.
 */

class App {
  constructor() {
    this.currentTab = 'dashboard';
    this.currentDaysRange = 30;
    this.transactionsPage = 1;
    this.transactionsPerPage = 15;
    this.searchQuery = '';
    this.categoryFilter = 'all';
    this.accountFilter = 'all';
    this.typeFilter = 'all';
    this.chartViewMode = 'cumulative';
    this.theme = 'dark';
  }

  escape(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  async init() {
    try {
      // Initialize Theme Preference
      this.initTheme();

      // --- PROFILE GATE ---
      // If no active profile exists, show the profile chooser and halt the app boot.
      if (!window.profileManager.hasActiveProfile()) {
        this.showProfileChooser();
        if (window.lucide) lucide.createIcons();
        return; // Don't init DB or render anything until a profile is chosen.
      }

      // Profile is set — update the sidebar profile pill
      this.updateSidebarProfilePill();

      // Initialize IndexedDB (uses active profile's isolated namespace)
      await window.db.init();

      // Setup Event Listeners & UI
      this.bindNavigationEvents();
      this.bindDropzoneEvents();
      this.bindFilterEvents();
      this.bindModalEvents();
      this.bindAccountModalEvents();
      this.bindProfileModalEvents();

      // Initial Duplicate Scan
      await window.duplicateDetector.scanDatabase();

      // Initial Render
      await this.refreshAllViews();

      // Lucide icons initialization
      if (window.lucide) {
        lucide.createIcons();
      }

      this.showToast('App initialized offline. All data is securely stored on your device.', 'info');
    } catch (err) {
      console.error('App init error:', err);
      this.showToast('Error initializing application: ' + err.message, 'error');
    }
  }

  // --- THEME MANAGEMENT (Dark / Light Mode) ---
  initTheme() {
    const saved = localStorage.getItem('money_tracker_theme');
    // Default mode is strictly dark mode
    this.theme = saved || 'dark';
    this.applyTheme(this.theme, false);
  }

  toggleTheme() {
    this.theme = this.theme === 'dark' ? 'light' : 'dark';
    localStorage.setItem('money_tracker_theme', this.theme);
    this.applyTheme(this.theme, true);
  }

  applyTheme(theme, reRenderCharts = false) {
    document.documentElement.setAttribute('data-theme', theme);
    const icon = document.getElementById('themeToggleIcon');
    const label = document.getElementById('themeToggleLabel');

    if (icon && label) {
      if (theme === 'light') {
        icon.setAttribute('data-lucide', 'moon');
        label.textContent = 'Dark';
      } else {
        icon.setAttribute('data-lucide', 'sun');
        label.textContent = 'Light';
      }
    }

    if (window.lucide) {
      lucide.createIcons();
    }

    if (reRenderCharts) {
      this.refreshCurrentTab();
    }
  }

  // --- Navigation & Routing ---
  bindNavigationEvents() {
    const navButtons = document.querySelectorAll('.nav-item button');
    navButtons.forEach(btn => {
      btn.addEventListener('click', (e) => {
        const targetTab = btn.getAttribute('data-tab');
        this.switchTab(targetTab);
      });
    });

    // Mobile sidebar toggle
    const mobileBtn = document.getElementById('mobileMenuToggle');
    const sidebar = document.querySelector('.sidebar');
    if (mobileBtn && sidebar) {
      mobileBtn.addEventListener('click', () => {
        sidebar.classList.toggle('open');
      });
    }

    // Chart Range Selector buttons
    const rangeBtns = document.querySelectorAll('[data-range]');
    rangeBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        rangeBtns.forEach(b => b.classList.remove('btn-primary'));
        rangeBtns.forEach(b => b.classList.add('btn-secondary'));
        btn.classList.remove('btn-secondary');
        btn.classList.add('btn-primary');
        this.currentDaysRange = parseInt(btn.getAttribute('data-range')) || 30;
        this.renderDashboardCharts();
      });
    });
  }

  switchTab(tabId) {
    this.currentTab = tabId;

    // Update Sidebar Active state
    document.querySelectorAll('.nav-item').forEach(item => {
      const btn = item.querySelector('button');
      if (btn && btn.getAttribute('data-tab') === tabId) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    });

    // Update Panels
    document.querySelectorAll('.tab-panel').forEach(panel => {
      panel.classList.remove('active');
    });

    const activePanel = document.getElementById(`panel-${tabId}`);
    if (activePanel) {
      activePanel.classList.add('active');
    }

    // Close mobile menu if open
    const sidebar = document.querySelector('.sidebar');
    if (sidebar) sidebar.classList.remove('open');

    // Refresh view specific data
    this.refreshCurrentTab();

    if (window.lucide) {
      lucide.createIcons();
    }
  }

  async refreshAllViews() {
    await this.updateSidebarBadges();
    await this.refreshCurrentTab();
  }

  async refreshCurrentTab() {
    switch (this.currentTab) {
      case 'dashboard':
        await this.renderDashboard();
        break;
      case 'review':
        await this.renderReviewView();
        break;
      case 'transactions':
        await this.renderTransactionsTable();
        break;
      case 'duplicates':
        await this.renderDuplicatesView();
        break;
      case 'accounts':
        await this.renderAccountsView();
        break;
      case 'rules':
        await this.renderRulesView();
        break;
      case 'reports':
        await this.renderReportsView();
        break;
      case 'import':
        await this.renderImportView();
        break;
    }
    if (window.lucide) {
      lucide.createIcons();
    }
  }

  async updateSidebarBadges() {
    const txns = await window.db.getAll('transactions');
    
    // Duplicate Badge
    const pendingDups = txns.filter(t => t.isDuplicate && t.duplicateStatus === 'pending_review');
    const dupCount = Math.floor(pendingDups.length / 2);
    const dupBadge = document.getElementById('sidebarDupBadge');
    if (dupBadge) {
      if (dupCount > 0) {
        dupBadge.textContent = `${dupCount}`;
        dupBadge.style.display = 'inline-block';
      } else {
        dupBadge.style.display = 'none';
      }
    }

    // Needs Review Badge
    const pendingReview = txns.filter(t => t.needsReview || t.category === 'Uncategorized');
    const reviewCount = pendingReview.length;
    const reviewBadge = document.getElementById('sidebarReviewBadge');
    if (reviewBadge) {
      if (reviewCount > 0) {
        reviewBadge.textContent = `${reviewCount}`;
        reviewBadge.style.display = 'inline-block';
      } else {
        reviewBadge.style.display = 'none';
      }
    }

    // Header badge on review tab
    const reviewCountHeader = document.getElementById('reviewCountHeaderBadge');
    if (reviewCountHeader) {
      reviewCountHeader.textContent = `${reviewCount} Undetected`;
    }

    // Dashboard Banner
    const banner = document.getElementById('dashboardReviewBanner');
    const bannerTitle = document.getElementById('dashboardReviewBannerTitle');
    if (banner && bannerTitle) {
      if (reviewCount > 0) {
        banner.style.display = 'flex';
        bannerTitle.textContent = `${reviewCount} Undetected Expense${reviewCount > 1 ? 's' : ''} Need Classification`;
      } else {
        banner.style.display = 'none';
      }
    }
  }

  // --- DASHBOARD VIEW ---
  async renderDashboard() {
    const transactions = await window.db.getAll('transactions');
    const budgetStatus = await window.budgetsManager.getBudgetsStatus();
    const accounts = await window.accountsManager.getAccountsWithMetrics();

    let totalNetWorth = 0;
    accounts.forEach(a => {
      totalNetWorth += a.computedBalance;
    });

    const kpiBalance = document.getElementById('kpiTotalBalance');
    const kpiIncome = document.getElementById('kpiTotalIncome');
    const kpiExpense = document.getElementById('kpiTotalExpense');
    const kpiSavings = document.getElementById('kpiSavingsRate');

    if (kpiBalance) kpiBalance.textContent = '₹ ' + totalNetWorth.toLocaleString('en-IN', { maximumFractionDigits: 0 });
    if (kpiIncome) kpiIncome.textContent = '₹ ' + budgetStatus.totalIncome.toLocaleString('en-IN', { maximumFractionDigits: 0 });
    if (kpiExpense) kpiExpense.textContent = '₹ ' + budgetStatus.totalExpense.toLocaleString('en-IN', { maximumFractionDigits: 0 });
    if (kpiSavings) kpiSavings.textContent = `${budgetStatus.savingsRate}%`;

    // Hero Flow Bar & Labels
    const heroInflow = document.getElementById('heroInflowLabel');
    const heroOutflow = document.getElementById('heroOutflowLabel');
    const heroBarIn = document.getElementById('heroFlowBarIncome');
    const heroBarOut = document.getElementById('heroFlowBarExpense');

    if (heroInflow) heroInflow.textContent = 'Inflow: ₹ ' + budgetStatus.totalIncome.toLocaleString('en-IN');
    if (heroOutflow) heroOutflow.textContent = 'Outflow: ₹ ' + budgetStatus.totalExpense.toLocaleString('en-IN');

    const totalCashflow = budgetStatus.totalIncome + budgetStatus.totalExpense;
    if (heroBarIn && heroBarOut) {
      if (totalCashflow > 0) {
        const inPct = Math.max(10, Math.min(90, Math.round((budgetStatus.totalIncome / totalCashflow) * 100)));
        heroBarIn.style.width = `${inPct}%`;
        heroBarOut.style.width = `${100 - inPct}%`;
      } else {
        heroBarIn.style.width = '50%';
        heroBarOut.style.width = '50%';
      }
    }

    await this.renderDashboardCharts();

    // Render Recent Transactions
    const recentTxnsContainer = document.getElementById('dashboardRecentTxns');
    if (recentTxnsContainer) {
      const validTxns = transactions
        .filter(t => t.duplicateStatus !== 'merged')
        .sort((a, b) => new Date(b.date) - new Date(a.date))
        .slice(0, 5);

      if (validTxns.length === 0) {
        recentTxnsContainer.innerHTML = `<tr><td colspan="5" style="text-align:center; padding:24px; color:var(--text-dim);">No transactions recorded yet.</td></tr>`;
      } else {
        recentTxnsContainer.innerHTML = validTxns.map(t => {
          const acc = accounts.find(a => a.id === t.accountId);
          return `
            <tr>
              <td><span style="color:var(--text-muted); font-size:0.8rem;">${this.escape(t.date)}</span></td>
              <td>
                <div style="font-weight:600; color:var(--text-main); display:flex; align-items:center; gap:6px;">
                  ${this.escape(t.description)}
                  ${t.needsReview ? `<span class="badge-tag needs-review" onclick="app.switchTab('review')">Unclassified</span>` : ''}
                </div>
                <div style="font-size:0.75rem; color:var(--text-dim);">${this.escape(t.rawNarration || t.notes || '')}</div>
              </td>
              <td><span class="badge-category">${this.escape(t.category)}</span></td>
              <td><span class="badge-account">${this.escape(acc ? acc.name : 'Account')}</span></td>
              <td style="text-align:right;">
                <span class="amount-display ${t.type}">
                  ${t.type === 'income' ? '+' : '-'} ₹ ${Number(t.amount).toLocaleString('en-IN')}
                </span>
              </td>
            </tr>
          `;
        }).join('');
      }
    }

    // Render Mini Accounts Snapshot
    const accountsListContainer = document.getElementById('dashboardAccountsList');
    if (accountsListContainer) {
      if (accounts.length === 0) {
        accountsListContainer.innerHTML = `<div style="color:var(--text-dim); font-size:0.85rem; padding:12px 0; text-align:center;">No accounts connected yet.<br><span style="font-size:0.75rem; color:var(--text-muted);">Upload a statement or add an account.</span></div>`;
      } else {
        accountsListContainer.innerHTML = accounts.slice(0, 4).map(acc => {
          const isCard = acc.type === 'credit_card';
          return `
            <div class="dashboard-account-pill">
              <div style="display:flex; align-items:center; gap:10px;">
                <div style="width:32px; height:32px; border-radius:8px; background:${isCard ? 'rgba(244,63,94,0.15)' : 'rgba(59,130,246,0.15)'}; color:${isCard ? '#f43f5e' : '#3b82f6'}; display:flex; align-items:center; justify-content:center;">
                  <i data-lucide="${isCard ? 'credit-card' : 'landmark'}" style="width:16px; height:16px;"></i>
                </div>
                <div>
                  <div style="font-weight:600; font-size:0.86rem; color:var(--text-main);">${this.escape(acc.name)}</div>
                  <div style="font-size:0.72rem; color:var(--text-muted);">${this.escape(acc.bankName)} •••• ${this.escape(acc.accountNumberLast4 || '0000')}</div>
                </div>
              </div>
              <div style="text-align:right;">
                <div style="font-family:var(--font-mono); font-weight:700; font-size:0.95rem; color:${isCard ? '#f43f5e' : '#10b981'};">
                  ₹ ${Math.abs(acc.computedBalance || 0).toLocaleString('en-IN')}
                </div>
                <div style="font-size:0.7rem; color:var(--text-dim);">${isCard ? `${acc.utilizationPercent}% limit used` : 'Available'}</div>
              </div>
            </div>
          `;
        }).join('');
      }
    }
  }

  setChartMode(mode) {
    this.chartViewMode = mode;
    const btnUni = document.getElementById('btnChartModeUnified');
    const btnCum = document.getElementById('btnChartModeCumulative');

    if (btnUni && btnCum) {
      if (mode === 'unified') {
        btnUni.className = 'btn btn-primary btn-sm';
        btnCum.className = 'btn btn-ghost btn-sm';
      } else {
        btnUni.className = 'btn btn-ghost btn-sm';
        btnCum.className = 'btn btn-primary btn-sm';
      }
    }

    this.renderDashboardCharts();
  }

  async renderDashboardCharts() {
    const transactions = await window.db.getAll('transactions');
    const accounts = await window.accountsManager.getAccountsWithMetrics();

    window.chartsEngine.renderCashflowChart('chartCashflow', transactions, this.currentDaysRange, this.chartViewMode);

    const donutData = window.chartsEngine.renderCategoryDonutChart('chartCategoryDonut', transactions);
    const catList = document.getElementById('dashboardCategoryList');
    if (catList && donutData && donutData.sortedCats) {
      if (donutData.sortedCats.length === 0) {
        catList.innerHTML = `<div style="text-align:center; padding:24px 12px; color:var(--text-dim); font-size:0.85rem;">No expense categories recorded yet.</div>`;
      } else {
        catList.innerHTML = donutData.sortedCats.map(([cat, amt], idx) => {
          const color = donutData.colors[idx % donutData.colors.length];
          const percent = donutData.totalSpend > 0 ? ((amt / donutData.totalSpend) * 100).toFixed(1) : 0;
          return `
            <div class="cat-item">
              <div class="cat-info">
                <span class="cat-dot" style="background:${color};"></span>
                <span>${cat}</span>
              </div>
              <div style="display:flex; align-items:center; gap:8px;">
                <span style="font-size:0.75rem; color:#94a3b8;">${percent}%</span>
                <span class="cat-amount">₹ ${Number(amt).toLocaleString('en-IN')}</span>
              </div>
            </div>
          `;
        }).join('');
      }
    }
  }

  // --- NEEDS REVIEW (UNDETECTED EXPENSES) VIEW ---
  async renderReviewView() {
    const transactions = await window.db.getAll('transactions');
    const categories = (await window.db.getAll('categories')).filter(c => c.name !== 'Uncategorized');
    const accounts = await window.accountsManager.getAccountsWithMetrics();

    const pendingReview = transactions.filter(t => (t.needsReview || t.category === 'Uncategorized') && t.duplicateStatus !== 'merged');
    const container = document.getElementById('reviewTransactionsContainer');
    if (!container) return;

    if (pendingReview.length === 0) {
      container.innerHTML = `
        <div class="glass-card" style="text-align:center; padding:48px 24px;">
          <div style="width:56px; height:56px; border-radius:50%; background:rgba(16, 185, 129, 0.15); color:#10b981; display:flex; align-items:center; justify-content:center; margin:0 auto 16px;">
            <i data-lucide="check-check" style="width:28px; height:28px;"></i>
          </div>
          <h3 style="font-size:1.25rem; font-weight:700; margin-bottom:6px;">All Transactions Classified!</h3>
          <p style="color:#94a3b8; font-size:0.88rem; max-width:480px; margin:0 auto 20px;">
            There are no unclassified or ambiguous transactions. When you import new statements, any unknown merchants will appear here for you to train the AI.
          </p>
          <button class="btn btn-secondary btn-sm" onclick="app.switchTab('dashboard')">
            Back to Dashboard
          </button>
        </div>
      `;
      return;
    }

    container.innerHTML = pendingReview.map(t => {
      const acc = accounts.find(a => a.id === t.accountId);
      const identifier = t.identifier || window.categorizer.extractIdentifier(t.rawNarration) || t.description;

      return `
        <div class="undetected-card" id="reviewCard_${t.id}">
          <div class="undetected-header">
            <div>
              <div style="display:flex; align-items:center; gap:8px; margin-bottom:4px;">
                <span class="badge-tag needs-review">⚠️ Unclassified Record</span>
                <span style="font-size:0.8rem; color:var(--text-muted); font-family:var(--font-mono);">${this.escape(t.date)}</span>
                <span class="badge-account">${this.escape(acc ? acc.name : 'Account')}</span>
              </div>
              <div style="font-size:1.1rem; font-weight:700; color:var(--text-main);">${this.escape(t.description)}</div>
              <div style="font-size:0.8rem; color:var(--text-dim); font-family:var(--font-mono); margin-top:2px;">
                Raw Statement Narration: <strong>${this.escape(t.rawNarration || t.notes || 'N/A')}</strong>
              </div>
            </div>
            <div style="font-size:1.4rem; font-weight:700; font-family:var(--font-mono); color:#f59e0b;">
              ₹ ${Number(t.amount).toLocaleString('en-IN')}
            </div>
          </div>

          <div class="undetected-form-row">
            <div>
              <label class="form-label" style="font-size:0.75rem;">Detected Identifier / UPI ID Pattern</label>
              <input type="text" id="patternInput_${t.id}" class="input-control" value="${this.escape(identifier)}" placeholder="e.g. rajesh.organic@okaxis">
            </div>

            <div>
              <label class="form-label" style="font-size:0.75rem;">Assign Category</label>
              <select id="categorySelect_${t.id}" class="select-control">
                ${categories.map(c => `<option value="${c.name}">${c.name}</option>`).join('')}
              </select>
            </div>

            <div>
              <label class="form-label" style="font-size:0.75rem;">Type</label>
              <select id="typeSelect_${t.id}" class="select-control">
                <option value="expense" ${t.type === 'expense' ? 'selected' : ''}>Expense (Debit)</option>
                <option value="income" ${t.type === 'income' ? 'selected' : ''}>Income (Credit)</option>
                <option value="transfer" ${t.type === 'transfer' ? 'selected' : ''}>Transfer</option>
              </select>
            </div>

            <div style="align-self:flex-end;">
              <button class="btn btn-primary" onclick="app.resolveUndetectedSingle('${t.id}')">
                <i data-lucide="sparkles" style="width:14px; height:14px;"></i> Save &amp; Teach AI
              </button>
            </div>
          </div>

          <div style="margin-top:10px; font-size:0.78rem; color:var(--text-muted); display:flex; align-items:center; gap:6px;">
            <i data-lucide="info" style="width:12px; height:12px; color:#06b6d4;"></i>
            <span>Future transactions with this UPI ID or merchant keyword will be automatically classified as this category.</span>
          </div>
        </div>
      `;
    }).join('');
  }

  async resolveUndetectedSingle(txnId) {
    const patternInput = document.getElementById(`patternInput_${txnId}`);
    const categorySelect = document.getElementById(`categorySelect_${txnId}`);
    const typeSelect = document.getElementById(`typeSelect_${txnId}`);

    if (!categorySelect || !patternInput) return;

    const pattern = patternInput.value.trim();
    const category = categorySelect.value;
    const type = typeSelect ? typeSelect.value : 'expense';

    // 1. Update the current transaction
    const txn = await window.db.getById('transactions', txnId);
    if (txn) {
      txn.category = category;
      txn.type = type;
      txn.needsReview = false;
      txn.confidence = 'learned';
      txn.description = window.categorizer.cleanNarration(txn.rawNarration) || pattern;
      await window.db.put('transactions', txn);
    }

    // 2. Teach rule and retroactively update all matching records
    const { reclassifiedCount } = await window.categorizer.learnRuleAndReclassify(pattern, category, type);

    this.showToast(`AI learned rule for "${pattern}"! ${reclassifiedCount > 1 ? `Reclassified ${reclassifiedCount} transactions.` : 'Updated transaction.'}`, 'success');
    await this.refreshAllViews();
  }

  // --- LEARNED RULES VIEW ---
  async renderRulesView() {
    const rules = await window.db.getAll('rules');
    const container = document.getElementById('learnedRulesContainer');
    if (!container) return;

    if (rules.length === 0) {
      container.innerHTML = `
        <div class="glass-card" style="text-align:center; padding:40px 20px;">
          <div style="width:48px; height:48px; border-radius:50%; background:rgba(6, 182, 212, 0.15); color:#06b6d4; display:flex; align-items:center; justify-content:center; margin:0 auto 12px;">
            <i data-lucide="brain" style="width:24px; height:24px;"></i>
          </div>
          <h4 style="font-weight:700; margin-bottom:4px; color:var(--text-main);">No Custom Rules Learned Yet</h4>
          <p style="color:var(--text-muted); font-size:0.85rem; max-width:420px; margin:0 auto;">
            When you classify unclassified transactions in the "Needs Review" section, Money Tracker automatically records rules here.
          </p>
        </div>
      `;
      return;
    }

    container.innerHTML = `
      <div class="rules-grid">
        ${rules.map(r => `
          <div class="rule-chip">
            <div>
              <div style="font-weight:700; font-size:0.9rem; color:var(--text-main); font-family:var(--font-mono);">${this.escape(r.pattern)}</div>
              <div style="display:flex; gap:6px; margin-top:6px;">
                <span class="badge-category">${this.escape(r.category)}</span>
                <span class="badge-tag ${r.type}">${this.escape(r.type)}</span>
              </div>
            </div>
            <button class="btn btn-ghost btn-sm btn-icon-only" onclick="app.deleteRule('${r.id}')" title="Delete Rule">
              <i data-lucide="trash-2" style="width:14px; height:14px; color:#f43f5e;"></i>
            </button>
          </div>
        `).join('')}
      </div>
    `;
  }

  async deleteRule(ruleId) {
    if (confirm('Delete this auto-classification rule?')) {
      await window.db.delete('rules', ruleId);
      this.showToast('Rule deleted', 'info');
      await this.renderRulesView();
    }
  }

  // --- TRANSACTIONS TABLE VIEW ---
  bindFilterEvents() {
    const searchInput = document.getElementById('txnSearchInput');
    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        this.searchQuery = e.target.value.toLowerCase();
        this.transactionsPage = 1;
        this.renderTransactionsTable();
      });
    }

    const catSelect = document.getElementById('txnCategoryFilter');
    if (catSelect) {
      catSelect.addEventListener('change', (e) => {
        this.categoryFilter = e.target.value;
        this.transactionsPage = 1;
        this.renderTransactionsTable();
      });
    }

    const accSelect = document.getElementById('txnAccountFilter');
    if (accSelect) {
      accSelect.addEventListener('change', (e) => {
        this.accountFilter = e.target.value;
        this.transactionsPage = 1;
        this.renderTransactionsTable();
      });
    }

    const typeSelect = document.getElementById('txnTypeFilter');
    if (typeSelect) {
      typeSelect.addEventListener('change', (e) => {
        this.typeFilter = e.target.value;
        this.transactionsPage = 1;
        this.renderTransactionsTable();
      });
    }
  }

  async renderTransactionsTable() {
    const transactions = await window.db.getAll('transactions');
    const accounts = await window.accountsManager.getAccountsWithMetrics();
    const categories = await window.db.getAll('categories');

    // Populate Category & Account Filter dropdowns
    const catSelect = document.getElementById('txnCategoryFilter');
    if (catSelect && catSelect.options.length <= 1) {
      categories.forEach(c => {
        const opt = document.createElement('option');
        opt.value = c.name;
        opt.textContent = c.name;
        catSelect.appendChild(opt);
      });
    }

    const accSelect = document.getElementById('txnAccountFilter');
    if (accSelect) {
      const currentVal = this.accountFilter || 'all';
      accSelect.innerHTML = '<option value="all">All Accounts</option>' + accounts.map(a => `<option value="${a.id}">${this.escape(a.name)}</option>`).join('');
      accSelect.value = currentVal;
    }

    // Filter Logic
    let filtered = transactions.filter(t => {
      if (t.duplicateStatus === 'merged') return false;

      if (this.searchQuery) {
        const q = this.searchQuery;
        const matchText = (t.description + ' ' + t.rawNarration + ' ' + t.category + ' ' + (t.referenceNo || '') + ' ' + (t.notes || '')).toLowerCase();
        if (!matchText.includes(q)) return false;
      }

      if (this.categoryFilter !== 'all' && t.category !== this.categoryFilter) return false;
      if (this.accountFilter !== 'all' && t.accountId !== this.accountFilter) return false;

      if (this.typeFilter === 'needs_review') {
        if (!t.needsReview && t.category !== 'Uncategorized') return false;
      } else if (this.typeFilter !== 'all' && t.type !== this.typeFilter) {
        return false;
      }

      return true;
    });

    filtered.sort((a, b) => new Date(b.date) - new Date(a.date));

    const totalCount = filtered.length;
    const totalPages = Math.ceil(totalCount / this.transactionsPerPage) || 1;
    if (this.transactionsPage > totalPages) this.transactionsPage = totalPages;

    const startIdx = (this.transactionsPage - 1) * this.transactionsPerPage;
    const pagedTxns = filtered.slice(startIdx, startIdx + this.transactionsPerPage);

    const tbody = document.getElementById('transactionsTableBody');
    if (tbody) {
      if (pagedTxns.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding:32px; color:var(--text-muted);">No matching transactions found.</td></tr>`;
      } else {
        tbody.innerHTML = pagedTxns.map(t => {
          const acc = accounts.find(a => a.id === t.accountId);
          const isDup = t.isDuplicate && t.duplicateStatus === 'pending_review';
          const isReview = t.needsReview || t.category === 'Uncategorized';

          return `
            <tr class="${isDup ? 'duplicate-row' : ''}">
              <td><span style="font-family:var(--font-mono); font-size:0.8rem; color:var(--text-muted);">${this.escape(t.date)}</span></td>
              <td>
                <div style="font-weight:600; color:var(--text-main); display:flex; align-items:center; gap:6px;">
                  ${this.escape(t.description)}
                  ${isDup ? `<span class="badge-tag duplicate" title="Potential duplicate detected"><i data-lucide="alert-circle" style="width:12px; height:12px;"></i> Match</span>` : ''}
                  ${isReview ? `<span class="badge-tag needs-review" onclick="app.switchTab('review')">Unclassified</span>` : ''}
                </div>
                <div style="font-size:0.75rem; color:var(--text-dim); font-family:var(--font-mono);">${this.escape(t.referenceNo ? 'Ref: ' + t.referenceNo : (t.rawNarration || ''))}</div>
              </td>
              <td><span class="badge-category">${this.escape(t.category)}</span></td>
              <td><span class="badge-account">${this.escape(acc ? acc.name : 'Account')}</span></td>
              <td><span class="badge-tag ${t.type}">${this.escape(t.type)}</span></td>
              <td style="text-align:right;">
                <span class="amount-display ${t.type}">
                  ${t.type === 'income' ? '+' : '-'} ₹ ${Number(t.amount).toLocaleString('en-IN')}
                </span>
              </td>
              <td style="text-align:center;">
                <button class="btn btn-ghost btn-sm btn-icon-only" onclick="app.deleteTransaction('${t.id}')" title="Delete">
                  <i data-lucide="trash-2" style="width:14px; height:14px; color:#f43f5e;"></i>
                </button>
              </td>
            </tr>
          `;
        }).join('');
      }
    }

    const pageInfo = document.getElementById('txnPaginationInfo');
    if (pageInfo) {
      pageInfo.textContent = `Showing ${totalCount > 0 ? startIdx + 1 : 0}-${Math.min(startIdx + this.transactionsPerPage, totalCount)} of ${totalCount} transactions`;
    }

    const prevBtn = document.getElementById('btnPrevPage');
    const nextBtn = document.getElementById('btnNextPage');
    if (prevBtn) prevBtn.disabled = this.transactionsPage <= 1;
    if (nextBtn) nextBtn.disabled = this.transactionsPage >= totalPages;
  }

  nextPage() {
    this.transactionsPage++;
    this.renderTransactionsTable();
  }

  prevPage() {
    if (this.transactionsPage > 1) {
      this.transactionsPage--;
      this.renderTransactionsTable();
    }
  }

  async deleteTransaction(id) {
    if (confirm('Are you sure you want to delete this transaction?')) {
      await window.db.delete('transactions', id);
      this.showToast('Transaction deleted successfully', 'info');
      await this.refreshAllViews();
    }
  }

  // --- DUPLICATE RESOLVER VIEW ---
  async renderDuplicatesView() {
    const scanResult = await window.duplicateDetector.scanDatabase();
    const container = document.getElementById('duplicateResolverContainer');
    const accounts = await window.accountsManager.getAccountsWithMetrics();

    if (!container) return;

    if (!scanResult.pairs || scanResult.pairs.length === 0) {
      container.innerHTML = `
        <div class="glass-card" style="text-align:center; padding:48px 24px;">
          <div style="width:56px; height:56px; border-radius:50%; background:rgba(16, 185, 129, 0.15); color:#10b981; display:flex; align-items:center; justify-content:center; margin:0 auto 16px;">
            <i data-lucide="check-check" style="width:28px; height:28px;"></i>
          </div>
          <h3 style="font-size:1.25rem; font-weight:700; margin-bottom:6px; color:var(--text-main);">All Clear! No Cross-Statement Duplicates</h3>
          <p style="color:var(--text-muted); font-size:0.88rem; max-width:480px; margin:0 auto 20px;">
            Your bank statements and UPI transaction logs are fully reconciled with zero double-counting.
          </p>
          <button class="btn btn-secondary btn-sm" onclick="app.rescanDuplicates()">
            <i data-lucide="refresh-cw" style="width:14px; height:14px;"></i> Re-Scan All Records
          </button>
        </div>
      `;
      return;
    }

    container.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
        <div style="font-size:0.95rem; color:var(--text-main); font-weight:600;">
          Found <span style="color:#f59e0b;">${scanResult.pairs.length} potential duplicate pair(s)</span> across your statement files
        </div>
        <button class="btn btn-primary btn-sm" onclick="app.mergeAllDuplicates()">
          <i data-lucide="sparkles" style="width:14px; height:14px;"></i> Auto-Merge All (${scanResult.pairs.length})
        </button>
      </div>

      ${scanResult.pairs.map((pair, idx) => {
        const acc1 = accounts.find(a => a.id === pair.tx1.accountId);
        const acc2 = accounts.find(a => a.id === pair.tx2.accountId);

        return `
          <div class="duplicate-pair-card">
            <div class="duplicate-match-header">
              <div style="display:flex; align-items:center; gap:8px;">
                <span class="badge-tag duplicate">Match Confidence: ${pair.confidence}%</span>
                <span style="font-size:0.82rem; color:var(--text-muted);">${pair.reason}</span>
              </div>
              <div style="font-size:1.15rem; font-weight:700; font-family:var(--font-mono); color:#f59e0b;">
                ₹ ${Number(pair.tx1.amount).toLocaleString('en-IN')}
              </div>
            </div>

            <div class="duplicate-side-by-side">
              <div class="duplicate-source-box">
                <div class="duplicate-source-badge">
                  <i data-lucide="file-text" style="width:12px; height:12px; display:inline;"></i> Source 1: ${this.escape(pair.tx1.sourceFile || 'Statement 1')}
                </div>
                <div style="font-weight:600; font-size:0.95rem; color:var(--text-main);">${this.escape(pair.tx1.description)}</div>
                <div style="font-size:0.78rem; color:var(--text-muted); margin:4px 0;">${this.escape(pair.tx1.rawNarration || '')}</div>
                <div style="display:flex; gap:6px; margin-top:8px;">
                  <span class="badge-account">${this.escape(acc1 ? acc1.name : 'Account')}</span>
                  <span class="badge-category">${this.escape(pair.tx1.category)}</span>
                  <span style="font-size:0.75rem; color:var(--text-dim); font-family:var(--font-mono); align-self:center;">${this.escape(pair.tx1.date)}</span>
                </div>
              </div>

              <div class="duplicate-source-box">
                <div class="duplicate-source-badge">
                  <i data-lucide="file-text" style="width:12px; height:12px; display:inline;"></i> Source 2: ${this.escape(pair.tx2.sourceFile || 'Statement 2')}
                </div>
                <div style="font-weight:600; font-size:0.95rem; color:var(--text-main);">${this.escape(pair.tx2.description)}</div>
                <div style="font-size:0.78rem; color:var(--text-muted); margin:4px 0;">${this.escape(pair.tx2.rawNarration || '')}</div>
                <div style="display:flex; gap:6px; margin-top:8px;">
                  <span class="badge-account">${this.escape(acc2 ? acc2.name : 'Account')}</span>
                  <span class="badge-category">${this.escape(pair.tx2.category)}</span>
                  <span style="font-size:0.75rem; color:var(--text-dim); font-family:var(--font-mono); align-self:center;">${this.escape(pair.tx2.date)}</span>
                </div>
              </div>
            </div>

            <div style="display:flex; justify-content:flex-end; gap:8px; flex-wrap:wrap;">
              <button class="btn btn-ghost btn-sm" onclick="app.dismissDuplicate('${pair.tx1.id}', '${pair.tx2.id}')">
                Mark as Separate
              </button>
              <button class="btn btn-danger btn-sm" onclick="app.deleteDuplicateSingle('${pair.tx2.id}', '${pair.tx1.id}')">
                Keep Left Only
              </button>
              <button class="btn btn-primary btn-sm" onclick="app.mergeDuplicatePair('${pair.tx1.id}', '${pair.tx2.id}')">
                <i data-lucide="merge" style="width:14px; height:14px;"></i> Merge & Enrich
              </button>
            </div>
          </div>
        `;
      }).join('')}
    `;
  }

  async mergeDuplicatePair(id1, id2) {
    await window.duplicateDetector.mergeAndEnrich(id1, id2);
    this.showToast('Merged duplicate records into enriched transaction', 'success');
    await this.refreshAllViews();
  }

  async deleteDuplicateSingle(delId, keepId) {
    await window.duplicateDetector.deleteDuplicate(delId, keepId);
    this.showToast('Removed redundant transaction', 'info');
    await this.refreshAllViews();
  }

  async dismissDuplicate(id1, id2) {
    await window.duplicateDetector.markAsSeparate(id1, id2);
    this.showToast('Marked as separate distinct transactions', 'info');
    await this.refreshAllViews();
  }

  async mergeAllDuplicates() {
    const scanResult = await window.duplicateDetector.scanDatabase();
    for (const pair of scanResult.pairs) {
      await window.duplicateDetector.mergeAndEnrich(pair.tx1.id, pair.tx2.id);
    }
    this.showToast(`Successfully merged ${scanResult.pairs.length} duplicate pairs!`, 'success');
    await this.refreshAllViews();
  }

  async rescanDuplicates() {
    await window.duplicateDetector.scanDatabase();
    this.showToast('Duplicate re-scan completed', 'info');
    await this.refreshAllViews();
  }

  // --- ACCOUNTS & CARDS VIEW ---
  // --- ACCOUNTS & CARDS VIEW WITH INTEGRATED EXPANDABLE INTELLIGENCE ---
  async renderAccountsView() {
    const accounts = await window.accountsManager.getAccountsWithMetrics();
    const allTxns = await window.db.getAll('transactions');
    const container = document.getElementById('accountsGridContainer');
    if (!container) return;

    if (accounts.length === 0) {
      container.innerHTML = `
        <div style="grid-column: 1 / -1; text-align:center; padding: 48px 24px; background: var(--bg-card); border: 1px dashed var(--glass-border); border-radius: var(--radius-lg);">
          <div style="width:52px; height:52px; border-radius:50%; background:rgba(37,99,235,0.12); color:#2563eb; display:flex; align-items:center; justify-content:center; margin: 0 auto 16px;">
            <i data-lucide="credit-card" style="width:26px; height:26px;"></i>
          </div>
          <h4 style="font-size:1.15rem; font-weight:700; margin-bottom:8px; color:var(--text-main);">No Connected Accounts or Cards Yet</h4>
          <p style="color:var(--text-dim); font-size:0.88rem; max-width:480px; margin: 0 auto 20px;">
            Upload your bank statements, credit card PDFs, or UPI histories to auto-detect your accounts, or click below to add one manually.
          </p>
          <button class="btn btn-primary btn-sm" onclick="app.openAccountModal()">
            <i data-lucide="plus" style="width:16px; height:16px;"></i> Add Account / Card Manually
          </button>
        </div>
      `;
      if (window.lucide) lucide.createIcons();
      return;
    }

    const now = new Date();
    const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);

    const categoryColors = {
      'Food & Dining': '#f59e0b', 'Groceries & Mart': '#10b981', 'Shopping & E-Comm': '#ec4899',
      'Travel & Commute': '#06b6d4', 'Bills & Utilities': '#8b5cf6', 'Subscriptions & OTT': '#ef4444',
      'Health & Pharmacy': '#14b8a6', 'Investments & SIP': '#3b82f6', 'Rent & Housing': '#6366f1',
      'Miscellaneous': '#64748b', 'Uncategorized': '#94a3b8'
    };

    container.innerHTML = accounts.map(acc => {
      const isCard = acc.type === 'credit_card';
      const isRuPay = acc.isRuPay || (acc.name && /rupay/i.test(acc.name));
      const isWallet = acc.type === 'wallet';
      const isCash = acc.type === 'cash';

      let cardClass = 'bank';
      if (isCard) cardClass = isRuPay ? 'rupay' : 'visa';
      else if (isWallet) cardClass = 'wallet';
      else if (isCash) cardClass = 'cash';

      // Transactions for this account
      const accTxns = allTxns
        .filter(t => t.accountId === acc.id && t.duplicateStatus !== 'merged')
        .sort((a, b) => new Date(b.date) - new Date(a.date));

      const totalSpend = accTxns.filter(t => t.type === 'expense').reduce((s, t) => s + Math.abs(t.amount || 0), 0);
      const totalCredits = accTxns.filter(t => t.type === 'income' || t.type === 'transfer').reduce((s, t) => s + Math.abs(t.amount || 0), 0);

      // This Month Spend
      const thisMonthTxns = accTxns.filter(t => {
        const d = new Date(t.date);
        return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && t.type === 'expense';
      });
      const thisMonthSpend = thisMonthTxns.reduce((s, t) => s + Math.abs(t.amount || 0), 0);

      // Last Month Spend
      const lastMonthTxns = accTxns.filter(t => {
        const d = new Date(t.date);
        return d.getFullYear() === lastMonth.getFullYear() && d.getMonth() === lastMonth.getMonth() && t.type === 'expense';
      });
      const lastMonthSpend = lastMonthTxns.reduce((s, t) => s + Math.abs(t.amount || 0), 0);
      const monthChange = lastMonthSpend > 0 ? Math.round(((thisMonthSpend - lastMonthSpend) / lastMonthSpend) * 100) : 0;

      // Category breakdown
      const catMap = {};
      accTxns.filter(t => t.type === 'expense').forEach(t => {
        const c = t.category || 'Uncategorized';
        catMap[c] = (catMap[c] || 0) + Math.abs(t.amount || 0);
      });
      const sortedCats = Object.entries(catMap).sort((a, b) => b[1] - a[1]).slice(0, 4);
      const maxCatSpend = sortedCats.length > 0 ? sortedCats[0][1] : 1;

      // Recent 5 txns
      const recentTxns = accTxns.slice(0, 5);

      return `
        <div class="account-card-wrapper" id="accWrapper_${acc.id}">
          <!-- Physical Card Face -->
          <div class="credit-card-ui ${cardClass}" 
               style="${acc.color ? `background: linear-gradient(135deg, ${acc.color} 0%, rgba(15,23,42,0.95) 100%);` : ''}"
               onclick="app.toggleAccountExpand('${acc.id}')"
               title="Click to view full card details & transactions">
            
            <div class="card-top">
              <div>
                <div style="font-size:0.75rem; text-transform:uppercase; letter-spacing:0.05em; opacity:0.85;">${this.escape(acc.bankName || 'Account')}</div>
                <div style="font-size:1.1rem; font-weight:700; display:flex; align-items:center; gap:8px;">
                  <span>${this.escape(acc.name)}</span>
                  ${isRuPay ? `<span style="font-size:0.65rem; background:linear-gradient(135deg, #059669, #0d9488); color:#fff; padding:1px 7px; border-radius:4px; font-weight:700; text-transform:uppercase; letter-spacing:0.04em;">RuPay CC</span>` : (acc.isAutoDetected ? `<span style="font-size:0.65rem; background:rgba(255,255,255,0.22); padding:1px 6px; border-radius:4px; font-weight:600; text-transform:uppercase; letter-spacing:0.04em;">Auto</span>` : '')}
                </div>
              </div>
              <div style="display:flex; align-items:center; gap:8px;">
                <button class="btn-card-action" onclick="event.stopPropagation(); app.openAccountModal('${acc.id}')" title="Edit Account" style="background:rgba(255,255,255,0.18); border:none; color:#fff; border-radius:6px; width:28px; height:28px; display:flex; align-items:center; justify-content:center; cursor:pointer; transition:0.2s;">
                  <i data-lucide="edit-2" style="width:14px; height:14px;"></i>
                </button>
                <button class="btn-card-action" onclick="event.stopPropagation(); app.deleteAccount('${acc.id}')" title="Delete Account" style="background:rgba(239,68,68,0.3); border:none; color:#fff; border-radius:6px; width:28px; height:28px; display:flex; align-items:center; justify-content:center; cursor:pointer; transition:0.2s;">
                  <i data-lucide="trash-2" style="width:14px; height:14px;"></i>
                </button>
                <div class="card-chip"></div>
              </div>
            </div>

            <div class="card-number">
              •••• •••• •••• ${this.escape(acc.accountNumberLast4 || (isWallet ? 'UPI' : (isCash ? 'CASH' : '0000')))}
            </div>

            <div class="card-bottom">
              <div>
                <div class="card-holder">${isCard ? 'Total Current Outstanding' : 'Available Balance'}</div>
                <div style="font-size:1.45rem; font-weight:700; font-family:var(--font-mono); color:${isCard ? '#f87171' : '#fff'};">
                  ₹ ${Math.abs(acc.computedBalance || 0).toLocaleString('en-IN')}
                </div>
              </div>
              <div style="text-align:right;">
                <div class="card-holder">${isCard ? 'Billing Cycle' : 'Type'}</div>
                <div style="font-size:0.85rem; font-weight:600; text-transform:uppercase;">
                  ${isCard ? `${acc.billingDay || 15}th of month` : (acc.type || 'bank')}
                </div>
              </div>
            </div>

            ${isCard ? `
              <div class="card-limit-progress">
                <div class="card-limit-bar ${acc.utilizationPercent > 70 ? 'high-utilization' : (acc.utilizationPercent > 30 ? 'med-utilization' : '')}" 
                     style="width: ${acc.utilizationPercent}%;"></div>
              </div>
              <div style="display:flex; justify-content:space-between; font-size:0.72rem; margin-top:6px; opacity:0.85;">
                <span>Utilization: ${acc.utilizationPercent}%</span>
                <span>Limit: ₹ ${(acc.creditLimit || 100000).toLocaleString('en-IN')}</span>
              </div>
            ` : ''}

            <div class="card-expand-toggle">
              <span>Card Intelligence & Details</span>
              <i data-lucide="chevron-down" class="expand-chevron"></i>
            </div>
          </div>

          <!-- Integrated Expandable Intelligence Drawer -->
          <div class="card-inline-drawer">
            <!-- 4 Quick Stats -->
            <div class="card-drawer-kpis">
              <div class="card-drawer-kpi-item">
                <div class="card-drawer-kpi-label">${isCard ? 'Outstanding Dues' : 'Live Balance'}</div>
                <div class="card-drawer-kpi-val" style="color:${isCard ? '#f43f5e' : '#10b981'};">₹ ${Math.abs(acc.computedBalance || 0).toLocaleString('en-IN')}</div>
              </div>
              <div class="card-drawer-kpi-item">
                <div class="card-drawer-kpi-label">This Month's Spend</div>
                <div class="card-drawer-kpi-val">₹ ${thisMonthSpend.toLocaleString('en-IN')}</div>
                ${lastMonthSpend > 0 ? `<div style="font-size:0.68rem; color:${monthChange > 0 ? '#f43f5e' : '#10b981'}; margin-top:2px;">${monthChange > 0 ? '↑' : '↓'} ${Math.abs(monthChange)}% vs last mo</div>` : ''}
              </div>
              <div class="card-drawer-kpi-item">
                <div class="card-drawer-kpi-label">Total Spend</div>
                <div class="card-drawer-kpi-val">₹ ${totalSpend.toLocaleString('en-IN')}</div>
              </div>
              <div class="card-drawer-kpi-item">
                <div class="card-drawer-kpi-label">${isCard ? 'Available Credit' : 'Total Credits/Income'}</div>
                <div class="card-drawer-kpi-val" style="color:${isCard ? '#3b82f6' : '#10b981'};">₹ ${(isCard ? (acc.availableCredit || 0) : totalCredits).toLocaleString('en-IN')}</div>
              </div>
            </div>

            <!-- Spending by Category -->
            ${sortedCats.length > 0 ? `
              <div style="margin-bottom: 16px;">
                <div class="card-drawer-section-title">
                  <i data-lucide="pie-chart" style="width:13px; height:13px; color:#8b5cf6;"></i> Top Spending Categories
                </div>
                ${sortedCats.map(([cat, amt]) => {
                  const pct = totalSpend > 0 ? Math.round((amt / totalSpend) * 100) : 0;
                  const barW = Math.max(8, Math.round((amt / maxCatSpend) * 100));
                  const col = categoryColors[cat] || '#64748b';
                  return `
                    <div class="card-drawer-cat-item">
                      <div class="card-drawer-cat-header">
                        <span style="color:var(--text-main);">${this.escape(cat)}</span>
                        <span style="font-family:var(--font-mono); color:var(--text-muted);">₹${amt.toLocaleString('en-IN')} <span style="font-size:0.65rem; opacity:0.7;">(${pct}%)</span></span>
                      </div>
                      <div class="card-drawer-cat-bar">
                        <div style="height:100%; width:${barW}%; background:${col}; border-radius:3px;"></div>
                      </div>
                    </div>
                  `;
                }).join('')}
              </div>
            ` : ''}

            <!-- Recent Transactions List -->
            <div>
              <div class="card-drawer-section-title">
                <i data-lucide="receipt" style="width:13px; height:13px; color:#3b82f6;"></i> Recent Transactions (${accTxns.length})
              </div>
              ${recentTxns.length > 0 ? recentTxns.map(t => {
                const isExp = t.type === 'expense';
                const isTrf = t.type === 'transfer';
                const amtCol = isExp ? '#f43f5e' : (isTrf ? '#8b5cf6' : '#10b981');
                const amtSign = isExp ? '-' : '+';
                return `
                  <div class="card-drawer-txn-item">
                    <div style="flex:1; min-width:0; padding-right:8px;">
                      <div style="font-size:0.78rem; font-weight:600; color:var(--text-main); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
                        ${this.escape(t.description || t.rawNarration || 'Transaction')}
                      </div>
                      <div style="font-size:0.68rem; color:var(--text-dim);">${this.escape(t.date)} • ${this.escape(t.category || '')}</div>
                    </div>
                    <div style="font-family:var(--font-mono); font-weight:700; font-size:0.82rem; color:${amtCol}; white-space:nowrap;">
                      ${amtSign} ₹${Math.abs(t.amount || 0).toLocaleString('en-IN')}
                    </div>
                  </div>
                `;
              }).join('') : `
                <div style="font-size:0.78rem; color:var(--text-dim); padding:8px 0;">No transactions recorded on this instrument yet.</div>
              `}
            </div>

            <!-- Action Buttons -->
            <div class="card-drawer-actions">
              <button class="btn btn-primary btn-sm" style="flex:1; font-size:0.75rem; padding:6px 10px;" onclick="app.openManualTxnModalWithAccount('${acc.id}')">
                <i data-lucide="plus" style="width:13px; height:13px;"></i> Add Transaction
              </button>
              <button class="btn btn-secondary btn-sm" style="font-size:0.75rem; padding:6px 10px;" onclick="app.openAccountModal('${acc.id}')">
                <i data-lucide="edit-2" style="width:13px; height:13px;"></i> Edit
              </button>
              <button class="btn btn-ghost btn-sm" style="font-size:0.75rem; padding:6px 8px;" onclick="app.toggleAccountExpand('${acc.id}')" title="Collapse">
                <i data-lucide="chevron-up" style="width:14px; height:14px;"></i>
              </button>
            </div>
          </div>
        </div>
      `;
    }).join('');

    if (window.lucide) lucide.createIcons();
  }

  toggleAccountExpand(accountId) {
    const wrapper = document.getElementById(`accWrapper_${accountId}`);
    if (!wrapper) return;

    const isExpanded = wrapper.classList.contains('expanded');
    
    // Close other expanded cards
    document.querySelectorAll('.account-card-wrapper.expanded').forEach(el => {
      if (el !== wrapper) el.classList.remove('expanded');
    });

    wrapper.classList.toggle('expanded', !isExpanded);
    if (window.lucide) lucide.createIcons();
  }

  openManualTxnModalWithAccount(accountId) {
    this.openManualTxnModal();
    const accSelect = document.getElementById('mTxnAccount');
    if (accSelect) accSelect.value = accountId;
  }

  openAccountModal(accountId = null) {
    const modal = document.getElementById('accountModal');
    if (!modal) return;

    const title = document.getElementById('accountModalTitle');
    const idInput = document.getElementById('accFormId');
    const nameInput = document.getElementById('accFormName');
    const bankInput = document.getElementById('accFormBank');
    const typeSelect = document.getElementById('accFormType');
    const last4Input = document.getElementById('accFormLast4');
    const balanceInput = document.getElementById('accFormBalance');
    const limitInput = document.getElementById('accFormLimit');
    const billingDayInput = document.getElementById('accFormBillingDay');
    const colorSelect = document.getElementById('accFormColor');

    const limitGroup = document.getElementById('accCreditLimitGroup');
    const cardDetailsRow = document.getElementById('accCardDetailsRow');

    if (accountId) {
      window.accountsManager.getAccount(accountId).then(acc => {
        if (!acc) return;
        if (title) title.textContent = 'Edit Account / Card';
        if (idInput) idInput.value = acc.id;
        if (nameInput) nameInput.value = acc.name || '';
        if (bankInput) bankInput.value = acc.bankName || '';
        if (typeSelect) {
          if (acc.type === 'credit_card' && acc.isRuPay) typeSelect.value = 'rupay_credit_card';
          else typeSelect.value = acc.type || 'bank';
        }
        if (last4Input) last4Input.value = acc.accountNumberLast4 || '';
        if (balanceInput) balanceInput.value = acc.balance || 0;
        if (limitInput) limitInput.value = acc.creditLimit || 100000;
        if (billingDayInput) billingDayInput.value = acc.billingDay || 15;
        if (colorSelect) colorSelect.value = acc.color || (acc.isRuPay ? '#0f766e' : '#1e3a8a');

        const isCard = acc.type === 'credit_card';
        if (limitGroup) limitGroup.style.display = isCard ? 'block' : 'none';
        if (cardDetailsRow) cardDetailsRow.style.display = isCard ? 'flex' : 'none';

        modal.classList.add('active');
        if (window.lucide) lucide.createIcons();
      });
    } else {
      if (title) title.textContent = 'Add Bank Account or Card';
      if (idInput) idInput.value = '';
      if (nameInput) nameInput.value = '';
      if (bankInput) bankInput.value = '';
      if (typeSelect) typeSelect.value = 'bank';
      if (last4Input) last4Input.value = '';
      if (balanceInput) balanceInput.value = '0.00';
      if (limitInput) limitInput.value = '100000.00';
      if (billingDayInput) billingDayInput.value = '15';
      if (colorSelect) colorSelect.value = '#1e3a8a';

      if (limitGroup) limitGroup.style.display = 'none';
      if (cardDetailsRow) cardDetailsRow.style.display = 'none';

      modal.classList.add('active');
      if (window.lucide) lucide.createIcons();
    }
  }

  closeAccountModal() {
    const modal = document.getElementById('accountModal');
    if (modal) modal.classList.remove('active');
  }

  async deleteAccount(accountId) {
    if (confirm('Are you sure you want to delete this account? Any existing transactions will be preserved and reassigned.')) {
      await window.accountsManager.deleteAccount(accountId);
      this.showToast('Account deleted successfully', 'info');
      await this.refreshAllViews();
    }
  }

  bindAccountModalEvents() {
    const openBtn = document.getElementById('btnOpenAddAccountModal');
    if (openBtn) {
      openBtn.addEventListener('click', () => this.openAccountModal());
    }

    const closeBtn = document.getElementById('btnCloseAccountModal');
    const cancelBtn = document.getElementById('btnCancelAccountModal');
    if (closeBtn) closeBtn.addEventListener('click', () => this.closeAccountModal());
    if (cancelBtn) cancelBtn.addEventListener('click', () => this.closeAccountModal());

    const typeSelect = document.getElementById('accFormType');
    const limitGroup = document.getElementById('accCreditLimitGroup');
    const cardDetailsRow = document.getElementById('accCardDetailsRow');

    if (typeSelect) {
      typeSelect.addEventListener('change', (e) => {
        const isCard = e.target.value === 'credit_card' || e.target.value === 'rupay_credit_card';
        if (limitGroup) limitGroup.style.display = isCard ? 'block' : 'none';
        if (cardDetailsRow) cardDetailsRow.style.display = isCard ? 'flex' : 'none';
      });
    }

    const form = document.getElementById('accountForm');
    if (form) {
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('accFormId').value;
        const name = document.getElementById('accFormName').value.trim();
        const bankName = document.getElementById('accFormBank').value.trim();
        const rawType = document.getElementById('accFormType').value;
        const isRuPay = rawType === 'rupay_credit_card';
        const type = isRuPay ? 'credit_card' : rawType;
        const accountNumberLast4 = document.getElementById('accFormLast4').value.trim() || (type === 'wallet' ? 'UPI' : (type === 'cash' ? 'CASH' : '0000'));
        const balance = parseFloat(document.getElementById('accFormBalance').value) || 0;
        const creditLimit = parseFloat(document.getElementById('accFormLimit').value) || 100000;
        const billingDay = parseInt(document.getElementById('accFormBillingDay').value) || 15;
        const color = document.getElementById('accFormColor').value || (isRuPay ? '#0f766e' : '#1e3a8a');

        if (!name || !bankName) {
          alert('Please enter account name and bank name.');
          return;
        }

        const accountData = {
          id: id || undefined,
          name,
          bankName,
          type,
          isRuPay,
          accountNumberLast4,
          balance,
          creditLimit: type === 'credit_card' ? creditLimit : undefined,
          billingDay: type === 'credit_card' ? billingDay : undefined,
          color,
          currency: '₹'
        };

        await window.accountsManager.saveAccount(accountData);
        this.closeAccountModal();
        this.showToast(id ? 'Account updated successfully!' : 'New account added successfully!', 'success');
        await this.refreshAllViews();
      });
    }
  }

  // --- STATEMENT IMPORT & DROPZONE ---
  bindDropzoneEvents() {
    const dropzone = document.getElementById('statementDropzone');
    const fileInput = document.getElementById('statementFileInput');

    if (dropzone && fileInput) {
      dropzone.addEventListener('click', () => fileInput.click());

      dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.classList.add('drag-over');
      });

      dropzone.addEventListener('dragleave', () => {
        dropzone.classList.remove('drag-over');
      });

      dropzone.addEventListener('drop', async (e) => {
        e.preventDefault();
        dropzone.classList.remove('drag-over');
        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
          await this.handleFileUpload(e.dataTransfer.files[0]);
        }
      });

      fileInput.addEventListener('change', async (e) => {
        if (e.target.files && e.target.files.length > 0) {
          await this.handleFileUpload(e.target.files[0]);
        }
      });
    }
  }

  async handleFileUpload(file) {
    const statusBox = document.getElementById('uploadStatusBox');
    if (statusBox) {
      statusBox.style.display = 'block';
      statusBox.innerHTML = `
        <div style="display:flex; align-items:center; gap:12px;">
          <div class="spinner" style="width:20px; height:20px; border:2px solid #3b82f6; border-top-color:transparent; border-radius:50%; animation:spin 0.8s linear infinite;"></div>
          <span>Parsing <strong>${file.name}</strong> 100% offline in browser...</span>
        </div>
      `;
    }

    try {
      const parseResult = await window.statementParser.parseFile(file);

      // Save transactions to DB
      await window.db.putBatch('transactions', parseResult.transactions);

      // Record statement upload
      await window.db.put('statements', {
        id: 'stmt_' + Date.now(),
        fileName: parseResult.fileName,
        fileType: parseResult.fileType,
        parsedDate: new Date().toISOString(),
        transactionCount: parseResult.count,
        bankOrApp: parseResult.detectedProfile
      });

      // Run duplicate detection scan immediately
      const dupScan = await window.duplicateDetector.scanDatabase();

      // Count unclassified
      const unclassified = parseResult.transactions.filter(t => t.needsReview).length;

      if (statusBox) {
        statusBox.innerHTML = `
          <div style="color:#10b981; font-weight:600;">
            ✓ Successfully imported ${parseResult.count} transactions from ${parseResult.detectedProfile}!
            ${unclassified > 0 ? `<div style="color:#f59e0b; margin-top:4px; font-size:0.85rem;">⚡ ${unclassified} transaction(s) need your review to teach the AI classifier.</div>` : ''}
            ${dupScan.duplicatesFound > 0 ? `<div style="color:#f59e0b; margin-top:4px; font-size:0.85rem;">⚠️ Found ${dupScan.duplicatesFound} potential duplicate transaction(s).</div>` : ''}
          </div>
        `;
      }

      this.showToast(`Imported ${parseResult.count} transactions!`, 'success');
      await this.refreshAllViews();

      if (unclassified > 0) {
        setTimeout(() => this.switchTab('review'), 1000);
      } else if (dupScan.duplicatesFound > 0) {
        setTimeout(() => this.switchTab('duplicates'), 1000);
      }
    } catch (err) {
      console.error('File parsing error:', err);
      if (statusBox) {
        statusBox.innerHTML = `<div style="color:#f43f5e; font-weight:600;">✗ Failed to parse statement: ${err.message}</div>`;
      }
      this.showToast(err.message, 'error');
    }
  }

  async renderImportView() {
    const statements = await window.db.getAll('statements');
    const historyTable = document.getElementById('statementHistoryTableBody');
    if (historyTable) {
      if (statements.length === 0) {
        historyTable.innerHTML = `<tr><td colspan="4" style="text-align:center; padding:20px; color:#64748b;">No statements uploaded yet.</td></tr>`;
      } else {
        historyTable.innerHTML = statements.map(s => `
          <tr>
            <td><strong>${this.escape(s.fileName)}</strong></td>
            <td><span class="badge-account">${this.escape(s.bankOrApp || s.fileType)}</span></td>
            <td>${s.transactionCount}</td>
            <td><span style="color:#94a3b8; font-size:0.8rem;">${new Date(s.parsedDate).toLocaleString()}</span></td>
          </tr>
        `).join('');
      }
    }
  }

  // --- REPORTS VIEW ---
  async renderReportsView() {
    const budgetStatus = await window.budgetsManager.getBudgetsStatus();
    const rIncome = document.getElementById('reportIncomeVal');
    const rExpense = document.getElementById('reportExpenseVal');
    const rSavings = document.getElementById('reportSavingsVal');

    if (rIncome) rIncome.textContent = '₹ ' + budgetStatus.totalIncome.toLocaleString('en-IN');
    if (rExpense) rExpense.textContent = '₹ ' + budgetStatus.totalExpense.toLocaleString('en-IN');
    if (rSavings) rSavings.textContent = '₹ ' + budgetStatus.netSavings.toLocaleString('en-IN');
  }

  // --- MANUAL TRANSACTION MODAL ---
  bindModalEvents() {
    const addTxnBtn = document.getElementById('btnOpenAddTxnModal');
    const modal = document.getElementById('manualTxnModal');
    const closeBtn = document.getElementById('btnCloseAddTxnModal');
    const cancelBtn = document.getElementById('btnCancelAddTxn');
    const form = document.getElementById('manualTxnForm');

    if (addTxnBtn && modal) {
      addTxnBtn.addEventListener('click', () => {
        this.populateModalAccountOptions();
        modal.classList.add('active');
      });
    }

    const closeModal = () => modal && modal.classList.remove('active');
    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);

    if (form) {
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const type = document.getElementById('mTxnType').value;
        const amount = parseFloat(document.getElementById('mTxnAmount').value) || 0;
        const date = document.getElementById('mTxnDate').value || new Date().toISOString().split('T')[0];
        const category = document.getElementById('mTxnCategory').value;
        const accountId = document.getElementById('mTxnAccount').value;
        const description = document.getElementById('mTxnDesc').value;
        const notes = document.getElementById('mTxnNotes').value;

        if (amount <= 0 || !description) {
          alert('Please enter a valid amount and description.');
          return;
        }

        const newTxn = {
          id: 'txn_manual_' + Date.now(),
          date,
          amount,
          type,
          category,
          needsReview: false,
          confidence: 'manual',
          identifier: description.toLowerCase(),
          description,
          rawNarration: 'Manual Entry: ' + description,
          accountId,
          paymentMode: type === 'income' ? 'Direct Credit' : 'Cash/Manual',
          referenceNo: 'MANUAL_' + Date.now().toString().slice(-6),
          sourceFile: 'Manual Entry',
          isDuplicate: false,
          notes,
          createdAt: new Date().toISOString()
        };

        await window.db.put('transactions', newTxn);

        // Auto-learn rule for this manual merchant
        if (description.length > 2 && category !== 'Uncategorized') {
          await window.categorizer.learnRuleAndReclassify(description, category, type);
        }

        closeModal();
        form.reset();
        this.showToast('Transaction saved offline successfully!', 'success');
        await this.refreshAllViews();
      });
    }
  }

  async populateModalAccountOptions() {
    const accounts = await window.db.getAll('accounts');
    const categories = (await window.db.getAll('categories')).filter(c => c.name !== 'Uncategorized');

    const accSelect = document.getElementById('mTxnAccount');
    if (accSelect) {
      if (accounts.length === 0) {
        accSelect.innerHTML = '<option value="">No Accounts (Add in Cards & Accounts or Upload Statement)</option>';
      } else {
        accSelect.innerHTML = accounts.map(a => `<option value="${a.id}">${this.escape(a.name)} (${this.escape(a.bankName || '')})</option>`).join('');
      }
    }

    const catSelect = document.getElementById('mTxnCategory');
    if (catSelect) {
      catSelect.innerHTML = categories.map(c => `<option value="${c.name}">${c.name}</option>`).join('');
    }

    const dateInput = document.getElementById('mTxnDate');
    if (dateInput) {
      dateInput.value = new Date().toISOString().split('T')[0];
    }
  }

  // --- PROFILE CHOOSER (shown when no profile is active) ---
  showProfileChooser() {
    const screen = document.getElementById('profileChooserScreen');
    if (screen) {
      screen.style.display = 'flex';
      this.renderProfileChooserCards();
    }
  }

  renderProfileChooserCards() {
    const profiles = window.profileManager.getProfiles();
    const grid = document.getElementById('profileChooserGrid');
    if (!grid) return;

    if (profiles.length === 0) {
      grid.innerHTML = '<div class="profile-chooser-empty"><i data-lucide="users" style="width:40px;height:40px;opacity:0.3;"></i><p>No profiles yet. Create your first one below!</p></div>';
    } else {
      grid.innerHTML = profiles.map(p => `
        <div class="profile-card" onclick="app.selectProfile('${p.id}')">
          <div class="profile-card-avatar" style="background:${p.color};">${this.escape(p.initial)}</div>
          <div class="profile-card-name">${this.escape(p.name)}</div>
          <div class="profile-card-meta">Created ${new Date(p.createdAt).toLocaleDateString()}</div>
          <button class="profile-card-delete" onclick="event.stopPropagation(); app.deleteProfileFromChooser('${p.id}')" title="Delete profile">
            <i data-lucide="trash-2" style="width:13px;height:13px;"></i>
          </button>
        </div>
      `).join('');
    }

    if (window.lucide) lucide.createIcons();
  }

  selectProfile(id) {
    window.profileManager.switchProfile(id);
  }

  createProfileFromChooser() {
    const input = document.getElementById('newProfileNameInput');
    const errorEl = document.getElementById('newProfileError');
    if (!input) return;
    const name = input.value.trim();
    if (!name) {
      if (errorEl) { errorEl.textContent = 'Please enter a name.'; errorEl.style.display = 'block'; }
      return;
    }
    try {
      window.profileManager.createProfile(name); // triggers reload
    } catch (err) {
      if (errorEl) { errorEl.textContent = err.message; errorEl.style.display = 'block'; }
    }
  }

  deleteProfileFromChooser(id) {
    // Show inline confirm buttons on the card itself
    const card = document.querySelector(`[onclick="app.selectProfile('${id}')"]`);
    if (!card) return;
    const deleteBtn = card.querySelector('.profile-card-delete');
    if (!deleteBtn) return;

    // Temporarily replace the card's onclick to prevent accidental selection
    card.setAttribute('data-confirming', '1');
    card.style.pointerEvents = 'none';

    deleteBtn.innerHTML = `
      <div class="profile-card-confirm" onclick="event.stopPropagation();">
        <span style="font-size:0.7rem; color:#ef4444; font-weight:700;">Delete?</span>
        <button class="btn-inline-confirm" onclick="event.stopPropagation(); app._execDeleteFromChooser('${id}')" title="Confirm delete">✓</button>
        <button class="btn-inline-cancel" onclick="event.stopPropagation(); app._cancelDeleteFromChooser('${id}')" title="Cancel">×</button>
      </div>
    `;
    deleteBtn.style.opacity = '1';
    deleteBtn.style.background = 'rgba(239,68,68,0.1)';
    deleteBtn.style.width = 'auto';
    deleteBtn.style.height = 'auto';
    deleteBtn.style.borderRadius = '8px';
    deleteBtn.style.padding = '4px 6px';
    card.style.pointerEvents = 'auto';
  }

  async _execDeleteFromChooser(id) {
    await window.profileManager.deleteProfile(id); // triggers reload
  }

  _cancelDeleteFromChooser(id) {
    // Re-render the chooser cards to restore original state
    this.renderProfileChooserCards();
  }

  // --- PROFILE MANAGER MODAL (in-app switching) ---
  openProfileModal() {
    const modal = document.getElementById('profileManagerModal');
    if (!modal) return;
    this.renderProfileManagerList();
    if (window.lucide) lucide.createIcons();
    requestAnimationFrame(() => {
      modal.classList.add('active');
    });
  }

  closeProfileModal() {
    const modal = document.getElementById('profileManagerModal');
    if (!modal) return;
    modal.classList.remove('active');
  }

  renderProfileManagerList() {
    const profiles = window.profileManager.getProfiles();
    const active = window.profileManager.getActiveProfile();
    const list = document.getElementById('profileManagerList');
    if (!list) return;

    list.innerHTML = profiles.map(p => {
      const isActive = active && active.id === p.id;
      return `
        <div class="profile-manager-item ${isActive ? 'active' : ''}" id="profile-row-${p.id}">
          <div class="profile-manager-avatar" id="profile-avatar-${p.id}" style="background:${p.color};">${this.escape(p.initial)}</div>
          <div class="profile-manager-info">
            <div class="profile-manager-name" id="profile-name-display-${p.id}">
              ${this.escape(p.name)}
              ${isActive ? '<span class="profile-active-badge">Active</span>' : ''}
            </div>
            <div class="profile-manager-date">Created ${new Date(p.createdAt).toLocaleDateString()}</div>
          </div>
          <div class="profile-manager-actions">
            ${!isActive ? `<button class="btn btn-sm btn-secondary" onclick="window.profileManager.switchProfile('${p.id}')">Switch</button>` : ''}
            <button class="btn btn-sm btn-ghost" style="color:var(--text-muted);" onclick="app.startEditProfile('${p.id}', '${this.escape(p.name)}')" title="Rename">
              <i data-lucide="pencil" style="width:14px;height:14px;"></i>
            </button>
            <button class="btn btn-sm btn-ghost" style="color:#ef4444;" onclick="app.deleteProfileFromManager('${p.id}')" title="Delete">
              <i data-lucide="trash-2" style="width:14px;height:14px;"></i>
            </button>
          </div>
        </div>
      `;
    }).join('');

    if (window.lucide) lucide.createIcons();
  }

  createProfileFromManager() {
    const input = document.getElementById('newProfileNameManagerInput');
    const errorEl = document.getElementById('newProfileManagerError');
    if (!input) return;
    const name = input.value.trim();
    if (!name) {
      if (errorEl) { errorEl.textContent = 'Please enter a name.'; errorEl.style.display = 'block'; }
      return;
    }
    try {
      window.profileManager.createProfile(name); // triggers reload
    } catch (err) {
      if (errorEl) { errorEl.textContent = err.message; errorEl.style.display = 'block'; }
    }
  }

  deleteProfileFromManager(id) {
    // Inline confirm: replace the action buttons with a confirm strip
    const actionsEl = document.querySelector(`#profile-row-${id} .profile-manager-actions`);
    if (!actionsEl) return;

    actionsEl.innerHTML = `
      <span style="font-size:0.78rem; color:#ef4444; font-weight:600; white-space:nowrap;">Delete?</span>
      <button class="btn btn-sm" style="background:#ef4444; color:#fff; border:none; padding:3px 10px; font-size:0.78rem;" onclick="app._execDeleteFromManager('${id}')">
        Yes, Delete
      </button>
      <button class="btn btn-sm btn-ghost" style="font-size:0.78rem;" onclick="app.renderProfileManagerList()">
        Cancel
      </button>
    `;
  }

  async _execDeleteFromManager(id) {
    await window.profileManager.deleteProfile(id); // triggers reload
  }

  // --- PROFILE INLINE RENAME ---

  /**
   * Switches a profile row in the manager modal into edit mode.
   * Replaces the name text with an input + Save / Cancel buttons.
   */
  startEditProfile(id, currentName) {
    const nameEl = document.getElementById(`profile-name-display-${id}`);
    const avatarEl = document.getElementById(`profile-avatar-${id}`);
    if (!nameEl) return;

    // Build inline editor — preserves the Active badge if present
    const isActive = nameEl.querySelector('.profile-active-badge') !== null;
    const activeBadge = isActive ? '<span class="profile-active-badge">Active</span>' : '';

    nameEl.innerHTML = `
      <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap;">
        <input
          id="profile-rename-input-${id}"
          class="profile-rename-input"
          type="text"
          value="${this.escape(currentName)}"
          maxlength="32"
          autocomplete="off"
          onkeydown="if(event.key==='Enter') app.saveProfileRename('${id}'); if(event.key==='Escape') app.cancelEditProfile('${id}', '${this.escape(currentName)}')"
        >
        ${activeBadge}
      </div>
      <div id="profile-rename-error-${id}" style="display:none; font-size:0.75rem; color:#ef4444; margin-top:2px;"></div>
      <div style="display:flex; gap:4px; margin-top:4px;">
        <button class="btn btn-sm btn-primary" style="font-size:0.75rem; padding:3px 10px;" onclick="app.saveProfileRename('${id}')">
          <i data-lucide="check" style="width:12px;height:12px;"></i> Save
        </button>
        <button class="btn btn-sm btn-ghost" style="font-size:0.75rem; padding:3px 8px;" onclick="app.cancelEditProfile('${id}', '${this.escape(currentName)}')">
          Cancel
        </button>
      </div>
    `;

    if (window.lucide) lucide.createIcons();

    // Auto-focus the input
    const input = document.getElementById(`profile-rename-input-${id}`);
    if (input) { input.focus(); input.select(); }
  }

  /**
   * Saves the renamed profile. Updates the row live without reloading.
   */
  saveProfileRename(id) {
    const input = document.getElementById(`profile-rename-input-${id}`);
    const errorEl = document.getElementById(`profile-rename-error-${id}`);
    if (!input) return;

    const newName = input.value.trim();
    try {
      const updated = window.profileManager.renameProfile(id, newName);

      // Update avatar initial
      const avatarEl = document.getElementById(`profile-avatar-${id}`);
      if (avatarEl) avatarEl.textContent = updated.initial;

      // Update sidebar pill if this is the active profile
      const active = window.profileManager.getActiveProfile();
      if (active && active.id === id) {
        this.updateSidebarProfilePill();
      }

      // Re-render the row back to normal view
      const nameEl = document.getElementById(`profile-name-display-${id}`);
      if (nameEl) {
        const isActive = active && active.id === id;
        nameEl.innerHTML = `
          ${this.escape(updated.name)}
          ${isActive ? '<span class="profile-active-badge">Active</span>' : ''}
        `;
      }

      if (window.lucide) lucide.createIcons();
    } catch (err) {
      if (errorEl) { errorEl.textContent = err.message; errorEl.style.display = 'block'; }
    }
  }

  /**
   * Cancels editing and restores the original name text.
   */
  cancelEditProfile(id, originalName) {
    const nameEl = document.getElementById(`profile-name-display-${id}`);
    if (!nameEl) return;
    const active = window.profileManager.getActiveProfile();
    const isActive = active && active.id === id;
    nameEl.innerHTML = `
      ${this.escape(originalName)}
      ${isActive ? '<span class="profile-active-badge">Active</span>' : ''}
    `;
    if (window.lucide) lucide.createIcons();
  }

  bindProfileModalEvents() {
    // Close on overlay click
    const modal = document.getElementById('profileManagerModal');
    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) this.closeProfileModal();
      });
    }
    // Enter key on new profile input
    const input = document.getElementById('newProfileNameManagerInput');
    if (input) {
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') this.createProfileFromManager();
      });
    }
    // Enter key on chooser input
    const chooserInput = document.getElementById('newProfileNameInput');
    if (chooserInput) {
      chooserInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') this.createProfileFromChooser();
      });
    }
  }

  updateSidebarProfilePill() {
    const profile = window.profileManager.getActiveProfile();
    const pill = document.getElementById('sidebarProfilePill');
    if (!pill || !profile) return;
    pill.innerHTML = `
      <div class="sidebar-profile-avatar" style="background:${profile.color};">${this.escape(profile.initial)}</div>
      <div class="sidebar-profile-info">
        <div class="sidebar-profile-name">${this.escape(profile.name)}</div>
        <div class="sidebar-profile-sub">Switch profile</div>
      </div>
      <i data-lucide="chevrons-up-down" style="width:13px;height:13px;opacity:0.4;flex-shrink:0;"></i>
    `;
    if (window.lucide) lucide.createIcons();
  }

  // --- TOAST NOTIFICATIONS (Disabled per user request) ---
  showToast(message, type = 'info') {
    // Popup notifications in the bottom right corner have been disabled
  }
}

// Global App Instance
window.app = new App();

// Boot on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
  window.app.init();
});
