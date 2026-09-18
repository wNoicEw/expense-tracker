// Test script for Web App Transaction Editing & Date/Time logic
const assert = require('assert');
const path = require('path');

const MoneyTrackerCalendar = require(path.join('..', 'js', 'calendar.js'));
const MoneyTrackerTimePicker = require(path.join('..', 'js', 'timepicker.js'));

// Simulate transaction storage and update logic
const transactions = [];

function createManualTxn({ description, amount, type, category, accountId, date, time, paymentMode, referenceNo, notes }) {
  const now = new Date();
  // Matches js/app.js: toLocaleDateString('en-CA') gives local YYYY-MM-DD, not the UTC-shifted
  // date that toISOString() would give near midnight in timezones ahead of UTC.
  const defaultDate = now.toLocaleDateString('en-CA');
  const defaultTime = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');

  const finalDate = date || defaultDate;
  const finalTime = time || defaultTime;

  assert(amount > 0, 'Amount must be positive');
  assert(description && description.trim().length > 0, 'Description is required');

  const txn = {
    id: 'txn_manual_' + Date.now(),
    date: finalDate,
    time: finalTime,
    amount,
    type,
    category: category || 'Uncategorized',
    accountId: accountId || '',
    description: description.trim(),
    paymentMode: paymentMode || (type === 'income' ? 'Direct Credit' : 'Cash/Manual'),
    referenceNo: referenceNo || ('MANUAL_' + Date.now().toString().slice(-6)),
    notes: notes || '',
    createdAt: new Date().toISOString()
  };

  transactions.push(txn);
  return txn;
}

function updateTxn(id, updates) {
  const idx = transactions.findIndex(t => t.id === id);
  assert(idx !== -1, 'Transaction not found');

  assert(updates.amount > 0, 'Amount must be positive');
  assert(updates.description && updates.description.trim().length > 0, 'Description required');

  transactions[idx] = {
    ...transactions[idx],
    ...updates,
    updatedAt: new Date().toISOString()
  };
  return transactions[idx];
}

console.log('Testing manual transaction creation with explicit date & time...');
const t1 = createManualTxn({
  description: 'Coffee at Blue Tokai',
  amount: 240,
  type: 'expense',
  category: 'Food & Dining',
  date: '2026-09-17',
  time: '15:30',
  notes: '#coffee'
});
assert.strictEqual(t1.date, '2026-09-17');
assert.strictEqual(t1.time, '15:30');
assert.strictEqual(t1.amount, 240);
console.log('✔ Manual transaction with explicit date/time passed');

console.log('Testing default date & time when omitted...');
const t2 = createManualTxn({
  description: 'Salary Advance',
  amount: 50000,
  type: 'income',
  category: 'Salary & Professional'
});
const todayStr = new Date().toLocaleDateString('en-CA');
assert.strictEqual(t2.date, todayStr);
assert(t2.time.match(/^\d{2}:\d{2}$/), 'Time should be formatted as HH:mm');
console.log('✔ Default date/time fallback passed');

console.log('Testing editing an existing transaction...');
const updated = updateTxn(t1.id, {
  description: 'Specialty Pour-over at Blue Tokai',
  amount: 320,
  date: '2026-09-18',
  time: '16:45',
  category: 'Food & Dining',
  notes: '#specialty #pourover'
});
assert.strictEqual(updated.description, 'Specialty Pour-over at Blue Tokai');
assert.strictEqual(updated.amount, 320);
assert.strictEqual(updated.date, '2026-09-18');
assert.strictEqual(updated.time, '16:45');
assert.strictEqual(updated.notes, '#specialty #pourover');
assert(updated.updatedAt, 'Should have updatedAt timestamp');
console.log('✔ Editing transaction details passed');

console.log('Testing calendar date-math (js/calendar.js)...');
const cal = MoneyTrackerCalendar.prototype;
assert.strictEqual(cal.isLeapYear(2024), true, '2024 is a leap year');
assert.strictEqual(cal.isLeapYear(2023), false, '2023 is not a leap year');
assert.strictEqual(cal.isLeapYear(1900), false, '1900 is not a leap year (divisible by 100, not 400)');
assert.strictEqual(cal.isLeapYear(2000), true, '2000 is a leap year (divisible by 400)');
assert.strictEqual(cal.getDaysInMonth(2024, 1), 29, 'Feb 2024 (leap) has 29 days');
assert.strictEqual(cal.getDaysInMonth(2023, 1), 28, 'Feb 2023 (non-leap) has 28 days');
assert.strictEqual(cal.getDaysInMonth(2026, 0), 31, 'Jan has 31 days');
assert.strictEqual(cal.formatDate(2026, 0, 5), '2026-01-05', 'formatDate pads month/day');
const parsed = cal.parseDate('2026-09-17');
assert.deepStrictEqual(parsed, { year: 2026, month: 8, day: 17 }, 'parseDate is 0-indexed month, round-trips formatDate');
assert.strictEqual(cal.parseDate('not-a-date'), null, 'parseDate rejects malformed input');
console.log('✔ Calendar date-math passed');

console.log('Testing timepicker time-math (js/timepicker.js)...');
const tp = MoneyTrackerTimePicker.prototype;
assert.deepStrictEqual(tp.parseTime('00:00'), { hour: 12, minute: 0, period: 'AM' }, 'Midnight is 12:00 AM');
assert.deepStrictEqual(tp.parseTime('12:00'), { hour: 12, minute: 0, period: 'PM' }, 'Noon is 12:00 PM');
assert.deepStrictEqual(tp.parseTime('13:05'), { hour: 1, minute: 5, period: 'PM' }, '13:05 is 1:05 PM');
assert.deepStrictEqual(tp.parseTime('23:59'), { hour: 11, minute: 59, period: 'PM' }, '23:59 is 11:59 PM');
assert.strictEqual(tp.get24HourString(12, 0, 'AM'), '00:00', '12:00 AM round-trips to 00:00');
assert.strictEqual(tp.get24HourString(12, 0, 'PM'), '12:00', '12:00 PM round-trips to 12:00');
assert.strictEqual(tp.get24HourString(1, 5, 'PM'), '13:05', '1:05 PM round-trips to 13:05');
console.log('✔ Timepicker time-math passed');

console.log('ALL WEB LOGIC TESTS PASSED!');
