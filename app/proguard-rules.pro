# Security & Stability Rules for Cash Memo

# 1. Keep Room database and DAO classes
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *
-keep @androidx.room.Entity class *

# 2. Keep Domain Models for Serialization
# This prevents R8 from renaming fields in your financial models
-keep @kotlinx.serialization.Serializable class com.example.cashmemo.data.model.** { *; }

# 3. Keep specific models used in the app
-keep class com.example.cashmemo.data.model.Account { *; }
-keep class com.example.cashmemo.data.model.Transaction { *; }
-keep class com.example.cashmemo.data.model.Borrower { *; }
-keep class com.example.cashmemo.data.model.Investment { *; }
-keep class com.example.cashmemo.data.model.Debt { *; }
-keep class com.example.cashmemo.data.model.Payment { *; }
-keep class com.example.cashmemo.data.model.FinancialSummary { *; }

# 4. General Kotlin Serialization rules
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keepclassmembers class com.example.cashmemo.** {
    *** Companion;
}
-keepnames class kotlinx.serialization.json.Json { *; }

# 5. Security: Remove log statements in production
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
