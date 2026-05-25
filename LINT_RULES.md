# Fynlo Lint Rules Specification

**Version:** 1.0
**Last updated:** 2026-05-25
**Status:** Specification. Implementation is a Sprint 2 deliverable.
**Tooling:** Detekt (Kotlin source rules), Android Lint (Compose/resource rules), KtLint (formatting), custom Compose Lint rules

---

## 0. Why this document exists

`DESIGN_SYSTEM.md` describes rules in prose. `AI_AGENT_PROTOCOL.md` asks agents to follow them. **Neither is enforced.** This document specifies the lint rules that turn those prose rules into compile-time / CI-failure errors.

Once implemented (Sprint 2), the design system stops being a suggestion and starts being a constraint. Violations fail the build. The agent or human writing the code can't ship the violation.

Each rule:
- Maps to a `DESIGN_SYSTEM.md` section
- Is implementable in Detekt or Android Lint
- Has a clear FORBIDDEN pattern and a clear ALLOWED replacement
- Has a severity: `error` (build fails) or `warning` (CI annotation)

---

## 1. Rule index

| ID | Rule | Severity | Maps to DS §  |
|---|---|---|---|
| FY001 | No raw color literals outside theme | error | §3 |
| FY002 | No raw dp literals outside theme | error | §5 |
| FY003 | No raw sp literals outside typography | error | §4 |
| FY004 | Direct String currency format forbidden | error | §8.1 |
| FY005 | Direct date format forbidden | error | §8.3 |
| FY006 | Scaffold with FAB requires fabSafePadding | error | §5.2 |
| FY007 | No emoji in dialog string resources | error | §16.2 |
| FY008 | No literal "Expense" / "Income" as category | error | §16.5 |
| FY009 | Plural strings must use pluralize() | warning | §16.13 |
| FY010 | Negative amounts require semantic_expense | warning | §16.20 |
| FY011 | Section headers must use type_section_header | error | §4.2 |
| FY012 | FAB must be RoundedCornerShape(20.dp) | error | §6.1 |
| FY013 | Touch targets must be ≥48dp | warning | §14 |
| FY014 | Icon-only buttons require contentDescription | error | §14 |
| FY015 | No localStorage / sessionStorage in artifacts | error | env |
| FY016 | Repayment data must use Repayment record, not paid field | error | C01 |
| FY017 | Exports must call recalculate() first | error | C02 |
| FY018 | Schema records must declare schemaVersion | warning | C03 |
| FY019 | Transactions must reference accounts by ID, not name | error | C03 |
| FY020 | Borrower.intType not Borrower.type | warning | C03 |
| FY021 | Debug-only code gated by BuildConfig.DEBUG | error | §16.18 |
| FY022 | No hardcoded version strings in exports | error | §16.10 |
| FY023 | Empty state must use shared EmptyState composable | warning | §9.6 |
| FY024 | Updated_at must update on mutation | error | C01 / C03 |

---

## 2. Detailed rules

### FY001 — No raw color literals outside theme

**Maps to:** `DESIGN_SYSTEM.md §3`
**Severity:** `error`
**Tooling:** Custom Detekt rule

**Forbidden:**
```kotlin
val titleColor = Color(0xFF111827)  // ← raw hex
val errorColor = Color.Red          // ← framework default
Text("Hello", color = Color(0xFF0E9F6E))
```

**Allowed:**
```kotlin
val titleColor = FynloColors.TextPrimary
val errorColor = FynloColors.SemanticExpense
Text("Hello", color = FynloColors.GreenPrimary)
```

**Exception:** Files inside `ui/theme/` package (where token definitions live).

**Implementation sketch:**
```kotlin
class RawColorLiteralRule(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "FY001",
        severity = Severity.Defect,
        description = "Raw color literal outside theme — use FynloColors.* (DESIGN_SYSTEM.md §3)",
        debt = Debt.FIVE_MINS
    )
    override fun visitCallExpression(expression: KtCallExpression) {
        val ref = expression.calleeExpression?.text ?: return
        if (ref == "Color") {
            val containing = expression.containingKtFile.packageFqName.asString()
            if (!containing.contains("ui.theme")) {
                report(CodeSmell(issue, Entity.from(expression),
                    "Use FynloColors token instead of Color() literal."))
            }
        }
    }
}
```

---

### FY002 — No raw dp literals outside theme

**Maps to:** `DESIGN_SYSTEM.md §5`
**Severity:** `error`

**Forbidden:**
```kotlin
Modifier.padding(15.dp)          // ← not on 4dp grid
Modifier.padding(top = 17.dp)    // ← arbitrary
Spacer(Modifier.height(13.dp))
```

**Allowed:**
```kotlin
Modifier.padding(FynloSpacing.s4)   // 16dp
Modifier.padding(top = FynloSpacing.s5)  // 20dp
Spacer(Modifier.height(FynloSpacing.s3))
```

**Exception:** Files inside `ui/theme/` package.

**Tolerance:** Allow `0.dp`, `1.dp` (hairline dividers). Forbid everything else outside the multiples-of-4 token set.

---

### FY003 — No raw sp literals outside typography

**Maps to:** `DESIGN_SYSTEM.md §4`
**Severity:** `error`

**Forbidden:**
```kotlin
Text("Hello", fontSize = 17.sp)
TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
```

**Allowed:**
```kotlin
Text("Hello", style = FynloTypography.Body)
Text("Heading", style = FynloTypography.CardTitle)
```

**Exception:** `ui/theme/Typography.kt`.

---

### FY004 — Direct String currency format forbidden

**Maps to:** `DESIGN_SYSTEM.md §8.1`, audit cluster C08
**Severity:** `error`

**Forbidden:**
```kotlin
"₹${amount}"
"₹%.2f".format(amount)
"₹$amount"
DecimalFormat("#,###.00").format(amount)
NumberFormat.getCurrencyInstance().format(amount)
```

**Allowed:**
```kotlin
formatCurrency(amount, Currency.INR, CurrencyStyle.Hero)
formatCurrency(amount, Currency.INR, CurrencyStyle.ListRow)
formatCurrency(amount, Currency.INR, CurrencyStyle.Detail)
```

**Implementation:** Detect `String.format` with `"%.2f"` or string template containing `"₹"`. Also detect `DecimalFormat`, `NumberFormat.getCurrencyInstance`.

---

### FY005 — Direct date format forbidden

**Maps to:** `DESIGN_SYSTEM.md §8.3`, audit cluster C11
**Severity:** `error`

**Forbidden:**
```kotlin
SimpleDateFormat("dd-MM-yyyy").format(date)
DateTimeFormatter.ofPattern("yyyyMMdd")
"${day}-${month}-${year}"
```

**Allowed:**
```kotlin
formatDate(date, DateStyle.Compact)       // respects user setting
formatDate(date, DateStyle.Relative)      // "2 days ago"
formatDate(date, DateStyle.Definitive)    // "25 May 2026, 9:07 AM"
formatDate(date, DateStyle.ChartAxis)     // "May 26"
```

**Exception:** Internal storage (ISO format epochMillis ↔ Long ↔ ISO string) — these aren't displayed, they're persisted. Mark the helper file with `// FY005-EXEMPT`.

---

### FY006 — Scaffold with FAB requires fabSafePadding

**Maps to:** `DESIGN_SYSTEM.md §5.2`, audit cluster C06
**Severity:** `error`

**Forbidden:**
```kotlin
Scaffold(
    floatingActionButton = { FynloFab(...) }
) {
    LazyColumn { ... }  // ← no fabSafePadding, will overlap
}
```

**Allowed:**
```kotlin
Scaffold(
    floatingActionButton = { FynloFab(...) }
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = FynloSpacing.FabSafeBottom)
    ) { ... }
}
// OR via modifier
LazyColumn(modifier = Modifier.fabSafePadding()) { ... }
```

**Implementation:** Detect `Scaffold` with non-null `floatingActionButton` argument. Inside, look for `LazyColumn`, `Column { verticalScroll }`, `LazyVerticalGrid` etc. If any of these is missing `contentPadding` with `FabSafeBottom` OR is not using `Modifier.fabSafePadding()`, report.

This is a structural rule and harder to implement perfectly. Initial version can be a warning that runs on `*Screen.kt` files only.

---

### FY007 — No emoji in dialog string resources

**Maps to:** `DESIGN_SYSTEM.md §16.2`, audit cluster C09
**Severity:** `error`
**Tooling:** Android Lint (XML resource check)

**Forbidden** in `strings.xml`:
```xml
<string name="dialog_warning">⚠️ This will delete data.</string>
<string name="dialog_cost">Cost is ₹100.</string>
```

**Why:** The UTF-8 encoding pipeline is fragile (per audit point 245). Until C09 is fully closed and verified by snapshot tests, emoji and special characters in resource strings are forbidden.

**Allowed:**
```xml
<string name="dialog_warning">Warning: This will delete data.</string>
<string name="dialog_cost">Cost is %1$s.</string>
<!-- Format with rupee symbol at runtime via formatCurrency -->
```

**Exception:** Lifted once C09 verification snapshot tests pass on all dialogs across all configurations (debug/release, light/dark, all locales). Remove this rule when that's done.

---

### FY008 — No literal "Expense" / "Income" as category

**Maps to:** `DESIGN_SYSTEM.md §16.5`, audit cluster C03
**Severity:** `error`

**Forbidden:**
```kotlin
val tx = Transaction(category = "Expense", ...)  // ← literal type used as category
val tx = Transaction(category = "Income", ...)
```

**Allowed:**
```kotlin
val tx = Transaction(category = "Uncategorized", ...)  // explicit fallback
val tx = Transaction(category = userSelectedCategory, ...)
```

Categories are distinct from transaction types. `Type` is `Income | Expense | Transfer`. `Category` is `Food | Salary | Fuel | ...`. Mixing them is the bug.

**Implementation:** String constant check on `Transaction` constructor `category` arg. Flag if value equals (case-insensitive) any of: "expense", "income", "transfer".

---

### FY009 — Plural strings must use pluralize()

**Maps to:** `DESIGN_SYSTEM.md §16.13`, audit cluster C10
**Severity:** `warning`

**Forbidden:**
```kotlin
"$count transactions"
"$count debt(s)"
"$count snapshots"
"You have $n loan${if (n != 1) "s" else ""}"
```

**Allowed:**
```kotlin
pluralize(count, "transaction")
pluralize(count, "debt")
resources.getQuantityString(R.plurals.snapshots, count, count)
```

**Implementation:** String template detection. Look for `"$x ${singular}s"` patterns where `singular` ends without trailing `s`. Heuristic, will have false positives — start as `warning`.

---

### FY010 — Negative amounts require semantic_expense

**Maps to:** `DESIGN_SYSTEM.md §16.20`, audit cluster C16
**Severity:** `warning`

**Forbidden:**
```kotlin
Text(text = "−₹2,000")  // no color override, uses default text color
Text(text = formatCurrency(amount), style = FynloTypography.BodyEmphasis)
// where amount < 0
```

**Allowed:**
```kotlin
val color = if (amount < 0) FynloColors.SemanticExpense else FynloColors.TextPrimary
Text(text = formatCurrency(amount), color = color)
```

Better: build a `MoneyText` composable that handles sign coloring automatically.

```kotlin
MoneyText(amount = -2000.0)  // automatically red, automatically − prefix
```

**Implementation:** Harder rule. May need a Compose lint check that flags `Text` composables receiving currency-formatted strings without color logic.

---

### FY011 — Section headers must use type_section_header

**Maps to:** `DESIGN_SYSTEM.md §4.2`, audit point 235 (Settings green bullets)
**Severity:** `error`

**Forbidden:**
```kotlin
Text("• Accounts", style = customSectionStyle)  // custom marker
Text("Accounts", fontSize = 20.sp, fontWeight = FontWeight.Bold)  // raw inline
Text("ACCOUNTS", letterSpacing = 1.5.sp, style = ...)
```

**Allowed:**
```kotlin
SectionHeader(text = "Accounts")  // shared composable
// OR
Text("Accounts", style = FynloTypography.SectionHeader, color = FynloColors.TextPrimary)
```

**Implementation:** Detect leading `•`, `►`, `▸` characters in Text() strings on screens. Detect raw font-size + bold combinations matching SectionHeader spec.

---

### FY012 — FAB must be RoundedCornerShape(20.dp)

**Maps to:** `DESIGN_SYSTEM.md §6.1`
**Severity:** `error`

**Forbidden:**
```kotlin
FloatingActionButton(
    onClick = {},
    shape = CircleShape  // ← Fynlo FAB is rounded square
) { ... }
```

**Allowed:**
```kotlin
FloatingActionButton(
    onClick = {},
    shape = FynloShape.Fab
) { ... }
// Or use the shared FynloFab composable:
FynloFab(onClick = {})
```

---

### FY013 — Touch targets must be ≥48dp

**Maps to:** `DESIGN_SYSTEM.md §14`
**Severity:** `warning`

**Forbidden:**
```kotlin
IconButton(
    onClick = {},
    modifier = Modifier.size(24.dp)  // ← below 48dp minimum
) { Icon(...) }
```

**Allowed:**
```kotlin
IconButton(
    onClick = {},
    modifier = Modifier.size(48.dp)
) { Icon(modifier = Modifier.size(24.dp), ...) }
```

Or wrap small visual icon in invisible 48dp target:
```kotlin
Box(
    modifier = Modifier.size(48.dp).clickable { ... },
    contentAlignment = Alignment.Center
) {
    Icon(modifier = Modifier.size(20.dp), ...)
}
```

---

### FY014 — Icon-only buttons require contentDescription

**Maps to:** `DESIGN_SYSTEM.md §14`
**Severity:** `error`

**Forbidden:**
```kotlin
IconButton(onClick = {}) {
    Icon(Icons.Default.Edit, contentDescription = null)  // ← null
}
IconButton(onClick = {}) {
    Icon(Icons.Default.Edit, "")  // ← empty
}
```

**Allowed:**
```kotlin
IconButton(onClick = {}) {
    Icon(Icons.Default.Edit, contentDescription = "Edit transaction")
}
```

**Tooling:** Android Lint already has `ContentDescription` rule; promote severity to error in `lint.xml`.

---

### FY015 — No localStorage / sessionStorage in artifacts

**Maps to:** Environment constraint (not a Fynlo rule per se, but applies to AI-generated Compose code that's experimental)
**Severity:** `error`

**Forbidden:**
```kotlin
// In any Compose code:
window.localStorage.setItem(...)  // not real Compose, but seen in mixed-codebase mistakes
```

Not relevant to native Android, but documenting in case AI agents mix WebView contexts.

---

### FY016 — Repayment data must use Repayment record, not paid field

**Maps to:** `DESIGN_SYSTEM.md` (n/a), audit cluster C01
**Severity:** `error`

**Forbidden** (after Sprint 1 lands):
```kotlin
data class Borrower(
    val id: String,
    val name: String,
    val amount: Double,
    val paid: Double  // ← FORBIDDEN after C01 migration
)
```

**Allowed:**
```kotlin
data class Borrower(
    val id: String,
    val name: String,
    val amount: Double
)

data class LoanRepayment(
    val id: String,
    val borrowerId: String,
    val amount: Double,
    val date: LocalDate,
    val note: String?,
    val createdAt: Long
)

// Derived:
fun Borrower.totalPaid(repayments: List<LoanRepayment>): Double =
    repayments.filter { it.borrowerId == id }.sumOf { it.amount }
```

**Implementation:** Detect `paid` property declaration on `Borrower` or `Debt` data classes outside of the migration shim (`legacy/`).

---

### FY017 — Exports must call recalculate() first

**Maps to:** audit cluster C02
**Severity:** `error`

**Forbidden:**
```kotlin
fun exportPdf(): File {
    val data = repository.getCurrentState()  // ← may be stale
    return PdfGenerator.generate(data)
}
```

**Allowed:**
```kotlin
fun exportPdf(): File {
    recalculateUseCase.execute()  // refresh interest accruals
    val data = repository.getCurrentState()
    return PdfGenerator.generate(data)
}
```

**Implementation:** Detect functions named `export*` / `generate*Pdf` / `generate*Xlsx` / `generate*Json` that don't call `recalculate*` before reading state. Flag.

---

### FY018 — Schema records must declare schemaVersion

**Maps to:** audit cluster C03 (Stage 3a)
**Severity:** `warning`

**Forbidden:**
```kotlin
fun toJsonBackup(): JsonObject {
    return jsonObjectOf(
        "accounts" to accounts,
        "transactions" to transactions
        // ← no schemaVersion
    )
}
```

**Allowed:**
```kotlin
fun toJsonBackup(): JsonObject {
    return jsonObjectOf(
        "schemaVersion" to BACKUP_SCHEMA_VERSION,
        "appVersion" to BuildConfig.VERSION_NAME,
        "exportedAt" to Instant.now().toString(),
        "accounts" to accounts,
        "transactions" to transactions
    )
}
```

---

### FY019 — Transactions must reference accounts by ID, not name

**Maps to:** audit cluster C03 (Stage 3b)
**Severity:** `error` (after Sprint 6 migration)

**Forbidden:**
```kotlin
data class Transaction(
    val toAcct: String,    // ← name, breaks on rename
    val fromAcct: String
)
```

**Allowed:**
```kotlin
data class Transaction(
    val toAcctId: String,   // ← UUID
    val fromAcctId: String?
)
```

---

### FY020 — Borrower.intType not Borrower.type

**Maps to:** audit cluster C03 (Stage 3b)
**Severity:** `warning` (during migration), `error` (after)

**Forbidden:**
```kotlin
data class Borrower(val type: String?)  // inconsistent with Debt.intType
```

**Allowed:**
```kotlin
data class Borrower(val intType: InterestType?)  // matches Debt
```

---

### FY021 — Debug-only code gated by BuildConfig.DEBUG

**Maps to:** `DESIGN_SYSTEM.md §16.18`, audit point 242 (Developer section visible in release)
**Severity:** `error`

**Forbidden:**
```kotlin
@Composable
fun SettingsScreen() {
    Column {
        // ...
        DeveloperSection(  // ← always shown
            onTestCrash = ...,
            onLoadTestData = ...,
            onWipeData = ...
        )
    }
}
```

**Allowed:**
```kotlin
@Composable
fun SettingsScreen() {
    Column {
        // ...
        if (BuildConfig.DEBUG) {
            DeveloperSection(...)
        }
    }
}
```

Or use the build variant pattern:
```kotlin
// src/debug/kotlin/.../DeveloperSection.kt — actual implementation
// src/release/kotlin/.../DeveloperSection.kt — no-op {}
```

**Implementation:** Detect Composable functions or destructive actions matching naming patterns (`*Crash*`, `*WipeAll*`, `*LoadTest*`, `*ResetAll*`) called outside `if (BuildConfig.DEBUG)` blocks or release-stub files.

---

### FY022 — No hardcoded version strings in exports

**Maps to:** `DESIGN_SYSTEM.md §16.10`, audit point 303
**Severity:** `error`

**Forbidden:**
```kotlin
val footer = "Generated by Fynlo v3.1 | $date"  // ← hardcoded "v3.1"
val footer = "Fynlo 3.2.1"
```

**Allowed:**
```kotlin
val footer = "Generated by Fynlo ${BuildConfig.VERSION_NAME} | $date"
```

**Implementation:** Regex scan in export-related files for hardcoded version-like strings (`v\d+\.\d+`, `\d+\.\d+\.\d+`).

---

### FY023 — Empty state must use shared EmptyState composable

**Maps to:** `DESIGN_SYSTEM.md §9.6`, audit cluster C19
**Severity:** `warning`

**Forbidden:**
```kotlin
// One-off empty state in each screen:
Column {
    Icon(Icons.Default.Star, ...)
    Text("No goals yet")
    Text("Add a goal")
    Button(onClick = ...) { Text("Add First Goal") }
}
```

**Allowed:**
```kotlin
EmptyState(
    icon = Icons.Default.Star,
    title = "No goals yet",
    body = "Set targets for big purchases or milestones",
    ctaLabel = "Add First Goal",
    onCtaClick = ...
)
```

**Implementation:** Pattern-detect a Column with `Icon + Text + Text + Button` arrangement that isn't using the shared component. Warning, not error (too easy to false-positive).

---

### FY024 — updated_at must update on mutation

**Maps to:** audit point 367, cluster C01/C03
**Severity:** `error`

**Forbidden:**
```kotlin
fun updateBorrower(id: String, changes: BorrowerChanges) {
    val borrower = repo.getBorrower(id)
    val updated = borrower.copy(
        amount = changes.amount ?: borrower.amount,
        paid = changes.paid ?: borrower.paid
        // ← updatedAt unchanged
    )
    repo.save(updated)
}
```

**Allowed:**
```kotlin
fun updateBorrower(id: String, changes: BorrowerChanges) {
    val borrower = repo.getBorrower(id)
    val updated = borrower.copy(
        amount = changes.amount ?: borrower.amount,
        paid = changes.paid ?: borrower.paid,
        updatedAt = System.currentTimeMillis()
    )
    repo.save(updated)
}
```

**Implementation:** Detect any `.copy(...)` call on records with `updatedAt` field that doesn't include `updatedAt` in the copy. Flag for review.

---

## 3. Implementation plan

### Sprint 2 deliverable

Implement the **P0/error-severity rules first**:
- FY001 — raw colors
- FY002 — raw dp
- FY003 — raw sp
- FY004 — direct currency format
- FY005 — direct date format
- FY006 — Scaffold FAB requires padding
- FY007 — emoji in dialogs
- FY008 — literal Expense/Income category
- FY011 — section header style
- FY012 — FAB shape
- FY014 — content descriptions
- FY021 — debug code gated

### Sprint 6 deliverable

Add schema-related rules once C03b migration is done:
- FY016 — repayment record (already added in Sprint 1, finalize)
- FY017 — exports recalc first (already added in Sprint 1, finalize)
- FY019 — account ID reference
- FY020 — intType naming
- FY024 — updatedAt on mutation

### Ongoing

Warning-severity rules implemented as time permits:
- FY009 — pluralize()
- FY010 — semantic color on negatives
- FY013 — touch target sizes
- FY018 — schemaVersion
- FY022 — no hardcoded versions in exports
- FY023 — shared EmptyState

---

## 4. Project structure for lint rules

```
fynlo/
├── lint-rules/                          ← new Gradle module
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── app/fynlo/lint/
│           ├── FynloRuleSet.kt          ← Detekt RuleSetProvider
│           ├── RawColorLiteralRule.kt   ← FY001
│           ├── RawDpLiteralRule.kt      ← FY002
│           ├── RawSpLiteralRule.kt      ← FY003
│           ├── DirectCurrencyFormatRule.kt  ← FY004
│           └── ... (one file per rule)
├── lint-baseline.xml                    ← suppressed legacy violations
├── detekt.yml                           ← include FynloRuleSet
└── app/
    └── build.gradle.kts                 ← apply detekt plugin
```

In `app/build.gradle.kts`:
```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt")
}
detekt {
    config = files("$rootDir/detekt.yml")
    baseline = file("$rootDir/lint-baseline.xml")
    autoCorrect = false
}
dependencies {
    detektPlugins(project(":lint-rules"))
}
```

CI invokes `./gradlew detekt`. Failures block PR merge.

---

## 5. Legacy code baseline

Existing code has many violations. Don't fail builds on day one. Generate a baseline once and treat NEW violations as the gate:

```bash
./gradlew detektBaseline    # writes lint-baseline.xml
```

The baseline file is committed. New violations after the baseline date fail the build. Legacy violations remain visible (CI annotates them) but don't block.

Over Sprints 3-5, gradually shrink the baseline as screens are refactored.

---

## 6. Coverage tracking

For each rule, track:
- Number of files violating it (from baseline)
- Number remaining after each sprint
- Target sprint for "baseline = 0"

Maintain in `lint-baseline-progress.md` updated after each sprint.

---

## 7. Adding new rules

When a new pattern needs enforcement:

1. Document the pattern in `DESIGN_SYSTEM.md`
2. Add the FY-number entry to `LINT_RULES.md` (this file)
3. Implement the rule in `lint-rules/`
4. Generate baseline so existing violations don't block
5. Update PR template if applicable
6. Mention in AI_AGENT_PROTOCOL.md if agents need to know

---

**End of LINT_RULES.md v1.0**
