# Fynlo Legal Protocol

**Version:** 1.0 - REQUIRES LAWYER REVIEW BEFORE PUBLISHING USER-FACING DOCS
**Last updated:** 2026-05-25
**Audience:** Avinash (developer), future co-developers, AI agents working on legal-touching changes
**Status:** Filled-in template. Lawyer must still review before monetization or first paid user.

---

## Warning: Critical disclaimer

This document is a **filled-in template** produced by an AI assistant. It is NOT legal advice. It is NOT a final legal document.

Before relying on any clause, before publishing the linked `PRIVACY_POLICY.md` or `TERMS_AND_CONDITIONS.md` at their hosted URLs, and before charging any user or showing any advertisement in Fynlo:

1. **Consult a qualified Indian tech/SaaS lawyer.** Areas: Consumer Protection Act 2019, Digital Personal Data Protection Act 2023 (DPDP), Information Technology Act 2000, GST law, Andhra Pradesh-specific consumer rules.
2. **Budget Rs 10,000-30,000 for initial legal setup.** Covers OPC registration consultation, ToS/Privacy Policy review, DPDP compliance check.
3. **Document the lawyer's name, contact, and review date** in Section 13.

Operating Fynlo as a paid app without lawyer-reviewed documents exposes Avinash personally to claims significantly larger than the lawyer's fee.

---

## 0. Why this document exists

`AI_AGENT_PROTOCOL.md` governs how agents touch code. `DESIGN_SYSTEM.md` governs how UI is built. `UX_AUDIT_2026-05-25.md` governs what bugs get fixed first.

**This document governs how Fynlo handles money, user data, legal obligations, and the user relationship.**

Without an entity, Avinash carries personal liability for the app's failures. Without ToS, default consumer law applies - often unfavorably. Without a Privacy Policy, Fynlo is in violation of DPDP and Play Store policies. Without a shutdown protocol, stopping Fynlo invites consumer court claims. Without retained records, defending any future claim becomes nearly impossible.

---

## 1. Current legal status of Fynlo

(Update this section as status changes.)

| Item | Status | Notes |
|---|---|---|
| Operator name | Avinash Reddy Karri | Sole proprietor / personal capacity (until OPC registered) |
| Business entity | NOT YET REGISTERED | OPC registration is a pre-monetization requirement |
| GST registration | N/A | Required if revenue exceeds Rs 20 lakh/year |
| Bank account in entity name | N/A | Use personal account until entity exists |
| ToS published | DRAFT - `TERMS_AND_CONDITIONS.md` | Will be hosted at `https://avinashr999.github.io/Fynlo/terms/` once GitHub Pages enabled |
| Privacy Policy published | DRAFT - `PRIVACY_POLICY.md` | Will be hosted at `https://avinashr999.github.io/Fynlo/privacy/` |
| Lawyer consulted | NOT YET | Required before monetization |
| Play Store listing | INTERNAL TESTING | Production rollout blocked by C01 + legal review |
| Monetization | DORMANT IN CODE | Billing UI/prices prepared; FeatureFlags.BILLING_ENABLED remains false for this AAB |
| Paid users | ZERO | App used by developer only |
| Pricing (when launched) | Rs 149/month, Rs 999/year with 7-day trial, hidden founding lifetime Rs 2,799 for first 100 users | Subject to lawyer review |
| Liability cap | Rs 999 | Per Section 11 of T&C; subject to lawyer review |

---

## 2. Pre-monetization checklist

**Do NOT charge any user or show any ad until ALL of the following are true:**

- [ ] OPC (One Person Company) registered with Ministry of Corporate Affairs
- [ ] PAN obtained for the OPC
- [ ] GST registered (mandatory above Rs 20 lakh annual revenue; recommended earlier for OPC operating SaaS)
- [ ] Business bank account opened in OPC's name
- [ ] Lawyer consulted at least once on monetization plan
- [ ] `TERMS_AND_CONDITIONS.md` reviewed by lawyer and published at the stable URL
- [ ] `PRIVACY_POLICY.md` reviewed by lawyer and published at the stable URL
- [ ] Refund policy reviewed against Google Play Developer Distribution Agreement
- [ ] In-app links to ToS + Privacy Policy from About screen AND from any payment screen
- [ ] Acceptance flow - user explicitly accepts ToS + Privacy Policy at sign-up
- [ ] DPDP compliance audit done (data inventory, retention, deletion mechanism, grievance officer)
- [ ] If ads: Privacy Policy specifically discloses all ad networks
- [ ] If subscription: Google Play Billing integrated correctly, restore-purchases tested
- [ ] Crashlytics + Analytics confirmed NOT logging financial PII
- [ ] Backup of all legal documents in `BACKUP_PROTOCOL.md` location

**Until every box is checked, monetization is OFF. Free-only operation continues.**

---

## 3. Choosing entity type

**Recommendation: One Person Company (OPC).**

| Type | Setup cost | Annual compliance | When |
|---|---|---|---|
| Sole Proprietorship + GST | ~Rs 2,000 | ~Rs 5,000/year | Avoid for monetized app - unlimited personal liability |
| **One Person Company (OPC)** | **~Rs 6,000-10,000** | **~Rs 15,000/year** | **Recommended for Fynlo** |
| Private Limited (Pvt Ltd) | ~Rs 15,000 | ~Rs 25,000+ | Only if planning co-founders / funding |
| LLP | ~Rs 10,000 | ~Rs 15,000/year | Requires 2+ partners - skip |

**Registration path:**
- Use ClearTax, IndiaFilings, or VakilSearch (~Rs 6,000-10,000 service fee + government fees)
- OR file directly via MCA portal if comfortable with the paperwork
- Timeline: 10-15 working days
- You'll receive Certificate of Incorporation, PAN, TAN

Once registered, update Section 1 of this document AND amend all references in `LEGAL_PROTOCOL.md`, `PRIVACY_POLICY.md`, `TERMS_AND_CONDITIONS.md` from "Avinash Reddy Karri" to "Fynlo [OPC name] (OPC) Private Limited."

---

## 4. Tax & GST obligations

(India-specific. CA to confirm based on actual revenue.)

- **GST registration threshold:** Rs 20 lakh annual revenue (state-specific variations apply). Recommended to register earlier if OPC is operational, regardless of revenue.
- **GST on app subscriptions:** 18% (SaaS classification). You collect from user, remit to government.
- **GST on app sales (founding lifetime purchases):** Same 18%.
- **TDS on Google Play payouts:** Google deducts TDS before remitting your revenue. Keep all Google payout statements.
- **Income tax:** Net profits taxable as business income; OPC rate ~25% on income up to Rs 400 crore.
- **Yearly compliance:** GST returns (monthly/quarterly), income tax returns, ROC filings (for OPC).

**Hire a CA before first paid user.** Budget Rs 5,000-15,000/year for compliance. Cheaper than penalties.

---

## 5. User data handling (DPDP compliance)

DPDP Act 2023 applies to Fynlo NOW - even before monetization - because the app processes personal data of Indian users.

### 5.1 Data inventory (current state)

| Data | Collected via | Stored | Purpose | Retention |
|---|---|---|---|---|
| Email + name | Google Sign-In | Firebase Auth + Firestore | Authentication, sync | Until user deletes account |
| Transactions | User entry | Local Room DB + Firestore | Core function | Until user deletes |
| Borrower/lender names + phones | User entry | Local + Firestore | Loan tracking, reminders | Until user deletes |
| Investment records | User entry | Local + Firestore | Portfolio tracking | Until user deletes |
| Crash reports | Firebase Crashlytics | Crashlytics servers | Debugging | 90 days (default) |
| Analytics events | Firebase Analytics | Analytics servers | Usage understanding | 14 months (default) |
| Device ID | Firebase | Firebase | Anti-abuse | App lifetime |

Update this table every time Fynlo adds a new data collection point or third-party SDK.

### 5.2 DPDP rights to honor

Indian users have these rights under DPDP Act 2023:

- **Right to access** - Settings > Backup & Export > Export JSON Backup
- **Right to correction** - User edits in-app
- **Right to erasure** - Settings > Profile & Security > Delete my account & all data (must wipe both local + Firestore + Crashlytics + Analytics)
- **Right to nominate** - TBD (currently unsupported; flag in app's About > Privacy notice)
- **Right to grievance redressal** - Avinash Reddy Karri is Grievance Officer; contact: avinash999.reddy@gmail.com (subject line: "Fynlo Grievance")

### 5.3 Required design changes for DPDP compliance

Add to UX_AUDIT as new cluster (after C22):

- [ ] In-app "Export my data" button (JSON export covers it, but formalize the labeling)
- [ ] In-app "Delete my account & all data" already exists; verify it actually deletes from Firestore, Crashlytics, Analytics (audit this)
- [ ] Grievance officer contact added to About screen
- [ ] DPDP notice at sign-up
- [ ] Analytics opt-out toggle in Settings
- [ ] Crash reporting opt-out toggle in Settings

### 5.4 Data breach notification

If a security incident exposes user data:

- Notify affected users as soon as practicable (within 72 hours target)
- Notify the Data Protection Board of India per DPDP rules
- Document the incident, response, and remediation

Draft breach notification email template to be stored in `LEGAL_PROTOCOL_ASSETS/` (to be created).

---

## 6. Monetization-specific rules

### 6.1 Pricing (as planned)

| Tier | Price | Includes |
|---|---|---|
| Monthly subscription | Rs 149/month | Pro features, auto-renews unless cancelled |
| Annual subscription | Rs 999/year | Pro features, auto-renews unless cancelled; 7-day trial when offered |
| Founding Lifetime | Rs 2,799 one-time | Hidden launch offer for first 100 eligible users |
| Free | Rs 0 | Defined in Terms Section 10 (free tier feature scope) |

### 6.2 Subscription rules

- Use Google Play Billing exclusively (Play Store policy)
- Subscription terms shown clearly at point of purchase
- Auto-renewal enabled by default; user can disable via Play Store Subscriptions
- Honor Google Play's refund rules (first 48 hours full refund, case-by-case after)
- Don't lock previously-free features behind paywall without 30+ days notice to existing users
- Restore purchases flow must work
- Subscription cancellation must link to Play Store subscription management

### 6.3 Founding Lifetime purchase rules

- One-time payment via Google Play Billing
- Definition: "lifetime" = lifetime of the Fynlo service while operated by Avinash Reddy Karri or successor entities; ends per discontinuation procedure in Section 8
- Refunds on discontinuation per Section 8.3
- Founding Lifetime users retain access if the subscription tier is discontinued, provided the founding lifetime tier is also continued (avoid breaking founding lifetime purchasers' access)

### 6.4 Ads (if implemented)

- Use only Google AdMob (no other ad networks without amending this document)
- Privacy Policy must list AdMob explicitly
- Don't show ads to anyone explicitly identified as under 18 (defensive - Fynlo is 18+)
- Don't show financial-product ads adjacent to user's loan/debt data (deceptive practice risk)
- Frequency caps: max 1 interstitial per 5 user actions

### 6.5 Pricing changes

If prices change for active subscriptions:
- Notify existing subscribers 30 days in advance
- Honor existing pricing through current billing period
- Use Google Play's price-change flow

---

## 7. Recordkeeping

| Record | Where | Retention | Why |
|---|---|---|---|
| User ToS/Privacy acceptance events | Firestore + offline JSON backup | 7 years | Defense against "I never agreed" claims |
| Payment records (Google Play payout statements) | Local + Google Drive backup | 7 years | Income tax + audit |
| User support correspondence | Email folder "Fynlo Support" | 3 years | Defense against bad-faith claims |
| Bug reports / crash data | Crashlytics + local export | 1 year | Pattern analysis |
| All deployed app versions (AAB files) | Drive backup + Git tag | Forever | Reproducing issues from any past version |
| Database schema versions | Git (always) | Forever | Restoring/auditing old data |
| Legal documents | Drive backup + signed PDFs | Forever | Establishing what you promised when |
| Lawyer correspondence | Email + Drive | Forever | Audit trail |

All records must be accessible if Avinash is unavailable. See `BACKUP_PROTOCOL.md` (to be written).

---

## 8. Discontinuation protocol

### 8.1 Decision criteria

Fynlo may be discontinued if:
- Operating it is no longer financially viable
- Legal/regulatory changes make continuation unsafe
- Personal circumstances prevent continued maintenance
- Technical infrastructure (Firebase, Play Store) becomes unavailable
- Security incident makes user safety unmanageable
- Acquisition or transfer to another entity

This right is exercised under Terms Section 14.

### 8.2 Notice period

- **Free users:** 60 days minimum notice before service ceases
- **Paid annual subscribers:** 90 days minimum notice, OR end of current billing period (whichever is longer)
- **Founding Lifetime users:** 90 days minimum notice + refund per Section 8.3

Notice channels:
1. In-app banner on every launch (dismissible after acknowledgement)
2. Email to registered Google account
3. Update to Play Store listing
4. Static page at `https://avinashr999.github.io/Fynlo/shutdown/`

### 8.3 Refund obligations

**Annual subscriptions:**
| Situation | Refund |
|---|---|
| Cancelled by user during Google Play free trial | Full (per Google Play default) |
| Cancelled within 48 hours of first purchase | Full (per Google Play default) |
| Fynlo discontinued mid-billing-period | Pro-rata refund for unused days |

**Founding Lifetime purchases (Rs 2,799):**

| Time since purchase | Refund |
|---|---|
| Within 30 days | Rs 2,799 (full, subject to Google Play policy) |
| Within 1 year | Rs 2,799 (full) |
| 1-2 years | Rs 1,400 (approximately 50%) |
| 2+ years | Discretionary goodwill refund, typically Rs 500-750 |
Refunds via Google Play developer-initiated refund flow. Document each refund.

### 8.4 Data preservation timeline

After shutdown announcement:

| Period | Status |
|---|---|
| Days 0-60 (notice period) | All features continue. Sync remains active. |
| Days 60-150 | Local app works. Cloud sync disabled. JSON/XLSX export works. |
| Days 150-365 | Local-only mode. Optional self-hosted export. Firestore data archived for user-initiated export. |
| Day 365+ | Firestore data permanently deleted. App officially end-of-life. |

A static webpage at `https://avinashr999.github.io/Fynlo/shutdown/` explains:
- What happened
- How to export remaining data
- Refund status
- Contact for questions

### 8.5 Open-source on shutdown

At Avinash's discretion at the time of discontinuation, the Fynlo source code may be released under an open-source license (MIT, Apache 2.0, or similar) so users can self-host. This is NOT a guarantee; document the choice when the time comes.

### 8.6 Communication template

Draft user shutdown notification:

```
Subject: Important: Fynlo will be discontinued on [DATE]

Hi [Name],

I'm writing to share difficult news: Fynlo will be discontinued on [DATE].

What this means for you:
- Until [DATE], the app continues to work normally.
- Between [DATE] and [DATE + 90], the app works locally but cloud sync is disabled.
- Until [DATE + 365], you can export your data anytime.
- After [DATE + 365], all data on our servers will be permanently deleted.

What you should do now:
1. Export your data: Settings > Backup & Export > Export JSON Backup
2. Save the file somewhere safe (computer, Google Drive, etc.)
3. If you're a paid user, your refund of Rs [AMOUNT] will be processed within 14 days.

Why this is happening:
[Brief honest reason]

I'm sorry for the inconvenience. Thank you for using Fynlo.

Questions? Email avinash999.reddy@gmail.com (subject: "Fynlo Shutdown").

Avinash Reddy Karri
Fynlo
```

### 8.7 Acquisition / transfer

If Fynlo is sold or transferred:
- Notify users 60 days in advance
- Give users option to delete account before transfer
- New entity inherits all obligations under existing ToS/Privacy Policy
- Old data practices remain in effect unless user explicitly accepts new ones

This follows Google's app ownership transfer process on Play Store.

---

## 9. Incident response (legal angle)

### 9.1 Severity levels

- **P0 - Data breach / data loss affecting users:** Respond within hours. Notify users + DPB India.
- **P1 - Privacy violation:** 24-48 hours response.
- **P2 - Service outage > 24 hours:** Communicate via status page + Play Store listing.
- **P3 - User complaint with legal escalation hint:** Respond within 7 days. Never ignore.

### 9.2 C01 (Recalculate destruction) legal status

The C01 bug destroys Rs 54K of payment history per tap in test data. Until Sprint 1 ships the fix:

- No external users > C01 is a P0 engineering issue, not yet a P0 legal incident
- After monetization without C01 fix > C01 becomes catastrophic legal exposure (data loss + paying users)

**Hard rule: Do not promote to Play Store production until C01 is verified fixed.**

If any external user reports affected data BEFORE Sprint 1 ships:
1. Acknowledge within 24 hours
2. Attempt recovery from their backups (JSON/XLSX)
3. Document the case in incident log
4. Apologize in writing
5. Offer remediation (free subscription extension, full refund, or both)

---

## 10. Liability limits & insurance

### 10.1 Liability cap

**Liability cap: Rs 999 (Indian Rupees nine hundred ninety-nine only).**

Set deliberately below the founding lifetime purchase price (Rs 2,799) so the clause is clearly a limitation, not a disguised full-refund. Defensibility considerations:

- Rs 999 is below the founding lifetime price and approximately one annual subscription cycle; lawyer review required before paid launch
- Cap applies "in aggregate to all claims" - not per claim, not per incident
- Cap does NOT shield against statutory liabilities (Consumer Protection Act, DPDP Act, fraud, gross negligence) - these are excluded from the cap by Section 11.2 of T&C

Final wording is in `TERMS_AND_CONDITIONS.md` Section 11.2. **Lawyer must specifically review this clause before publication.**

### 10.2 What the cap CANNOT shield against

Indian law does not allow contracts to exclude:
- Consumer Protection Act 2019 compensation orders (district forum can award independent of contract)
- DPDP Act penalties (paid to government, up to Rs 250 crore)
- Liability for fraud or fraudulent misrepresentation
- Liability for gross negligence
- Death or personal injury
- Other liabilities specified by Indian law

Realistic worst-case scenario for a single user dispute under Consumer Protection Act:
- Full refund of price paid: Rs 2,799
- "Mental agony" compensation: Rs 5,000-25,000
- Legal costs: Rs 2,000-10,000
- **Total: Rs 10,000-40,000 despite Rs 999 cap**

The cap reduces routine claims significantly but does not eliminate consumer-court exposure.

### 10.3 Insurance

For solo developers, professional liability + cyber liability insurance starts around Rs 15,000-30,000/year.

**Skip until paid revenue exceeds Rs 2 lakh/month.** Below that, policy cost outweighs probable claims.

---

## 11. Dispute resolution

ToS specifies (per `TERMS_AND_CONDITIONS.md` Section 16):
- **Jurisdiction:** Courts of Kakinada, Andhra Pradesh, India
- **Governing law:** Laws of India
- **Initial dispute resolution:** Email to avinash999.reddy@gmail.com (subject "Fynlo Dispute"), 30 days for response before formal action
- **Consumer Protection Act forum** - users retain statutory right to file with district/state consumer forum independent of Section 16

### 11.1 If sued

If a legal notice or court summons arrives:
1. Don't respond yourself. Contact lawyer immediately.
2. Don't delete any records related to the dispute.
3. Preserve user data from any deletion until matter resolved.
4. Don't communicate with opposing party except through lawyer.

### 11.2 Consumer forum

Most likely venue for small user disputes. Filing fee nominal (Rs 100-500). User doesn't need a lawyer; you do.

Have lawyer's contact ready (Section 13).

---

## 12. Periodic legal review

| Review | Frequency | Owner |
|---|---|---|
| ToS + Privacy Policy version | Annually | Lawyer + Avinash |
| DPDP compliance audit | Annually | Avinash (with lawyer) |
| Tax filing review | Monthly/quarterly | CA |
| Insurance review (if active) | Annually | Avinash |
| Privacy policy update for new SDKs | On each addition | Avinash |
| ToS update for major feature changes | On each major release | Lawyer + Avinash |

Set calendar reminders.

---

## 13. Contacts

| Role | Name | Contact | Last Updated |
|---|---|---|---|
| Lawyer (tech/SaaS) | TBD | TBD | - |
| CA (chartered accountant) | TBD | TBD | - |
| Company secretary (if OPC) | TBD | TBD | - |
| Insurance agent | N/A | - | - |
| Google Play developer support | Via Play Console | - | - |
| Firebase support | Via Firebase Console | - | - |
| DPB India (grievance escalation) | TBD per DPDP rules | - | - |
| Fynlo Grievance Officer | Avinash Reddy Karri | avinash999.reddy@gmail.com | 2026-05-25 |

Fill in lawyer/CA contacts when engaged. Backup all contact info per `BACKUP_PROTOCOL.md`.

---

## 14. Acceptance flow specification

For implementation in Fynlo (add to UX_AUDIT as new cluster, P0 priority - blocks monetization):

On user's first sign-in (or first launch after acceptance flow ships):

1. Show ToS + Privacy Policy summary screen
2. Two scroll-to-accept checkboxes:
   - "I accept the Terms & Conditions" - link to full document at the GitHub Pages URL
   - "I accept the Privacy Policy" - link to full document
3. "Continue" button enabled only when both checkboxes are checked
4. Record acceptance event in Firestore with: userId, document version (T&C v1.0, Privacy v1.0), timestamp, app version
5. Re-prompt on any material change to either document (when version bumps to v2.0+)

This creates the legal record that the user agreed.

### 14.1 Storage of acceptance records

```kotlin
// Firestore document
// /acceptance/{userId}_{timestamp}
{
  userId: "google-uid-here",
  docType: "terms" | "privacy",
  docVersion: "1.0",
  timestamp: 1779648184763,
  appVersion: "3.3.0",  // when this was shown
  deviceModel: "OnePlus CPH2767",
  acceptanceMethod: "checkbox" | "click-through",
  ipAddress: null  // do NOT collect IP without disclosure
}
```

Records exported to JSON backup with each user backup, AND retained on server even after account deletion (per Section 7 retention rules).

---

## 15. Cross-references

- **`PRIVACY_POLICY.md`** - user-facing privacy disclosures (hosted at `https://avinashr999.github.io/Fynlo/privacy/`)
- **`TERMS_AND_CONDITIONS.md`** - user-facing service terms (hosted at `https://avinashr999.github.io/Fynlo/terms/`)
- **`BACKUP_PROTOCOL.md`** - keystore, business records, contact info redundancy (to be written)
- **`INCIDENT_PROTOCOL.md`** - operational incident response (to be written)
- **`UX_AUDIT_2026-05-25.md`** - add new cluster C23 for "Legal acceptance flow + DPDP UI changes" before monetization

---

## 16. Adding to this document

Any change involving:
- Business entity status
- Monetization model
- New data collection
- New third-party SDK
- New jurisdiction (e.g., users outside India)
- Major service-scope change

...requires updating this document AND `PRIVACY_POLICY.md` AND `TERMS_AND_CONDITIONS.md`, AND lawyer review before going live.

Track changes via Git commits. Tag each commit affecting legal posture with `[legal]` prefix.

---

## 17. The one paragraph that matters

**Operating Fynlo as a paid app without a registered OPC, lawyer-reviewed ToS + Privacy Policy, DPDP compliance, and proper recordkeeping exposes Avinash personally to consumer-court claims, DPDP penalties, tax non-compliance, and Play Store takedown. First-year setup cost: Rs 20,000-40,000. Skip-it cost in claims, penalties, and lost business: Rs 50,000-25,00,000. Don't skip.**

---

**End of LEGAL_PROTOCOL.md v1.0**

**Next step:** Schedule lawyer consultation. Bring this document, `PRIVACY_POLICY.md`, and `TERMS_AND_CONDITIONS.md`. Ask the lawyer to mark up all three.







