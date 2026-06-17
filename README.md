# Fynlo

Fynlo is a personal Android financial ledger built with Kotlin and Jetpack Compose. It tracks money using double-entry style flows from origin to destination, with PIN security, lending and debt tracking, investments, budgets, goals, exports, encrypted backup support, and optional Firebase-backed sync.

Current development milestone: **3.2.91** (`versionCode` 214).

Release status: all UX audit clusters P0-P3, including C03b, are closed in the current development line. Per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`, Play Console upload is still gated on the release-prep checklist.

## Features

- 4-digit PIN lock on startup
- Master dashboard with net worth, assets, liabilities, and account health
- Double-entry style transaction capture with source and destination accounts
- Lending, borrowing, repayments, debt payoff planning, and EMI tools
- Investments, valuation history, CAGR/XIRR helper logic, budgets, goals, and projects
- People/contact book with stable IDs and loan/debt linkage
- Recurring transactions, reminders, and collection calendar
- Global search with filters, fuzzy matching, and recent searches
- PDF, CSV, XLSX, JSON, and encrypted backup/export flows
- Optional Google Sign-In and Firestore sync
- Crashlytics, Firebase Performance, and macrobenchmark/baseline-profile setup

## Tech Stack

- Kotlin 2.2.10
- Jetpack Compose BOM `2026.04.01`, Material 3, Material Icons Extended
- Navigation Compose
- Room 2.8.4 with KSP 2.3.7, schema export through Room version 25
- Hilt 2.56
- Firebase BOM 33.7.0: Auth, Firestore, Crashlytics, Performance, Analytics
- DataStore Preferences, WorkManager, Biometric, Billing client
- Android Gradle Plugin 9.2.1 / Gradle 9.4.1
- minSdk 26, targetSdk 36, compileSdk 36
- Java 17 / JVM target 17

## Getting Started

### Prerequisites

- Android Studio with Android SDK 36
- JDK 17
- A device or emulator for instrumented migration tests

### Firebase Setup

The app uses Firebase for optional sync and diagnostics. The repo includes `app/google-services.json` for the production flavor so the project can build. For an isolated fork:

1. Create a Firebase project.
2. Add an Android app with package name `app.fynlo`.
3. Enable Firestore and Authentication with Google Sign-In.
4. Replace `app/google-services.json`.
5. For true dev/prod isolation, create a separate Firebase Android app for `app.fynlo.dev` and place its config at `app/src/dev/google-services.json`.

See `app/src/dev/README.md` for the dev flavor notes.

## Build And Test

```bash
./gradlew assembleProdDebug
./gradlew testProdDebugUnitTest
./gradlew :app:assembleProdRelease --no-daemon --console=plain --no-configuration-cache
./gradlew :app:bundleProdRelease --no-daemon --console=plain --no-configuration-cache
./gradlew :app:connectedProdDebugAndroidTest
```

The connected Android test task requires a running device or emulator and is important for Room migration validation.
Production release APK/AAB builds are slower than debug builds; use a long command timeout when running them from automation.

## Project Layout

```text
app/src/main/java/app/fynlo/
|-- FynloApplication.kt
|-- MainActivity.kt
|-- FinanceViewModel.kt
|-- data/          Room entities, repositories, sync, preferences
|-- logic/         Pure finance, import/export, ID, search, and planning helpers
|-- notifications/ WorkManager and reminder scheduling
|-- ui/            Compose navigation, screens, components, theme
```

Other important folders:

- `app/schemas/` - exported Room schemas
- `macrobenchmark/` - startup, scroll, and baseline profile tests
- `decisions/` - architecture and release-cadence decisions
- `docs/` - static policy pages
- `release_notes/` - milestone release notes

## Governance

Before making code or UI changes, read:

- `PROJECT_STATE_FOR_AI.md`
- `AI_AGENT_PROTOCOL.md`
- `DESIGN_SYSTEM.md`
- `UX_AUDIT_2026-05-25.md`

Data-integrity areas such as migrations, exports, sync, recalculate balances, and repayments require regression tests.

## Privacy

Fynlo stores financial data locally in Room. Firebase sync is optional. Contacts import is runtime-permission gated. See `PRIVACY_POLICY.md` for the full policy.

## License

Personal project; no license is granted for redistribution unless stated otherwise.
