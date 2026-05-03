# Cash Memo — ProGuard / R8 Rules (Production)

# ── Room ─────────────────────────────────────────────────────────────────────
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(...); }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *
-keep @androidx.room.Entity class *
-keep @androidx.room.TypeConverters class *

# ── Kotlin Serialization (all domain models) ─────────────────────────────────
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations
-keep @kotlinx.serialization.Serializable class com.example.cashmemo.data.model.** { *; }
-keepclassmembers class com.example.cashmemo.data.model.** { *; }

# ── Explicit model preservation ───────────────────────────────────────────────
-keep class com.example.cashmemo.data.model.Account        { *; }
-keep class com.example.cashmemo.data.model.Transaction    { *; }
-keep class com.example.cashmemo.data.model.Borrower       { *; }
-keep class com.example.cashmemo.data.model.Investment     { *; }
-keep class com.example.cashmemo.data.model.Debt           { *; }
-keep class com.example.cashmemo.data.model.Payment        { *; }
-keep class com.example.cashmemo.data.model.DebtPayment    { *; }
-keep class com.example.cashmemo.data.model.Budget         { *; }
-keep class com.example.cashmemo.data.model.Goal           { *; }
-keep class com.example.cashmemo.data.model.Project        { *; }
-keep class com.example.cashmemo.data.model.Person         { *; }
-keep class com.example.cashmemo.data.model.FinancialSummary { *; }
-keep class com.example.cashmemo.data.model.RecurringTransaction { *; }
-keep class com.example.cashmemo.data.model.NetWorthSnapshot { *; }

# ── Kotlin companion objects ──────────────────────────────────────────────────
-keepclassmembers class com.example.cashmemo.** {
    *** Companion;
}
-keepnames class kotlinx.serialization.json.Json { *; }

# ── Firebase / Firestore ──────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Coroutines ────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── iText7 PDF library ────────────────────────────────────────────────────────
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── WorkManager ───────────────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class com.example.cashmemo.notifications.** { *; }

# ── Biometric ────────────────────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Remove debug logs in production ──────────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ── Keep BuildConfig ──────────────────────────────────────────────────────────
-keep class com.example.cashmemo.BuildConfig { *; }

# ── Navigation ───────────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }

# ── Misc ─────────────────────────────────────────────────────────────────────
-dontwarn org.slf4j.**
