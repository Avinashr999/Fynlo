# Fynlo Analytics Protocol

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** Defines what events Fynlo logs to Firebase Analytics, what's forbidden, and how analytics is used.

---

## 0. Why this document exists

Analytics is a tool. Used right, it tells you which features matter and where users get stuck. Used wrong, it becomes surveillance, privacy violation, or just noise. This protocol exists to keep Fynlo's analytics useful AND respectful.

---

## 1. Principles

1. **Counts, not values.** "User exported PDF" is fine; "User exported PDF showing balance of ₹50,000" is forbidden.
2. **Aggregate, not individual.** Dashboard answers "how many users use feature X?" not "what does user Y do?"
3. **Opt-in (post DPDP-compliance).** Analytics defaults OFF until user explicitly enables in Settings.
4. **No financial values, ever.** Loan amounts, balances, transaction values must NEVER be in analytics events.
5. **Pseudonymous user IDs only.** Use Firebase's auto-generated `app_instance_id`, not Google email.
6. **Decisions should drive metrics, not the other way around.** Pick what you want to learn first, then add the metric. Don't track "just because."

---

## 2. Allowed events

### 2.1 Lifecycle (auto-collected by Firebase)
- `first_open` — first launch
- `session_start` — session begins
- `app_remove` — app uninstalled
- `screen_view` — built-in screen tracking

### 2.2 Feature engagement (custom, no values)
- `transaction_added` — params: `{ type: "income" | "expense" | "transfer" }` (no amount)
- `loan_added` — no params
- `debt_added` — no params
- `investment_added` — params: `{ type: "fd" | "stocks" | "mf" | "other" }` (no value)
- `goal_added` — no params
- `budget_set` — no params
- `recurring_added` — no params

### 2.3 Exports
- `export_pdf` — params: `{ source: "reports" | "history" }`
- `export_json` — no params
- `export_xlsx` — no params

### 2.4 Critical paths
- `recalculate_tapped` — for understanding C01 mitigation effectiveness (DELETE this event after C01 closes; no longer useful)
- `account_deletion_requested` — for understanding churn

### 2.5 Onboarding funnel
- `signup_started`
- `signup_completed`
- `terms_accepted`
- `first_transaction_added` — distinguish from `transaction_added`; ONE-TIME event marking activation

### 2.6 Errors
- `error_recoverable` — params: `{ category: "sync" | "export" | "import" | "other" }` (no error message text — that's PII risk)
- `crash_recovered` — if user restored from auto-recovery

### 2.7 Settings changes
- `setting_changed` — params: `{ setting: "currency" | "date_format" | "theme" | etc. }` (the setting name, NOT the value chosen)

---

## 3. Forbidden events / parameters

Never log:
- Any amount in rupees (or any currency)
- Account names
- Borrower / lender names
- Phone numbers
- Email addresses (already covered by Firebase auto-collection of UID; don't double-log)
- Transaction descriptions / notes
- Investment names
- Specific dates that could correlate to user life events
- Free-text user input

Examples:

```kotlin
// FORBIDDEN
firebaseAnalytics.logEvent("loan_added") {
    param("amount", 50000.0)                       // ← FORBIDDEN — financial value
    param("borrower", "Ravi Kumar")                // ← FORBIDDEN — PII
    param("phone", "9876543210")                   // ← FORBIDDEN — PII
}

// ALLOWED
firebaseAnalytics.logEvent("loan_added") {
    param("interest_tier", "moderate")             // ← OK — categorical, non-PII
    param("from_screen", "loans_tab")              // ← OK — UX flow info
}
```

---

## 4. User properties (Firebase)

User properties are persistent per-user attributes. Allowed:
- `account_type` — "free" / "trial" / "annual" / "lifetime" (after monetization)
- `app_version` — auto-set by Firebase
- `device_type` — "phone" / "tablet" (set in onboarding)
- `years_using` — incremented annually, capped at 5

Forbidden:
- Email address
- Name
- Total transaction count (could be PII proxy)
- Total loan portfolio value
- Any financial value

---

## 5. Opt-in flow

### 5.1 Default state

After DPDP-compliance update (Sprint X): analytics defaults OFF for new users.

### 5.2 In-app opt-in

Settings → Privacy → Diagnostics & Usage → toggle ON

Display copy:
```
Help improve Fynlo

If you opt in, Fynlo collects anonymous usage statistics (which screens you visit, 
which features you use). We never log your financial data or personal information.

You can turn this off anytime.

[ Turn ON ]  [ Keep OFF ]
```

### 5.3 Existing users

For users who installed before the opt-in update, show a one-time dialog explaining the change and asking for explicit consent. Default to OFF if they dismiss.

### 5.4 Disabling

User toggles OFF → call:
```kotlin
FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
```
This stops collection immediately. Historical data remains in Firebase per its retention (14 months default).

---

## 6. Reviewing analytics data

### 6.1 Cadence

- **Weekly:** Active users, crash-free rate, top screens viewed
- **Monthly:** Feature adoption (which features have >10% MAU), onboarding funnel, retention curves
- **Quarterly:** Cohort analysis, feature deprecation candidates (low usage = candidate for removal)

### 6.2 What to look for

- New features ship → does usage match expectations?
- Funnel drop-off (signup_started → signup_completed) — where are users abandoning?
- Recalculate frequency before vs after C01 fix — did fix change behavior?
- Power user vs casual user feature mix

### 6.3 What NOT to do

- Don't try to identify individual users from analytics
- Don't infer user identity from rare combinations of properties
- Don't share aggregated data with third parties without anonymizing thoroughly
- Don't make UX decisions from a sample of 1 (e.g., "a user reported X, so let's redesign Y")

---

## 7. A/B testing (future)

If Fynlo adopts A/B testing (Firebase Remote Config + Analytics):

- Use Firebase Remote Config for variant assignment
- Set user property `experiment_X = control/variant`
- Run experiments for fixed duration (typically 7-14 days)
- Stop experiments and apply winner cleanly — don't leave dead variants in code

Don't:
- Run experiments without a hypothesis
- Run experiments with insufficient sample size
- Run experiments on financial features without extra care (don't A/B test data integrity!)

---

## 8. BigQuery export (advanced, future)

If you connect Firebase Analytics to BigQuery for deeper analysis:

- All BigQuery datasets are subject to same PII rules
- Don't export to BQ if it would expose data you can't trust your tooling chain to protect
- Cost: BigQuery is paid; budget ~₹100-500/month for small project
- Skip until analytics volume justifies it (>10,000 MAU)

---

## 9. Ads + Analytics interaction (if ads tier launches)

If Fynlo introduces ads:
- AdMob has its own analytics, separate from Firebase Analytics
- AdMob may collect device-level data for ad targeting (Google Ad ID, approximate location)
- This is NOT user financial data; AdMob doesn't see Fynlo's Firestore
- Disclose AdMob's collection separately in `PRIVACY_POLICY.md` §5.3
- Subscribers (paid tier) should NEVER see ads → set user property `account_type` to gate

---

## 10. Decommissioning events

When a feature is removed, its events become noise. Plan:

1. Mark deprecated events in code with `// DEPRECATED v3.X — remove after v4.X`
2. Stop sending the event in v4.X
3. Don't query the event in dashboards
4. Firebase retains historical data per its retention; let it expire naturally

---

## 11. Cross-references

- `PRIVACY_POLICY.md` §3-4 — what users see about analytics
- `PRIVACY_PROTOCOL.md` §3 — internal data flow rules
- `LEGAL_PROTOCOL.md` §5.2 — DPDP consent
- `LINT_RULES.md` (future FY025) — code-level enforcement

---

## 12. The one rule

**Count behaviors, never values. If you can't justify the event without revealing PII or financial details, you can't log it.**

---

**End of ANALYTICS_PROTOCOL.md v1.0**
