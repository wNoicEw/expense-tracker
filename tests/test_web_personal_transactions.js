const assert = require('assert');
const path = require('path');

const FriendsManager = require(path.join(__dirname, '..', 'js', 'friends.js'));

// Mock localStorage
const mockStorage = new Map();
global.localStorage = {
  getItem: (key) => mockStorage.get(key) || null,
  setItem: (key, val) => mockStorage.set(key, String(val)),
  removeItem: (key) => mockStorage.delete(key),
  clear: () => mockStorage.clear()
};

global.window = {
  profileManager: {
    getActiveProfile: () => ({ id: 'prof_test', name: 'Test User', currency: 'INR' })
  },
  DateUtil: {
    compareTxnDesc: (a, b) => String(b.date || '').localeCompare(String(a.date || ''))
  },
  CurrencyEngine: {
    convert: (amt) => amt
  }
};

const fm = new FriendsManager();

console.log('Testing FriendsManager...');

// 1. UPI ID & Payee Name Extraction from various statement formats
const t1 = {
  id: 't1',
  amount: 500,
  type: 'expense',
  category: 'Friend',
  rawNarration: 'UPI/DR/426912345678/RAHUL SHARMA/OKAXIS/rahul@okaxis/Payment',
  description: 'Rahul Sharma',
  date: '2026-09-20'
};

const upi1 = fm.extractUpiId(t1.rawNarration);
assert.strictEqual(upi1, 'rahul@okaxis');
const name1 = fm.extractFriendName(t1);
assert.strictEqual(name1, 'Rahul Sharma');
const key1 = fm.getFriendKey(t1);
assert.strictEqual(key1, 'upi:rahul@okaxis');
console.log('✔ UPI statement narration extraction passed');

// 2. Navi / GPay format: "Paid to PRIYA PATEL — Snacks"
const t2 = {
  id: 't2',
  amount: 250,
  type: 'expense',
  category: 'Friend',
  rawNarration: 'Paid to PRIYA PATEL — Snacks',
  description: 'Priya Patel (Snacks)',
  date: '2026-09-21'
};
const name2 = fm.extractFriendName(t2);
assert.strictEqual(name2, 'Priya Patel');
const key2 = fm.getFriendKey(t2);
assert.strictEqual(key2, 'name:priya_patel');
console.log('✔ Navi/GPay pattern extraction passed');

// 3. Balance Calculation (Outgoing vs Incoming)
// User paid Rahul ₹500 (expense)
// Rahul paid user ₹200 (income)
const t3 = {
  id: 't3',
  amount: 200,
  type: 'income',
  category: 'Friend',
  rawNarration: 'Received from rahul@okaxis',
  description: 'Rahul Sharma',
  date: '2026-09-22'
};

const profiles = fm.getFriendProfiles([t1, t2, t3]);
assert.strictEqual(profiles.length, 2);

const rahulProfile = profiles.find(p => p.id === 'upi:rahul@okaxis');
assert(rahulProfile, 'Rahul profile must exist');
assert.strictEqual(rahulProfile.totalOutgoing, 500);
assert.strictEqual(rahulProfile.totalIncoming, 200);
assert.strictEqual(rahulProfile.netBalance, 300); // 500 - 200 = +300 (Rahul owes you ₹300)
assert.strictEqual(rahulProfile.status, 'owes_you');
assert.strictEqual(rahulProfile.transactionCount, 2);
console.log('✔ Friend balance calculation (You get) passed');

// 4. Friend Profile Renaming (Custom name override)
fm.setFriendCustomName('upi:rahul@okaxis', 'Rahul Bro');
const profilesAfterRename = fm.getFriendProfiles([t1, t2, t3]);
const renamedRahul = profilesAfterRename.find(p => p.id === 'upi:rahul@okaxis');
assert.strictEqual(renamedRahul.displayName, 'Rahul Bro');
assert.strictEqual(renamedRahul.isCustomName, true);
console.log('✔ Custom profile renaming passed');

// 5. Settling up balance
const t4 = {
  id: 't4',
  amount: 300,
  type: 'income',
  category: 'Friend',
  rawNarration: 'Settlement from rahul@okaxis',
  description: 'Settlement',
  date: '2026-09-23'
};
const profilesSettled = fm.getFriendProfiles([t1, t3, t4]);
const settledRahul = profilesSettled.find(p => p.id === 'upi:rahul@okaxis');
assert.strictEqual(settledRahul.netBalance, 0);
assert.strictEqual(settledRahul.status, 'settled');
console.log('✔ Settled balance detection passed');

// 6. Non-Friend transactions are ignored
const nonFriendTxn = {
  id: 'nf1',
  amount: 1000,
  type: 'expense',
  category: 'Groceries & Mart',
  description: 'Blinkit',
  date: '2026-09-23'
};
const profilesWithMisc = fm.getFriendProfiles([t1, t2, nonFriendTxn]);
assert.strictEqual(profilesWithMisc.length, 2);
console.log('✔ Non-friend transactions exclusion passed');

console.log('\nALL PERSONAL TRANSACTIONS & FRIEND TESTS PASSED!\n');
