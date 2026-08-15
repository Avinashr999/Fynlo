# Open Issues And Release Plan

## Risk Resolution Pass - 2026-08-15

This pass updates the original migration risks using newer local evidence from `PROJECT_STATE_FOR_AI.md`, current workspace files, and the local attachment cache. It does not modify production code and does not upload anything.

| ID | Original Risk | Updated Status | What Changed / Evidence | Remaining Gate |
|---|---|---|---|---|
| OPN-001 | Full production cloud round-trip was not fully evidenced. | Resolved as user-verified, still keep as release smoke. | `PROJECT_STATE_FOR_AI.md` now records cloud/local parity as `PHONE VERIFIED BY USER` after sign-in/sync testing. | Repeat once before production release if auth/sync code changes. |
| OPN-002 | Dev Google sign-in needed real dev Firebase/OAuth setup. | Reduced, still configuration-gated. | `app/google-services.json` and `app/src/dev/google-services.json` exist. Prior phone evidence showed dev sign-in button launched but Google returned setup/status failure. | If dev sign-in must work, verify Firebase OAuth/SHA for `app.fynlo.dev`; otherwise keep clear dev-only message. |
| OPN-003 | Old data could create noisy Book Check warnings. | Mitigated. | Project state records grouped old-interest review rows and normal-user Book Check simplification. | Old historical rows can still need user review; do not auto-guess. |
| OPN-004 | Dark mode readability needed repeat audit. | Partly mitigated, still release visual gate. | Dark-mode contrast fixes are documented. Historical notes still say dark mode is fragile after UI passes. | Phone visual check before each release build. |
| OPN-005 | Global search/top inset and keyboard screens regressed. | Mitigated. | Project state records top-gap removal, compact header, no auto-keyboard on open, and later search text clipping fix. | Keep in smoke checklist because this has regressed before. |
| OPN-006 | External tester mismatch was reported on old version 3.2.108. | External retest required. | Many accounting fixes landed after 3.2.108, but the external tester data cannot be reproduced from local docs alone. | Ask tester to retest latest internal build with clean known data. |
| OPN-007 | Financial repairs/migrations must remain idempotent. | Ongoing engineering rule, not a one-time risk. | Tests and Book Check repair principles exist; no broad migration was needed for the July interest fix. | Any future repair must support dry-run/export and repeated safe execution. |
| OPN-008 | Play warnings can return after build-tool changes. | Mitigated. | Project state records AAB, R8 mapping, native-symbol ZIP, and user-confirmed upload of mapping/symbols. | For every new AAB, upload matching mapping/native symbols. |
| OPN-009 | Uploaded screenshots/attachments might be inaccessible. | Reduced locally. | Current workspace contains 11 local `.codex-remote-attachments` image files now indexed in `10_SOURCE_INDEX.csv`. | Still not a durable release record unless copied into tracked docs/assets. |
| OPN-010 | Minimalism can drift as audit features expand. | Ongoing product design gate. | User preference and minimal Book Check visibility are documented. | Review new UI against the ?does this help the user act?? rule. |

## Current Remaining Release Gates

| Gate | Status | Owner / Trigger |
|---|---|---|
| Latest cloud round-trip after auth/sync changes | Required only if auth/sync code changes again | Phone/user or Codex with test account |
| Dev Google sign-in | Configuration-gated | Firebase/Google OAuth setup for dev app ID |
| External tester mismatch retest | External | Tester on latest internal build |
| Dark mode visual sweep | Required before next public release | Phone smoke |
| Native symbols + mapping upload | Required per AAB | Play Console uploader |
| Book Check old-interest review | Expected on old data | User confirms or assigns period; app must not guess |

## Gate Execution Pass - 2026-08-15

This pass executed the real local gates available from the laptop and connected phone. It did not change production code.

| Gate | Result | Evidence |
|---|---|---|
| Prod debug compile | Passed | `:app:compileProdDebugKotlin` completed successfully. |
| Prod debug unit tests | Passed | `:app:testProdDebugUnitTest` completed successfully. |
| Install latest test builds | Passed | Both `app.fynlo` and `app.fynlo.dev` installed on the connected phone. |
| Installed version check | Passed | Production: `3.2.114` / `238`; developer: `3.2.114-dev` / `238`. |
| Release AAB build | Passed | `:app:bundleProdRelease` completed successfully. |
| Release artifacts | Present | AAB, `mapping.txt`, and native-symbol ZIP exist for the prod release build. |
| Dark mode dashboard | Passed local visual smoke | Screenshot saved under `gate-evidence-2026-08-15/dark-dashboard.png`; content is readable. |
| Global search top spacing | Passed local visual smoke | Screenshot saved under `gate-evidence-2026-08-15/global-search.png`; no old large top gap. |
| Global search keyboard state | Passed local visual smoke | Screenshot saved under `gate-evidence-2026-08-15/global-search-keyboard.png`; search remains visible with keyboard open. |

Artifacts for Play Console:

- AAB: `app/build/outputs/bundle/prodRelease/app-prod-release.aab`
- R8 mapping: `app/build/outputs/mapping/prodRelease/mapping.txt`
- Native symbols: `app/build/outputs/native-debug-symbols/prodRelease/fynlo-prod-release-native-symbols.zip`

Remaining non-local gates:

- Dev Google sign-in remains configuration-gated unless Firebase/Google OAuth/SHA for `app.fynlo.dev` is confirmed in the Firebase/Google consoles.
- External tester mismatch requires the affected tester to retest the latest internal build; it cannot be reproduced from local files alone.
- Cloud round-trip is not required for this pass because no auth/sync code changed. Repeat it if auth or sync changes again.

## Before Next Internal-Testing AAB

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

## Before Production Release

1. Repeat cloud round-trip with a test Google account if auth or sync changed.
2. Validate privacy policy and delete account URLs are live.
3. Confirm Data Safety still matches code: Firebase auth/sync, Crashlytics/Performance/Analytics, optional contacts, financial records stored locally/cloud by user choice.
4. Check dark mode and light mode on at least one real phone.
5. Export report PDF and XLSX from realistic data.
6. Review Play Console vitals after testers install; data can take time to appear.
