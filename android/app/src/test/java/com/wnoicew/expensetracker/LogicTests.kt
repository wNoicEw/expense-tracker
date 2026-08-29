package com.wnoicew.expensetracker

import com.wnoicew.expensetracker.data.PRESET_GRADIENTS
import com.wnoicew.expensetracker.data.UserProfile
import com.wnoicew.expensetracker.data.engine.CategorizerEngine
import com.wnoicew.expensetracker.data.engine.DuplicateDetectorEngine
import com.wnoicew.expensetracker.data.engine.ExportEngine
import com.wnoicew.expensetracker.data.engine.StatementParserEngine
import com.wnoicew.expensetracker.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class LogicTests {

    // ==========================================
    // 1. USER PROFILE TESTS
    // ==========================================

    @Test
    fun testUserProfileCreationAndInitials() {
        val profile1 = UserProfile(id = UUID.randomUUID().toString(), name = "Sarah Jenkins", initial = "S", gradientColors = PRESET_GRADIENTS[0])
        assertEquals("Sarah Jenkins", profile1.name)
        assertEquals("S", profile1.initial)
        assertTrue(profile1.gradientColors.isNotEmpty())

        val profile2 = UserProfile(id = UUID.randomUUID().toString(), name = " rahul sharma ", initial = "R", gradientColors = PRESET_GRADIENTS[1])
        assertEquals("rahul sharma", profile2.name.trim())
    }

    // ==========================================
    // 2. AUTO-CATEGORIZATION ENGINE TESTS (12 CATEGORIES)
    // ==========================================

    @Test
    fun testCategorizerEngineAllCategories() {
        // Food & Dining
        val food = CategorizerEngine.categorize("UPI/SWIGGY/PAYMENT/12345")
        assertEquals("Food & Dining", food.category)
        assertEquals("Swiggy Food", food.cleanTitle)
        assertEquals(TransactionType.EXPENSE, food.type)
        assertFalse(food.needsReview)

        // Groceries & Mart
        val groc = CategorizerEngine.categorize("BLINKIT QUICK COMMERCE")
        assertEquals("Groceries & Mart", groc.category)
        assertEquals("Blinkit Groceries", groc.cleanTitle)

        // Shopping & E-Commerce
        val shop = CategorizerEngine.categorize("AMAZON INDIA SELLER PAY")
        assertEquals("Shopping & E-Comm", shop.category)
        assertEquals("Amazon India", shop.cleanTitle)

        // Travel & Commute
        val travel = CategorizerEngine.categorize("UBER RIDES MUMBAI")
        assertEquals("Travel & Commute", travel.category)
        assertEquals("Uber Rides", travel.cleanTitle)

        // Bills & Utilities
        val bills = CategorizerEngine.categorize("BESCOM ELECTRICITY BILL BBPS")
        assertEquals("Bills & Utilities", bills.category)

        // Subscriptions & OTT
        val ott = CategorizerEngine.categorize("NETFLIX MONTHLY SUBSCRIPTION")
        assertEquals("Subscriptions & OTT", ott.category)
        assertEquals("Netflix OTT", ott.cleanTitle)

        // Health & Pharmacy
        val health = CategorizerEngine.categorize("APOLLO PHARMACY BANGALORE")
        assertEquals("Health & Pharmacy", health.category)

        // Investments & SIP
        val invest = CategorizerEngine.categorize("ZERODHA BROKING FUND ADD")
        assertEquals("Investments & SIP", invest.category)

        // Rent & Housing
        val rent = CategorizerEngine.categorize("NOBROKER RENT PAYMENT")
        assertEquals("Rent & Housing", rent.category)

        // Transfers & CC Bill
        val transfer = CategorizerEngine.categorize("CRED APP CARD PAYMENT")
        assertEquals("Transfers & CC Bill", transfer.category)
        assertEquals(TransactionType.TRANSFER, transfer.type)

        // Salary & Professional
        val salary = CategorizerEngine.categorize("ACH CR/TECH CORP/SALARY AUG")
        assertEquals("Salary & Professional", salary.category)
        assertEquals(TransactionType.INCOME, salary.type)

        // Freelance & Side Hustle
        val freelance = CategorizerEngine.categorize("UPWORK ESCROW PAYOUT")
        assertEquals("Freelance & Side Hustle", freelance.category)
        assertEquals(TransactionType.INCOME, freelance.type)

        // Uncategorized Fallback
        val unknown = CategorizerEngine.categorize("MISC RANDOM XYZ 99881122")
        assertEquals("Uncategorized", unknown.category)
        assertTrue(unknown.needsReview)
    }

    @Test
    fun testCategorizerCustomRuleLearningPrecedence() {
        val customRules = listOf(
            RuleEntity(pattern = "local chai shop", category = "Food & Dining", type = TransactionType.EXPENSE),
            RuleEntity(pattern = "crypto client", category = "Freelance & Side Hustle", type = TransactionType.INCOME)
        )

        val res1 = CategorizerEngine.categorize("UPI/DR/LOCAL CHAI SHOP/48392", 30.0, customRules)
        assertEquals("Food & Dining", res1.category)
        assertEquals("learned", res1.confidence)
        assertFalse(res1.needsReview)

        val res2 = CategorizerEngine.categorize("INWARD WIRE CRYPTO CLIENT", 50000.0, customRules)
        assertEquals("Freelance & Side Hustle", res2.category)
        assertEquals(TransactionType.INCOME, res2.type)
        assertEquals("learned", res2.confidence)
    }

    // ==========================================
    // 3. DUPLICATE DETECTOR & RESOLUTION TESTS
    // ==========================================

    @Test
    fun testDuplicateDetectorExactUTRMatch() {
        val t1 = TransactionEntity(
            id = "t1",
            date = System.currentTimeMillis(),
            description = "Starbucks",
            amount = 450.0,
            referenceNo = "UTR9876543210",
            sourceFile = "GPay.csv"
        )
        val t2 = TransactionEntity(
            id = "t2",
            date = System.currentTimeMillis() + 1000,
            description = "POS/STARBUCKS COFFEE",
            amount = 450.0,
            referenceNo = "UTR9876543210",
            sourceFile = "HDFC_Statement.csv"
        )

        val match = DuplicateDetectorEngine.compareTransactions(t1, t2)
        assertTrue(match.isMatch)
        assertEquals(99, match.confidence)

        // 99% UTR matches are 100% unequivocal and excluded from manual Duplicate Resolver review
        val duplicates = DuplicateDetectorEngine.scanDuplicates(listOf(t1, t2))
        assertEquals(0, duplicates.size)

        val (merged, neutralized) = DuplicateDetectorEngine.mergeTransactions(t1, t2)
        assertEquals("none", merged.duplicateStatus)
        assertEquals("deleted", neutralized.duplicateStatus)
        assertEquals(TransactionType.TRANSFER, neutralized.type)
    }

    @Test
    fun testDuplicateDetectorSameDateAmountAndMerchantMatch() {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val t1Time = sdf.parse("2026-08-20 12:00:00")!!.time
        val t2Time = sdf.parse("2026-08-20 14:00:00")!!.time // Same day, 2 hours later

        val t1 = TransactionEntity(
            id = "t1",
            date = t1Time,
            description = "Swiggy Order Food",
            amount = 320.0,
            sourceFile = "PhonePe.csv"
        )
        val t2 = TransactionEntity(
            id = "t2",
            date = t2Time,
            description = "SWIGGY BANGALORE",
            amount = 320.0,
            sourceFile = "ICICI_Statement.csv"
        )

        val duplicates = DuplicateDetectorEngine.scanDuplicates(listOf(t1, t2))
        assertEquals(1, duplicates.size)
        assertEquals(95, duplicates[0].confidence)

        val (kept1, kept2) = DuplicateDetectorEngine.markSeparate(t1, t2)
        assertEquals("dismissed", kept1.duplicateStatus)
        assertEquals("dismissed", kept2.duplicateStatus)
    }

    @Test
    fun testDuplicateDetectorDifferentDateIgnored() {
        val now = System.currentTimeMillis()
        val oneDayLater = now + (24 * 60 * 60 * 1000) // 1 day later
        val t1 = TransactionEntity(id = "t1", date = now, description = "Swiggy Food", amount = 320.0)
        val t2 = TransactionEntity(id = "t2", date = oneDayLater, description = "Swiggy Food", amount = 320.0)

        val duplicates = DuplicateDetectorEngine.scanDuplicates(listOf(t1, t2))
        assertTrue(duplicates.isEmpty())
    }

    @Test
    fun testDuplicateDetectorNonMatchingAmountsIgnored() {
        val t1 = TransactionEntity(id = "t1", date = System.currentTimeMillis(), description = "Uber", amount = 250.0)
        val t2 = TransactionEntity(id = "t2", date = System.currentTimeMillis(), description = "Uber", amount = 450.0)

        val duplicates = DuplicateDetectorEngine.scanDuplicates(listOf(t1, t2))
        assertTrue(duplicates.isEmpty())
    }

    @Test
    fun testFilterExactDuplicatesOnUpload() {
        val now = System.currentTimeMillis()
        val t1 = TransactionEntity(id = "t1", date = now, description = "Swiggy", amount = 350.0, referenceNo = "UTR123456789")
        val t2 = TransactionEntity(id = "t2", date = now, description = "Amazon Pay", amount = 1200.0, referenceNo = "UTR987654321")
        val existingDb = listOf(t1, t2)

        // Incoming batch has 1 exact duplicate of t1, 1 exact duplicate within batch, and 1 fresh transaction
        val incomingT1 = TransactionEntity(id = "inc1", date = now, description = "Swiggy", amount = 350.0, referenceNo = "UTR123456789")
        val incomingT3 = TransactionEntity(id = "inc3", date = now, description = "Zomato", amount = 550.0, referenceNo = "UTR555555555")
        val incomingT3Dup = TransactionEntity(id = "inc4", date = now, description = "Zomato", amount = 550.0, referenceNo = "UTR555555555")

        val result = DuplicateDetectorEngine.filterExactDuplicates(
            incoming = listOf(incomingT1, incomingT3, incomingT3Dup),
            existing = existingDb
        )

        assertEquals(1, result.filteredTransactions.size)
        assertEquals("Zomato", result.filteredTransactions[0].description)
        assertEquals(2, result.exactDuplicatesCount)
    }


    // ==========================================
    // 4. STATEMENT CSV PARSER TESTS
    // ==========================================

    @Test
    fun testStatementParserEngineHdfcFormat() {
        val csvLines = listOf(
            "Date,Narration,Chq/Ref Number,Value Dt,Withdrawal Amt,Deposit Amt,Closing Balance",
            "2026-08-20,UPI-SWIGGY-12345,REF889900,2026-08-20,450.00,,45000.00",
            "2026-08-21,ACH CR-TECH CORP SALARY,SAL112233,2026-08-21,,95000.00,140000.00"
        )

        val result = StatementParserEngine.parseCsvLines(csvLines, "HDFC_Aug.csv")
        assertEquals(2, result.transactions.size)
        assertEquals(95000.0, result.totalInflow, 0.001)
        assertEquals(450.0, result.totalOutflow, 0.001)
        assertEquals("Food & Dining", result.transactions[0].category)
        assertEquals("Salary & Professional", result.transactions[1].category)
    }

    @Test
    fun testStatementParserEngineGenericFormat() {
        val csvLines = listOf(
            "Date,Description,Amount,Type,Reference",
            "2026-08-20,Zomato Food Order,650.00,Debit,REF123456",
            "2026-08-21,Salary Credit,85000.00,Credit,SAL998877",
            "2026-08-22,Amazon Shopping,1999.00,Debit,AMZ445566"
        )

        val result = StatementParserEngine.parseCsvLines(csvLines, "General_Statement.csv")
        assertEquals(3, result.transactions.size)
        assertEquals(85000.0, result.totalInflow, 0.001)
        assertEquals(2649.0, result.totalOutflow, 0.001)
        assertEquals("Food & Dining", result.transactions[0].category)
        assertEquals("Salary & Professional", result.transactions[1].category)
        assertEquals("Shopping & E-Comm", result.transactions[2].category)
    }

    // ==========================================
    // 5. EXPORT & BACKUP ENGINE TESTS
    // ==========================================

    @Test
    fun testExportEngineCsvAndJsonBackup() {
        val txns = listOf(
            TransactionEntity(id = "t1", description = "Netflix", amount = 649.0, category = "Subscriptions & OTT", type = TransactionType.EXPENSE)
        )
        val accs = listOf(
            AccountEntity(id = "a1", name = "HDFC Bank", balance = 50000.0, type = "Bank Account")
        )
        val rules = listOf(
            RuleEntity(id = "r1", pattern = "chai", category = "Food & Dining", type = TransactionType.EXPENSE)
        )

        // CSV Test
        val csv = ExportEngine.generateCsv(txns, accs)
        assertTrue(csv.contains("Date,Type,Category,Description,Amount,Account,PaymentMode,ReferenceNo,SourceFile,Notes"))
        assertTrue(csv.contains("Netflix"))
        assertTrue(csv.contains("Subscriptions & OTT"))

        // JSON Backup Test
        val jsonString = ExportEngine.generateJsonBackup("Personal Profile", txns, accs, rules)
        assertTrue(jsonString.contains("Personal Profile"))
        assertTrue(jsonString.contains("transactions"))

        val restored = ExportEngine.parseJsonBackup(jsonString)
        assertEquals(1, restored.transactions.size)
        assertEquals(1, restored.accounts.size)
        assertEquals(1, restored.rules.size)
        assertEquals("Netflix", restored.transactions[0].description)
        assertEquals("HDFC Bank", restored.accounts[0].name)
    }

    // ==========================================
    // 6. AUTO-DETECTION OF CARDS & ACCOUNTS TESTS
    // ==========================================

    @Test
    fun testAccountAndCardAutoDetection() {
        // Test SBI Account Detection
        val sbiMeta = StatementParserEngine.extractAccountMetadata(
            "State Bank of India Account No: 12345678901234 Statement of Account",
            "SBI_Statement.pdf"
        )
        assertEquals("State Bank of India (SBI)", sbiMeta.bankName)
        assertEquals("Bank Account", sbiMeta.type)
        assertEquals("1234", sbiMeta.lastFour)
        assertEquals("State Bank of India (SBI) Account (•••• 1234)", sbiMeta.name)
        assertEquals(1, sbiMeta.gradientIndex)

        // Test HDFC Credit Card Detection
        val hdfcCCMeta = StatementParserEngine.extractAccountMetadata(
            "HDFC Bank Credit Card Statement Card ending in 4589 Credit Limit: 1,50,000 Total Amount Due: 24,500",
            "HDFC_CC_AUG.pdf"
        )
        assertEquals("HDFC Bank", hdfcCCMeta.bankName)
        assertEquals("Credit Card", hdfcCCMeta.type)
        assertEquals("4589", hdfcCCMeta.lastFour)
        assertEquals("HDFC Bank Credit Card (•••• 4589)", hdfcCCMeta.name)
        assertEquals(150000.0, hdfcCCMeta.creditLimit, 0.01)

        // Test PhonePe Digital Wallet Detection
        val phonePeMeta = StatementParserEngine.extractAccountMetadata(
            "PhonePe Statement Transaction History for 9876543210",
            "PhonePe_2025.pdf"
        )
        assertEquals("PhonePe", phonePeMeta.bankName)
        assertEquals("Digital Wallet", phonePeMeta.type)
        assertEquals("PhonePe UPI Wallet", phonePeMeta.name)

        // Test RuPay Credit Card on UPI Detection
        val rupayMeta = StatementParserEngine.detectRuPayCC("Paid to Swiggy via HDFC Bank RuPay Credit Card **7788")
        assertNotNull(rupayMeta)
        assertEquals("HDFC Bank", rupayMeta!!.bankName)
        assertEquals("Credit Card", rupayMeta.type)
        assertEquals("7788", rupayMeta.lastFour)
        assertEquals("HDFC Bank RuPay Credit Card (•••• 7788)", rupayMeta.name)
        assertTrue(rupayMeta.isRuPay)
    }

    @Test
    fun testSbiPdfParsing() {
        val sbiSampleLines = listOf(
            "Account Summary",
            "Welcome: Mr. KUNTAL SAHA",
            "Date of Statement : 25-08-2026",
            "Account open Date : 21/05/2019",
            "CIF Number : 89348187458",
            "Account Number : 38471245239",
            "REGULAR SB CHQ-INDIVIDUALS",
            "IFSC Code : SBIN0004744",
            "Statement From : 01-04-2025 to 31-03-2026",
            "STATEMENT OF ACCOUNT State Bank of India",
            "Branch Name : CHAKDAH",
            "Balance",
            "05/04/2025",
            "05/04/2025",
            "DEP TFR",
            "UPI/CR/102675420440/RAJIB SAHA/HDFC/rajib.0025/PA",
            "0097738162095 AT 04744 CHAKDAH",
            "-",
            "-",
            "23,000.00",
            "1,82,287.01",
            "05/04/2025",
            "05/04/2025",
            "DEP TFR",
            "UPI/CR/102675476703/RAJIB S/KKBK/rajib.0025/PAY",
            "0097738162095 AT 04744 CHAKDAH",
            "-",
            "-",
            "20,000.00",
            "2,02,287.01",
            "06/04/2025",
            "06/04/2025",
            "WDL TFR",
            "UPI/DR/509692869793/BANHISHA /BKID/banhisaha4/UPI",
            "0097690162095 AT 04744 CHAKDAH",
            "-",
            "10,000.00",
            "-",
            "1,92,287.01",
            "1",
            "Page no.",
            "Balance",
            "07/04/2025",
            "07/04/2025",
            "WDL TFR",
            "UPI/DR/546347275587/DUMMY NAME/bkid/4063101100/UP",
            "0097691162095 AT 04744 CHAKDAH",
            "-",
            "33,000.00",
            "-",
            "1,59,287.01"
        )

        val fullText = sbiSampleLines.joinToString("\n")
        val meta = StatementParserEngine.extractAccountMetadata(fullText, "SBI_Statement.pdf")
        assertEquals("State Bank of India (SBI)", meta.bankName)
        assertEquals("Bank Account", meta.type)
        assertEquals("5239", meta.lastFour)

        val result = StatementParserEngine.parseSbiPdf(sbiSampleLines, "SBI_Statement.pdf")
        assertEquals(4, result.transactions.size)
        assertEquals(43000.0, result.totalInflow, 0.001)
        assertEquals(43000.0, result.totalOutflow, 0.001)
        assertEquals(TransactionType.INCOME, result.transactions[0].type)
        assertEquals(23000.0, result.transactions[0].amount, 0.001)
        assertEquals(TransactionType.INCOME, result.transactions[1].type)
        assertEquals(20000.0, result.transactions[1].amount, 0.001)
        assertEquals(TransactionType.EXPENSE, result.transactions[2].type)
        assertEquals(10000.0, result.transactions[2].amount, 0.001)
        assertEquals(TransactionType.EXPENSE, result.transactions[3].type)
        assertEquals(33000.0, result.transactions[3].amount, 0.001)
    }
}

