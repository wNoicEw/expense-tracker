package com.wnoicew.expensetracker.data.engine

import com.wnoicew.expensetracker.data.model.AccountEntity
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
        rules: List<RuleEntity>
    ): String {
        val root = JSONObject()
        root.put("version", "1.1.0")
        root.put("profileName", profileName)
        root.put("exportDate", System.currentTimeMillis())

        val txnsArray = JSONArray()
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
            obj.put("isDuplicate", t.isDuplicate)
            obj.put("duplicateStatus", t.duplicateStatus)
            obj.put("needsReview", t.needsReview)
            obj.put("confidence", t.confidence)
            txnsArray.put(obj)
        }
        root.put("transactions", txnsArray)

        val accsArray = JSONArray()
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
            accsArray.put(obj)
        }
        root.put("accounts", accsArray)

        val rulesArray = JSONArray()
        for (r in rules) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("pattern", r.pattern)
            obj.put("category", r.category)
            obj.put("type", r.type.name)
            obj.put("createdAt", r.createdAt)
            rulesArray.put(obj)
        }
        root.put("rules", rulesArray)

        return root.toString(2)
    }

    fun parseJsonBackup(jsonStr: String): BackupData {
        val root = JSONObject(jsonStr)
        val txnsList = mutableListOf<TransactionEntity>()
        val accsList = mutableListOf<AccountEntity>()
        val rulesList = mutableListOf<RuleEntity>()

        if (root.has("transactions")) {
            val array = root.getJSONArray("transactions")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                txnsList.add(
                    TransactionEntity(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        date = obj.optLong("date", System.currentTimeMillis()),
                        description = obj.optString("description", "Imported Transaction"),
                        amount = obj.optDouble("amount", 0.0),
                        type = try { TransactionType.valueOf(obj.optString("type", "EXPENSE")) } catch (e: Exception) { TransactionType.EXPENSE },
                        category = obj.optString("category", "Uncategorized"),
                        accountId = obj.optString("accountId", ""),
                        accountName = obj.optString("accountName", ""),
                        note = obj.optString("note", ""),
                        referenceNo = obj.optString("referenceNo", ""),
                        paymentMode = obj.optString("paymentMode", "Online"),
                        sourceFile = obj.optString("sourceFile", "Backup Restore"),
                        rawNarration = obj.optString("rawNarration", ""),
                        isDuplicate = obj.optBoolean("isDuplicate", false),
                        duplicateStatus = obj.optString("duplicateStatus", "none"),
                        needsReview = obj.optBoolean("needsReview", false),
                        confidence = obj.optString("confidence", "high")
                    )
                )
            }
        }

        if (root.has("accounts")) {
            val array = root.getJSONArray("accounts")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                accsList.add(
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

        if (root.has("rules")) {
            val array = root.getJSONArray("rules")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                rulesList.add(
                    RuleEntity(
                        id = obj.optString("id", "rule_" + System.currentTimeMillis()),
                        pattern = obj.optString("pattern", ""),
                        category = obj.optString("category", "Uncategorized"),
                        type = try { TransactionType.valueOf(obj.optString("type", "EXPENSE")) } catch (e: Exception) { TransactionType.EXPENSE },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        return BackupData(txnsList, accsList, rulesList)
    }
}
