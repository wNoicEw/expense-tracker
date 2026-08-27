/**
 * Smart Auto-Categorization & Self-Learning Rule Engine
 * 1. Auto-classifies transactions using built-in merchant profiles & learned rules.
 * 2. Unmatched/cryptic transactions are tagged as "Uncategorized" (Needs Review).
 * 3. When a user classifies an unknown transaction, it automatically learns the UPI ID / Merchant pattern
 *    and auto-categorizes all future (and past) matching transactions!
 */

class Categorizer {
  constructor() {
    this.merchantRules = [
      // Food & Dining
      {
        category: 'Food & Dining',
        keywords: ['swiggy', 'zomato', 'starbucks', 'mcdonald', 'kfc', 'burger king', 'domino', 'pizza hut', 'biryani', 'chaayos', 'chai point', 'cafe coffee day', 'subway', 'haldiram', 'barbeque nation', 'eatfit', 'bakehouse', 'restaurant', 'dining', 'dhaba', 'bakery', 'juice', 'shawarma', 'tiffin', 'canteen', 'sweets', 'sweets & snacks', 'food court'],
        cleanName: 'Food & Dining'
      },
      // Groceries & Daily Needs
      {
        category: 'Groceries & Mart',
        keywords: ['blinkit', 'zepto', 'instamart', 'bigbasket', 'dmart', 'd-mart', 'spencer', 'reliance retail', 'smart point', 'natures basket', 'more retail', 'grofers', 'bbdaily', 'country delight', 'dairy', 'supermarket', 'grocery', 'kirana', 'vegetable', 'fruits', 'provision', 'freshtohome', 'licious'],
        cleanName: 'Groceries'
      },
      // Shopping & E-Commerce
      {
        category: 'Shopping & E-Comm',
        keywords: ['amazon', 'flipkart', 'myntra', 'ajio', 'nykaa', 'zara', 'h&m', 'tata cliq', 'meesho', 'croma', 'reliance digital', 'decathlon', 'nike', 'adidas', 'uniqlo', 'apple store', 'ikea', 'lifestyle', 'westside', 'shoppers stop', 'lenskart', 'boat', 'snitch', 'urbanic', 'clovia', 'purplle', 'clothing', 'electronics', 'footwear'],
        cleanName: 'Shopping'
      },
      // Travel & Transport
      {
        category: 'Travel & Commute',
        keywords: ['uber', 'ola', 'rapido', 'irctc', 'makemytrip', 'cleartrip', 'yatra', 'goibibo', 'indigo', 'air india', 'vistara', 'akasa', 'spicejet', 'petrol', 'fuel', 'hpcl', 'bpcl', 'iocl', 'shell', 'indian oil', 'hindustan petroleum', 'bharat petroleum', 'metro', 'fastag', 'toll', 'parking', 'auto rickshaw', 'railway', 'train', 'bus', 'redbus', 'chalo'],
        cleanName: 'Travel'
      },
      // Bills & Utilities
      {
        category: 'Bills & Utilities',
        keywords: ['bescom', 'tata power', 'adani elec', 'mseb', 'uppcl', 'cesc', 'tneb', 'bwssb', 'water board', 'mahanagar gas', 'igl', 'adani gas', 'airtel', 'jio', 'vodafone', 'vi prepaid', 'vi postpaid', 'act fibernet', 'hathway', 'tata play', 'tatasky', 'dth', 'electricity', 'broadband', 'cylinder', 'indane', 'hp gas', 'bharat gas', 'bbps', 'billpay', 'utility'],
        cleanName: 'Bills & Utilities'
      },
      // Subscriptions & OTT
      {
        category: 'Subscriptions & OTT',
        keywords: ['netflix', 'spotify', 'amazon prime', 'hotstar', 'disney', 'apple music', 'youtube premium', 'sonyliv', 'zee5', 'chatgpt', 'openai', 'midjourney', 'claude', 'adobe', 'google one', 'google storage', 'icloud', 'cursor', 'github', 'notion', 'canva', 'playstation', 'xbox', 'steam', 'audible', 'kindle'],
        cleanName: 'Subscription'
      },
      // Health & Wellness
      {
        category: 'Health & Pharmacy',
        keywords: ['apollo pharmacy', 'netmeds', 'tata 1mg', '1mg', 'pharmeasy', 'medplus', 'cult.fit', 'cultfit', 'gold gym', 'anytime fitness', 'hospital', 'clinic', 'dental', 'diagnostic', 'pathology', 'dr lal pathlabs', 'metropolis', 'fortis', 'max healthcare', 'manipal', 'medical', 'pharmacy', 'chemist', 'doctor', 'physio', 'opticals'],
        cleanName: 'Health & Medical'
      },
      // Investments & Wealth
      {
        category: 'Investments & SIP',
        keywords: ['zerodha', 'groww', 'upstox', 'coin', 'kuvera', 'angel one', 'indmoney', 'smallcase', 'et money', 'mutual fund', 'sip', 'nsdl', 'cdsl', 'nps', 'ppf', 'sgb', 'motilal oswal', 'icici direct', 'hdfc securities', 'uti amc', 'sbi mutual', 'nippon', 'mirae asset', 'parag parikh', 'stocks', 'equity', 'gold'],
        cleanName: 'Investments'
      },
      // Rent & Housing
      {
        category: 'Rent & Housing',
        keywords: ['rent', 'maintenance', 'housing society', 'nobroker', 'magicbricks', 'housing.com', 'mygate', 'urban company', 'housekeeping', 'apartment', 'landlord', 'flat maintenance'],
        cleanName: 'Housing & Rent'
      },
      // Transfers & Credit Card Bill Payments — MUST BE CHECKED BEFORE SALARY
      // CRITICAL: These must be typed as 'transfer' to prevent double-counting expenses
      // when both a CC statement (showing the spend) and a bank statement (showing the CC bill payment) are imported.
      {
        category: 'Transfers & CC Bill',
        keywords: [
          // Generic CC payment terms
          'credit card payment', 'cc payment', 'card bill payment', 'card payment',
          'cc bill', 'card bill', 'card settlement', 'card outstanding',
          'minimum due', 'min due', 'total amount due', 'amount due',
          'bill payment credit card', 'credit card bill',
          // Payment platforms & aggregators
          'cred', 'billdesk cc', 'billdesk card', 'billdesk credit',
          'razorpay card', 'payu card',
          // Autopay / Standing Instructions / ECS / NACH
          'autopay cc', 'autopay card', 'auto pay card', 'auto pay credit',
          'nach card', 'nach credit card', 'nach cc',
          'ecs credit card', 'ecs card', 'ecs cc',
          'si credit card', 'si card payment', 'standing instruction card',
          // Bank-specific CC payment labels
          'hdfc card payment', 'hdfc cc payment', 'hdfc credit card',
          'sbi card payment', 'sbi cc payment', 'sbi credit card payment',
          'icici card payment', 'icici cc payment',
          'axis card payment', 'axis cc payment',
          'kotak card payment', 'kotak cc payment',
          'amex payment', 'amex bill', 'american express payment',
          'sc card payment', 'citi card payment', 'citi cc payment',
          'bob card payment', 'pnb card payment', 'canara card payment',
          'rbl card payment', 'indusind card payment', 'yes bank card payment',
          // NEFT/IMPS/RTGS to card
          'neft to card', 'imps to card', 'rtgs to card',
          'neft card', 'imps card', 'rtgs card',
          // Fund transfers between own accounts
          'self transfer', 'fund transfer', 'own account transfer',
          'imps transfer', 'neft transfer', 'rtgs transfer',
          'internal transfer', 'sweep transfer',
          // ATM & Cash
          'atm cash', 'atm withdrawal', 'cash deposit', 'atm deposit',
          'cash withdrawal'
        ],
        cleanName: 'Transfer'
      },
      // Salary & Professional Income
      {
        category: 'Salary & Professional',
        keywords: ['salary', 'payroll', 'ach credit', 'ach cr/', 'tech corp', 'infotech', 'tcs', 'infosys', 'wipro', 'accenture', 'google india', 'microsoft india', 'amazon dev', 'corporate stipend', 'wages', 'compensation'],
        cleanName: 'Salary Credit'
      },
      // Freelance & Business
      {
        category: 'Freelance & Side Hustle',
        keywords: ['freelance', 'consulting', 'upwork', 'fiverr', 'stripe', 'paypal', 'client payment', 'saas payout', 'royalty', 'ad revenue', 'google adsense'],
        cleanName: 'Freelance'
      }
    ];
  }

  /**
   * Extract UPI ID or key merchant identifier from raw narration
   */
  extractIdentifier(rawNarration) {
    if (!rawNarration) return '';

    // Match UPI ID (e.g. name@okaxis, merchant@icici, 9876543210@paytm, user@ybl)
    const upiMatch = rawNarration.match(/([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)/i);
    if (upiMatch) {
      return upiMatch[1].toLowerCase();
    }

    // Match POS merchant tags (e.g. POS/MERCHANT NAME/LOCATION)
    const posMatch = rawNarration.match(/POS\/([^\/]+)/i);
    if (posMatch && posMatch[1].trim().length > 3) {
      return posMatch[1].trim().toLowerCase();
    }

    // Match ACH / IMPS Payee Name
    const impsMatch = rawNarration.match(/(?:IMPS|NEFT|ACH|TRANSFER)[\/\s:-]+[0-9]*[\/\s:-]*([a-zA-Z\s]{3,25})/i);
    if (impsMatch && impsMatch[1].trim().length > 3) {
      return impsMatch[1].trim().toLowerCase();
    }

    // Fallback clean token
    const clean = this.cleanNarration(rawNarration).toLowerCase();
    return clean.split(' ').slice(0, 3).join(' ');
  }

  /**
   * Categorize transaction text and return category, clean title, and classification status
   */
  async categorize(rawText, amount, customRules = []) {
    const text = (rawText || '').toLowerCase();
    const identifier = this.extractIdentifier(rawText);

    // 1. Check user-defined learned rules FIRST (highest precedence)
    if (customRules && customRules.length > 0) {
      for (const rule of customRules) {
        const pattern = (rule.pattern || rule.keyword || '').toLowerCase();
        if (pattern && (text.includes(pattern) || (identifier && identifier.includes(pattern)))) {
          return {
            category: rule.category,
            cleanTitle: this.cleanNarration(rawText),
            type: rule.type || (amount >= 0 ? 'expense' : 'income'),
            confidence: 'learned',
            needsReview: false,
            matchedPattern: pattern,
            identifier: identifier
          };
        }
      }
    }

    // 2. Check Built-in Merchant Rules (High confidence)
    for (const rule of this.merchantRules) {
      for (const keyword of rule.keywords) {
        if (text.includes(keyword)) {
          let type = 'expense';
          if (rule.category === 'Salary & Professional' || rule.category === 'Freelance & Side Hustle') {
            type = 'income';
          } else if (rule.category === 'Transfers & CC Bill') {
            type = 'transfer';
          }

          return {
            category: rule.category,
            cleanTitle: this.extractMerchantName(rawText, keyword),
            type: type,
            confidence: 'high',
            needsReview: false,
            matchedKeyword: keyword,
            identifier: identifier
          };
        }
      }
    }

    // 3. Fallback heuristic for salary / credit (word-boundary safe)
    if (text.includes('salary') || text.includes('payroll') || text.includes('ach credit') || /\bach cr\//i.test(text)) {
      return {
        category: 'Salary & Professional',
        cleanTitle: this.cleanNarration(rawText),
        type: 'income',
        confidence: 'high',
        needsReview: false,
        identifier: identifier
      };
    }

    // 4. Undetected / Low Confidence -> Mark as "Uncategorized" (Needs Review)
    return {
      category: 'Uncategorized',
      cleanTitle: this.cleanNarration(rawText) || 'Unclassified Transaction',
      type: 'expense',
      confidence: 'low',
      needsReview: true,
      identifier: identifier
    };
  }

  /**
   * Teach the engine a new classification rule and auto-reclassify existing matching transactions!
   */
  async learnRuleAndReclassify(pattern, category, type = 'expense') {
    if (!pattern || !category || category === 'Uncategorized') return { learnedCount: 0 };

    const cleanPattern = pattern.trim().toLowerCase();

    // 1. Save rule to IndexedDB
    const newRule = {
      id: 'rule_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6),
      pattern: cleanPattern,
      category: category,
      type: type,
      createdAt: new Date().toISOString()
    };
    await window.db.put('rules', newRule);

    // 2. Retroactively update all existing matching transactions in DB
    const allTransactions = await window.db.getAll('transactions');
    let reclassifiedCount = 0;

    for (const t of allTransactions) {
      const narr = (t.rawNarration || '').toLowerCase();
      const desc = (t.description || '').toLowerCase();
      const ref = (t.referenceNo || '').toLowerCase();

      if (narr.includes(cleanPattern) || desc.includes(cleanPattern) || ref.includes(cleanPattern)) {
        t.category = category;
        t.type = type;
        t.needsReview = false;
        t.confidence = 'learned';
        reclassifiedCount++;
      }
    }

    if (reclassifiedCount > 0) {
      await window.db.putBatch('transactions', allTransactions);
    }

    return {
      rule: newRule,
      reclassifiedCount: reclassifiedCount
    };
  }

  /**
   * Extract pretty merchant / payee name from bank & UPI narration
   */
  extractMerchantName(rawNarration, matchedKeyword = '', type = 'expense') {
    return this.cleanIndianTransactionTitle(rawNarration, type);
  }

  /**
   * Smart Indian Transaction Cleaner — converts chaotic raw bank/UPI narrations into clean, readable merchant/payee titles
   */
  cleanIndianTransactionTitle(rawNarration, transactionType = 'expense') {
    if (!rawNarration || !rawNarration.trim()) return 'Transaction';
    
    let raw = rawNarration.trim();

    // 1. Navi statement format: "Paid to NAME — Note [Bank Instrument]" or "Received from..."
    const naviMatch = raw.match(/^(Paid\s+to|Paid\s+for|Received\s+from|Refund\s+from)\s+([^—\[]+)(?:\s*[—\-]\s*([^\[]+))?/i);
    if (naviMatch) {
      const direction = naviMatch[1].toLowerCase();
      const person = naviMatch[2].trim();
      const note = (naviMatch[3] || '').trim();
      const personClean = person.split(/\s+/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');

      if (note && note.length > 1) {
        return `${personClean} (${note})`.slice(0, 40);
      } else if (direction.includes('received')) {
        return `${personClean} (Received)`.slice(0, 40);
      } else if (direction.includes('refund')) {
        return `${personClean} (Refund)`.slice(0, 40);
      }
      return personClean.slice(0, 40);
    }

    const lowerText = raw.toLowerCase();

    // 2. Standard brand mappings
    const brandMap = {
      'swiggy': 'Swiggy Food',
      'instamart': 'Swiggy Instamart',
      'zomato': 'Zomato Dining',
      'blinkit': 'Blinkit Groceries',
      'zepto': 'Zepto Quick Mart',
      'bigbasket': 'BigBasket',
      'amazon': 'Amazon India',
      'flipkart': 'Flipkart',
      'myntra': 'Myntra Fashion',
      'uber': 'Uber Rides',
      'ola': 'Ola Cabs',
      'rapido': 'Rapido Bike Taxi',
      'groww': 'Groww',
      'zerodha': 'Zerodha',
      'cred': 'CRED Club',
      'irctc': 'IRCTC Indian Railways',
      'uts': 'IRCTC UTS (Train Ticket)',
      'indian r': 'Indian Railways (IRCTC)',
      'indian s': 'SBI ePay / Govt. Portal',
      'google': 'Google Pay / BBPS',
      'dominos': "Domino's Pizza",
      'barbequ': 'Barbeque Nation',
      'jugals': 'Jugals Sweets',
      'sbi life': 'SBI Life Insurance',
      'iit guwa': 'IIT Guwahati Fee',
      'flightsm': 'Flightsmode Travel',
      'k s foods': 'K S Foods',
      'bhojoho': 'Bhojohori Manna Restaurant',
      'yeasin': 'Yeasin Ali',
      'santher': 'Santhiya',
      'santhiya': 'Santhiya',
      'indstock': 'INDstocks (Trading)',
      'mutual f': 'Groww Mutual Funds (BSE)',
      'iccl': 'ICCL (Groww Mutual Funds)',
      'sbimops': 'SBI MOPS Portal Fee',
      'grips': 'GRIPS WB State Govt. Portal',
      'sujay': 'Sujay S',
      'sahil': 'Sahil Ar',
      'banhisha': 'Banhisha',
      'kuntal': 'Kuntal Saha',
      'ishita': 'Ishita Saha',
      'rajib': 'Rajib Saha',
      'tarun': 'Tarun Ka',
      'sanjay': 'Sanjay',
      'nuego': 'NueGo Bus Travel',
      'bharatpe': 'BharatPe Merchant',
      'netflix': 'Netflix OTT',
      'spotify': 'Spotify Music',
      'airtel': 'Airtel Broadband / Bill',
      'jio': 'Reliance Jio Recharge',
      'bescom': 'BESCOM Electricity',
      'starbucks': 'Starbucks Coffee',
      'apollo': 'Apollo Pharmacy'
    };

    for (const [k, v] of Object.entries(brandMap)) {
      if (lowerText.includes(k)) {
        if (transactionType === 'income' && !v.toLowerCase().includes('received') && !v.endsWith(')')) {
          return `${v} (Received)`;
        }
        return v;
      }
    }

    // 3. UPI Format: UPI/DR/<ref>/<NAME>/<BANK>/<VPA>/... or UPI/<NAME>/PAYMENT/...
    if (/UPI\s*\//i.test(raw)) {
      const parts = raw.split('/').map(p => p.trim()).filter(Boolean);
      const upiIdx = parts.findIndex(p => /UPI/i.test(p));
      
      let payee = '';
      if (upiIdx !== -1) {
        if (parts.length > upiIdx + 3 && (/^DR$/i.test(parts[upiIdx + 1]) || /^CR$/i.test(parts[upiIdx + 1]))) {
          payee = parts[upiIdx + 3];
        } else if (parts.length > upiIdx + 1) {
          payee = parts[upiIdx + 1];
        }
      }

      payee = payee.replace(/\b\d{8,}\s+AT\s+\d+.*$/i, '').replace(/\bAT\s+\d+.*$/i, '').trim();

      if (payee) {
        const cleanWords = payee.split(/\s+/)
          .filter(w => !['dr', 'cr', 'upi', 'paid', 'payment', 'p', 'at', 'in', '-'].includes(w.toLowerCase()))
          .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase());

        if (cleanWords.length > 0) {
          let title = cleanWords.join(' ');
          if (transactionType === 'income') title += ' (Received)';
          return title.slice(0, 35);
        }
      }
    }



    // 3. Direct Debit / Mandate / AMC
    if (/sbi life/i.test(raw)) return 'SBI Life Insurance (Mandate)';
    if (/atm\s*card\s*amc|atmcard\s*amc/i.test(raw)) return 'SBI Debit Card Annual Fee (AMC)';
    if (/error\s*cr/i.test(raw)) return 'Bank Correction / Adjustment';

    // 4. Interest Credit
    if (/interest\s*credit|int\s*credit/i.test(raw)) return 'Savings Account Interest';

    // 5. CEMTEX / Govt Direct Benefit
    if (/cemtex/i.test(raw)) {
      if (/banglar yuba/i.test(raw)) return 'Govt. Assistance — Banglar Yuba Sathi';
      const benefit = raw.replace(/CEMTEX\s+(?:DEP|CR)\s*/i, '').trim();
      return `Govt. Direct Benefit (${benefit.slice(0, 20)})`;
    }

    // 6. NEFT / IMPS transfers
    if (/neft|imps/i.test(raw)) {
      const neftMatch = raw.match(/(?:NEFT|IMPS)\*[^*]+\*[^*]+\*([^*-]+)/i);
      if (neftMatch) {
        const party = neftMatch[1].trim();
        if (/icici prudentia/i.test(party)) return 'ICICI Prudential Life Insurance';
        return party.split(/\s+/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ').slice(0, 35);
      }
    }

    // 7. Insurance Survival Benefit
    if (/survival\s*benefit/i.test(raw)) return 'Insurance Survival Benefit Credit';

    // 8. Reversal / Internal Transfer code (e.g. 009769... AT 04744 CHAKDAH)
    if (/^\d{8,}\s+AT\s+\d+/i.test(raw)) {
      return transactionType === 'income' ? 'Instant Reversal / Refund' : 'Direct Account Transfer';
    }

    // 9. Generic cleanup fallback
    let clean = raw
      .replace(/\b\d{8,}\s+AT\s+\d+.*$/i, '')
      .replace(/\bAT\s+\d+.*$/i, '')
      .replace(/^(UPI|POS|NEFT|IMPS|DEP|WDL|TFR|CLG|TRF|TRANSFER|DEBIT|CREDIT)[\s/-]+/i, '')
      .replace(/[-_/]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();

    const words = clean.split(/\s+/)
      .filter(w => w.length > 1 && !/^\d+$/.test(w))
      .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
      .slice(0, 4);

    return words.join(' ').slice(0, 35) || 'Transaction';
  }

  /**
   * Strip cryptographic bank noise, raw routing prefixes, and timestamps
   */
  cleanNarration(narration) {
    return this.cleanIndianTransactionTitle(narration);
  }
}


// Global instance
window.categorizer = new Categorizer();
