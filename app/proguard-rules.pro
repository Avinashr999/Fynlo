# Fynlo — ProGuard / R8 Rules (Production)

# ── Room ─────────────────────────────────────────────────────────────────────
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(...); }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *
-keep @androidx.room.Entity class *
-keep @androidx.room.TypeConverters class *

# ── Kotlin Serialization (all domain models) ─────────────────────────────────
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, RuntimeVisibleAnnotations
-keep @kotlinx.serialization.Serializable class app.fynlo.data.model.** { *; }
-keepclassmembers class app.fynlo.data.model.** { *; }

# ── Explicit model preservation ───────────────────────────────────────────────
-keep class app.fynlo.data.model.Account        { *; }
-keep class app.fynlo.data.model.Transaction    { *; }
-keep class app.fynlo.data.model.Borrower       { *; }
-keep class app.fynlo.data.model.Investment     { *; }
-keep class app.fynlo.data.model.Debt           { *; }
-keep class app.fynlo.data.model.Payment        { *; }
-keep class app.fynlo.data.model.DebtPayment    { *; }
-keep class app.fynlo.data.model.Budget         { *; }
-keep class app.fynlo.data.model.Goal           { *; }
-keep class app.fynlo.data.model.Project        { *; }
-keep class app.fynlo.data.model.Person         { *; }
-keep class app.fynlo.data.model.FinancialSummary { *; }
-keep class app.fynlo.data.model.RecurringTransaction { *; }
-keep class app.fynlo.data.model.NetWorthSnapshot { *; }

# ── Kotlin companion objects ──────────────────────────────────────────────────
-keepclassmembers class app.fynlo.** {
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
-keep class app.fynlo.notifications.** { *; }

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
-keep class app.fynlo.BuildConfig { *; }

# ── Navigation ───────────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }

# ── Misc ─────────────────────────────────────────────────────────────────────
-dontwarn org.slf4j.**
