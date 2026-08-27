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

        val duplicates = DuplicateDetectorEngine.scanDuplicates(listOf(t1, t2))
        assertEquals(1, duplicates.size)
        assertEquals(99, duplicates[0].confidence)

        val (merged, neutralized) = DuplicateDetectorEngine.mergeTransactions(t1, t2)
        assertEquals("merged_primary", merged.duplicateStatus)
        assertEquals("merged", neutralized.duplicateStatus)
        assertEquals(TransactionType.TRANSFER, neutralized.type)
    }

    @Test
    fun testDuplicateDetectorTimeProximityFuzzyMatch() {
        val now = System.currentTimeMillis()
        val t1 = TransactionEntity(
            id = "t1",
            date = now,
            description = "Swiggy Order Food",
            amount = 320.0,
            sourceFile = "PhonePe.csv"
        )
        val t2 = TransactionEntity(
            id = "t2",
            date = now + (2 * 60 * 60 * 1000), // 2 hours later
            description = "SWIGGY BANGALORE",
            amount = 320.0,
            sourceFile = "ICICI_Statement.csv"
        )

        val duplicates = DuplicateDetectorEngine.scanDuplicates(listOf(t1, t2))
        assertEquals(1, duplicates.size)
        assertEquals(90, duplicates[0].confidence)

        val (kept1, kept2) = DuplicateDetectorEngine.markSeparate(t1, t2)
        assertEquals("dismissed", kept1.duplicateStatus)
        assertEquals("dismissed", kept2.duplicateStatus)
    }

    @Test
    fun testDuplicateDetectorNonMatchingAmountsIgnored() {
        val t1 = TransactionEntity(id = "t1", date = System.currentTimeMillis(), description = "Uber", amount = 250.0)
        val t2 = TransactionEntity(id = "t2", date = System.currentTimeMillis(), description = "Uber", amount = 450.0)

        val duplicates = DuplicateDetectorEngine.scanDuplicates(listOf(t1, t2))
        assertTrue(duplicates.isEmpty())
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
        val budgets = listOf(
            BudgetEntity(id = "b1", categoryName = "Food & Dining", monthlyBudget = 8000.0)
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
        val jsonString = ExportEngine.generateJsonBackup("Personal Profile", txns, accs, budgets, rules)
        assertTrue(jsonString.contains("Personal Profile"))
        assertTrue(jsonString.contains("transactions"))

        val restored = ExportEngine.parseJsonBackup(jsonString)
        assertEquals(1, restored.transactions.size)
        assertEquals(1, restored.accounts.size)
        assertEquals(1, restored.budgets.size)
        assertEquals(1, restored.rules.size)
        assertEquals("Netflix", restored.transactions[0].description)
        assertEquals("HDFC Bank", restored.accounts[0].name)
    }
}
