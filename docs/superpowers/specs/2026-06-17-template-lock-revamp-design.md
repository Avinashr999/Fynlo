# Fynlo Template-Lock Revamp Design

## Source Of Truth

The visual target is the approved template shown in the user-provided screenshots from `2026-06-17 091353` and `2026-06-17 091407`.

The app should feel like that template from the first launch screen through the final legal/disclaimer screens: compact, premium, calm, financial, and highly consistent.

## Design Direction

- Use a warm off-white canvas instead of plain white where possible.
- Use deep emerald as the primary brand surface and action color.
- Use black-carbon typography with muted green-gray supporting text.
- Prefer compact cards, tight rows, clear sections, and restrained shadows.
- Keep rounded corners controlled: mostly 12-22 dp depending on hierarchy.
- Use icon tiles, count labels, and section headers consistently.
- Keep bottom navigation embedded and premium, matching the template feel.
- Use bottom sheets/forms that look like the investment form template.

## Native Adaptation Rules

- Do not force exact HTML pixel metrics where Android text scaling, dynamic data, or device size would break readability.
- Keep all touch targets usable on phones.
- Preserve existing finance behavior, validation, sync, and data integrity.
- Prefer shared Compose components over per-screen hardcoded styling.
- Any screen that cannot be fully redesigned in one pass must still use the new tokens and shell components so it no longer feels visually separate.

## Surfaces In Scope

- Intro/login/offline screen, including the latest app icon.
- App shell: top bar, drawer, bottom navigation, search, sync, privacy controls.
- Main tabs: Dashboard, Loans, Invest, Reports, Expenses.
- Add/edit/detail forms and modal/bottom-sheet flows.
- Hamburger/drawer destinations: Settings, Profile/Security, Budgeting, Savings, Goals, Contact Book, Triggering Transactions, Managed Projects, EMI Calculator, legal/disclaimer screens, and related utility screens.
- Settings personalization and feedback states.
- PDF and XLSX report styling where app tokens should carry into exports.

## Implementation Strategy

1. Create a template-aligned component layer in `DesignSystem.kt`.
2. Move theme tokens and typography closer to the template.
3. Replace mixed old Material surfaces screen by screen with shared components.
4. Keep each change buildable and testable.
5. Install on the connected phone and smoke test real flows after each broad pass.

## Acceptance Criteria

- No main screen should look like a different design language from the template.
- Cards, section headers, nav, buttons, and forms should share the same visual rules.
- Dashboard spacing should be denser and more purposeful.
- Investment, loans, reports, and expenses should feel like sibling screens.
- Settings and drawer utility screens should no longer look like old/default Material screens.
- PDF layout should remain non-overlapping and use the final brand palette.
- Prod debug compile, prod debug unit tests, dev install, prod release install, and prod release AAB must pass before commit.
