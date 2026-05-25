# Fynlo Accessibility Audit Protocol

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** Defines how Fynlo verifies accessibility for users with disabilities.

---

## 0. Why this document exists

`DESIGN_SYSTEM.md` §14 lists accessibility requirements (touch targets, contrast, content descriptions). This protocol defines how to actually verify those requirements are met on a running app.

Accessibility isn't optional:
- Indian Rights of Persons with Disabilities Act 2016 applies to digital services
- Google Play has accessibility-related policies
- A user with visual or motor impairment shouldn't be locked out of their financial data
- Even users without disabilities benefit from good a11y (larger touch targets, clear contrast)

---

## 1. Audit cadence

| Audit | Frequency |
|---|---|
| Quick scan (5 minutes) | Every PR with UI changes |
| Manual audit (30 minutes) | Every major release |
| TalkBack walkthrough (60 minutes) | Every major release |
| Full external audit | Annually (or when team grows beyond solo) |

---

## 2. Quick scan checklist (per PR)

For any PR touching UI, verify in code review:

- [ ] All `IconButton` calls have non-null, non-empty `contentDescription`
- [ ] All `Icon` composables in clickable contexts have `contentDescription`
- [ ] Touch targets are ≥48dp (use `Modifier.size(48.dp)` or wrap in 48dp Box)
- [ ] Color is not the only signal (e.g., negative numbers have `−` prefix AND red color)
- [ ] No green-on-red or red-on-green text (worst contrast for color-blind users)
- [ ] Text contrast ratio ≥4.5:1 against background (use any online contrast checker)

Lint catches some (FY013, FY014). Reviewer catches the rest.

---

## 3. Manual audit (per major release)

Run through every primary screen with this checklist:

### 3.1 Visual

- [ ] All text is legible at default system font size
- [ ] Increase system font size by 1 step → app still usable (text doesn't get cut off)
- [ ] Increase further to max → degrades gracefully (scrolling works, no overlaps)
- [ ] Switch to dark mode (if implemented) → contrast preserved
- [ ] Color-blind simulation (Chrome DevTools or smartphone setting) → all signals still readable

### 3.2 Touch

- [ ] Every interactive element ≥48×48dp touch target (use Layout Inspector to verify)
- [ ] Spacing between touch targets ≥8dp (so adjacent buttons don't get mis-tapped)
- [ ] Long-press gestures, if any, also have a regular tap alternative
- [ ] Swipe-to-delete or similar gestures also have a button alternative

### 3.3 Layout

- [ ] Pinch-zoom works on content where reading matters (or use system font scaling)
- [ ] No critical UI hidden behind keyboard
- [ ] No critical UI hidden behind FAB (per FY006 rule + audit cluster C06)
- [ ] No critical UI cut off in landscape (if landscape is supported)
- [ ] Safe areas (notches, gesture nav) respected on edge-to-edge layouts

---

## 4. TalkBack walkthrough

TalkBack is Android's screen reader for users with visual impairments. Verify every key flow works with TalkBack enabled.

### 4.1 Enable TalkBack

Settings → Accessibility → TalkBack → ON

Volume Up + Down (hold both for 3 seconds) is the default shortcut to toggle TalkBack quickly.

### 4.2 Test flows

For each flow, do the full path with TalkBack on (eyes closed if you can):

1. **Sign in flow** — can you sign in with Google?
2. **Add a transaction** — can you select type, amount, account, category, save?
3. **View Dashboard** — does TalkBack announce balance correctly? Does it skip decorative elements?
4. **Open Loans tab** — can you navigate to a borrower, add a repayment?
5. **Export PDF** — can you trigger export, find the resulting file?

### 4.3 What to check

For each screen:
- Are labels meaningful? ("Edit button" not "Button")
- Are decorative images marked as such? (no announcement for pure-decoration icons)
- Is reading order logical? (top to bottom, left to right)
- Are state changes announced? (e.g., "Transaction added" after save)
- Are errors announced? (form validation messages must be read aloud, not just visual)

### 4.4 Document issues

For each TalkBack issue found, file as a UX_AUDIT entry under accessibility cluster (currently doesn't exist; create C24 if it becomes needed).

---

## 5. Automated tools

### 5.1 Accessibility Scanner

Google's free app:
1. Install from Play Store: "Accessibility Scanner"
2. Enable in Settings → Accessibility
3. Open Fynlo, tap the Scanner FAB
4. Get a report of issues

Run on every primary screen during manual audit. Fix highest-priority issues first.

### 5.2 Android Studio Layout Inspector

In Android Studio:
- View → Tool Windows → Layout Inspector
- Capture the running app's view tree
- Inspect each Compose component's bounds, contentDescription, semantics

Great for finding rogue 30dp touch targets or missing semantics.

### 5.3 Espresso Accessibility Tests

Espresso has `AccessibilityChecks.enable()` which fails tests on a11y issues:

```kotlin
@get:Rule
val composeRule = createComposeRule()

@Before
fun setup() {
    AccessibilityChecks.enable()
}
```

Run in CI to catch regressions automatically. Adoption is gradual — start with new screens, backfill as Sprint capacity allows.

---

## 6. Reachability & one-handed use

Although not a disability concern, many users use Fynlo one-handed on large devices.

Check:
- Primary actions reachable with thumb on a 6.5"+ phone
- FAB position is bottom-right (default; reachable with right thumb)
- No critical actions in top corners without alternative

---

## 7. Indian-specific accessibility considerations

### 7.1 Localization (future)

If Fynlo adds Telugu, Hindi, Tamil etc.:
- Right-to-left languages aren't relevant for these (all LTR)
- Verify text expansion: Hindi/Telugu text can be 1.5x the width of English; ensure layouts don't break
- Verify font supports glyphs needed (e.g., Devanagari, Tamil)

### 7.2 Number formats

Indian numbering uses lakhs/crores. The design system (§8.1) uses ", " grouping (₹50,000). Verify with TalkBack: is "₹50,000" read as "fifty thousand rupees" or "five comma zero zero zero rupees"? If the latter, add ARIA-style semantics for clearer reading.

### 7.3 Low-spec devices

Many Indian users are on entry-level Androids (Snapdragon 4xx, 2-3GB RAM). Accessibility is also performance:
- Animations should respect "Reduce Motion" setting (Settings → Accessibility)
- Heavy effects (blur, shadows, gradients) should degrade on low-end devices
- App should remain responsive with TalkBack on (some apps stall under screen reader load)

---

## 8. Reporting & remediation

### 8.1 Audit report

After each manual audit, write `audits/a11y-YYYY-MM-DD.md`:

```markdown
# Accessibility Audit — YYYY-MM-DD

## Auditor: [Name / self]
## Scope: [Screens covered]
## Method: [Manual / TalkBack / Scanner / mix]

## Findings

| Issue | Severity | Screen | Cluster |
|---|---|---|---|
| Icon button without contentDescription | High | Loans tab | C__ |
| Touch target 36dp on dropdown | High | Settings | C__ |
| ... | ... | ... | ... |

## Recommendations
- ...

## Next audit due: [date]
```

### 8.2 Severity

- **Blocker** — user cannot complete a core flow (sign in, add transaction, view balance)
- **High** — user can complete the flow but with significant friction
- **Medium** — minor inconvenience
- **Low** — cosmetic / polish

Blockers and Highs go into the next sprint. Mediums and Lows get backlogged.

---

## 9. The 80/20 of Fynlo accessibility

If you have limited audit time, prioritize:

1. **Touch targets ≥48dp** (most common issue)
2. **contentDescription on all icon buttons**
3. **Color is not the only signal** (esp. on negative numbers)
4. **Text doesn't cut off at larger system font sizes**
5. **TalkBack can announce balances and transaction amounts**

Get these right and the rest is incremental.

---

## 10. The one rule

**An app that handles your finances should work for everyone who needs to manage finances — including users with disabilities. Test with TalkBack at least quarterly.**

---

**End of ACCESSIBILITY_AUDIT_PROTOCOL.md v1.0**
