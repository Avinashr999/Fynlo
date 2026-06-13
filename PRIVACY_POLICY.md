# Fynlo — Privacy Policy

**Document version:** 1.0
**Effective date:** June 13, 2026
**Last updated:** 2026-05-25
**URL:** https://avinashr999.github.io/Fynlo/privacy/

---

## ⚠️ Notice to developer (delete before publishing)

> This document is filled with reasonable defaults but **still requires lawyer review** before publishing or showing to any user.
>
> Before going live:
>
> 1. Have an Indian tech lawyer review the entire document (DPDP Act 2023, IT Act 2000, Consumer Protection Act 2019)
> 2. Verify all factual claims match what Fynlo actually does (data inventory in §3, third-party SDKs in §5)
> 3. Set the effective date
> 4. Once OPC is registered, replace "Avinash Reddy Karri" with the OPC name throughout
> 5. Confirm the Firestore region in §6 (should be `asia-south1` / Mumbai — verify in Firebase Console)
> 6. Delete this notice block
>
> Specific items to ask the lawyer about:
> - Are the DPDP user rights in §8 worded correctly?
> - Should there be additional disclosure for ad-supported tier in §5.3 (currently only AdMob mentioned)?
> - Is the data retention in §7 defensible? (Esp. 7-year acceptance records vs DPDP minimum)
> - Is the children's privacy clause in §10 sufficient? (Fynlo is 18+ only)
> - Should the policy state Crashlytics is opt-in by default vs current opt-out plan?

---

## 1. Who we are

This Privacy Policy explains how **Fynlo** ("we", "us", "our", "the App") collects, uses, stores, and protects your personal information.

Fynlo is operated by **Avinash Reddy Karri** (the "Operator"), based in Kakinada, Andhra Pradesh, India.

*Note: Operator status will update to a registered One Person Company (OPC) before monetization launch. Users will be notified of the change via in-app notice.*

Contact for privacy matters:

- **Email:** avinash999.reddy@gmail.com (subject: "Fynlo Privacy")
- **Postal address:** [TO BE ADDED — Avinash's business address in Kakinada]
- **Grievance Officer:** Avinash Reddy Karri — avinash999.reddy@gmail.com (subject: "Fynlo Grievance") — per DPDP Act 2023 §10

---

## 2. Scope

This Privacy Policy applies to:

- The Fynlo mobile application available on Google Play Store and other distribution channels
- Any data we collect, process, store, or share in connection with your use of Fynlo

This Privacy Policy does NOT apply to:

- Third-party services you access through Fynlo (Google account services, WhatsApp links, SMS links)
- Any other app or service not explicitly branded as Fynlo

---

## 3. What data we collect

### 3.1 Data you provide directly

When you use Fynlo, you enter information manually. This includes:

- **Account information:** Account names, balances, transaction types
- **Transaction data:** Date, amount, category, description, notes for each transaction
- **Loan & debt records:** Borrower/lender names, phone numbers, loan amounts, interest rates, repayment history, notes
- **Investment records:** Investment names, amounts, types, values, dates
- **Contact information:** Names and phone numbers you add to your Contact Book (used for loan reminders only)
- **Savings goals:** Goal names, target amounts, dates, progress
- **Budget settings:** Category budgets, alert thresholds
- **Recurring transaction definitions:** Names, amounts, frequencies

### 3.2 Data we collect automatically

- **Authentication data via Google Sign-In:** Your name, email address, and unique Google ID. We do NOT receive your Google password.
- **Device information:** Device model, operating system version, app version, screen size, language preference
- **Usage data via Firebase Analytics:** Which screens you visit, which features you use, how often you open the app (no personal financial values are logged)
- **Crash data via Firebase Crashlytics:** Stack traces and device state if the app crashes (we make best effort to exclude personal financial values from logs)
- **Synchronization metadata:** Timestamps of when records were created or modified, conflict resolution data

### 3.3 Data we do NOT collect

We do NOT collect:

- Your Google password
- Your bank account credentials, UPI PINs, OTPs, or any banking authentication
- Your real bank account numbers or card numbers (Fynlo is a manual ledger; we never connect to your actual bank accounts)
- Your contact list from your phone (we only store contacts you manually add)
- Your photos, location, or files outside what you explicitly upload
- Biometric data (your fingerprint stays on your device; we receive only a yes/no unlock signal)
- Any data that allows us to access your bank, investment, or financial accounts

---

## 4. How we use your data

We use your data for these purposes:

| Purpose | Legal basis (DPDP) | Data used |
|---|---|---|
| Provide the app's core features | Consent | All entered data |
| Sync your data across your devices | Consent | All entered data |
| Authenticate you | Consent + legitimate use | Google sign-in data |
| Improve the app | Legitimate interest | Analytics, crash data |
| Send you optional notifications (loan reminders, budget alerts) | Consent | Loan + budget data |
| Comply with legal obligations | Legal requirement | As required by law |
| Defend legal claims | Legal requirement | As needed for the specific claim |
| Process subscription payments (when monetization is active) | Contract performance | Payment data (via Google Play; we don't see your card) |
| Serve ads (when ad-supported tier is active) | Consent | Limited usage data per ad-network requirements |

We do NOT use your data to:

- Sell to third parties
- Train AI models
- Profile you for commercial decisions outside Fynlo
- Share with marketing partners

---

## 5. Third-party services we use

Fynlo uses these third-party services, each with their own privacy policies:

### 5.1 Google services

- **Google Sign-In** — for authentication. [Google Privacy Policy](https://policies.google.com/privacy)
- **Firebase Firestore** — for cloud data sync. [Firebase Privacy](https://firebase.google.com/support/privacy)
- **Firebase Authentication** — for managing your account. [Same as Firebase]
- **Firebase Crashlytics** — for crash reporting. [Same as Firebase]
- **Firebase Analytics** — for usage analytics. [Same as Firebase]
- **Google Play Store** — for app distribution and (when monetization is active) subscription billing. [Google Play Privacy](https://play.google.com/intl/en/about/privacy/)

Firestore data is stored in the `asia-south1` (Mumbai) region.

### 5.2 Optional third-party links

Fynlo links to (but does not embed):

- **WhatsApp** — when you tap "Send WhatsApp reminder" on a loan, Fynlo opens WhatsApp with a pre-filled message. We don't transmit anything to WhatsApp ourselves.
- **SMS** — same as above; Fynlo opens your default SMS app.
- **Google Play Store** — when you tap "Rate on Play Store" in Settings.

### 5.3 Advertising partners

Currently no ads are shown. If Fynlo introduces an ad-supported tier in the future:

- **Google AdMob** — for serving ads. AdMob may collect device identifiers (Google Advertising ID), approximate location (via IP), and ad interaction data. [AdMob privacy](https://support.google.com/admob/answer/6128543)

We do NOT share any of your financial data with ad networks. Ad targeting is based only on AdMob's own profile of your device — not on your loans, transactions, or balances within Fynlo.

You can opt out of personalized ads in your device's Settings → Google → Ads → Opt out of Ads Personalization.

This Privacy Policy will be updated to disclose any new ad networks before they are integrated, with 30 days notice to existing users for material changes.

### 5.4 Future SDKs

Any future third-party SDK added to Fynlo will be disclosed in this section before its first use, with notification to existing users via the app.

---

## 6. Where your data is stored

- **On your device:** All your data is stored locally on your device using Android's secure storage (Room database).
- **In the cloud:** A copy is stored in Google's Firebase Firestore servers in the `asia-south1` (Mumbai) region.
- **In backups you create:** When you export a JSON or XLSX backup via Fynlo's export features, the file is stored wherever you choose to save it (your device's Downloads folder, Google Drive, etc.). After that point, the file is no longer under our control.

---

## 7. How long we keep your data

| Data | Retention |
|---|---|
| Your active app data | Until you delete it or close your account |
| Your account (after closure) | Deleted within 30 days of your account-deletion request |
| Backups in our control | Same as account data |
| Crash reports | 90 days (Firebase Crashlytics default) |
| Analytics events | 14 months (Firebase Analytics default) |
| Acceptance records (ToS, Privacy Policy) | 7 years after account closure (for legal defense) |
| Payment records (when monetization is active) | 7 years (Indian tax law) |
| Records subject to ongoing legal claim | Until claim is resolved |

When the retention period expires, data is deleted or anonymized.

---

## 8. Your rights

Under the Digital Personal Data Protection Act 2023 (India), you have these rights:

### 8.1 Right to access

You can request a copy of all personal data we hold about you.

**How to exercise:** Settings → Backup & Export → Export JSON Backup. This gives you a complete copy of your data in a machine-readable format.

For data beyond what the in-app export covers (e.g., your analytics or crash reports), email avinash999.reddy@gmail.com (subject: "Fynlo Data Access").

### 8.2 Right to correction

You can correct any inaccurate or incomplete data.

**How to exercise:** Edit the data directly in the app. For data you can't edit directly (e.g., your authenticated email), contact us.

### 8.3 Right to erasure

You can request that we delete all your data.

**How to exercise:** Settings → Profile & Security → Delete my account & all data. This permanently deletes your data from your device AND from our servers within 30 days.

Some data may be retained longer where legally required (e.g., payment records for tax purposes, acceptance records for legal defense, per §7).

### 8.4 Right to grievance redressal

If you have a complaint about how we handle your data, contact our Grievance Officer:

- **Name:** Avinash Reddy Karri
- **Email:** avinash999.reddy@gmail.com (subject: "Fynlo Grievance")
- **Postal:** [TO BE ADDED — Kakinada business address]

We will respond within 7 days. If you're not satisfied with our response, you may escalate to the **Data Protection Board of India** per DPDP Act 2023.

### 8.5 Right to nominate

You may nominate another person to exercise your rights in case of your death or incapacity.

**How to exercise:** Currently not supported in-app (planned for a future release). For now, contact us via email with details of your nomination.

### 8.6 Right to withdraw consent

You can withdraw consent for any specific processing.

**How to exercise:**

- For analytics & crash reporting: Settings → Privacy → Diagnostics & Usage (planned in upcoming release)
- For optional notifications: Settings → Notifications
- For all processing: Delete your account (see §8.3)

Withdrawing consent does not affect processing already done lawfully before the withdrawal.

---

## 9. Security

We take these measures to protect your data:

- **Encryption in transit:** All communication between the app and our servers uses TLS encryption
- **Encryption at rest:** Firestore encrypts stored data using Google's standard encryption
- **Authentication:** Google Sign-In with optional PIN + biometric lock on the device
- **Access control:** Only your authenticated account can access your data; other users cannot
- **Regular security review:** We periodically review our Firestore security rules and access patterns

However, no system is perfectly secure. If a data breach occurs, we will:

- Notify you as soon as practicable (target: within 72 hours of confirming the breach)
- Notify the Data Protection Board of India as required
- Take immediate steps to contain and remediate the breach
- Provide guidance on protecting yourself

---

## 10. Children's privacy

Fynlo is intended for users aged 18 and above. We do not knowingly collect data from children under 18.

If we learn that we've collected data from a child under 18 without parental consent, we will delete it promptly.

If you believe a child has provided us data, please contact us at avinash999.reddy@gmail.com (subject: "Fynlo Child Data Concern").

---

## 11. International users

Fynlo is designed primarily for users in India. If you use Fynlo from outside India:

- Your data may be stored in India (Firestore `asia-south1` region) and is subject to Indian law
- You may have additional rights under your local privacy laws (GDPR for EU, CCPA for California, etc.)
- We will honor those rights to the extent we are legally required to

For specific cross-border concerns, contact us at avinash999.reddy@gmail.com.

---

## 12. Changes to this Privacy Policy

We may update this Privacy Policy from time to time. When we do:

- **Minor changes** (clarifications, typo fixes): Effective immediately. Latest version always available at https://avinashr999.github.io/Fynlo/privacy/
- **Material changes** (new data collected, new uses, new third parties): At least 30 days notice via in-app notification and email. You may need to re-accept before continuing to use the app.

If you don't agree to material changes, you can delete your account before the changes take effect.

Material change history:

- v1.0 — Initial Privacy Policy (effective June 13, 2026)

---

## 13. Discontinuation of Fynlo

Fynlo may at some future point be discontinued. If that happens:

### 13.1 Notice

We will notify you at least 60 days before any reduction in service, via:

1. In-app notification on every launch
2. Email to your registered Google account
3. Update to the Play Store listing
4. A public notice at https://avinashr999.github.io/Fynlo/shutdown/

### 13.2 Data export window

For at least 12 months after the discontinuation announcement, you will be able to export your data via the in-app export feature OR (if the app is no longer available) via a web-based export tool we will provide at https://avinashr999.github.io/Fynlo/shutdown/

### 13.3 Data deletion timeline

- **First 60 days after announcement:** All features continue
- **Days 60-150:** Local app works; cloud sync disabled
- **Days 150-365:** Export-only mode (no new entries possible)
- **Day 365+:** All cloud data permanently deleted

If you want your data deleted immediately rather than waiting through this schedule, you can request immediate deletion at any time during the wind-down period.

### 13.4 Refunds for paid users

If you have an active subscription when Fynlo is discontinued, refunds are provided per the schedule in our Terms & Conditions §14.4. Refunds are processed through Google Play Billing within 14 days.

### 13.5 Acquisition or transfer

If Fynlo is acquired by another entity or its operations are transferred:

- We will notify you at least 60 days before the transfer
- You can delete your account before the transfer
- The new operator will inherit the obligations of this Privacy Policy
- Any new policy from the new operator will require your explicit acceptance

### 13.6 Open-source release

At our discretion, we may release Fynlo's source code as open-source upon discontinuation, allowing motivated users to self-host. This is not guaranteed.

---

## 14. Contact us

For any questions about this Privacy Policy or how we handle your data:

- **Email:** avinash999.reddy@gmail.com
  - Subject "Fynlo Privacy" for privacy questions
  - Subject "Fynlo Grievance" for formal grievances
  - Subject "Fynlo Data Access" for access requests
- **Postal:** [TO BE ADDED — Avinash's business address in Kakinada]
- **Grievance Officer:** Avinash Reddy Karri (contact as above)

We aim to respond within 7 days. For DPDP-related grievances, we will respond within the timelines mandated by the DPDP Act.

---

## 15. Legal basis (summary)

This Privacy Policy is governed by:

- **Information Technology Act 2000** and its rules (India)
- **Digital Personal Data Protection Act 2023** (India)
- **Consumer Protection Act 2019** (India)
- **Indian Contract Act 1872**
- The terms of our **Terms & Conditions** at https://avinashr999.github.io/Fynlo/terms/
- Applicable rules and regulations issued from time to time

For users outside India, additional local laws may apply where mandated.

---

**Document version 1.0**
**Effective date:** June 13, 2026
**URL:** https://avinashr999.github.io/Fynlo/privacy/
