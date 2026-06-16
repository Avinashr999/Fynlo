# Fynlo Ledger Pro Redesign

Date: 2026-06-16
Status: Proposed

## Goal

Make Fynlo feel like one coherent finance product across Reports, Investments, add/edit forms, and hamburger-menu utility screens.

The current app is stable and usable, but some areas feel visually disconnected:

- Reports has uneven empty space and a loose tile grid.
- Investment is dense and tracker-like compared with the calmer dashboard style.
- Add/edit dialogs feel older than the rest of the app.
- Secondary screens do not always share the same action placement and spacing rules.

The redesign should keep Fynlo's core identity: clean surface, emerald primary action, restrained risk colors, large readable money values, and fast local-first interactions.

## Design Direction

Use a "Ledger Pro" language:

- White and soft gray surfaces.
- Emerald for primary actions and positive money movement.
- Red only for losses, overdue, destructive actions, or negative cash movement.
- Amber only for warnings.
- Fewer decorative cards, more structured sections.
- Dense but readable rows for repeated financial objects.
- Bottom sheets for creation/editing, not old centered form popups.

Do not introduce a flashy theme, gradient-heavy visuals, or decorative layouts. Fynlo is a money app. Trust and scan speed matter more than decoration.

## Pass 1: Reports Hub

Replace the current loose report tile grid with a more useful report hub.

Structure:

- Header: `Reports`, with a short subtitle.
- Period selector: compact horizontal chips.
- Summary band: three compact metrics for `Net Worth`, `P&L`, and `Money Flow`.
- Report groups:
  - Business health: P&L Statement, Monthly Summary.
  - Wealth: Net Worth, Interest Income, Investments if added later.
  - Cash movement: Money Flow.
  - Loans and debt: Debt Payoff, EMI Calculator.
- Each report entry should show:
  - icon
  - report name
  - one-line explanation
  - preview metric
  - chevron or clear tap affordance

Expected outcome:

- Less empty space.
- Clearer grouping.
- Easier scanning.
- Report tiles feel like part of the app, not a separate pastel launcher.

## Pass 1: Investment Screen

Redesign Investment as a portfolio dashboard.

Structure:

- Portfolio hero:
  - portfolio value
  - gain/loss amount and percent
  - invested amount and holdings count
  - CAGR/XIRR when available
- Allocation:
  - compact segmented bar
  - top 3 allocation rows
  - optional "Other" grouping for small positions
- Holdings:
  - compact rows by default
  - each row shows name, type, current value, gain/loss
  - secondary details are hidden until expand/tap
  - actions are consistent: Update Value, Withdraw, More

Avoid showing every metric for every holding all the time. The current screen is information-rich, but visually heavy. The redesign should let users scan the portfolio first and drill into a holding second.

Expected outcome:

- Investment feels connected to Dashboard and Reports.
- Less vertical noise.
- Better first-screen hierarchy.
- Clearer action placement.

## Pass 2: Modern Add/Edit Forms

Introduce a shared modern form surface for add/edit flows.

Preferred pattern:

- Full-height modal bottom sheet on mobile.
- Header with title, short context, and close button.
- Grouped sections:
  - Basic details.
  - Money/account source.
  - Interest/schedule, only when relevant.
  - Notes.
- Sticky bottom action bar:
  - Cancel or secondary action.
  - Save/Add primary action.
- Inline validation near the field.
- Success feedback through the existing feedback event/toast path.

Initial migration targets:

- Add/Edit Investment.
- Add Lending/Customer.
- Add Debt.
- Add Transaction.

Later migration targets:

- Budget.
- Recurring.
- Account balance edit.
- Settings import/export dialogs where appropriate.

Expected outcome:

- Forms feel native and current.
- Long forms stop feeling cramped.
- Save actions are always reachable.
- Validation is visible without hunting.

## Pass 3: Secondary Screens

Align hamburger-menu screens to the same structure:

- Same header rhythm.
- Same empty-state component.
- Same list row density.
- Same action placement.
- No duplicate action buttons.
- Enough bottom inset for FAB and bottom navigation.

Screens:

- Settings.
- Profile and Security.
- Budgeting.
- Savings Goals.
- Contact Book.
- Recurring.
- Projects.
- EMI Calculator.
- About.

Expected outcome:

- The app feels designed as one product.
- Secondary screens do not feel like separate templates.

## Implementation Order

1. Reports hub redesign.
2. Investment screen redesign.
3. Shared modern form sheet component.
4. Migrate Investment, Lending, Debt, and Transaction forms.
5. Secondary screen alignment pass.
6. Manual device QA.
7. Version bump, release build, install, and AAB verification.

## Verification

Run after each implementation pass:

```powershell
.\gradlew.bat :app:compileProdDebugKotlin --no-daemon
.\gradlew.bat :app:testProdDebugUnitTest --no-daemon
```

Before release:

```powershell
.\gradlew.bat :app:bundleProdRelease --no-daemon --console=plain
.\gradlew.bat :app:installProdRelease --no-daemon --console=plain
```

Manual smoke:

- Open Dashboard, Reports, Investment, Loans, Expenses.
- Open at least one add/edit form for investment, lending, debt, and transaction.
- Check no content sits under bottom navigation or FAB.
- Check success feedback appears after save/delete/update.
- Check logcat for fatal, Room, SQLite, and migration errors.

## Non-Goals

- No color-brand rewrite.
- No animation-heavy redesign.
- No full navigation restructure.
- No business-logic changes unless a UI flow exposes a correctness bug.
- No Play Store release without a final manual device pass.

## Approval Check

This spec is ready for implementation if the user agrees with:

- Reports becomes grouped and metric-driven.
- Investment becomes a cleaner portfolio dashboard.
- Old centered add/edit forms move toward full-height modal bottom sheets.
- The theme stays calm, emerald-led, and finance-focused.
