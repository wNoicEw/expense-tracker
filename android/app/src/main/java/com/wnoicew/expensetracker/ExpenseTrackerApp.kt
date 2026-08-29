package com.wnoicew.expensetracker

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            PDFBoxResourceLoader.init(this)
        } catch (_: Exception) {}
    }
}
