// Regression tests for the parser / dedupe / categorizer / DateUtil fixes.
// Run under a non-UTC zone to exercise the date-shift bugs:
//   TZ=Asia/Kolkata node tests/test_web_regressions.js
const assert = require('assert');
const StatementParser = require('../js/parser.js');
const DuplicateDetector = require('../js/duplicateDetector.js');
const Categorizer = require('../js/categorizer.js');
const DateUtil = require('../js/dateutil.js');

const p = new StatementParser();
const d = new DuplicateDetector();
const c = new Categorizer();
let n = 0;
const t = (name, fn) => { fn(); n++; console.log('✔', name); };

t('dates: local, no UTC shift, day-first', () => {
  assert.strictEqual(p.normalizeDate('05/03/2024'), '2024-03-05');
  assert.strictEqual(p.normalizeDate('15 Jan 2024'), '2024-01-15');
  assert.strictEqual(p.normalizeDate('Jan 15, 2024'), '2024-01-15');
  assert.strictEqual(p.normalizeDate('2024-01-15'), '2024-01-15');
  assert.strictEqual(p.normalizeDate('01/15/2024'), '2024-01-15'); // swap only when day-first is impossible
  assert.strictEqual(p.normalizeDate(new Date(2024, 0, 1)), '2024-01-01'); // local midnight must not become Dec 31
  assert.strictEqual(p.normalizeDate(45292), '2024-01-01'); // Excel serial
});

t('dates: unreadable / impossible -> null (never silently "today")', () => {
  assert.strictEqual(p.normalizeDate('31/02/2024'), null);
  assert.strictEqual(p.normalizeDate('garbage'), null);
  assert.strictEqual(p.normalizeDate(''), null);
  assert.strictEqual(p.normalizeDate('01/01/1850'), null);
});

t('DateUtil: round-trips local calendar day and sorts newest first', () => {
  assert.strictEqual(DateUtil.toISODate(DateUtil.parseLocal('2024-03-01')), '2024-03-01');
  assert.ok(isNaN(DateUtil.parseLocal('nope').getTime()));
  const rows = [{ date: '2024-01-01', time: '09:00' }, { date: '2024-01-02' }, { date: '2024-01-01', time: '18:00' }];
  rows.sort(DateUtil.compareTxnDesc);
  assert.deepStrictEqual(rows.map(r => r.date + (r.time || '')), ['2024-01-02', '2024-01-0118:00', '2024-01-0109:00']);
});

t('amount: date/time digits are never read as the amount', () => {
  assert.strictEqual(p.extractAmount('15 Jan 2024 Swiggy Rs. 1,250.00', '15 Jan 2024').value, 1250);
  assert.strictEqual(p.extractAmount('15/01/2024 10:32 AM UPI-ZOMATO 349.50', '15/01/2024').value, 349.5);
  assert.strictEqual(p.extractAmount('Ref 123456789012 no amount here'), null);
  assert.strictEqual(p.extractAmount('₹ 0.00'), null);
});

t('amount cells: Cr/Dr, accounting negatives, symbols', () => {
  assert.deepStrictEqual(p.parseAmountCell('₹1,234.50 Cr'), { value: 1234.5, suffix: 'cr' });
  assert.deepStrictEqual(p.parseAmountCell('(500.00)'), { value: -500, suffix: null });
  assert.strictEqual(p.parseAmountCell('12,00,000.00 Dr').value, 1200000);
});

t('credit/debit headers are anchored ("Description"/"Address" are not Cr/Dr)', () => {
  assert.ok(!p.isCreditHeader('Description'));
  assert.ok(!p.isDebitHeader('Address'));
  assert.ok(p.isCreditHeader('Credit Amount'));
  assert.ok(p.isCreditHeader('Cr'));
  assert.ok(p.isDebitHeader('Withdrawals'));
  assert.ok(p.isDebitHeader('Dr.'));
});

t('resolveAmountCells: credit column stays income; unsigned amount is undetermined, not expense', () => {
  assert.deepStrictEqual(p.resolveAmountCells('', '50,000.00', null, 'NEFT'), { amount: 50000, explicitType: 'income' });
  assert.deepStrictEqual(p.resolveAmountCells('200', '', null, 'x'), { amount: 200, explicitType: 'expense' });
  assert.deepStrictEqual(p.resolveAmountCells('', '', '750', 'Some merchant'), { amount: 750, explicitType: null });
  assert.deepStrictEqual(p.resolveAmountCells('', '', '-750', 'x'), { amount: 750, explicitType: 'expense' });
  assert.deepStrictEqual(p.resolveAmountCells('', '', '750 Cr', 'x'), { amount: 750, explicitType: 'income' });
});

t('raw table rows use the safe amount extractor', () => {
  const { records } = p.parseRawTableRows([
    ['15/01/2024', 'UPI-SWIGGY', '1,250.00', 'Dr'],
    ['16/01/2024', 'SALARY', '85,000.00', 'Cr']
  ]);
  assert.strictEqual(records[0].amount, 1250);
  assert.strictEqual(records[0].explicitType, 'expense');
  assert.strictEqual(records[1].explicitType, 'income');
});

const tx = (o) => ({
  id: Math.random().toString(36), date: '2024-01-15', amount: 200, type: 'expense',
  description: 'Uber', rawNarration: 'UPI-UBER', referenceNo: '', ...o
});

t('dedupe: opposite direction is never a duplicate', () => {
  assert.strictEqual(d.compareTransactions(tx({}), tx({ type: 'income' })).isMatch, false);
});

t('dedupe: cross-currency transactions with identical amount are NEVER duplicates', () => {
  const usdTx = tx({ amount: 10, currency: 'USD', description: 'Coffee' });
  const inrTx = tx({ amount: 10, currency: 'INR', description: 'Coffee' });
  assert.strictEqual(d.compareTransactions(usdTx, inrTx).isMatch, false);
});

t('dedupe: same UTR merges, but transfer legs never auto-merge (confidence < 95)', () => {
  const a = tx({ referenceNo: '412345678901' });
  const b = tx({ referenceNo: '412345678901' });
  assert.ok(d.compareTransactions(a, b).confidence >= 95);
  const ta = tx({ type: 'transfer', referenceNo: '412345678901' });
  const tb = tx({ type: 'transfer', referenceNo: '412345678901' });
  assert.ok(d.compareTransactions(ta, tb).confidence < 95);
});

t('dedupe: two identical rides in ONE statement both survive; same real ref collapses', () => {
  const rides = [tx({ id: 'a' }), tx({ id: 'b' })];
  assert.strictEqual(d.filterExactDuplicates(rides, []).filteredTransactions.length, 2);
  const withRef = [tx({ id: 'a', referenceNo: '412345678901' }), tx({ id: 'b', referenceNo: '412345678901' })];
  assert.strictEqual(d.filterExactDuplicates(withRef, []).filteredTransactions.length, 1);
});

t('dedupe: re-import of identical row is skipped; different merchant is kept', () => {
  const existing = [tx({ id: 'db1' })];
  assert.strictEqual(d.filterExactDuplicates([tx({})], existing).exactDuplicatesCount, 1);
  assert.strictEqual(
    d.filterExactDuplicates([tx({ description: 'Ola', rawNarration: 'UPI-OLA' })], existing).exactDuplicatesCount, 0);
});

t('categorizer: newest learned rule wins', () => {
  const rules = [
    { id: 'old', pattern: 'amazon', category: 'Shopping & E-Comm', createdAt: '2024-01-01' },
    { id: 'new', pattern: 'amazon', category: 'Groceries & Mart', createdAt: '2024-06-01' }
  ];
  assert.strictEqual(c.orderRules(rules)[0].id, 'new');
});

// ---- Real-statement regressions (synthetic data shaped like the SBI and Navi PDFs) ----
const row = (...cells) => ({ words: cells.map(([text, x]) => ({ text, x })) });

t('header row repeated on every PDF page is recognised, real narrations are not', () => {
  assert.ok(p.isColumnHeaderRow('Date Narration Chq./Ref.No. Value Dt Withdrawal Amt. Deposit Amt. Balance'));
  assert.ok(p.isColumnHeaderRow('Date Description Debit Credit Balance'));
  assert.ok(!p.isColumnHeaderRow('UPI/DR/123456789012/RAJU/Credit Card bill'));
  assert.ok(!p.isColumnHeaderRow('Transfer to savings balance'));
});

t('Navi: "Bill payment of …" rows are kept, payee excludes the account column, page-header text is ignored', () => {
  const rows = [
    row(['Date', 28], ['Transaction details', 115], ['Account', 382], ['Amount', 520]),
    row(['14 Aug 2026', 28], ['Bill payment of HDFC Credit Card', 115], ['State Bank of India', 382], ['₹4,500', 536]),
    row(['12:39 AM', 28], ['UPI txn ID: 100000000001', 115], ['- 2105', 382]),
    row(['13 Aug 2026', 28], ['Paid to SOME SHOP', 115], ['HDFC Bank RuPay', 382], ['₹75', 550]),
    row(['12:56 PM', 28], ['UPI txn ID: 100000000002', 115], ['Credit Card - XX99', 382]),
    row(['Note: Paid via Navi UPI', 115]),
    // a long payee wraps onto the time row; the txn ID follows on the next row; then a page break
    row(['15 Aug 2026', 28], ['Paid to LONG NAME PRIVATE', 115], ['HDFC Bank RuPay', 382], ['₹900', 543]),
    row(['1:05 AM', 28], ['LIMITED', 115], ['Credit Card - XX99', 382]),
    row(['UPI txn ID: 100000000003', 115]),
    row(['SOME USER NAME', 483]),
    row(['+91 0000000000', 482]),
    row(['Date', 28], ['Transaction details', 115], ['Account', 382], ['Amount', 520]),
    row(['12 Aug 2026', 28], ['Received from A FRIEND', 115], ['State Bank of India', 382], ['₹1,000.50', 530]),
    row(['9:00 AM', 28], ['UPI txn ID: 111111111111', 115], ['- 2105', 382])
  ];
  const { records } = p.parseNaviBlocks(rows, '', {});
  assert.strictEqual(records.length, 4);
  assert.deepStrictEqual(records.map(r => r.amount), [4500, 75, 900, 1000.5]);
  assert.strictEqual(records[0].narration, 'Bill payment of HDFC Credit Card');
  assert.strictEqual(records[0].accountInfo, 'State Bank of India - 2105');
  assert.strictEqual(records[1].narration, 'Paid to SOME SHOP — Paid via Navi UPI');
  assert.strictEqual(records[1].accountInfo, 'HDFC Bank RuPay Credit Card - XX99');
  assert.strictEqual(records[2].narration, 'Paid to LONG NAME PRIVATE LIMITED');
  assert.strictEqual(records[2].referenceNo, '100000000003');
  assert.strictEqual(records[2].accountInfo, 'HDFC Bank RuPay Credit Card - XX99');
  assert.strictEqual(records[3].explicitType, 'income');
  assert.ok(records.every(r => !/USER NAME|\+91|Transaction details/.test(r.narration)));
});

t('account detection: SBI bank statement is a bank account keyed by Account Number, not CIF, not a wallet', () => {
  // pdf.js emits header labels first and the values after, in the same order
  const text = 'STATEMENT OF ACCOUNT State Bank of India CIF Number : Account Number : Account Status : Currency : '
    + '12345678901 98765432105 OPEN INR Statement From : 01-04-2025 to 31-03-2026 '
    + '05/04/2025 DEP TFR UPI/CR/1/x/paytm/gpay 06/04/2025 WDL TFR UPI/DR/2/y';
  const m = p.extractAccountMetadata(text, 'statement.pdf');
  assert.strictEqual(m.type, 'bank');
  assert.strictEqual(m.last4, '2105');
});

t('account detection: Navi statement is a UPI wallet, not "State Bank of India" from its Account column', () => {
  const m = p.extractAccountMetadata('Transaction statement from 20 May 2026 Paid via Navi UPI State Bank of India - 2105', 'Navi_Statement.pdf');
  assert.strictEqual(m.bankName, 'Navi UPI');
  assert.strictEqual(m.type, 'wallet');
});

t('Navi: glued bank account "Paid to JOHN DOE HDFC Bank - 1234" splits payee and extracts HDFC Bank account', () => {
  const rows = [
    row(['Date', 28], ['Transaction details', 115], ['Account', 382], ['Amount', 520]),
    row(['27 Aug 2026', 28], ['Paid to JOHN DOE HDFC Bank - 1234', 115], ['₹70.00', 536]),
    row(['12:00 AM', 28], ['UPI txn ID: 100000000001', 115]),
    row(['Note: Paid via Navi UPI', 115])
  ];
  const { records } = p.parseNaviBlocks(rows, '', {});
  assert.strictEqual(records.length, 1);
  assert.strictEqual(records[0].amount, 70);
  assert.strictEqual(records[0].narration, 'Paid to JOHN DOE — Paid via Navi UPI');
  assert.strictEqual(records[0].accountInfo, 'HDFC Bank - 1234');
});

t('categorizer: cleanIndianTransactionTitle skips boilerplate "Paid via Navi UPI" note', () => {
  const title = c.cleanIndianTransactionTitle('Paid to JOHN DOE — Paid via Navi UPI');
  assert.strictEqual(title, 'John Doe');
});

t('RuPay card: two-digit masked suffix "XX99" is captured', () => {
  const m = p.detectRuPayCC('HDFC Bank RuPay Credit Card - XX99');
  assert.strictEqual(m.last4, '99');
  assert.ok(/hdfc/i.test(m.bankName));
});

// ---- UPI apps (PhonePe / Google Pay / Paytm): per-row paying account, synthetic rows shaped like the public parsers' layouts ----
t('PhonePe: "Paid by XXXXXXXX1234" becomes accountInfo, not narration; wallet / missing line -> empty', () => {
  const rows = [
    row(['Date', 28], ['Transaction Details', 110], ['Type', 400], ['Amount', 480]),
    row(['Aug 10, 2026', 28], ['Paid to SOME SHOP', 110], ['DEBIT', 400], ['₹100', 480]),
    row(['01:00 PM', 28], ['Transaction ID T2608100000000000001', 110]),
    row(['UTR No. 100000000001', 110]),
    row(['Paid by XXXXXXXX1234', 110]),
    row(['Aug 09, 2026', 28], ['Paid to WALLET SHOP', 110], ['DEBIT', 400], ['₹140', 480]),
    row(['02:00 PM', 28], ['UTR No. 100000000002 Paid by PhonePe Wallet', 110]),
    row(['Aug 08, 2026', 28], ['Received from A FRIEND', 110], ['CREDIT', 400], ['₹500', 480]),
    row(['03:00 PM', 28], ['Transaction ID T2608080000000000003', 110]),
    row(['Credited to XXXXXXXX1234', 110]),
    row(['Aug 07, 2026', 28], ['Paid to NO INFO SHOP', 110], ['DEBIT', 400], ['₹150', 480])
  ];
  const { records } = p.parsePhonePePDF(rows, '', {});
  assert.deepStrictEqual(records.map(r => r.accountInfo), ['XXXXXXXX1234', '', 'XXXXXXXX1234', '']);
  assert.strictEqual(records[0].narration, 'Paid to SOME SHOP');
  assert.strictEqual(records[0].referenceNo, '100000000001');
  assert.strictEqual(records[2].explicitType, 'income');
});

t('Google Pay: year wrapped onto the 2nd row is re-joined; "Paid by <bank> <last4>" / received "Paid to <bank> <last4>" become accountInfo', () => {
  const rows = [
    row(['Date & time', 28], ['Transaction details', 110], ['Amount', 480]),
    row(['10 Aug,', 28], ['Paid to SOME SHOP', 110], ['₹1,100', 480]),
    row(['2026', 28], ['UPI Transaction ID: 100000000001', 110]),
    row(['01:00 PM', 28], ['Paid by HDFC Bank 1234', 110]),
    row(['09 Aug,', 28], ['Received from A FRIEND', 110], ['₹500', 480]),
    row(['2026', 28], ['UPI Transaction ID: 100000000002', 110]),
    row(['02:00 PM', 28], ['Paid to Punjab National Bank 4321', 110]),
    row(['08 Aug,', 28], ['Paid to CARD SHOP', 110], ['₹130', 480]),
    row(['2026', 28], ['UPI Transaction ID: 100000000003', 110]),
    row(['03:00 PM', 28], ['Paid by HDFC Bank Credit Card 4455', 110]),
    row(['07 Aug 2026', 28], ['Paid to ONE ROW SHOP', 110], ['₹90', 480]) // single-row date, no account line
  ];
  const { records } = p.parseUPIAppPDF(rows, '', { bankName: 'Google Pay' });
  assert.deepStrictEqual(records.map(r => r.date), ['2026-08-10', '2026-08-09', '2026-08-08', '2026-08-07']);
  assert.deepStrictEqual(records.map(r => r.amount), [1100, 500, 130, 90]);
  assert.deepStrictEqual(records.map(r => r.accountInfo),
    ['HDFC Bank 1234', 'Punjab National Bank 4321', 'HDFC Bank Credit Card 4455 on UPI', '']);
  assert.strictEqual(records[0].narration, 'Paid to SOME SHOP');
  assert.strictEqual(records[0].referenceNo, '100000000001');
  assert.strictEqual(records[1].explicitType, 'income');
});

t('Google Pay: skips statement period range header, recognizes Self transfer as transfer, and handles 2-digit masked RuPay card', () => {
  const rows = [
    row(['Transaction statement', 28]),
    row(['01 March 2026 - 31 August 2026', 28], ['₹50,000.00', 400], ['₹20,000.00', 480]), // Period range header to skip
    row(['Date & time', 28], ['Transaction details', 110], ['Amount', 480]),
    row(['01 Mar, 2026 Paid to SOME SHOP ₹400', 28]),
    row(['12:04 PM UPI Transaction ID: 100000000001', 28]),
    row(['Paid by HDFC Bank XX99 | RuPay credit card', 28]),
    row(['03 Mar, 2026 Received from A FRIEND ₹982', 28]),
    row(['11:11 AM UPI Transaction ID: 100000000002', 28]),
    row(['Paid to State Bank of India 1234', 28]),
    row(['06 Apr, 2026 Self transfer to State Bank of India 1234 ₹30,000', 28]),
    row(['10:01 AM UPI Transaction ID: 100000000003', 28]),
    row(['Paid by HDFC Bank 5678', 28])
  ];
  const { records } = p.parseUPIAppPDF(rows, '', { bankName: 'Google Pay' });
  assert.strictEqual(records.length, 3);
  assert.deepStrictEqual(records.map(r => r.date), ['2026-03-01', '2026-03-03', '2026-04-06']);
  assert.deepStrictEqual(records.map(r => r.amount), [400, 982, 30000]);
  assert.deepStrictEqual(records.map(r => r.explicitType), ['expense', 'income', 'transfer']);
  assert.strictEqual(records[0].accountInfo, 'HDFC Bank XX99 | RuPay credit card on UPI');
  assert.strictEqual(records[1].accountInfo, 'State Bank of India 1234');
  assert.strictEqual(records[2].accountInfo, 'HDFC Bank 5678');
});

t('Paytm: "Your Account" column (incl. wrapped text) and block-format "Paid from" lines become accountInfo', () => {
  const table = [
    row(['Date', 28], ['Transaction Details', 110], ['Your Account', 360], ['Amount', 500]),
    row(['10/08/2026', 28], ['Paid to SOME SHOP', 110], ['State Bank Of India', 360], ['100.00', 500]),
    row(['- 1234', 360]),
    row(['09/08/2026', 28], ['Paid to WALLET SHOP', 110], ['Paytm Wallet', 360], ['120.00', 500]),
    row(['08/08/2026', 28], ['Paid to CARD SHOP', 110], ['HDFC Bank RuPay Credit Card - XX44', 360], ['130.00', 500])
  ];
  const t1 = p.parsePaytmPDF(table, '', { bankName: 'Paytm' }).records;
  assert.deepStrictEqual(t1.map(r => r.accountInfo), ['State Bank Of India - 1234', '', 'HDFC Bank RuPay Credit Card - XX44 on UPI']);
  assert.strictEqual(t1[0].narration, 'Paid to SOME SHOP');

  const block = [
    row(['10 Aug 2026', 28], ['Paid to SOME SHOP', 110], ['₹100', 480]),
    row(['Paid from HDFC Bank 1234', 110]),
    row(['09 Aug 2026', 28], ['Paid to NO INFO SHOP', 110], ['₹120', 480])
  ];
  const t2 = p.parsePaytmPDF(block, '', { bankName: 'Paytm' }).records;
  assert.deepStrictEqual(t2.map(r => r.accountInfo), ['HDFC Bank 1234', '']);
  assert.strictEqual(t2[0].narration, 'Paid to SOME SHOP');
});

t('non-UPI table parsing is untouched: no accountInfo key without the account column option', () => {
  const rows = [
    row(['Date', 28], ['Narration', 110], ['Debit', 360], ['Credit', 420]),
    row(['10/08/2026', 28], ['UPI-SOME SHOP', 110], ['100.00', 360])
  ];
  const { records } = p.parseGenericTablePDF(rows, '', { bankName: 'HDFC Bank' });
  assert.strictEqual(records.length, 1);
  assert.ok(!('accountInfo' in records[0]));
});

// Full parseFile normalisation (account resolution) with a stubbed account store that mirrors accounts.js matching
const runFile = async (name, parse) => {
  const made = [];
  global.window = {
    categorizer: c,
    db: { getAll: async () => [] },
    accountsManager: {
      getOrCreateAccountFromStatement: async (m) => {
        let a = made.find(x => x.type === m.type && m.last4 !== '0000' && x.last4 === m.last4) ||
                made.find(x => x.bankName === m.bankName && x.type === m.type);
        if (!a) { a = { id: 'acc' + made.length, type: m.type, last4: m.last4, bankName: m.bankName, name: m.name }; made.push(a); }
        return a.id;
      }
    }
  };
  const q = new StatementParser();
  q.parsePDF = async () => parse(q);
  const res = await q.parseFile({ name, size: 1 }, null);
  const by = {};
  res.transactions.forEach(x => {
    const a = made.find(m => m.id === x.accountId);
    const k = a.type === 'wallet' ? 'wallet' : `${a.type}:${a.last4}`;
    (by[k] = by[k] || []).push(x);
  });
  return { by, made, txns: res.transactions };
};
const counts = (by) => Object.fromEntries(Object.entries(by).map(([k, v]) => [k, v.length]));

const upiFile = async (name, bank, profile, parse, bill) => {
  const meta = p.extractAccountMetadata(`${bank} UPI statement`, name);
  const { by, made, txns } = await runFile(name, (q) => ({ ...parse(q), accountMetadata: meta, profile }));
  // 2 txns on bank 1234 (+ received and bill payment), 1 on bank 4321, 1 on the card, the rest on the default wallet
  assert.deepStrictEqual(counts(by), { 'bank:1234': 4, 'bank:4321': 1, 'credit_card:4455': 1, wallet: 2 });
  assert.strictEqual(made.filter(m => m.type === 'credit_card').length, 1); // paying a card bill never creates a card account
  const billTxn = txns.find(x => /Bill payment of HDFC Credit Card/.test(x.rawNarration));
  assert.strictEqual(billTxn.type, 'transfer');
  assert.notStrictEqual(billTxn.explicitType, 'income'); // "Credit" in the payee is not the CREDIT type column
  assert.strictEqual(made.find(m => m.id === billTxn.accountId).last4, '1234');
  assert.ok(by['credit_card:4455'][0].paymentMode.includes('RuPay Credit Card'));
};

const phonepeRows = [
  ['Aug 10, 2026', 'Paid to SHOP A', 'DEBIT', 100, 'Paid by XXXXXXXX1234'],
  ['Aug 10, 2026', 'Paid to SHOP B', 'DEBIT', 110, 'Paid by XXXXXXXX1234'],
  ['Aug 09, 2026', 'Paid to SHOP C', 'DEBIT', 120, 'Paid by XX4321'],
  ['Aug 08, 2026', 'Paid to SHOP D', 'DEBIT', 130, 'Paid by HDFC Bank RuPay Credit Card XX4455'],
  ['Aug 07, 2026', 'Paid to SHOP E', 'DEBIT', 140, 'Paid by PhonePe Wallet'],
  ['Aug 06, 2026', 'Paid to SHOP F', 'DEBIT', 150, null],
  ['Aug 05, 2026', 'Received from A FRIEND', 'CREDIT', 500, 'Credited to XXXXXXXX1234'],
  ['Aug 04, 2026', 'Bill payment of HDFC Credit Card', 'DEBIT', 4500, 'Paid by XXXXXXXX1234']
].flatMap(([d, det, type, amt, inst], i) => [
  row([d, 28], [det, 110], [type, 400], [`₹${amt}`, 480]),
  row(['01:00 PM', 28], [`Transaction ID T260800000000000000${i}1`, 110]),
  row([`UTR No. 10000000000${i}`, 110]),
  ...(inst ? [row([inst, 110])] : [])
]);
phonepeRows.unshift(row(['Date', 28], ['Transaction Details', 110], ['Type', 400], ['Amount', 480]));

const gpayRows = [
  ['10 Aug,', 'Paid to SHOP A', 100, 'Paid by HDFC Bank 1234'],
  ['10 Aug,', 'Paid to SHOP B', 110, 'Paid by HDFC Bank 1234'],
  ['09 Aug,', 'Paid to SHOP C', 120, 'Paid by Punjab National Bank 4321'],
  ['08 Aug,', 'Paid to SHOP D', 130, 'Paid by HDFC Bank RuPay Credit Card 4455'],
  ['07 Aug,', 'Paid to SHOP E', 140, 'Paid by UPI Lite'],
  ['06 Aug,', 'Paid to SHOP F', 150, null],
  ['05 Aug,', 'Received from A FRIEND', 500, 'Paid to HDFC Bank 1234'],
  ['04 Aug,', 'Bill payment of HDFC Credit Card', 4500, 'Paid by HDFC Bank 1234']
].flatMap(([d, det, amt, inst], i) => [
  row([d, 28], [det, 110], [`₹${amt}`, 480]),
  row(['2026', 28], [`UPI Transaction ID: 10000000000${i}`, 110]),
  row(['01:00 PM', 28], ...(inst ? [[inst, 110]] : []))
]);

const paytmRows = [
  ['10/08/2026', 'Paid to SHOP A', 'State Bank Of India - 1234', '100.00'],
  ['10/08/2026', 'Paid to SHOP B', 'State Bank Of India - 1234', '110.00'],
  ['09/08/2026', 'Paid to SHOP C', 'Punjab National Bank - 4321', '120.00'],
  ['08/08/2026', 'Paid to SHOP D', 'HDFC Bank RuPay Credit Card - XX4455', '130.00'],
  ['07/08/2026', 'Paid to SHOP E', 'Paytm Wallet', '140.00'],
  ['06/08/2026', 'Paid to SHOP F', '', '150.00'],
  ['05/08/2026', 'Received from A FRIEND', 'State Bank Of India - 1234', '500.00'],
  ['04/08/2026', 'Bill payment of HDFC Credit Card', 'State Bank Of India - 1234', '4500.00']
].map(([d, det, acc, amt]) => row([d, 28], [det, 110], ...(acc ? [[acc, 360]] : []), [amt, 500]));
paytmRows.unshift(row(['Date', 28], ['Transaction Details', 110], ['Your Account', 360], ['Amount', 500]));

(async () => {
  const ta = async (name, fn) => { await fn(); n++; console.log('✔', name); };
  await ta('parseFile PhonePe: mixed banks + RuPay card + wallet split into separate accounts; card bill stays a transfer',
    () => upiFile('PhonePe_x.pdf', 'PhonePe', 'PhonePe UPI Statement', q => q.parsePhonePePDF(phonepeRows, '', {})));
  await ta('parseFile Google Pay: mixed banks + credit card + UPI Lite split into separate accounts; card bill stays a transfer',
    () => upiFile('GPay_x.pdf', 'Google Pay', 'Google Pay Statement PDF', q => q.parseUPIAppPDF(gpayRows, '', { bankName: 'Google Pay' })));
  await ta('parseFile Paytm: mixed banks + RuPay card + wallet split into separate accounts; card bill stays a transfer',
    () => upiFile('Paytm_x.pdf', 'Paytm', 'Paytm Statement', q => q.parsePaytmPDF(paytmRows, '', { bankName: 'Paytm' })));
  await ta('parseFile: masked-only or bank-prefixed last-4 keys the account; "Wallet" / no-digit text keeps the default account', async () => {
    const acc = async (info) => {
      const { by } = await runFile('PhonePe_y.pdf', (q) => ({
        records: [{ date: '2026-08-10', narration: 'Paid to SHOP A', amount: 10, explicitType: 'expense', accountInfo: info }],
        accountMetadata: q.extractAccountMetadata('PhonePe statement', 'PhonePe_y.pdf'), profile: 'PhonePe UPI Statement'
      }));
      return Object.keys(by)[0];
    };
    assert.strictEqual(await acc('XXXXXXXX3863'), 'bank:3863');
    assert.strictEqual(await acc('XXXX 3863'), 'bank:3863');
    assert.strictEqual(await acc('HDFC Bank A/c XX3863'), 'bank:3863');
    assert.strictEqual(await acc('Wallet'), 'wallet');
    assert.strictEqual(await acc('Account'), 'wallet'); // PhonePe CSV literal: no digits -> default account
  });
  // real accounts.js against an in-memory store (the stub above only mirrors it)
  await ta('accounts: two accounts or cards at one bank stay separate; same last-4 reuses; unknown last-4 still falls back', async () => {
    const store = [];
    global.window = { db: { getAll: async () => store, put: async (_, a) => { store.push(a); } } };
    delete require.cache[require.resolve('../js/accounts.js')];
    require('../js/accounts.js');
    const am = global.window.accountsManager;
    const bank = (last4) => ({ bankName: 'Some Bank', type: 'bank', last4, name: `Some Bank Account (•••• ${last4})` });
    const card = (last4) => ({ bankName: 'Some Bank RuPay', type: 'credit_card', last4, isRuPay: true, name: `Some Bank RuPay Card ${last4}` });
    const a = await am.getOrCreateAccountFromStatement(bank('1111'));
    const b = await am.getOrCreateAccountFromStatement(bank('2222'));
    assert.notStrictEqual(a, b);
    assert.strictEqual(await am.getOrCreateAccountFromStatement(bank('1111')), a);
    const c1 = await am.getOrCreateAccountFromStatement(card('44'));
    const c2 = await am.getOrCreateAccountFromStatement(card('55'));
    assert.notStrictEqual(c1, c2);
    // a placeholder last-4 (or a file with none) may still match the existing account of that bank
    assert.strictEqual(await am.getOrCreateAccountFromStatement(bank('0000')), a);
    assert.strictEqual(store.length, 4);
  });
  await ta('currency: symbols, codes, conversion math, and once-a-day sync check', async () => {
    const storage = {};
    global.localStorage = {
      getItem: (k) => storage[k] || null,
      setItem: (k, v) => { storage[k] = String(v); },
      removeItem: (k) => { delete storage[k]; }
    };
    global.window = global.window || {};
    delete require.cache[require.resolve('../js/currency.js')];
    const { CurrencyEngine } = require('../js/currency.js');

    // 1. Supported currencies & symbols
    assert.strictEqual(CurrencyEngine.getSymbol('INR'), '₹');
    assert.strictEqual(CurrencyEngine.getSymbol('USD'), '$');
    assert.strictEqual(CurrencyEngine.getSymbol('EUR'), '€');
    assert.strictEqual(CurrencyEngine.getSymbol('GBP'), '£');
    assert.strictEqual(CurrencyEngine.getSymbol('CHF'), '₣');
    assert.strictEqual(CurrencyEngine.getSymbol('JPY'), '¥');

    // 2. Conversion math
    const inrFromUsd = CurrencyEngine.convert(100, 'USD', 'INR');
    assert.strictEqual(Math.round(inrFromUsd), 8350);

    const usdFromInr = CurrencyEngine.convert(8350, 'INR', 'USD');
    assert.strictEqual(Math.round(usdFromInr), 100);

    const gbpFromUsd = CurrencyEngine.convert(100, 'USD', 'GBP');
    assert.strictEqual(Math.round(gbpFromUsd), 78);

    const chfFromUsd = CurrencyEngine.convert(100, 'USD', 'CHF');
    assert.strictEqual(Math.round(chfFromUsd), 89);

    assert.strictEqual(CurrencyEngine.convert(50, 'EUR', 'EUR'), 50);

    // 3. Formatting
    const formattedInr = CurrencyEngine.format(1250, 'INR', { maximumFractionDigits: 0 });
    assert.ok(formattedInr.includes('₹') && formattedInr.includes('1,250'));

    const formattedUsd = CurrencyEngine.format(99.5, 'USD');
    assert.ok(formattedUsd.includes('$') && formattedUsd.includes('99.50'));

    // 4. Once a day policy check
    const today = CurrencyEngine._getTodayLocalDate();
    CurrencyEngine.lastFetchDate = today;
    const fetched = await CurrencyEngine.checkAndFetchDailyRates();
    assert.strictEqual(fetched, false);
  });

  t('refunds: auto-detected as refund (not income) and never merged with expenses in dedupe', () => {
    // 1. Parser auto-detection
    const res1 = p.resolveAmountCells(null, '500.00', null, 'Amazon India Refund');
    assert.strictEqual(res1.explicitType, 'refund');
    assert.strictEqual(res1.amount, 500);

    const res2 = p.resolveAmountCells(null, null, '349.00 Cr', 'Flipkart Reversal Credit');
    assert.strictEqual(res2.explicitType, 'refund');
    assert.strictEqual(res2.amount, 349);

    const resSalary = p.resolveAmountCells(null, '50000.00', null, 'Monthly Salary Credit');
    assert.strictEqual(resSalary.explicitType, 'income');

    // 2. Dedupe isolation: Expense and Refund MUST NEVER merge
    const tExpense = {
      id: 't_exp',
      date: '2026-09-18',
      amount: 500,
      type: 'expense',
      rawNarration: 'Amazon Retail Purchase',
      referenceNo: 'REF123456'
    };
    const tRefund = {
      id: 't_ref',
      date: '2026-09-18',
      amount: 500,
      type: 'refund',
      rawNarration: 'Amazon Retail Refund',
      referenceNo: 'REF123456'
    };

    const match = d.compareTransactions(tExpense, tRefund);
    assert.strictEqual(match.isMatch, false, 'Expense and Refund must never be treated as duplicates');
  });

  t('profiles: createProfile inherits active profile currency when currency is omitted', () => {
    const fs = require('fs');
    const path = require('path');
    const vmContext = require('vm');
    const profilesCode = fs.readFileSync(path.join(__dirname, '..', 'js', 'profiles.js'), 'utf8');
    const storage = {};
    const mockWindow = {
      localStorage: {
        getItem: (k) => storage[k] || null,
        setItem: (k, v) => { storage[k] = String(v); },
        removeItem: (k) => { delete storage[k]; }
      },
      location: { reload: () => {} }
    };
    mockWindow.window = mockWindow;
    const vm = new vmContext.Script(profilesCode);
    const ctx = vmContext.createContext(mockWindow);
    vm.runInContext(ctx);

    const pm = ctx.profileManager;
    // Create base profile with EUR
    const p1 = pm.createProfile('Alpha', 'EUR');
    assert.strictEqual(p1.currency, 'EUR');

    // Create second profile without specifying currency -> must inherit active profile currency (EUR)
    const p2 = pm.createProfile('Beta');
    assert.strictEqual(p2.currency, 'EUR');
  });

  t('currency: manual rate overrides, override detection, and reset to API rates', () => {
    const { CurrencyEngine } = require('../js/currency.js');
    // 1. Initial baseline
    assert.strictEqual(CurrencyEngine.isManualOverride('EUR'), false);

    // 2. Set custom override
    const ok = CurrencyEngine.updateManualRate('EUR', 0.80);
    assert.strictEqual(ok, true);
    assert.strictEqual(CurrencyEngine.rates.eur, 0.80);
    assert.strictEqual(CurrencyEngine.isManualOverride('EUR'), true);
    assert.strictEqual(CurrencyEngine.hasAnyManualOverride(), true);

    // 3. Conversion with overridden rate (100 USD = 80 EUR, so 80 EUR = 100 USD)
    const convertedToUsd = CurrencyEngine.convert(80, 'EUR', 'USD');
    assert.ok(Math.abs(convertedToUsd - 100) < 0.001);

    // 4. Reset single currency to API
    CurrencyEngine.resetRateToApi('EUR');
    assert.strictEqual(CurrencyEngine.isManualOverride('EUR'), false);
    assert.strictEqual(CurrencyEngine.rates.eur, CurrencyEngine.apiRates.eur);

    // 5. Override multiple and reset all
    CurrencyEngine.updateManualRate('INR', 90.0);
    CurrencyEngine.updateManualRate('JPY', 160.0);
    assert.strictEqual(CurrencyEngine.hasAnyManualOverride(), true);

    CurrencyEngine.resetAllRatesToApi();
    assert.strictEqual(CurrencyEngine.hasAnyManualOverride(), false);
    assert.strictEqual(CurrencyEngine.isManualOverride('INR'), false);
    assert.strictEqual(CurrencyEngine.isManualOverride('JPY'), false);
  });

  t('currency: dynamic default base currency rates, list exclusion, and relative manual overrides', () => {
    const { CurrencyEngine } = require('../js/currency.js');
    CurrencyEngine.resetAllRatesToApi();

    // 1. When EUR is default/base currency:
    // API Baseline: 1 USD = 0.92 EUR, 1 USD = 83.5 INR
    // 1 EUR = (83.5 / 0.92) INR = ~90.7608 INR
    const apiInrPerEur = CurrencyEngine.getApiRateAgainstBase('INR', 'EUR');
    assert.ok(Math.abs(apiInrPerEur - (83.5 / 0.92)) < 0.001);

    const initialRate = CurrencyEngine.getRateAgainstBase('INR', 'EUR');
    assert.ok(Math.abs(initialRate - (83.5 / 0.92)) < 0.001);

    // 2. Base currency against itself is 1.0 (excluded from display list)
    assert.strictEqual(CurrencyEngine.getRateAgainstBase('EUR', 'EUR'), 1.0);
    const supported = CurrencyEngine.getSupportedCurrencies();
    const filteredForEur = supported.filter(c => c.code !== 'EUR');
    assert.strictEqual(filteredForEur.length, supported.length - 1);
    assert.ok(!filteredForEur.some(c => c.code === 'EUR'));

    // 3. User overrides rate against base: e.g. 1 EUR = 100 INR
    const updated = CurrencyEngine.updateManualRateAgainstBase('INR', 'EUR', 100.0);
    assert.strictEqual(updated, true);
    assert.strictEqual(CurrencyEngine.isManualOverride('INR'), true);

    const newRate = CurrencyEngine.getRateAgainstBase('INR', 'EUR');
    assert.strictEqual(newRate, 100.0);

    // 4. Conversions reflect 1 EUR = 100 INR
    assert.strictEqual(CurrencyEngine.convert(10, 'EUR', 'INR'), 1000.0);
    assert.strictEqual(CurrencyEngine.convert(1000, 'INR', 'EUR'), 10.0);

    // 5. Reset INR against EUR back to API baseline
    CurrencyEngine.resetRateToApiAgainstBase('INR', 'EUR');
    assert.strictEqual(CurrencyEngine.isManualOverride('INR'), false);
    const restoredRate = CurrencyEngine.getRateAgainstBase('INR', 'EUR');
    assert.ok(Math.abs(restoredRate - (83.5 / 0.92)) < 0.001);

    CurrencyEngine.resetAllRatesToApi();
  });

  console.log(`\nALL ${n} WEB REGRESSION TESTS PASSED`);
})().catch((e) => { console.error(e); process.exit(1); });

