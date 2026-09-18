/**
 * Local-calendar date helpers.
 * Transactions store dates as 'YYYY-MM-DD' (a calendar day, not an instant). `new Date('YYYY-MM-DD')`
 * parses as UTC midnight and `toISOString()` converts back through UTC, which shifts the day
 * in any timezone ahead of or behind UTC (e.g. IST, UTC+5:30). Everything here stays local.
 */
const DateUtil = {
  toISODate(d) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  },

  today() {
    return this.toISODate(new Date());
  },

  // 'YYYY-MM-DD' (optionally followed by a time) -> Date at LOCAL midnight; Invalid Date if unreadable
  parseLocal(str) {
    const m = String(str || '').match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (!m) return new Date(NaN);
    return new Date(+m[1], +m[2] - 1, +m[3]);
  },

  // Newest first; the optional time field breaks same-day ties
  compareTxnDesc(a, b) {
    const ka = `${String(a.date || '').slice(0, 10)} ${a.time || '00:00'}`;
    const kb = `${String(b.date || '').slice(0, 10)} ${b.time || '00:00'}`;
    return kb.localeCompare(ka);
  }
};

if (typeof window !== 'undefined') {
  window.DateUtil = DateUtil;
}

if (typeof module !== 'undefined' && module.exports) {
  module.exports = DateUtil;
}
