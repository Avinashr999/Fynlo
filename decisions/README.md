# Architecture Decision Records (ADRs)

This directory captures **decisions that compound over time** — calls that
affect more than one cluster, lock in invariants other code relies on, or
that future-me will want to know the *reasoning* behind, not just the
outcome.

Lightweight tactical work (one-off cleanups, single-file refactors, CI
fixes) belongs in `UX_AUDIT` clusters or `§9 Infrastructure backlog` — ADRs
are for the kind of call where "why did we do it *this* way?" still matters
six months from now.

## Format

One markdown file per decision, named `YYYY-MM-DD-short-slug.md`. The body
follows the Michael Nygard format:

- **Status** — Proposed · Accepted · Deprecated · Superseded by `YYYY-MM-DD-...`
- **Context** — Forces in play, what we'd been doing, what hurts
- **Decision** — What we're going to do, plainly, unhedged
- **Consequences** — What gets easier, harder, or locked in
- **Alternatives considered** *(optional)* — paths not taken, and why

The date in the filename is the *decision* date, not the implementation
date. If a decision is later revised, **don't rewrite history in place** —
write a new ADR and mark the old one `Superseded by <new file>`.

## Index

| Date | Title | Status | Affects |
|---|---|---|---|
| 2026-05-26 | [C01 fix strategy — Payments as the single source of truth](./2026-05-26-c01-fix-strategy.md) | Proposed | C01, C02, C03, INF04 |
