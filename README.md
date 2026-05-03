# Cash Memo

A personal-use Android financial ledger built with Kotlin and Jetpack Compose. Cash Memo tracks every rupee using double-entry principles (origin → destination), with PIN security, automated anniversary-based interest, PDF/CSV exports, and an optional Firebase-backed sync for cross-device use.

Current version: **2.0.0** (versionCode 90).

## Features

- 4-digit PIN lock on startup
- Master dashboard: Net Worth, Total Assets (cash + investments + lent), Total Liabilities (principal + interest)
- Per-account passbook / statement view
- Spending analytics with category breakdown
- Anniversary-based step-compound interest engine (`A = P(1 + r/100)^n`, with simple interest for the partial year)
- Lending, borrowing, investments, budgets, and goals
- Smart Flow Wizard for connected money tracking
- Google Sign-In + Firestore sync (optional)
- JSON import/export for disaster recovery
- PDF + CSV exports via iText 7

## Tech Stack

- Kotlin 2.1.10
- Jetpack Compose (BOM `2026.04.01`), Material 3, Material Icons Extended
- Navigation Compose
- Room 2.8.4 (KSP)
- Firebase BOM 33.7.0 (Auth, Firestore), Play Services Auth 21.0.0
- kotlinx.serialization
- iText 7.2.5 for PDF
- minSdk 26, targetSdk 36, compileSdk 36
- Java 17 / JVM target 17 (with core library desugaring)
- Android Gradle Plugin 8.10.0 / Gradle 8.13

## Getting Started

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- JDK 17
- Android SDK 36

### Firebase Setup

The app uses Firebase for optional sync. The repo includes `app/google-services.json` so it builds out of the box. If you fork this and want your own Firebase project:

1. Create a project at <https://console.firebase.google.com>.
2. Add an Android app with package name `com.example.cashmemo` (or your own — you'll also need to update `applicationId` in `app/build.gradle.kts`).
3. Enable **Firestore** and **Authentication → Google Sign-In**.
4. Download the new `google-services.json` and replace `app/google-services.json`.

### Build

```bash
./gradlew assembleDebug         # debug APK
./gradlew assembleRelease       # release APK (requires signing config)
./gradlew test                  # unit tests
./gradlew connectedAndroidTest  # instrumented tests (device/emulator required)
```

The wrapper will fetch Gradle 8.13 on first run.

### Run

Open the project in Android Studio and run the `app` configuration on an emulator or device (API 26+). Default PIN on first launch is **1234**.

## Project Layout

```
app/src/main/java/com/example/cashmemo/
├── CashMemoApplication.kt
├── MainActivity.kt
├── FinanceViewModel.kt
├── UiState.kt
├── data/        # Room entities, DAOs, FinanceRepository
├── logic/       # InterestEngine, DateUtils, ExportUtility
└── ui/          # Compose screens, components, navigation
```

## Privacy

Cash Memo stores all data locally in Room. Optional Firestore sync is opt-in via Google Sign-In. See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for the full policy.

## License

Personal project — no license granted for redistribution at this time.
