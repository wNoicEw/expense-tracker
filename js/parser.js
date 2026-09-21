/**
 * Universal Client-Side Bank, Card & UPI Statement Parser
 * 100% Offline Parsing for PDF, Excel (.xlsx/.xls), CSV, and Text statements.
 * Features Automatic Financial Institution, Instrument Type & Account Number Detection.
 */

class StatementParser {
  constructor() {
    this.supportedBanks = [
      'Google Pay UPI',
      'PhonePe Statement',
      'Paytm Wallet & Bank',
      'CRED Card Statement',
      'HDFC Bank',
      'State Bank of India (SBI)',
      'ICICI Bank',
      'Axis Bank',
      'Kotak Mahindra Bank',
      'Punjab National Bank (PNB)',
      'Bank of Baroda',
      'Canara Bank',
      'Union Bank of India',
      'Amazon Pay',
      'American Express',
      'Standard Chartered',
      'Generic Standard CSV/Excel'
    ];
  }

  /**
   * Main Entry Point: parse any File object
   */
  async parseFile(file, targetAccountId = null, password = null) {
    const fileName = file.name;
    const fileExt = fileName.split('.').pop().toLowerCase();

    let rawRecords = [];
    let detectedProfile = 'Generic Statement';
    let accountMetadata = null;

    if (fileExt === 'pdf') {
      const parsed = await this.parsePDF(file, password);
      rawRecords = parsed.records;
      detectedProfile = parsed.profile;
      accountMetadata = parsed.accountMetadata;
    } else if (fileExt === 'csv' || fileExt === 'txt') {
      const text = await this.readFileAsText(file);
      const parsed = this.parseCSVText(text, fileName);
      rawRecords = parsed.records;
      detectedProfile = parsed.profile;
      accountMetadata = parsed.accountMetadata;
    } else if (fileExt === 'xlsx' || fileExt === 'xls') {
      const buffer = await this.readFileAsArrayBuffer(file);
      const parsed = this.parseExcelBuffer(buffer, fileName);
      rawRecords = parsed.records;
      detectedProfile = parsed.profile;
      accountMetadata = parsed.accountMetadata;
    } else {
      throw new Error(`Unsupported file format .${fileExt}. Please upload PDF, CSV, or Excel file.`);
    }

    if (!rawRecords || rawRecords.length === 0) {
      // Build a rich error so the UI can distinguish the failure reason
      const err = new Error(
        detectedProfile && detectedProfile !== 'Generic Statement' && !detectedProfile.includes('Unknown')
          ? `Detected as "${detectedProfile}" but could not extract any transactions. The PDF may be password-protected, image-scanned, or in an unsupported variant.`
          : `Could not detect the statement format. Please ensure this is a bank/UPI/credit card statement PDF, CSV, or Excel file.`
      );
      err.detectionFailed = true;
      err.detectedProfile = detectedProfile || null;
      err.fileType = fileExt;
      throw err;
    }

    // Auto-resolve or dynamically create Account in database if not explicitly provided
    let resolvedAccountId = targetAccountId;
    const defaultAccountId = async () => {
      if (!resolvedAccountId && window.accountsManager) {
        resolvedAccountId = await window.accountsManager.getOrCreateAccountFromStatement(accountMetadata);
      }
      return resolvedAccountId || (resolvedAccountId = 'acc_cash_default');
    };
    // Multi-account UPI histories name the paying account on every row: Navi "State Bank of India - 2105",
    // and for PhonePe / Google Pay / Paytm "HDFC Bank 1234", "HDFC Bank XXXX1234" or just "XXXXXXXX3863"
    const upiApp = /navi|phonepe|paytm|google pay|gpay/i.test(detectedProfile);
    const rowAccountCache = new Map();
    const accountFromRow = async (info) => {
      const s = String(info || '').trim();
      if (targetAccountId || !window.accountsManager || !s || /credit\s*card/i.test(s)) return null;
      // Masked number without a bank name: no bank to key on, so the last 4 digits alone separate the accounts
      const masked = upiApp && s.match(/^[Xx*•\s]+(\d{2,4})$/);
      const m = masked
        || s.match(/^(.*?[A-Za-z].*?)\s*(?:-|–|—)\s*([A-Za-z0-9]{2,4})$/)
        || (upiApp && s.match(/^(.*?[A-Za-z].*?)\s+(?:(?:a\/c|acct?|account|ending(?:\s+in)?)\s*(?:no\.?)?\s*)?[Xx*•]*(\d{2,4})$/i));
      if (!m) return null;
      const last4 = masked ? masked[1] : m[2];
      // bankName is also a match key in getOrCreateAccountFromStatement, so it carries the digits here
      const bankName = masked ? `Bank A/c ${last4}` : (/state\s*bank|\bsbi\b/i.test(m[1]) ? 'State Bank of India (SBI)' : m[1].trim());
      const key = `${bankName}|${last4}`;
      if (!rowAccountCache.has(key)) {
        rowAccountCache.set(key, await window.accountsManager.getOrCreateAccountFromStatement({
          bankName, type: 'bank', last4, name: `${masked ? 'Bank' : bankName} Account (•••• ${last4})`,
          color: /state\s*bank|\bsbi\b/i.test(bankName) ? '#065f46' : '#1e3a8a', creditLimit: 100000, billingDay: 15
        }));
      }
      return rowAccountCache.get(key);
    };

    // Categorize, normalize, and tag transactions
    const customRules = (await window.db.getAll('rules')) || [];
    const normalizedTransactions = [];

    // CC bill payment patterns for post-categorization safety net
    const ccPaymentRegex = /\b(bill\s*payment\s*of\s+[\w&.\s]{0,40}?credit\s*card|credit card payment|cc payment|card bill|card payment|cc bill|cred|billdesk.*card|billdesk.*cc|autopay.*card|autopay.*cc|nach.*card|nach.*cc|ecs.*card|ecs.*cc|hdfc card|sbi card|icici card|axis card|kotak card|amex.*payment|amex.*bill|card settlement|card outstanding|minimum due|total amount due)\b/i;

    for (let i = 0; i < rawRecords.length; i++) {
      const row = rawRecords[i];
      const narration = row.narration || '';
      const accountInfo = row.accountInfo || '';
      const combinedInfo = `${narration} ${accountInfo}`;

      const categorization = await window.categorizer.categorize(narration, row.amount, customRules);
      
      let txnType = row.explicitType || categorization.type;
      let txnCategory = categorization.category;
      const amount = Math.abs(parseFloat(row.amount) || 0);

      // Skip invalid or 0 amount lines
      if (amount <= 0) continue;

      // --- UPI RUPAY CREDIT CARD INTELLIGENCE ---
      // Check if this specific row was paid using a linked RuPay Credit Card on UPI
      let txnAccountId = await accountFromRow(accountInfo);
      let paymentMode = row.paymentMode || this.guessPaymentMode(narration, detectedProfile, accountMetadata?.type);

      // (default account is resolved after the card override below, so it is only created when actually needed)
      // A bill payment *of* a credit card is not a purchase made *with* one, even if the narration mentions UPI
      const isCcBillPayment = ccPaymentRegex.test(narration.toLowerCase());
      const ruPayMeta = (isCcBillPayment && !/rupay/i.test(accountInfo)) ? null : this.detectRuPayCC(combinedInfo);
      if (ruPayMeta && window.accountsManager) {
        // Automatically link this transaction to the RuPay Credit Card account
        txnAccountId = await window.accountsManager.getOrCreateAccountFromStatement(ruPayMeta);
        paymentMode = 'UPI (RuPay Credit Card)';
      }

      // CRITICAL: Post-categorization override for CC bill payments
      // If the source account is a bank/wallet (not a credit card) and the narration
      // matches CC payment patterns, force it to type='transfer' to prevent double-counting.
      // The actual expense is already recorded on the credit card statement.
      if (accountMetadata && accountMetadata.type !== 'credit_card' && !ruPayMeta) {
        const lowerNarr = narration.toLowerCase();
        if (ccPaymentRegex.test(lowerNarr)) {
          txnType = 'transfer';
          txnCategory = 'Transfers & CC Bill';
        }
      }

      if (!txnAccountId) txnAccountId = await defaultAccountId();

      const dateUnreadable = !row.date;

      const txn = {
        id: 'txn_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9),
        date: row.date || this.localISODate(new Date()),
        amount: amount,
        type: txnType,
        category: txnCategory,
        needsReview: categorization.needsReview || dateUnreadable,
        confidence: categorization.confidence || 'medium',
        identifier: categorization.identifier || '',
        description: row.customDescription || categorization.cleanTitle,
        rawNarration: narration,
        explicitType: row.explicitType || null,
        accountId: txnAccountId,
        paymentMode: paymentMode,
        referenceNo: row.referenceNo || this.extractRefNo(narration) || `REF_${Date.now()}_${i}`,
        sourceFile: fileName,
        isDuplicate: false,
        duplicateWithId: null,
        duplicateStatus: 'none',
        notes: (ruPayMeta ? `Paid via RuPay Credit Card (${ruPayMeta.name}) on UPI` : `Imported from ${fileName} (${detectedProfile})`)
          + (dateUnreadable ? ' — date could not be read from the statement, defaulted to today; please verify' : ''),
        createdAt: new Date().toISOString()
      };

      normalizedTransactions.push(txn);
    }

    return {
      fileName: fileName,
      fileType: fileExt,
      detectedProfile: detectedProfile,
      accountMetadata: accountMetadata,
      accountId: resolvedAccountId,
      transactions: normalizedTransactions,
      count: normalizedTransactions.length
    };
  }

  // --- PDF PARSING ENGINE (Using PDF.js) ---
  // 3-stage pipeline: extract items with coords → build spatial rows → route to bank-specific parser

  async parsePDF(file, password = null) {
    const arrayBuffer = await this.readFileAsArrayBuffer(file);
    let pdf;
    try {
      const loadingTask = pdfjsLib.getDocument({
        data: arrayBuffer,
        password: password || undefined
      });
      pdf = await loadingTask.promise;
    } catch (err) {
      if (
        err.name === 'PasswordException' ||
        err.code === 1 || // NEED_PASSWORD
        err.code === 2 || // INCORRECT_PASSWORD
        (err.message && err.message.toLowerCase().includes('password'))
      ) {
        const pwErr = new Error(
          err.code === 2 || (err.message && err.message.toLowerCase().includes('incorrect'))
            ? 'Incorrect PDF password. Please check and try again.'
            : 'This PDF statement is password-protected.'
        );
        pwErr.isPasswordProtected = true;
        pwErr.isIncorrectPassword = err.code === 2 || (err.message && err.message.toLowerCase().includes('incorrect'));
        pwErr.fileName = file.name;
        throw pwErr;
      }
      throw err;
    }

    // Stage 1: collect all text items with x,y coordinates across all pages
    let allItems = []; // [{text, x, y, page, localY}]
    let fullTextArr = [];

    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
      const page = await pdf.getPage(pageNum);
      const viewport = page.getViewport({ scale: 1 });
      const pageHeight = viewport.height;
      const textContent = await page.getTextContent();
      const items = textContent.items;
      if (!items || items.length === 0) continue;

      const pageOffsetY = (pageNum - 1) * 2000;

      items.forEach(item => {
        const text = (item.str || '').trim();
        if (!text) return;
        const x = item.transform[4];
        // PDF y is bottom-up; convert to top-down for easier reading with page-offset
        const y = pageOffsetY + (pageHeight - item.transform[5]);
        allItems.push({ text, x, y, page: pageNum, localY: pageHeight - item.transform[5] });
        fullTextArr.push(text);
      });
    }

    const fullText = fullTextArr.join(' ');

    // Stage 2: detect bank profile
    const accountMetadata = this.extractAccountMetadata(fullText, file.name);
    const bankKey = accountMetadata.bankName.toLowerCase();

    // Stage 3: build spatial rows, then route to the right sub-parser
    const spatialRows = this.buildSpatialRows(allItems);

    const ft = fullText.toLowerCase();
    const fname = file.name.toLowerCase();
    const header = fullText.slice(0, 1500).toLowerCase();
    let result;

    // --- 1. UPI App block-format statements (Priority over underlying banks mentioned in txns) ---
    if (bankKey.includes('navi') || ft.includes('paid via navi') || (ft.includes('upi txn id') && (ft.includes('navi') || /navi/i.test(fname)))) {
      result = this.parseNaviBlocks(spatialRows, fullText, accountMetadata);

    } else if (bankKey === 'google pay' || header.includes('google pay') || /gpay/i.test(fname) || ft.includes('google pay') || ft.includes('gpay') || ft.includes('tez ')) {
      result = this.parseUPIAppPDF(spatialRows, fullText, accountMetadata);

    } else if (bankKey === 'phonepe' || header.includes('phonepe') || ft.includes('phonepe') || /phonepe/i.test(fname)) {
      result = this.parsePhonePePDF(spatialRows, fullText, accountMetadata);

    } else if (bankKey === 'paytm' || ((ft.includes('paytm') && (ft.includes('wallet') || ft.includes('passbook') || ft.includes('transaction history') || ft.includes('upi statement'))) || /paytm/i.test(fname))) {
      result = this.parsePaytmPDF(spatialRows, fullText, accountMetadata);

    // --- 2. SBI Bank Account (Priority over generic keyword matching) ---
    // Word-boundary match: a bare substring test also fires on IFSC codes (SBIN0001234) and VPAs (@oksbi)
    } else if (ft.includes('state bank') || /\bsbi\b/.test(ft) || /sbi/i.test(fname) || ft.includes('wdl tfr') || ft.includes('dep tfr') || bankKey.includes('sbi') || bankKey.includes('state bank')) {
      result = this.parseSBICoordinates(spatialRows, fullText, accountMetadata);

    // --- 4. Credit Card Statements (Strict verification to avoid false positives on bank statements) ---
    } else if (
      (accountMetadata.type === 'credit_card' ||
       ft.includes('credit card statement') || ft.includes('card statement') ||
       ft.includes('minimum amount due') || ft.includes('total amount due') ||
       ft.includes('payment due date')) &&
      !ft.includes('wdl tfr') && !ft.includes('dep tfr')
    ) {
      result = this.parseCreditCardPDF(spatialRows, fullText, accountMetadata);

    // --- 5. HDFC / ICICI / Axis / Kotak / other public/private banks ---
    } else if (
      bankKey.includes('hdfc') || bankKey.includes('icici') || bankKey.includes('axis') ||
      bankKey.includes('kotak') || bankKey.includes('pnb') || bankKey.includes('canara') ||
      bankKey.includes('baroda') || bankKey.includes('union') || bankKey.includes('yes bank') ||
      bankKey.includes('indusind') || bankKey.includes('federal') || bankKey.includes('idfc') ||
      bankKey.includes('standard chartered') || bankKey.includes('citibank') || bankKey.includes('rbl')
    ) {
      result = this.parseGenericTablePDF(spatialRows, fullText, accountMetadata);
      if (!result.records || result.records.length === 0) {
        result = this.parseUPIAppPDF(spatialRows, fullText, accountMetadata);
      }


    // --- Generic fallback cascade ---
    } else {
      result = this.parseGenericTablePDF(spatialRows, fullText, accountMetadata);
      if (!result.records || result.records.length === 0) {
        result = this.parseCreditCardPDF(spatialRows, fullText, accountMetadata);
      }
      if (!result.records || result.records.length === 0) {
        const lines = spatialRows.map(r => r.words.map(w => w.text).join(' '));
        result = this.extractTransactionsFromTextLines(lines, file.name, accountMetadata);
      }
    }

    return result;
  }

  /**
   * Build spatial rows: group PDF text items by y-proximity (±4px), sort words by x within each row.
   * Returns: [{y, words: [{text, x}]}]
   */
  buildSpatialRows(items, yTolerance = 4) {
    const rows = [];
    // Sort all items by y (top-down) then x (left-right)
    const sorted = [...items].sort((a, b) => a.y !== b.y ? a.y - b.y : a.x - b.x);

    for (const item of sorted) {
      // Find an existing row close enough
      let found = false;
      for (const row of rows) {
        if (Math.abs(row.y - item.y) <= yTolerance) {
          row.words.push({ text: item.text, x: item.x });
          // Recompute representative y as average
          row.y = (row.y + item.y) / 2;
          found = true;
          break;
        }
      }
      if (!found) {
        rows.push({ y: item.y, words: [{ text: item.text, x: item.x }] });
      }
    }

    // Sort words within each row by x
    rows.forEach(r => r.words.sort((a, b) => a.x - b.x));
    return rows;
  }

  /**
   * PhonePe Statement PDF Parser
   * Format:  Header row: "Date | Transaction Details | Type | Amount"
   * Each transaction spans 2-3 lines:
   *   Line 1 (anchor): "Apr 02, 2025   Paid to Merchant Name   DEBIT   ₹185"
   *   Line 2:          "06:57 PM   Transaction ID: T...   UTR No. ..."
   *   Line 3 (opt):    "Paid by XXXXXXXX3863"
   *
   * Type column explicitly says DEBIT or CREDIT.
   */
  parsePhonePePDF(spatialRows, fullText, accountMetadata) {
    const records = [];
    const lines = spatialRows.map(r => r.words.map(w => w.text).join(' ').trim()).filter(l => l);

    // PhonePe date format: "Apr 02, 2025" or "02 Apr, 2025" or "Apr 2, 2025"
    const dateRe = /(?:(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*\s+\d{1,2},?\s+\d{4}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*,?\s+\d{4}|\d{2}[\/\-]\d{2}[\/\-]\d{4})/i;
    const amountRe = /(?:₹|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)/;
    const typeRe = /\b(DEBIT|CREDIT|DR|CR)\b/i;
    const txnIdRe = /(?:Transaction\s*ID|Txn\s*ID|T\d{20,})[:\s]*([T\w]{10,30})/i;
    const utrRe = /UTR\s*No\.?\s*[:\s]*(\d{10,15})/i;

    // Header detection
    const headerIdx = lines.findIndex(l => /date/i.test(l) && /transaction\s*details/i.test(l) && /(type|amount)/i.test(l));

    for (let i = Math.max(0, headerIdx + 1); i < lines.length; i++) {
      const line = lines[i];
      const dateM = line.match(dateRe);
      const amtM = line.match(amountRe);
      // The Type column sits right before the amount, so the LAST match is it ("Bill payment of HDFC Credit Card  DEBIT")
      const typeM = [...line.matchAll(new RegExp(typeRe.source, 'gi'))].pop();

      if (!dateM || !amtM) continue;

      const amount = parseFloat(amtM[1].replace(/,/g, ''));
      if (isNaN(amount) || amount <= 0) continue;

      const isCredit = typeM && /CREDIT|CR\b/i.test(typeM[0]);
      const isDebit = typeM && /DEBIT|DR\b/i.test(typeM[0]);
      const explicitType = isCredit ? 'income' : 'expense';

      // Extract narration: everything between date and type/amount
      let narration = (typeM ? line.slice(0, typeM.index) + line.slice(typeM.index + typeM[0].length) : line)
        .replace(dateM[0], '')
        .replace(amtM[0], '')
        .replace(/\s+/g, ' ')
        .trim();

      // Collect the following lines: txn ID / UTR, and the payer instrument ("Paid by XXXXXXXX3863")
      let txnId = '';
      let utr = '';
      let accountInfo = '';
      for (let j = i + 1; j <= Math.min(i + 5, lines.length - 1); j++) {
        const nxt = lines[j];
        if (nxt.match(dateRe) && nxt.match(amountRe)) break; // next transaction
        const tid = nxt.match(txnIdRe);
        if (tid) txnId = tid[1];
        const u = nxt.match(utrRe);
        if (u) utr = u[1];
        const inst = this.upiInstrumentLine(nxt);
        if (inst !== null) accountInfo = inst;
        // Append useful info to narration if not already there
        if (j <= i + 3 && inst === null && !/^\d{1,2}:\d{2}/.test(nxt) && !tid && !u && nxt.length > 3 && nxt.length < 80) {
          narration += ' ' + nxt;
        }
      }

      records.push({
        date: this.normalizeDate(dateM[0]),
        narration: narration || 'PhonePe Transaction',
        amount,
        explicitType,
        referenceNo: utr || txnId || this.extractRefNo(line),
        accountInfo,
        paymentMode: 'UPI'
      });
    }

    return { records, profile: 'PhonePe UPI Statement', accountMetadata };
  }

  /**
   * Paytm Wallet / UPI Statement PDF Parser
   * Format varies; typically:
   *   Table columns: Date | Description/Narration | Amount | Type (Dr/Cr) | Balance
   *   OR block format: "DATE  Description  ₹AMOUNT  SUCCESS"
   */
  parsePaytmPDF(spatialRows, fullText, accountMetadata) {
    const records = [];
    const lines = spatialRows.map(r => r.words.map(w => w.text).join(' ').trim()).filter(l => l);

    const dateRe = /(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*\s+\d{2,4})/i;
    // First try table format via generic parser
    // ("Your Account" column = the paying bank / wallet; its header wording is unverified, so unknown headers just yield no account)
    const tableResult = this.parseGenericTablePDF(spatialRows, fullText, accountMetadata, { accountCol: /^(your\s*account|paid\s*(from|via|by)|payment\s*(mode|method)|instrument)$/i });
    if (tableResult.records && tableResult.records.length > 0) {
      tableResult.profile = 'Paytm Statement';
      return tableResult;
    }

    // Fallback: block/line format
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const dateM = line.match(dateRe);
      if (!dateM) continue;
      const amtM = this.extractAmount(line, dateM[0]);
      if (!amtM) continue;

      const amount = amtM.value;

      const isRefund = /refund|reversal|cashback/i.test(line);
      const isCredit = this.upiIsIncome(line, /credit|received|\bcr\b|refund|cashback|added/i);

      let narration = line.replace(dateM[0], '').replace(amtM.text, '').replace(/\s+/g, ' ').trim();

      // Collect continuation line; a "Paid from <bank> 1234" line within the block is the paying account
      let accountInfo = '';
      for (let j = i + 1; j <= Math.min(i + 3, lines.length - 1) && !lines[j].match(dateRe); j++) {
        const inst = this.upiInstrumentLine(lines[j]);
        if (inst !== null) accountInfo = inst;
      }
      if (i + 1 < lines.length && !lines[i + 1].match(dateRe)) {
        const nxt = lines[i + 1];
        if (nxt.length > 3 && nxt.length < 100 && this.upiInstrumentLine(nxt) === null) narration += ' ' + nxt;
      }

      records.push({
        date: this.normalizeDate(dateM[0]),
        narration: narration || 'Paytm Transaction',
        amount,
        explicitType: isRefund ? 'refund' : (isCredit ? 'income' : 'expense'),
        referenceNo: this.extractRefNo(line),
        accountInfo,
        paymentMode: 'UPI'
      });
    }

    return { records, profile: 'Paytm Statement', accountMetadata };
  }

  /**
   * Universal Credit Card PDF Parser
   * Covers: HDFC CC, ICICI CC (Amazon Pay ICICI), Axis CC, Kotak CC, SBI Card, CRED, Amex, Standard Chartered
   *
   * All Indian CC statements share these quirks:
   *   - Column headers: Date | Transaction Details/Description | Amount (CR suffix = credit)
   *   - OR separate columns: Date | Description | Debit | Credit
   *   - ICICI: adds "SerNo" and "Reward Points" columns
   *   - Kotak: uses "Debit" and "Credit" separate columns
   *   - SBI Card: "Transaction Date", "Transaction Details", "Amount (in INR)"
   *   - Amount with " CR" suffix = refund/payment (income)
   *   - Multi-line descriptions common in all
   *   - Password-protected (we don't handle unlocking here — user must pre-unlock)
   */
  parseCreditCardPDF(spatialRows, fullText, accountMetadata) {
    const records = [];

    // Extended CC column header synonyms
    const colPatterns = {
      date:      /^(txn\.?\s*date|transaction\s*date|date|posting\s*date|value\s*date|stmt\s*date|sl\.?\s*no\.?.*date|date\s*of\s*txn)$/i,
      narration: /^(transaction\s*details?|description|particulars|details|merchant|narrative|narration|trans\.?\s*details?|paid\s*to|payee)$/i,
      amount:    /^(amount|amount\s*\(in\s*[₹\w]+\)|debit|dr|charged|spend|txn\s*amount|net\s*amount|transaction\s*amount|spend\s*amount)$/i,
      credit:    /^(credit|cr|payment|refund|amount\s*cr|credited)$/i,
      ref:       /^(ref\.?\s*no\.?|reference|serno|ser\.?\s*no\.?|sl\.?\s*no\.?|txn\.?\s*id|transaction\s*id|chq\.?|cheque)$/i,
      points:    /^(reward\s*points?|points|rp|miles|cashback\s*points?)$/i,
      intl:      /^(intl\.?#?|international|forex|currency)$/i,
    };

    // Find header row
    let headerRowIdx = -1;
    let colBands = {};

    for (let i = 0; i < Math.min(spatialRows.length, 60); i++) {
      const row = spatialRows[i];
      const words = row.words;
      const matches = {};

      // Check individual words AND combinations (e.g., "Transaction" + "Date")
      const rowText = words.map(w => w.text).join(' ');
      for (const [col, re] of Object.entries(colPatterns)) {
        // Try each word individually
        for (const w of words) {
          if (re.test(w.text.trim())) {
            if (!(col in matches)) matches[col] = w.x;
          }
        }
        // Also try the combined row text positions
        // This handles "Transaction Details" as two separate words
        const comboMatch = rowText.match(new RegExp(re.source, 'i'));
        if (comboMatch && !(col in matches)) {
          // Find x of the first word of the match
          const matchStart = comboMatch.index;
          // Find approximate x from the first word of this span
          const approxWord = words.find(w => rowText.indexOf(w.text) >= matchStart - 5 && rowText.indexOf(w.text) <= matchStart + 30);
          if (approxWord) matches[col] = approxWord.x;
        }
      }

      // CC statement needs at least date + (amount or narration)
      if (matches.date !== undefined && (matches.amount !== undefined || matches.narration !== undefined)) {
        headerRowIdx = i;
        const sorted = Object.entries(matches).sort((a, b) => a[1] - b[1]);
        for (let k = 0; k < sorted.length; k++) {
          const [col, xStart] = sorted[k];
          const xEnd = k + 1 < sorted.length ? sorted[k + 1][1] - 2 : 9999;
          colBands[col] = { xMin: xStart - 15, xMax: xEnd };
        }
        break;
      }
    }

    if (headerRowIdx === -1) {
      return { records: [], profile: accountMetadata.bankName + ' Credit Card', accountMetadata };
    }

    const getCol = (word, col) => {
      const band = colBands[col];
      if (!band) return false;
      return word.x >= band.xMin && word.x <= band.xMax;
    };

    let currentTxn = null;

    for (let i = headerRowIdx + 1; i < spatialRows.length; i++) {
      const row = spatialRows[i];
      const words = row.words;
      if (!words || words.length === 0) continue;

      const rowText = words.map(w => w.text).join(' ');

      // Skip summary / footer rows
      if (/^(total|subtotal|grand\s*total|opening\s*balance|closing\s*balance|payment\s*due|minimum\s*due|statement\s*period|page\s*\d)/i.test(rowText.trim())) {
        if (currentTxn && currentTxn.amount > 0) {
          records.push(this._buildCCRecord(currentTxn));
          currentTxn = null;
        }
        continue;
      }

      // Find date-band word
      const dateWord = words.find(w => getCol(w, 'date') && /\d{1,2}[\/\-\.]\d{1,2}[\/\-\.](\d{2}|\d{4})/.test(w.text));

      if (dateWord) {
        if (currentTxn && currentTxn.amount > 0) {
          records.push(this._buildCCRecord(currentTxn));
        }

        const narrWords = words.filter(w => getCol(w, 'narration')).map(w => w.text);
        const refWords = words.filter(w => getCol(w, 'ref')).map(w => w.text);

        // Amount detection for CC: look in amount column, handle " CR" suffix
        let txnAmount = 0;
        let explicitType = 'expense';

        const amtW = words.find(w => getCol(w, 'amount') && w.text !== dateWord.text);
        const crW = words.find(w => getCol(w, 'credit'));

        if (amtW) {
          const rawAmt = amtW.text.replace(/[₹,\s]/g, '');
          // Check for CR suffix on the same or next word
          const hasCR = /\bCR\b/i.test(rowText) && !/\bDR\b/i.test(rowText);
          const isCredit = hasCR || (crW && parseFloat(crW.text.replace(/[₹,\s]/g, '')) > 0);
          const v = parseFloat(rawAmt.replace(/CR$/i, '').replace(/[^0-9.]/g, ''));
          if (!isNaN(v) && v > 0) {
            txnAmount = v;
            explicitType = isCredit ? 'income' : 'expense';
          }
        }

        // If no amount found in column, search row for numeric values
        if (txnAmount === 0) {
          for (const w of words) {
            if (w === dateWord) continue;
            const rawAmt = w.text.replace(/[₹,\s]/g, '').replace(/CR$/i, '');
            const v = parseFloat(rawAmt.replace(/[^0-9.]/g, ''));
            if (!isNaN(v) && v > 100 && v < 10000000) { // between ₹100 and 1Cr
              txnAmount = v;
              const hasCR = /CR\b/i.test(w.text) || (/CR\b/i.test(rowText) && !/DR\b/i.test(rowText));
              if (hasCR) explicitType = 'income';
              break;
            }
          }
        }

        currentTxn = {
          date: this.normalizeDate(dateWord.text),
          narrationParts: narrWords,
          amount: txnAmount,
          explicitType,
          referenceNo: refWords.join(' ').trim() || this.extractRefNo(rowText)
        };

      } else if (currentTxn) {
        // Continuation: skip point/intl/ref columns, add to narration
        if (/^\s*$/.test(rowText)) continue;
        const skipPatterns = /^(total|subtotal|opening|closing|due|page|\d{4,}|[A-Z]{2}\d{8,})/i;
        if (skipPatterns.test(rowText.trim()) || this.isColumnHeaderRow(rowText)) continue;

        const narrCont = words.filter(w => {
          const isAmt = /^[\d,]+\.\d{2}(CR)?$/.test(w.text.trim());
          const isDate = /\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]/.test(w.text);
          const isPoints = /^\d{1,5}$/.test(w.text.trim()) && parseInt(w.text) < 50000;
          return !isAmt && !isDate && !isPoints;
        }).map(w => w.text);

        if (narrCont.length > 0) {
          currentTxn.narrationParts.push(...narrCont);
        }

        // Pick up amount if not yet found on this continuation line
        if (currentTxn.amount === 0) {
          const amtW = words.find(w => getCol(w, 'amount'));
          if (amtW) {
            const v = parseFloat(amtW.text.replace(/[₹,CR\s]/gi, '').replace(/[^0-9.]/g, ''));
            if (!isNaN(v) && v > 0) {
              currentTxn.amount = v;
              if (/CR\b/i.test(amtW.text)) currentTxn.explicitType = 'income';
            }
          }
        }
      }
    }

    if (currentTxn && currentTxn.amount > 0) {
      records.push(this._buildCCRecord(currentTxn));
    }

    const detectedProfile = accountMetadata.bankName + ' Credit Card Statement';
    return { records, profile: detectedProfile, accountMetadata };
  }

  /** Helper: build final CC record from currentTxn object */
  _buildCCRecord(txn) {
    const narration = txn.narrationParts
      .join(' ')
      .replace(/\s+/g, ' ')
      .replace(/\bCR\b/gi, '')
      .replace(/\bDR\b/gi, '')
      .trim();
    let explicitType = txn.explicitType;
    if (explicitType === 'income' && /refund|reversal|cashback/i.test(narration)) {
      explicitType = 'refund';
    }
    return {
      date: txn.date,
      narration: narration || 'Credit Card Transaction',
      amount: txn.amount,
      explicitType,
      referenceNo: txn.referenceNo || null,
      paymentMode: 'Credit Card'
    };
  }

  /**
   * Navi UPI PDF Parser
   * Format: fixed 3-4 line blocks per transaction:
   *   Line 1: "20 Aug 2026 Paid to NAME ₹AMOUNT"  OR  "20 Aug 2026 Received from NAME ₹AMOUNT"
   *   Line 2: "Bank Name" (optional instrument)
   *   Line 3: "H:MM PM UPI txn ID: XXXXXX Account - XXXX"
   *   Line 4: "Note: ..."
   */

  parseNaviBlocks(spatialRows, fullText, accountMetadata) {
    const records = [];
    const DATE_RE = /^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*\s+\d{4})$/i;
    const AMOUNT_RE = /^(?:₹|Rs\.?|INR)?\s*([\d,]+(?:\.\d{1,2})?)$/i;
    // Columns from the real Navi layout: date x≈28, details x≈115, account x≈382, amount x≥500
    const inCol = (w, lo, hi) => w.x >= lo && w.x < hi;
    const join = (words) => words.map(w => w.text).join(' ').replace(/\s+/g, ' ').trim();

    const anchorOf = (row) => {
      const dateText = join(row.words.filter(w => inCol(w, 0, 100)));
      const dm = dateText.match(DATE_RE);
      const amtWord = row.words.find(w => w.x >= 500 && AMOUNT_RE.test(w.text.trim()));
      if (!dm || !amtWord) return null;
      return { rawDate: dm[1], amount: parseFloat(amtWord.text.match(AMOUNT_RE)[1].replace(/,/g, '')) };
    };

    for (let i = 0; i < spatialRows.length; i++) {
      const row = spatialRows[i];
      const anchor = anchorOf(row);
      if (!anchor || isNaN(anchor.amount) || anchor.amount <= 0) continue;

      // Any "Paid to / Paid for / Bill payment of / Recharge of …" row is an expense unless it is a receipt or refund
      let details = join(row.words.filter(w => inCol(w, 100, 350)));
      if (!details) continue;
      const isRefund = /^refund/i.test(details);
      const isIncome = /^received/i.test(details);

      let account = join(row.words.filter(w => inCol(w, 350, 500)));
      let txnId = '';
      let noteText = '';

      // Following rows: "6:24 PM | UPI txn ID: … | Credit Card - XX99" and "Note: …".
      // Anything else (page header, user name, phone number) is ignored rather than glued into the narration.
      for (let j = i + 1; j <= Math.min(i + 5, spatialRows.length - 1); j++) {
        const next = spatialRows[j];
        if (anchorOf(next)) break;
        const nextDetails = join(next.words.filter(w => inCol(w, 100, 350)));
        const nextAccount = join(next.words.filter(w => inCol(w, 350, 500)));
        const isTimeRow = /^\d{1,2}:\d{2}\s*[AP]M$/i.test(join(next.words.filter(w => inCol(w, 0, 100))));
        const idMatch = nextDetails.match(/UPI\s+txn\s+ID[:\s]*(\d+)/i);
        if (idMatch) {
          txnId = idMatch[1];
        } else if (/^Note:/i.test(nextDetails)) {
          noteText = nextDetails.replace(/^Note:\s*/i, '').trim();
        } else if (isTimeRow && nextDetails) {
          details = `${details} ${nextDetails}`; // long payee name wrapped onto the time row
        }
        // "Credit Card - XX99" / "- 2105" continue the account column on the time or txn-ID row
        if (nextAccount && (idMatch || isTimeRow)) account = `${account} ${nextAccount}`.trim();
      }

      // Check if account column was merged into details: e.g. "Paid to SAMPLE BENEFICIARY HDFC Bank - 1234"
      const bankSuffixMatch = details.match(/^(.*?)\s+(State\s+Bank\s+[Oo]f\s+India|Kotak\s+Mahindra\s+Bank|Punjab\s+National\s+Bank|Bank\s+[Oo]f\s+[A-Za-z]+|Union\s+Bank\s+[Oo]f\s+India|Central\s+Bank\s+[Oo]f\s+India|IDFC\s+FIRST\s+Bank|Standard\s+Chartered\s+Bank|Airtel\s+Payments\s+Bank|Paytm\s+Payments\s+Bank|[A-Za-z&.]+\s+Bank|SBI|HDFC|ICICI|Axis|Kotak|PNB|BOB)\s*(?:-|–|—|a\/c|acct?\.?|account|ending(?:\s+in)?|no\.?)?\s*[xX*•]*(\d{2,4})\s*$/i);
      if (bankSuffixMatch) {
        details = bankSuffixMatch[1].trim();
        if (!account) {
          account = `${bankSuffixMatch[2].trim()} - ${bankSuffixMatch[3].trim()}`;
        }
      }

      const narration = details + (noteText ? ` — ${noteText}` : '');

      records.push({
        date: this.normalizeDate(anchor.rawDate),
        narration,
        amount: anchor.amount,
        explicitType: isRefund ? 'refund' : (isIncome ? 'income' : 'expense'),
        referenceNo: txnId || null,
        accountInfo: account,
        paymentMode: /credit\s*card/i.test(account) ? 'UPI (RuPay Credit Card)' : 'UPI'
      });
    }

    return { records, profile: 'Navi UPI Statement', accountMetadata };
  }

  /**
   * SBI Bank Statement PDF Parser — Coordinate-Based Column Classification
   * SBI column X boundaries (normalized from real PDF analysis):
   *   Date:       x < 80
   *   ValueDate:  80 ≤ x < 135
   *   Narration:  135 ≤ x < 300
   *   DR/CR flag: 300 ≤ x < 345
   *   Amount:     345 ≤ x < 490
   *   Balance:    x ≥ 490
   */
  parseSBICoordinates(spatialRows, fullText, accountMetadata) {
    const records = [];

    // SBI transaction type markers appear on a row slightly above the date row
    const typeMarkerRe = /\b(WDL\s*TFR|DEP\s*TFR|WDL\s*CLG|DEP\s*CLG|DEBIT|CREDIT|ATM|NEFT|IMPS|RTGS|UPI|INT|SWP|INT\s*CREDIT|MANDATE|MANDATE\s*DEBIT|CEMTEX\s*DEP|INTEREST\s*CREDIT)\b/i;
    const dateRe = /^\d{2}\/\d{2}\/\d{4}$/;
    const amountRe = /^-?[\d,]+\.\d{2}$/;

    let pendingType = null; // 'income' or 'expense'
    let pendingNarration = [];
    let transactions = [];
    let currentTxn = null;

    for (let i = 0; i < spatialRows.length; i++) {
      const row = spatialRows[i];
      const words = row.words;
      if (!words || words.length === 0) continue;

      const rowText = words.map(w => w.text).join(' ');

      // Ignore summary / footer / header rows
      if (/^(Account Summary|Statement From|Statement Summary|Page no\.)/i.test(rowText.trim()) || this.isColumnHeaderRow(rowText)) {
        continue;
      }

      const hasDate = words.some(w => dateRe.test(w.text) && w.x < 85);
      const debitWords = words.filter(w => w.x >= 320 && w.x < 410 && w.text !== '-' && amountRe.test(w.text));
      const creditWords = words.filter(w => w.x >= 410 && w.x < 490 && w.text !== '-' && amountRe.test(w.text));
      const balanceWords = words.filter(w => w.x >= 490 && w.text !== '-' && amountRe.test(w.text));
      const hasAmount = debitWords.length > 0 || creditWords.length > 0;

      if ((typeMarkerRe.test(rowText) || /CEMTEX/i.test(rowText)) && !hasDate) {
        // Type marker row
        if (/WDL|DEBIT|ATM|SWP|MANDATE\s*DEBIT/i.test(rowText)) pendingType = 'expense';
        else if (/DEP|CREDIT|INT\s*CREDIT|INTEREST\s*CREDIT|CEMTEX/i.test(rowText)) pendingType = 'income';
        
        pendingNarration = words.filter(w => w.x >= 100 && w.x < 320).map(w => w.text);
        continue;
      }

      if (hasDate && (hasAmount || balanceWords.length > 0)) {
        // Primary transaction row
        if (currentTxn) transactions.push(currentTxn);

        const dateWord = words.find(w => dateRe.test(w.text) && w.x < 85);
        const dateStr = dateWord ? dateWord.text : null;

        const narrationWords = words.filter(w => w.x >= 130 && w.x < 315 && w.text !== '-').map(w => w.text);

        let txnAmount = 0;
        let explicitType = 'expense';

        if (creditWords.length > 0) {
          txnAmount = parseFloat(creditWords[0].text.replace(/,/g, '')) || 0;
          explicitType = 'income';
        } else if (debitWords.length > 0) {
          txnAmount = parseFloat(debitWords[0].text.replace(/,/g, '')) || 0;
          explicitType = 'expense';
        } else if (pendingType === 'income') {
          explicitType = 'income';
        }

        const fullNarrationWords = [...pendingNarration, ...narrationWords];
        let refMatch = rowText.match(/(?:UPI|UTR|IMPS|NEFT|REF)[\/\s:-]*([0-9A-Za-z]{8,18})/i);
        if (!refMatch && pendingNarration.length > 0) {
          refMatch = pendingNarration.join(' ').match(/(?:UPI|UTR|IMPS|NEFT|REF)[\/\s:-]*([0-9A-Za-z]{8,18})/i);
        }

        currentTxn = {
          date: this.normalizeDate(dateStr),
          narrationParts: fullNarrationWords,
          amount: txnAmount,
          explicitType: explicitType,
          referenceNo: refMatch ? refMatch[1] : this.extractRefNo(rowText)
        };
        pendingType = null;
        pendingNarration = [];

      } else if (currentTxn && !hasDate && !hasAmount) {
        // Continuation row — add to narration
        const continuationWords = words.filter(w => w.x >= 100 && w.x < 320 && w.text !== '-').map(w => w.text);
        if (continuationWords.length > 0) {
          currentTxn.narrationParts.push(...continuationWords);
        }
      }
    }

    if (currentTxn) transactions.push(currentTxn);

    // Build final records
    for (const txn of transactions) {
      if (!txn.amount || txn.amount <= 0) continue;
      const narration = txn.narrationParts
        .join(' ')
        .replace(/\s+/g, ' ')
        .replace(/AT\s+\d+.*$/gi, '') // remove branch codes
        .trim();

      records.push({
        date: txn.date,
        narration: narration || 'SBI Bank Transaction',
        amount: txn.amount,
        explicitType: txn.explicitType,
        referenceNo: txn.referenceNo
      });
    }

    const detectedProfile = 'State Bank of India (SBI) Statement';
    return { records, profile: detectedProfile, accountMetadata };
  }

  /**
   * Generic Table PDF Parser — works for HDFC, ICICI, Axis, Kotak, PNB, etc.
   * Strategy:
   *   1. Find header row (contains Date + Narration/Description + Debit/Credit/Amount)
   *   2. Record X-coordinate ranges for each column
   *   3. For each subsequent row: if Date-band word exists → new transaction row
   *      else → append narration to current transaction (multi-line narration)
   */
  parseGenericTablePDF(spatialRows, fullText, accountMetadata, opts = {}) {
    const records = [];

    // Column header synonym maps (covers HDFC, ICICI, Axis, Kotak, PNB, YES Bank, IndusInd, RBL etc.)
    const colPatterns = {
      date:      /^(txn\.?\s*date|transaction\s*date|date|value\s*dt|value\s*date|trans\.?\s*date|posting\s*date|trade\s*date|effective\s*date|processed\s*date)$/i,
      narration: /^(narration|description|particulars|details|remark|payee|paid\s*to|transaction\s*details|trans\.?\s*details|remarks|merchant|narrative|account\s*details|chq\.?\s*no\.?\s*narration)$/i,
      debit:     /^(debit|withdrawal|withdrawal\s*amt\.?|dr|dr\.?\s*amount|paid\s*out|amount\s*debited|wdl|wdl\.?\s*amt|debit\s*amount|withdrawals)$/i,
      credit:    /^(credit|deposit|deposit\s*amt\.?|cr|cr\.?\s*amount|paid\s*in|amount\s*credited|dep|credit\s*amount|deposits|receipts)$/i,
      amount:    /^(amount|total|net\s*amount|transaction\s*amount|txn\s*amount|spend\s*amount|amount\s*\([₹inr]+\))$/i,
      ref:       /^(ref\.?\s*no\.?|chq\.?\/ref\.?\s*no\.?|chq\s*\/\s*ref\s*no|utr|cheque\s*no\.?|chq\.?|txn\.?\s*id|transaction\s*id|reference|ref|narr\.?\s*no\.?|instrument\s*no|instrument\s*id)$/i,
      balance:   /^(balance|closing\s*balance|avl\.?\s*bal\.?|running\s*balance|running\s*bal|outstanding\s*balance|ledger\s*balance)$/i,
    };
    // Optional paying-account column (UPI apps); other banks never pass it, so their column bands are unchanged
    if (opts.accountCol) colPatterns.account = opts.accountCol;
    const accountOf = (t) => opts.accountCol ? { accountInfo: this.upiInstrumentText(t.accountParts.join(' ')) } : {};

    // Find header row
    let headerRowIdx = -1;
    let colBands = {}; // {colName: {xMin, xMax}}

    for (let i = 0; i < spatialRows.length; i++) {
      const row = spatialRows[i];
      const words = row.words;
      const matches = {};
      for (const w of words) {
        for (const [col, re] of Object.entries(colPatterns)) {
          if (re.test(w.text.trim())) {
            matches[col] = w.x;
          }
        }
      }
      // Need at least date + (debit or credit or amount) to count as a header
      if (matches.date !== undefined && (matches.debit !== undefined || matches.credit !== undefined || matches.amount !== undefined)) {
        headerRowIdx = i;
        // Build column bands: xMin = col x - 5, xMax = next col x - 5
        const sorted = Object.entries(matches).sort((a, b) => a[1] - b[1]);
        for (let k = 0; k < sorted.length; k++) {
          const [col, xStart] = sorted[k];
          const xEnd = k + 1 < sorted.length ? sorted[k + 1][1] - 2 : 9999;
          colBands[col] = { xMin: xStart - 10, xMax: xEnd };
        }
        break;
      }
    }

    if (headerRowIdx === -1) {
      // No structured table header found — return empty to trigger fallback
      return { records: [], profile: accountMetadata.bankName + ' Statement', accountMetadata };
    }

    const getCol = (word, col) => {
      const band = colBands[col];
      if (!band) return false;
      return word.x >= band.xMin && word.x <= band.xMax;
    };

    let currentTxn = null;

    for (let i = headerRowIdx + 1; i < spatialRows.length; i++) {
      const row = spatialRows[i];
      const words = row.words;
      if (!words || words.length === 0) continue;

      // Check if this row starts a new transaction (has a date-band word that looks like a date)
      const dateWord = words.find(w => getCol(w, 'date') && /\d{1,2}[\/\-\.]\d{1,2}[\/\-\.](\d{2}|\d{4})/.test(w.text));

      if (dateWord) {
        // Save previous
        if (currentTxn && currentTxn.amount > 0) {
          records.push({
            date: currentTxn.date,
            narration: currentTxn.narrationParts.join(' ').replace(/\s+/g, ' ').trim() || 'Bank Transaction',
            amount: currentTxn.amount,
            explicitType: currentTxn.explicitType,
            referenceNo: currentTxn.referenceNo,
            ...accountOf(currentTxn)
          });
        }

        const narrWords = words.filter(w => getCol(w, 'narration')).map(w => w.text);
        const refWords = words.filter(w => getCol(w, 'ref')).map(w => w.text);
        const accountParts = words.filter(w => getCol(w, 'account')).map(w => w.text);

        // Debit
        let txnAmount = 0;
        let explicitType = 'expense';

        const debitW = words.find(w => getCol(w, 'debit'));
        const creditW = words.find(w => getCol(w, 'credit'));
        const amountW = words.find(w => getCol(w, 'amount'));

        if (debitW) {
          const v = parseFloat(debitW.text.replace(/[₹,\s]/g, ''));
          if (!isNaN(v) && v > 0) { txnAmount = v; explicitType = 'expense'; }
        }
        if (txnAmount === 0 && creditW) {
          const v = parseFloat(creditW.text.replace(/[₹,\s]/g, ''));
          if (!isNaN(v) && v > 0) {
            txnAmount = v;
            const isRefund = /refund|reversal|cashback/i.test(narrWords.join(' '));
            explicitType = isRefund ? 'refund' : 'income';
          }
        }
        if (txnAmount === 0 && amountW) {
          const v = parseFloat(amountW.text.replace(/[₹,\s]/g, ''));
          if (!isNaN(v) && v > 0) {
            txnAmount = v;
            const narrText = narrWords.join(' ');
            const isRefund = /refund|reversal|cashback/i.test(narrText);
            const incomeRe = /credit|deposit|salary|refund|received|cashback/i;
            const isIncome = (opts.accountCol ? this.upiIsIncome(narrText, incomeRe) : incomeRe.test(narrText));
            explicitType = isRefund ? 'refund' : (isIncome ? 'income' : 'expense');
          }
        }

        const rowText = words.map(w => w.text).join(' ');
        const refNo = refWords.join(' ').trim() || this.extractRefNo(rowText);

        currentTxn = {
          date: this.normalizeDate(dateWord.text),
          narrationParts: narrWords,
          amount: txnAmount,
          explicitType: explicitType,
          referenceNo: refNo || null,
          accountParts
        };

      } else if (currentTxn) {
        // Continuation row — append narration words
        const rowText = words.map(w => w.text).join(' ');
        // Skip rows that look like totals or page footers
        if (/^(total|subtotal|page|opening|closing|grand|statement)/i.test(rowText.trim()) || this.isColumnHeaderRow(rowText)) continue;
        if (colBands.account) currentTxn.accountParts.push(...words.filter(w => getCol(w, 'account')).map(w => w.text));
        const narrContinuation = words.filter(w => {
          if (colBands.account && getCol(w, 'account')) return false; // wrapped account text ("- 1234") is not narration
          // Add words in narration band OR anywhere that doesn't look like an amount/date
          const isDate = /\d{1,2}[\/\-\.]\d{1,2}[\/\-\.](\d{2}|\d{4})/.test(w.text);
          const isAmount = /^[\d,]+\.\d{2}$/.test(w.text);
          return !isDate && !isAmount;
        }).map(w => w.text);
        if (narrContinuation.length > 0) {
          currentTxn.narrationParts.push(...narrContinuation);
        }

        // Also pick up debit/credit amount if not already found
        if (currentTxn.amount === 0) {
          const debitW = words.find(w => getCol(w, 'debit'));
          const creditW = words.find(w => getCol(w, 'credit'));
          const amountW = words.find(w => getCol(w, 'amount'));
          if (debitW) {
            const v = parseFloat(debitW.text.replace(/[₹,\s]/g, ''));
            if (!isNaN(v) && v > 0) { currentTxn.amount = v; currentTxn.explicitType = 'expense'; }
          } else if (creditW) {
            const v = parseFloat(creditW.text.replace(/[₹,\s]/g, ''));
            if (!isNaN(v) && v > 0) { currentTxn.amount = v; currentTxn.explicitType = 'income'; }
          } else if (amountW) {
            const v = parseFloat(amountW.text.replace(/[₹,\s]/g, ''));
            if (!isNaN(v) && v > 0) { currentTxn.amount = v; }
          }
        }
      }
    }

    // Push last
    if (currentTxn && currentTxn.amount > 0) {
      records.push({
        date: currentTxn.date,
        narration: currentTxn.narrationParts.join(' ').replace(/\s+/g, ' ').trim() || 'Bank Transaction',
        amount: currentTxn.amount,
        explicitType: currentTxn.explicitType,
        referenceNo: currentTxn.referenceNo,
        ...accountOf(currentTxn)
      });
    }

    const detectedProfile = accountMetadata.bankName + ' Statement PDF';
    return { records, profile: detectedProfile, accountMetadata };
  }

  /**
   * Payer instrument of a UPI-app row ("Paid by HDFC Bank 1234", "Debited from XXXXXXXX3863", "Paid to HDFC Bank 1234"
   * on GPay received rows, or a bare "State Bank Of India - 1234") -> the accountInfo string, else null when the
   * line is not an instrument line. '' means wallet / UPI Lite / no usable text: the row uses the default account.
   */
  upiInstrumentLine(line) {
    const s = String(line || '').replace(/^\d{1,2}:\d{2}\s*(?:[AP]M)?\s+/i, '');
    const m = s.match(/^paid\s+to\s+(.+)$/i)
      || s.match(/(?:^|\s)(?:paid\s+(?:by|from|using|via)|debited\s+from|credited\s+to|payment\s+(?:method|mode))\b\s*:?\s*(.*)$/i);
    if (m) return this.upiInstrumentText(m[1].replace(/\s*(?:UPI\s*)?(?:Transaction|Txn)\s*ID.*$|\s*UTR.*$/i, ''));
    return /bank/i.test(s) && /^[A-Za-z][A-Za-z &.]{2,40}?\s*-\s*[Xx*•]*\d{4}$/.test(s) ? this.upiInstrumentText(s) : null;
  }

  // "Paid to / Bill payment of HDFC Credit Card" is money going out even though the line says "credit"
  upiIsIncome(line, incomeRe) {
    if (/\breceived\s+from\b/i.test(line)) return true;
    return !/\b(?:paid\s+(?:to|for)|bill\s*payment|recharge)\b/i.test(line) && incomeRe.test(line);
  }

  upiInstrumentText(raw) {
    const s = String(raw || '').replace(/^bank\s+account\s*/i, '').trim();
    if (!s || /wallet|upi\s*lite|balance/i.test(s)) return '';
    // detectRuPayCC only accepts a plain "credit card" when the text also ties it to UPI
    return /credit\s*card|rupay/i.test(s) ? `${s} on UPI` : s;
  }

  /**
   * UPI App PDF Parser (PhonePe / Google Pay statement exports)
   * These have block-per-transaction format similar to Navi but with different anchor patterns.
   * PhonePe: "DATE  MERCHANT NAME  ₹AMOUNT  Debit/Credit"
   * GPay: 3 rows per transaction in Date | Details | Amount columns:
   *   "01 Jan,  Paid to NAME  ₹100" / "2025  UPI Transaction ID: 123…" / "10:30 AM  Paid by HDFC Bank 1234"
   *   (received rows carry "Paid to <bank> <last4>" as the account line). The year may wrap onto the 2nd row.
   */
  parseUPIAppPDF(spatialRows, fullText, accountMetadata) {
    const records = [];
    const lines = spatialRows.map(r => r.words.map(w => w.text).join(' ').trim()).filter(l => l);

    // Re-join a date whose year wrapped onto the next row: "01 Jan," + "2025 UPI Transaction ID: …"
    for (let k = 0; k + 1 < lines.length; k++) {
      const wrapped = lines[k].match(/^(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*),?(?!\w)(?!\s*\d{4}\b)/i);
      const year = wrapped && lines[k + 1].match(/^((?:19|20)\d{2})\b\s*/);
      if (year) {
        lines[k] = `${wrapped[1]} ${year[1]}${lines[k].slice(wrapped[0].length)}`;
        lines[k + 1] = lines[k + 1].slice(year[0].length);
      }
    }

    const anchorRe = /(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4}|\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\w*,?\s+\d{2,4})/i;
    const periodRe = /\b\d{1,2}\s+[A-Za-z]+\s+\d{4}\s*-\s*\d{1,2}\s+[A-Za-z]+\s+\d{4}\b/i;
    let i = 0;
    while (i < lines.length) {
      const line = lines[i];
      const dateM = line.match(anchorRe);
      const amtM = dateM ? this.extractAmount(line, dateM[0]) : null;

      // Skip statement period summary headers e.g. "01 March 2026 - 31 August 2026 ₹1,14,905.49 ₹41,318.66"
      if (dateM && amtM && !periodRe.test(line) && !/statement\s*period/i.test(line)) {
        const amount = amtM.value;
        {
          const isRefund = /refund|reversal|cashback/i.test(line);
          const isTransfer = /self\s*transfer/i.test(line);
          const isIncome = !isTransfer && this.upiIsIncome(line, /credit|received|refund|cashback|\bcr\b/i);

          // Collect next 1-2 lines as narration supplement; the payer account line and txn ID (up to 4 rows down) are kept apart
          let narration = line.replace(dateM[0], '').replace(amtM.text, '').replace(/\s+/g, ' ').trim();
          let accountInfo = '';
          let txnId = '';
          for (let j = i + 1; j <= Math.min(i + 4, lines.length - 1); j++) {
            const nextLine = lines[j];
            const nextDate = nextLine.match(anchorRe);
            if (nextDate && this.extractAmount(nextLine, nextDate[0])) break;
            const inst = this.upiInstrumentLine(nextLine);
            if (inst !== null) accountInfo = inst;
            const tid = nextLine.match(/(?:UPI\s*)?Transaction\s*ID[:\s]*([A-Za-z0-9]{8,30})/i);
            if (tid) txnId = tid[1];
            if (j <= i + 2 && inst === null && !tid && !/^\d{1,2}:\d{2}\s*(?:[AP]M)?$/i.test(nextLine) && nextLine.length > 3 && nextLine.length < 100) {
              narration += ' ' + nextLine;
            }
          }

          records.push({
            date: this.normalizeDate(dateM[0]),
            narration: narration || 'UPI Transaction',
            amount: amount,
            explicitType: isRefund ? 'refund' : (isTransfer ? 'transfer' : (isIncome ? 'income' : 'expense')),
            referenceNo: txnId || this.extractRefNo(line),
            accountInfo,
            paymentMode: 'UPI'
          });
        }
      }
      i++;
    }

    const detectedProfile = accountMetadata.bankName + ' Statement PDF';
    return { records, profile: detectedProfile, accountMetadata };
  }

  /**
   * Fallback: enhanced line-by-line parser with multi-line narration grouping.
   * Used when no structured table is detected.
   */
  extractTransactionsFromTextLines(lines, fileName = '', accountMetadata = null) {
    if (!accountMetadata) {
      accountMetadata = this.extractAccountMetadata(lines.join(' '), fileName);
    }
    const records = [];
    const detectedProfile = accountMetadata.bankName + (accountMetadata.type === 'credit_card' ? ' Card' : ' Statement');

    const dateRe = /(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})|(\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{2,4})/i;
    const amountRe = /(?:₹|Rs\.?|INR)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2}))\b/g;

    let currentTxn = null;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const dateMatch = line.match(dateRe);

      if (dateMatch) {
        // Extract amounts
        const amounts = [];
        let m;
        const re = /(?:₹|Rs\.?|INR)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2}))\b/g;
        while ((m = re.exec(line)) !== null) {
          const n = parseFloat(m[1].replace(/,/g, ''));
          if (!isNaN(n) && n > 0) amounts.push(n);
        }

        if (amounts.length > 0) {
          // Save previous
          if (currentTxn) {
            records.push({
              date: currentTxn.date,
              narration: currentTxn.narrationParts.join(' ').replace(/\s+/g, ' ').trim() || 'Bank Transaction',
              amount: currentTxn.amount,
              explicitType: currentTxn.explicitType,
              referenceNo: currentTxn.referenceNo
            });
          }

          const rawDateStr = dateMatch[0];
          const isRefund = /refund|reversal|cashback/i.test(line);
          const isCredit = /credit|cr\b|deposit|received|refund|cashback|reversal|salary/i.test(line);
          const isDebit = /debit|dr\b|withdrawal|paid|purchase|pos|spent/i.test(line);
          const narration = line.replace(rawDateStr, '').trim();

          currentTxn = {
            date: this.normalizeDate(rawDateStr),
            narrationParts: [narration],
            amount: amounts[0],
            explicitType: isRefund ? 'refund' : (isCredit ? 'income' : (isDebit ? 'expense' : (narration.toLowerCase().includes('salary') ? 'income' : 'expense'))),
            referenceNo: this.extractRefNo(line)
          };
        }
      } else if (currentTxn && line.length > 3 && line.length < 150) {
        // Potential narration continuation — only if line has no big amounts
        const bigAmounts = [];
        let bm;
        const bre = /([0-9]{1,3}(?:,[0-9]{2,3})+(?:\.[0-9]{1,2})?)/g;
        while ((bm = bre.exec(line)) !== null) {
          const n = parseFloat(bm[1].replace(/,/g, ''));
          if (!isNaN(n) && n > 1000) bigAmounts.push(n);
        }
        if (bigAmounts.length === 0) {
          currentTxn.narrationParts.push(line);
        }
      }
    }

    // Push last
    if (currentTxn && currentTxn.amount > 0) {
      records.push({
        date: currentTxn.date,
        narration: currentTxn.narrationParts.join(' ').replace(/\s+/g, ' ').trim() || 'Bank Transaction',
        amount: currentTxn.amount,
        explicitType: currentTxn.explicitType,
        referenceNo: currentTxn.referenceNo
      });
    }

    return { records, profile: detectedProfile, accountMetadata };
  }

  // --- CSV / TEXT PARSER ---
  parseCSVText(csvText, fileName = '') {
    const results = Papa.parse(csvText, {
      header: true,
      skipEmptyLines: true,
      dynamicTyping: false
    });

    const accountMetadata = this.extractAccountMetadata(csvText, fileName);

    if (!results.data || results.data.length === 0) {
      const unheadered = Papa.parse(csvText, { skipEmptyLines: true });
      const rawRes = this.parseRawTableRows(unheadered.data, fileName);
      return { ...rawRes, accountMetadata };
    }

    const rows = results.data;
    const headers = Object.keys(rows[0] || {});

    const dateCol = headers.find(h => /date|txn.?date|time/i.test(h));
    const descCol = headers.find(h => /narration|desc|particular|remark|details|payee|paid to|party/i.test(h));
    const debitCol = headers.find(h => this.isDebitHeader(h));
    const creditCol = headers.find(h => this.isCreditHeader(h));
    const amountCol = headers.find(h => /amount|total|sum/i.test(h));
    const refCol = headers.find(h => /ref|utr|txn.?id|cheque|chq|order/i.test(h));
    const instrumentCol = headers.find(h => /account|instrument|mode|method|paid.?via|paid.?from|card/i.test(h));

    const records = [];

    for (let i = 0; i < rows.length; i++) {
      const r = rows[i];
      const rawDate = dateCol ? r[dateCol] : null;
      const narration = descCol ? r[descCol] : Object.values(r).join(' ');
      const refNo = refCol ? r[refCol] : null;
      const accountInfo = instrumentCol ? r[instrumentCol] : null;

      const { amount, explicitType } = this.resolveAmountCells(
        debitCol ? r[debitCol] : null,
        creditCol ? r[creditCol] : null,
        amountCol ? r[amountCol] : null,
        narration
      );

      if (amount > 0 && rawDate) {
        records.push({
          date: this.normalizeDate(rawDate),
          narration: narration || 'CSV Record',
          amount: amount,
          explicitType: explicitType,
          referenceNo: refNo,
          accountInfo: accountInfo
        });
      }
    }

    const detectedProfile = accountMetadata.bankName + (accountMetadata.type === 'credit_card' ? ' Card CSV' : ' Statement CSV');
    return { records, profile: detectedProfile, accountMetadata };
  }

  parseRawTableRows(rows, fileName = '') {
    const records = [];
    const fullText = rows.map(r => r.join(' ')).join(' ');
    const accountMetadata = this.extractAccountMetadata(fullText, fileName);

    rows.forEach(row => {
      const line = row.join(' ');
      const dateMatch = line.match(/(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})/);
      const amt = dateMatch ? this.extractAmount(line, dateMatch[0]) : null;

      if (dateMatch && amt) {
        records.push({
          date: this.normalizeDate(dateMatch[0]),
          narration: line,
          amount: amt.value,
          explicitType: /\bdr\b/i.test(line) ? 'expense' : (/\b(cr|credit|deposit)\b/i.test(line) ? 'income' : null)
        });
      }
    });

    return { records, profile: 'Raw Statement', accountMetadata };
  }

  // --- EXCEL (.XLSX / .XLS) PARSER ---
  parseExcelBuffer(buffer, fileName = '') {
    const workbook = XLSX.read(buffer, { type: 'array' });
    const firstSheetName = workbook.SheetNames[0];
    const worksheet = workbook.Sheets[firstSheetName];
    const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 });

    if (!jsonData || jsonData.length === 0) {
      throw new Error('Excel sheet is empty or contains unreadable format.');
    }

    const fullText = jsonData.slice(0, 15).map(r => (Array.isArray(r) ? r.join(' ') : '')).join(' ');
    const accountMetadata = this.extractAccountMetadata(fullText, fileName);

    let headerIdx = 0;
    for (let i = 0; i < Math.min(10, jsonData.length); i++) {
      const row = jsonData[i];
      if (Array.isArray(row) && row.some(cell => /date|narration|amount|debit|credit/i.test(String(cell)))) {
        headerIdx = i;
        break;
      }
    }

    const headers = (jsonData[headerIdx] || []).map(h => String(h).trim());
    const dateIdx = headers.findIndex(h => /date|txn.?date|time/i.test(h));
    const descIdx = headers.findIndex(h => /narration|desc|particular|remark|details|payee|paid to/i.test(h));
    const debitIdx = headers.findIndex(h => this.isDebitHeader(h));
    const creditIdx = headers.findIndex(h => this.isCreditHeader(h));
    const amountIdx = headers.findIndex(h => /amount|total|sum/i.test(h));
    const refIdx = headers.findIndex(h => /ref|utr|txn.?id|cheque|chq/i.test(h));
    const instIdx = headers.findIndex(h => /account|instrument|mode|method|paid.?via|paid.?from|card/i.test(h));

    const records = [];

    for (let i = headerIdx + 1; i < jsonData.length; i++) {
      const row = jsonData[i];
      if (!Array.isArray(row) || row.length === 0) continue;

      const rawDate = dateIdx >= 0 ? row[dateIdx] : row[0];
      const narration = descIdx >= 0 ? row[descIdx] : row.join(' ');
      const refNo = refIdx >= 0 ? row[refIdx] : null;
      const accountInfo = instIdx >= 0 ? row[instIdx] : null;

      const { amount, explicitType } = this.resolveAmountCells(
        debitIdx >= 0 ? row[debitIdx] : null,
        creditIdx >= 0 ? row[creditIdx] : null,
        amountIdx >= 0 ? row[amountIdx] : null,
        narration
      );

      if (amount > 0 && rawDate) {
        records.push({
          date: this.normalizeDate(rawDate),
          narration: narration || 'Excel Record',
          amount: amount,
          explicitType: explicitType,
          referenceNo: refNo,
          accountInfo: accountInfo
        });
      }
    }

    const detectedProfile = accountMetadata.bankName + (accountMetadata.type === 'credit_card' ? ' Card Excel' : ' Statement Excel');
    return { records, profile: detectedProfile, accountMetadata };
  }

  /**
   * Financial Institution, Instrument Type & Account Number Metadata Extractor
   */
  extractAccountMetadata(fullText, fileName = '') {
    const fn = fileName.toLowerCase();
    const header = fullText.slice(0, 1500).toLowerCase();
    const text = (fullText + ' ' + fileName).toLowerCase();
    
    // 1. Detect Financial Institution / App (Priority: Filename -> Header -> Body)
    let bankName = 'Primary Bank';
    let color = '#1e3a8a';
    
    if (fn.includes('navi') || header.includes('paid via navi') || header.includes('navi technologies')) {
      bankName = 'Navi UPI';
      color = '#2563eb';
    } else if (fn.includes('gpay') || fn.includes('google pay') || header.includes('google pay app') || header.includes('payments made by you on the google pay')) {
      bankName = 'Google Pay';
      color = '#4338ca';
    } else if (fn.includes('phonepe') || header.includes('phonepe statement') || header.includes('phonepe private limited')) {
      bankName = 'PhonePe';
      color = '#6b21a8';
    } else if (fn.includes('paytm') || header.includes('paytm payments bank') || header.includes('paytm wallet') || header.includes('paytm transaction statement')) {
      bankName = 'Paytm';
      color = '#0369a1';
    } else if (fn.includes('sbi') || fn.includes('state bank') || header.includes('state bank of india') || /\bsbin\b/i.test(header) || header.includes('wdl tfr') || header.includes('dep tfr')) {
      bankName = 'State Bank of India (SBI)';
      color = '#065f46';
    } else if (header.includes('phonepe')) {
      bankName = 'PhonePe';
      color = '#6b21a8';
    } else if (header.includes('paytm')) {
      bankName = 'Paytm';
      color = '#0369a1';
    } else if (header.includes('google pay') || /\bgpay\b/i.test(header)) {
      bankName = 'Google Pay';
      color = '#4338ca';
    } else if (fn.includes('hdfc') || header.includes('hdfc bank') || header.includes('www.hdfcbank.com') || header.includes('hdfc card')) {
      bankName = 'HDFC Bank';
      color = '#1e3a8a';
    } else if (fn.includes('icici') || header.includes('icici bank') || header.includes('icici card')) {
      bankName = 'ICICI Bank';
      color = '#9a3412';
    } else if (fn.includes('axis') || header.includes('axis bank') || header.includes('axis card')) {
      bankName = 'Axis Bank';
      color = '#831843';
    } else if (fn.includes('kotak') || header.includes('kotak mahindra') || header.includes('kotak card')) {
      bankName = 'Kotak Mahindra Bank';
      color = '#b91c1c';
    } else if (fn.includes('pnb') || fn.includes('punjab national') || header.includes('punjab national') || /\bpnb\b/i.test(header)) {
      bankName = 'Punjab National Bank (PNB)';
      color = '#7c2d12';
    } else if (fn.includes('baroda') || header.includes('bank of baroda') || /\bbob\b/i.test(fn)) {
      bankName = 'Bank of Baroda';
      color = '#ea580c';
    } else if (fn.includes('canara') || header.includes('canara bank')) {
      bankName = 'Canara Bank';
      color = '#0284c7';
    } else if (fn.includes('union') || header.includes('union bank')) {
      bankName = 'Union Bank of India';
      color = '#0d9488';
    } else if (/\bcred\b/i.test(fn) || /\bcred\b/i.test(header)) {
      bankName = 'CRED';
      color = '#171717';
    } else if (header.includes('amazon pay')) {
      bankName = 'Amazon Pay';
      color = '#c2410c';
    } else if (text.includes('hdfc bank') && !text.includes('wdl tfr')) {
      bankName = 'HDFC Bank';
      color = '#1e3a8a';
    } else if (text.includes('state bank') || /\bsbi\b/.test(text)) {
      bankName = 'State Bank of India (SBI)';
      color = '#065f46';
    } else {
      const cleanName = fileName.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
      bankName = cleanName.length > 2 ? cleanName : 'Personal Account';
      color = '#334155';
    }

    // 2. Detect Instrument Type (Strict: prevent bank statements with "limit" from being marked credit_card)
    let type = 'bank';
    const isBankStatement = text.includes('wdl tfr') || text.includes('dep tfr') || text.includes('savings account') || text.includes('current account') || text.includes('clear balance');
    if (!isBankStatement && (text.includes('credit card statement') || text.includes('minimum amount due') || text.includes('total amount due') || text.includes('card statement') || text.includes('card ending in'))) {
      type = 'credit_card';
    } else if (!isBankStatement && (bankName === 'Navi UPI' || text.includes('google pay') || text.includes('gpay') || text.includes('phonepe') || text.includes('paytm wallet') || text.includes('amazon pay wallet') || text.includes('upi history') || text.includes('upi statement'))) {
      type = 'wallet';
    } else if (text.includes('cash')) {
      type = 'cash';
    } else {
      type = 'bank';
    }


    // 3. Detect Account / Card Number Last 4 digits
    let last4 = '';
    
    // Explicit keywords with account/card number (extracts trailing 4 digits)
    // Explicit keywords with account/card number (extracts trailing 4 digits)
    const keywordMatch = fullText.match(/(?:account(?:\s*number|\s*no\.?)?|a\/c(?:\s*number|\s*no\.?)?|acct|card(?:\s*number|\s*no\.?)?|ending in|ending with|xx|[*X]{4,12})[\s#:-]*([0-9]{4,18})/i);
    if (keywordMatch && keywordMatch[1]) {
      const numStr = keywordMatch[1].trim();
      last4 = numStr.slice(-4);
    }

    if (!last4 && (bankName.includes('SBI') || bankName.includes('State Bank'))) {
      const nums = [...fullText.matchAll(/\b(\d{11})\b/g)].map(m => m[1]);
      const cifAt = fullText.search(/cif\s*(?:number|no)/i);
      const accAt = fullText.search(/account\s*(?:number|no)/i);
      // CIF number precedes the account number in the header, so with both labels present the second value is the account
      const pick = (cifAt >= 0 && accAt > cifAt && nums.length > 1) ? nums[1] : nums[0];
      if (pick) last4 = pick.slice(-4);
    }

    if (!last4) {
      const cardPattern = fullText.match(/\b\d{4}\s*\d{4}\s*\d{4}\s*(\d{4})\b/);
      if (cardPattern) last4 = cardPattern[1];
    }

    if (!last4) {
      const maskedPattern = fullText.match(/\b[X*]{4,12}\s*(\d{4})\b/);
      if (maskedPattern) last4 = maskedPattern[1];
    }

    if (!last4 && fileName) {
      const fnDigits = fileName.match(/\b(\d{4})\b/);
      if (fnDigits && fnDigits[1] && (parseInt(fnDigits[1]) < 2020 || parseInt(fnDigits[1]) > 2035)) {
        last4 = fnDigits[1];
      }
    }

    // 4. Generate User-friendly Account Name
    let name = '';
    if (type === 'credit_card') {
      name = `${bankName} Credit Card` + (last4 ? ` (•••• ${last4})` : '');
    } else if (type === 'wallet') {
      name = /wallet/i.test(bankName) ? bankName : `${bankName} Wallet`;
    } else if (type === 'cash') {
      name = 'Cash in Hand';
    } else {
      name = `${bankName} Account` + (last4 ? ` (•••• ${last4})` : '');
    }

    // 5. Credit Limit detection (if credit card)
    let creditLimit = 100000;
    if (type === 'credit_card') {
      const limitMatch = fullText.match(/credit\s*limit[:\s]*(?:₹|rs\.?|inr)?\s*([0-9,]+)/i);
      if (limitMatch && limitMatch[1]) {
        const parsedLimit = parseFloat(limitMatch[1].replace(/,/g, ''));
        if (!isNaN(parsedLimit) && parsedLimit > 0) creditLimit = parsedLimit;
      }
    }

    // 6. Billing day detection
    let billingDay = 15;
    if (type === 'credit_card') {
      const dueMatch = fullText.match(/(?:due\s*date|statement\s*date)[:\s]*(\d{1,2})[\/\-\.]/i);
      if (dueMatch && dueMatch[1]) {
        const day = parseInt(dueMatch[1]);
        if (day >= 1 && day <= 31) billingDay = day;
      }
    }

    return {
      bankName,
      type,
      last4: last4 || (type === 'wallet' ? 'UPI' : (type === 'cash' ? 'CASH' : '0000')),
      name,
      color,
      creditLimit,
      billingDay
    };
  }

  /**
   * UPI RuPay Credit Card Detector
   * Automatically extracts linked RuPay CC instruments from UPI app narrations (GPay, PhonePe, Paytm, CRED, etc.)
   */
  detectRuPayCC(text) {
    if (!text) return null;
    const str = String(text);
    const lower = str.toLowerCase();

    // Check if the narration or metadata indicates a RuPay card or linked credit card on UPI
    const hasRuPay = /\b(rupay|rupay\s*cc|rupay\s*credit\s*card|rupay\s*card|upi-rupay|rupay-cc)\b/i.test(lower);
    const hasCreditCardUPI = /\b(credit\s*card|linked\s*card|cc\s*on\s*upi|card\s*ending)\b/i.test(lower) && 
                            /\b(upi|paid\s*via|debited\s*from|instrument|gpay|phonepe|paytm|cred)\b/i.test(lower);

    if (!hasRuPay && !hasCreditCardUPI) return null;

    // Detect Bank / Card Issuer
    let bankName = 'RuPay';
    let color = '#0f766e'; // Emerald-Teal brand gradient for RuPay

    if (/hdfc/i.test(str)) { bankName = 'HDFC Bank'; color = '#1e3a8a'; }
    else if (/icici/i.test(str)) { bankName = 'ICICI Bank'; color = '#9a3412'; }
    else if (/sbi|state\s*bank/i.test(str)) { bankName = 'SBI Card'; color = '#065f46'; }
    else if (/axis/i.test(str)) { bankName = 'Axis Bank'; color = '#831843'; }
    else if (/kotak/i.test(str)) { bankName = 'Kotak Bank'; color = '#b91c1c'; }
    else if (/pnb|punjab/i.test(str)) { bankName = 'PNB'; color = '#7c2d12'; }
    else if (/bob|baroda/i.test(str)) { bankName = 'Bank of Baroda'; color = '#ea580c'; }
    else if (/canara/i.test(str)) { bankName = 'Canara Bank'; color = '#0284c7'; }
    else if (/union/i.test(str)) { bankName = 'Union Bank'; color = '#0d9488'; }

    // Detect last 4 digits (e.g. RuPay Credit Card **4589 or ending in 4589)
    let last4 = '';
    const numMatch = str.match(/(?:card|rupay|cc|ending(?:\s*in)?|xx|[*X]{2,12}|a\/c)[\s#:-]*([0-9]{4})\b/i)
      || str.match(/\b[xX*]{2,}\s*([0-9]{2,4})\b/);
    if (numMatch && numMatch[1]) {
      last4 = numMatch[1];
    } else {
      const standAlone4 = str.match(/\b([0-9]{4})\b/);
      if (standAlone4 && standAlone4[1] && (parseInt(standAlone4[1]) < 2020 || parseInt(standAlone4[1]) > 2035)) {
        last4 = standAlone4[1];
      }
    }

    return {
      type: 'credit_card',
      bankName: `${bankName} RuPay`,
      last4: last4 || 'RUPAY',
      name: `${bankName} RuPay Credit Card` + (last4 ? ` (•••• ${last4})` : ''),
      color: color,
      creditLimit: 100000,
      billingDay: 15,
      isRuPay: true,
      isAutoDetected: true
    };
  }

  localISODate(d) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  buildISODate(year, month, day) {
    const maxYear = new Date().getFullYear() + 1;
    if (year < 1990 || year > maxYear) return null;
    const probe = new Date(year, month - 1, day);
    if (probe.getFullYear() !== year || probe.getMonth() !== month - 1 || probe.getDate() !== day) return null;
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  }

  /**
   * Returns a local-calendar 'YYYY-MM-DD' string, or null when the input can't be read
   * as a real date. Never converts through UTC (that shifts dates a day back in UTC+ zones).
   */
  normalizeDate(rawDate) {
    if (rawDate === null || rawDate === undefined || rawDate === '') return null;
    if (rawDate instanceof Date) return isNaN(rawDate.getTime()) ? null : this.localISODate(rawDate);

    const str = String(rawDate).trim();

    if (/^\d{5}$/.test(str)) {
      const d = new Date(Date.UTC(1899, 11, 30) + parseInt(str, 10) * 86400000);
      return this.buildISODate(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
    }

    const isoMatch = str.match(/^(\d{4})[\/\-\.](\d{1,2})[\/\-\.](\d{1,2})(?!\d)/);
    if (isoMatch) return this.buildISODate(+isoMatch[1], +isoMatch[2], +isoMatch[3]);

    const numMatch = str.match(/^(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{2}|\d{4})(?!\d)/);
    if (numMatch) {
      let first = +numMatch[1];
      let second = +numMatch[2];
      let year = +numMatch[3];
      if (numMatch[3].length === 2) year += 2000;
      // Indian statements are day-first; only swap when day-first is impossible (e.g. 01/15/2024).
      if (second > 12 && first <= 12) [first, second] = [second, first];
      return this.buildISODate(year, second, first);
    }

    const months = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12 };
    const dayFirst = str.match(/^(\d{1,2})[\s\-\/\.]+([A-Za-z]{3,9})\.?,?[\s\-\/\.]+(\d{2}|\d{4})(?!\d)/);
    if (dayFirst && months[dayFirst[2].slice(0, 3).toLowerCase()]) {
      let year = +dayFirst[3];
      if (dayFirst[3].length === 2) year += 2000;
      return this.buildISODate(year, months[dayFirst[2].slice(0, 3).toLowerCase()], +dayFirst[1]);
    }
    const monthFirst = str.match(/^([A-Za-z]{3,9})\.?\s+(\d{1,2}),?\s+(\d{4})(?!\d)/);
    if (monthFirst && months[monthFirst[1].slice(0, 3).toLowerCase()]) {
      return this.buildISODate(+monthFirst[3], months[monthFirst[1].slice(0, 3).toLowerCase()], +monthFirst[2]);
    }

    return null;
  }

  /**
   * Find the transaction amount in a statement line. The date and any time-of-day are
   * removed first so "15 Jan 2024 ... Rs. 1,250.00" yields 1250, not 15. Bare integers are
   * ignored (too easily an ID); an amount needs a currency prefix, thousands commas or decimals.
   */
  extractAmount(line, dateText = '') {
    let s = dateText ? String(line).replace(dateText, ' ') : String(line);
    s = s.replace(/\b\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?/gi, ' ');
    let m = s.match(/(?:₹|Rs\.?|INR)\s*([\d,]+(?:\.\d{1,2})?)/i);
    let value;
    let text;
    if (m) {
      value = parseFloat(m[1].replace(/,/g, ''));
      text = m[0];
    } else {
      m = s.match(/(?:^|[^\w.])(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+\.\d{1,2})(?![\w.])/);
      if (!m) return null;
      value = parseFloat(m[1].replace(/,/g, ''));
      text = m[1];
    }
    if (isNaN(value) || value <= 0) return null;
    return { value, text };
  }

  /**
   * True for a repeated table-header row ("Date  Narration  Chq./Ref.No.  Withdrawal  Deposit  Balance").
   * Banks reprint it at the top of every page; without this guard it is glued onto the previous
   * transaction's narration as if it were a wrapped line. Needs 3+ distinct column-header terms,
   * so a real narration that merely contains one of these words is never dropped.
   */
  isColumnHeaderRow(text) {
    const terms = [/\bdate\b/i, /\bvalue\s*d(?:t|ate)\b/i, /\b(?:narration|description|particulars|details|remarks)\b/i,
      /\b(?:chq|cheque|ref)\b/i, /\b(?:debit|withdrawal|dr)\b/i, /\b(?:credit|deposit|cr)\b/i, /\bbalance\b/i, /\bamount\b/i];
    return terms.filter(re => re.test(text)).length >= 3;
  }

  // Anchored so "Description" (des-CR-iption) or "Address" (ad-DR-ess) never count as credit/debit columns.
  isDebitHeader(h) {
    return /(^|[\s_\-.])(debits?|withdrawals?|dr|paid\s*out)(\b|$)/i.test(String(h));
  }

  isCreditHeader(h) {
    return /(^|[\s_\-.])(credits?|deposits?|cr|paid\s*in)(\b|$)/i.test(String(h));
  }

  /**
   * Resolve one CSV/Excel row's amount and direction. When the direction can't be known
   * (single unsigned Amount column) explicitType is null so the categorizer decides,
   * instead of forcing every such row to 'expense' (which turned salary into spending).
   */
  resolveAmountCells(debitCell, creditCell, amountCell, narration) {
    const isRefund = /\b(refund|reversal)\b/i.test(narration || '');
    const debit = this.parseAmountCell(debitCell).value;
    if (debit > 0) return { amount: debit, explicitType: 'expense' };

    const credit = this.parseAmountCell(creditCell).value;
    if (credit > 0) return { amount: credit, explicitType: isRefund ? 'refund' : 'income' };

    if (amountCell !== null && amountCell !== undefined && amountCell !== '') {
      const { value, suffix } = this.parseAmountCell(amountCell);
      if (!isNaN(value) && value !== 0) {
        let explicitType = null;
        if (value < 0 || suffix === 'dr') explicitType = 'expense';
        else if (isRefund) explicitType = 'refund';
        else if (suffix === 'cr' || /\b(credit|deposit|salary)\b/i.test(narration || '')) explicitType = 'income';
        return { amount: Math.abs(value), explicitType };
      }
    }
    return { amount: 0, explicitType: null };
  }

  /**
   * Parse a spreadsheet amount cell: handles currency symbols, thousands commas,
   * accounting-style negatives "(1,234.00)" and Cr/Dr suffixes.
   */
  parseAmountCell(raw) {
    if (raw === null || raw === undefined) return { value: NaN, suffix: null };
    let s = String(raw).trim();
    let negative = false;
    if (/^\(.*\)$/.test(s)) {
      negative = true;
      s = s.slice(1, -1);
    }
    let suffix = null;
    const suffixMatch = s.match(/\s*(cr|dr)\.?$/i);
    if (suffixMatch) {
      suffix = suffixMatch[1].toLowerCase();
      s = s.slice(0, suffixMatch.index);
    }
    let value = parseFloat(s.replace(/₹|Rs\.?|INR|,|\s/gi, ''));
    if (!isNaN(value) && negative) value = -value;
    return { value, suffix };
  }

  extractRefNo(text) {
    if (!text) return null;
    const utrMatch = text.match(/(?:UPI|UTR|IMPS|REF)[\/\s:-]*([0-9a-zA-Z]{8,18})/i);
    if (utrMatch) return utrMatch[1];
    
    const longDigits = text.match(/\b\d{10,14}\b/);
    if (longDigits) return longDigits[0];

    return null;
  }

  guessPaymentMode(narration, profile = '', detectedType = 'bank') {
    const text = (narration + ' ' + profile).toLowerCase();
    if (detectedType === 'credit_card' || text.includes('card') || text.includes('pos')) return 'Credit Card';
    if (detectedType === 'wallet' || text.includes('upi') || text.includes('gpay') || text.includes('phonepe') || text.includes('paytm')) return 'UPI';
    if (text.includes('imps')) return 'IMPS';
    if (text.includes('neft')) return 'NEFT';
    if (text.includes('ach') || text.includes('salary')) return 'NetBanking';
    if (detectedType === 'cash' || text.includes('cash')) return 'Cash';
    return 'Online Banking';
  }

  readFileAsText(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => resolve(e.target.result);
      reader.onerror = (e) => reject(e);
      reader.readAsText(file);
    });
  }

  readFileAsArrayBuffer(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => resolve(e.target.result);
      reader.onerror = (e) => reject(e);
      reader.readAsArrayBuffer(file);
    });
  }
}

// Global instance
if (typeof window !== 'undefined') {
  window.statementParser = new StatementParser();
}

// Node/test export (does not affect browser <script> usage)
if (typeof module !== 'undefined' && module.exports) {
  module.exports = StatementParser;
}
