/**
 * Application Main Controller & UI Coordinator
 * Handles tab navigation, UI updates, user interactions, AI rule learning, modals, and toasts.
 */

class App {
  constructor() {
    this.currentTab = 'dashboard';
    this.currentDaysRange = 30;
    this.currentPieDaysRange = 30;
    this.transactionsPage = 1;
    this.transactionsPerPage = 15;
    this.searchQuery = '';
    this.categoryFilter = 'all';
    this.accountFilter = 'all';
    this.typeFilter = 'all';
    this.currencyFilter = 'all';
    this.chartViewMode = 'cumulative';
    this.theme = 'dark';
    this.txnViewMode = 'table';
    this._modalFocusReturn = new Map();
  }

  // --- MODAL ACCESSIBILITY (focus trapping, focus in on open, focus restore on close) ---
  trapModalFocus(modal) {
    if (!modal) return;
    this.releaseModalFocus(modal);

    const focusableSelectors = 'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

    const keyHandler = (e) => {
      if (e.key !== 'Tab') return;
      const focusables = Array.from(modal.querySelectorAll(focusableSelectors)).filter(
        el => el.offsetParent !== null && !el.hasAttribute('disabled') && getComputedStyle(el).visibility !== 'hidden'
      );
      if (focusables.length === 0) {
        e.preventDefault();
        return;
      }
      const first = focusables[0];
      const last = focusables[focusables.length - 1];

      if (e.shiftKey) {
        if (document.activeElement === first || !modal.contains(document.activeElement)) {
          e.preventDefault();
          last.focus();
        }
      } else {
        if (document.activeElement === last || !modal.contains(document.activeElement)) {
          e.preventDefault();
          first.focus();
        }
      }
    };

    modal._focusTrapHandler = keyHandler;
    modal.addEventListener('keydown', keyHandler);
  }

  releaseModalFocus(modal) {
    if (!modal || !modal._focusTrapHandler) return;
    modal.removeEventListener('keydown', modal._focusTrapHandler);
    modal._focusTrapHandler = null;
  }

  focusModal(modal) {
    if (!modal) return;
    this._modalFocusReturn.set(modal.id, document.activeElement);
    this.trapModalFocus(modal);
    const dialogEl = modal.querySelector('[role="dialog"]') || modal;
    const focusable = dialogEl.querySelector('input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), [tabindex]:not([tabindex="-1"])');
    (focusable || dialogEl).focus();
  }

  restoreModalFocus(modal) {
    if (!modal) return;
    this.releaseModalFocus(modal);
    const returnEl = this._modalFocusReturn.get(modal.id);
    this._modalFocusReturn.delete(modal.id);
    if (returnEl && typeof returnEl.focus === 'function' && document.contains(returnEl)) {
      returnEl.focus();
    }
  }

  // --- SAFE LUCIDE ICON HYDRATION ---
  hydrateIcons(root = null) {
    const lucideObj = window.lucide || (typeof lucide !== 'undefined' ? lucide : null);
    if (lucideObj && typeof lucideObj.createIcons === 'function') {
      try {
        if (root) {
          lucideObj.createIcons({ root });
        } else {
          lucideObj.createIcons();
        }
      } catch (err) {
        console.warn('Lucide icon hydration warning:', err);
      }
    }
  }

  bindGlobalModalEscape() {
    const modalCloseActions = {
      manualTxnModal: () => document.getElementById('btnCloseAddTxnModal')?.click(),
      editTxnModal: () => document.getElementById('btnCloseEditTxnModal')?.click(),
      accountModal: () => this.closeAccountModal(),
      pdfPasswordModal: () => this.closePdfPasswordModal(),
      profileManagerModal: () => this.closeProfileModal()
    };
    document.addEventListener('keydown', (e) => {
      if (e.key !== 'Escape') return;
      // A calendar/timepicker popover open inside a modal should close on its own
      // Escape first (their own handlers do that) — don't also close the modal underneath it.
      if (window.calendarEngine?.activePopover || window.timePickerEngine?.activePopover) return;
      for (const [id, closeFn] of Object.entries(modalCloseActions)) {
        const modal = document.getElementById(id);
        if (modal && modal.classList.contains('active')) {
          closeFn();
          break;
        }
      }
    });
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

  // --- BACKUP REMINDER (data lives only in this browser's IndexedDB) ---
  shouldShowBackupReminder(transactionCount) {
    if (!transactionCount) return false;
    try {
      const profileId = (window.profileManager && window.profileManager.getActiveProfile()?.id) || 'default';
      const dismissedUntil = parseInt(localStorage.getItem('backupReminderDismissedUntil_' + profileId) || '0', 10);
      if (Date.now() < dismissedUntil) return false;
      const lastBackupAt = parseInt(localStorage.getItem('lastBackupAt_' + profileId) || '0', 10);
      const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
      return (Date.now() - lastBackupAt) > THIRTY_DAYS_MS;
    } catch (e) {
      return false;
    }
  }

  dismissBackupReminder() {
    try {
      const profileId = (window.profileManager && window.profileManager.getActiveProfile()?.id) || 'default';
      const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;
      localStorage.setItem('backupReminderDismissedUntil_' + profileId, String(Date.now() + SEVEN_DAYS_MS));
    } catch (e) { /* localStorage unavailable, ignore */ }
    const banner = document.getElementById('dashboardBackupBanner');
    if (banner) banner.style.display = 'none';
  }

  async init() {
    try {
      // Initialize Theme Preference
      this.initTheme();

      // --- PROFILE GATE ---
      // If no active profile exists, show the profile chooser and halt the app boot.
      if (!window.profileManager.hasActiveProfile()) {
        this.showProfileChooser();
        this.hydrateIcons();
        return; // Don't init DB or render anything until a profile is chosen.
      }

      // Profile is set — update the sidebar profile pill
      this.updateSidebarProfilePill();

      // Sync daily exchange rates in background (once per calendar day)
      if (window.CurrencyEngine) {
        window.CurrencyEngine.checkAndFetchDailyRates().catch(() => {});
      }

      // Initialize IndexedDB (uses active profile's isolated namespace)
      await window.db.init();

      // Ask the browser not to evict this profile's data under storage pressure (best effort)
      if (navigator.storage && navigator.storage.persist) {
        navigator.storage.persist().catch(() => {});
      }

      // Setup Event Listeners & UI
      this.bindNavigationEvents();
      this.bindDropzoneEvents();
      this.bindFilterEvents();
      this.bindModalEvents();
      this.bindAccountModalEvents();
      this.bindProfileModalEvents();
      this.bindGlobalModalEscape();

      // Initial Duplicate Scan
      await window.duplicateDetector.scanDatabase();

      // Initial Render
      await this.refreshAllViews();

      // Lucide icons initialization
      this.hydrateIcons();

      this.showToast('App initialized offline. All data is securely stored on your device.', 'info');
    } catch (err) {
      console.error('App init error:', err);
      this.showFatalError(err);
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

    this.hydrateIcons();

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

    // Mobile sidebar toggle & backdrop
    const mobileBtn = document.getElementById('mobileMenuToggle');
    const sidebar = document.querySelector('.sidebar');
    const backdrop = document.getElementById('sidebarBackdrop');

    const setSidebarOpen = (open) => {
      if (sidebar) sidebar.classList.toggle('open', open);
      if (backdrop) backdrop.classList.toggle('active', open);
      if (mobileBtn) {
        mobileBtn.setAttribute('aria-expanded', String(open));
        mobileBtn.setAttribute('aria-label', open ? 'Close navigation menu' : 'Open navigation menu');
      }
    };

    if (mobileBtn && sidebar) {
      mobileBtn.addEventListener('click', () => {
        const isOpen = sidebar.classList.contains('open');
        setSidebarOpen(!isOpen);
      });
    }

    if (backdrop) {
      backdrop.addEventListener('click', () => {
        setSidebarOpen(false);
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
        const rangeVal = btn.getAttribute('data-range');
        this.currentDaysRange = rangeVal === 'all' ? 'all' : (parseInt(rangeVal) || 30);
        this.renderDashboardCharts();
      });
    });

    // Pie Chart / Category Donut Range Selector buttons
    const pieRangeBtns = document.querySelectorAll('[data-pie-range]');
    pieRangeBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        pieRangeBtns.forEach(b => b.classList.remove('btn-primary'));
        pieRangeBtns.forEach(b => b.classList.add('btn-secondary'));
        btn.classList.remove('btn-secondary');
        btn.classList.add('btn-primary');
        const rangeVal = btn.getAttribute('data-pie-range');
        this.currentPieDaysRange = rangeVal === 'all' ? 'all' : (parseInt(rangeVal) || 30);
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

    // Announce the new view: header title + document title (screen readers, history, tab strip)
    const navLabel = document.querySelector(`.nav-item button[data-tab="${tabId}"] span`);
    const heading = document.getElementById('pageTitle');
    if (navLabel && heading) {
      heading.textContent = navLabel.textContent.trim();
      document.title = `${heading.textContent} · Money Tracker`;
    }

    // Close mobile menu if open
    const sidebar = document.querySelector('.sidebar');
    if (sidebar) sidebar.classList.remove('open');
    const backdrop = document.getElementById('sidebarBackdrop');
    if (backdrop) backdrop.classList.remove('active');
    const menuBtn = document.getElementById('mobileMenuToggle');
    if (menuBtn) {
      menuBtn.setAttribute('aria-expanded', 'false');
      menuBtn.setAttribute('aria-label', 'Open navigation menu');
    }

    // Refresh view specific data
    this.refreshCurrentTab();

    this.hydrateIcons();
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
        if (this.txnViewMode === 'calendar') {
          await this.renderTxnCalendar();
        } else {
          await this.renderTransactionsTable();
        }
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
      case 'currency':
        await this.renderCurrencyView();
        break;
      case 'import':
        await this.renderImportView();
        break;
    }
    this.hydrateIcons();
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
    const daysBack = this.currentDaysRange === 'all' ? null : this.currentDaysRange;
    const budgetStatus = await window.budgetsManager.getBudgetsStatus(daysBack);

    const backupBanner = document.getElementById('dashboardBackupBanner');
    if (backupBanner) {
      const validTxnCount = transactions.filter(t => t.duplicateStatus !== 'merged').length;
      backupBanner.style.display = this.shouldShowBackupReminder(validTxnCount) ? 'flex' : 'none';
    }
    const accounts = await window.accountsManager.getAccountsWithMetrics();

    const active = window.profileManager?.getActiveProfile();
    const primaryCurrency = (active && active.currency) || 'INR';

    let totalNetWorth = 0;
    accounts.forEach(a => {
      const accCurr = a.currency || primaryCurrency;
      const converted = window.CurrencyEngine
        ? window.CurrencyEngine.convert(a.computedBalance, accCurr, primaryCurrency)
        : a.computedBalance;
      totalNetWorth += converted;
    });

    const kpiBalance = document.getElementById('kpiTotalBalance');
    const kpiIncome = document.getElementById('kpiTotalIncome');
    const kpiExpense = document.getElementById('kpiTotalExpense');
    const kpiSavings = document.getElementById('kpiSavingsRate');

    if (kpiBalance) kpiBalance.textContent = window.CurrencyEngine ? window.CurrencyEngine.format(totalNetWorth, primaryCurrency, { maximumFractionDigits: 0 }) : '₹ ' + Math.round(totalNetWorth);
    if (kpiIncome) kpiIncome.textContent = window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalIncome, primaryCurrency, { maximumFractionDigits: 0 }) : '₹ ' + Math.round(budgetStatus.totalIncome);
    if (kpiExpense) kpiExpense.textContent = window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalExpense, primaryCurrency, { maximumFractionDigits: 0 }) : '₹ ' + Math.round(budgetStatus.totalExpense);
    if (kpiSavings) kpiSavings.textContent = `${budgetStatus.savingsRate}%`;

    // Hero Flow Bar & Labels
    const heroInflow = document.getElementById('heroInflowLabel');
    const heroOutflow = document.getElementById('heroOutflowLabel');
    const heroBarIn = document.getElementById('heroFlowBarIncome');
    const heroBarOut = document.getElementById('heroFlowBarExpense');

    if (heroInflow) heroInflow.textContent = 'Inflow: ' + (window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalIncome, primaryCurrency) : '₹ ' + budgetStatus.totalIncome);
    if (heroOutflow) heroOutflow.textContent = 'Outflow: ' + (window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalExpense, primaryCurrency) : '₹ ' + budgetStatus.totalExpense);

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
        .sort((a, b) => DateUtil.compareTxnDesc(a, b))
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
                  ${t.type === 'income' || t.type === 'refund' ? '+' : '-'} ${window.CurrencyEngine ? window.CurrencyEngine.format(t.amount, t.currency || primaryCurrency) : '₹ ' + Number(t.amount).toLocaleString('en-IN')}
                </span>
                ${(t.currency && t.currency !== primaryCurrency && window.CurrencyEngine) ? `
                  <div style="font-size:0.7rem; color:var(--text-dim); font-weight:normal;">
                    ≈ ${window.CurrencyEngine.format(window.CurrencyEngine.convert(t.amount, t.currency, primaryCurrency), primaryCurrency)}
                  </div>
                ` : ''}
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

    const donutData = window.chartsEngine.renderCategoryDonutChart('chartCategoryDonut', transactions, this.currentPieDaysRange);
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
                <span class="cat-amount">${window.CurrencyEngine ? window.CurrencyEngine.format(amt, primaryCurrency) : '₹ ' + Number(amt).toLocaleString('en-IN')}</span>
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
              ${window.CurrencyEngine ? window.CurrencyEngine.format(t.amount, t.currency || primaryCurrency) : '₹ ' + Number(t.amount).toLocaleString('en-IN')}
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
                <option value="refund" ${t.type === 'refund' ? 'selected' : ''}>Refund (Reversal)</option>
              </select>
            </div>

            <div style="align-self:flex-end;">
              <button class="btn btn-primary" data-id="${this.escape(t.id)}" onclick="app.resolveUndetectedSingle(this.dataset.id)">
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
            <button class="btn btn-ghost btn-sm btn-icon-only" data-id="${this.escape(r.id)}" onclick="app.deleteRule(this.dataset.id)" title="Delete Rule">
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

  async openAddRuleModal() {
    const modal = document.getElementById('addRuleModal');
    if (!modal) return;
    const catSelect = document.getElementById('ruleCategorySelect');
    if (catSelect) {
      const categories = (await window.db.getAll('categories')).filter(c => c.name !== 'Uncategorized');
      catSelect.innerHTML = categories.map(c => `<option value="${this.escape(c.name)}">${this.escape(c.name)}</option>`).join('');
    }
    const patternInput = document.getElementById('rulePatternInput');
    if (patternInput) {
      patternInput.value = '';
    }
    const errEl = document.getElementById('addRuleError');
    if (errEl) errEl.style.display = 'none';

    modal.style.display = 'flex';
    this.hydrateIcons(modal);
    if (patternInput) patternInput.focus();
  }

  closeAddRuleModal() {
    const modal = document.getElementById('addRuleModal');
    if (modal) modal.style.display = 'none';
  }

  async saveCustomRule() {
    const patternInput = document.getElementById('rulePatternInput');
    const catSelect = document.getElementById('ruleCategorySelect');
    const typeSelect = document.getElementById('ruleTypeSelect');
    const errEl = document.getElementById('addRuleError');

    const pattern = (patternInput?.value || '').trim();
    const category = catSelect?.value || 'General';
    const type = typeSelect?.value || 'expense';

    if (!pattern) {
      if (errEl) {
        errEl.textContent = 'Please enter a keyword pattern or UPI ID';
        errEl.style.display = 'block';
      }
      return;
    }

    try {
      const { reclassifiedCount } = await window.categorizer.learnRuleAndReclassify(pattern, category, type);
      this.closeAddRuleModal();
      this.showToast(`Custom rule saved! ${reclassifiedCount > 0 ? `Reclassified ${reclassifiedCount} existing transaction${reclassifiedCount > 1 ? 's' : ''}.` : ''}`, 'success');
      await this.refreshAllViews();
    } catch (err) {
      console.error('Error saving rule:', err);
      if (errEl) {
        errEl.textContent = 'Failed to save rule: ' + err.message;
        errEl.style.display = 'block';
      }
    }
  }

  // --- TRANSACTIONS TABLE VIEW ---
  bindFilterEvents() {
    const searchInput = document.getElementById('txnSearchInput');
    if (searchInput) {
      let searchTimer;
      searchInput.addEventListener('input', (e) => {
        this.searchQuery = e.target.value.toLowerCase();
        this.transactionsPage = 1;
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => this.renderTransactionsTable(), 150);
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

    const curSelect = document.getElementById('txnCurrencyFilter');
    if (curSelect) {
      curSelect.addEventListener('change', (e) => {
        this.currencyFilter = e.target.value;
        this.transactionsPage = 1;
        this.renderTransactionsTable();
      });
    }
  }

  async renderTransactionsTable() {
    const renderSeq = (this._txnRenderSeq = (this._txnRenderSeq || 0) + 1);
    const transactions = await window.db.getAll('transactions');
    const accounts = await window.accountsManager.getAccountsWithMetrics();
    const categories = await window.db.getAll('categories');
    if (renderSeq !== this._txnRenderSeq) return; // a newer render superseded this one

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

      if (this.currencyFilter && this.currencyFilter !== 'all') {
        const txnCur = (t.currency || 'INR').toUpperCase();
        if (txnCur !== this.currencyFilter.toUpperCase()) return false;
      }

      if (this.typeFilter === 'needs_review') {
        if (!t.needsReview && t.category !== 'Uncategorized') return false;
      } else if (this.typeFilter !== 'all' && t.type !== this.typeFilter) {
        return false;
      }

      return true;
    });

    filtered.sort((a, b) => DateUtil.compareTxnDesc(a, b));

    const totalCount = filtered.length;
    const totalPages = Math.ceil(totalCount / this.transactionsPerPage) || 1;
    if (this.transactionsPage > totalPages) this.transactionsPage = totalPages;

    const startIdx = (this.transactionsPage - 1) * this.transactionsPerPage;
    const pagedTxns = filtered.slice(startIdx, startIdx + this.transactionsPerPage);

    const tbody = document.getElementById('transactionsTableBody');
    if (tbody) {
      if (pagedTxns.length === 0) {
        const hasAny = transactions.some(t => t.duplicateStatus !== 'merged');
        tbody.innerHTML = hasAny
          ? `<tr><td colspan="7" style="text-align:center; padding:32px; color:var(--text-muted);">No transactions match these filters. Try clearing the search or filters.</td></tr>`
          : `<tr><td colspan="7" style="text-align:center; padding:40px 16px; color:var(--text-muted);">
              <div style="font-weight:600; color:var(--text-main); margin-bottom:6px;">No transactions yet</div>
              <div style="margin-bottom:14px;">Import a bank or UPI statement, or add a transaction by hand.</div>
              <button type="button" class="btn btn-primary btn-sm" onclick="app.switchTab('import')">Import a statement</button>
            </td></tr>`;
      } else {
        tbody.innerHTML = pagedTxns.map(t => {
          const acc = accounts.find(a => a.id === t.accountId);
          const isDup = t.isDuplicate && t.duplicateStatus === 'pending_review';
          const isReview = t.needsReview || t.category === 'Uncategorized';

          return `
            <tr class="${isDup ? 'duplicate-row' : ''}">
              <td>
                <div style="font-family:var(--font-mono); font-size:0.8rem; color:var(--text-muted);">${this.escape(t.date)}</div>
                ${t.time ? `<div style="font-family:var(--font-mono); font-size:0.7rem; color:var(--text-dim); margin-top:2px;">${this.escape(t.time)}</div>` : ''}
              </td>
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
                  ${t.type === 'income' || t.type === 'refund' ? '+' : '-'} ${window.CurrencyEngine ? window.CurrencyEngine.format(t.amount, t.currency || primaryCurrency) : '₹ ' + Number(t.amount).toLocaleString('en-IN')}
                </span>
                ${(t.currency && t.currency !== primaryCurrency && window.CurrencyEngine) ? `
                  <div style="font-size:0.7rem; color:var(--text-dim); font-weight:normal;">
                    ≈ ${window.CurrencyEngine.format(window.CurrencyEngine.convert(t.amount, t.currency, primaryCurrency), primaryCurrency)}
                  </div>
                ` : ''}
              </td>
              <td style="text-align:center;">
                <div style="display:inline-flex; align-items:center; gap:4px;">
                  <button class="btn btn-ghost btn-sm btn-icon-only" data-id="${this.escape(t.id)}" onclick="app.openEditTxnModal(this.dataset.id)" title="Edit Transaction">
                    <i data-lucide="edit-3" style="width:14px; height:14px; color:#3b82f6;"></i>
                  </button>
                  <button class="btn btn-ghost btn-sm btn-icon-only" data-id="${this.escape(t.id)}" onclick="app.deleteTransaction(this.dataset.id)" title="Delete">
                    <i data-lucide="trash-2" style="width:14px; height:14px; color:#f43f5e;"></i>
                  </button>
                </div>
              </td>
            </tr>
          `;
        }).join('');
        if (tbody) {
          this.hydrateIcons(tbody);
        }
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

  deleteTxn(id) {
    return this.deleteTransaction(id);
  }

  setTxnViewMode(mode) {
    this.txnViewMode = mode;
    const btnTable = document.getElementById('btnTxnViewTable');
    const btnCal = document.getElementById('btnTxnViewCalendar');
    const tableContainer = document.getElementById('txnTableViewContainer');
    const calContainer = document.getElementById('txnCalendarViewContainer');

    if (mode === 'calendar') {
      if (btnTable) {
        btnTable.classList.remove('btn-primary');
        btnTable.classList.add('btn-ghost');
      }
      if (btnCal) {
        btnCal.classList.remove('btn-ghost');
        btnCal.classList.add('btn-primary');
      }
      if (tableContainer) tableContainer.style.display = 'none';
      if (calContainer) {
        calContainer.style.display = 'block';
        this.renderTxnCalendar();
      }
    } else {
      if (btnTable) {
        btnTable.classList.remove('btn-ghost');
        btnTable.classList.add('btn-primary');
      }
      if (btnCal) {
        btnCal.classList.remove('btn-primary');
        btnCal.classList.add('btn-ghost');
      }
      if (tableContainer) tableContainer.style.display = 'block';
      if (calContainer) calContainer.style.display = 'none';
      this.renderTransactionsTable();
    }
    this.hydrateIcons();
  }

  async renderTxnCalendar() {
    const calContainer = document.getElementById('txnCalendarViewContainer');
    if (!calContainer || !window.calendarEngine) return;
    const transactions = await window.db.getAll('transactions');
    const activeTxns = transactions.filter(t => t.duplicateStatus !== 'merged');
    window.calendarEngine.renderFinancialCalendar(calContainer, activeTxns);
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
                <span style="font-size:0.82rem; color:var(--text-muted);">${this.escape(pair.reason)}</span>
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
              <button class="btn btn-ghost btn-sm" data-id1="${this.escape(pair.tx1.id)}" data-id2="${this.escape(pair.tx2.id)}" onclick="app.dismissDuplicate(this.dataset.id1, this.dataset.id2)">
                Mark as Separate
              </button>
              <button class="btn btn-danger btn-sm" data-id1="${this.escape(pair.tx2.id)}" data-id2="${this.escape(pair.tx1.id)}" onclick="app.deleteDuplicateSingle(this.dataset.id1, this.dataset.id2)">
                Keep Left Only
              </button>
              <button class="btn btn-primary btn-sm" data-id1="${this.escape(pair.tx1.id)}" data-id2="${this.escape(pair.tx2.id)}" onclick="app.mergeDuplicatePair(this.dataset.id1, this.dataset.id2)">
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
        .sort((a, b) => DateUtil.compareTxnDesc(a, b));

      const totalSpend = accTxns.filter(t => t.type === 'expense').reduce((s, t) => s + Math.abs(t.amount || 0), 0);
      const totalCredits = accTxns.filter(t => t.type === 'income' || t.type === 'transfer' || t.type === 'refund').reduce((s, t) => s + Math.abs(t.amount || 0), 0);

      // This Month Spend
      const thisMonthTxns = accTxns.filter(t => {
        const d = DateUtil.parseLocal(t.date);
        return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && t.type === 'expense';
      });
      const thisMonthSpend = thisMonthTxns.reduce((s, t) => s + Math.abs(t.amount || 0), 0);

      // Last Month Spend
      const lastMonthTxns = accTxns.filter(t => {
        const d = DateUtil.parseLocal(t.date);
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
               data-id="${this.escape(acc.id)}"
               onclick="app.toggleAccountExpand(this.dataset.id)"
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
                <button class="btn-card-action" data-id="${this.escape(acc.id)}" onclick="event.stopPropagation(); app.openAccountModal(this.dataset.id)" title="Edit Account" style="background:rgba(255,255,255,0.18); border:none; color:#fff; border-radius:6px; width:28px; height:28px; display:flex; align-items:center; justify-content:center; cursor:pointer; transition:0.2s;">
                  <i data-lucide="edit-2" style="width:14px; height:14px;"></i>
                </button>
                <button class="btn-card-action" data-id="${this.escape(acc.id)}" onclick="event.stopPropagation(); app.deleteAccount(this.dataset.id)" title="Delete Account" style="background:rgba(239,68,68,0.3); border:none; color:#fff; border-radius:6px; width:28px; height:28px; display:flex; align-items:center; justify-content:center; cursor:pointer; transition:0.2s;">
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
                const isRefund = t.type === 'refund';
                const amtCol = isExp ? '#f43f5e' : (isTrf ? '#8b5cf6' : (isRefund ? '#06b6d4' : '#10b981'));
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

    this.hydrateIcons();
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
    this.hydrateIcons();
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
        this.hydrateIcons();
        this.focusModal(modal);
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
      this.hydrateIcons();
      this.focusModal(modal);
    }
  }

  closeAccountModal() {
    const modal = document.getElementById('accountModal');
    if (modal) modal.classList.remove('active');
    this.restoreModalFocus(modal);
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
        const limitRaw = parseFloat(document.getElementById('accFormLimit').value);
        const creditLimit = Number.isFinite(limitRaw) && limitRaw >= 0 ? limitRaw : 100000;
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
          for (const droppedFile of Array.from(e.dataTransfer.files)) {
            await this.handleFileUpload(droppedFile);
          }
        }
      });

      fileInput.addEventListener('change', async (e) => {
        const picked = Array.from(e.target.files || []);
        // Reset so picking the same file again (e.g. after fixing it) fires 'change' again
        e.target.value = '';
        for (const pickedFile of picked) {
          await this.handleFileUpload(pickedFile);
        }
      });
    }
  }

  async handleFileUpload(file, password = null) {
    const statusBox = document.getElementById('uploadStatusBox');
    const isPDF = file.name.toLowerCase().endsWith('.pdf');
    if (statusBox) {
      statusBox.style.display = 'block';
      statusBox.innerHTML = `
        <div style="display:flex; flex-direction:column; gap:10px;">
          <div style="display:flex; align-items:center; gap:12px;">
            <div class="spinner" style="width:20px; height:20px; border:2px solid #6366f1; border-top-color:transparent; border-radius:50%; animation:spin 0.8s linear infinite; flex-shrink:0;"></div>
            <div>
              <div style="font-weight:600; color:var(--text-main); font-size:0.9rem;">Parsing <strong>${this.escape(file.name)}</strong></div>
              <div style="font-size:0.78rem; color:var(--text-muted); margin-top:2px;">100% offline · nothing leaves your device</div>
            </div>
          </div>
          ${isPDF ? `
          <div style="display:flex; gap:0; font-size:0.75rem; color:var(--text-muted); padding-left:32px;">
            <span style="color:#6366f1; font-weight:600;">① Extracting text&nbsp;</span>
            <span style="color:#94a3b8;">→ ② Auto-detecting format → ③ Categorising</span>
          </div>` : ''}
        </div>
      `;
    }

    try {
      const parseResult = await window.statementParser.parseFile(file, null, password);

      // Filter exact duplicates against database and current batch
      const existingTxns = (await window.db.getAll('transactions')) || [];
      const { filteredTransactions, exactDuplicatesCount } = window.duplicateDetector.filterExactDuplicates(
        parseResult.transactions,
        existingTxns
      );

      if (filteredTransactions.length > 0) {
        // Save only non-duplicate transactions to DB
        await window.db.putBatch('transactions', filteredTransactions);

        // Record statement upload
        await window.db.put('statements', {
          id: 'stmt_' + Date.now(),
          fileName: parseResult.fileName,
          fileType: parseResult.fileType,
          parsedDate: new Date().toISOString(),
          transactionCount: filteredTransactions.length,
          bankOrApp: parseResult.detectedProfile
        });
      }

      // Run duplicate detection scan for cross-statement fuzzy merges
      const dupScan = await window.duplicateDetector.scanDatabase();

      // Count unclassified in newly saved transactions
      const unclassified = filteredTransactions.filter(t => t.needsReview).length;

      if (statusBox) {
        const hasImported = filteredTransactions.length > 0;
        statusBox.innerHTML = `
          <div style="
            background: linear-gradient(135deg, rgba(16,185,129,0.08), rgba(5,150,105,0.05));
            border: 1px solid rgba(16,185,129,0.3);
            border-radius: 12px;
            padding: 16px 20px;
          ">
            <div style="display:flex; align-items:center; gap:10px; margin-bottom:${unclassified > 0 || dupScan.duplicatesFound > 0 || exactDuplicatesCount > 0 ? '10px' : '0'};">
              <span style="font-size:1.3rem;">${hasImported ? '✅' : 'ℹ️'}</span>
              <div>
                <div style="font-weight:700; color:${hasImported ? '#10b981' : '#38bdf8'}; font-size:0.95rem;">
                  ${hasImported 
                    ? `Imported ${filteredTransactions.length} transaction${filteredTransactions.length !== 1 ? 's' : ''}` 
                    : `No new transactions to import`}
                </div>
                <div style="font-size:0.78rem; color:var(--text-muted); margin-top:2px;">
                  Detected as <span style="
                    background:rgba(99,102,241,0.15); color:#818cf8;
                    padding:1px 8px; border-radius:10px; font-size:0.75rem;
                    border:1px solid rgba(99,102,241,0.25); font-weight:600;
                  ">${this.escape(parseResult.detectedProfile)}</span>
                  &nbsp;·&nbsp; ${this.escape(file.name)}
                </div>
              </div>
            </div>
            ${exactDuplicatesCount > 0 ? `
              <div style="color:#38bdf8; font-size:0.82rem; padding:6px 10px; background:rgba(56,189,248,0.1); border-radius:6px; margin-top:6px;">
                🔍 <strong>${exactDuplicatesCount}</strong> exact duplicate transaction(s) already existed in your database and were automatically filtered out.
              </div>` : ''}
            ${unclassified > 0 ? `
              <div style="color:#f59e0b; font-size:0.82rem; padding:6px 10px; background:rgba(245,158,11,0.1); border-radius:6px; margin-top:6px;">
                ⚡ <strong>${unclassified}</strong> transaction(s) need your review to teach the AI classifier.
              </div>` : ''}
            ${dupScan.duplicatesFound > 0 ? `
              <div style="color:#f97316; font-size:0.82rem; padding:6px 10px; background:rgba(249,115,22,0.1); border-radius:6px; margin-top:6px;">
                ⚠️ <strong>${dupScan.duplicatesFound}</strong> potential cross-statement duplicate transaction(s) detected.
              </div>` : ''}
          </div>
        `;
      }

      if (filteredTransactions.length > 0) {
        this.showToast(`Imported ${filteredTransactions.length} transactions!${exactDuplicatesCount > 0 ? ` (${exactDuplicatesCount} duplicates skipped)` : ''}`, 'success');
      } else {
        this.showToast(`All ${exactDuplicatesCount} transactions already exist in database!`, 'info');
      }

      await this.refreshAllViews();

      if (unclassified > 0) {
        setTimeout(() => this.switchTab('review'), 1000);
      } else if (dupScan.duplicatesFound > 0) {
        setTimeout(() => this.switchTab('duplicates'), 1000);
      }
    } catch (err) {
      console.error('File parsing error:', err);

      if (err.isPasswordProtected) {
        if (statusBox) statusBox.style.display = 'none';
        this.promptPdfPassword(file, err.isIncorrectPassword);
        return;
      }

      if (statusBox) {
        if (err.detectionFailed) {
          // Two sub-cases: (a) format detected but empty, (b) format not detected at all
          const profileKnown = err.detectedProfile && err.detectedProfile !== 'Generic Statement';

          statusBox.innerHTML = `
            <div style="
              background: linear-gradient(135deg, rgba(244,63,94,0.08), rgba(239,68,68,0.05));
              border: 1px solid rgba(244,63,94,0.3);
              border-radius: 12px;
              padding: 18px 20px;
              font-size: 0.88rem;
            ">
              <div style="display:flex; align-items:flex-start; gap:12px; margin-bottom:14px;">
                <span style="font-size:1.5rem; flex-shrink:0;">⚠️</span>
                <div>
                  <div style="font-weight:700; color:#f43f5e; font-size:0.95rem; margin-bottom:4px;">
                    ${profileKnown ? `Detected: "${this.escape(err.detectedProfile)}" — but no transactions found` : 'Failed to detect statement format'}
                  </div>
                  <div style="color:var(--text-muted);">
                    File: <strong style="color:var(--text-main);">${this.escape(file.name)}</strong>
                  </div>
                </div>
              </div>

              ${profileKnown ? `
                <div style="background:rgba(251,191,36,0.1); border:1px solid rgba(251,191,36,0.3); border-radius:8px; padding:12px 14px; margin-bottom:14px;">
                  <div style="font-weight:600; color:#f59e0b; margin-bottom:6px;">💡 Likely Causes</div>
                  <ul style="margin:0; padding-left:18px; color:var(--text-muted); line-height:1.8;">
                    <li>PDF is <strong>password-protected</strong> — open it in Adobe / browser, enter the password, then print/save as PDF without password</li>
                    <li>PDF contains <strong>scanned images</strong> instead of selectable text — requires OCR (unsupported in offline mode)</li>
                    <li>Statement is in an <strong>unsupported variant</strong> of this bank's format</li>
                  </ul>
                </div>
              ` : `
                <div style="background:rgba(251,191,36,0.1); border:1px solid rgba(251,191,36,0.3); border-radius:8px; padding:12px 14px; margin-bottom:14px;">
                  <div style="font-weight:600; color:#f59e0b; margin-bottom:8px;">📋 Supported Formats</div>
                  <div style="display:flex; flex-wrap:wrap; gap:6px;">
                    ${['Navi UPI','PhonePe','Paytm','Google Pay','SBI Bank','HDFC Bank','ICICI Bank','Axis Bank','Kotak Bank','HDFC CC','ICICI CC','Axis CC','SBI Card','Amazon Pay ICICI','CSV','Excel (.xlsx)'].map(f =>
                      `<span style="background:rgba(99,102,241,0.15); color:#818cf8; padding:2px 10px; border-radius:20px; font-size:0.78rem; border:1px solid rgba(99,102,241,0.25);">${f}</span>`
                    ).join('')}
                  </div>
                  <div style="margin-top:10px; color:var(--text-muted); line-height:1.7;">
                    💡 <strong>Tips:</strong> Make sure the PDF has selectable text (not scanned). If password-protected, unlock it first. For best results, download the statement directly from your bank's official app.
                  </div>
                </div>
              `}

              <div style="display:flex; gap:10px; flex-wrap:wrap;">
                <button onclick="document.getElementById('statementFileInput').click()" style="
                  background: linear-gradient(135deg, #6366f1, #8b5cf6);
                  color: #fff; border: none; border-radius: 8px;
                  padding: 8px 16px; cursor: pointer; font-size: 0.83rem; font-weight: 600;
                ">📂 Try Another File</button>
                <button onclick="this.closest('.upload-status-box').style.display='none'" style="
                  background: transparent; color: var(--text-muted);
                  border: 1px solid var(--border-color); border-radius: 8px;
                  padding: 8px 14px; cursor: pointer; font-size: 0.83rem;
                ">Dismiss</button>
              </div>
            </div>
          `;
        } else {
          // Generic parse/runtime error
          statusBox.innerHTML = `
            <div style="display:flex; align-items:center; gap:10px; color:#f43f5e; font-weight:600; padding:4px 0;">
              <span style="font-size:1.2rem;">✗</span>
              <span>Failed to parse <strong>${this.escape(file.name)}</strong>: ${this.escape(err.message)}</span>
            </div>
          `;
        }
      }

      this.showToast(err.detectionFailed
        ? (err.detectedProfile ? `Detected "${err.detectedProfile}" but found 0 transactions` : 'Failed to detect statement format')
        : err.message,
        'error'
      );
    }
  }

  promptPdfPassword(file, isIncorrect = false) {
    this.pendingPdfFile = file;
    const modal = document.getElementById('pdfPasswordModal');
    const nameEl = document.getElementById('pdfPasswordFileName');
    const inputEl = document.getElementById('pdfPasswordInput');
    const errEl = document.getElementById('pdfPasswordError');
    const btn = document.getElementById('btnSubmitPdfPassword');

    if (btn) {
      btn.disabled = false;
      btn.innerHTML = '<i data-lucide="unlock" style="width:16px; height:16px;"></i> Unlock &amp; Import';
    }

    if (nameEl) nameEl.textContent = file.name;
    if (inputEl) {
      inputEl.value = '';
      inputEl.type = 'password';
    }
    const eyeIcon = document.getElementById('pdfPwEyeIcon');
    if (eyeIcon) eyeIcon.setAttribute('data-lucide', 'eye');

    if (errEl) {
      if (isIncorrect) {
        errEl.textContent = '❌ Incorrect password. Please check and try again.';
        errEl.style.display = 'block';
      } else {
        errEl.textContent = '';
        errEl.style.display = 'none';
      }
    }

    if (modal) modal.classList.add('active');
    this.hydrateIcons();
    setTimeout(() => {
      this.focusModal(modal);
    }, 150);
  }

  closePdfPasswordModal() {
    const modal = document.getElementById('pdfPasswordModal');
    if (modal) modal.classList.remove('active');
    this.restoreModalFocus(modal);
    this.pendingPdfFile = null;
    const inputEl = document.getElementById('pdfPasswordInput');
    if (inputEl) inputEl.value = '';
    const errEl = document.getElementById('pdfPasswordError');
    if (errEl) {
      errEl.textContent = '';
      errEl.style.display = 'none';
    }
  }

  togglePdfPasswordVisibility() {
    const inputEl = document.getElementById('pdfPasswordInput');
    const eyeIcon = document.getElementById('pdfPwEyeIcon');
    if (!inputEl) return;
    if (inputEl.type === 'password') {
      inputEl.type = 'text';
      if (eyeIcon) eyeIcon.setAttribute('data-lucide', 'eye-off');
    } else {
      inputEl.type = 'password';
      if (eyeIcon) eyeIcon.setAttribute('data-lucide', 'eye');
    }
    this.hydrateIcons();
  }

  async submitPdfPassword() {
    const file = this.pendingPdfFile;
    const inputEl = document.getElementById('pdfPasswordInput');
    const errEl = document.getElementById('pdfPasswordError');
    const btn = document.getElementById('btnSubmitPdfPassword');
    if (!file || !inputEl) return;

    const password = inputEl.value.trim();
    if (!password) {
      if (errEl) {
        errEl.textContent = 'Please enter the PDF password.';
        errEl.style.display = 'block';
      }
      return;
    }

    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '<span class="spinner" style="width:14px;height:14px;border-width:2px;display:inline-block;"></span> Unlocking...';
    }

    try {
      await this.handleFileUpload(file, password);
      this.closePdfPasswordModal();
    } catch (e) {
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = '<i data-lucide="unlock" style="width:16px; height:16px;"></i> Unlock &amp; Import';
        this.hydrateIcons();
      }
      if (e && e.isPasswordProtected) {
        if (errEl) {
          errEl.textContent = '❌ Incorrect password. Please check and try again.';
          errEl.style.display = 'block';
        }
        inputEl.select();
      }
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
    const active = window.profileManager?.getActiveProfile();
    const primaryCurrency = (active && active.currency) || 'INR';

    const rIncome = document.getElementById('reportIncomeVal');
    const rExpense = document.getElementById('reportExpenseVal');
    const rSavings = document.getElementById('reportSavingsVal');

    if (rIncome) rIncome.textContent = window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalIncome, primaryCurrency) : '₹ ' + budgetStatus.totalIncome.toLocaleString('en-IN');
    if (rExpense) rExpense.textContent = window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.totalExpense, primaryCurrency) : '₹ ' + budgetStatus.totalExpense.toLocaleString('en-IN');
    if (rSavings) rSavings.textContent = window.CurrencyEngine ? window.CurrencyEngine.format(budgetStatus.netSavings, primaryCurrency) : '₹ ' + budgetStatus.netSavings.toLocaleString('en-IN');
  }

  // --- MANUAL & EDIT TRANSACTION MODALS ---
  bindModalEvents() {
    const addTxnBtn = document.getElementById('btnOpenAddTxnModal');
    const modal = document.getElementById('manualTxnModal');
    const closeBtn = document.getElementById('btnCloseAddTxnModal');
    const cancelBtn = document.getElementById('btnCancelAddTxn');
    const form = document.getElementById('manualTxnForm');

    if (addTxnBtn) {
      addTxnBtn.addEventListener('click', () => {
        this.openManualTxnModal();
      });
    }

    // Live Currency Conversion Hint Listeners
    const mAmount = document.getElementById('mTxnAmount');
    const mCurrency = document.getElementById('mTxnCurrency');
    const eAmount = document.getElementById('eTxnAmount');
    const eCurrency = document.getElementById('eTxnCurrency');

    if (mAmount) mAmount.addEventListener('input', () => this.updateTxnConversionHint('m'));
    if (mCurrency) mCurrency.addEventListener('change', () => this.updateTxnConversionHint('m'));
    if (eAmount) eAmount.addEventListener('input', () => this.updateTxnConversionHint('e'));
    if (eCurrency) eCurrency.addEventListener('change', () => this.updateTxnConversionHint('e'));

    const closeModal = () => {
      if (modal) modal.classList.remove('active');
      if (window.calendarEngine) window.calendarEngine.closeDatePicker();
      this.restoreModalFocus(modal);
    };
    if (closeBtn) closeBtn.addEventListener('click', closeModal);
    if (cancelBtn) cancelBtn.addEventListener('click', closeModal);

    if (form) {
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const type = document.getElementById('mTxnType').value;
        const amount = parseFloat(document.getElementById('mTxnAmount').value) || 0;
        const now = new Date();
        const defaultDate = now.toLocaleDateString('en-CA');
        const defaultTime = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
        const date = document.getElementById('mTxnDate').value || defaultDate;
        const time = document.getElementById('mTxnTime').value || defaultTime;
        const category = document.getElementById('mTxnCategory').value;
        const accountId = document.getElementById('mTxnAccount').value;
        const description = document.getElementById('mTxnDesc').value;
        const paymentMode = document.getElementById('mTxnMode')?.value || (type === 'income' ? 'Direct Credit' : 'Cash/Manual');
        const referenceNo = document.getElementById('mTxnRef')?.value || ('MANUAL_' + Date.now().toString().slice(-6));
        const notes = document.getElementById('mTxnNotes').value;

        if (amount <= 0 || !description) {
          alert('Please enter a valid amount and description.');
          return;
        }

        const active = window.profileManager?.getActiveProfile();
        const primaryCurrency = (active && active.currency) || 'INR';
        const currency = document.getElementById('mTxnCurrency')?.value || primaryCurrency;

        const newTxn = {
          id: 'txn_manual_' + Date.now(),
          date,
          time,
          amount,
          currency,
          type,
          category,
          needsReview: false,
          confidence: 'manual',
          identifier: description.toLowerCase(),
          description,
          rawNarration: 'Manual Entry: ' + description,
          accountId,
          paymentMode,
          referenceNo,
          sourceFile: 'Manual Entry',
          isDuplicate: false,
          notes,
          createdAt: new Date().toISOString()
        };

        await window.db.put('transactions', newTxn);

        // Learn the rule for future imports only; don't rewrite other existing transactions
        if (description.length > 2 && category !== 'Uncategorized') {
          await window.categorizer.learnRuleAndReclassify(description, category, type, false);
        }

        closeModal();
        form.reset();
        this.showToast('Transaction saved offline successfully!', 'success');
        await this.refreshAllViews();
      });
    }

    // Edit Transaction Modal Wiring
    const editModal = document.getElementById('editTxnModal');
    const closeEditBtn = document.getElementById('btnCloseEditTxnModal');
    const cancelEditBtn = document.getElementById('btnCancelEditTxn');
    const editForm = document.getElementById('editTxnForm');

    const closeEditModal = () => {
      if (editModal) editModal.classList.remove('active');
      if (window.calendarEngine) window.calendarEngine.closeDatePicker();
      this.restoreModalFocus(editModal);
    };
    if (closeEditBtn) closeEditBtn.addEventListener('click', closeEditModal);
    if (cancelEditBtn) cancelEditBtn.addEventListener('click', closeEditModal);

    if (editForm) {
      editForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('eTxnId').value;
        const txn = (window.db.getById ? await window.db.getById('transactions', id) : (window.db.get ? await window.db.get('transactions', id) : null)) || (await window.db.getAll('transactions')).find(t => t.id === id);
        if (!txn) {
          alert('Transaction not found.');
          return;
        }

        const type = document.getElementById('eTxnType').value;
        const amount = parseFloat(document.getElementById('eTxnAmount').value) || 0;
        const now = new Date();
        const defaultDate = now.toLocaleDateString('en-CA');
        const defaultTime = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
        const date = document.getElementById('eTxnDate').value || defaultDate;
        const time = document.getElementById('eTxnTime').value || defaultTime;
        const category = document.getElementById('eTxnCategory').value;
        const accountId = document.getElementById('eTxnAccount').value;
        const description = document.getElementById('eTxnDesc').value;
        const paymentMode = document.getElementById('eTxnMode').value || txn.paymentMode || 'Online';
        const referenceNo = document.getElementById('eTxnRef').value || txn.referenceNo || '';
        const notes = document.getElementById('eTxnNotes').value || '';

        if (amount <= 0 || !description) {
          alert('Please enter a valid amount and description.');
          return;
        }

        const active = window.profileManager?.getActiveProfile();
        const primaryCurrency = (active && active.currency) || 'INR';
        const currency = document.getElementById('eTxnCurrency')?.value || txn.currency || primaryCurrency;

        txn.type = type;
        txn.amount = amount;
        txn.currency = currency;
        txn.date = date;
        txn.time = time;
        txn.category = category;
        txn.accountId = accountId;
        txn.description = description;
        txn.paymentMode = paymentMode;
        txn.referenceNo = referenceNo;
        txn.notes = notes;
        txn.updatedAt = new Date().toISOString();
        txn.confidence = 'manual';

        await window.db.put('transactions', txn);

        if (description.length > 2 && category !== 'Uncategorized') {
          await window.categorizer.learnRuleAndReclassify(description, category, type, false);
        }

        closeEditModal();
        this.showToast('Transaction updated successfully!', 'success');
        await this.refreshAllViews();
      });
    }
  }

  setQuickDateTime(targetModal, preset) {
    const isEdit = targetModal === 'edit';
    const dateInput = document.getElementById(isEdit ? 'eTxnDate' : 'mTxnDate');
    const timeInput = document.getElementById(isEdit ? 'eTxnTime' : 'mTxnTime');

    if (!dateInput || !timeInput) return;

    const now = new Date();

    if (preset === 'now') {
      dateInput.value = now.toLocaleDateString('en-CA');
      timeInput.value = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
    } else if (preset === 'today') {
      dateInput.value = now.toLocaleDateString('en-CA');
    } else if (preset === 'yesterday') {
      const yesterday = new Date(now);
      yesterday.setDate(yesterday.getDate() - 1);
      dateInput.value = yesterday.toLocaleDateString('en-CA');
    }
  }

  async openManualTxnModal(accountId = null, prefillDate = null) {
    const modal = document.getElementById('manualTxnModal');
    if (!modal) return;
    await this.populateModalAccountOptions();
    if (accountId) {
      const accSelect = document.getElementById('mTxnAccount');
      if (accSelect) accSelect.value = accountId;
    }
    const modeSelect = document.getElementById('mTxnMode');
    if (modeSelect) modeSelect.value = 'UPI';

    const dateInput = document.getElementById('mTxnDate');
    if (dateInput) {
      if (prefillDate) {
        dateInput.value = prefillDate;
      } else if (!dateInput.value) {
        dateInput.value = new Date().toLocaleDateString('en-CA');
      }
    }

    const active = window.profileManager?.getActiveProfile();
    const primaryCurrency = (active && active.currency) || 'INR';
    const currSelect = document.getElementById('mTxnCurrency');
    if (currSelect) {
      currSelect.value = primaryCurrency;
    }
    this.updateTxnConversionHint('m');

    modal.classList.add('active');
    this.hydrateIcons();
    this.focusModal(modal);
  }

  openAddTxnModal(prefillDate = null) {
    this.openManualTxnModal(null, prefillDate);
  }

  async openEditTxnModal(txnId) {
    const txn = (window.db.getById ? await window.db.getById('transactions', txnId) : (window.db.get ? await window.db.get('transactions', txnId) : null)) || (await window.db.getAll('transactions')).find(t => t.id === txnId);
    if (!txn) {
      this.showToast('Transaction not found', 'error');
      return;
    }

    const accounts = await window.db.getAll('accounts');
    const categories = (await window.db.getAll('categories')).filter(c => c.name !== 'Uncategorized');

    const modal = document.getElementById('editTxnModal');
    const idInput = document.getElementById('eTxnId');
    const typeSelect = document.getElementById('eTxnType');
    const amountInput = document.getElementById('eTxnAmount');
    const dateInput = document.getElementById('eTxnDate');
    const timeInput = document.getElementById('eTxnTime');
    const accSelect = document.getElementById('eTxnAccount');
    const catSelect = document.getElementById('eTxnCategory');
    const descInput = document.getElementById('eTxnDesc');
    const modeSelect = document.getElementById('eTxnMode');
    const refInput = document.getElementById('eTxnRef');
    const notesInput = document.getElementById('eTxnNotes');

    if (accSelect) {
      if (accounts.length === 0) {
        accSelect.innerHTML = `
          <option value="default_bank">Primary Bank Account</option>
          <option value="default_cash">Cash / Wallet</option>
          <option value="default_card">Credit Card</option>
        `;
      } else {
        accSelect.innerHTML = accounts.map(a => `<option value="${a.id}">${this.escape(a.name)} (${this.escape(a.bankName || '')})</option>`).join('');
      }
      if (txn.accountId) {
        const exists = Array.from(accSelect.options).some(o => o.value === txn.accountId);
        if (!exists) {
          const opt = document.createElement('option');
          opt.value = txn.accountId;
          opt.textContent = txn.accountName || txn.accountId;
          accSelect.appendChild(opt);
        }
        accSelect.value = txn.accountId;
      }
    }

    if (catSelect) {
      catSelect.innerHTML = categories.map(c => `<option value="${this.escape(c.name)}">${this.escape(c.name)}</option>`).join('');
      if (txn.category) catSelect.value = txn.category;
    }

    if (idInput) idInput.value = txn.id;
    if (typeSelect) typeSelect.value = txn.type || 'expense';
    if (amountInput) amountInput.value = txn.amount;

    // Normalizing Date & Time
    let dVal = txn.date || '';
    let tVal = txn.time || '';
    if (dVal.includes('T')) {
      const parts = dVal.split('T');
      dVal = parts[0];
      if (!tVal && parts[1]) tVal = parts[1].slice(0, 5);
    } else if (dVal.includes(' ')) {
      const parts = dVal.split(' ');
      dVal = parts[0];
      if (!tVal && parts[1]) tVal = parts[1].slice(0, 5);
    }
    if (!tVal && txn.createdAt) {
      try {
        const cd = new Date(txn.createdAt);
        if (!isNaN(cd.getTime())) {
          tVal = String(cd.getHours()).padStart(2, '0') + ':' + String(cd.getMinutes()).padStart(2, '0');
        }
      } catch (e) {}
    }
    if (!tVal) tVal = '12:00';

    if (dateInput) dateInput.value = dVal;
    if (timeInput) timeInput.value = tVal;
    if (descInput) descInput.value = txn.description || '';
    
    // Select or preserve payment mode
    if (modeSelect) {
      const targetMode = (txn.paymentMode || 'UPI').trim();
      let matched = false;
      for (const opt of modeSelect.options) {
        if (opt.value.toLowerCase() === targetMode.toLowerCase() || opt.text.toLowerCase().includes(targetMode.toLowerCase())) {
          modeSelect.value = opt.value;
          matched = true;
          break;
        }
      }
      if (!matched && targetMode) {
        const opt = document.createElement('option');
        opt.value = targetMode;
        opt.textContent = targetMode;
        modeSelect.appendChild(opt);
        modeSelect.value = targetMode;
      }
    }

    if (refInput) refInput.value = txn.referenceNo || '';
    if (notesInput) notesInput.value = txn.notes || '';

    const active = window.profileManager?.getActiveProfile();
    const primaryCurrency = (active && active.currency) || 'INR';
    const currSelect = document.getElementById('eTxnCurrency');
    if (currSelect) {
      currSelect.value = txn.currency || primaryCurrency;
    }
    this.updateTxnConversionHint('e');

    if (modal) {
      modal.classList.add('active');
      this.hydrateIcons();
      this.focusModal(modal);
    }
  }

  async populateModalAccountOptions() {
    const accounts = await window.db.getAll('accounts');
    const categories = (await window.db.getAll('categories')).filter(c => c.name !== 'Uncategorized');

    const accSelect = document.getElementById('mTxnAccount');
    if (accSelect) {
      if (accounts.length === 0) {
        accSelect.innerHTML = `
          <option value="default_bank">Primary Bank Account</option>
          <option value="default_cash">Cash / Wallet</option>
          <option value="default_card">Credit Card</option>
        `;
      } else {
        accSelect.innerHTML = accounts.map(a => `<option value="${a.id}">${this.escape(a.name)} (${this.escape(a.bankName || '')})</option>`).join('');
      }
    }

    const catSelect = document.getElementById('mTxnCategory');
    if (catSelect) {
      catSelect.innerHTML = categories.map(c => `<option value="${this.escape(c.name)}">${this.escape(c.name)}</option>`).join('');
    }

    const dateInput = document.getElementById('mTxnDate');
    const timeInput = document.getElementById('mTxnTime');
    const now = new Date();
    if (dateInput) {
      dateInput.value = now.toLocaleDateString('en-CA');
    }
    if (timeInput) {
      timeInput.value = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
    }
  }

  // --- PROFILE CHOOSER (shown when no profile is active) ---
  showProfileChooser() {
    const screen = document.getElementById('profileChooserScreen');
    if (screen) {
      screen.style.display = 'flex';
      this.renderProfileChooserCards();
      this.renderChooserCurrencyCards();

      const chooserInput = document.getElementById('newProfileNameInput');
      if (chooserInput && !chooserInput.dataset.scrollBound) {
        chooserInput.dataset.scrollBound = '1';
        chooserInput.addEventListener('focus', () => {
          setTimeout(() => {
            chooserInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }, 300);
        });
      }
    }
  }

  renderChooserCurrencyCards() {
    const grid = document.getElementById('chooserCurrencyGrid');
    if (!grid) return;
    const currencies = window.CurrencyEngine ? window.CurrencyEngine.getSupportedCurrencies() : [];
    if (!this.chooserCurrency) this.chooserCurrency = 'INR';

    grid.innerHTML = currencies.map(c => `
      <div class="currency-backlight-card ${c.code === this.chooserCurrency ? 'selected' : ''}"
           role="radio"
           aria-checked="${c.code === this.chooserCurrency}"
           tabindex="0"
           onclick="app.selectChooserCurrency('${c.code}')"
           onkeydown="if(event.key==='Enter'||event.key===' ') app.selectChooserCurrency('${c.code}')"
           title="${c.name} (${c.code})">
        <div class="cbc-top-row">
          <span class="cbc-flag">${c.flag || ''}</span>
          <span class="cbc-badge" aria-hidden="true">
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
          </span>
        </div>
        <div class="cbc-symbol">${c.symbol}</div>
        <div class="cbc-code">${c.code}</div>
        <div class="cbc-name">${c.name.split(' ')[0]}</div>
      </div>
    `).join('');
  }

  selectChooserCurrency(code) {
    this.chooserCurrency = code;
    this.renderChooserCurrencyCards();
  }

  updateTxnConversionHint(prefix) {
    const hint = document.getElementById(`${prefix}TxnConversionHint`);
    const amountInput = document.getElementById(`${prefix}TxnAmount`);
    const currencySelect = document.getElementById(`${prefix}TxnCurrency`);
    if (!hint || !amountInput || !currencySelect) return;

    const amount = parseFloat(amountInput.value) || 0;
    const currency = currencySelect.value;
    const active = window.profileManager?.getActiveProfile();
    const primaryCurrency = (active && active.currency) || 'INR';

    if (currency !== primaryCurrency && amount > 0 && window.CurrencyEngine) {
      const converted = window.CurrencyEngine.convert(amount, currency, primaryCurrency);
      const rate = window.CurrencyEngine.getExchangeRate(currency, primaryCurrency);
      hint.innerHTML = `<span>≈ ${window.CurrencyEngine.format(converted, primaryCurrency)}</span> <span style="opacity:0.65; margin-left:auto;">1 ${currency} = ${rate.toFixed(4)} ${primaryCurrency}</span>`;
      hint.style.display = 'flex';
    } else {
      hint.style.display = 'none';
      hint.innerHTML = '';
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

    this.hydrateIcons();
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
      window.profileManager.createProfile(name, this.chooserCurrency || 'INR'); // triggers reload
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

  openProfileModal() {
    const modal = document.getElementById('profileManagerModal');
    if (!modal) return;
    this.renderProfileManagerList();
    this.renderManagerCurrencyCards();
    this.hydrateIcons();
    requestAnimationFrame(() => {
      modal.classList.add('active');
      this.focusModal(modal);
    });
  }

  renderManagerCurrencyCards() {
    const grid = document.getElementById('managerCurrencyGrid');
    if (!grid) return;
    const active = window.profileManager.getActiveProfile();
    const activeCurrency = (active && active.currency) || 'INR';
    const currencies = window.CurrencyEngine ? window.CurrencyEngine.getSupportedCurrencies() : [];

    grid.innerHTML = currencies.map(c => `
      <div class="currency-backlight-card ${c.code === activeCurrency ? 'selected' : ''}"
           role="radio"
           aria-checked="${c.code === activeCurrency}"
           tabindex="0"
           onclick="app.setActiveProfileCurrency('${c.code}')"
           onkeydown="if(event.key==='Enter'||event.key===' ') app.setActiveProfileCurrency('${c.code}')"
           title="${c.name} (${c.code})">
        <div class="cbc-top-row">
          <span class="cbc-flag">${c.flag || ''}</span>
          <span class="cbc-badge" aria-hidden="true">
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
          </span>
        </div>
        <div class="cbc-symbol">${c.symbol}</div>
        <div class="cbc-code">${c.code}</div>
        <div class="cbc-name">${c.name.split(' ')[0]}</div>
      </div>
    `).join('');
  }

  setActiveProfileCurrency(code) {
    const active = window.profileManager.getActiveProfile();
    if (!active) return;
    if (active.currency === code) return;
    window.profileManager.updateProfileCurrency(active.id, code);
    this.renderManagerCurrencyCards();
    this.renderProfileManagerList();
    this.renderDashboard();
    this.renderTransactionsTable();
    this.renderAccounts();
    this.renderReportsView();
    this.showToast(`Primary currency changed to ${code} (${window.CurrencyEngine.getSymbol(code)})`, 'success');
  }

  closeProfileModal() {
    const modal = document.getElementById('profileManagerModal');
    if (!modal) return;
    modal.classList.remove('active');
    this.restoreModalFocus(modal);
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
            <button class="btn btn-sm btn-ghost" style="color:var(--text-muted);" onclick="app.startEditProfile('${p.id}')" title="Rename">
              <i data-lucide="pencil" style="width:14px;height:14px;"></i>
            </button>
            <button class="btn btn-sm btn-ghost" style="color:#ef4444;" onclick="app.deleteProfileFromManager('${p.id}')" title="Delete">
              <i data-lucide="trash-2" style="width:14px;height:14px;"></i>
            </button>
          </div>
        </div>
      `;
    }).join('');

    this.hydrateIcons();
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
      const activeCur = window.profileManager?.getActiveProfile()?.currency || 'INR';
      window.profileManager.createProfile(name, activeCur); // triggers reload
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
  startEditProfile(id) {
    const nameEl = document.getElementById(`profile-name-display-${id}`);
    const profile = window.profileManager.getProfiles().find(p => p.id === id);
    if (!nameEl || !profile) return;
    const currentName = profile.name;

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
          onkeydown="if(event.key==='Enter') app.saveProfileRename('${id}'); if(event.key==='Escape') app.cancelEditProfile('${id}')"
        >
        ${activeBadge}
      </div>
      <div id="profile-rename-error-${id}" style="display:none; font-size:0.75rem; color:#ef4444; margin-top:2px;"></div>
      <div style="display:flex; gap:4px; margin-top:4px;">
        <button class="btn btn-sm btn-primary" style="font-size:0.75rem; padding:3px 10px;" onclick="app.saveProfileRename('${id}')">
          <i data-lucide="check" style="width:12px;height:12px;"></i> Save
        </button>
        <button class="btn btn-sm btn-ghost" style="font-size:0.75rem; padding:3px 8px;" onclick="app.cancelEditProfile('${id}')">
          Cancel
        </button>
      </div>
    `;

    this.hydrateIcons();

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

      this.hydrateIcons();
    } catch (err) {
      if (errorEl) { errorEl.textContent = err.message; errorEl.style.display = 'block'; }
    }
  }

  /**
   * Cancels editing and restores the original name text.
   */
  cancelEditProfile(id) {
    const nameEl = document.getElementById(`profile-name-display-${id}`);
    const profile = window.profileManager.getProfiles().find(p => p.id === id);
    if (!nameEl || !profile) return;
    const active = window.profileManager.getActiveProfile();
    const isActive = active && active.id === id;
    nameEl.innerHTML = `
      ${this.escape(profile.name)}
      ${isActive ? '<span class="profile-active-badge">Active</span>' : ''}
    `;
    this.hydrateIcons();
  }

  bindProfileModalEvents() {
    // Close on overlay click
    const modal = document.getElementById('profileManagerModal');
    if (modal) {
      modal.addEventListener('click', (e) => {
        if (e.target === modal) this.closeProfileModal();
      });
    }
    // Enter key & focus scroll on new profile input
    const input = document.getElementById('newProfileNameManagerInput');
    if (input) {
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') this.createProfileFromManager();
      });
      input.addEventListener('focus', () => {
        setTimeout(() => {
          input.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }, 300);
      });
    }
    // Enter key & focus scroll on chooser input
    const chooserInput = document.getElementById('newProfileNameInput');
    if (chooserInput) {
      chooserInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') this.createProfileFromChooser();
      });
      chooserInput.addEventListener('focus', () => {
        setTimeout(() => {
          chooserInput.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }, 300);
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
    this.hydrateIcons();
  }

  // --- TOAST NOTIFICATIONS (Disabled per user request) ---
  showToast(message, type = 'info') {
    // Info/success popups are intentionally disabled; errors must never be silent.
    if (type !== 'error') return;
    let region = document.getElementById('appErrorToast');
    if (!region) {
      region = document.createElement('div');
      region.id = 'appErrorToast';
      region.className = 'app-error-toast';
      region.setAttribute('role', 'alert');
      document.body.appendChild(region);
    }
    region.textContent = message;
    region.classList.add('show');
    clearTimeout(this._errorToastTimer);
    this._errorToastTimer = setTimeout(() => region.classList.remove('show'), 6000);
  }

  async restoreBackupFromInput(input) {
    const file = input.files && input.files[0];
    input.value = '';
    if (!file) return;
    const status = document.getElementById('restoreBackupStatus');
    const say = (msg, ok) => {
      if (!status) return;
      status.textContent = msg;
      status.className = 'restore-status ' + (ok ? 'ok' : 'error');
    };
    if (!window.confirm('Restoring replaces ALL current data in this profile (transactions, accounts, rules, budgets) with the contents of the backup. This can’t be undone. Continue?')) {
      return;
    }
    try {
      const counts = await window.exportEngine.restoreBackupFromFile(file);
      say(`Restored ${counts.transactions} transactions, ${counts.accounts} accounts and ${counts.rules} learned rules.`, true);
      await this.refreshAllViews();
    } catch (err) {
      say(err.message || 'The backup could not be restored.', false);
    }
  }

  // Blocking failure (e.g. IndexedDB unavailable): say what happened, that data is safe, and how to recover.
  showFatalError(err) {
    const existing = document.getElementById('appFatalError');
    if (existing) existing.remove();

    const box = document.createElement('div');
    box.id = 'appFatalError';
    box.className = 'fatal-error-banner';
    box.setAttribute('role', 'alert');

    const title = document.createElement('h2');
    title.textContent = "Money Tracker couldn't open its local storage";
    const body = document.createElement('p');
    body.textContent = 'Your data lives in this browser’s local database, and the browser refused to open it. '
      + 'This usually happens in a private/incognito window, when device storage is full, or while another tab is updating the app. '
      + 'Nothing has been deleted.';
    const detail = document.createElement('p');
    detail.className = 'fatal-error-detail';
    detail.textContent = 'Details: ' + ((err && err.message) || String(err));
    const actions = document.createElement('div');
    actions.className = 'fatal-error-actions';
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.className = 'btn btn-primary btn-sm';
    retry.textContent = 'Try again';
    retry.addEventListener('click', () => window.location.reload());
    actions.appendChild(retry);

    box.append(title, body, detail, actions);
    document.body.prepend(box);
    retry.focus();
  }

  // ==========================================
  // CURRENCY INFORMATION & EXCHANGE RATES HUB
  // ==========================================

  async renderCurrencyView() {
    const grid = document.getElementById('currencyRatesGrid');
    if (!grid) return;

    const engine = window.CurrencyEngine;
    if (!engine) return;

    const currencies = engine.getSupportedCurrencies();
    const activeProfile = window.profileManager ? window.profileManager.getActiveProfile() : null;
    const primaryCurrency = activeProfile ? (activeProfile.currency || 'INR') : 'INR';

    // Update Hero sync state & base badge
    const tsLabel = document.getElementById('currencyLastUpdatedLabel');
    if (tsLabel) {
      tsLabel.textContent = `Last API Sync: ${engine.getLastFetchTimestamp()}`;
    }

    const baseBadge = document.getElementById('currencyBaseBadge');
    if (baseBadge) {
      baseBadge.textContent = `Base: ${primaryCurrency}`;
    }

    const dot = document.getElementById('currencyPulseDot');
    if (dot) {
      const isOffline = engine.getLastFetchTimestamp().toLowerCase().includes('offline');
      dot.classList.toggle('offline', isOffline);
    }

    const resetAllBtn = document.getElementById('btnResetAllRates');
    if (resetAllBtn) {
      resetAllBtn.style.display = engine.hasAnyManualOverride() ? 'inline-flex' : 'none';
    }

    // Filter out the primary base currency (e.g. if EUR is default, EUR is omitted as 1 EUR = 1 EUR)
    const displayCurrencies = currencies.filter(curr => curr.code.toUpperCase() !== primaryCurrency.toUpperCase());

    const formatRate = (r) => {
      if (r >= 100) return r.toFixed(2);
      if (r >= 1) return r.toFixed(4);
      return r.toFixed(6);
    };

    let cardsHtml = '';
    for (const curr of displayCurrencies) {
      const currentRate = engine.getRateAgainstBase(curr.code, primaryCurrency);
      const apiRate = engine.getApiRateAgainstBase(curr.code, primaryCurrency);
      const isOverridden = engine.isManualOverride(curr.code);
      const inverseRate = currentRate > 0 ? (1.0 / currentRate) : 0;

      const flag = curr.code === 'INR' ? '🇮🇳' :
                   curr.code === 'USD' ? '🇺🇸' :
                   curr.code === 'EUR' ? '🇪🇺' :
                   curr.code === 'GBP' ? '🇬🇧' :
                   curr.code === 'CHF' ? '🇨🇭' :
                   curr.code === 'JPY' ? '🇯🇵' : '🌐';

      cardsHtml += `
        <div class="currency-rate-card" data-code="${curr.code}">
          <div class="currency-card-header">
            <div class="currency-card-title-group">
              <div class="currency-card-flag-circle">${flag}</div>
              <div>
                <div style="display:flex; align-items:center;">
                  <span class="currency-card-code">${curr.code}</span>
                  <span class="currency-card-symbol">(${curr.symbol})</span>
                </div>
                <div class="currency-card-name">${curr.name}</div>
              </div>
            </div>
            <div class="currency-card-badges">
              ${isOverridden ? '<span class="currency-badge-pill custom">Custom</span>' : ''}
            </div>
          </div>

          <div class="currency-metrics-box">
            <div class="currency-metric-row">
              <span class="currency-metric-label">Rate vs ${primaryCurrency}</span>
              <span class="currency-metric-value">1 ${primaryCurrency} = ${formatRate(currentRate)} ${curr.code}</span>
            </div>
            <div class="currency-metric-row">
              <span class="currency-metric-label">Inverse Equivalent</span>
              <span class="currency-metric-value" style="color:var(--color-primary);">1 ${curr.code} ≈ ${engine.format(inverseRate, primaryCurrency)}</span>
            </div>
          </div>

          ${isOverridden ? `
            <div class="currency-api-hint">
              API Baseline: 1 ${primaryCurrency} = ${formatRate(apiRate)} ${curr.code}
            </div>
          ` : ''}

          <div class="currency-card-actions">
            ${isOverridden ? `
              <button type="button" class="btn btn-ghost btn-sm" onclick="app.resetCurrencyRate('${curr.code}')" style="color:#f59e0b; font-size:0.75rem;">
                <i data-lucide="rotate-ccw" style="width:12px; height:12px;"></i> Reset to API
              </button>
            ` : ''}
            <button type="button" class="btn btn-secondary btn-sm" onclick="app.openEditCurrencyModal('${curr.code}')" style="font-size:0.78rem;">
              <i data-lucide="edit-3" style="width:12px; height:12px;"></i> Edit Rate
            </button>
          </div>
        </div>
      `;
    }

    grid.innerHTML = cardsHtml;
    this.hydrateIcons(grid);
  }

  async forceSyncCurrencyRates() {
    const icon = document.getElementById('currencySyncIcon');
    if (icon) icon.classList.add('currency-spinning');

    const success = await window.CurrencyEngine.forceFetchRates();

    if (icon) icon.classList.remove('currency-spinning');

    if (success) {
      await this.refreshAllViews();
    } else {
      alert('Could not fetch latest rates from the exchange rate API. Please check your internet connection.');
    }
  }

  openEditCurrencyModal(code) {
    const engine = window.CurrencyEngine;
    const curr = engine.getCurrency(code);
    if (!curr) return;

    const modal = document.getElementById('editCurrencyModal');
    if (!modal) return;

    const activeProfile = window.profileManager ? window.profileManager.getActiveProfile() : null;
    const primaryCurrency = activeProfile ? (activeProfile.currency || 'INR') : 'INR';

    const flag = curr.code === 'INR' ? '🇮🇳' :
                 curr.code === 'USD' ? '🇺🇸' :
                 curr.code === 'EUR' ? '🇪🇺' :
                 curr.code === 'GBP' ? '🇬🇧' :
                 curr.code === 'CHF' ? '🇨🇭' :
                 curr.code === 'JPY' ? '🇯🇵' : '🌐';

    const currentRate = engine.getRateAgainstBase(curr.code, primaryCurrency);

    const formatRate = (r) => {
      if (r >= 100) return r.toFixed(2);
      if (r >= 1) return r.toFixed(4);
      return r.toFixed(6);
    };

    document.getElementById('editCurrencyFlagCircle').textContent = flag;
    document.getElementById('editCurrencyModalTitle').textContent = `Edit ${curr.name} Rate`;
    const subtitleEl = document.getElementById('editCurrencyModalSubtitle');
    if (subtitleEl) subtitleEl.textContent = `1 ${primaryCurrency} conversion rate`;

    const labelEl = document.getElementById('editCurrencyRateLabel');
    if (labelEl) labelEl.textContent = `Rate for 1 ${primaryCurrency}`;

    const prefixEl = document.getElementById('editCurrencyBasePrefix');
    if (prefixEl) prefixEl.textContent = `1 ${primaryCurrency} =`;

    document.getElementById('editCurrencyCodeInput').value = curr.code;
    document.getElementById('editCurrencyCodeSuffix').textContent = curr.code;
    document.getElementById('editCurrencyRateInput').value = formatRate(currentRate);
    document.getElementById('editCurrencyRateInput').placeholder = `e.g. ${formatRate(currentRate)}`;
    document.getElementById('editCurrencyError').style.display = 'none';

    this.onEditCurrencyInputChange();

    modal.style.display = 'flex';
    document.getElementById('editCurrencyRateInput').focus();
    this.hydrateIcons(modal);
  }

  closeEditCurrencyModal() {
    const modal = document.getElementById('editCurrencyModal');
    if (modal) modal.style.display = 'none';
  }

  onEditCurrencyInputChange() {
    const code = document.getElementById('editCurrencyCodeInput').value;
    const inputVal = parseFloat(document.getElementById('editCurrencyRateInput').value);
    const previewBox = document.getElementById('editCurrencyPreviewBox');
    const previewText = document.getElementById('editCurrencyPreviewText');

    const activeProfile = window.profileManager ? window.profileManager.getActiveProfile() : null;
    const primaryCurrency = activeProfile ? (activeProfile.currency || 'INR') : 'INR';

    if (isNaN(inputVal) || inputVal <= 0) {
      if (previewBox) previewBox.style.display = 'none';
      return;
    }

    if (previewBox) previewBox.style.display = 'block';

    const inverseVal = 1.0 / inputVal;

    if (previewText) {
      previewText.textContent = `1 ${code} ≈ ${window.CurrencyEngine.format(inverseVal, primaryCurrency)}`;
    }
  }

  async saveManualCurrencyRate() {
    const code = document.getElementById('editCurrencyCodeInput').value;
    const inputVal = parseFloat(document.getElementById('editCurrencyRateInput').value);
    const errEl = document.getElementById('editCurrencyError');

    const activeProfile = window.profileManager ? window.profileManager.getActiveProfile() : null;
    const primaryCurrency = activeProfile ? (activeProfile.currency || 'INR') : 'INR';

    if (isNaN(inputVal) || inputVal <= 0) {
      if (errEl) {
        errEl.textContent = 'Please enter a valid positive conversion rate.';
        errEl.style.display = 'block';
      }
      return;
    }

    window.CurrencyEngine.updateManualRateAgainstBase(code, primaryCurrency, inputVal);
    this.closeEditCurrencyModal();
    await this.refreshAllViews();
  }

  async resetCurrencyRate(code) {
    const activeProfile = window.profileManager ? window.profileManager.getActiveProfile() : null;
    const primaryCurrency = activeProfile ? (activeProfile.currency || 'INR') : 'INR';
    window.CurrencyEngine.resetRateToApiAgainstBase(code, primaryCurrency);
    await this.refreshAllViews();
  }

  async resetAllCurrencyRates() {
    window.CurrencyEngine.resetAllRatesToApi();
    await this.refreshAllViews();
  }
}

// Global App Instance
window.app = new App();
window.hydrateIcons = (root) => window.app?.hydrateIcons(root);

// Boot on DOM Ready
document.addEventListener('DOMContentLoaded', () => {
  window.app.init();
});
