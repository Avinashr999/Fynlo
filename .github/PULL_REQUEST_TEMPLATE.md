# Pull Request — Fynlo

<!--
This template enforces the Fynlo governance protocol.
Every PR must fill out every section. Sections marked **(required)** must be
non-empty for the PR to be eligible for merge.
-->

## 1. Summary (required)

<!-- One paragraph describing what this PR does and why. Plain English. -->

**What this PR does:**

**Why now:**

---

## 2. Cluster declaration (required)

<!-- Pick one. Delete the others. -->

- [ ] **Closes cluster** C__ — {title}
- [ ] **Partial cluster** C__ — subtasks {N-M} of {total}
- [ ] **New work outside audit** — justification: {...}
- [ ] **Hotfix** for {symptom} — root cause cluster: C__ (or unknown)

**Raw audit points closed by this PR:** #__, #__, #__

---

## 3. Priority alignment (required)

<!-- Confirm this work doesn't jump priority. -->

- [ ] All P0 ship-blockers (C01, C02, C03a, C05) are either closed OR this PR is part of P0 work
- [ ] This PR is not a P3 task while P0/P1 remains incomplete (or has explicit user approval to jump priority — link to confirmation)

**Sprint this PR targets:** _____

---

## 4. Data-integrity touch (required)

<!-- Required for every PR. Be honest. -->

Does this PR touch any of:
- [ ] Recalculate Balances flow (C01)
- [ ] Exports — PDF / JSON / XLSX (C02)
- [ ] Schema — any field add/remove/rename (C03)
- [ ] Sync — Firestore push/pull/conflict (C03)
- [ ] Repayment records — loan or debt payments (C01)
- [ ] None of the above

**If any box above is checked, regression test added:** [ ] Yes / [ ] No / [ ] N/A — justify: ___

**For Recalculate touches:** unit test loads fixture with non-zero `paid` values and asserts they survive after recalc — [ ] yes / [ ] not applicable

**For schema touches:** round-trip test (export → wipe → restore → diff = empty) passes — [ ] yes / [ ] not applicable

---

## 5. Design system compliance (required for UI changes)

<!-- If this PR has no UI change, write "N/A — non-UI" and skip the rest. -->

**Archetype:**
- [ ] Home (Dashboard reference)
- [ ] Report (Interest Income reference)
- [ ] Sheet (Add Transaction reference)
- [ ] Dialog (Wipe ALL Data reference)
- [ ] Settings-style
- [ ] Static content (About reference)
- [ ] N/A

**Reference screen this PR most resembles:** _______

**Design tokens used (paste the relevant ones):**
```
Colors: FynloColors.___, FynloColors.___
Typography: FynloTypography.___, FynloTypography.___
Spacing: FynloSpacing.___
Shape: FynloShape.___
```

**Design system sections applied:**
- [ ] §3 (Color tokens)
- [ ] §4 (Typography)
- [ ] §5 (Spacing)
- [ ] §6 (Shape)
- [ ] §8 (Number/Date formatting)
- [ ] §9.{N} (specific component patterns) — list: ___
- [ ] §11 (Color semantics)

**Anti-patterns checklist (§16) — confirm none introduced:**

- [ ] No two FABs on a single screen
- [ ] No emoji in dialog string resources (until C09 verified)
- [ ] No decimal places on display rupee amounts
- [ ] No raw integer amounts in UI
- [ ] No category bleed across Income/Expense
- [ ] No free-text fields for known-finite values (account names, categories)
- [ ] Max 1 action icon per row + overflow
- [ ] Disabled buttons show why they're disabled
- [ ] No "Add First X" CTA + FAB simultaneously
- [ ] No hardcoded version strings in exports
- [ ] Date strings use separators
- [ ] No mixing of epoch ms and ISO date in same record
- [ ] Plurals use pluralize()
- [ ] No pure black `#000000` text
- [ ] No pure white `#FFFFFF` screen background
- [ ] Colors used semantically (no green check for "selected")
- [ ] Section headers plain bold (no green bullets)
- [ ] No sparkle ✨ on action buttons
- [ ] Empty insight rows with ₹0 hidden, not shown
- [ ] Negative numbers in `semantic_expense` color
- [ ] No FAB on Report screens
- [ ] No `type_hero` on Report screens (use `type_chart_hero`)

**Deviations from design system:**
- [ ] None
- [ ] Justified deviation: {explain + link to DESIGN_SYSTEM.md PR that documents the new pattern}

---

## 6. Testing (required)

**Tests added in this PR:**
- {test name 1} — what it asserts
- {test name 2} — what it asserts

**Tests modified:**
- {test name} — why

**Manual verification:**
- [ ] Verified on physical device (CPH2767 or other) — describe what you tested
- [ ] Verified on emulator
- [ ] Not applicable

**Screenshots / videos:** (drag-and-drop into the PR description)

Before:

After:

---

## 7. Lint & build status (required)

- [ ] `./gradlew detekt` passes (no NEW violations beyond baseline)
- [ ] `./gradlew lint` passes
- [ ] `./gradlew test` passes
- [ ] `./gradlew :app:assembleRelease` builds (don't ship, just verify)

**If new lint violations introduced, justify (or fix):**

---

## 8. Documentation updates (required)

<!-- Did this PR change behavior, schema, design, or governance? -->

- [ ] `PROJECT_STATE_FOR_AI.md` — updated journal entry for this PR
- [ ] `DESIGN_SYSTEM.md` — updated if new pattern introduced
- [ ] `UX_AUDIT_2026-05-25.md` — marked closed audit points as done
- [ ] `LINT_RULES.md` — added new rule if applicable
- [ ] `AI_AGENT_PROTOCOL.md` — updated if protocol changed
- [ ] `CHANGELOG.md` — entry added for user-facing change
- [ ] `README.md` / `ARCHITECTURE.md` — updated if structure changed
- [ ] N/A — no doc changes needed

---

## 9. Version & release (required)

**Target version:** _____ (e.g., 3.2.2, 3.3.0)
**`versionCode` bumped:** [ ] Yes / [ ] No (link to where)
**`versionName` bumped:** [ ] Yes / [ ] No

**Release channel after merge:**
- [ ] Internal testing only
- [ ] Open/closed alpha
- [ ] Production rollout

**Ship-blocker check:** Per `UX_AUDIT.md §5`, does this version meet all gates?
- [ ] Yes
- [ ] No — explain: {...}

---

## 10. Risk & rollback (required for P0/P1 work)

<!-- For P0 and P1 work, fill this in. For polish (P2), can write "Low risk — polish only" -->

**What could go wrong:**

**How to detect:**

**How to roll back:**

**Data migration involved:** [ ] Yes / [ ] No
- If yes: migration is **reversible** [ ] / **one-way** [ ] (justify)
- If yes: migration tested against {N} sample backups

---

## 11. Reviewer focus

<!-- Optional. Highlight specific areas you'd like the reviewer to scrutinize. -->

Please pay attention to:
- ...
- ...

---

## 12. Post-merge tasks

<!-- What needs to happen after this PR merges? -->

- [ ] Watch crash reports / Firebase Analytics for {hours/days}
- [ ] Update `PROJECT_STATE_FOR_AI.md` with merge outcome
- [ ] Close raw audit points {list}
- [ ] Mark cluster C__ as Done in `UX_AUDIT.md`
- [ ] Other: ___

---

## Reviewer checklist

<!-- For the reviewer to fill, not the author. -->

- [ ] Read all sections above; none skipped
- [ ] Verified cluster declaration matches actual changes
- [ ] Verified design system compliance claims by inspecting code
- [ ] Verified anti-patterns checklist by inspecting code
- [ ] Tests cover the change adequately
- [ ] Documentation updates are accurate
- [ ] Version bump matches semver intent (major/minor/patch)
- [ ] Risk/rollback plan is realistic
- [ ] CI is green (lint + tests + build)
- [ ] Approved
