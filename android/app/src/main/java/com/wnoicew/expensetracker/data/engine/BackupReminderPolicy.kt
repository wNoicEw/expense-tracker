package com.wnoicew.expensetracker.data.engine

object BackupReminderPolicy {
    const val REMINDER_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000
    const val SNOOZE_MS = 7L * 24 * 60 * 60 * 1000

    fun shouldShow(txnCount: Int, lastBackupAt: Long, snoozedUntil: Long, now: Long): Boolean {
        if (txnCount <= 0) return false
        if (now < snoozedUntil) return false
        return (now - lastBackupAt) > REMINDER_INTERVAL_MS
    }
}
