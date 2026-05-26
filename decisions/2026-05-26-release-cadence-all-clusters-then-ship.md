# Release cadence — complete all clusters, then ship once

**Date:** 2026-05-26
**Status:** Accepted
**Affects clusters:** every cluster in `UX_AUDIT_2026-05-25.md` (C01–C22 + C03b) and the entire `RELEASE_PROTOCOL.md` shipping pipeline
**Sprint:** all sprints
**Supersedes:** —

---

## Context

The original release narrative — encoded in `RELEASE_PROTOCOL.md §1` (release channels), `§4` (release procedure), `§9` (channel-specific notes), and the audit's `§4` sprint plan — assumes **incremental releases**: each sprint closes one or more clusters, ships through Internal Testing → Closed Alpha → Open Beta → Production with staged rollouts, and the next sprint starts from a stable shipped baseline. The protocol's halt criteria (`§6`), rollback procedure (`§7`), and per-channel cadences (`§9.4`) all only make sense in that incremental model.

Several facts shift the calculus for *this* project:

1. **Fynlo is not on Play Store.** There is no public user base. C01's destructive Recalculate bug, while severe in absolute terms, is currently affecting only the local-dev install on the author's device — which already has the fix on `master`. No external users are losing data while the fix sits unpushed-to-production.
2. **The audit's 22 clusters span a wide arc.** C01–C09 are bug/data-integrity work; C12–C15 + C21 are visual-design redesigns; C22 is a v4+ feature backlog (import contacts, OCR, voice search, snowball/avalanche, AI categorization, dark mode, encrypted backups, NAV auto-import, bank statement parsing). Shipping these incrementally would mean a sequence of small public releases each with its own staged rollout, halt-criteria watching, and postmortem overhead — for an app whose first-impression matters and whose users haven't formed expectations yet.
3. **Release management has real fixed cost.** Each promotion to Production runs a 24h+72h+staged-rollout clock, plus crash-rate watching, plus changelog/release-notes/in-app-banner authorship, plus the per-release smoke test (`§3.5`) and macrobench (`§3.4`) gates. At 22 clusters across 6 sprints, that's potentially 6 × (smoke + macrobench + staged rollout) of release overhead for an audience of zero.

The author has chosen to absorb the cost of one big release rather than the cost of six small ones, and to use the first public Play Store appearance as the *v1.0-feature-complete* moment rather than as a staged debut.

## Decision

**No upload to Play Console happens until every cluster in `UX_AUDIT_2026-05-25.md` is closed.** Specifically:

- All 4 P0 clusters: C01 (closed), C02, C03a, C05.
- All 12 P1 clusters: C04, C06, C07, C08, C09, C12, C13, C14, C15, C18, C21, C03b.
- All 5 P2 clusters: C10, C11, C16, C17, C19, C20.
- C22 — the P3 v4+ feature backlog — **explicitly included**, despite the audit itself marking C22 as "Post-Sprint 6 (v4)". The author treats the audit's deferral as the audit's recommendation, not a binding choice.

Development continues on `master` with normal commit/test/CI hygiene. The existing `RELEASE_PROTOCOL.md` pipeline becomes **dormant** — it remains the authoritative document for *how* a release happens, but does not gate ongoing development work. Cluster completion is the only release trigger.

Internal milestone version bumps (e.g., the current `versionName = "3.2.2"`, `versionCode = 125` for the C01 closure) are kept as development markers and may continue per-cluster, but do **not** trigger any `:app:bundleProdRelease` → Play Console flow. When the final cluster lands, the author picks a public versionName (likely `1.0.0` for the first-ever Play Store release, or whatever fits the chosen marketing narrative) and runs the full `RELEASE_PROTOCOL §4` procedure once.

## Consequences

**Easier**

- **One smoke test + one macrobench, not six.** `RELEASE_PROTOCOL §3.4`/§3.5 gates run once at the end, against the actual ship-ready code. No accumulating drift between gate runs and the build that finally ships.
- **No halt-criteria scaffolding.** `§6` (Crash rate > 2%, P0 from external user, rating drop, sync outage) is structurally inapplicable while the app is closed. The team does not need to staff up for rollback responses or postmortem windows.
- **First impression is the polished impression.** Users land on a feature-complete app rather than incrementally watching it grow capabilities. Aligns with how the visual/UX redesign clusters (C12–C15) interact — partial redesigns shipped over multiple releases visibly accumulate inconsistency; one full redesign ships clean.
- **Per-release artifact churn (CHANGELOG sub-entries, release notes drafts, in-app banners) collapses** into one final document set, drafted with full hindsight.

**Harder**

- **Timeline is unbounded.** C22 alone has ~15 feature items, several of which are genuine R&D (AI categorization, receipt OCR, bank statement parsing) and not just engineering work. There is no calendar commitment to a launch date encoded in this decision; the trigger is feature-complete, not time-elapsed. The author should reread this paragraph annually and decide whether the strategy is still serving them.
- **No real-user feedback loop during construction.** Bugs, UX dead-ends, performance issues that only surface under real-world data and devices will not be caught until launch. Compensating measures: internal device matrix testing, dummy data seeders (already in place), Firebase Test Lab for crash detection on emulators when CI integration lands (INF05/INF06).
- **Re-running the release gates at launch time costs more.** By the time clusters 2–22 close, the C01 smoke test and macrobench results from 2026-05-26 are stale. Everything in `release_notes/3.2.2.md`'s checklist needs re-running against the all-clusters-complete codebase.
- **The C01 fix is "saved up" rather than shipped.** Justified by the no-Play-Store-users premise. If that premise changes — e.g., the author decides to do a private alpha — this ADR must be revisited first.

**Locked in** *(future ADRs would need to supersede this one to undo)*

- The `RELEASE_PROTOCOL.md` pipeline is dormant for as long as this ADR is `Accepted`. Edits to the protocol are still allowed (it's reference material), but executing `§4` is gated on cluster completion.
- Internal version bumps are decoupled from public shipping. The author may bump `versionName` per cluster closure, hold all such builds locally, and pick a new public versionName at launch.
- `release_notes/<version>.md` files for dormant milestones (`3.2.2.md`, future per-cluster files) are development-record artifacts, not Play Store copy. The Play Store copy is drafted at launch time against the consolidated state.

## Alternatives considered

**A. Original incremental model** — ship per sprint, staged rollout each time, as `RELEASE_PROTOCOL §4` + `§9.4` describe. *Rejected:* no Play Store user base today means staged rollouts are gating against nothing. Six rounds of release overhead for an audience of zero.

**B. Ship after P0+P1+P2, exclude C22** — release at "all bug/UX work done," let C22's v4+ features ship in subsequent releases. *Rejected by the author:* the chosen scope is "literally everything including C22." Documented for clarity in case the author later revises (this ADR would then be superseded by a "ship at P0+P1+P2" follow-up).

**C. Closed Alpha during construction** — invite a small group of testers via Play Console's Closed Alpha track, get the C01 fix in their hands, gather real-world feedback during cluster 2–22 work. *Not chosen now but worth flagging for revisit:* if the author later wants real-world feedback without committing to a public launch, Closed Alpha is the appropriate mid-point. This ADR does not foreclose adding it later — a new ADR can reopen the question.

**D. Time-boxed release** — pick a calendar date (e.g., end of Q3), ship whatever clusters are closed by then. *Not chosen:* the author has prioritized completeness-over-cadence. Recorded as a potential rescue lever if the unbounded timeline becomes a problem.

## Cross-references

- `UX_AUDIT_2026-05-25.md` — §2 (cluster list), §3 (priority buckets), §4 (sprint plan), §C22 (deferred backlog)
- `RELEASE_PROTOCOL.md` — `§1`, `§4`, `§6`, `§9` — *the protocol this ADR puts into dormancy until cluster completion*
- `PROJECT_STATE_FOR_AI.md` — `§0.4` (the "one thing to remember" section, updated to point at this ADR), `§6` (journal, updated to reflect held-release strategy)
- `release_notes/3.2.2.md` — kept as a development-milestone record, not a release artifact
- `CHANGELOG.md [3.2.2]` — Status remains "Draft" indefinitely under this ADR; will flip to "Shipped" only at the all-clusters launch
- `decisions/2026-05-26-c01-fix-strategy.md` — sibling decision, still Accepted; this ADR doesn't change C01's technical resolution, only when it reaches users
