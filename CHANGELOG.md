# Changelog

All notable changes to Fynlo are documented here.

## [3.2.3] - 2026-05-26 *(Development milestone — C02 closure; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C02 — stale exports and missing auto-recalc.** The audit's reproducer ("PDF generated pre-recalc showed ₹268,081; post-recalc showed ₹241,663 — same data, ₹26K difference, no warning") is structurally no longer reachable. Five-part fix:
  1. **Auto-recalc on launch** — `FynloApplication.onCreate`'s post-init coroutine calls `recalcCoordinator.runIfStaleOnLaunch()` which runs the recalc once per calendar day (debounced on `lastRecalcAt < today.startOfDay` in the user's local zone, NOT a 24h sliding window). Failure is non-fatal.
  2. **Auto-recalc before every export** — all four formats (JSON, CSV, PDF, XLSX) route through `recalcCoordinator.runAndStamp()` first. XLSX previously bypassed the ViewModel entirely; now goes through a new `FinanceViewModel.exportToXLSX(os)` wrapper for consistency.
  3. **Dashboard "Last updated X ago" subtitle** — small `labelSmall`-styled line below the hero net-worth number on `HomeScreenModern`. Consumes `UserPreferences.lastRecalcAt(...)` reactively; renders via Android's `DateUtils.getRelativeTimeSpanString`. Renders "Not recalculated yet" when no recalc has ever run.
  4. **Before/after `AlertDialog` on manual Recalculate** — `FinanceViewModel.recalculateAllBalancesCapturingDelta()` captures pre/post `FinancialSummary` and returns a `RecalcDelta` with signed change fields. The Settings dialog shows pre→post for net worth plus signed deltas for receivables / cash / investments, or a "your data was already up to date" message when `isNoOp` (the common post-C01 case where structural enforcement means most recalcs change nothing). The old fire-and-forget Toast is gone.
  5. **Timestamp in exports** — `ExportUtility.generatePDF` adds a "Recalculated: \<date\>" line under the existing "Generated:" header; `ExcelExportUtility.generateFullBackup` prepends a `Metadata` sheet with `Generated` / `Recalculated at` / `Export type` rows. `RecalcCoordinator.runAndStamp()` now returns the stamped time so callers don't need a separate DataStore read.

### Added
- **`app.fynlo.data.RecalcCoordinator`** (`@Singleton`, Hilt-injected). Wraps `FinanceRepository.recalculateAllBalances()` with `lastRecalcAt` stamping; exposes `runAndStamp(): Long`, `runIfStaleOnLaunch()`, and a pure `shouldRecalcOnLaunch(lastRecalcAt, now, zone): Boolean` predicate on the companion (testable without Robolectric).
- **`UserPreferences.lastRecalcAt(ctx): Flow<Long>` + `setLastRecalcAt(ctx, ms)`** — DataStore-backed; follows the existing read-Flow + suspend-write pattern.
- **`FinanceViewModel.exportToXLSX(os)`** — new wrapper so XLSX gets the same recalc-stamp contract as PDF / CSV / JSON.
- **`app.fynlo.RecalcDelta`** data class — before/after snapshot of `FinancialSummary` with derived change fields and an `isNoOp` predicate. Used by the Settings result dialog.
- **`app/src/test/.../AutoRecalcDataIntegrityTest`** — 8 pure-function tests for the debounce predicate (zero / yesterday-late / same-day / week-gap / zone-correctness / arithmetic boundary). Matches `*DataIntegrity*` and `*Recalculate*` filters; the CI data-integrity gate count goes **3 → 11**.

### Changed
- **`versionName`** `3.2.2` → `3.2.3`, **`versionCode`** `125` → `126`. Internal milestone marker — not uploaded per the release-cadence ADR.
- **`SettingsScreen` PDF / XLSX launchers** rewrapped — PDF in `scope.launch(Dispatchers.IO)` since `exportToPDF` is now suspend; XLSX routes through `viewModel.exportToXLSX(os)` instead of calling `ExcelExportUtility.generateFullBackup` directly.

## [3.2.2] - 2026-05-26 *(Development milestone — C01 closure; not promoted per `decisions/2026-05-26-release-cadence-all-clusters-then-ship.md`)*

### Fixed
- **C01 — Recalculate Balances no longer destroys payment history.** Legacy borrowers and debts whose partial repayment was tracked only on the cumulative `paid` field (with `paidPrincipal == paidInterest == 0` and no rows in `payments` / `debt_payments`) had `paid` silently zeroed on every tap of Recalculate. The fix is structural: `payments` and `debt_payments` are the **single source of truth** for repayment history; `paid` is a derived projection re-computed from `SUM(payments)`. (UX_AUDIT §C01; ADR `decisions/2026-05-26-c01-fix-strategy.md`; commits `331c1ae` Stage 1, `5a00d4a` Stage 2.)
- `setup-gradle@v3` × Gradle 9 incompatibility — every `./gradlew` invocation in CI was failing in 6s with `Cannot get the value of write-only property 'removeUnusedEntriesOlderThan'`. Upgraded to `@v4` in both workflows.
- `kotlinx-serialization` × `room-testing` runtime incompatibility — `room-testing:2.8.4` was compiled against `kotlinx-serialization 1.8.x` (where `GeneratedSerializer.typeParametersSerializers()` has a default implementation), but the project pinned `1.7.3` where it's still abstract. `MigrationTestHelper.loadSchema` failed with `AbstractMethodError` on every test. Bumped `kotlinxSerialization` `1.7.3 → 1.8.1` in `gradle/libs.versions.toml`. Bump also covers `androidx.room:room-testing:2.8.4 androidTestImplementation` (previously stuck at `2.6.1`).
- **PDF export: consolidated Lending & Receivables table now includes a `Paid` column.** The widely-shared whole-portfolio PDF (the one users hand to accountants) was rendering only `Principal / Rate / Lent On / Due / Status / Notes`, making a borrower with `paid > 0` indistinguishable from a brand-new loan. Surfaced by the 3.2.2 §3.5 smoke test. The per-borrower-statement PDF (`generateLoanStatementPDF`), XLSX, and JSON all had it; only this one widely-shared table did not. Touches C21 (PDF/XLSX export quality) — doesn't close the cluster, just resolves the most visible artefact.
- **PDF footer no longer hardcodes the version string.** `ExportUtility.kt` had `"Generated by Fynlo v3.1 | ..."` baked in, so a 3.2.2 build was emitting PDFs labelled `v3.1`. Now reads `BuildConfig.VERSION_NAME`. This was a pre-existing `LINT_RULES.md FY022` violation; the protocol-compliance check in `checks.yml` gates on `pull_request` only, so direct-push commits never tripped it.

### Added
- **Schema migration `v15 → v16`** — one-time backfill on first launch: every borrower / debt with `paid > 0` and no rows in `payments` / `debt_payments` gets one synthetic `Payment` (or `DebtPayment`) row with `amount = paid`, `principal = paid`, `interest = 0`, `date = loanDate`, `type = "Legacy backfill"`, notes recording that the actual repayment date is genuinely unknown. Total `paid` is preserved exactly. Schema export `app/schemas/.../16.json` committed.
- **`RecalculateBalancesDataIntegrityTest`** — Robolectric-backed JVM test exercising real Room SQL against an in-memory `FynloDatabase` in three cases: legacy borrower with backfilled Payment, current-schema borrower with split Payment row, brand-new borrower with zero payments. All three green in CI.
- **`FynloDatabaseMigrationTest`** — instrumented test (`app/src/androidTest/`) using Room's classic `MigrationTestHelper`. Five cases: backfills a legacy borrower, leaves an already-populated borrower untouched, mirrors for the debts side, leaves `paid = 0` borrowers untouched, and re-opens the database with full Room afterwards to catch schema-validation drift. All five passed on CPH2767 / Android 16 against the real `v15 → v16` migration code path. Schemas are exposed to the test APK via `sourceSets["androidTest"].assets.srcDirs("$projectDir/schemas")`. Requires a connected device/emulator (`./gradlew :app:connectedProdDebugAndroidTest`); CI integration via Firebase Test Lab is a follow-up.
- **Data-integrity CI gate** — `.github/workflows/checks.yml` runs an explicit `--tests '*DataIntegrity*' --tests '*Recalculate*'` Gradle filter on every push / PR, so a regression cannot pass silently in the test log. (Closes INF04 in `UX_AUDIT §9`.)
- **ADR directory** `decisions/` with the C01 fix-strategy ADR (Michael Nygard format: Status / Context / Decision / Consequences / Alternatives considered).
- **Release notes directory** `release_notes/` with `3.2.2.md` (Play Store copy + migration disclosure + in-app banner draft + pre-release checklist).
- **`§6 Journal`** section added to `PROJECT_STATE_FOR_AI.md` (referenced by the preamble's reading-order block but never created).

### Changed
- **`FinanceRepository.recalculateAllBalances()`** rewritten: no longer calls the destructive `recalculateBorrowerPaid` / `recalculateDebtPaid` DAO queries (`UPDATE ... SET paid = paidPrincipal + paidInterest`). Now derives `paid` exclusively via `rebuildBorrowerPaidFromPayments` / `rebuildDebtPaidFromDebtPayments`, with the `WHERE EXISTS (payments)` gate removed and `SUM(...)` wrapped in `COALESCE(..., 0)` so brand-new borrowers land at `paid = 0` (not NULL).
- **`FinanceRepository.editTransaction`** Loan / Debt Repayment branches: editing a repayment transaction now does delete-old-Payment + insert-new-Payment + rebuild, instead of mutating `paid` directly. Sync now also pushes the Payment / DebtPayment delete (was a latent sync-correctness gap).
- **`FinanceRepository.deleteTransaction`** Loan / Debt Repayment branches: the "no matching Payment" fallback that reversed `paid` directly is removed. When no Payment matches the transaction's amount/date, `paid` is left as-is — consistent with the invariant `paid == SUM(payments)`.
- **`FinanceRepository.insertPaymentWithDest` / `insertDebtPaymentWithSource`**: the conditional `updateBorrower/DebtPaid{Amount,Principal,Interest}` writers are replaced by a single `rebuild...FromPayments` call after the Payment row is inserted.
- `PROJECT_STATE_FOR_AI.md` `Version` header bumped from stale `1.8.0` to `3.2.1` (matches `app/build.gradle.kts` `versionName`); §0.4 / §0.5 reflect C01 closure with structural guardrails for future agents.

### Removed
- **10 DAO methods** that allowed direct mutation of derived columns:
  - `updateBorrowerPaidAmount` / `updateBorrowerPaidPrincipal` / `updateBorrowerPaidInterest`
  - `updateDebtPaidAmount` / `updateDebtPaidPrincipal` / `updateDebtPaidInterest`
  - `recalculateBorrowerPaid` / `recalculateDebtPaid` *(the destructive queries that motivated the entire C01 ADR)*
  - `seedPaidPrincipalFromPaid` / `seedDebtPaidPrincipalFromPaid` *(dead code — their logic lives inline in migrations 11→12 / 12→13 / 15→16)*
- Matching repository wrappers on `LendingRepository` and `DebtRepository`.

### Security / data integrity
- **`paid == SUM(payments)` is now structurally enforced.** No DAO method exists that lets new code violate the invariant without first re-adding one of the ten deleted queries — and that re-introduction would be visible in code review and would fail the data-integrity CI gate.

### Performance
- Cold start ~4 % faster in Partial AOT mode (the production ship mode), up to ~10 % on devices with fully cold caches. Driven by the baseline profile bundled in the APK via `baseline-prof.txt` + `profileinstaller`. Measured on CPH2767 / Android 16, 2026-05-24; see `PROJECT_STATE_FOR_AI.md §5.6`.

### Infrastructure
- `gradle/wrapper/gradle-wrapper.jar` is now **committed** to the repository (was gitignored with a misleading "optional — keeps repo lighter" comment; broke `./gradlew` on every fresh clone, including all CI runs). `.gitignore` corrected.
- `gradlew` committed with executable bit (`100755`) instead of `100644`.
- `gradle/actions/setup-gradle@v3` → `@v4` in `android.yml` and `checks.yml` (Gradle 9 compatibility).
- Android Lint baseline (`app/lint-baseline.xml`) adopted: existing 41 errors + 174 warnings are filtered; new lint errors still fail CI. (Notable real issue inside the baseline: `windowLayoutInDisplayCutoutMode` in `values-night/themes.xml` requires API 27 but `minSdk` is 26 — flagged for a real fix in a follow-up.)
- `paths-ignore: ['**/*.md', 'docs/**']` on Android workflows — docs-only commits no longer trigger ~15 minutes of CI per commit. Workflow files themselves are deliberately not ignored.
- Governance documents added at repo root: `LEGAL_PROTOCOL.md`, `PRIVACY_POLICY.md`, `TERMS_AND_CONDITIONS.md`, `RELEASE_PROTOCOL.md`, `DATA_RECOVERY_PROTOCOL.md`, `BACKUP_PROTOCOL.md`, `INCIDENT_PROTOCOL.md`, `PRIVACY_PROTOCOL.md`, `EXTERNAL_DEPENDENCY_PROTOCOL.md`, `ANALYTICS_PROTOCOL.md`, `ONBOARDING_PROTOCOL.md`, `ACCESSIBILITY_AUDIT_PROTOCOL.md`, `SUNSET_PROTOCOL.md`, `GOVERNANCE_README.md`, `AI_AGENT_PROTOCOL.md`, `LINT_RULES.md`, `DESIGN_SYSTEM.md`, `UX_AUDIT_2026-05-25.md`, plus `.github/PULL_REQUEST_TEMPLATE.md`, `.github/workflows/checks.yml`, and `docs/privacy.md` / `docs/terms.md` for the GitHub Pages legal mirror (`docs/_config.yml` site title set to `Fynlo Legal`).

### Skipped
- **3.2.1** was an internal milestone (the governance / CI hygiene commits) and is intentionally never promoted to Play Store production — `RELEASE_PROTOCOL.md §8`'s hard rule: *"Do NOT promote 3.2.1 to production. Skip directly to 3.2.2."*

### Still TODO before 3.2.2 ships
*(Not blockers for the changelog entry itself, but listed here so the release procedure has a single source of truth.)*

- ~~`app/build.gradle.kts` `versionName` / `versionCode` bump~~ ✅ done (`versionName = "3.2.2"`, `versionCode = 125`).
- ~~Instrumented migration test~~ ✅ done — `FynloDatabaseMigrationTest` (5 cases, instrumented; passed on CPH2767 / Android 16, 2026-05-26). CI integration via Firebase Test Lab is a separate follow-up.
- `UX_AUDIT_2026-05-25.md §C01` status flip to "Closed in 3.2.2" once the tag lands.
- Macrobenchmark re-run + manual smoke test (`RELEASE_PROTOCOL.md §3.4`, §3.5) — need a device.

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
