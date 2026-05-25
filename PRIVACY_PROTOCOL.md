# Fynlo Privacy Protocol (Internal)

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** Internal procedures for handling user data in compliance with DPDP and privacy commitments. Distinct from `PRIVACY_POLICY.md` (user-facing).

---

## 0. Why this document exists

`PRIVACY_POLICY.md` tells users what we do with their data. **This document tells us (Avinash, future contributors, AI agents) how to actually do it.** Without internal procedures, the public commitments are aspirational.

---

## 1. Data minimization principle

Default to collecting as little as possible:

- If you don't need a data point to deliver a feature, don't collect it
- If you can compute something locally, don't send it to Firestore
- If a feature works without analytics, don't add analytics
- If user can opt out of a collection, default opt-out is safer than default opt-in

Before adding any new data collection, answer:
1. Why do we need this?
2. Can the feature work without it?
3. How long do we keep it?
4. Is this disclosed in `PRIVACY_POLICY.md`?
5. Will user be asked for consent?

If you can't answer all five, don't ship.

---

## 2. PII classification

| Tier | Data | Handling |
|---|---|---|
| **Tier 1 — Critical** | None currently (no banking creds, no SSN-equivalent) | Forbidden to collect |
| **Tier 2 — Sensitive** | Name, email, Google UID, phone numbers, financial values, transaction history | Encrypted at rest + in transit; access logged |
| **Tier 3 — Operational** | Device model, OS version, app version, screen size | Standard storage |
| **Tier 4 — Aggregated/anonymous** | Crash counts, feature usage counts (no per-user values) | Standard storage |

Rule: never log Tier 2 data to Crashlytics or Analytics in a way that ties to specific values. Per-user identifiers OK, but no "loan amount = 50000" in stack traces.

---

## 3. What gets sent to which third party

| Data | To Firestore | To Crashlytics | To Analytics | To AdMob (future) |
|---|---|---|---|---|
| Name (Google sign-in) | Yes | Hashed UID only | No | No |
| Email | Yes (auth) | No | No | No |
| Transactions | Yes | No | No (event counts only) | No |
| Loan amounts | Yes | No | No | No |
| Borrower names + phones | Yes | No | No | No |
| Investment values | Yes | No | No | No |
| Crash stack traces | No | Yes | No | No |
| Screen view events | No | No | Yes (no values) | No |
| Device ID | No | Yes | Yes | Yes |

**Rule:** Crashlytics and Analytics MUST NOT receive financial values. If a stack trace would include a value, scrub it.

---

## 4. PII scrubbing

In any code that logs or sends data outside Firestore:

```kotlin
// BAD
Log.d("Loan", "Created loan for ${borrower.name} of ₹${amount}")
Crashlytics.log("Failed to update loan id=${loan.id} paid=${loan.paid}")

// GOOD
Log.d("Loan", "Created loan for borrower (id hash: ${borrower.id.hashCode()})")
Crashlytics.log("Failed to update loan (id hash: ${loan.id.hashCode()})")

// BEST
// No logging of any user-specific value; rely on stack trace + breadcrumbs
```

Add to `LINT_RULES.md` (future): FY025 — no PII in Crashlytics or Analytics calls.

---

## 5. User consent

### 5.1 At signup

User must explicitly accept:
- Terms & Conditions
- Privacy Policy

Implementation per `LEGAL_PROTOCOL.md` §14. Record event in Firestore with: userId, doc versions, timestamp, app version.

### 5.2 For optional processing

- **Analytics:** Default OFF post-DPDP compliance update. User opts in via Settings → Privacy → Diagnostics & Usage.
- **Crash reporting:** Default OFF post-DPDP compliance update. User opts in.
- **Optional notifications:** Default OFF; user opts in per notification type.
- **Ads (future):** Default OFF for personalized ads.

### 5.3 Withdrawing consent

Settings → Privacy → toggle off any of the above. When toggled off:
- Stop sending new data
- Don't delete old data (legitimate processing already happened); user can full-delete via account deletion
- Apply within 24 hours

---

## 6. Data access controls

### 6.1 Who can access user data

- **Avinash:** Has Firebase admin access for debugging / recovery
- **No one else** — currently no other contributors
- **Lawyer:** Only on signed engagement, only for specific legal matters
- **No analytics teams** — no contractors, no consultants

### 6.2 Logging access

Firebase Audit Logs are on by default for Firestore. Verify:
- Firebase Console → Project Settings → Integrations → Cloud Audit Logs enabled
- Logs retained per Firebase default (90 days)

For any manual data access (Firestore Console direct query / edit): document in `incidents/YYYY-MM-DD-data-access-{reason}.md`. Even if it's for legitimate recovery, log it.

### 6.3 No exporting users' data outside Firebase

Don't:
- Download Firestore data to your local machine for analysis
- Run scripts that pull user data
- Share aggregated dashboards with PII

Exception: explicit DSAR (data subject access request) where user asked for their own data — fulfill via in-app export, not by you manually pulling.

---

## 7. Third-party SDK introduction

Before adding any new SDK to Fynlo:

1. Read its privacy policy. What does it collect?
2. Verify it's compliant with DPDP / GDPR
3. Document the addition in this doc + `PRIVACY_POLICY.md` §5
4. Get the addition lawyer-reviewed if it's anything beyond basic Firebase/Google
5. Add 30-day user notice before activating
6. Update `LEGAL_PROTOCOL.md` §5.1 inventory

Default answer for "should I add SDK X?": no. Defaults that minimize SDK count are safer.

---

## 8. Data retention enforcement

Per `PRIVACY_POLICY.md` §7, retention periods are committed. Internal enforcement:

| Data | Retention | Enforcement mechanism |
|---|---|---|
| Active user data | Until user deletes | User-controlled |
| Closed-account data | 30 days | Cloud Function deletes 30 days after account deletion request |
| Crashlytics | 90 days | Firebase default; verify in Console |
| Analytics | 14 months | Firebase default; verify in Console |
| Acceptance records | 7 years post-account-deletion | Cloud Function moves to archive collection; manual purge at 7 years |

Set calendar reminders for manual retention enforcement (e.g., annual review of archive collection).

---

## 9. Cross-border transfers

Currently: all data stored in `asia-south1` (Mumbai). No cross-border transfer.

If users outside India start using Fynlo significantly: review whether their data still goes to Mumbai (likely yes for sync), whether this satisfies their local laws, and update Privacy Policy if needed.

---

## 10. DPDP-specific obligations

### 10.1 Grievance officer

Avinash Reddy Karri serves as Grievance Officer. Contact: avinash999.reddy@gmail.com (subject "Fynlo Grievance"). Must respond within 7 days.

### 10.2 Notice at collection

At each point of data collection, user must understand what's being collected and why. Current implementation: Privacy Policy disclosed at signup. Future: in-app inline disclosures for new collection points.

### 10.3 Children

Fynlo is 18+. If user indicates being under 18 (or you suspect it from data patterns), suspend the account, request age verification, and delete if minor.

---

## 11. Breach response

If a breach occurs:

1. **Immediately:** Contain the breach (rotate credentials, revoke compromised tokens, restrict access)
2. **Within 24 hours:** Assess scope (how many users, what data, what risk to them)
3. **Within 72 hours:** Notify Data Protection Board of India (per DPDP rules)
4. **Within 72 hours:** Notify affected users
5. **Within 7 days:** Publish public statement if widespread
6. **Within 30 days:** Postmortem in `incidents/`

See `INCIDENT_PROTOCOL.md` §3 for P0 response procedure.

---

## 12. AI agents and PII

Anthropic's Claude (and other AI agents) used to develop Fynlo MAY have access to:
- Source code (no PII)
- Test data / fixtures (no real user PII)
- Documentation (no PII)

AI agents MUST NOT be given:
- Real user data from Firestore
- Real user backups
- Real production credentials
- Anything tagged Tier 2 PII from §2

If you need an AI to debug something involving real user data, redact PII first (use the JSON parsing tool from `DATA_RECOVERY_PROTOCOL.md` to scrub names/values).

---

## 13. Periodic audit

Annually:
- Review §3 (data flow to third parties) — still accurate?
- Review §7 (third-party SDKs) — any new ones added?
- Review §8 (retention) — Cloud Functions still running?
- Review §10 (DPDP compliance) — any rule changes from DPB?
- Update this doc + `PRIVACY_POLICY.md` to match

---

## 14. The one rule

**Treat user data the way you'd want yours treated. If you wouldn't be OK with a company doing X with your finances, don't do X with theirs.**

---

**End of PRIVACY_PROTOCOL.md v1.0**
