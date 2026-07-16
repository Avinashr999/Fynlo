# Test And Verification Matrix

## Automated Verification

| ID | Area | Verification | Status | Evidence |
|---|---|---|---|---|
| TST-001 | Kotlin compile | `:app:compileProdDebugKotlin` | Passed in multiple release gates | User/session reports. |
| TST-002 | Unit tests | `:app:testProdDebugUnitTest` | Passed in multiple release gates | User/session reports. |
| TST-003 | Interest policy | Forced rerun of key interest tests | Passed | `InterestPolicyTest`. |
| TST-004 | Ledger accountability | Forced rerun | Passed | `LedgerAccountabilityTest`. |
| TST-005 | Money action idempotency | Duplicate action/data integrity tests | Passed | `MoneyActionIdempotencyDataIntegrityTest`. |
| TST-006 | Financial summary | Summary calculation tests | Present/passed in prior gates | `FinancialSummaryCalculationTest`. |
| TST-007 | Sync logic | Sync logic tests | Present/passed in prior gates | `SyncLogicTest`. |
| TST-008 | Investment funding | Add/delete/restore regression | Passed earlier | Repository tests. |
| TST-009 | Loan wrong-date correction | Wrong future date to past date accrual | Added/passed | Interest regression tests. |
| TST-010 | Debt wrong-date correction | Wrong future date to past date accrual | Added/passed | Debt regression tests. |
| TST-011 | Paid-ahead accrual | Accrual continues after advance interest | Added/passed | Interest policy tests. |
| TST-012 | Book Check severity | Paid-ahead shown as warning/review | Added/passed | Ledger accountability tests. |
| TST-013 | Reset cloud/local | Reset support tables and stale cloud data | Tested in flow | Sync tests/user smoke. |
| TST-014 | R8/release | Release optimization verified | Passed | Build outputs. |
| TST-015 | Native symbols | Native symbol zip task produced artifact | Passed | Build output and upload. |
| TST-016 | Mapping file | R8 mapping produced/uploaded | Passed | User confirmation. |
| TST-017 | Android install | Prod/debug installed on connected phone | Passed when phone available | Session reports. |
| TST-018 | Launch sanity | Installed app launched | Passed | Session reports. |

## Manual / Phone Verification

| ID | Flow | Status | Notes |
|---|---|---|---|
| VER-001 | Main navigation/dashboard | Phone-verified by user at several points | Needs repeat after each release build. |
| VER-002 | Loans add/edit/delete/payment | Phone-verified by user for many cases | Interest-date cases were actively tested. |
| VER-003 | Debt add/edit/payment/delete | Phone-verified by user | Some balance/source issues were fixed after testing. |
| VER-004 | Investment add/delete/withdraw/source | Phone-verified by user | Funding/restore safety was key release gate. |
| VER-005 | Account statements/running balances | Phone-verified by user | User requested paise and before/change/after clarity. |
| VER-006 | Expenses/income dialogs | Phone-verified by user | Minimalism/design passes followed. |
| VER-007 | Reports PDF/XLSX export | Phone-verified by user | User confirmed video/export looked okay. |
| VER-008 | Book Check | Phone-verified by user | Still can surface old-data review warnings. |
| VER-009 | Audit trail CSV | User exported and inspected | CSV fields include timestamp/action/entity/account/before/after/reason. |
| VER-010 | Reset All Data | User verified old data no longer came back after later fix | Needs caution in future cloud work. |
| VER-011 | Google sign-in production | Reported working after Play/Firebase setup | Dev app remains config-dependent. |
| VER-012 | Dark mode | Improved and inspected | Repeat visual audit advised. |
| VER-013 | Global search layout | Fixed after repeated reports | Keep in release smoke. |
| VER-014 | EMI calculator keyboard/insets | Fixed after repeated reports | Keep in release smoke. |
| VER-015 | Play internal testing upload | User uploaded AAB, mapping, native symbols | Console accepted after fixes. |
| VER-016 | 17 local UI/accounting items | User said fully verified | Exact raw list is partly in conversation, not fully source-indexed. |

## Minimum Release Smoke Checklist

1. Fresh launch as guest and signed-in account.
2. Add income, expense, transfer, loan given, debt taken, investment purchase.
3. Edit amount/date/source for loan, debt, investment, and account movement where supported.
4. Collect loan interest/principal; pay debt interest/principal.
5. Change loan/debt date backward and forward; confirm no duplicate rows and interest remains understandable.
6. Run Book Check; confirm critical issues are real and review items are plain language.
7. Reset local data on a test account; confirm old cloud data does not return unexpectedly.
8. Export PDF/XLSX and inspect first and later pages.
9. Restart app; compare dashboard totals with detail screens.
10. Install release/internal-testing build and repeat abbreviated smoke.
