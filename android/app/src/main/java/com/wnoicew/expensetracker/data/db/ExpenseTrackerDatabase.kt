package com.wnoicew.expensetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wnoicew.expensetracker.data.model.AccountEntity
import com.wnoicew.expensetracker.data.model.RuleEntity
import com.wnoicew.expensetracker.data.model.StatementUploadEntity
import com.wnoicew.expensetracker.data.model.TransactionEntity
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        RuleEntity::class,
        StatementUploadEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun ruleDao(): RuleDao
    abstract fun statementUploadDao(): StatementUploadDao

    companion object {
        private val instances = ConcurrentHashMap<String, ExpenseTrackerDatabase>()

        fun getDatabase(context: Context, profileId: String): ExpenseTrackerDatabase {
            return instances.computeIfAbsent(profileId) { id ->
                Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseTrackerDatabase::class.java,
                    "ExpenseTrackerDB_$id"
                )
                .fallbackToDestructiveMigration()
                .build()
            }
        }
    }
}
