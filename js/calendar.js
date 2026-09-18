/**
 * ============================================================================
 * MONEY TRACKER - FINANCIAL CALENDAR & DATEPICKER ENGINE
 * ============================================================================
 * Adapted & enhanced from trananhtuat/js-calendar:
 * - Dynamic leap year & days calculation
 * - Month picker overlay with scale animation
 * - Year stepper controls
 * - Themed DatePicker popover for Add/Edit transaction inputs
 * - Monthly Financial Calendar view with expense/income badges & day ledger
 * - Seamless support for both Dark OLED and Light luxury themes
 * ============================================================================
 */

class MoneyTrackerCalendar {
  constructor() {
    this.monthNames = [
      'January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'
    ];
    this.monthNamesShort = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
    ];
    this.weekDays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

    // Active popover state
    this.activePopover = null;
    this.activeTargetInput = null;

    // Financial calendar state
    this.finCurrentYear = new Date().getFullYear();
    this.finCurrentMonth = new Date().getMonth();
    this.finSelectedDate = null; // 'YYYY-MM-DD'

    // Bind outside clicks
    this.handleOutsideClick = this.handleOutsideClick.bind(this);
    this.handleKeyDown = this.handleKeyDown.bind(this);
    document.addEventListener('click', this.handleOutsideClick, true);
    document.addEventListener('keydown', this.handleKeyDown);
  }

  // --- Date Math Helpers (adapted from trananhtuat/js-calendar) ---
  isLeapYear(year) {
    return (year % 4 === 0 && year % 100 !== 0) || (year % 400 === 0);
  }

  getDaysInMonth(year, month) {
    const daysOfMonth = [31, this.isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
    return daysOfMonth[month];
  }

  formatDate(year, month, day) {
    const mm = String(month + 1).padStart(2, '0');
    const dd = String(day).padStart(2, '0');
    return `${year}-${mm}-${dd}`;
  }

  parseDate(dateStr) {
    if (!dateStr || typeof dateStr !== 'string') return null;
    const parts = dateStr.split('-');
    if (parts.length !== 3) return null;
    const y = parseInt(parts[0], 10);
    const m = parseInt(parts[1], 10) - 1;
    const d = parseInt(parts[2], 10);
    if (isNaN(y) || isNaN(m) || isNaN(d)) return null;
    return { year: y, month: m, day: d };
  }

  // ==========================================================================
  // PART 1: FLOATING THEMED DATEPICKER POPOVER FOR INPUTS
  // ==========================================================================

  /**
   * Opens the custom calendar popover anchored to a target <input type="date">
   */
  openDatePicker(inputElement, onSelectCallback = null) {
    if (!inputElement) return;

    const now = Date.now();
    if (this._lastOpenTime && (now - this._lastOpenTime < 250) && this.activeTargetInput === inputElement) {
      return;
    }
    this._lastOpenTime = now;

    // If popover is already open for this input, close it
    if (this.activePopover && this.activeTargetInput === inputElement) {
      this.closeDatePicker();
      return;
    }

    this.closeDatePicker();
    if (window.timePickerEngine && typeof window.timePickerEngine.close === 'function') {
      window.timePickerEngine.close();
    }

    this.activeTargetInput = inputElement;
    this.onDateSelect = onSelectCallback;

    // Read current value or fallback to today
    let initYear = new Date().getFullYear();
    let initMonth = new Date().getMonth();
    let selectedDateStr = inputElement.value || '';

    const parsed = this.parseDate(selectedDateStr);
    if (parsed) {
      initYear = parsed.year;
      initMonth = parsed.month;
    }

    // Create popover container
    const popover = document.createElement('div');
    popover.className = 'mt-calendar-popover';
    popover.id = 'mtCalendarPopover';
    popover.setAttribute('role', 'dialog');
    popover.setAttribute('aria-modal', 'true');
    popover.setAttribute('aria-label', 'Choose date');
    popover.setAttribute('tabindex', '-1');

    this.activePopover = popover;
    this.popoverYear = initYear;
    this.popoverMonth = initMonth;
    this.popoverSelectedDate = selectedDateStr;
    this.focusReturnEl = inputElement;

    // Render calendar into popover
    this.renderPopoverCalendar();

    document.body.appendChild(popover);
    this.positionPopover(inputElement, popover);
    popover.focus();
  }

  positionPopover(target, popover) {
    const rect = target.getBoundingClientRect();
    const popoverWidth = 320;
    const padding = 12;

    let left = rect.left;
    if (left + popoverWidth > window.innerWidth - padding) {
      left = window.innerWidth - popoverWidth - padding;
    }
    if (left < padding) left = padding;

    let top = rect.bottom + 8;
    const popoverHeight = 380;
    if (top + popoverHeight > window.innerHeight - padding) {
      top = rect.top - popoverHeight - 8;
      if (top < padding) top = padding;
    }

    popover.style.left = `${Math.round(left)}px`;
    popover.style.top = `${Math.round(top)}px`;
  }

  renderPopoverCalendar() {
    if (!this.activePopover) return;

    const year = this.popoverYear;
    const month = this.popoverMonth;
    const selectedDate = this.popoverSelectedDate;
    const today = new Date();
    const todayStr = this.formatDate(today.getFullYear(), today.getMonth(), today.getDate());

    const daysCount = this.getDaysInMonth(year, month);
    const firstDayIndex = new Date(year, month, 1).getDay(); // 0 = Sun, 1 = Mon ...

    let html = `
      <div class="mt-calendar mt-calendar-compact">
        <!-- Header -->
        <div class="mt-calendar-header">
          <button type="button" class="mt-month-picker-btn" id="mtPopoverMonthPickerBtn" title="Choose Month">
            <span>${this.monthNames[month]}</span>
            <i data-lucide="chevron-down" style="width:14px;height:14px;margin-left:4px;"></i>
          </button>
          <div class="mt-year-stepper">
            <button type="button" class="mt-year-btn" id="mtPopoverPrevYear" title="Previous Year">
              <i data-lucide="chevron-left" style="width:14px;height:14px;"></i>
            </button>
            <button type="button" class="mt-year-display" id="mtPopoverYearDisplay" title="Click to type a year">${year}</button>
            <button type="button" class="mt-year-btn" id="mtPopoverNextYear" title="Next Year">
              <i data-lucide="chevron-right" style="width:14px;height:14px;"></i>
            </button>
          </div>
        </div>

        <!-- Weekday Headers -->
        <div class="mt-calendar-weekdays">
          ${this.weekDays.map(d => `<div>${d}</div>`).join('')}
        </div>

        <!-- Days Grid -->
        <div class="mt-calendar-days" role="grid" aria-label="${this.monthNames[month]} ${year}">
    `;

    // Empty lead cells
    for (let i = 0; i < firstDayIndex; i++) {
      html += `<div class="mt-day-cell empty" role="presentation"></div>`;
    }

    // Roving tabindex: focus the selected day if visible this month, else today if visible, else day 1.
    const parsedSelected = this.parseDate(selectedDate);
    let focusDay = 1;
    if (parsedSelected && parsedSelected.year === year && parsedSelected.month === month) {
      focusDay = parsedSelected.day;
    } else if (todayStr.startsWith(this.formatDate(year, month, 1).slice(0, 7))) {
      focusDay = today.getDate();
    }

    // Day cells
    for (let d = 1; d <= daysCount; d++) {
      const dateStr = this.formatDate(year, month, d);
      const isToday = (dateStr === todayStr);
      const isSelected = (dateStr === selectedDate);
      const isTabbable = (d === focusDay);

      let classes = ['mt-day-cell'];
      if (isToday) classes.push('curr-date');
      if (isSelected) classes.push('selected-date');

      html += `
        <div class="${classes.join(' ')}" data-date="${dateStr}" role="gridcell" tabindex="${isTabbable ? '0' : '-1'}"
          aria-selected="${isSelected}" aria-label="${this.monthNames[month]} ${d}, ${year}${isToday ? ' (Today)' : ''}">
          <span class="mt-day-number">${d}</span>
        </div>
      `;
    }

    html += `
        </div>

        <!-- Month Picker Overlay (animated scale from trananhtuat/js-calendar) -->
        <div class="mt-month-list-overlay" id="mtPopoverMonthOverlay">
          <div class="mt-month-list-grid">
            ${this.monthNamesShort.map((mName, idx) => `
              <div class="mt-month-item ${idx === month ? 'selected' : ''}" data-month="${idx}" role="button" tabindex="0" aria-label="${this.monthNames[idx]}">
                ${mName}
              </div>
            `).join('')}
          </div>
        </div>

        <!-- Popover Footer Quick Buttons -->
        <div class="mt-calendar-footer">
          <div class="mt-quick-shortcuts">
            <button type="button" class="mt-shortcut-btn" id="mtPopoverQuickToday">Today</button>
            <button type="button" class="mt-shortcut-btn" id="mtPopoverQuickYesterday">Yesterday</button>
            <button type="button" class="mt-shortcut-btn" id="mtPopoverClear">Clear</button>
          </div>
          <button type="button" class="mt-shortcut-btn close" id="mtPopoverCloseBtn">Close</button>
        </div>
      </div>
    `;

    this.activePopover.innerHTML = html;

    if (window.lucide) {
      lucide.createIcons({ root: this.activePopover });
    }

    // Bind Popover events
    this.bindPopoverEvents();
  }

  bindPopoverEvents() {
    if (!this.activePopover) return;

    // Month picker button toggles month overlay
    const monthPickerBtn = this.activePopover.querySelector('#mtPopoverMonthPickerBtn');
    const monthOverlay = this.activePopover.querySelector('#mtPopoverMonthOverlay');
    if (monthPickerBtn && monthOverlay) {
      monthPickerBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        monthOverlay.classList.toggle('show');
      });
    }

    // Month overlay items
    const monthItems = this.activePopover.querySelectorAll('.mt-month-item');
    monthItems.forEach(item => {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        const m = parseInt(item.getAttribute('data-month'), 10);
        this.popoverMonth = m;
        this.renderPopoverCalendar();
      });
      item.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          e.stopPropagation();
          item.click();
        }
      });
    });

    // Year stepper
    const prevYearBtn = this.activePopover.querySelector('#mtPopoverPrevYear');
    const nextYearBtn = this.activePopover.querySelector('#mtPopoverNextYear');
    if (prevYearBtn) {
      prevYearBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.popoverYear--;
        this.renderPopoverCalendar();
      });
    }
    if (nextYearBtn) {
      nextYearBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.popoverYear++;
        this.renderPopoverCalendar();
      });
    }

    // Click the year to type one directly, instead of stepping one year at a time
    const yearDisplay = this.activePopover.querySelector('#mtPopoverYearDisplay');
    if (yearDisplay) {
      yearDisplay.addEventListener('click', (e) => {
        e.stopPropagation();
        const input = document.createElement('input');
        input.type = 'number';
        input.className = 'mt-year-input';
        input.value = this.popoverYear;
        yearDisplay.replaceWith(input);
        input.focus();
        input.select();

        const commit = () => {
          const y = parseInt(input.value, 10);
          if (!isNaN(y) && y >= 1000 && y <= 9999) {
            this.popoverYear = y;
          }
          this.renderPopoverCalendar();
        };

        input.addEventListener('keydown', (ke) => {
          ke.stopPropagation();
          if (ke.key === 'Enter') {
            ke.preventDefault();
            commit();
          } else if (ke.key === 'Escape') {
            ke.preventDefault();
            this.renderPopoverCalendar();
          }
        });
        input.addEventListener('blur', commit);
      });
    }

    // Day selection (click + full keyboard support with roving tabindex)
    const dayCells = this.activePopover.querySelectorAll('.mt-day-cell:not(.empty)');
    dayCells.forEach((cell, idx) => {
      cell.addEventListener('click', (e) => {
        e.stopPropagation();
        const pickedDate = cell.getAttribute('data-date');
        this.selectDate(pickedDate);
      });

      cell.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          e.stopPropagation();
          this.selectDate(cell.getAttribute('data-date'));
          return;
        }

        let targetIdx = null;
        if (e.key === 'ArrowRight') targetIdx = idx + 1;
        else if (e.key === 'ArrowLeft') targetIdx = idx - 1;
        else if (e.key === 'ArrowDown') targetIdx = idx + 7;
        else if (e.key === 'ArrowUp') targetIdx = idx - 7;
        else if (e.key === 'Home') targetIdx = 0;
        else if (e.key === 'End') targetIdx = dayCells.length - 1;

        if (targetIdx !== null && targetIdx >= 0 && targetIdx < dayCells.length) {
          e.preventDefault();
          e.stopPropagation();
          cell.setAttribute('tabindex', '-1');
          const targetCell = dayCells[targetIdx];
          targetCell.setAttribute('tabindex', '0');
          targetCell.focus();
        }
      });
    });

    // Quick shortcuts
    const quickTodayBtn = this.activePopover.querySelector('#mtPopoverQuickToday');
    if (quickTodayBtn) {
      quickTodayBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const t = new Date();
        const todayStr = this.formatDate(t.getFullYear(), t.getMonth(), t.getDate());
        this.selectDate(todayStr);
      });
    }

    const clearBtn = this.activePopover.querySelector('#mtPopoverClear');
    if (clearBtn) {
      clearBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.clearDate();
      });
    }

    const quickYestBtn = this.activePopover.querySelector('#mtPopoverQuickYesterday');
    if (quickYestBtn) {
      quickYestBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const y = new Date();
        y.setDate(y.getDate() - 1);
        const yestStr = this.formatDate(y.getFullYear(), y.getMonth(), y.getDate());
        this.selectDate(yestStr);
      });
    }

    const closeBtn = this.activePopover.querySelector('#mtPopoverCloseBtn');
    if (closeBtn) {
      closeBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        this.closeDatePicker();
      });
    }
  }

  selectDate(dateStr) {
    if (!this.activeTargetInput) return;

    this.activeTargetInput.value = dateStr;
    // Dispatch standard input and change events so listeners trigger
    this.activeTargetInput.dispatchEvent(new Event('input', { bubbles: true }));
    this.activeTargetInput.dispatchEvent(new Event('change', { bubbles: true }));

    if (typeof this.onDateSelect === 'function') {
      this.onDateSelect(dateStr);
    }

    this.closeDatePicker();
  }

  clearDate() {
    if (!this.activeTargetInput) return;

    this.activeTargetInput.value = '';
    this.activeTargetInput.dispatchEvent(new Event('input', { bubbles: true }));
    this.activeTargetInput.dispatchEvent(new Event('change', { bubbles: true }));

    if (typeof this.onDateSelect === 'function') {
      this.onDateSelect('');
    }

    this.closeDatePicker();
  }

  closeDatePicker() {
    if (this.activePopover) {
      this.activePopover.remove();
      this.activePopover = null;
    }
    this.activeTargetInput = null;
    this.onDateSelect = null;
    if (this.focusReturnEl && typeof this.focusReturnEl.focus === 'function') {
      this.focusReturnEl.focus();
    }
    this.focusReturnEl = null;
  }

  handleOutsideClick(e) {
    if (!this.activePopover) return;
    if (this.activePopover.contains(e.target)) return;
    if (this.activeTargetInput && this.activeTargetInput.contains(e.target)) return;
    if (e.target.closest && (e.target.closest('.datetime-input-box') || e.target.closest('.mt-calendar-launcher-btn'))) return;

    this.closeDatePicker();
  }

  handleKeyDown(e) {
    if (e.key === 'Escape' && this.activePopover) {
      this.closeDatePicker();
    }
  }

  // ==========================================================================
  // PART 2: MONTHLY FINANCIAL CALENDAR VIEW (FOR TRANSACTIONS TAB)
  // ==========================================================================

  /**
   * Renders the full interactive financial calendar into a container
   */
  renderFinancialCalendar(container, transactions = [], onDateSelected = null) {
    if (!container) return;

    const year = this.finCurrentYear;
    const month = this.finCurrentMonth;
    const today = new Date();
    const todayStr = this.formatDate(today.getFullYear(), today.getMonth(), today.getDate());

    const daysCount = this.getDaysInMonth(year, month);
    const firstDayIndex = new Date(year, month, 1).getDay();

    // Group transactions by date for this month
    const monthPrefix = `${year}-${String(month + 1).padStart(2, '0')}`;
    const txnsByDate = {};
    let monthTotalIncome = 0;
    let monthTotalExpense = 0;

    transactions.forEach(t => {
      if (t.date && t.date.startsWith(monthPrefix)) {
        if (!txnsByDate[t.date]) {
          txnsByDate[t.date] = { income: 0, expense: 0, count: 0, items: [] };
        }
        txnsByDate[t.date].count++;
        txnsByDate[t.date].items.push(t);

        const amt = parseFloat(t.amount) || 0;
        if (t.type === 'income') {
          txnsByDate[t.date].income += amt;
          monthTotalIncome += amt;
        } else if (t.type === 'expense') {
          txnsByDate[t.date].expense += amt;
          monthTotalExpense += amt;
        }
      }
    });

    const netCashflow = monthTotalIncome - monthTotalExpense;

    let html = `
      <div class="mt-financial-calendar-wrapper">
        
        <!-- Monthly Financial Header -->
        <div class="mt-fin-calendar-header-card">
          <div class="mt-fin-calendar-nav">
            <div class="mt-fin-month-picker-wrap">
              <button type="button" class="mt-fin-month-btn" id="mtFinMonthPickerBtn">
                <span>${this.monthNames[month]} ${year}</span>
                <i data-lucide="chevron-down" style="width:16px;height:16px;"></i>
              </button>
            </div>
            <div class="mt-fin-nav-arrows">
              <button type="button" class="btn btn-secondary btn-sm" id="mtFinPrevMonth" title="Previous Month">
                <i data-lucide="chevron-left" style="width:14px;height:14px;"></i>
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="mtFinCurrentMonthBtn" title="Go to current month">
                Today
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="mtFinNextMonth" title="Next Month">
                <i data-lucide="chevron-right" style="width:14px;height:14px;"></i>
              </button>
            </div>
          </div>

          <!-- Month Totals Summary Pills -->
          <div class="mt-fin-summary-row">
            <div class="mt-fin-summary-pill income">
              <span class="pill-label">Total Inflow</span>
              <span class="pill-val">₹ ${monthTotalIncome.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
            </div>
            <div class="mt-fin-summary-pill expense">
              <span class="pill-label">Total Outflow</span>
              <span class="pill-val">₹ ${monthTotalExpense.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
            </div>
            <div class="mt-fin-summary-pill net ${netCashflow >= 0 ? 'positive' : 'negative'}">
              <span class="pill-label">Net Cash Flow</span>
              <span class="pill-val">${netCashflow >= 0 ? '+' : ''}₹ ${netCashflow.toLocaleString('en-IN', { maximumFractionDigits: 0 })}</span>
            </div>
          </div>
        </div>

        <!-- Main Calendar Grid -->
        <div class="mt-calendar mt-calendar-full">
          <!-- Weekday Headers -->
          <div class="mt-calendar-weekdays">
            ${this.weekDays.map(d => `<div>${d}</div>`).join('')}
          </div>

          <!-- Days Grid -->
          <div class="mt-calendar-days">
    `;

    // Lead empty cells
    for (let i = 0; i < firstDayIndex; i++) {
      html += `<div class="mt-day-cell empty"></div>`;
    }

    // Days with transactions
    for (let d = 1; d <= daysCount; d++) {
      const dateStr = this.formatDate(year, month, d);
      const isToday = (dateStr === todayStr);
      const isSelected = (dateStr === this.finSelectedDate);
      const dayData = txnsByDate[dateStr];

      let classes = ['mt-day-cell', 'fin-day-cell'];
      if (isToday) classes.push('curr-date');
      if (isSelected) classes.push('selected-date');
      if (dayData && dayData.count > 0) classes.push('has-txns');

      html += `
        <div class="${classes.join(' ')}" data-date="${dateStr}">
          <div class="mt-day-header">
            <span class="mt-day-number">${d}</span>
            ${dayData && dayData.count > 0 ? `<span class="mt-day-txn-badge">${dayData.count}</span>` : ''}
          </div>
          
          <div class="mt-day-fin-badges">
            ${dayData && dayData.expense > 0 ? `
              <div class="mt-day-badge expense" title="Expense: ₹${dayData.expense.toLocaleString('en-IN')}">
                -₹${dayData.expense >= 1000 ? (dayData.expense / 1000).toFixed(1) + 'k' : Math.round(dayData.expense)}
              </div>
            ` : ''}
            ${dayData && dayData.income > 0 ? `
              <div class="mt-day-badge income" title="Income: ₹${dayData.income.toLocaleString('en-IN')}">
                +₹${dayData.income >= 1000 ? (dayData.income / 1000).toFixed(1) + 'k' : Math.round(dayData.income)}
              </div>
            ` : ''}
          </div>
        </div>
      `;
    }

    html += `
          </div>

          <!-- Month List Overlay -->
          <div class="mt-month-list-overlay" id="mtFinMonthOverlay">
            <div class="mt-month-list-header">
              <span>Select Month</span>
              <div class="mt-year-stepper">
                <button type="button" class="mt-year-btn" id="mtFinOverlayPrevYear"><i data-lucide="chevron-left"></i></button>
                <span class="mt-year-display" id="mtFinOverlayYear">${year}</span>
                <button type="button" class="mt-year-btn" id="mtFinOverlayNextYear"><i data-lucide="chevron-right"></i></button>
              </div>
            </div>
            <div class="mt-month-list-grid">
              ${this.monthNames.map((mName, idx) => `
                <div class="mt-month-item ${idx === month ? 'selected' : ''}" data-month="${idx}">
                  ${mName}
                </div>
              `).join('')}
            </div>
          </div>
        </div>

        <!-- Day Ledger Container (shows transactions for the selected day) -->
        <div class="mt-day-ledger-card" id="mtDayLedgerCard" style="${this.finSelectedDate ? '' : 'display:none;'}">
          <div class="mt-day-ledger-header">
            <div style="display:flex; align-items:center; gap:8px;">
              <i data-lucide="receipt" style="width:18px;height:18px;color:var(--color-primary);"></i>
              <h4 id="mtDayLedgerTitle" style="margin:0; font-size:0.98rem; font-weight:700;">
                Transactions on ${this.finSelectedDate || ''}
              </h4>
            </div>
            <div style="display:flex; gap:8px;">
              <button type="button" class="btn btn-primary btn-sm" id="mtDayAddTxnBtn">
                <i data-lucide="plus" style="width:14px;height:14px;"></i> Add for this Date
              </button>
              <button type="button" class="btn btn-ghost btn-sm btn-icon-only" id="mtDayLedgerCloseBtn" title="Close details">
                <i data-lucide="x" style="width:16px;height:16px;"></i>
              </button>
            </div>
          </div>
          <div class="mt-day-ledger-list" id="mtDayLedgerList">
            <!-- Populated on click -->
          </div>
        </div>

      </div>
    `;

    container.innerHTML = html;

    if (window.lucide) {
      lucide.createIcons({ root: container });
    }

    // Populate day ledger if a date is selected
    if (this.finSelectedDate) {
      this.populateDayLedger(this.finSelectedDate, txnsByDate[this.finSelectedDate]?.items || []);
    }

    // Bind Financial Calendar Events
    this.bindFinancialEvents(container, transactions, onDateSelected);
  }

  bindFinancialEvents(container, transactions, onDateSelected) {
    // Prev / Next Month
    const prevBtn = container.querySelector('#mtFinPrevMonth');
    const nextBtn = container.querySelector('#mtFinNextMonth');
    const todayBtn = container.querySelector('#mtFinCurrentMonthBtn');

    if (prevBtn) {
      prevBtn.addEventListener('click', () => {
        if (this.finCurrentMonth === 0) {
          this.finCurrentMonth = 11;
          this.finCurrentYear--;
        } else {
          this.finCurrentMonth--;
        }
        this.renderFinancialCalendar(container, transactions, onDateSelected);
      });
    }

    if (nextBtn) {
      nextBtn.addEventListener('click', () => {
        if (this.finCurrentMonth === 11) {
          this.finCurrentMonth = 0;
          this.finCurrentYear++;
        } else {
          this.finCurrentMonth++;
        }
        this.renderFinancialCalendar(container, transactions, onDateSelected);
      });
    }

    if (todayBtn) {
      todayBtn.addEventListener('click', () => {
        const now = new Date();
        this.finCurrentYear = now.getFullYear();
        this.finCurrentMonth = now.getMonth();
        this.finSelectedDate = this.formatDate(now.getFullYear(), now.getMonth(), now.getDate());
        this.renderFinancialCalendar(container, transactions, onDateSelected);
      });
    }

    // Month picker overlay toggle
    const monthPickerBtn = container.querySelector('#mtFinMonthPickerBtn');
    const monthOverlay = container.querySelector('#mtFinMonthOverlay');
    if (monthPickerBtn && monthOverlay) {
      monthPickerBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        monthOverlay.classList.toggle('show');
      });
    }

    // Month overlay year stepper
    const overlayPrevYear = container.querySelector('#mtFinOverlayPrevYear');
    const overlayNextYear = container.querySelector('#mtFinOverlayNextYear');
    const overlayYearDisplay = container.querySelector('#mtFinOverlayYear');

    if (overlayPrevYear && overlayYearDisplay) {
      overlayPrevYear.addEventListener('click', (e) => {
        e.stopPropagation();
        this.finCurrentYear--;
        overlayYearDisplay.textContent = this.finCurrentYear;
      });
    }

    if (overlayNextYear && overlayYearDisplay) {
      overlayNextYear.addEventListener('click', (e) => {
        e.stopPropagation();
        this.finCurrentYear++;
        overlayYearDisplay.textContent = this.finCurrentYear;
      });
    }

    // Month overlay items
    const monthItems = container.querySelectorAll('#mtFinMonthOverlay .mt-month-item');
    monthItems.forEach(item => {
      item.addEventListener('click', (e) => {
        e.stopPropagation();
        const m = parseInt(item.getAttribute('data-month'), 10);
        this.finCurrentMonth = m;
        this.renderFinancialCalendar(container, transactions, onDateSelected);
      });
    });

    // Day cell click
    const dayCells = container.querySelectorAll('.fin-day-cell:not(.empty)');
    dayCells.forEach(cell => {
      cell.addEventListener('click', () => {
        const pickedDate = cell.getAttribute('data-date');
        this.finSelectedDate = pickedDate;

        dayCells.forEach(c => c.classList.remove('selected-date'));
        cell.classList.add('selected-date');

        // Filter transactions for this day
        const dayTxns = transactions.filter(t => t.date === pickedDate);
        this.populateDayLedger(pickedDate, dayTxns);

        if (typeof onDateSelected === 'function') {
          onDateSelected(pickedDate, dayTxns);
        }
      });
    });

    // Close ledger button
    const closeLedgerBtn = container.querySelector('#mtDayLedgerCloseBtn');
    if (closeLedgerBtn) {
      closeLedgerBtn.addEventListener('click', () => {
        const ledgerCard = container.querySelector('#mtDayLedgerCard');
        if (ledgerCard) ledgerCard.style.display = 'none';
        this.finSelectedDate = null;
        dayCells.forEach(c => c.classList.remove('selected-date'));
      });
    }

    // Add for this date button
    const addTxnBtn = container.querySelector('#mtDayAddTxnBtn');
    if (addTxnBtn) {
      addTxnBtn.addEventListener('click', () => {
        if (window.app && typeof window.app.openAddTxnModal === 'function') {
          window.app.openAddTxnModal(this.finSelectedDate);
        }
      });
    }
  }

  populateDayLedger(dateStr, dayTxns) {
    const card = document.getElementById('mtDayLedgerCard');
    const title = document.getElementById('mtDayLedgerTitle');
    const list = document.getElementById('mtDayLedgerList');
    if (!card || !list) return;

    card.style.display = 'block';
    if (title) title.textContent = `Transactions on ${dateStr} (${dayTxns.length} record${dayTxns.length === 1 ? '' : 's'})`;

    if (dayTxns.length === 0) {
      list.innerHTML = `
        <div class="mt-empty-day-state">
          <i data-lucide="calendar-check" style="width:24px;height:24px;color:var(--text-muted);opacity:0.6;"></i>
          <span>No transactions recorded on this date.</span>
        </div>
      `;
    } else {
      list.innerHTML = dayTxns.map(t => {
        const amt = parseFloat(t.amount) || 0;
        const isExp = t.type === 'expense';
        const isInc = t.type === 'income';
        const badgeClass = isExp ? 'expense' : (isInc ? 'income' : 'transfer');
        const sign = isExp ? '-' : (isInc ? '+' : '');

        return `
          <div class="mt-ledger-item">
            <div class="mt-ledger-info">
              <div class="mt-ledger-desc">${this.escapeHtml(t.description || 'Untitled Transaction')}</div>
              <div class="mt-ledger-meta">
                <span class="badge-tag ${badgeClass}">${t.type}</span>
                <span>${this.escapeHtml(t.category || 'General')}</span>
                ${t.paymentMode ? `<span>• ${this.escapeHtml(t.paymentMode)}</span>` : ''}
                ${t.time ? `<span>• ${this.escapeHtml(t.time)}</span>` : ''}
              </div>
            </div>
            <div class="mt-ledger-right">
              <div class="mt-ledger-amount ${badgeClass}">
                ${sign}₹ ${amt.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </div>
              <div class="mt-ledger-actions">
                <button type="button" class="btn btn-ghost btn-sm btn-icon-only mt-ledger-edit-btn" data-txn-id="${this.escapeHtml(t.id)}" title="Edit Transaction">
                  <i data-lucide="edit-2" style="width:14px;height:14px;"></i>
                </button>
                <button type="button" class="btn btn-ghost btn-sm btn-icon-only text-danger mt-ledger-delete-btn" data-txn-id="${this.escapeHtml(t.id)}" title="Delete Transaction">
                  <i data-lucide="trash-2" style="width:14px;height:14px;"></i>
                </button>
              </div>
            </div>
          </div>
        `;
      }).join('');
    }

    if (window.lucide) {
      lucide.createIcons({ root: list });
    }

    list.querySelectorAll('.mt-ledger-edit-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        if (window.app && typeof window.app.openEditTxnModal === 'function') {
          window.app.openEditTxnModal(btn.getAttribute('data-txn-id'));
        }
      });
    });
    list.querySelectorAll('.mt-ledger-delete-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        if (window.app && typeof window.app.deleteTxn === 'function') {
          window.app.deleteTxn(btn.getAttribute('data-txn-id'));
        }
      });
    });
  }

  escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
}

// Global Singleton Instance (browser only)
if (typeof window !== 'undefined') {
  window.calendarEngine = new MoneyTrackerCalendar();
}

// Node/test export (does not affect browser <script> usage)
if (typeof module !== 'undefined' && module.exports) {
  module.exports = MoneyTrackerCalendar;
}
