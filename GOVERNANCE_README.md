# Fynlo Governance — How it all fits

**Last updated:** 2026-05-25
**Audience:** You (Avinash), future Claude/AI sessions, any human contributor

This document orients you to the governance system around Fynlo. It explains what each document does, how they interact, and what to do when adding new features, fixing bugs, or asking AI agents for help.

---

## The 7 governance artifacts

| # | File | Type | When you touch it |
|---|---|---|---|
| 1 | `PROJECT_STATE_FOR_AI.md` | State + journal | After every PR — append journal entry |
| 2 | `DESIGN_SYSTEM.md` | Rules (visual) | When introducing a new pattern, archetype, or token |
| 3 | `UX_AUDIT_2026-05-25.md` | Plan | When closing a cluster — mark Done |
| 4 | `AI_AGENT_PROTOCOL.md` | Rules (AI behavior) | When the protocol needs updating |
| 5 | `LINT_RULES.md` | Rules (code) | When adding a new automated check |
| 6 | `.github/PULL_REQUEST_TEMPLATE.md` | Process | When the PR review checklist needs updating |
| 7 | `.github/workflows/checks.yml` | Automation | When CI checks need updating |

---

## How they work together

```
                   ┌─────────────────────────────────┐
                   │  PROJECT_STATE_FOR_AI.md §0     │  ← Read first
                   │  "Required reading"             │
                   └────────────┬────────────────────┘
                                │ points to
              ┌─────────────────┼─────────────────────┐
              ▼                 ▼                     ▼
     ┌──────────────┐  ┌─────────────────┐  ┌──────────────────┐
     │ DESIGN_      │  │ UX_AUDIT_       │  │ AI_AGENT_        │
     │ SYSTEM.md    │  │ 2026-05-25.md   │  │ PROTOCOL.md      │
     │              │  │                 │  │                  │
     │ • Tokens     │  │ • 22 clusters   │  │ • Required reads │
     │ • Archetypes │  │ • P0/P1/P2/P3   │  │ • Cluster decl   │
     │ • Anti-      │  │ • Sprint plan   │  │ • DI escalation  │
     │   patterns   │  │ • Ship gates    │  │ • Output format  │
     └──────┬───────┘  └────────┬────────┘  └────────┬─────────┘
            │                   │                    │
            │ enforced by       │ tracked in         │ enforced by
            ▼                   ▼                    ▼
     ┌──────────────┐  ┌─────────────────┐  ┌──────────────────┐
     │ LINT_RULES   │  │ PR_CHECKLIST    │  │ checks.yml       │
     │ (Detekt)     │  │ (review)        │  │ (CI gate)        │
     └──────────────┘  └─────────────────┘  └──────────────────┘
```

The flow:

1. **`PROJECT_STATE_FOR_AI.md §0`** is the entry point. Tells any reader (human or AI) what to read.
2. **`DESIGN_SYSTEM.md`** describes the rules for what code/UI should look like.
3. **`UX_AUDIT_2026-05-25.md`** describes what's currently broken and how to fix it in priority order.
4. **`AI_AGENT_PROTOCOL.md`** tells AI agents how to operate on the codebase.
5. **`LINT_RULES.md`** codifies the design system into Detekt/Lint rules (automated).
6. **`PR_CHECKLIST.md`** asks human reviewers to verify compliance (manual).
7. **`.github/workflows/checks.yml`** runs lint + tests + protocol checks on every PR (automated gate).

If a rule is in the design system but not yet in lint, it's enforced by code review. If a rule is in lint, it's enforced by CI. If a rule is in CI, the PR cannot merge while violating it.

---

## Setup — drop these into the repo

```bash
# At Fynlo repo root:
cp /path/to/downloads/DESIGN_SYSTEM.md ./DESIGN_SYSTEM.md
cp /path/to/downloads/UX_AUDIT_2026-05-25.md ./UX_AUDIT_2026-05-25.md
cp /path/to/downloads/AI_AGENT_PROTOCOL.md ./AI_AGENT_PROTOCOL.md
cp /path/to/downloads/LINT_RULES.md ./LINT_RULES.md
cp /path/to/downloads/GOVERNANCE_README.md ./GOVERNANCE_README.md

# Prepend §0 preamble to PROJECT_STATE_FOR_AI.md
# (manually copy contents of PROJECT_STATE_section_0_preamble.md
#  to the TOP of PROJECT_STATE_FOR_AI.md, before existing §1)

# Set up PR template
mkdir -p .github
cp /path/to/downloads/PR_CHECKLIST.md .github/PULL_REQUEST_TEMPLATE.md

# Set up CI workflow
mkdir -p .github/workflows
cp /path/to/downloads/checks.yml .github/workflows/checks.yml

# Commit
git add -A
git commit -m "chore: add governance artifacts (design system, audit, AI protocol, lint, CI)"
git push origin master
```

GitHub will automatically use `.github/PULL_REQUEST_TEMPLATE.md` as the default PR body. It will automatically run `.github/workflows/checks.yml` on every PR.

---

## How to use this in different scenarios

### Scenario A: Bug report from yourself

> "I noticed the FAB overlaps the last row on the Loans tab."

1. Check `UX_AUDIT_2026-05-25.md` — is this a known issue?
   - Yes → it's in cluster C06 (FAB overlap), already planned for Sprint 2.
2. If you want to fix it now (Sprint order says no, since Sprint 1 isn't done):
   - Either: wait for Sprint 2
   - Or: explicitly override priority and document why
3. When you do fix it: PR follows the template, declares "Closes part of C06", updates UX_AUDIT to mark C06 Done.

### Scenario B: Asking Claude for help

> "Claude, fix the Loans tab FAB overlap."

Expected Claude response:
```
═══════════════════════════════════════
SESSION START — FYNLO PROTOCOL ACK
═══════════════════════════════════════
☑ Read PROJECT_STATE_FOR_AI.md (latest journal: ...)
☑ Read AI_AGENT_PROTOCOL.md v1.0
☑ Read DESIGN_SYSTEM.md v1.1
☑ Read UX_AUDIT_2026-05-25.md
☑ Identified cluster: C06 (FAB overlap system-wide)
☑ Design system sections: §5.2 (FAB-overlap rule), §9.7 (FAB pattern)
☑ Anti-patterns avoided: §16.1

Task: Fix FAB overlap on Loans tab.

⚠ Priority check: Sprint 1 (C01 + C02 + C05) is not yet complete.
This is Sprint 2 work. Recommended to complete C01 first.
Proceeding only if you confirm priority override.
═══════════════════════════════════════
```

If Claude doesn't produce that block, ask "where's the protocol acknowledgement?" — Claude should self-correct.

### Scenario C: New feature request

> "Let's add receipt OCR to the Expenses tab."

1. Check `UX_AUDIT_2026-05-25.md` — yes, it's in C22 (P3 backlog).
2. Claude should refuse to proceed until P0 ship-blockers are done:
   > "This is C22 (P3 backlog). C01 (P0 ship-blocker) is still open. Recommend completing C01 first."
3. If you confirm priority override: PR follows template, declares "New work outside cluster" or "Promotes C22 sub-item to active work", justifies why.

### Scenario D: Spotting a new bug not in the audit

> "I just noticed the Investment screen shows ₹0.00 for closed FDs."

1. Add it to the audit:
   - Open `UX_AUDIT_2026-05-25.md`
   - Find the relevant cluster (likely C14 Invest redesign or C16 color semantics)
   - Add as a raw audit point with next number
2. Then fix it (or schedule it). PR declares "Closes new audit point #368 (added in this PR), part of C14".

### Scenario E: Want to introduce a new design pattern

> "I want to add a 'comparison view' showing two months side-by-side."

1. This isn't in `DESIGN_SYSTEM.md`. Don't invent it inline.
2. Open a PR that ONLY extends `DESIGN_SYSTEM.md`:
   - Add §9.16 "Comparison view (Report archetype variant)"
   - Define the tokens, layout, behavior
   - Get the pattern reviewed
   - Merge the doc change
3. Open a second PR that implements the feature using the documented pattern.

### Scenario F: Lint rule needs updating

> "The current FY004 lint rule false-positives on test fixtures."

1. Open a PR that updates `LINT_RULES.md` — clarify exception scope
2. Update the rule implementation in `lint-rules/`
3. Regenerate baseline if needed
4. Same PR or follow-up: update PR_CHECKLIST.md if relevant

---

## Versioning the governance docs

Major changes bump the version number at the top of the doc:

- `DESIGN_SYSTEM.md` — v1.0 → v1.1 → v2.0
- `AI_AGENT_PROTOCOL.md` — v1.0 → v1.1
- `UX_AUDIT_YYYY-MM-DD.md` — keep dated; old audits archived in `docs/audits/`

Minor changes (typo fixes, clarifications) don't bump version. Add a date stamp.

---

## When governance gets in the way

The protocol exists to prevent regressions in a personal-finance app where mistakes are costly. But governance can become bureaucracy if not pruned.

**Signs that governance is over-burdened:**

- You routinely skip the protocol acknowledgement because "this is a 1-line change"
- PRs sit in review because filling the template is harder than writing the code
- AI agents refuse so much that you stop using them

**Mitigations:**

- For 1-line typo fixes: a single-line PR description that says "Typo. No cluster. No DI touch. No UI." is acceptable.
- For polish PRs (P2): the data-integrity section can say "N/A — UI polish only".
- The protocol's strictness is calibrated for the current risk (open P0). Once Sprint 1 is done and shipped, some checks can be relaxed (you'll edit `AI_AGENT_PROTOCOL.md §0.4` to remove the C01 warning).

The goal is **safety with reasonable friction**, not maximum friction.

---

## What to do when this conversation ends

After this Claude session ends, the chat context is gone. The artifacts live on:

- All files in `/mnt/user-data/outputs/` are downloadable now
- Once committed to your repo, they persist forever
- Future Claude sessions will read them per the protocol

To verify it's working: start a new Claude session, paste a Fynlo question, and check that Claude's first response begins with the protocol acknowledgement block. If not, paste this:

> "Read PROJECT_STATE_FOR_AI.md §0 in the repo and follow the protocol."

Claude will get on track.

---

## Maintaining this system

Every 3-6 months or after major milestones (e.g., Sprint 1 ships, Sprint 6 ships):

1. **Review `UX_AUDIT_*.md`** — mark closed clusters, possibly archive and start a new audit
2. **Review `DESIGN_SYSTEM.md`** — promote v1.1 → v1.2 if patterns emerged
3. **Review `AI_AGENT_PROTOCOL.md`** — relax checks no longer needed, tighten checks for new risks
4. **Review `LINT_RULES.md`** — add rules that would have caught recent bugs
5. **Review `PR_CHECKLIST.md`** — remove checklist items that always pass; add for new failure modes
6. **Review `.github/workflows/checks.yml`** — same

The goal is to keep the system current with reality, not to preserve it as a museum piece.

---

## Final word

These docs are an investment. The payoff comes when:

- A new feature ships without breaking an old one
- An AI agent produces code that compiles AND fits the design AND doesn't violate priority
- A user opens Fynlo six months from now and finds it more coherent than today

You'll know the system is working when "did Claude/Cursor/Copilot follow the rules?" is a yes by default, not a hope.

---

**End of GOVERNANCE_README.md**
