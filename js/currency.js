/**
 * Currency & Exchange Rate Engine — Money Tracker
 * 
 * Supports:
 * - INR (₹) - Indian Rupee
 * - USD ($) - US Dollar
 * - EUR (€) - Euro
 * - GBP (£) - British Pound
 * - CHF (CHF) - Swiss Franc
 * - JPY (¥) - Japanese Yen
 * 
 * Daily Exchange Rate Sync:
 * - Fetches once per calendar day when the app is opened for the first time.
 * - Primary endpoint: https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json
 * - Fallback endpoint: https://latest.currency-api.pages.dev/v1/currencies/usd.json
 * - Offline baseline snapshot guarantees 100% functionality without internet.
 */

const SUPPORTED_CURRENCIES = [
  { code: 'INR', symbol: '₹', name: 'Indian Rupee', flag: '🇮🇳', locale: 'en-IN', decimals: 2 },
  { code: 'USD', symbol: '$', name: 'US Dollar', flag: '🇺🇸', locale: 'en-US', decimals: 2 },
  { code: 'EUR', symbol: '€', name: 'Euro', flag: '🇪🇺', locale: 'de-DE', decimals: 2 },
  { code: 'GBP', symbol: '£', name: 'British Pound', flag: '🇬🇧', locale: 'en-GB', decimals: 2 },
  { code: 'CHF', symbol: '₣', name: 'Swiss Franc', flag: '🇨🇭', locale: 'de-CH', decimals: 2 },
  { code: 'JPY', symbol: '¥', name: 'Japanese Yen', flag: '🇯🇵', locale: 'ja-JP', decimals: 0 }
];

const CURRENCY_MAP = SUPPORTED_CURRENCIES.reduce((acc, curr) => {
  acc[curr.code] = curr;
  return acc;
}, {});

// Built-in baseline rates (USD base: 1 USD = X currency)
const BASELINE_USD_RATES = {
  usd: 1.0,
  inr: 83.5,
  eur: 0.92,
  gbp: 0.78,
  chf: 0.89,
  jpy: 155.0
};

const STORAGE_KEY_RATES = 'money_tracker_exchange_rates';
const STORAGE_KEY_API_RATES = 'money_tracker_exchange_rates_api';
const STORAGE_KEY_FETCH_DATE = 'money_tracker_exchange_rates_date';
const STORAGE_KEY_FETCH_TIMESTAMP = 'money_tracker_exchange_rates_timestamp';
const STORAGE_KEY_OVERRIDES = 'money_tracker_currency_overrides';

class CurrencyEngineClass {
  constructor() {
    this.apiRates = this._loadCachedApiRates();
    this.manualOverrides = new Set(this._loadCachedOverrides());
    this.rates = this._loadCachedRates();
    this.lastFetchDate = this._safeGetItem(STORAGE_KEY_FETCH_DATE) || '';
    this.lastFetchTimestamp = this._safeGetItem(STORAGE_KEY_FETCH_TIMESTAMP) || '';
  }

  _safeGetItem(key) {
    try {
      return typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null;
    } catch {
      return null;
    }
  }

  _safeSetItem(key, val) {
    try {
      if (typeof localStorage !== 'undefined') localStorage.setItem(key, val);
    } catch {}
  }

  /**
   * Returns list of all supported currencies.
   */
  getSupportedCurrencies() {
    return SUPPORTED_CURRENCIES;
  }

  /**
   * Returns currency metadata or fallback to INR.
   */
  getCurrency(code) {
    if (!code) return CURRENCY_MAP['INR'];
    return CURRENCY_MAP[code.toUpperCase()] || CURRENCY_MAP['INR'];
  }

  /**
   * Returns symbol for currency code (e.g. '$', '₹', '€', '¥').
   */
  getSymbol(code) {
    return this.getCurrency(code).symbol;
  }

  getApiRate(code) {
    const key = (code || 'USD').toLowerCase();
    return this.apiRates[key] || BASELINE_USD_RATES[key] || 1.0;
  }

  isManualOverride(code) {
    return this.manualOverrides.has((code || '').toUpperCase());
  }

  hasAnyManualOverride() {
    return this.manualOverrides.size > 0;
  }

  getLastFetchTimestamp() {
    const ts = this._safeGetItem(STORAGE_KEY_FETCH_TIMESTAMP);
    return ts || (this.lastFetchDate ? `Synced ${this.lastFetchDate}` : 'Offline Baseline Rates');
  }

  _getProfileId() {
    try {
      if (typeof window !== 'undefined' && window.profileManager && typeof window.profileManager.getActiveProfile === 'function') {
        const p = window.profileManager.getActiveProfile();
        if (p && p.id) return p.id;
      }
    } catch {}
    return 'default';
  }

  _getRatesStorageKey() {
    const pid = this._getProfileId();
    return pid === 'default' ? STORAGE_KEY_RATES : `${STORAGE_KEY_RATES}_${pid}`;
  }

  _getOverridesStorageKey() {
    const pid = this._getProfileId();
    return pid === 'default' ? STORAGE_KEY_OVERRIDES : `${STORAGE_KEY_OVERRIDES}_${pid}`;
  }

  reloadActiveProfileRates() {
    this.manualOverrides = new Set(this._loadCachedOverrides());
    this.rates = this._loadCachedRates();
  }

  _loadCachedOverrides() {
    try {
      const key = this._getOverridesStorageKey();
      let raw = this._safeGetItem(key);
      if (!raw && key !== STORAGE_KEY_OVERRIDES) {
        raw = this._safeGetItem(STORAGE_KEY_OVERRIDES);
      }
      if (raw) {
        const arr = JSON.parse(raw);
        if (Array.isArray(arr)) return arr.map(c => String(c).toUpperCase());
      }
    } catch {}
    return [];
  }

  _loadCachedApiRates() {
    try {
      const raw = this._safeGetItem(STORAGE_KEY_API_RATES);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed === 'object') {
          return { ...BASELINE_USD_RATES, ...parsed };
        }
      }
    } catch {}
    return { ...BASELINE_USD_RATES };
  }

  /**
   * Load active rates from localStorage or fall back to baseline.
   */
  _loadCachedRates() {
    try {
      const key = this._getRatesStorageKey();
      let raw = this._safeGetItem(key);
      if (!raw && key !== STORAGE_KEY_RATES) {
        raw = this._safeGetItem(STORAGE_KEY_RATES);
      }
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed === 'object') {
          return { ...BASELINE_USD_RATES, ...parsed };
        }
      }
    } catch {}
    return { ...BASELINE_USD_RATES };
  }

  _getTodayLocalDate() {
    const now = new Date();
    const y = now.getFullYear();
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  _formatFriendlyTimestamp(d = new Date()) {
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const day = String(d.getDate()).padStart(2, '0');
    const month = months[d.getMonth()];
    const year = d.getFullYear();
    let hours = d.getHours();
    const minutes = String(d.getMinutes()).padStart(2, '0');
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12;
    if (hours === 0) hours = 12;
    return `${day} ${month} ${year}, ${String(hours).padStart(2, '0')}:${minutes} ${ampm}`;
  }

  /**
   * Synchronize exchange rates if app is opened for the first time today.
   * @returns {Promise<boolean>} true if rates were updated
   */
  async checkAndFetchDailyRates() {
    const today = this._getTodayLocalDate();
    if (this.lastFetchDate === today) {
      return false;
    }
    return this.forceFetchRates();
  }

  /**
   * Force pull latest exchange rates from CDN API on demand, bypassing daily cache.
   * Updates API cache, active rates (for non-overridden), and timestamps.
   */
  async forceFetchRates() {
    const primaryUrl = 'https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json';
    const fallbackUrl = 'https://latest.currency-api.pages.dev/v1/currencies/usd.json';

    let fetchedData = null;

    try {
      const resp = await fetch(primaryUrl, { cache: 'no-cache' });
      if (resp.ok) {
        fetchedData = await resp.json();
      }
    } catch {}

    if (!fetchedData || !fetchedData.usd) {
      try {
        const resp = await fetch(fallbackUrl, { cache: 'no-cache' });
        if (resp.ok) {
          fetchedData = await resp.json();
        }
      } catch {}
    }

    if (fetchedData && fetchedData.usd) {
      const usdObj = fetchedData.usd;
      const newApiRates = { ...this.apiRates };
      const newActiveRates = { ...this.rates };

      for (const curr of SUPPORTED_CURRENCIES) {
        const key = curr.code.toLowerCase();
        if (typeof usdObj[key] === 'number' && usdObj[key] > 0) {
          newApiRates[key] = usdObj[key];
          if (!this.isManualOverride(curr.code)) {
            newActiveRates[key] = usdObj[key];
          }
        }
      }

      this.apiRates = newApiRates;
      this.rates = newActiveRates;
      const today = this._getTodayLocalDate();
      const timestamp = this._formatFriendlyTimestamp();
      this.lastFetchDate = today;
      this.lastFetchTimestamp = timestamp;

      this._safeSetItem(STORAGE_KEY_API_RATES, JSON.stringify(newApiRates));
      this._safeSetItem(STORAGE_KEY_RATES, JSON.stringify(newActiveRates));
      this._safeSetItem(STORAGE_KEY_FETCH_DATE, today);
      this._safeSetItem(STORAGE_KEY_FETCH_TIMESTAMP, timestamp);

      return true;
    }

    return false;
  }

  /**
   * Returns active base currency (from active profile, or 'INR' fallback).
   */
  getBaseCurrency() {
    try {
      if (typeof window !== 'undefined' && window.profileManager && typeof window.profileManager.getActiveProfile === 'function') {
        const p = window.profileManager.getActiveProfile();
        if (p && p.currency) return p.currency.toUpperCase();
      }
    } catch {}
    return this.baseCurrency || 'INR';
  }

  /**
   * Get dynamic effective rate of target currency relative to base currency (1 base = X target).
   */
  getRateAgainstBase(targetCode, baseCode) {
    const tKey = (targetCode || 'USD').toLowerCase();
    const bKey = (baseCode || this.getBaseCurrency()).toLowerCase();
    if (tKey === bKey) return 1.0;
    const rateTarget = this.rates[tKey] || BASELINE_USD_RATES[tKey] || 1.0;
    const rateBase = this.rates[bKey] || BASELINE_USD_RATES[bKey] || 1.0;
    if (rateBase <= 0) return 1.0;
    return rateTarget / rateBase;
  }

  /**
   * Get pure API baseline rate of target currency relative to base currency (1 base = X target).
   */
  getApiRateAgainstBase(targetCode, baseCode) {
    const tKey = (targetCode || 'USD').toLowerCase();
    const bKey = (baseCode || this.getBaseCurrency()).toLowerCase();
    if (tKey === bKey) return 1.0;
    const apiTarget = this.apiRates[tKey] || BASELINE_USD_RATES[tKey] || 1.0;
    const apiBase = this.apiRates[bKey] || BASELINE_USD_RATES[bKey] || 1.0;
    if (apiBase <= 0) return 1.0;
    return apiTarget / apiBase;
  }

  /**
   * Manually override the conversion rate for targetCode relative to baseCode (1 base = rateFromBase * target).
   */
  updateManualRateAgainstBase(targetCode, baseCode, rateFromBase) {
    const rate = Number(rateFromBase);
    if (isNaN(rate) || rate <= 0) return false;

    const tUpper = (targetCode || '').toUpperCase();
    const tLower = tUpper.toLowerCase();
    const bUpper = (baseCode || this.getBaseCurrency()).toUpperCase();
    const bLower = bUpper.toLowerCase();

    const rateBase = this.rates[bLower] || BASELINE_USD_RATES[bLower] || 1.0;
    // 1 base = rate * target => rates[target] = rate * rates[base]
    this.rates[tLower] = rate * rateBase;
    this.manualOverrides.add(tUpper);

    this._safeSetItem(this._getRatesStorageKey(), JSON.stringify(this.rates));
    this._safeSetItem(this._getOverridesStorageKey(), JSON.stringify(Array.from(this.manualOverrides)));
    return true;
  }

  /**
   * Manually override conversion rate relative to USD (or default base).
   */
  updateManualRate(code, rateToUsd) {
    return this.updateManualRateAgainstBase(code, 'USD', rateToUsd);
  }

  /**
   * Resets a specific currency to the latest API-fetched rate relative to baseCode.
   */
  resetRateToApiAgainstBase(targetCode, baseCode) {
    const tUpper = (targetCode || '').toUpperCase();
    const tLower = tUpper.toLowerCase();
    const bUpper = (baseCode || this.getBaseCurrency()).toUpperCase();
    const bLower = bUpper.toLowerCase();

    this.manualOverrides.delete(tUpper);

    const apiTarget = this.apiRates[tLower] || BASELINE_USD_RATES[tLower] || 1.0;
    const apiBase = this.apiRates[bLower] || BASELINE_USD_RATES[bLower] || 1.0;
    const rateBase = this.rates[bLower] || BASELINE_USD_RATES[bLower] || 1.0;

    this.rates[tLower] = (apiTarget / apiBase) * rateBase;

    this._safeSetItem(this._getRatesStorageKey(), JSON.stringify(this.rates));
    this._safeSetItem(this._getOverridesStorageKey(), JSON.stringify(Array.from(this.manualOverrides)));
    return true;
  }

  /**
   * Resets a specific currency to the latest API-fetched rate (or baseline).
   */
  resetRateToApi(code) {
    return this.resetRateToApiAgainstBase(code, 'USD');
  }

  /**
   * Resets all currencies to their latest API-fetched rates, clearing all manual overrides.
   */
  resetAllRatesToApi() {
    this.manualOverrides.clear();
    for (const curr of SUPPORTED_CURRENCIES) {
      const lower = curr.code.toLowerCase();
      this.rates[lower] = this.apiRates[lower] || BASELINE_USD_RATES[lower] || 1.0;
    }

    this._safeSetItem(this._getRatesStorageKey(), JSON.stringify(this.rates));
    this._safeSetItem(this._getOverridesStorageKey(), JSON.stringify([]));
    return true;
  }

  /**
   * Convert an amount from one currency to another using the cached USD-relative rates.
   * @param {number} amount
   * @param {string} fromCurrency
   * @param {string} toCurrency
   * @returns {number}
   */
  convert(amount, fromCurrency, toCurrency) {
    if (amount === null || amount === undefined || isNaN(amount)) return 0;
    const fromCode = (fromCurrency || 'INR').toLowerCase();
    const toCode = (toCurrency || 'INR').toLowerCase();
    if (fromCode === toCode) return Number(amount);

    const rateFrom = this.rates[fromCode] || BASELINE_USD_RATES[fromCode] || 1.0;
    const rateTo = this.rates[toCode] || BASELINE_USD_RATES[toCode] || 1.0;

    // Convert from source to USD base, then to target
    const inUsd = Number(amount) / rateFrom;
    return inUsd * rateTo;
  }

  /**
   * Get direct exchange rate between two currencies (1 unit of fromCurrency = X units of toCurrency).
   */
  getExchangeRate(fromCurrency, toCurrency) {
    const fromCode = (fromCurrency || 'INR').toLowerCase();
    const toCode = (toCurrency || 'INR').toLowerCase();
    if (fromCode === toCode) return 1.0;
    const rateFrom = this.rates[fromCode] || BASELINE_USD_RATES[fromCode] || 1.0;
    const rateTo = this.rates[toCode] || BASELINE_USD_RATES[toCode] || 1.0;
    return rateTo / rateFrom;
  }

  /**
   * Format a numerical amount in the given currency with proper symbol and locale rules.
   * @param {number} amount
   * @param {string} currencyCode
   * @param {object} options - { minimumFractionDigits, maximumFractionDigits, showCode, withSign }
   * @returns {string}
   */
  format(amount, currencyCode = 'INR', options = {}) {
    const curr = this.getCurrency(currencyCode);
    const val = Number(amount) || 0;
    const absVal = Math.abs(val);

    const maxDecimals = options.maximumFractionDigits !== undefined
      ? options.maximumFractionDigits
      : (options.compact ? 0 : curr.decimals);
    let minDecimals = options.minimumFractionDigits !== undefined
      ? options.minimumFractionDigits
      : (options.compact ? 0 : (curr.decimals === 0 ? 0 : 2));
    if (minDecimals > maxDecimals) {
      minDecimals = maxDecimals;
    }

    let formattedNum = absVal.toLocaleString(curr.locale, {
      minimumFractionDigits: minDecimals,
      maximumFractionDigits: maxDecimals
    });

    let sign = '';
    if (options.withSign) {
      sign = val > 0 ? '+ ' : (val < 0 ? '- ' : '');
    } else if (val < 0) {
      sign = '- ';
    }

    const codeSuffix = options.showCode ? ` ${curr.code}` : '';
    return `${sign}${curr.symbol} ${formattedNum}${codeSuffix}`;
  }

  /**
   * Compact number formatting (e.g. 1.2k, 1.5L, 2.4M).
   */
  formatCompact(amount, currencyCode = 'INR') {
    const curr = this.getCurrency(currencyCode);
    const val = Number(amount) || 0;
    const abs = Math.abs(val);
    const sign = val < 0 ? '-' : '';

    if (curr.code === 'INR') {
      if (abs >= 10000000) return `${sign}${curr.symbol} ${(abs / 10000000).toFixed(1)}Cr`;
      if (abs >= 100000) return `${sign}${curr.symbol} ${(abs / 100000).toFixed(1)}L`;
      if (abs >= 1000) return `${sign}${curr.symbol} ${(abs / 1000).toFixed(1)}k`;
      return `${sign}${curr.symbol} ${Math.round(abs)}`;
    } else {
      if (abs >= 1000000000) return `${sign}${curr.symbol} ${(abs / 1000000000).toFixed(1)}B`;
      if (abs >= 1000000) return `${sign}${curr.symbol} ${(abs / 1000000).toFixed(1)}M`;
      if (abs >= 1000) return `${sign}${curr.symbol} ${(abs / 1000).toFixed(1)}k`;
      return `${sign}${curr.symbol} ${Math.round(abs)}`;
    }
  }
}

// Singleton global instance
window.CurrencyEngine = new CurrencyEngineClass();

if (typeof module !== 'undefined' && module.exports) {
  module.exports = {
    CurrencyEngine: window.CurrencyEngine,
    SUPPORTED_CURRENCIES,
    BASELINE_USD_RATES
  };
}
