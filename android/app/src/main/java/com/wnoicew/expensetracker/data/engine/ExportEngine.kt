package com.wnoicew.expensetracker.data.engine

import com.wnoicew.expensetracker.data.model.AccountEntity
import com.wnoicew.expensetracker.data.model.BudgetEntity
import com.wnoicew.expensetracker.data.model.RuleEntity
import com.wnoicew.expensetracker.data.model.TransactionEntity
import com.wnoicew.expensetracker.data.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object ExportEngine {

    data class BackupData(
        val transactions: List<TransactionEntity>,
        val accounts: List<AccountEntity>,
        val budgets: List<BudgetEntity>,
        val rules: List<RuleEntity>
    )

    fun generateCsv(
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("Date,Type,Category,Description,Amount,Account,PaymentMode,ReferenceNo,SourceFile,Notes\n")

        for (t in transactions) {
            val dateStr = dateFormat.format(Date(t.date))
            val accName = t.accountName.ifBlank {
                accounts.find { it.id == t.accountId }?.name ?: "Main Account"
            }
            val escapedDesc = "\"" + t.description.replace("\"", "\"\"") + "\""
            val escapedNote = "\"" + t.note.replace("\"", "\"\"") + "\""

            sb.append("${dateStr},${t.type.name},${t.category},${escapedDesc},${t.amount},\"${accName}\",${t.paymentMode},\"${t.referenceNo}\",\"${t.sourceFile}\",${escapedNote}\n")
        }

        return sb.toString()
    }

    fun generateJsonBackup(
        profileName: String,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        budgets: List<BudgetEntity>,
        rules: List<RuleEntity>
    ): String {
        val root = JSONObject()
        root.put("version", "1.1.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("profileName", profileName)

        // Transactions
        val txnArray = JSONArray()
        for (t in transactions) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("date", t.date)
            obj.put("description", t.description)
            obj.put("amount", t.amount)
            obj.put("type", t.type.name)
            obj.put("category", t.category)
            obj.put("accountId", t.accountId)
            obj.put("accountName", t.accountName)
            obj.put("note", t.note)
            obj.put("referenceNo", t.referenceNo)
            obj.put("paymentMode", t.paymentMode)
            obj.put("sourceFile", t.sourceFile)
            obj.put("rawNarration", t.rawNarration)
            obj.put("needsReview", t.needsReview)
            txnArray.put(obj)
        }
        root.put("transactions", txnArray)

        // Accounts
        val accArray = JSONArray()
        for (a in accounts) {
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("name", a.name)
            obj.put("type", a.type)
            obj.put("balance", a.balance)
            obj.put("creditLimit", a.creditLimit)
            obj.put("gradientIndex", a.gradientIndex)
            obj.put("lastFour", a.lastFour)
            obj.put("bankName", a.bankName)
            accArray.put(obj)
        }
        root.put("accounts", accArray)

        // Budgets
        val budgetArray = JSONArray()
        for (b in budgets) {
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("categoryName", b.categoryName)
            obj.put("monthlyBudget", b.monthlyBudget)
            budgetArray.put(obj)
        }
        root.put("budgets", budgetArray)

        // Rules
        val ruleArray = JSONArray()
        for (r in rules) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("pattern", r.pattern)
            obj.put("category", r.category)
            obj.put("type", r.type.name)
            ruleArray.put(obj)
        }
        root.put("rules", ruleArray)

        return root.toString(2)
    }

    fun parseJsonBackup(jsonStr: String): BackupData {
        val root = JSONObject(jsonStr)

        val txns = mutableListOf<TransactionEntity>()
        val txnArray = root.optJSONArray("transactions")
        if (txnArray != null) {
            for (i in 0 until txnArray.length()) {
                val obj = txnArray.getJSONObject(i)
                txns.add(
                    TransactionEntity(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        description = obj.optString("description", "Entry"),
                        amount = obj.optDouble("amount", 0.0),
                        type = try { TransactionType.valueOf(obj.optString("type", "EXPENSE")) } catch (_: Exception) { TransactionType.EXPENSE },
                        category = obj.optString("category", "Uncategorized"),
                        accountId = obj.optString("accountId", ""),
                        accountName = obj.optString("accountName", ""),
                        note = obj.optString("note", ""),
                        referenceNo = obj.optString("referenceNo", ""),
                        paymentMode = obj.optString("paymentMode", "Online"),
                        sourceFile = obj.optString("sourceFile", "Backup Restore"),
                        rawNarration = obj.optString("rawNarration", ""),
                        needsReview = obj.optBoolean("needsReview", false)
                    )
                )
            }
        }

        val accs = mutableListOf<AccountEntity>()
        val accArray = root.optJSONArray("accounts")
        if (accArray != null) {
            for (i in 0 until accArray.length()) {
                val obj = accArray.getJSONObject(i)
                accs.add(
                    AccountEntity(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Account"),
                        type = obj.optString("type", "Bank Account"),
                        balance = obj.optDouble("balance", 0.0),
                        creditLimit = obj.optDouble("creditLimit", 0.0),
                        gradientIndex = obj.optInt("gradientIndex", 0),
                        lastFour = obj.optString("lastFour", ""),
                        bankName = obj.optString("bankName", "")
                    )
                )
            }
        }

        val budgets = mutableListOf<BudgetEntity>()
        val budgetArray = root.optJSONArray("budgets")
        if (budgetArray != null) {
            for (i in 0 until budgetArray.length()) {
                val obj = budgetArray.getJSONObject(i)
                budgets.add(
                    BudgetEntity(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        categoryName = obj.optString("categoryName", "General"),
                        monthlyBudget = obj.optDouble("monthlyBudget", 5000.0)
                    )
                )
            }
        }

        val rules = mutableListOf<RuleEntity>()
        val ruleArray = root.optJSONArray("rules")
        if (ruleArray != null) {
            for (i in 0 until ruleArray.length()) {
                val obj = ruleArray.getJSONObject(i)
                rules.add(
                    RuleEntity(
                        id = obj.optString("id", "rule_" + System.currentTimeMillis()),
                        pattern = obj.optString("pattern", ""),
                        category = obj.optString("category", "General"),
                        type = try { TransactionType.valueOf(obj.optString("type", "EXPENSE")) } catch (_: Exception) { TransactionType.EXPENSE }
                    )
                )
            }
        }

        return BackupData(txns, accs, budgets, rules)
    }
}
