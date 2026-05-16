# Changelog

All notable changes to Fynlo are documented here.

## [3.2.0] - 2026-05-16

### Added
- Hilt dependency injection (upgraded to 2.56 for Gradle 9 compatibility)
- First Launch Setup Wizard (language, theme, notifications, profile)
- Jetpack Preferences DataStore (replacing SharedPreferences)
- Default currency preference (9 currencies)
- Date format preference (3 formats)
- Firebase Analytics integration (screen views, feature usage, onboarding)
- Crashlytics non-fatal exception recording + ANR tracking
- Firebase Performance Monitoring with app startup trace
- Network security config (cleartext disabled)
- Staging build variant for QA testing
- 5 focused repositories (Lending, Debt, Investment, Expense, Account)
- 30 unit tests (InterestEngine + FinancialSummary calculations)

### Changed
- Hilt Gradle plugin now works with KSP on Gradle 9.x (version 2.56)
- ThemeController migrated from SharedPreferences to DataStore
- Navigation flow: Onboarding -> Setup Wizard -> Login
- ProGuard rules: added Hilt/Dagger keep rules

### Fixed
- Removed duplicate POST_NOTIFICATIONS request from MainActivity
- Fixed smart quote encoding issues in Navigation.kt

### Security
- Added network_security_config.xml (disables cleartext HTTP)
- ProGuard strips debug/verbose/info logs in release builds
- Firestore rules enforce user isolation and field validation

## [3.1.0] - 2026-05-15

### Added
- App launcher icon changed to emerald
- LoginScreen redesigned to match emerald design language
- Dark mode audit for onboarding illustrations

## [3.0.0] - Previous

### Features
- Multi-project workspace support
- Lending with SI/CI/RB/Both interest types
- Debt tracking with principal/interest split
- Investment portfolio with valuations
- Recurring transactions with auto-logging
- Net worth history and snapshots
- Money flow visualization
- Excel/JSON/CSV/PDF export
- PIN lock + biometric unlock
- Firebase cloud sync (offline-first)
- Smart Flow Wizard for quick logging
