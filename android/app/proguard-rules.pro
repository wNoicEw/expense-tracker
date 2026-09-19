# Proguard / R8 Keep Rules for Money Tracker

# Room Database & Entities
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.wnoicew.expensetracker.data.model.** { *; }
-keep class com.wnoicew.expensetracker.data.db.** { *; }

# PDFBox Android (Tom Roussel)
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.fontbox.**

# Apache Commons CSV
-keep class org.apache.commons.csv.** { *; }
-dontwarn org.apache.commons.csv.**

# Gson & JSON Serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# Kotlin Coroutines & Flow
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-dontwarn kotlinx.coroutines.**

# Compose
-keep class androidx.compose.runtime.** { *; }
