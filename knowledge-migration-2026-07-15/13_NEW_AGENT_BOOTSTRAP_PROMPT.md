# New Agent Bootstrap Prompt

You are continuing work on the Fynlo Ledger Android app in `C:\Users\user\AndroidStudioProjects\Fynlo`.

Before changing code, read this migration package:

1. `knowledge-migration-2026-07-15/00_EXECUTIVE_OVERVIEW.md`
2. `knowledge-migration-2026-07-15/05_FINANCIAL_LOGIC_SPEC.md`
3. `knowledge-migration-2026-07-15/06_TEST_AND_VERIFICATION_MATRIX.md`
4. `knowledge-migration-2026-07-15/07_OPEN_ISSUES_AND_RELEASE_PLAN.md`
5. `PROJECT_STATE_FOR_AI.md`
6. `docs/release-handoff/2026-06-20-internal-testing-knowledge-hub.md`

Core product rule: Fynlo Ledger is a clean, minimal, trustworthy personal finance ledger. Do not turn user screens into an accounting/debug tool. Keep complex logic behind the scenes.

Core accounting rule: every money movement needs traceability. Do not change principal, interest, dates, source accounts, balances, or cloud state silently.

Important current behavior:

- Package name remains `app.fynlo`; public name is Fynlo Ledger.
- Local/guest mode is supported; cloud backup is optional.
- Production Google sign-in and developer Google sign-in are separate. Dev may need its own Firebase/OAuth setup.
- Loan/debt interest accrues from the current start date. Paid-ahead interest explains zero due but must not stop accrual.
- Payment rows are source of truth for paid principal/interest.
- Book Check should show review/warning for unclear old interest, not serious corruption unless ledger data is actually broken.
- Reset All Data and Reset cloud backup are separate high-risk flows.

Use these release-gate commands unless the project has intentionally changed:

```powershell
.\gradlew.bat :app:compileProdDebugKotlin --no-daemon
.\gradlew.bat :app:testProdDebugUnitTest --no-daemon
```

For release AAB, use a longer timeout and bump version code/name before upload. Preserve matching mapping/native-symbol artifacts for Play Console.

Do not claim completion from automated tests alone. For money flows, require phone smoke or clearly state that phone verification is still pending.
