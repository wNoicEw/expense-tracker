/**
 * ============================================================================
 * MONEY TRACKER - MATERIALIZE-STYLE THEMED TIME PICKER ENGINE
 * ============================================================================
 * Adapted from Materialize CSS clock-dial timepicker:
 * - Digital display header with active Hour / Minute toggle
 * - AM / PM segment selector
 * - Interactive analog clock face with SVG rotating hand & selection head
 * - 12-hour circular dial with auto-transition to minutes view
 * - Smooth drag & click interaction with angle math
 * - "Now", "Cancel", and "OK" quick actions
 * - Standard 24-hour HH:mm output value format for 100% data compatibility
 * - Beautiful glassmorphic design supporting both Dark OLED and Light themes
 * ============================================================================
 */

class MoneyTrackerTimePicker {
  constructor() {
    this.activePopover = null;
    this.activeTargetInput = null;

    // Time picker internal state
    this.currentView = 'hours'; // 'hours' | 'minutes'
    this.selectedHour = 12;     // 1 - 12
    this.selectedMinute = 0;    // 0 - 59
    this.selectedPeriod = 'PM'; // 'AM' | 'PM'
    this.isDragging = false;
    this.lastOpenTimestamp = 0;

    // Clock geometry constants
    this.dialSize = 260; // plate diameter in px
    this.center = 130;   // dial center (x, y)
    this.radius = 96;    // distance from center to tick center

    // Bound listeners
    this.handleOutsideClick = this.handleOutsideClick.bind(this);
    this.handleKeyDown = this.handleKeyDown.bind(this);
    this.onPlateMouseMove = this.onPlateMouseMove.bind(this);
    this.onPlateMouseUp = this.onPlateMouseUp.bind(this);

    document.addEventListener('click', this.handleOutsideClick, true);
    document.addEventListener('keydown', this.handleKeyDown);
    document.addEventListener('mousemove', this.onPlateMouseMove);
    document.addEventListener('mouseup', this.onPlateMouseUp);
    document.addEventListener('touchmove', this.onPlateMouseMove, { passive: false });
    document.addEventListener('touchend', this.onPlateMouseUp);
  }

  /**
   * Parse input value (supports 'HH:mm', 'H:m', or ISO strings)
   */
  parseTime(val) {
    if (val && typeof val === 'string' && val.includes(':')) {
      const parts = val.trim().split(':');
      let h = parseInt(parts[0], 10);
      let m = parseInt(parts[1], 10);
      if (!isNaN(h) && !isNaN(m)) {
        h = Math.max(0, Math.min(23, h));
        m = Math.max(0, Math.min(59, m));
        const period = h >= 12 ? 'PM' : 'AM';
        const h12 = h % 12 === 0 ? 12 : h % 12;
        return { hour: h12, minute: m, period };
      }
    }
    const now = new Date();
    const h = now.getHours();
    const m = now.getMinutes();
    return {
      hour: h % 12 === 0 ? 12 : h % 12,
      minute: m,
      period: h >= 12 ? 'PM' : 'AM'
    };
  }

  /**
   * Format current state to 24-hour 'HH:mm' string
   */
  get24HourString(hour12, minute, period) {
    let h = hour12 % 12;
    if (period === 'PM') h += 12;
    return `${String(h).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
  }

  /**
   * Open the Time Picker anchored near targetInput
   */
  openTimePicker(targetInput) {
    const now = Date.now();
    if (now - this.lastOpenTimestamp < 250) return;
    this.lastOpenTimestamp = now;

    if (this.activePopover) {
      if (this.activeTargetInput === targetInput) {
        this.close();
        return;
      }
      this.close();
    }
    if (window.calendarEngine && typeof window.calendarEngine.closeDatePicker === 'function') {
      window.calendarEngine.closeDatePicker();
    }

    if (!targetInput) return;
    this.activeTargetInput = targetInput;
    this.focusReturnEl = targetInput;

    const parsed = this.parseTime(targetInput.value);
    this.selectedHour = parsed.hour;
    this.selectedMinute = parsed.minute;
    this.selectedPeriod = parsed.period;
    this.currentView = 'hours';

    this.renderPopover();
  }

  /**
   * Construct and display the popover DOM
   */
  renderPopover() {
    const popover = document.createElement('div');
    popover.className = 'mt-timepicker-popover';
    popover.id = 'mtTimePickerPopover';
    popover.setAttribute('role', 'dialog');
    popover.setAttribute('aria-modal', 'true');
    popover.setAttribute('aria-label', 'Choose time');
    popover.setAttribute('tabindex', '-1');

    popover.innerHTML = `
      <div class="mt-timepicker-container">
        <!-- Digital Display Header -->
        <div class="mt-timepicker-header">
          <div class="mt-timepicker-digits">
            <button type="button" class="mt-timepicker-digit-btn ${this.currentView === 'hours' ? 'active' : ''}" id="mtTpBtnHours" title="Choose hours">
              ${String(this.selectedHour).padStart(2, '0')}
            </button>
            <span class="mt-timepicker-colon">:</span>
            <button type="button" class="mt-timepicker-digit-btn ${this.currentView === 'minutes' ? 'active' : ''}" id="mtTpBtnMinutes" title="Choose minutes">
              ${String(this.selectedMinute).padStart(2, '0')}
            </button>
          </div>
          <div class="mt-timepicker-ampm">
            <button type="button" class="mt-timepicker-ampm-btn ${this.selectedPeriod === 'AM' ? 'active' : ''}" id="mtTpBtnAM">AM</button>
            <button type="button" class="mt-timepicker-ampm-btn ${this.selectedPeriod === 'PM' ? 'active' : ''}" id="mtTpBtnPM">PM</button>
          </div>
        </div>

        <!-- Analog Clock Display -->
        <div class="mt-timepicker-body">
          <div class="mt-timepicker-plate" id="mtTpPlate">
            <svg class="mt-timepicker-canvas" width="${this.dialSize}" height="${this.dialSize}" viewBox="0 0 ${this.dialSize} ${this.dialSize}">
              <!-- Hour Arm Group (Shorter, thicker arm with counter tail) -->
              <g id="mtTpHourGroup" class="mt-tp-hand-group ${this.currentView === 'hours' ? 'active-arm' : 'inactive-arm'}">
                <line id="mtTpHourTail" class="mt-tp-hour-tail"></line>
                <line id="mtTpHourArm" class="mt-tp-hour-arm"></line>
                <circle id="mtTpHourHead" class="mt-tp-hour-head"></circle>
                <circle id="mtTpHourHeadDot" class="mt-tp-hour-head-dot"></circle>
              </g>
              <!-- Minute Arm Group (Longer, sleeker arm with counter tail) -->
              <g id="mtTpMinuteGroup" class="mt-tp-hand-group ${this.currentView === 'minutes' ? 'active-arm' : 'inactive-arm'}">
                <line id="mtTpMinuteTail" class="mt-tp-minute-tail"></line>
                <line id="mtTpMinuteArm" class="mt-tp-minute-arm"></line>
                <circle id="mtTpMinuteHead" class="mt-tp-minute-head"></circle>
                <circle id="mtTpMinuteHeadDot" class="mt-tp-minute-head-dot"></circle>
              </g>
              <!-- Center Hub: Real watch center pivot with dual concentric rings -->
              <circle cx="${this.center}" cy="${this.center}" r="7" class="mt-tp-hub-outer"></circle>
              <circle cx="${this.center}" cy="${this.center}" r="3" class="mt-tp-hub-inner"></circle>
            </svg>
            <!-- Ticks Container -->
            <div class="mt-timepicker-dial" id="mtTpDial"></div>
          </div>
        </div>

        <!-- Footer Actions -->
        <div class="mt-timepicker-footer">
          <button type="button" class="mt-tp-action-btn mt-tp-now-btn" id="mtTpNow">Now</button>
          <div class="mt-tp-footer-right">
            <button type="button" class="mt-tp-action-btn mt-tp-cancel-btn" id="mtTpCancel">Cancel</button>
            <button type="button" class="mt-tp-action-btn mt-tp-ok-btn" id="mtTpOk">OK</button>
          </div>
        </div>
      </div>
    `;

    document.body.appendChild(popover);
    this.activePopover = popover;

    // Attach Header & Footer handlers
    const btnHours = popover.querySelector('#mtTpBtnHours');
    const btnMinutes = popover.querySelector('#mtTpBtnMinutes');
    const btnAM = popover.querySelector('#mtTpBtnAM');
    const btnPM = popover.querySelector('#mtTpBtnPM');
    const btnNow = popover.querySelector('#mtTpNow');
    const btnCancel = popover.querySelector('#mtTpCancel');
    const btnOk = popover.querySelector('#mtTpOk');

    btnHours.addEventListener('click', () => this.switchView('hours'));
    btnMinutes.addEventListener('click', () => this.switchView('minutes'));

    btnAM.addEventListener('click', () => {
      this.selectedPeriod = 'AM';
      btnAM.classList.add('active');
      btnPM.classList.remove('active');
    });

    btnPM.addEventListener('click', () => {
      this.selectedPeriod = 'PM';
      btnPM.classList.add('active');
      btnAM.classList.remove('active');
    });

    btnNow.addEventListener('click', () => {
      const now = new Date();
      const h = now.getHours();
      const m = now.getMinutes();
      this.selectedHour = h % 12 === 0 ? 12 : h % 12;
      this.selectedMinute = m;
      this.selectedPeriod = h >= 12 ? 'PM' : 'AM';
      this.updateDigitalDisplay();
      this.renderDial();
    });

    btnCancel.addEventListener('click', () => this.close());
    btnOk.addEventListener('click', () => this.commit());

    // Dial plate drag/click handlers
    const plate = popover.querySelector('#mtTpPlate');
    plate.addEventListener('mousedown', (e) => this.onPlateMouseDown(e));
    plate.addEventListener('touchstart', (e) => this.onPlateMouseDown(e), { passive: false });

    // Initial render of dial ticks
    this.renderDial();

    // Position popover
    this.positionPopover();
    popover.focus();
  }

  /**
   * Switch between 'hours' and 'minutes' view
   */
  switchView(view) {
    if (this.currentView === view) return;
    this.currentView = view;
    this.updateDigitalDisplay();
    this.renderDial(true);
  }

  /**
   * Update the digital numbers in the header
   */
  updateDigitalDisplay() {
    if (!this.activePopover) return;
    const btnHours = this.activePopover.querySelector('#mtTpBtnHours');
    const btnMinutes = this.activePopover.querySelector('#mtTpBtnMinutes');
    const btnAM = this.activePopover.querySelector('#mtTpBtnAM');
    const btnPM = this.activePopover.querySelector('#mtTpBtnPM');

    if (btnHours) {
      btnHours.textContent = String(this.selectedHour).padStart(2, '0');
      btnHours.classList.toggle('active', this.currentView === 'hours');
    }
    if (btnMinutes) {
      btnMinutes.textContent = String(this.selectedMinute).padStart(2, '0');
      btnMinutes.classList.toggle('active', this.currentView === 'minutes');
    }
    if (btnAM && btnPM) {
      btnAM.classList.toggle('active', this.selectedPeriod === 'AM');
      btnPM.classList.toggle('active', this.selectedPeriod === 'PM');
    }
  }

  /**
   * Render the numbers on the clock face dial
   */
  renderDial(withAnimation = false) {
    if (!this.activePopover) return;
    const dial = this.activePopover.querySelector('#mtTpDial');
    if (!dial) return;

    const hadFocus = document.activeElement && document.activeElement.classList && document.activeElement.classList.contains('mt-tp-tick');
    let tickToFocus = null;

    dial.innerHTML = '';
    if (withAnimation) {
      dial.style.animation = 'none';
      void dial.offsetWidth; // trigger reflow
      dial.style.animation = 'mtTpDialFade 0.2s cubic-bezier(0.16, 1, 0.3, 1)';
    }

    if (this.currentView === 'hours') {
      // 1 to 12
      for (let h = 1; h <= 12; h++) {
        const angle = (h * 30 - 90) * (Math.PI / 180);
        const x = this.center + this.radius * Math.cos(angle);
        const y = this.center + this.radius * Math.sin(angle);

        const isActive = this.selectedHour === h;
        const tick = document.createElement('div');
        tick.className = `mt-tp-tick ${isActive ? 'active' : ''}`;
        tick.style.left = `${x}px`;
        tick.style.top = `${y}px`;
        tick.textContent = h;
        tick.setAttribute('role', 'button');
        tick.setAttribute('tabindex', isActive ? '0' : '-1');
        tick.setAttribute('aria-pressed', String(isActive));
        tick.setAttribute('aria-label', `${h} o'clock`);
        tick.addEventListener('keydown', (e) => this.handleTickKeyDown(e, h));
        if (isActive) tickToFocus = tick;
        dial.appendChild(tick);
      }
    } else {
      // 00 to 55 (every 5 minutes displayed, intermediate minutes selectable via drag).
      // The current minute might not land on a displayed tick (e.g. parsed from "09:19"),
      // so track the nearest tick too — something must always be keyboard-reachable.
      let nearestTick = null;
      let nearestDiff = Infinity;

      for (let m = 0; m < 60; m += 5) {
        const angle = (m * 6 - 90) * (Math.PI / 180);
        const x = this.center + this.radius * Math.cos(angle);
        const y = this.center + this.radius * Math.sin(angle);

        const isActive = this.selectedMinute === m;
        const tick = document.createElement('div');
        tick.className = `mt-tp-tick ${isActive ? 'active' : ''}`;
        tick.style.left = `${x}px`;
        tick.style.top = `${y}px`;
        tick.textContent = String(m).padStart(2, '0');
        tick.setAttribute('role', 'button');
        tick.setAttribute('aria-pressed', String(isActive));
        tick.setAttribute('aria-label', `${m} minutes`);
        tick.addEventListener('keydown', (e) => this.handleTickKeyDown(e, m));

        const diff = Math.min(Math.abs(m - this.selectedMinute), 60 - Math.abs(m - this.selectedMinute));
        if (diff < nearestDiff) {
          nearestDiff = diff;
          nearestTick = tick;
        }

        if (isActive) tickToFocus = tick;
        dial.appendChild(tick);
      }

      const rovingTick = tickToFocus || nearestTick;
      if (rovingTick) {
        rovingTick.setAttribute('tabindex', '0');
        if (!tickToFocus) tickToFocus = rovingTick;
      }
      dial.querySelectorAll('.mt-tp-tick').forEach(t => {
        if (t !== rovingTick) t.setAttribute('tabindex', '-1');
      });
    }

    if (hadFocus && tickToFocus) {
      tickToFocus.focus();
    }

    this.updateClockHand();
  }

  /**
   * Keyboard support for a dial tick: Enter/Space selects it, arrow keys step the value.
   */
  handleTickKeyDown(e, tickValue) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      e.stopPropagation();
      if (this.currentView === 'hours') {
        this.selectedHour = tickValue;
        this.updateDigitalDisplay();
        this.renderDial();
        setTimeout(() => this.switchView('minutes'), 160);
      } else {
        this.selectedMinute = tickValue;
        this.updateDigitalDisplay();
        this.renderDial();
      }
      return;
    }

    let step = 0;
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') step = 1;
    else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') step = -1;
    if (step === 0) return;

    e.preventDefault();
    e.stopPropagation();

    if (this.currentView === 'hours') {
      let h = ((this.selectedHour - 1 + step) % 12 + 12) % 12 + 1;
      this.selectedHour = h;
    } else {
      let m = ((this.selectedMinute + step * 5) % 60 + 60) % 60;
      this.selectedMinute = m;
    }
    this.updateDigitalDisplay();
    this.renderDial();
  }

  /**
   * Update both Hour and Minute SVG clock hands, counter-tails, and selection halos
   */
  updateClockHand() {
    if (!this.activePopover) return;
    const hourGroup = this.activePopover.querySelector('#mtTpHourGroup');
    const hourArm = this.activePopover.querySelector('#mtTpHourArm');
    const hourTail = this.activePopover.querySelector('#mtTpHourTail');
    const hourHead = this.activePopover.querySelector('#mtTpHourHead');
    const hourHeadDot = this.activePopover.querySelector('#mtTpHourHeadDot');

    const minuteGroup = this.activePopover.querySelector('#mtTpMinuteGroup');
    const minuteArm = this.activePopover.querySelector('#mtTpMinuteArm');
    const minuteTail = this.activePopover.querySelector('#mtTpMinuteTail');
    const minuteHead = this.activePopover.querySelector('#mtTpMinuteHead');
    const minuteHeadDot = this.activePopover.querySelector('#mtTpMinuteHeadDot');

    if (!hourArm || !minuteArm) return;

    const isHours = this.currentView === 'hours';

    // Update active hand groups
    if (hourGroup) {
      hourGroup.setAttribute('class', `mt-tp-hand-group ${isHours ? 'active-arm' : 'inactive-arm'}`);
    }
    if (minuteGroup) {
      minuteGroup.setAttribute('class', `mt-tp-hand-group ${!isHours ? 'active-arm' : 'inactive-arm'}`);
    }

    // --- 1. Hour Hand Geometry (Shorter, thicker arm) ---
    let hourDeg = (this.selectedHour % 12) * 30;
    if (!isHours) {
      hourDeg += (this.selectedMinute / 60) * 30;
    }
    const hourRad = (hourDeg - 90) * (Math.PI / 180);

    const hourArmLen = isHours ? 80 : 64;
    const hArmX = this.center + hourArmLen * Math.cos(hourRad);
    const hArmY = this.center + hourArmLen * Math.sin(hourRad);
    const hTailX = this.center - 14 * Math.cos(hourRad);
    const hTailY = this.center - 14 * Math.sin(hourRad);
    const hHeadX = this.center + this.radius * Math.cos(hourRad);
    const hHeadY = this.center + this.radius * Math.sin(hourRad);

    hourArm.setAttribute('x1', this.center);
    hourArm.setAttribute('y1', this.center);
    hourArm.setAttribute('x2', hArmX);
    hourArm.setAttribute('y2', hArmY);

    if (hourTail) {
      hourTail.setAttribute('x1', this.center);
      hourTail.setAttribute('y1', this.center);
      hourTail.setAttribute('x2', hTailX);
      hourTail.setAttribute('y2', hTailY);
    }

    if (hourHead && hourHeadDot) {
      if (isHours) {
        hourHead.style.display = '';
        hourHeadDot.style.display = '';
        hourHead.setAttribute('cx', hHeadX);
        hourHead.setAttribute('cy', hHeadY);
        hourHead.setAttribute('r', 16);
        hourHeadDot.setAttribute('cx', hHeadX);
        hourHeadDot.setAttribute('cy', hHeadY);
        hourHeadDot.setAttribute('r', 2.5);
      } else {
        hourHead.style.display = 'none';
        hourHeadDot.style.display = 'none';
      }
    }

    // --- 2. Minute Hand Geometry (Longer, sleeker arm) ---
    const minDeg = this.selectedMinute * 6;
    const minRad = (minDeg - 90) * (Math.PI / 180);

    const minArmLen = !isHours ? 80 : 92;
    const mArmX = this.center + minArmLen * Math.cos(minRad);
    const mArmY = this.center + minArmLen * Math.sin(minRad);
    const mTailX = this.center - 18 * Math.cos(minRad);
    const mTailY = this.center - 18 * Math.sin(minRad);
    const mHeadX = this.center + this.radius * Math.cos(minRad);
    const mHeadY = this.center + this.radius * Math.sin(minRad);

    minuteArm.setAttribute('x1', this.center);
    minuteArm.setAttribute('y1', this.center);
    minuteArm.setAttribute('x2', mArmX);
    minuteArm.setAttribute('y2', mArmY);

    if (minuteTail) {
      minuteTail.setAttribute('x1', this.center);
      minuteTail.setAttribute('y1', this.center);
      minuteTail.setAttribute('x2', mTailX);
      minuteTail.setAttribute('y2', mTailY);
    }

    if (minuteHead && minuteHeadDot) {
      if (!isHours) {
        minuteHead.style.display = '';
        minuteHeadDot.style.display = '';
        minuteHead.setAttribute('cx', mHeadX);
        minuteHead.setAttribute('cy', mHeadY);
        minuteHead.setAttribute('r', 16);
        minuteHeadDot.setAttribute('cx', mHeadX);
        minuteHeadDot.setAttribute('cy', mHeadY);
        minuteHeadDot.setAttribute('r', 2.5);
      } else {
        minuteHead.style.display = 'none';
        minuteHeadDot.style.display = 'none';
      }
    }
  }

  /**
   * Calculate angle & update selected value from mouse/touch position
   */
  calculatePosition(e) {
    if (!this.activePopover) return;
    const plate = this.activePopover.querySelector('#mtTpPlate');
    if (!plate) return;

    const rect = plate.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;

    const x = clientX - rect.left - this.center;
    const y = clientY - rect.top - this.center;

    let angle = Math.atan2(y, x) * (180 / Math.PI) + 90;
    if (angle < 0) angle += 360;

    if (this.currentView === 'hours') {
      let step = Math.round(angle / 30);
      if (step === 0) step = 12;
      this.selectedHour = step;
    } else {
      let minute = Math.round(angle / 6) % 60;
      this.selectedMinute = minute;
    }

    this.updateDigitalDisplay();
    this.updateClockHand();

    // Update active class on ticks
    const ticks = this.activePopover.querySelectorAll('.mt-tp-tick');
    const targetVal = this.currentView === 'hours' ? this.selectedHour : this.selectedMinute;
    ticks.forEach(t => {
      const val = parseInt(t.textContent, 10);
      t.classList.toggle('active', val === targetVal);
    });
  }

  onPlateMouseDown(e) {
    if (e.cancelable && e.touches) e.preventDefault();
    this.isDragging = true;
    this.calculatePosition(e);
  }

  onPlateMouseMove(e) {
    if (!this.isDragging) return;
    if (e.cancelable && e.touches) e.preventDefault();
    this.calculatePosition(e);
  }

  onPlateMouseUp(e) {
    if (!this.isDragging) return;
    this.isDragging = false;

    // If we just selected an hour, smoothly auto-advance to minutes view!
    if (this.currentView === 'hours') {
      setTimeout(() => {
        if (this.activePopover) {
          this.switchView('minutes');
        }
      }, 160);
    }
  }

  /**
   * Position popover relative to the input container
   */
  positionPopover() {
    if (!this.activePopover || !this.activeTargetInput) return;

    const trigger = this.activeTargetInput.closest('.datetime-input-box') || this.activeTargetInput;
    const rect = trigger.getBoundingClientRect();
    const popRect = this.activePopover.getBoundingClientRect();

    let top = rect.bottom + 8;
    let left = rect.left;

    // Keep within viewport horizontally
    if (left + popRect.width > window.innerWidth - 16) {
      left = window.innerWidth - popRect.width - 16;
    }
    if (left < 16) left = 16;

    // Flip up or clamp if bottom goes off-screen
    if (top + popRect.height > window.innerHeight - 16) {
      const altTop = rect.top - popRect.height - 8;
      if (altTop > 16) {
        top = altTop;
      } else {
        top = Math.max(16, window.innerHeight - popRect.height - 16);
      }
    }

    this.activePopover.style.top = `${Math.round(top)}px`;
    this.activePopover.style.left = `${Math.round(left)}px`;
  }

  /**
   * Commit the selected time into the target input
   */
  commit() {
    if (this.activeTargetInput) {
      const formatted = this.get24HourString(this.selectedHour, this.selectedMinute, this.selectedPeriod);
      this.activeTargetInput.value = formatted;
      this.activeTargetInput.dispatchEvent(new Event('input', { bubbles: true }));
      this.activeTargetInput.dispatchEvent(new Event('change', { bubbles: true }));
    }
    this.close();
  }

  /**
   * Dismiss the popover
   */
  close() {
    if (this.activePopover) {
      this.activePopover.remove();
      this.activePopover = null;
      this.activeTargetInput = null;
      this.isDragging = false;
    }
    if (this.focusReturnEl && typeof this.focusReturnEl.focus === 'function') {
      this.focusReturnEl.focus();
    }
    this.focusReturnEl = null;
  }

  handleOutsideClick(e) {
    if (!this.activePopover) return;
    if (this.activePopover.contains(e.target)) return;
    if (this.activeTargetInput && (this.activeTargetInput === e.target || this.activeTargetInput.closest('.datetime-input-box')?.contains(e.target))) {
      return;
    }
    this.close();
  }

  handleKeyDown(e) {
    if (e.key === 'Escape' && this.activePopover) {
      this.close();
    } else if (e.key === 'Enter' && this.activePopover) {
      this.commit();
    }
  }
}

// Global initialization
if (typeof window !== 'undefined') {
  window.timePickerEngine = new MoneyTrackerTimePicker();
}

// Node/test export (does not affect browser <script> usage)
if (typeof module !== 'undefined' && module.exports) {
  module.exports = MoneyTrackerTimePicker;
}
