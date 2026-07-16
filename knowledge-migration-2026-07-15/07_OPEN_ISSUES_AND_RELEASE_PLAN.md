# Open Issues And Release Plan

## Open Issues / Release Risks

| ID | Risk | Severity | Current Status | Recommended Next Step |
|---|---|---|---|---|
| OPN-001 | Full production cloud round-trip is not fully evidenced in repository docs. | High | Partly user-tested; exact full matrix not captured. | Run controlled cloud test: fresh install, guest data, sign in, sync, reinstall, restore. |
| OPN-002 | Dev Google sign-in needs real dev Firebase/OAuth setup. | Medium | Documented; production separate. | Configure dev OAuth/SHA or keep clear dev-only message. |
| OPN-003 | Old data can still create Book Check review warnings for unclear interest periods. | Medium | Expected; not corruption. | Add one-tap review/edit route where safe. |
| OPN-004 | Dark mode readability needs repeat final audit after every visual pass. | Medium | Improved but historically fragile. | Device walkthrough in dark mode before release. |
| OPN-005 | Global search/top inset and keyboard screens historically regressed. | Medium | Fixed lately. | Add screenshot/emulator regression check. |
| OPN-006 | External user internal testing data mismatch was reported for version 3.2.108. | High | Many fixes landed later. | Ask tester to retest latest version with clean or known data. |
| OPN-007 | Financial data migrations/repairs must remain idempotent. | High | Current fixes aimed to avoid migration. | Any future repair should have dry-run and backup/export first. |
| OPN-008 | Play Console warnings can return after build-tool changes. | Medium | Native symbols/mapping uploaded once. | Confirm each new AAB has matching mapping/native symbols. |
| OPN-009 | Some uploaded screenshots/attachments are outside repo and may not remain accessible. | Low | Migration uses repo/docs plus conversation summary. | Keep important release evidence in repo docs. |
| OPN-010 | User-facing minimalism can drift as new audit features are added. | Medium | Strong preference documented. | Gate new screens with “does this help user act?” review. |

## Release Plan

### Before next internal-testing AAB

1. Confirm working tree is clean except intentional generated artifacts.
2. Run prod debug compile and tests.
3. Install prod/debug and dev/debug if phone is available.
4. Run the minimum release smoke checklist in `06_TEST_AND_VERIFICATION_MATRIX.md`.
5. Confirm Book Check has no critical issues on test data, or document why a warning is expected.
6. Build release AAB with a new version code and version name.
7. Generate and retain mapping/native-symbol artifacts for that exact version.
8. Upload AAB, mapping, and native symbols to Play Console.
9. Add release notes inside language tags if Play Console requires them.
10. Send internal testing link to testers; do not assume emails alone notify them clearly.

### Before production release

1. Repeat cloud round-trip with a test Google account.
2. Validate privacy policy and delete account URLs are live.
3. Confirm Data Safety still matches code: Firebase auth/sync, Crashlytics/Performance/Analytics, optional contacts, financial records stored locally/cloud by user choice.
4. Check dark mode and light mode on at least one real phone.
5. Export report PDF and XLSX from realistic data.
6. Review Play Console vitals after testers install; data can take time to appear.
