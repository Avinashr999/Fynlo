# Requirements And Decisions

This file separates user/product requirements from implementation decisions. Status labels mean:

- Implemented: repository evidence exists.
- Tested: automated test or build evidence exists.
- Phone-verified: user or Codex reported device verification.
- Reported: user reported behavior; source may be external to repo.
- Inferred: derived from commits/docs/conversation and should be rechecked before release.

## Requirements

| ID | Requirement | Status | Evidence |
|---|---|---|---|
| REQ-001 | Money actions must be idempotent and protected against duplicate taps. | Implemented, Tested | Transaction safety commits; `MoneyActionIdempotencyDataIntegrityTest`. |
| REQ-002 | Investment funding must deduct the selected account by stable account ID. | Implemented, Tested | Investment funding path and regression tests. |
| REQ-003 | Investment delete/restore must restore only the original funding movement once. | Implemented, Tested | Repository delete/restore changes and tests. |
| REQ-004 | Loan creation must record the source account used for disbursement. | Implemented | Loan traceability commits and UI. |
| REQ-005 | Debt creation must record the account where borrowed money was received. | Implemented | Debt traceability commits and UI. |
| REQ-006 | Account transfers must debit one account and credit another by the same amount, with no net-worth change. | Implemented, Tested | Transfer logic commits; ledger/account tests. |
| REQ-007 | Account and transaction history must sort by event date and time. | Implemented | DAO ordering and transaction history commits. |
| REQ-008 | Account statements must show before, change, and balance after where relevant. | Implemented, Phone-verified | Running balance timeline work and phone screenshots. |
| REQ-009 | Interest accrual must always calculate from the current loan/debt start date, unless the user intentionally caps interest after due date. | Implemented, Tested | `InterestPolicy`, `InterestEngine`, date edit regression tests. |
| REQ-010 | Accrued interest and collected/paid interest must be tracked separately. | Implemented, Tested | Period-aware interest policy. |
| REQ-011 | Interest due must be `max(0, accrued - current paid - waived)`. | Implemented, Tested | `InterestPolicy` tests. |
| REQ-012 | Advance/paid-ahead interest explains zero due but must not stop future accrual. | Implemented, Tested | Paid-ahead regression tests. |
| REQ-013 | Interest-only rows should carry user-understandable period meaning. | Implemented | Period choice UX and payment row fields. |
| REQ-014 | Older unclear interest rows should be Book Check review/warning, not serious corruption. | Implemented | `LedgerAccountability`. |
| REQ-015 | Date edits must rebuild paid totals from actual payment rows and avoid duplicates. | Implemented, Tested | DAO rebuild methods and regression tests. |
| REQ-016 | Borrower/debt detail screens must use layman-friendly language. | Implemented | UI simplification passes. |
| REQ-017 | Book Check must be actionable and avoid developer/debug language. | Implemented | Minimalism and Book Check UX commits. |
| REQ-018 | Reset All Data must not allow old Firestore data to reappear. | Implemented, Tested | Reset/cloud sync fixes and tests. |
| REQ-019 | Reset cloud backup must stop listeners, clear cloud, and repush the current local state. | Implemented | SyncManager and repository reset changes. |
| REQ-020 | Guest/local users must be able to sign in later without silently deleting local data. | Implemented; production verified, dev config caveat | Auth changes and user reports. |
| REQ-021 | Production Google sign-in must work; dev flavor needs correct Firebase/OAuth setup. | Implemented for prod, blocked for dev without config | Dev README and auth notes. |
| REQ-022 | Cloud sync state should show checking/syncing/synced/error feedback. | Implemented | Startup sync feedback commits. |
| REQ-023 | PDF/XLSX exports must avoid overlaps and remain readable. | Implemented, Phone-verified | Report export work and user confirmation. |
| REQ-024 | Public app identity is Fynlo Ledger while package stays `app.fynlo`. | Implemented | Brand rename commits. |
| REQ-025 | Privacy policy, terms, delete-account links, and Play data safety must be aligned. | Implemented | GitHub Pages docs and Play setup notes. |
| REQ-026 | UI should follow the approved minimal premium template. | Implemented, Phone-verified | Template lock and revamp commits. |
| REQ-027 | Dark mode must be readable. | Mostly implemented; verify before release | Dark mode audit commits. |
| REQ-028 | Global search top gap/insets must be fixed. | Implemented; user rechecked later | Search spacing commits. |
| REQ-029 | Zero-balance accounts should be closable/deletable safely. | Implemented | Account close/delete commits. |
| REQ-030 | Currency display should include paise where accounts need exactness. | Implemented | Paise/account display commits. |
| REQ-031 | Release AAB should happen only after phone smoke for critical accounting flows. | Process decision | Release-gate notes. |
| REQ-032 | Native symbols and mapping files must be generated/uploadable for Play Console. | Implemented | Build task and user upload confirmation. |

## Decisions

| ID | Decision | Rationale | Evidence |
|---|---|---|---|
| DEC-001 | Keep package name `app.fynlo`; change visible brand to Fynlo Ledger. | Avoid package migration while avoiding Play Store name conflict. | Brand rename commits. |
| DEC-002 | Use stable account IDs for money movements. | Names can change; IDs preserve traceability. | Repository commits. |
| DEC-003 | Treat payment rows as source of truth for paid principal/interest. | Prevent stale totals after date edits. | DAO rebuild methods. |
| DEC-004 | Paid-ahead interest is normal/review, not corruption. | Extra interest can be intentional. | Book Check and interest policy. |
| DEC-005 | Book Check is a trust assistant, not a developer diagnostics page. | User requested minimal layman UX. | UX principle and commits. |
| DEC-006 | No silent financial changes. | Financial records need explicit confirmation. | Product principle. |
| DEC-007 | Debt-funded investments may be journal-only and should not double-move account balance. | Prevent net-worth/account inflation. | Ledger accountability rules. |
| DEC-008 | Reset cloud backup is separate from Reset All Data. | Avoid accidental cloud wipe and stale rehydrate. | Sync reset work. |
| DEC-009 | Dev Google sign-in requires separate Firebase/OAuth configuration. | App IDs differ. | Dev README. |
| DEC-010 | Use Credential Manager/Google ID credentials instead of deprecated Google Sign-In API. | Modern auth path and warning cleanup. | Auth modernization commits. |
| DEC-011 | Keep R8/minification/resource shrinking enabled for release. | Play optimization and release quality. | Build config. |
| DEC-012 | Generate native-symbol zip and upload mapping/native symbols manually in Play Console. | Improve crash/ANR diagnostics. | Build tasks and user confirmation. |
| DEC-013 | Phone smoke comes before release AAB. | Accounting app needs device reality check. | Release-gate discussions. |
| DEC-014 | Target age is 18+. | App is finance/accounting oriented. | Play setup discussion. |
| DEC-015 | App is free and has no ads. | Current product positioning. | Play setup discussion. |
| DEC-016 | Ordinary screens avoid internal words like stale, allocation, bucket, sync state. | Keep app approachable. | UX principle. |
| DEC-017 | Local-first with optional cloud backup. | Users can continue without sign-in. | Auth/onboarding flow. |
| DEC-018 | Save buttons should become active only after valid data and show feedback after action. | Trust and clarity. | UI consistency pass. |
| DEC-019 | This migration package is local only and not uploaded. | User asked for local knowledge extraction. | Current task. |
| DEC-020 | Manual/user verification must be labeled separately from automated test evidence. | Avoid overstating release readiness. | Current task. |

