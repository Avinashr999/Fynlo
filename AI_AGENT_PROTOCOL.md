# AI Agent Protocol — Fynlo

**Version:** 1.0
**Last updated:** 2026-05-25
**Audience:** Claude (chat, Code, agents), Cursor, GitHub Copilot, any other AI assistant working on the Fynlo codebase
**Authority:** This protocol is binding. Violating it produces work that gets rejected.

---

## 0. Why this document exists

Fynlo has three governing documents:

- `PROJECT_STATE_FOR_AI.md` — what the project is, what's been done
- `DESIGN_SYSTEM.md` — visual & interaction rules
- `UX_AUDIT_2026-05-25.md` — known issues, priority order, sprint plan

AI agents (including Claude) start each session with no memory. Without a forcing function, you'll suggest changes that contradict rules already decided. This document is that forcing function.

If you are an AI agent reading this, **follow this protocol exactly**. If you can't or won't, stop and tell the user before producing any code.

---

## 1. Required reading at session start

Before suggesting ANY code change, design change, schema change, or new feature, you MUST read these files in order:

1. **`PROJECT_STATE_FOR_AI.md`** — every section, especially §0 and the most recent journal entries
2. **`DESIGN_SYSTEM.md`** — §1 (archetypes), §11 (color semantics), §16 (anti-patterns) at minimum; full read recommended
3. **`UX_AUDIT_2026-05-25.md`** — §2 (cluster list), §3 (priority buckets), §5 (ship-blocking matrix), §8 (the critical paragraph)
4. **The specific files you are about to modify** — view them via your file-reading tool, do not rely on memory

If any of these files don't exist yet (fresh repo state), the user is asking you to create them or migrate to a different system. Confirm before proceeding.

### 1.1 Confirmation requirement

Your **first response in any new session** must begin with this exact confirmation block:

```
═══════════════════════════════════════
SESSION START — FYNLO PROTOCOL ACK
═══════════════════════════════════════
☑ Read PROJECT_STATE_FOR_AI.md (sections: {list})
☑ Read DESIGN_SYSTEM.md v{version}
☑ Read UX_AUDIT_2026-05-25.md
☑ Identified relevant cluster(s): {C__, C__, or "no cluster"}
☑ Identified affected design system sections: {§__, §__}
☑ Identified anti-patterns to avoid: {16.N, 16.N, or "none applicable"}

Task understanding: {one-sentence restatement}

Proceeding.
═══════════════════════════════════════
```

If you cannot fill in any line, stop and ask the user.

If the user pushes back ("just skip this and code"), the protocol still applies. Do the reading silently, fill in the block silently, but produce work that conforms. Never produce work that contradicts the docs because the user asked you to skip the check.

---

## 2. The cluster-or-explain rule

Every change you propose must declare its relationship to the audit:

- **Closing an existing cluster:** "This addresses cluster C04 (Smart defaults). Raw audit points closed: #14, #15, #167."
- **Partial cluster work:** "This is part of cluster C12 (Loans tab redesign). Specifically subtasks 3-5 of 10."
- **New work outside any cluster:** "This is new work not in the current audit. Justification: {reason}. Recommend adding to UX_AUDIT under cluster C__ or as a new cluster."
- **Hotfix / urgent:** "This is a hotfix for {symptom}. Likely root cause cluster: C__. Not blocking on cluster work."

Never produce a code change without one of the above. If the user asks for something that doesn't fit, propose adding it to the audit first.

---

## 3. The data-integrity escalation

Any change touching these subsystems requires extra scrutiny:

- **Recalculate Balances** — touches C01 (the destruction bug). Must include a regression test that loads a fixture with non-zero `paid` values and asserts they survive.
- **Exports** (PDF / JSON / XLSX) — touches C02. Must auto-recalc before export.
- **Schema changes** (any new field, type change, ID strategy change) — touches C03. Must include migration logic AND a round-trip test (export → wipe → restore → diff = empty).
- **Sync** (Firestore push/pull, conflict resolution) — touches C03. Must include `updatedAt` correctness assertion.
- **Repayment records** (loan or debt payments) — touches C01. Must store as separate records, never as a cumulative field on parent.

If your change touches any of these and you do NOT include a regression test, your PR will be rejected by CI (when implemented) or rejected by review (until then).

State this explicitly in your output: "Data-integrity touch: {yes/no}. Regression test included: {yes/no/N/A}. Justification if no: {...}."

---

## 4. The design system compliance check

Every UI change you propose must end with a compliance block:

```
─── Design system compliance ───
Archetype: {Home / Report / Sheet / Dialog / Settings-style}
Reference: {Dashboard / Interest Income / Debt Payoff / About}

Tokens used:
- Colors: {FynloColors.___, FynloColors.___}
- Type: {FynloTypography.___}
- Spacing: {FynloSpacing.___}
- Shape: {FynloShape.___}

Patterns applied: §{N}, §{N}
Anti-patterns avoided: §16.{N}, §16.{N}

Deviations from design system: {none / explanation + justification}
─────────────────────────────────
```

If you have any deviation, you must EITHER:
- Update `DESIGN_SYSTEM.md` to document the new pattern (preferred for genuinely new patterns)
- Refactor your change to fit the system (preferred for one-off temptations)

You may not silently deviate.

---

## 5. Priority discipline

The audit has a strict priority order:

- **P0** — ship blockers (C01, C02, C03a, C05)
- **P1** — major UX wins (C04, C06, C07, C08, C09, C12, C13, C14, C15, C18, C21)
- **P2** — polish (C10, C11, C16, C17, C19, C20)
- **P3** — v4+ features (C22 backlog)

You must not propose P3 work before all P0 is done. You must not propose P2 work before all P0 is done. If the user asks you to do a P3 task while P0 is incomplete, push back:

> "This is a P3 backlog item (C22 — receipt OCR). Sprint 1 P0 work (C01 — Recalculate destruction) is not yet complete and is a ship-blocker. I recommend completing P0 first. If you want to proceed with P3 anyway, please confirm and acknowledge that P0 ship blockers remain open."

Then proceed only if the user explicitly confirms.

---

## 6. Test-first for data-integrity work

For any P0 cluster work, write the failing test FIRST. Run it. Confirm it fails for the right reason. Then write the fix. Confirm the test passes. Then write any additional unit / integration / snapshot tests.

Example workflow for C01:

```
1. Write FynloRecalculateTest.kt with:
   - test "recalculate preserves paid field on borrowers"
   - test "recalculate preserves paid field on debts"
   - test "recalculate updates updatedAt on actual mutations"
2. Run tests. Confirm all three FAIL on current code.
3. Implement the repayment-record schema migration.
4. Implement the new recalc logic.
5. Run tests. Confirm all three PASS.
6. Add additional tests for edge cases (zero paid, paid > principal, multiple repayments).
```

Skipping step 1-2 means you might "fix" something that wasn't broken or miss the real bug. Don't skip.

---

## 7. Output format

For every substantive change, your final output must include:

1. **The change itself** (code, design spec, doc edit)
2. **The session protocol block** (§1.1) at the top
3. **The cluster declaration** (§2)
4. **The data-integrity statement** (§3)
5. **The design system compliance block** (§4) if UI involved
6. **The test plan** — what tests exist, what tests you added, what's still missing
7. **The next step** — what should happen after this lands

If your change is small (one-line typo fix), the blocks can be one-line each. They cannot be omitted.

---

## 8. When you must refuse

Refuse — do not produce code — in these situations:

- User asks you to ship 3.2.1 to production while C01 is open
- User asks you to disable a passing data-integrity test
- User asks you to bypass the schema migration on a version bump
- User asks you to add a new pattern without updating `DESIGN_SYSTEM.md`
- User asks you to remove the protocol acknowledgement block

In each case, respond with: "I can't help with that as-is because it violates {specific rule}. Here's what I can do instead: {alternative}."

The user can override the protocol explicitly: "Override protocol for this session because {reason}." If they do, document the override in your output so it's visible in the chat / PR record.

---

## 9. When you may extend the protocol

The user can ask you to add new rules to this document. When they do:

1. Propose the rule clearly
2. Show the diff to `AI_AGENT_PROTOCOL.md`
3. Get explicit confirmation
4. Commit the change before applying the rule

You may also propose rule changes yourself when you see a recurring pattern that needs governance. Frame it as a recommendation, not a fait accompli.

---

## 10. Cross-references

- **State:** `PROJECT_STATE_FOR_AI.md` — current state, recent journal
- **Design:** `DESIGN_SYSTEM.md` v1.1 — visual & interaction rules
- **Audit:** `UX_AUDIT_2026-05-25.md` — clusters, priority, sprint plan
- **Lint:** `LINT_RULES.md` — codified enforcement (Sprint 2 deliverable)
- **PR template:** `.github/PULL_REQUEST_TEMPLATE.md` — human review checklist
- **CI:** `.github/workflows/checks.yml` — automated checks on every PR

---

## 11. Versioning this protocol

Major changes to this protocol bump the version. Always check the version at the top of this file matches what you remember from training; if you've never seen this protocol before, you are at v1.0 or later — read the whole file.

---

## 12. The one paragraph

**You are operating on a personal finance app that handles real money. Mistakes here aren't aesthetic — they erase the user's payment history, ship lies to their accountant, and break their trust in the tool they use daily. The protocol is strict because the consequences of skipping it are bad. Follow it.**

---

**End of AI_AGENT_PROTOCOL.md v1.0**
