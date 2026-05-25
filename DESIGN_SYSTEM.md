# Fynlo Design System

**Version:** 1.1 (extracted from v3.2.1, two-archetype model)
**Last updated:** 2026-05-25
**Authority:** This document is the source of truth for visual & interaction patterns. When a screen disagrees with this document, the screen is wrong.

**Changes from v1.0:** Reframed around two screen archetypes (Home and Report) instead of treating Dashboard as the sole canonical reference. Interest Income and Debt Payoff promoted to reference implementations for the Report archetype.

---

## 0. Why this document exists

Fynlo has two kinds of screens, and they should look different from each other while still feeling like the same app. The Dashboard is the canonical **Home screen**. The Interest Income screen is the canonical **Report screen**. Both are well-designed *for their job*. Most other screens drift from one or both references.

This document captures:
1. The **shared foundation** every Fynlo screen must respect (colors, type, icons, FAB, navigation).
2. The **two archetypes** — Home and Report — with explicit pattern guidance for each.
3. The **migration plan** to bring every existing screen into the correct archetype.

If you're adding a screen and you can't tell whether it's a Home or Report screen, ask: does the user RETURN to this screen constantly, or do they VISIT it to answer a specific question? Returns → Home. Visits → Report.

---

## 1. The two archetypes

### 1.1 Home screen archetype

**Reference implementation:** Dashboard.

**Purpose:** A place the user returns to many times per day to orient themselves.

**Characteristics:**
- **Hero NUMBER** at top — one large definitive figure ("₹241,663 net worth")
- **Quick-action tiles** — colored squares for common tasks (Expense / Income / Lend / History)
- **Sectioned list rows** — Accounts, Insights, with clear headers and row patterns
- **Calm visual rhythm** — generous whitespace, single hero, restrained color use
- **FAB present** — for the screen's primary creation action
- **Bottom nav visible** — Home screens are always reachable from bottom nav

**Other current/intended Home screens:**
- Loans tab → should look like Dashboard (does not currently)
- Expenses tab → should look like Dashboard (does not currently)
- Invest tab → should look like Dashboard (does not currently)
- Reports landing → debatable, currently a hybrid; treat as Home (it's a launcher)

### 1.2 Report screen archetype

**Reference implementation:** Interest Income screen. Debt Payoff screen as secondary reference.

**Purpose:** A place the user visits to answer a specific data question, then leaves.

**Characteristics:**
- **Hero CHART** at top — line/bar/area chart showing the relevant data over time
- **Notable callouts** — "Best month: April ₹12K", "Total earned: ₹85,651" — as cards or pills
- **Period breakdown rows** — monthly / weekly entries below the chart
- **Information density acceptable** — users come here for data, not for calm
- **Abbreviated numbers** — K/L abbreviation on chart axes and dense list rows; full numbers on callouts
- **No FAB** by default — Report screens are read-only views of computed data
- **Back arrow only** — Report screens are pushed onto a stack, not bottom-nav destinations

**Other current/intended Report screens:**
- Monthly Summary → should look like Interest Income (partially does)
- P&L Statement → should look like Interest Income (does not currently — no chart)
- Net Worth History → should look like Interest Income (partially does)
- Money Flow → should look like Interest Income (currently empty placeholder)
- Debt Payoff → already a reference Report screen
- Interest Income → reference Report screen

### 1.3 Sheets and dialogs (cross-cutting)

Not an archetype. Bottom sheets (Add Transaction, Add Recurring) and dialogs (Wipe ALL Data, Restore Real Data) appear on both archetypes. They follow shared rules in §9.8 and §9.9.

---

## 2. Shared foundation (both archetypes must respect)

Regardless of archetype, every Fynlo screen MUST use:

- The same color palette (§3)
- The same type scale (§4)
- The same spacing grid (§5)
- The same shape tokens (§6)
- The same icon style and library (§7)
- The same number/date formatting rules (§8)
- The same FAB shape when a FAB is present (§9.7)
- The same back/navigation patterns (§9.1)
- The same dialog/sheet/form patterns (§9.8 – §9.10)
- The same color semantics (§11)
- The same voice and tone (§13)

The archetype determines the *page composition*. The foundation determines the *vocabulary* used inside the composition.

---

## 3. Color tokens

### 3.1 Brand & primary

| Token | Hex | Usage |
|---|---|---|
| `fynlo_green_primary` | `#0E9F6E` | App name wordmark, primary CTAs, FAB, selected tabs, sync indicator |
| `fynlo_green_tint` | `#D1FAE5` | Mint-pill button backgrounds, section dividers |
| `fynlo_green_icon_bg` | `#E8F5EE` | Round icon container backgrounds on list rows |

### 3.2 Semantic colors

| Token | Hex | Meaning |
|---|---|---|
| `semantic_income` | `#10B981` | Income, positive growth, money coming in |
| `semantic_expense` | `#EF4444` | Expense, debt, money out, destructive |
| `semantic_lend` | `#3B82F6` | Lending, receivables, money out that should return |
| `semantic_neutral` | `#9CA3AF` | History, archive, zero state, inactive |
| `semantic_warning` | `#F59E0B` | Growing assets, pending, attention |

### 3.3 Background tints (10% opacity)

| Token | Usage |
|---|---|
| `bg_expense_tint` | Red square behind Expense `−` icon |
| `bg_income_tint` | Green square behind Income `+` icon |
| `bg_lend_tint` | Blue square behind Lend handshake icon |
| `bg_neutral_tint` | Grey square behind History clock icon |

### 3.4 Surface & text

| Token | Hex | Usage |
|---|---|---|
| `surface_app_bg` | `#F9FAFB` | Screen background |
| `surface_card` | `#FFFFFF` | Card, sheet, dialog backgrounds |
| `surface_app_bar` | `#FFFFFF` | Top app bar (no separator) |
| `text_primary` | `#111827` | Hero numbers, titles, account names |
| `text_secondary` | `#6B7280` | Subtitles, captions |
| `text_tertiary` | `#9CA3AF` | Placeholders, disabled |
| `divider` | `#F3F4F6` | List row dividers, separators |

### 3.5 Chart colors (Report archetype)

When charts use multiple series, color them semantically:

| Series | Color |
|---|---|
| Income / inflows | `semantic_income` |
| Expense / outflows | `semantic_expense` |
| Lending / receivables | `semantic_lend` |
| Investments | `semantic_warning` |
| Net / total | `text_primary` |
| Comparison / previous | `text_tertiary` @ 60% opacity |

### 3.6 Forbidden colors

- Pure black `#000000` for text — use `text_primary`
- Pure white `#FFFFFF` for background — use `surface_app_bg`
- Stark red `#FF0000` — use `semantic_expense`
- Anything outside this palette without amending this doc

---

## 4. Typography

### 4.1 Type scale

| Token | Size | Weight | Line height | Usage |
|---|---|---|---|---|
| `type_hero` | 48sp | Bold | 56sp | Dashboard Net Worth, Loans Total Outstanding, Invest Portfolio Value |
| `type_chart_hero` | 28sp | Bold | 36sp | Report screens — number summary above/inside chart |
| `type_screen_title` | 32sp | Bold | 40sp | "Savings Goals", "Settings" — left-aligned below app bar |
| `type_section_header` | 20sp | Bold | 28sp | "Accounts", "Insights" |
| `type_card_title` | 18sp | SemiBold | 24sp | Card titles, account names |
| `type_body` | 16sp | Regular | 24sp | Body text |
| `type_body_emphasis` | 16sp | SemiBold | 24sp | Right-aligned amounts in list rows |
| `type_caption` | 14sp | Regular | 20sp | Subtitles, descriptions |
| `type_callout_value` | 22sp | Bold | 28sp | Report callout values |
| `type_callout_label` | 12sp | Medium | 16sp | Report callout labels (UPPERCASE, +0.5sp letter-spacing) |
| `type_small` | 12sp | Regular | 16sp | Bottom nav labels, badges |
| `type_micro` | 10sp | Medium | 14sp | Currency codes, chart axis labels |

### 4.2 Rules

- **One hero per screen** — `type_hero` (Home) OR `type_chart_hero` (Report), never both.
- **Screen titles always left-aligned** below the centered "Fynlo" app bar wordmark.
- **Section headers plain bold** — no green bullets, no decorations.
- **Tabular figures** — `fontFeatureSettings = "tnum"` for any number column.

---

## 5. Spacing system

4dp base grid. Every spacing value must be a multiple of 4dp.

| Token | Value | Usage |
|---|---|---|
| `space_1` | 4dp | Tight icon-to-label gap |
| `space_2` | 8dp | Inside chips, related items |
| `space_3` | 12dp | Inside cards |
| `space_4` | 16dp | Default padding |
| `space_5` | 20dp | Section title to first item |
| `space_6` | 24dp | Between sections |
| `space_8` | 32dp | Top of screen below app bar |
| `space_10` | 40dp | Hero number breathing room |
| `space_12` | 48dp | Bottom above nav |
| `space_16` | 64dp | FAB clearance |

### 5.1 Screen padding

- Horizontal: 16dp both sides
- Top: Screen title 24dp below app bar
- Bottom: 96dp for FAB-overlapping content (64 + 32 safety)

### 5.2 The FAB-overlap rule

Every Home screen with a FAB MUST add 96dp `contentPadding.bottom` to its scrollable container. Currently violated on Dashboard, Loans, Invest, Monthly Summary, Debt Payoff, Expenses. Fix once in `Modifier.fabSafePadding()`, apply universally.

### 5.3 Chart sizing (Report archetype)

- Compact: 200dp tall
- Default: 280dp tall
- Dense: 360dp tall
- Horizontal padding: 16dp (matches screen)
- Chart-to-callout-row spacing: 24dp

---

## 6. Shape (border radius)

| Token | Value | Usage |
|---|---|---|
| `radius_chip` | 8dp | Filter chips, color swatches |
| `radius_button` | 24dp | Primary CTAs (full pill) |
| `radius_card` | 16dp | Cards, dialogs, sheets (top corners only on sheets) |
| `radius_tile` | 20dp | Quick-action squares (Home) |
| `radius_callout` | 12dp | Notable callout cards (Report) |
| `radius_icon_container` | 50% | Round icon containers |
| `radius_fab` | 20dp | FAB — square with rounded corners, NOT circular |

### 6.1 FAB rule

Fynlo's FAB is a **rounded square**, 56dp × 56dp, position `Alignment.BottomCenter` with 16dp from bottom edge. Distinctive — preserve it.

---

## 7. Elevation, iconography, charts

### 7.1 Elevation

Low-elevation app. Reserve elevation for genuinely floating things.

| Token | dp | Usage |
|---|---|---|
| `elevation_flat` | 0 | Background, list rows, dividers |
| `elevation_card` | 1 | Cards — barely visible |
| `elevation_sheet` | 8 | Bottom sheets, dialogs |
| `elevation_fab` | 6 | FAB |
| `elevation_app_bar` | 0 → 2 | Lifts when content scrolls under |

Shadow color: `#0F172A` @ 8% opacity. Never pure black.

### 7.2 Iconography

- Style: Filled (nav, primary), outlined (secondary, utility)
- Size: 24dp standard, 20dp in chips, 32dp for tiles
- Color: Inherits from semantic context
- Library: Material Symbols (rounded) default, Lucide fallback

#### Icon containers (list rows)
- 40dp circle, `fynlo_green_icon_bg`
- 24dp icon, `fynlo_green_primary`
- 12dp margin to label

#### Forbidden
- Multiple action icons per row — max 1 + overflow `⋯`
- Emoji as icons — remove ✨ from action buttons
- Mixed icon weights on same screen

### 7.3 Charts (Report specifics)

- Library: MPAndroidChart or Compose-friendly equivalent (Vico)
- Grid lines: `divider` color, dashed, max 4 horizontal
- Axis labels: `type_micro`, `text_tertiary`
- Data point markers: filled circles, semantic color, 6dp diameter; 10dp on select
- Empty chart state: chart frame with centered message — never blank space
- Animation: 600ms draw-in (`motion_chart`) on first appearance only

---

## 8. Number & date formatting

### 8.1 Number formatting matrix

| Context | Format | Example |
|---|---|---|
| Hero number (Home) | Full, no decimals, Indian commas | `₹2,41,663` |
| Chart hero (Report) | Full or abbreviated if ≥10 chars | `₹85,651` or `₹2.4L` |
| Callout value (Report) | Abbreviated | `₹12K`, `₹2.4L` |
| List row amount ≥₹1L | Abbreviated, 1 decimal | `₹2.4L` |
| List row amount ₹1K–₹1L | Abbreviated, 0 decimals | `₹15K` |
| List row amount < ₹1K | Full | `₹500` |
| Detail screen amount | Full, Indian commas | `₹2,41,663` |
| Edit form input | Raw integer typing | `241663` |
| Negative | `−` prefix, `semantic_expense` | `−₹2,000` |
| Zero in tables | `—` not `₹0` | `—` |
| Decimals | Hide unless required | `₹15,000` not `₹15,000.00` |

### 8.2 Indian numbering

Default for INR projects:
- `₹2,41,663` not `₹241,663`
- `₹1,23,45,678` not `₹12,345,678`
- `₹2.4L` for lakh
- `₹1.2Cr` for crore

Non-INR projects follow that currency's locale convention.

### 8.3 Date formatting

| Context | Format | Example |
|---|---|---|
| Relative recent | "X ago" / "in X" | "2 days ago" |
| Same week | Day name | "Monday" |
| Same year | Day + month | "25 May" |
| Different year | Day + month + year | "25 May 2025" |
| Compact (list) | User setting | "25-05-26" |
| Definitive (detail) | Long | "25 May 2026, 9:07 AM" |
| Chart axis | Compact | "May 26", "Apr" |

### 8.4 User-configurable

Settings exposes `dd-MM-yyyy / MM-dd-yyyy / yyyy-MM-dd`. All numeric date displays respect this setting.

### 8.5 Forbidden

- `20260525` (no separators)
- Mixing epoch ms and ISO strings in same record
- Raw integers in display (`30000` instead of `₹30,000`)
- Mixing decimal/no-decimal on same screen
- Pure black `−` minus instead of semantic red
- `₹0.00` for zero — use `—` or hide

---

## 9. Component patterns

### 9.1 Screen scaffold (shared)

```
┌─────────────────────────────────────┐
│ Top App Bar (white, 56dp)           │
│  ← / ☰    Fynlo    🔍  ☁︎          │
├─────────────────────────────────────┤
│                                     │ ← space_8
│ Screen Title (type_screen_title)    │
│ Subtitle (type_caption, optional)   │ ← space_2
│                                     │ ← space_6
│ ── Archetype-specific content ──    │
│                                     │
│                              ┌───┐  │
│                              │ + │  │ ← FAB (Home only)
│                              └───┘  │
├─────────────────────────────────────┤
│ [Bottom Nav, 56dp]                  │ ← Home only
└─────────────────────────────────────┘
```

**Top app bar:**
- White, no separator
- Centered "Fynlo" wordmark in green, 18sp Bold
- Left: back arrow (Report) or hamburger (Home top-level)
- Right: search + cloud sync indicator
- Elevation 0 → 2 when content scrolls under

### 9.2 Home archetype composition

```
┌─────────────────────────────────────┐
│ "Good afternoon ☀️"                 │ ← type_caption
│ "Total net worth"                   │ ← type_caption, text_secondary
│ ₹ 2,41,663                          │ ← type_hero
│                                     │
│ Assets        Liabilities           │ ← type_caption
│ ₹5,24,657     ₹2,82,994             │ ← type_card_title
│                                     │
│ ┌────────────────────────────────┐  │
│ │ Net worth trend / sparkline    │  │
│ └────────────────────────────────┘  │
│                                     │
│ [Expense] [Income] [Lend] [History] │ ← Quick-action tiles
│                                     │
│ Accounts                            │ ← type_section_header
│ [List rows with icons §9.4]         │
│                                     │
│ Insights                            │
│ [Donut chart §9.13]                 │
└─────────────────────────────────────┘
```

### 9.3 Report archetype composition

```
┌─────────────────────────────────────┐
│ Interest Income                     │ ← type_screen_title
│ Earnings from lent loans            │ ← type_caption
│                                     │
│ ₹ 85,651                            │ ← type_chart_hero
│ Total earned (FY 2025-26)           │ ← type_caption
│                                     │
│ ┌────────────────────────────────┐  │
│ │ ▁▃▅▇▆▅▇█▆▅▄▂  (chart, 280dp)  │  │
│ └────────────────────────────────┘  │
│                                     │
│ ┌──────────────┐ ┌──────────────┐   │
│ │ BEST MONTH   │ │ AVG / MONTH  │   │ ← Callouts §9.14
│ │ ₹12K (Apr)   │ │ ₹7,138       │   │
│ └──────────────┘ └──────────────┘   │
│                                     │
│ Monthly breakdown                   │ ← type_section_header
│ [Period rows with values]           │
└─────────────────────────────────────┘
```

No FAB. No bottom nav.

### 9.4 List row with icon (shared)

```
┌────────────────────────────────────────┐
│  ⊙   Cash in Hand            ₹15,000   │  ← 64dp
└────────────────────────────────────────┘
```

- 64dp plain / 72dp with subtitle
- Icon container 40dp circle, 12dp from left
- Label `type_card_title`, 16dp from icon
- Amount `type_body_emphasis`, right-aligned
- Divider between rows only
- Full row tappable, ripple

Variants: subtitle, chevron, swipe actions.

### 9.5 Quick-action tile (Home only)

```
┌──────────┐
│  ┌────┐  │
│  │ −  │  │  ← 56dp tile, radius_tile
│  └────┘  │
│  Expense │  ← type_caption
└──────────┘
```

- 56dp × 56dp tile, semantic tint background
- 24dp icon, semantic color
- Tap target: 80dp × 80dp including label
- 4-up grid on Dashboard

### 9.6 Empty state (shared)

```
        ⭐                    ← 64dp grey icon
   No savings goals yet      ← type_card_title, centered
 Set targets for big          ← type_caption, centered
   purchases or milestones
   ┌──────────────┐
   │ Add First Goal │         ← Primary CTA, radius_button
   └──────────────┘
```

- Grey icon, 64dp centered
- Title short, no exclamation
- Body one sentence
- ONE CTA — "Add First X"
- Top 1/3 of available space
- **No FAB when empty-state CTA is shown.** Currently violated on Savings Goals, Recurring, Budgeting.

### 9.7 FAB (Home only)

- 56dp × 56dp, `radius_fab`, NOT circle
- `fynlo_green_primary` fill, white `+`
- `Alignment.BottomCenter`, 16dp from bottom
- `elevation_fab` (6dp)
- Hide when empty-state CTA shown
- 96dp bottom padding required on scrollable above it

Report screens have NO FAB. Reports are read-only.

### 9.8 Bottom sheet (shared)

```
┌─────────────────────────────────────┐
│      ▔▔                              │ ← drag handle
│  Add Savings Goal                   │ ← type_card_title
│  [Field 1]                          │
│  [Field 2]                          │
│        [Cancel]        [Save]       │
└─────────────────────────────────────┘
```

- Top corners only rounded (`radius_card`)
- Scrim black @ 40%
- Drag handle 32×4dp, `divider` color, centered
- Title `type_card_title`, 24dp from edges
- 20dp between fields
- Cancel (text) + Primary (pill), bottom-right
- Disabled Save: 50% opacity + inline reason

### 9.9 Dialog (shared)

```
┌───────────────────────────────┐
│  Wipe ALL Data?               │
│  ⚠ PERMANENT DESTRUCTION:     │
│  This will delete everything. │
│        [Cancel]   [WIPE]      │
└───────────────────────────────┘
```

- All corners rounded
- White background
- Button colors REFLECT destructiveness:
  - Restorative: `fynlo_green_primary` (Restore, Save)
  - Destructive: `semantic_expense` (Wipe, Delete)
  - Neutral: text button only (Cancel)
- **Load Test Data is NOT destructive** — use neutral primary

### 9.10 Form field (shared)

```
Goal Name (e.g., New Car)       ← placeholder, text_tertiary
────────────────────────────    ← 1dp underline, green on focus
```

- 48dp single-line, grows for multi-line
- Placeholder: `"Field Name (e.g., example)"`
- Required: ` *` suffix in semantic_expense
- Validation inline, `type_caption`, semantic_expense
- Currency: `₹` prefix inline

### 9.11 Chip (shared)

- Selected: `fynlo_green_tint` fill, `fynlo_green_primary` text
- Unselected: white fill, `text_secondary` text, divider border
- 32dp tall, 12dp horizontal padding
- `radius_chip` (8dp)
- **Never wrap text mid-word**

### 9.12 Toggle switch (shared)

Material 3 default. ON green, OFF grey, Disabled 50% opacity.

### 9.13 Donut chart (Home — Insights)

```
        ╭─────╮
       │   ●   │  ← center summary
        ╰─────╯
   ┌─────┬─────┬─────┐
   │ ▓ Cash    140K  │
   │ ▒ Inv     384K  │
   │ ░ Lend    103K  │
   │ ● Owed   −283K  │
   └─────────────────┘
```

- 140dp donut, 24dp stroke
- Center: `type_card_title` summary
- Wedges colored by semantic
- Legend below, each row tappable

### 9.14 Time-series chart hero (Report)

Interest Income canonical pattern:

```
₹ 85,651                          ← type_chart_hero
Total earned (FY 2025-26)         ← type_caption

┌──────────────────────────────┐
│  ▁  ▃  ▅  ▇  ▆  ▅  ▇  █     │ ← 280dp default
│  May Jun Jul Aug Sep Oct Nov │
└──────────────────────────────┘
```

- Chart height: 280dp default
- X-axis: time periods
- Y-axis: minimal — 2-3 reference values
- Series colored by §3.5
- Tap point: callout with value + period

### 9.15 Notable callout (Report)

```
┌──────────────┐ ┌──────────────┐
│ BEST MONTH   │ │ AVG / MONTH  │
│ ₹12K (Apr)   │ │ ₹7,138       │
└──────────────┘ └──────────────┘
```

- White fill, `radius_callout` (12dp), 1dp divider border
- 16dp internal padding
- Label `type_callout_label` UPPERCASE, +0.5sp letter-spacing
- Value `type_callout_value`, `text_primary`
- 2-up or 3-up, equal widths, 12dp gap

Common callouts: BEST MONTH, WORST MONTH, AVG/MONTH, TOTAL, GROWTH %, PROJECTION, COMPARED TO LAST PERIOD.

---

## 10. Information architecture

| Surface | Archetype | Reached via |
|---|---|---|
| Dashboard | Home | Bottom nav |
| Loans | Home | Bottom nav |
| Invest | Home | Bottom nav |
| Reports landing | Home (launcher) | Bottom nav |
| Expenses | Home | Bottom nav |
| P&L Statement | Report | Reports landing |
| Net Worth History | Report | Reports landing |
| Monthly Summary | Report | Reports landing |
| Money Flow | Report | Reports landing |
| Debt Payoff | Report | Reports landing |
| Interest Income | Report | Reports landing |
| Add Transaction | Sheet | FAB |
| Borrower Detail | Detail | Loans row tap |
| Investment Detail | Detail | Invest row tap |
| Savings Goals | Home (secondary) | Drawer |
| Recurring | Home (secondary) | Drawer |
| Budgeting | Home (secondary) | Drawer |
| Contact Book | Home (secondary) | Drawer |
| Manage Projects | Settings-style | Drawer |
| Profile & Security | Settings-style | Drawer |
| Settings | Settings-style | Drawer |
| About | Static content | Drawer |
| Search | Modal | App bar search |

**"Home (secondary)"** — bottom nav hidden, but follows Home patterns (hero, list rows, FAB). For drawer screens that create data.

---

## 11. Color semantics

| Color | Means | Examples |
|---|---|---|
| `fynlo_green_primary` | Primary action / brand | FAB, save, app name |
| `semantic_income` | Money IN | Income txns, positive growth |
| `semantic_expense` | Money OUT / destructive | Expense txns, debts, delete |
| `semantic_lend` | Money OUT that returns | Lent loans, receivables |
| `semantic_warning` | Attention | Overdue, growing, pending |
| `semantic_neutral` | Inactive / zero | Closed, empty, history |

### 11.1 Common violations to fix

- Green check for "selected project" → looks like income. Use filled radio or blue check.
- Restore button red → restorative, use green.
- Load Test Data button red → neutral, not destructive.
- Loans tab Outstanding shown red → should be green (money you'll receive).
- Negative investment growth `−1,500` in plain text → must be `semantic_expense`.

---

## 12. Motion & interaction

### 12.1 Durations

| Token | Duration | Curve | Usage |
|---|---|---|---|
| `motion_fast` | 100ms | Linear | Ripple, button press |
| `motion_standard` | 200ms | FastOutSlowIn | Most transitions |
| `motion_slow` | 350ms | FastOutSlowIn | Bottom sheets |
| `motion_chart` | 600ms | Emphasized | Chart draw-in (Report) |
| `motion_emphasis` | 500ms | Emphasized | Hero transitions, success |

### 12.2 Required

- Ripple on every tappable
- Success feedback (snackbar/haptic) on save
- Clear next action on error
- Pull-to-refresh on Home screens
- Swipe-back from left edge

### 12.3 Loading states

- <200ms: no indicator
- 200ms–2s: skeleton matching layout
- >2s: skeleton + bottom progress bar
- Long-running: modal with progress + cancel

Never block UI on background sync.

---

## 13. Voice & tone

### 13.1 Principles

- Direct
- Indian English (lakh, crore, ₹)
- Calm with money — no exclamation on numbers
- Honest about limits — "Recalculated 5 days ago"
- Helpful in errors — "Couldn't sync. Check internet."

### 13.2 Vocabulary

| Use | Don't |
|---|---|
| Loan (lent to others) | Hand loan, advance |
| Debt (you owe) | Liability |
| Account | Wallet, holding |
| Transaction | Entry |
| Recalculate | Refresh interest |
| Income / Expense | Credit / Debit |
| Overdue | Late, breached |

### 13.3 Empty state copy

`"No [things] yet"` + `"[Why]. [What to do]"` + `"Add First [Thing]"`

### 13.4 Pluralization

Always smart-singular. `1 transaction` not `1 transactions`. Build `pluralize(count, "transaction")` helper.

---

## 14. Accessibility

- Touch ≥48dp (invisible padding around small icons)
- Contrast ≥4.5:1 body, ≥3:1 large
- ContentDescription on every icon-only button
- Color never sole indicator (overdue = red AND icon)
- TalkBack on all interactive elements
- Dynamic type — layouts reflow

### 14.1 Reduced motion

System "reduce animations" ON:
- Use `motion_fast` for transitions
- Skip chart draw-in
- Keep functional confirmations, shorten

---

## 15. Dark mode (when implemented)

| Light | Dark |
|---|---|
| `surface_app_bg` `#F9FAFB` | `#0F172A` |
| `surface_card` `#FFFFFF` | `#1E293B` |
| `text_primary` `#111827` | `#F1F5F9` |
| `text_secondary` `#6B7280` | `#94A3B8` |
| `divider` `#F3F4F6` | `#334155` |
| `fynlo_green_primary` `#0E9F6E` | `#34D399` |
| `fynlo_green_icon_bg` `#E8F5EE` | `#064E3B` |

Rules: never pure black background; elevation via tints not shadows; semantic colors brighten for contrast.

---

## 16. Anti-patterns (forbidden)

In current Fynlo, must be removed:

1. Two FABs on a single screen (Savings, Recurring, Budgeting empty states)
2. Garbled emoji in dialogs (`â,¹` instead of ₹) — fix UTF-8 pipeline
3. Decimal places on rupee amounts (`₹15,000.00`)
4. Raw integer in UI (`30000` on Edit Loan)
5. Category bleed across Income/Expense toggle
6. Free-text fields for known-finite values (Account, Category)
7. Multiple action icons per row — max 1 + overflow
8. Disabled buttons with no hint why
9. "Add First X" CTA AND FAB simultaneously
10. Hardcoded version strings in exports
11. Date strings without separators (`20260525`)
12. Mixing epoch ms and ISO date in same record
13. Pluralization without smart singular (`1 snapshots`)
14. Pure black text on light grey background
15. Stark white screen backgrounds
16. Color used non-semantically (green check for "selected")
17. Section headers with decorations (green bullets in Settings)
18. Sparkle emoji ✨ on action buttons
19. Empty insight rows showing ₹0 (Hand Loans)
20. Negative numbers in default text color
21. **FAB on Report screens**
22. **`type_hero` on Report screens** — use `type_chart_hero`

---

## 17. Adding to this document

When you need a pattern this doesn't cover:

1. Don't invent one-off patterns.
2. Open a PR extending this document FIRST.
3. Review against principles: calm, semantic, two archetypes, generous spacing.
4. Then implement.

Every new screen reviewable with: "Does it match DESIGN_SYSTEM.md?" Yes → ship. No → fix screen or extend doc.

---

## 18. Migration plan

**Reference implementations — do NOT refactor:**
- Dashboard — Home archetype
- Interest Income — Report archetype
- Debt Payoff — Report archetype (secondary)
- About & Disclaimer — static-content reference

**Refactor in priority order:**

### Phase 1 — Home archetype fixes

1. **Loans tab** — biggest violator, highest traffic
   - Add hero number (Total Outstanding)
   - Simplify borrower row to 1 primary action + overflow
   - Standardize Lent vs Owed layouts
   - Fix FAB padding
2. **Expenses tab**
   - Add hero (This Month's Spend)
   - Fix FAB padding
   - Fix "Expense" literal category bug
3. **Invest tab**
   - Add hero (Portfolio Value with growth %)
   - Fix Mutual Fund SIP semantic color
   - Reorder Update Value vs Withdraw button weight
4. **Reports landing**
   - Remove duplicate "Where Money Went"
   - Standardize tile sizing

### Phase 2 — Report archetype fixes

5. **Monthly Summary**
   - Add proper hero chart with headline number
   - Add callouts (Best month, Avg, Total)
   - Fix y-axis labels
6. **P&L Statement**
   - Add hero chart (income vs expense over time)
   - Add callouts (Net P&L, vs last period)
   - Fix "Total Lent Out" misleading definition
7. **Net Worth History**
   - Add hero chart
   - Fix "1 snapshots" pluralization
   - Add backfill option
8. **Money Flow**
   - Build the screen (currently empty) or remove the tile
   - Sankey or simplified flow chart

### Phase 3 — Sheets, settings, secondary

9. **Add Transaction sheet** — fix toggle position, smart defaults, category bleed
10. **Settings** — migrate green-bullet headers to plain bold; standardize destructive dialog colors; hide Developer in release
11. **Empty states app-wide** — remove duplicate FABs, standardize "Add First X"
12. **Drawer** — compact header, add user identifier, order by usage
13. **Other sheets** (Add Recurring / Goal / Contact / Budget) — apply §9.10 form pattern, smart defaults

### Phase 4 — Cross-cutting infrastructure

14. **`Modifier.fabSafePadding()`** — single fix applied universally
15. **Smart defaults system** — most-recently-used category, account
16. **UTF-8 dialog encoding fix** — affects all dialogs
17. **Pluralization helper** — replace every hardcoded plural
18. **Number formatting library** — Indian numbering, K/L abbrev, semantic color
19. **Date formatting respects user setting** — every numeric date

Estimated total: 4-6 sprints with one engineer.

---

## Appendix A: Token quick reference (Kotlin)

```kotlin
object FynloColors {
    val GreenPrimary = Color(0xFF0E9F6E)
    val GreenTint = Color(0xFFD1FAE5)
    val GreenIconBg = Color(0xFFE8F5EE)

    val SemanticIncome = Color(0xFF10B981)
    val SemanticExpense = Color(0xFFEF4444)
    val SemanticLend = Color(0xFF3B82F6)
    val SemanticWarning = Color(0xFFF59E0B)
    val SemanticNeutral = Color(0xFF9CA3AF)

    val SurfaceAppBg = Color(0xFFF9FAFB)
    val SurfaceCard = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF111827)
    val TextSecondary = Color(0xFF6B7280)
    val TextTertiary = Color(0xFF9CA3AF)
    val Divider = Color(0xFFF3F4F6)
}

object FynloTypography {
    val Hero = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
    val ChartHero = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    val ScreenTitle = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val SectionHeader = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val CardTitle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val BodyEmphasis = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val CalloutValue = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val CalloutLabel = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
    val Small = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
}

object FynloSpacing {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp
    val FabSafeBottom = 96.dp
    val ChartCompact = 200.dp
    val ChartDefault = 280.dp
    val ChartDense = 360.dp
}

object FynloShape {
    val Chip = RoundedCornerShape(8.dp)
    val Button = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(16.dp)
    val Tile = RoundedCornerShape(20.dp)
    val Callout = RoundedCornerShape(12.dp)
    val Fab = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}
```

---

## Appendix B: Quick archetype decision

When building a new screen, answer one question:

**Does the user RETURN to this screen multiple times per day to orient themselves?**

- **Yes** → Home archetype. Dashboard pattern. Hero number, quick-action tiles, list rows, FAB if creating data, bottom nav visible.
- **No** → Report archetype. Interest Income pattern. Hero chart, callout cards, period breakdown, no FAB, back arrow only.

If unsure, default to Home — it has more flexibility.

---

**End of DESIGN_SYSTEM.md v1.1**
