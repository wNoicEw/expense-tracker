package com.wnoicew.expensetracker

import com.wnoicew.expensetracker.data.PRESET_GRADIENTS
import com.wnoicew.expensetracker.data.UserProfile
import com.wnoicew.expensetracker.data.engine.BackupFormatException
import com.wnoicew.expensetracker.data.engine.BackupReminderPolicy
import com.wnoicew.expensetracker.data.engine.CategorizerEngine
import com.wnoicew.expensetracker.data.engine.DuplicateDetectorEngine
import com.wnoicew.expensetracker.data.engine.ExportEngine
import com.wnoicew.expensetracker.data.engine.StatementParserEngine
import com.wnoicew.expensetracker.data.model.*
import com.wnoicew.expensetracker.ui.KpiMath
import com.wnoicew.expensetracker.ui.KpiRange
import com.wnoicew.expensetracker.ui.components.CalendarAggregator
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
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

    @Test
    fun testCsvEscapesQuotesNeutralisesFormulasAndSkipsMerged() {
        val txns = listOf(
            TransactionEntity(id = "a", description = "=HYPERLINK(\"http://x\")", amount = 10.0, category = "+cmd", note = "say \"hi\"", sourceFile = "@src"),
            TransactionEntity(id = "b", description = "Merged dup", amount = 5.0, duplicateStatus = "merged"),
            TransactionEntity(id = "c", description = "Big", amount = 12500000.0, accountName = "-Acct")
        )
        val csv = ExportEngine.generateCsv(txns, emptyList())
        val rows = csv.trim().lines()

        assertEquals(3, rows.size)
        assertFalse(csv.contains("Merged dup"))
        assertTrue(rows[1].contains("\"'=HYPERLINK(\"\"http://x\"\")\""))
        assertTrue(rows[1].contains("\"'+cmd\""))
        assertTrue(rows[1].contains("\"say \"\"hi\"\"\""))
        assertTrue(rows[1].contains("\"'@src\""))
        assertTrue(rows[2].contains(",12500000,") || rows[2].contains(",12500000.0,"))
        assertTrue(rows[2].contains("\"'-Acct\""))
        assertFalse(rows[2].contains("E7"))
    }

    @Test
    fun testJsonBackupRoundTripsDuplicateFields() {
        val txns = listOf(
            TransactionEntity(
                id = "t1", description = "Swiggy", amount = 320.0,
                duplicateWithId = "t2", duplicateStatus = "pending_review",
                duplicateConfidence = 95, duplicateReason = "Same day and amount"
            ),
            TransactionEntity(id = "t3", description = "No dup", amount = 1.0)
        )
        val restored = ExportEngine.parseJsonBackup(ExportEngine.generateJsonBackup("P", txns, emptyList(), emptyList()))

        assertEquals("t2", restored.transactions[0].duplicateWithId)
        assertEquals(95, restored.transactions[0].duplicateConfidence)
        assertEquals("Same day and amount", restored.transactions[0].duplicateReason)
        assertEquals("pending_review", restored.transactions[0].duplicateStatus)
        assertNull(restored.transactions[1].duplicateWithId)
    }

    @Test
    fun testRestoreRejectsNewerOrUnrecognisedBackupVersion() {
        val ok = """{"version":"${ExportEngine.BACKUP_VERSION}","transactions":[]}"""
        assertEquals(0, ExportEngine.parseJsonBackup(ok).transactions.size)
        assertEquals(0, ExportEngine.parseJsonBackup("""{"version":"1.1.0","transactions":[]}""").transactions.size)
        assertEquals(0, ExportEngine.parseJsonBackup("""{"transactions":[]}""").transactions.size)
        assertEquals(0, ExportEngine.parseJsonBackup("""{"version":"1.2.9","transactions":[]}""").transactions.size)

        val newerMinor = assertThrows(BackupFormatException::class.java) {
            ExportEngine.parseJsonBackup("""{"version":"1.3.0","transactions":[]}""")
        }
        assertTrue(newerMinor.message!!.contains("newer"))
        assertThrows(BackupFormatException::class.java) { ExportEngine.parseJsonBackup("""{"version":"2.0.0"}""") }
        assertThrows(BackupFormatException::class.java) { ExportEngine.parseJsonBackup("""{"version":"banana"}""") }
        assertThrows(BackupFormatException::class.java) { ExportEngine.parseJsonBackup("not json") }
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
            "Welcome: Mr. TEST USER",
            "Date of Statement : 25-08-2026",
            "Account open Date : 21/05/2019",
            "CIF Number : 12345678901",
            "Account Number : 98765432105",
            "REGULAR SB CHQ-INDIVIDUALS",
            "IFSC Code : SBIN0000001",
            "Statement From : 01-04-2025 to 31-03-2026",
            "STATEMENT OF ACCOUNT State Bank of India",
            "Branch Name : BRANCH",
            "Balance",
            "05/04/2025",
            "05/04/2025",
            "DEP TFR",
            "UPI/CR/200000000001/FRIEND ONE/HDFC/friend.one/PA",
            "0099999999001 AT 00001 BRANCH",
            "-",
            "-",
            "23,000.00",
            "1,82,287.01",
            "05/04/2025",
            "05/04/2025",
            "DEP TFR",
            "UPI/CR/200000000002/FRIEND T/KKBK/friend.one/PAY",
            "0099999999001 AT 00001 BRANCH",
            "-",
            "-",
            "20,000.00",
            "2,02,287.01",
            "06/04/2025",
            "06/04/2025",
            "WDL TFR",
            "UPI/DR/200000000003/FRIEND TWO /BKID/friend.two/UPI",
            "0099999999002 AT 00001 BRANCH",
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
            "UPI/DR/200000000004/DUMMY NAME/bkid/1234567890/UP",
            "0099999999003 AT 00001 BRANCH",
            "-",
            "33,000.00",
            "-",
            "1,59,287.01"
        )

        val fullText = sbiSampleLines.joinToString("\n")
        val meta = StatementParserEngine.extractAccountMetadata(fullText, "SBI_Statement.pdf")
        assertEquals("State Bank of India (SBI)", meta.bankName)
        assertEquals("Bank Account", meta.type)
        assertEquals("2105", meta.lastFour)

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

    // ==========================================
    // 9. TRANSACTION CREATION & EDITING TESTS
    // ==========================================

    @Test
    fun testTransactionCreationAndEditing() {
        val initialTimestamp = 1758110400000L // Specific epoch
        val originalTxn = TransactionEntity(
            date = initialTimestamp,
            description = "Blue Tokai Coffee",
            amount = 240.0,
            type = TransactionType.EXPENSE,
            category = "Food & Dining",
            accountName = "HDFC Bank",
            paymentMode = "UPI",
            note = "#coffee",
            referenceNo = "MANUAL_123456"
        )

        assertEquals(initialTimestamp, originalTxn.date)
        assertEquals(240.0, originalTxn.amount, 0.001)
        assertEquals("Blue Tokai Coffee", originalTxn.description)
        assertEquals("Food & Dining", originalTxn.category)

        // Simulate editing all details (date/time, amount, category, type, notes, paymentMode, etc.)
        val updatedTimestamp = 1758196800000L // 1 day later
        val editedTxn = originalTxn.copy(
            date = updatedTimestamp,
            description = "Blue Tokai Specialty Pour-Over",
            amount = 320.0,
            type = TransactionType.EXPENSE,
            category = "Food & Dining",
            accountName = "ICICI Credit Card",
            paymentMode = "Credit Card",
            note = "#coffee #pourover",
            referenceNo = "REF_987654",
            needsReview = false
        )

        assertEquals(updatedTimestamp, editedTxn.date)
        assertEquals(320.0, editedTxn.amount, 0.001)
        assertEquals("Blue Tokai Specialty Pour-Over", editedTxn.description)
        assertEquals("ICICI Credit Card", editedTxn.accountName)
        assertEquals("Credit Card", editedTxn.paymentMode)
        assertEquals("#coffee #pourover", editedTxn.note)
        assertEquals("REF_987654", editedTxn.referenceNo)
        assertFalse(editedTxn.needsReview)
    }

    // ==========================================
    // 11. CALENDAR AGGREGATION (production CalendarAggregator)
    // ==========================================

    private val utc = ZoneId.of("UTC")

    private fun epoch(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(utc).toInstant().toEpochMilli()

    private fun txn(
        date: Long,
        amount: Double,
        type: TransactionType,
        status: String = "none",
        id: String = UUID.randomUUID().toString()
    ) = TransactionEntity(id = id, date = date, description = "t", amount = amount, type = type, duplicateStatus = status)

    @Test
    fun testCalendarAggregateExcludesMergedAndTransfersFromTotals() {
        val txns = listOf(
            txn(epoch(2026, 9, 15), 50000.0, TransactionType.INCOME),
            txn(epoch(2026, 9, 15), 2500.0, TransactionType.EXPENSE),
            txn(epoch(2026, 9, 18), 1200.0, TransactionType.EXPENSE),
            txn(epoch(2026, 9, 18), 1200.0, TransactionType.EXPENSE, status = "merged"),
            txn(epoch(2026, 9, 18), 9000.0, TransactionType.TRANSFER),
            txn(epoch(2026, 8, 31), 777.0, TransactionType.EXPENSE)
        )

        val agg = CalendarAggregator.aggregate(txns, 2026, 9, utc)

        assertEquals(50000.0, agg.inflow, 0.001)
        assertEquals(3700.0, agg.outflow, 0.001)
        assertEquals(46300.0, agg.net, 0.001)
        assertEquals(setOf(15, 18), agg.days.keys)
        assertEquals(2, agg.days[15]!!.count)
        val day18 = agg.days[18]!!
        assertEquals(2, day18.count)
        assertEquals(1200.0, day18.expense, 0.001)
        assertEquals(0.0, day18.income, 0.001)
    }

    @Test
    fun testCalendarAggregateLeapDayAndMonthBoundaries() {
        val txns = listOf(
            txn(epoch(2024, 2, 29, 23, 59), 10.0, TransactionType.EXPENSE),
            txn(epoch(2024, 3, 1, 0, 0), 20.0, TransactionType.EXPENSE),
            txn(epoch(2024, 1, 31, 23, 59), 30.0, TransactionType.EXPENSE)
        )

        val feb = CalendarAggregator.aggregate(txns, 2024, 2, utc)
        assertEquals(setOf(29), feb.days.keys)
        assertEquals(10.0, feb.outflow, 0.001)
        assertEquals(LocalDate.of(2024, 2, 29), feb.days[29]!!.date)

        val nonLeapFeb = CalendarAggregator.aggregate(txns, 2023, 2, utc)
        assertTrue(nonLeapFeb.days.isEmpty())
    }

    @Test
    fun testCalendarAggregateDecemberToJanuaryRollover() {
        val txns = listOf(
            txn(epoch(2025, 12, 31, 23, 30), 100.0, TransactionType.EXPENSE),
            txn(epoch(2026, 1, 1, 0, 30), 200.0, TransactionType.INCOME)
        )

        val dec = CalendarAggregator.aggregate(txns, 2025, 12, utc)
        val jan = CalendarAggregator.aggregate(txns, 2026, 1, utc)

        assertEquals(100.0, dec.outflow, 0.001)
        assertEquals(0.0, dec.inflow, 0.001)
        assertEquals(setOf(31), dec.days.keys)
        assertEquals(200.0, jan.inflow, 0.001)
        assertEquals(setOf(1), jan.days.keys)
    }

    @Test
    fun testCalendarAggregateUsesLocalZoneForDayBucket() {
        val lateUtc = epoch(2026, 9, 18, 20, 0)
        val txns = listOf(txn(lateUtc, 5.0, TransactionType.EXPENSE))

        val ist = ZoneId.of("Asia/Kolkata")
        assertEquals(setOf(18), CalendarAggregator.aggregate(txns, 2026, 9, utc).days.keys)
        assertEquals(setOf(19), CalendarAggregator.aggregate(txns, 2026, 9, ist).days.keys)
    }

    @Test
    fun testCalendarSelectionClampsIntoNewMonth() {
        assertEquals(LocalDate.of(2024, 2, 29), CalendarAggregator.clampToMonth(LocalDate.of(2026, 1, 31), 2024, 2))
        assertEquals(LocalDate.of(2026, 2, 28), CalendarAggregator.clampToMonth(LocalDate.of(2026, 1, 31), 2026, 2))
        assertEquals(LocalDate.of(2026, 1, 15), CalendarAggregator.clampToMonth(LocalDate.of(2025, 12, 15), 2026, 1))
        assertEquals(LocalDate.of(2026, 5, 1), CalendarAggregator.clampToMonth(null, 2026, 5))
    }

    @Test
    fun testCalendarAddForDateKeepsCurrentTimeOfDay() {
        val now = epoch(2026, 9, 18, 14, 35)
        val millis = CalendarAggregator.dateWithCurrentTime(LocalDate.of(2026, 9, 3), now, utc)
        assertEquals(epoch(2026, 9, 3, 14, 35), millis)
    }

    @Test
    fun testCalendarLeadingBlankCellsAreSundayFirst() {
        assertEquals(2, CalendarAggregator.leadingBlankCells(2026, 9)) // Tuesday
        assertEquals(0, CalendarAggregator.leadingBlankCells(2026, 2)) // Sunday
        assertEquals(5, CalendarAggregator.leadingBlankCells(2025, 8)) // Friday
    }

    @Test
    fun testCalendarCompactAmountAndDayDescription() {
        assertEquals("500", CalendarAggregator.compactAmount(500.0))
        assertEquals("1.2k", CalendarAggregator.compactAmount(1234.0))
        assertEquals("2k", CalendarAggregator.compactAmount(2000.0))
        assertEquals("1.5L", CalendarAggregator.compactAmount(150000.0))
        assertEquals("1.2Cr", CalendarAggregator.compactAmount(12000000.0))

        val txns = listOf(
            txn(epoch(2026, 9, 18), 100.0, TransactionType.EXPENSE),
            txn(epoch(2026, 9, 18), 300.0, TransactionType.EXPENSE),
            txn(epoch(2026, 9, 18), 500.0, TransactionType.INCOME)
        )
        val summary = CalendarAggregator.aggregate(txns, 2026, 9, utc).days[18]
        assertEquals(
            "18 September, 2 expenses, 1 income, selected",
            CalendarAggregator.describeDay(LocalDate.of(2026, 9, 18), summary, isToday = false, isSelected = true, locale = Locale.ENGLISH)
        )
        assertEquals(
            "3 September, today, no transactions",
            CalendarAggregator.describeDay(LocalDate.of(2026, 9, 3), null, isToday = true, isSelected = false, locale = Locale.ENGLISH)
        )
    }

    // ==========================================
    // 12. BACKUP REMINDER POLICY (production BackupReminderPolicy)
    // ==========================================

    @Test
    fun testBackupReminderPolicyThresholds() {
        val now = 1758200000000L
        val day = 24L * 60 * 60 * 1000L
        val oldBackup = now - 31 * day

        assertFalse(BackupReminderPolicy.shouldShow(0, 0L, 0L, now))
        assertTrue(BackupReminderPolicy.shouldShow(5, 0L, 0L, now))
        assertFalse(BackupReminderPolicy.shouldShow(5, now - 5 * day, 0L, now))
        assertTrue(BackupReminderPolicy.shouldShow(5, oldBackup, 0L, now))
        assertFalse(BackupReminderPolicy.shouldShow(5, oldBackup, now + BackupReminderPolicy.SNOOZE_MS, now))
        assertTrue(BackupReminderPolicy.shouldShow(5, oldBackup, now + BackupReminderPolicy.SNOOZE_MS, now + 8 * day))
        assertFalse(BackupReminderPolicy.shouldShow(5, now - 30 * day, 0L, now))
    }

    // ==========================================
    // 13. STATEMENT DATE PARSING
    // ==========================================

    private val parseNow = epoch(2026, 9, 18)

    private fun ymd(millis: Long?): Triple<Int, Int, Int>? {
        if (millis == null) return null
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testParseDateRealStatementFormats() {
        assertEquals(Triple(2025, 1, 5), ymd(StatementParserEngine.parseDate("05-01-2025", parseNow)))
        assertEquals(Triple(2025, 1, 5), ymd(StatementParserEngine.parseDate("05/01/25", parseNow)))
        assertEquals(Triple(2025, 1, 5), ymd(StatementParserEngine.parseDate("05/01/2025", parseNow)))
        assertEquals(Triple(2024, 1, 15), ymd(StatementParserEngine.parseDate("15 Jan 2024", parseNow)))
        assertEquals(Triple(2024, 1, 15), ymd(StatementParserEngine.parseDate("15 January 2024", parseNow)))
        assertEquals(Triple(2024, 1, 15), ymd(StatementParserEngine.parseDate("2024-01-15", parseNow)))
        assertEquals(Triple(2024, 1, 15), ymd(StatementParserEngine.parseDate("2024-01-15 10:30:00", parseNow)))
        assertEquals(Triple(2024, 1, 15), ymd(StatementParserEngine.parseDate("Jan 15, 2024", parseNow)))
        assertEquals(Triple(2024, 1, 15), ymd(StatementParserEngine.parseDate("15-Jan-2024", parseNow)))
    }

    @Test
    fun testParseDateMonthFirstOnlyWhenDayFirstIsImpossible() {
        // 15 can't be a month, so 01/15/2025 is unambiguously US order
        assertEquals(Triple(2025, 1, 15), ymd(StatementParserEngine.parseDate("01/15/2025", parseNow)))
        assertEquals(Triple(2025, 1, 15), ymd(StatementParserEngine.parseDate("01-15-2025", parseNow)))
        assertEquals(Triple(2025, 3, 25), ymd(StatementParserEngine.parseDate("03/25/25", parseNow)))
        // ambiguous stays day-first (Indian convention): 5 June, never 6 May
        assertEquals(Triple(2025, 6, 5), ymd(StatementParserEngine.parseDate("05/06/2025", parseNow)))
        // impossible either way stays unreadable (flagged), never guessed
        assertNull(StatementParserEngine.parseDate("13/13/2025", parseNow))
        assertNull(StatementParserEngine.parseDate("02/30/2025", parseNow))
    }

    @Test
    fun testAccountDetectionSbiBankStatementIsBankAccountKeyedByAccountNumber() {
        // pdf text lists header labels first, values after: CIF number precedes the account number
        val text = "STATEMENT OF ACCOUNT State Bank of India CIF Number : Account Number : Account Status : " +
            "12345678901 98765432105 OPEN INR 05/04/2025 DEP TFR UPI/CR/1/x/paytm/gpay 06/04/2025 WDL TFR UPI/DR/2/y"
        val meta = StatementParserEngine.extractAccountMetadata(text, "statement.pdf")
        assertEquals("Bank Account", meta.type)
        assertEquals("2105", meta.lastFour)
    }

    @Test
    fun testAccountDetectionNaviIsWalletNotSbi() {
        val meta = StatementParserEngine.extractAccountMetadata(
            "Transaction statement from 20 May 2026 Paid via Navi UPI State Bank of India - 2105", "Navi_Statement.pdf"
        )
        assertEquals("Navi UPI", meta.bankName)
        assertEquals("Digital Wallet", meta.type)
        assertEquals("Navi UPI Wallet", meta.name)
    }

    @Test
    fun testParseDateRejectsInvalidAndOutOfRange() {
        assertNull(StatementParserEngine.parseDate("31/02/2024", parseNow))
        assertNull(StatementParserEngine.parseDate("32/01/2024", parseNow))
        assertNull(StatementParserEngine.parseDate("", parseNow))
        assertNull(StatementParserEngine.parseDate("not a date", parseNow))
        assertNull(StatementParserEngine.parseDate("01/01/1980", parseNow))
        assertNull(StatementParserEngine.parseDate("01/01/2099", parseNow))
        assertNull(StatementParserEngine.parseDate("15/01/2024 garbage", parseNow))
    }

    @Test
    fun testCsvRowWithUnreadableDateIsFlaggedNotSilentlyDatedToday() {
        val lines = listOf(
            "Date,Description,Amount,Type",
            "31/02/2024,Zomato Food Order,650.00,Debit",
            "2024-01-15,Zomato Food Order,100.00,Debit"
        )
        val result = StatementParserEngine.parseCsvLines(lines, "bad_dates.csv")

        assertEquals(2, result.transactions.size)
        val bad = result.transactions[0]
        assertTrue(bad.needsReview)
        assertEquals("low", bad.confidence)
        assertTrue(bad.note.contains("31/02/2024"))
        val good = result.transactions[1]
        assertEquals(Triple(2024, 1, 15), ymd(good.date))
        assertTrue(good.note.isEmpty())
    }

    // ==========================================
    // 14. KPI WINDOWS (production KpiMath)
    // ==========================================

    @Test
    fun testKpiWindowUsesLocalCalendarDays() {
        val today = LocalDate.of(2026, 9, 18)
        val txns = listOf(
            txn(epoch(2026, 8, 20, 0, 0), 100.0, TransactionType.EXPENSE), // first day of the 30D window
            txn(epoch(2026, 8, 19, 23, 59), 999.0, TransactionType.EXPENSE), // just outside
            txn(epoch(2026, 9, 18, 9, 0), 5000.0, TransactionType.INCOME),
            txn(epoch(2026, 9, 10, 9, 0), 50.0, TransactionType.EXPENSE, status = "merged"),
            txn(epoch(2026, 9, 10, 9, 0), 70.0, TransactionType.TRANSFER)
        )

        val t30 = KpiMath.totals(txns, KpiRange.D30, today, utc)
        assertEquals(5000.0, t30.inflow, 0.001)
        assertEquals(100.0, t30.outflow, 0.001)
        assertEquals(98.0, t30.savingsRatePercent, 0.001)

        val t7 = KpiMath.totals(txns, KpiRange.D7, today, utc)
        assertEquals(0.0, t7.outflow, 0.001)

        val all = KpiMath.totals(txns, KpiRange.ALL, today, utc)
        assertEquals(1099.0, all.outflow, 0.001)
    }

    @Test
    fun testKpiWindowMovesAtMidnight() {
        val txns = listOf(txn(epoch(2026, 8, 20, 6, 0), 100.0, TransactionType.EXPENSE))
        assertEquals(100.0, KpiMath.totals(txns, KpiRange.D30, LocalDate.of(2026, 9, 18), utc).outflow, 0.001)
        assertEquals(0.0, KpiMath.totals(txns, KpiRange.D30, LocalDate.of(2026, 9, 19), utc).outflow, 0.001)
    }

    // ==========================================
    // PER-ROW ACCOUNTS (UPI histories mixing bank, RuPay card and wallet rows)
    // ==========================================

    @Test
    fun testNaviRowAccountRupayCardInEveryGlueOrder() {
        val txnLine = "12:56 PM UPI txn ID: 100000000002"
        // account column split: bank part glued to the payee, card part on the txn-ID line, on its own line, or glued to the payee too
        val variants = listOf(
            "SOME SHOP HDFC Bank RuPay" to listOf("$txnLine Credit Card - XX99", "Note: Paid via Navi UPI"),
            "SOME SHOP HDFC Bank RuPay" to listOf(txnLine, "Credit Card - XX99", "Note: Paid via Navi UPI"),
            "SOME SHOP HDFC Bank RuPay Credit Card - XX99" to listOf(txnLine)
        )
        for ((payee, follow) in variants) {
            val r = StatementParserEngine.splitNaviAccount(payee, follow)
            assertEquals("SOME SHOP", r.payee)
            assertEquals("Credit Card", r.account?.type)
            assertTrue(r.account?.isRuPay == true)
            assertEquals("HDFC Bank", r.account?.bankName)
        }
    }

    @Test
    fun testNaviRowAccountBankKeyedByLast4AndSbiNormalised() {
        val txnLine = "1:05 PM UPI txn ID: 100000000001"
        val sameLine = StatementParserEngine.splitNaviAccount("A FRIEND State Bank of India", listOf("$txnLine - 1234"))
        val ownLine = StatementParserEngine.splitNaviAccount("A FRIEND State Bank of India", listOf(txnLine, "- 1234", "Note: x"))
        val bankOnFollowUp = StatementParserEngine.splitNaviAccount("A FRIEND", listOf("State Bank of India - 1234"))
        for (r in listOf(sameLine, ownLine, bankOnFollowUp)) {
            assertEquals("A FRIEND", r.payee)
            assertEquals("Bank Account", r.account?.type)
            assertEquals("1234", r.account?.lastFour)
            assertEquals("State Bank of India (SBI)", r.account?.bankName)
            assertFalse(r.account?.isRuPay == true)
        }
    }

    @Test
    fun testNaviRowAccountMissingOrUntrustedTextNeverBreaksTheRow() {
        val none = StatementParserEngine.splitNaviAccount("SOME SHOP", listOf("12:56 PM UPI txn ID: 100000000004"))
        assertEquals("SOME SHOP", none.payee)
        assertNull(none.account)
        // a trailing "... Bank" with no "- 1234" beside it may be part of the payee's own name
        val plain = StatementParserEngine.splitNaviAccount("SOME SHOP Sunrise Bank", listOf("12:56 PM UPI txn ID: 100000000004"))
        assertEquals("SOME SHOP Sunrise Bank", plain.payee)
        assertNull(plain.account)
        // bank column present but the last4 fragment lost: payee is cleaned, no account guessed
        val noLast4 = StatementParserEngine.splitNaviAccount("A FRIEND State Bank of India", listOf("12:56 PM UPI txn ID: 100000000004"))
        assertEquals("A FRIEND", noLast4.payee)
        assertNull(noLast4.account)
    }

    @Test
    fun testNaviBillPaymentOfCardIsBankRowTransferAndNeverACardAccount() {
        val lines = listOf(
            "Date Transaction details Account Amount",
            "12 May 2026 Paid to SOME SHOP HDFC Bank RuPay 75",
            "12:56 PM UPI txn ID: 100000000002 Credit Card - XX99",
            "Note: Paid via Navi UPI",
            "13 May 2026 Paid to A FRIEND State Bank of India 120",
            "1:05 PM UPI txn ID: 100000000001",
            "- 1234",
            "Note: Paid via Navi UPI",
            "14 May 2026 Bill payment of HDFC Credit Card State Bank of India 9,999",
            "9:00 AM UPI txn ID: 100000000003 - 1234",
            "Note: UPI",
            "15 May 2026 Paid to SOME SHOP 30",
            "3:00 PM UPI txn ID: 100000000004"
        )
        val res = StatementParserEngine.parseNaviPdf(lines, "navi.pdf", "", "", emptyList())
        assertEquals(4, res.transactions.size)
        assertEquals(4, res.rowAccounts.size)

        val card = res.transactions[0]
        assertTrue(res.rowAccounts[0]?.isRuPay == true)
        assertEquals("Paid to SOME SHOP — Paid via Navi UPI", card.rawNarration)
        assertEquals("UPI (RuPay Credit Card)", card.paymentMode)
        assertEquals("100000000002", card.referenceNo)

        assertEquals("1234", res.rowAccounts[1]?.lastFour)
        assertEquals("Paid to A FRIEND — Paid via Navi UPI", res.transactions[1].rawNarration)

        val bill = res.transactions[2]
        assertEquals(TransactionType.TRANSFER, bill.type)
        assertEquals(9999.0, bill.amount, 0.001)
        assertEquals("Bill payment of HDFC Credit Card — UPI", bill.rawNarration)
        assertEquals("Bank Account", res.rowAccounts[2]?.type)
        assertEquals("1234", res.rowAccounts[2]?.lastFour)
        assertEquals("UPI", bill.paymentMode)

        assertNull(res.rowAccounts[3]) // falls back to the file-level Navi wallet
    }

    @Test
    fun testRowAccountForPrefersHintAndNeverInventsCardForBillPayments() {
        fun t(narr: String, type: TransactionType = TransactionType.EXPENSE) =
            TransactionEntity(description = "t", amount = 1.0, type = type, rawNarration = narr)
        val bank = StatementParserEngine.accountHintFromInstrument("State Bank of India - 1234")
        assertEquals(bank, StatementParserEngine.rowAccountFor(t("Paid to SOME SHOP — Paid via Navi UPI"), bank))
        // no hint: the old narration detection still finds a RuPay card
        assertTrue(StatementParserEngine.rowAccountFor(t("Paid to SOME SHOP HDFC Bank RuPay Credit Card - XX99"), null)?.isRuPay == true)
        // a payment OF a card, even one that mentions UPI, is not a purchase made with one
        assertNull(StatementParserEngine.rowAccountFor(t("Bill payment of HDFC Credit Card — UPI"), null))
        assertNull(StatementParserEngine.rowAccountFor(t("Paid to SOME SHOP via UPI credit card", TransactionType.TRANSFER), null))
        assertNull(StatementParserEngine.rowAccountFor(t("Paid to SOME SHOP"), null))
    }

    @Test
    fun testAccountHintFromInstrumentShapes() {
        val masked = StatementParserEngine.accountHintFromInstrument("Paid by XXXXXXXX3863")!!
        assertEquals("Bank Account", masked.type)
        assertEquals("3863", masked.lastFour)
        assertEquals("Bank Account (•••• 3863)", masked.name)

        val hdfc = StatementParserEngine.accountHintFromInstrument("Paid by HDFC Bank 4321")!!
        assertEquals("HDFC Bank", hdfc.bankName)
        assertEquals("4321", hdfc.lastFour)
        assertEquals("HDFC Bank Account (•••• 4321)", hdfc.name)
        assertEquals("4321", StatementParserEngine.accountHintFromInstrument("Paid by Axis Bank A/c XX4321")?.lastFour)

        val card = StatementParserEngine.accountHintFromInstrument("Paid by HDFC Bank Credit Card XX4321")!!
        assertEquals("Credit Card", card.type)
        assertEquals("4321", card.lastFour)

        // free text that is not an instrument yields nothing rather than a guess
        assertNull(StatementParserEngine.accountHintFromInstrument("Paid by SOME SHOP 1234"))
        assertNull(StatementParserEngine.accountHintFromInstrument(""))
        assertEquals("1234", StatementParserEngine.bankAccountInText("Paid to SOME SHOP State Bank Of India - 1234")?.lastFour)
        assertNull(StatementParserEngine.bankAccountInText("Paid to SOME SHOP"))
    }

    @Test
    fun testPhonePeRowsCarryPaidByAccountAndWholeRupeeAmounts() {
        val lines = listOf(
            "Date Transaction Details Type Amount",
            "Apr 02, 2025 Paid to SOME SHOP DEBIT ₹185",
            "06:57 PM Transaction ID T2504021234567890123456",
            "UTR No: 100000000001",
            "Paid by XXXXXXXX3863",
            "Apr 03, 2025 Received from A FRIEND CREDIT ₹1,500",
            "08:00 AM Transaction ID T2504031234567890123456",
            "Apr 04, 2025 Paid to SOME SHOP DEBIT ₹50"
        )
        val res = StatementParserEngine.parsePhonePePdf(lines, "phonepe.pdf", "", "", emptyList())
        assertEquals(listOf(185.0, 1500.0, 50.0), res.transactions.map { it.amount })
        assertEquals(TransactionType.INCOME, res.transactions[1].type)
        assertEquals("100000000001", res.transactions[0].referenceNo)
        assertEquals("Paid to SOME SHOP", res.transactions[0].rawNarration)
        assertEquals("3863", res.rowAccounts[0]?.lastFour)
        assertNull(res.rowAccounts[1])
        assertNull(res.rowAccounts[2])
    }

    @Test
    fun testGooglePayRowsWithSplitDatesAndPaidByBank() {
        val lines = listOf(
            "Transaction statement",
            "01 Jan, 2025 Paid to SOME SHOP ₹1,234",
            "10:30 AM UPI Transaction ID: 100000000001",
            "Paid by State Bank of India 1234",
            "03 Jan,",
            "2025 Received from A FRIEND ₹500",
            "UPI Transaction ID: 100000000002",
            "Paid by XXXXXXXX3863",
            "05 Jan, 2025",
            "Paid to SOME SHOP",
            "UPI Transaction ID: 100000000003",
            "₹99.50"
        )
        val res = StatementParserEngine.parseGooglePayLines(lines, "gpay.pdf")
        assertEquals(3, res.transactions.size)
        assertEquals(listOf(1234.0, 500.0, 99.5), res.transactions.map { it.amount })
        assertEquals(TransactionType.INCOME, res.transactions[1].type)
        assertEquals("100000000001", res.transactions[0].referenceNo)
        assertTrue(res.transactions.all { it.note.isEmpty() }) // year on its own line still dated the row
        assertEquals("1234", res.rowAccounts[0]?.lastFour)
        assertEquals("State Bank of India (SBI)", res.rowAccounts[0]?.bankName)
        assertEquals("3863", res.rowAccounts[1]?.lastFour)
        assertNull(res.rowAccounts[2])
        assertEquals(500.0, res.totalInflow, 0.001)
        assertEquals(1333.5, res.totalOutflow, 0.001)
    }
}
