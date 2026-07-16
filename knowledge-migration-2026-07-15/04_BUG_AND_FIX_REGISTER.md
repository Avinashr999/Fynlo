# Bug And Fix Register

| ID | Area | Problem | Fix / Current Handling | Status | Evidence |
|---|---|---|---|---|---|
| BUG-001 | Investment funding | Adding/deleting/restoring an investment could increase net worth while Personal Cash stayed unchanged. | Pass selected source account ID through dialog/view model/repository; restore only original funding transaction. | Fixed, Tested | Commits and tests. |
| BUG-002 | Duplicate actions | Double taps could create duplicate money rows. | Idempotency and in-flight guards. | Fixed, Tested | `MoneyActionIdempotencyDataIntegrityTest`. |
| BUG-003 | Firestore reset | Old cloud data could reappear after reset. | Stop listeners, clear support tables, one-shot sync scope reset. | Fixed, Tested | Sync/reset commits. |
| BUG-004 | Account transfer | Transfer needed equal debit/credit and no net-worth effect. | Dedicated transfer flow and ledger rules. | Fixed | Transfer commits. |
| BUG-005 | Debt creation edit | Editing debt amount/source could not reconcile receiving account. | Debt edit/source handling and Book Check checks. | Fixed | Debt traceability commits. |
| BUG-006 | Loan source visibility | Loan detail did not show where money was lent from. | Added disbursed-from/source display. | Fixed | UI commits. |
| BUG-007 | Debt use visibility | Debt detail did not show where borrowed money went. | Added deposited-to/account trace. | Fixed | UI commits. |
| BUG-008 | Investment source visibility | Investment card did not clearly show source account. | Added funded-by display and trace. | Fixed | UI commits. |
| BUG-009 | Date edit interest | Editing a loan/debt date corrected visible date but interest could still behave from old state. | Rebuild paid totals from payment rows; accrue from current date. | Fixed, Tested | Interest tests. |
| BUG-010 | Paid-ahead confusion | UI showed zero interest due without explaining advance interest. | Added paid-ahead/advance explanations and Book Check warning. | Fixed | UI and policy commits. |
| BUG-011 | Paid-ahead freeze risk | Paid-ahead interest could be interpreted as stopping accrual. | Interest due and paid-ahead are derived separately; accrual continues. | Fixed, Tested | `InterestPolicyTest`. |
| BUG-012 | Lakshmi Devi date edit | Changing loan date still showed confusing old paid/advance values. | Simplified period handling and unclear old interest review. | Fixed by later interest simplification | Conversation and tests. |
| BUG-013 | Samanvi Travels amount confusion | User-side ₹10,00,000 mention conflicted with inspected ₹6,00,000 DB row. | Documented ₹6,00,000 as authoritative for inspected row. | Clarified | Project state. |
| BUG-014 | PDF export overlap | Report charts/headings/tables overlapped. | Reworked PDF spacing, pagination, and table layout. | Fixed, Phone-verified | User confirmation. |
| BUG-015 | XLSX formatting | Summary sheet/display was rough. | Improved export styling/formatting. | Fixed | Export commits. |
| BUG-016 | Login setup | Google sign-in failed in internal testing due Play/Firebase SHA/OAuth setup. | Play app signing SHA added; modern Credential Manager later. | Fixed for prod, dev caveat remains | Auth docs. |
| BUG-017 | Dev auth | Developer app Google sign-in shows setup missing unless dev OAuth configured. | Dev README documents exact missing setup. | Known configuration caveat | `app/src/dev/README.md`. |
| BUG-018 | Dark mode | Some dark mode screens had unreadable text. | Dark mode readability audit/fixes. | Mostly fixed; verify before release | User reports and commits. |
| BUG-019 | Global search top gap | Search screen had excessive top inset/gap. | Insets/layout fixes. | Fixed, Phone-checked later | UI commits and screenshots. |
| BUG-020 | EMI keyboard | Keyboard covered EMI content and top spacing was poor. | Keyboard/inset and layout fixes. | Fixed per later user report | UI commits. |
| BUG-021 | Settings crash | Opening settings could close app after UI pass. | Fixed settings crash. | Fixed | Later commits. |
| BUG-022 | Account close | Zero-balance account close/delete was not enabled/clear. | Account close/delete controls revised. | Fixed | Account commits. |
| BUG-023 | Book Check noise | Old payments produced repeated noisy warnings. | Segregated user-facing vs developer checks and plain guidance. | Improved; old data may still need review | Book Check commits. |
| BUG-024 | Quick actions spacing | Dashboard quick-actions/accounts spacing was too airy. | Dashboard compacting/minimalism pass. | Fixed | UI commits. |
| BUG-025 | Versioning | Play upload rejected reused/older version codes. | Version bumps to later codes through 238. | Fixed | Build commits. |
| BUG-026 | Play native symbols warning | Play warned native symbols missing. | Added native-symbol package task and uploaded symbols/mapping. | Fixed by user upload | Build task and user confirmation. |

## Fix Count Notes

Some fixes span multiple commits and screens. Count used for the migration manifest: 28 fix records, including the 26 bugs above plus two cross-cutting fixes: release documentation/worklog maintenance and Play Store asset/store listing updates.
