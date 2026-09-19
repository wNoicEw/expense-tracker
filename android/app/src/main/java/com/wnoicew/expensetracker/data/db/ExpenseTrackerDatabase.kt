package com.wnoicew.expensetracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 4,
    exportSchema = true
)
abstract class ExpenseTrackerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun ruleDao(): RuleDao
    abstract fun statementUploadDao(): StatementUploadDao

    companion object {
        private val instances = ConcurrentHashMap<String, ExpenseTrackerDatabase>()

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
                db.execSQL("ALTER TABLE accounts ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
            }
        }

        fun closeAndForget(profileId: String) {
            instances.remove(profileId)?.close()
        }

        fun getDatabase(context: Context, profileId: String): ExpenseTrackerDatabase {
            return instances.computeIfAbsent(profileId) { id ->
                Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseTrackerDatabase::class.java,
                    "ExpenseTrackerDB_$id"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build()
            }
        }
    }
}
