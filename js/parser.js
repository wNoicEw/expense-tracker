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
  async parseFile(file, targetAccountId = null) {
    const fileName = file.name;
    const fileExt = fileName.split('.').pop().toLowerCase();

    let rawRecords = [];
    let detectedProfile = 'Generic Statement';
    let accountMetadata = null;

    if (fileExt === 'pdf') {
      const parsed = await this.parsePDF(file);
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
      throw new Error('No valid financial transactions could be extracted from this statement.');
    }

    // Auto-resolve or dynamically create Account in database if not explicitly provided
    let resolvedAccountId = targetAccountId;
    if (!resolvedAccountId && window.accountsManager) {
      resolvedAccountId = await window.accountsManager.getOrCreateAccountFromStatement(accountMetadata);
    }
    if (!resolvedAccountId) {
      resolvedAccountId = 'acc_cash_default';
    }

    // Categorize, normalize, and tag transactions
    const customRules = (await window.db.getAll('rules')) || [];
    const normalizedTransactions = [];

    // CC bill payment patterns for post-categorization safety net
    const ccPaymentRegex = /\b(credit card payment|cc payment|card bill|card payment|cc bill|cred|billdesk.*card|billdesk.*cc|autopay.*card|autopay.*cc|nach.*card|nach.*cc|ecs.*card|ecs.*cc|hdfc card|sbi card|icici card|axis card|kotak card|amex.*payment|amex.*bill|card settlement|card outstanding|minimum due|total amount due)\b/i;

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
      let txnAccountId = resolvedAccountId;
      let paymentMode = row.paymentMode || this.guessPaymentMode(narration, detectedProfile, accountMetadata?.type);

      const ruPayMeta = this.detectRuPayCC(combinedInfo);
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

      const txn = {
        id: 'txn_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9),
        date: row.date || new Date().toISOString().split('T')[0],
        amount: amount,
        type: txnType,
        category: txnCategory,
        needsReview: categorization.needsReview || false,
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
        notes: ruPayMeta ? `Paid via RuPay Credit Card (${ruPayMeta.name}) on UPI` : `Imported from ${fileName} (${detectedProfile})`,
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
  async parsePDF(file) {
    const arrayBuffer = await this.readFileAsArrayBuffer(file);
    const pdf = await pdfjsLib.getDocument({ data: arrayBuffer }).promise;
    
    let allTextLines = [];

    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i);
      const textContent = await page.getTextContent();
      
      const items = textContent.items;
      if (!items || items.length === 0) continue;

      const linesMap = new Map();
      items.forEach(item => {
        const y = Math.round(item.transform[5]);
        if (!linesMap.has(y)) {
          linesMap.set(y, []);
        }
        linesMap.get(y).push(item);
      });

      const sortedY = Array.from(linesMap.keys()).sort((a, b) => b - a);
      
      sortedY.forEach(y => {
        const lineItems = linesMap.get(y).sort((a, b) => a.transform[4] - b.transform[4]);
        const lineString = lineItems.map(it => it.str).join(' ').trim();
        if (lineString) {
          allTextLines.push(lineString);
        }
      });
    }

    return this.extractTransactionsFromTextLines(allTextLines, file.name);
  }

  extractTransactionsFromTextLines(lines, fileName = '') {
    const records = [];
    const fullText = lines.join(' ');
    const accountMetadata = this.extractAccountMetadata(fullText, fileName);
    let detectedProfile = accountMetadata.bankName + (accountMetadata.type === 'credit_card' ? ' Card' : ' Statement');

    const dateRegex = /(\d{1,2}[\/\-\.]\d{1,2}[\/\-\.]\d{2,4})|(\d{1,2}\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{2,4})/i;

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      const dateMatch = line.match(dateRegex);

      if (dateMatch) {
        const amounts = [];
        let match;
        const re = /(?:₹|Rs\.?|INR)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2}))\b/g;
        while ((match = re.exec(line)) !== null) {
          const num = parseFloat(match[1].replace(/,/g, ''));
          if (!isNaN(num) && num > 0) {
            amounts.push(num);
          }
        }

        if (amounts.length > 0) {
          const rawDateStr = dateMatch[0];
          const standardDate = this.normalizeDate(rawDateStr);

          let isCredit = /credit|cr\b|deposit|received|refund|cashback|reversal/i.test(line);
          let isDebit = /debit|dr\b|withdrawal|paid to|sent to|purchase|pos|spent/i.test(line);
          
          let narration = line.replace(rawDateStr, '').trim();
          const txnAmount = amounts[0];

          records.push({
            date: standardDate,
            narration: narration || 'Bank Transaction',
            amount: txnAmount,
            explicitType: isCredit ? 'income' : (isDebit ? 'expense' : (narration.toLowerCase().includes('salary') ? 'income' : 'expense')),
            referenceNo: this.extractRefNo(line)
          });
        }
      }
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
    const debitCol = headers.find(h => /debit|withdrawal|dr|paid out/i.test(h));
    const creditCol = headers.find(h => /credit|deposit|cr|paid in/i.test(h));
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

      let amount = 0;
      let explicitType = 'expense';

      if (debitCol && r[debitCol] && parseFloat(String(r[debitCol]).replace(/,/g, '')) > 0) {
        amount = parseFloat(String(r[debitCol]).replace(/,/g, ''));
        explicitType = 'expense';
      } else if (creditCol && r[creditCol] && parseFloat(String(r[creditCol]).replace(/,/g, '')) > 0) {
        amount = parseFloat(String(r[creditCol]).replace(/,/g, ''));
        explicitType = 'income';
      } else if (amountCol && r[amountCol]) {
        const rawNum = parseFloat(String(r[amountCol]).replace(/,/g, ''));
        amount = Math.abs(rawNum);
        if (rawNum < 0) explicitType = 'expense';
        else if (/credit|deposit|salary|refund/i.test(narration)) explicitType = 'income';
      }

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
      const amountMatch = line.match(/(?:₹|Rs\.?|INR)?\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2}))\b/);

      if (dateMatch && amountMatch) {
        records.push({
          date: this.normalizeDate(dateMatch[0]),
          narration: line,
          amount: parseFloat(amountMatch[1].replace(/,/g, '')),
          explicitType: /cr|credit|deposit/i.test(line) ? 'income' : 'expense'
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
    const debitIdx = headers.findIndex(h => /debit|withdrawal|dr|paid out/i.test(h));
    const creditIdx = headers.findIndex(h => /credit|deposit|cr|paid in/i.test(h));
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

      let amount = 0;
      let explicitType = 'expense';

      if (debitIdx >= 0 && row[debitIdx] && parseFloat(String(row[debitIdx]).replace(/,/g, '')) > 0) {
        amount = parseFloat(String(row[debitIdx]).replace(/,/g, ''));
        explicitType = 'expense';
      } else if (creditIdx >= 0 && row[creditIdx] && parseFloat(String(row[creditIdx]).replace(/,/g, '')) > 0) {
        amount = parseFloat(String(row[creditIdx]).replace(/,/g, ''));
        explicitType = 'income';
      } else if (amountIdx >= 0 && row[amountIdx]) {
        const rawNum = parseFloat(String(row[amountIdx]).replace(/,/g, ''));
        amount = Math.abs(rawNum);
        if (rawNum < 0) explicitType = 'expense';
        else if (/credit|deposit|salary|refund/i.test(narration)) explicitType = 'income';
      }

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
    const text = (fullText + ' ' + fileName).toLowerCase();
    
    // 1. Detect Financial Institution / App
    let bankName = 'Primary Bank';
    let color = '#1e3a8a';
    
    if (text.includes('hdfc')) {
      bankName = 'HDFC Bank';
      color = '#1e3a8a';
    } else if (text.includes('state bank') || text.includes('sbi')) {
      bankName = 'State Bank of India (SBI)';
      color = '#065f46';
    } else if (text.includes('icici')) {
      bankName = 'ICICI Bank';
      color = '#9a3412';
    } else if (text.includes('axis')) {
      bankName = 'Axis Bank';
      color = '#831843';
    } else if (text.includes('kotak')) {
      bankName = 'Kotak Mahindra Bank';
      color = '#b91c1c';
    } else if (text.includes('punjab national') || text.includes('pnb')) {
      bankName = 'Punjab National Bank (PNB)';
      color = '#7c2d12';
    } else if (text.includes('bank of baroda') || text.includes('bob')) {
      bankName = 'Bank of Baroda';
      color = '#ea580c';
    } else if (text.includes('canara')) {
      bankName = 'Canara Bank';
      color = '#0284c7';
    } else if (text.includes('union bank')) {
      bankName = 'Union Bank of India';
      color = '#0d9488';
    } else if (text.includes('cred')) {
      bankName = 'CRED';
      color = '#171717';
    } else if (text.includes('google pay') || text.includes('gpay')) {
      bankName = 'Google Pay';
      color = '#4338ca';
    } else if (text.includes('phonepe')) {
      bankName = 'PhonePe';
      color = '#6b21a8';
    } else if (text.includes('paytm')) {
      bankName = 'Paytm';
      color = '#0369a1';
    } else if (text.includes('amazon pay')) {
      bankName = 'Amazon Pay';
      color = '#c2410c';
    } else if (text.includes('american express') || text.includes('amex')) {
      bankName = 'American Express';
      color = '#0369a1';
    } else if (text.includes('standard chartered')) {
      bankName = 'Standard Chartered';
      color = '#047857';
    } else if (text.includes('citi')) {
      bankName = 'Citibank';
      color = '#1e40af';
    } else if (text.includes('cash')) {
      bankName = 'Cash';
      color = '#854d0e';
    } else {
      const cleanName = fileName.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
      bankName = cleanName.length > 2 ? cleanName : 'Personal Account';
      color = '#334155';
    }

    // 2. Detect Instrument Type
    let type = 'bank';
    if (text.includes('credit card') || text.includes('card statement') || text.includes('cred card') || text.includes('card ending') || text.includes('due date') || text.includes('credit limit') || text.includes('minimum amount due')) {
      type = 'credit_card';
    } else if (text.includes('google pay') || text.includes('gpay') || text.includes('phonepe') || text.includes('paytm wallet') || text.includes('amazon pay wallet') || text.includes('upi history') || text.includes('upi statement')) {
      type = 'wallet';
    } else if (text.includes('cash')) {
      type = 'cash';
    } else {
      type = 'bank';
    }

    // 3. Detect Account / Card Number Last 4 digits
    let last4 = '';
    
    // Explicit keywords with account/card number (extracts trailing 4 digits)
    const keywordMatch = fullText.match(/(?:account(?:\s*no\.?)?|a\/c(?:\s*no\.?)?|acct|card(?:\s*no\.?)?|ending in|ending with|xx|[*X]{4,12})[\s#:-]*([0-9]{4,18})/i);
    if (keywordMatch && keywordMatch[1]) {
      const numStr = keywordMatch[1].trim();
      last4 = numStr.slice(-4);
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
      name = `${bankName} UPI Wallet`;
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
    const numMatch = str.match(/(?:card|rupay|cc|ending(?:\s*in)?|xx|[*X]{2,12}|a\/c)[\s#:-]*([0-9]{4})\b/i);
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

  normalizeDate(rawDate) {
    if (!rawDate) return new Date().toISOString().split('T')[0];

    const str = String(rawDate).trim();

    if (/^\d{5}$/.test(str)) {
      const d = new Date((parseInt(str) - (25567 + 2)) * 86400 * 1000);
      return d.toISOString().split('T')[0];
    }

    const dmyMatch = str.match(/^(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{2,4})/);
    if (dmyMatch) {
      let day = dmyMatch[1].padStart(2, '0');
      let month = dmyMatch[2].padStart(2, '0');
      let year = dmyMatch[3];
      if (year.length === 2) year = '20' + year;
      return `${year}-${month}-${day}`;
    }

    const parsed = new Date(str);
    if (!isNaN(parsed.getTime())) {
      return parsed.toISOString().split('T')[0];
    }

    return new Date().toISOString().split('T')[0];
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
window.statementParser = new StatementParser();
