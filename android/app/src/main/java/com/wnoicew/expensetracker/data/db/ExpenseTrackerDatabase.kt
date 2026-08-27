package com.wnoicew.expensetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wnoicew.expensetracker.data.model.AccountEntity
import com.wnoicew.expensetracker.data.model.BudgetEntity
import com.wnoicew.expensetracker.data.model.RuleEntity
import com.wnoicew.expensetracker.data.model.TransactionEntity
import java.util.concurrent.ConcurrentHashMap

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        RuleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun ruleDao(): RuleDao

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
