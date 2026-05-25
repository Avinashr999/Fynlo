# Fynlo Sunset Protocol

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** Defines how to wind down Fynlo if/when discontinuation becomes necessary.

---

## 0. Why this document exists

Most apps die quietly. For an app that holds users' financial data, dying quietly is unethical. This protocol ensures that if Fynlo ever ends, users get notice, can export their data, get refunds where applicable, and aren't surprised.

This document is the operational complement to:
- `LEGAL_PROTOCOL.md` §8 (legal obligations on shutdown)
- `TERMS_AND_CONDITIONS.md` §14 (user-facing contract terms)
- `PRIVACY_POLICY.md` §13 (user-facing privacy terms)

---

## 1. When to use this protocol

Trigger conditions:

- Operating Fynlo is no longer financially viable
- Legal / regulatory changes make continuation unsafe
- Avinash's personal circumstances prevent continued maintenance
- Infrastructure (Firebase, Play Store) becomes unavailable
- Security incident makes continuation impossible
- Acquisition / transfer to another entity

If you're reading this protocol because one of these triggered, take a breath. Sunset isn't failure; it's the responsible end of something that served its purpose.

---

## 2. Decision-making

### 2.1 Confirm the decision

Before announcing publicly:
- Have you slept on the decision for at least a week?
- Have you discussed with one trusted advisor?
- Have you reviewed financials (can you keep going at minimum cost)?
- Have you explored alternatives (sell, open-source while keeping running, hibernate)?
- Have you consulted the lawyer? (see `LEGAL_PROTOCOL.md` §13)

### 2.2 Set the shutdown date

Minimum 60 days from announcement for free users.
Minimum 90 days from announcement for paid users (or end of their billing period — whichever is longer).

Choose a date that:
- Gives users time to export
- Aligns with logical financial cycles (end of a tax year is friendly to users tracking financial data)
- Doesn't fall on a holiday weekend

---

## 3. Phase 1 — Announcement (day 0)

### 3.1 Communication channels

Publish simultaneously across:

1. **In-app banner** — appears on every launch, dismissible after acknowledgement
2. **Email** — to every registered Google account
3. **Play Store listing** — update description with shutdown notice
4. **GitHub Pages** — populate `https://avinashr999.github.io/Fynlo/shutdown/`
5. **README.md** — top of file: shutdown notice
6. **CHANGELOG.md** — final entry: shutdown announcement

### 3.2 Announcement template

Use the template in `LEGAL_PROTOCOL.md` §8.6. Customize specifics, keep tone honest and warm.

### 3.3 What to say

- Date of full shutdown
- Why (honest, not evasive; doesn't have to be detailed)
- What stays available when (the wind-down timeline)
- How to export data NOW
- Refund schedule (if paid users exist)
- Contact email for questions
- Sincere thanks

### 3.4 What NOT to say

- Don't blame users
- Don't blame "the market" generically
- Don't make promises you can't keep ("we'll be back!")
- Don't hide the date

---

## 4. Phase 2 — Wind-down (days 0-365)

### 4.1 Days 0-60: Normal operation continues

- All features work
- Sync remains active
- Support emails answered within 7 days
- Banner reminds users to export

### 4.2 Days 60-150: Local-only mode

- App ships an update that disables cloud sync
- Local data still works fully
- Export still works
- New transactions can still be entered locally
- Sign-in still works for read of cloud (last fetch)

### 4.3 Days 150-365: Export-only mode

- App ships another update — read-only after this version
- User can view all data, export, but not add/edit
- Cloud still accessible for export only
- Communication: "You have until [day 365] to export. After that, server data is deleted."

### 4.4 Day 365: Cloud data deletion

- All Firestore data permanently deleted
- All Firebase Auth records deleted
- App still works locally for users who have it installed
- Play Store listing pulled

---

## 5. Refunds

Per `TERMS_AND_CONDITIONS.md` §14.4:

**Annual subscriptions:**
- Pro-rata refund for unused days of current billing period
- Refund within 14 days via Google Play Billing
- No future renewals charged

**Lifetime purchases (₹1,299):**

| Time since purchase | Refund |
|---|---|
| Within 30 days | ₹1,299 (full, per Google Play) |
| 30 days - 1 year | ₹1,299 (full) |
| 1-2 years | ₹650 (50%) |
| 2+ years | Discretionary; recommend ₹300-500 |

### 5.1 Refund processing

1. Generate list of all paid users from Google Play Console
2. For each, calculate refund per schedule
3. Use Google Play developer-initiated refund flow
4. Email each user with refund confirmation
5. Track in `sunset-refunds.csv` (gitignored — contains PII)

### 5.2 Edge cases

- **Users who can't be reached:** still process refund via Google Play (it returns funds even if email bounces)
- **Users who refuse refund:** document in tracking sheet; treat as donation
- **Users who request more than schedule:** evaluate case-by-case; if reasonable, accommodate

---

## 6. Open-source release (optional)

Consider open-sourcing Fynlo at shutdown.

### 6.1 Why
- Lets users self-host if they really care
- Gives back to the community
- Documents the work for future reference
- Some users will appreciate it; the rest don't mind

### 6.2 Why not
- Source might contain not-yet-cleaned secrets (vet thoroughly)
- Lifetime users might feel cheated ("now anyone can have it for free?")
- Maintaining as open-source is more work, not less

### 6.3 If yes

1. Audit repo for any secrets, hardcoded credentials, PII in test data
2. Replace Firebase project ID with placeholder
3. Add OSS license (MIT or Apache 2.0 — Apache 2.0 has explicit patent grant, slightly better)
4. Add `CONTRIBUTING.md` explaining the project is unmaintained but PRs accepted for self-hosters
5. Push to public repo (or flip Avinashr999/Fynlo from private to public)
6. Announce in the shutdown email + GitHub Pages

---

## 7. Data deletion

### 7.1 Firestore

On day 365: run cleanup script:

```python
# pseudocode — implement when needed
from firebase_admin import firestore

db = firestore.client()

# Delete all user data
for collection in ['users', 'transactions', 'borrowers', 'debts', 'investments', 'goals']:
    for doc in db.collection(collection).stream():
        doc.reference.delete()

# Delete acceptance records LAST (need them for legal defense for 7 years post-shutdown)
# Move to archive collection instead:
for doc in db.collection('acceptance').stream():
    db.collection('acceptance_archive').document(doc.id).set(doc.to_dict())
    doc.reference.delete()
```

Verify deletion is complete. Firebase Console → Firestore → confirm collections empty.

### 7.2 Firebase Auth

Delete all auth users:
- Firebase Console → Authentication → Users → Select All → Delete

### 7.3 Analytics + Crashlytics

These delete on Firebase's own schedule (14 months for Analytics, 90 days for Crashlytics). No manual action needed.

### 7.4 What to retain

- Acceptance records — 7 years post-shutdown (legal defense)
- Payment records — 7 years post-shutdown (tax law)
- Incident reports — forever (in `incidents/` folder, Git-versioned)
- Lawyer correspondence — forever
- The source code — forever (Git history)

Store retained records per `BACKUP_PROTOCOL.md`.

---

## 8. Infrastructure shutdown

After Day 365:

| Service | Action | When |
|---|---|---|
| Play Store listing | Unpublish | Day 365 |
| Firebase Firestore | Data deleted but project kept (for auth archive) | Day 365 |
| Firebase Hosting (if used) | Static shutdown page stays for 1 year | Day 365-730 |
| Firebase Auth | Users deleted, project kept | Day 365 |
| GitHub repo | Stays public (open-source) or private (archived) | Permanent |
| GitHub Pages | Stays as memorial page or redirect | Permanent or 2 years |
| Domain (if registered) | Renew for 1 more year so links don't break, then let lapse | Year 2 |
| Business bank account | Close after final refunds clear | Day 90 after last refund |
| GST registration | Cancel via GST portal | After last filing covering shutdown period |
| OPC | Strike off via MCA (optional; keeps OPC dormant otherwise) | After all legal obligations cleared, usually 2+ years post-shutdown |

---

## 9. Personal closure

For Avinash:
- Save final copy of repo + Drive backups to long-term archive (USB drive in safe)
- Take screenshots of last app state, Firebase Console, Play Store listing
- Write a personal retrospective (not for publication — for you)
- Allow yourself to grieve the project; it's normal for personal work

For users:
- Thank them genuinely
- Offer to help with migration to alternative apps (suggest 2-3 options)
- Stay reachable at the email for at least 1 year for follow-up questions

---

## 10. Acquisition / transfer alternative

If someone offers to buy / take over Fynlo before shutdown:

### 10.1 Evaluate
- Are they competent to maintain it?
- Will they respect existing T&C and Privacy Policy?
- Will they honor lifetime users?
- Do they have legal capacity (registered entity)?

### 10.2 Document
- Asset purchase agreement (lawyer-drafted)
- Transfer of: source code, keystore, Firebase project, Play Store listing
- Continuation of T&C / Privacy Policy with the new operator

### 10.3 Notify users
- 60 days before transfer
- "Fynlo is being transferred to [new operator]. They will honor your existing terms. You can delete your account before transfer if you prefer."

### 10.4 Then transfer
- Play Console: developer account transfer (Google has a process)
- Firebase: add new owner; remove yourself
- GitHub: transfer repo ownership
- Update DNS, legal docs

This is preferable to shutdown if a willing successor exists.

---

## 11. Cross-references

- `LEGAL_PROTOCOL.md` §8 — legal obligations
- `TERMS_AND_CONDITIONS.md` §14 — user contract
- `PRIVACY_POLICY.md` §13 — user privacy commitments
- `BACKUP_PROTOCOL.md` — what to preserve forever
- `DATA_RECOVERY_PROTOCOL.md` — if data needs recovery during wind-down

---

## 12. The one rule

**Sunset Fynlo the way you'd want any service you depend on to be sunset: with honest notice, with time to export, with refunds where due, and with respect.**

---

**End of SUNSET_PROTOCOL.md v1.0**
