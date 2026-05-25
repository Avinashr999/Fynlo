# Fynlo Backup Protocol

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** Defines what must be backed up to survive catastrophic events.

---

## 0. Why this document exists

Fynlo has single points of failure that, if lost, end the project:

- **Release keystore** (`secrets/fynlo-release.jks`) — if lost, you can NEVER publish another update to the existing Play Store listing. Users stuck on whatever version they have. Project effectively dead.
- **Firebase project credentials** — if lost, no cloud sync, no analytics, no crash reports.
- **Source code** — if GitHub disappears and you have no local copy, project gone.
- **Business records** — if you can't prove ownership / acceptance / payments, legal defense impossible.
- **Avinash himself** — if you're unavailable for months, can anyone else access these?

This protocol defines the backup strategy that prevents any single failure from ending Fynlo.

---

## 1. Backup principle: 3-2-1

For every critical asset:

- **3 copies** of the data
- **2 different storage media** (local disk + cloud)
- **1 off-site** (geographically separate)

Examples:
- Keystore on (1) local SSD, (2) Google Drive encrypted, (3) USB drive in physical safe → satisfies 3-2-1
- Source code on (1) local disk, (2) GitHub.com, (3) GitLab mirror → satisfies 3-2-1

---

## 2. Critical assets inventory

| Asset | Where it lives | Backup tier | Restore procedure |
|---|---|---|---|
| Release keystore (`fynlo-release.jks`) | `secrets/` (gitignored) | TIER 1 — never lose | §3 |
| Keystore password | Password manager | TIER 1 — never lose | §3 |
| Source code | GitHub `Avinashr999/Fynlo` | TIER 2 | §4 |
| All shipped AAB files | Play Console + local | TIER 2 | §5 |
| Firebase project credentials | Firebase Console + local | TIER 2 | §6 |
| Firestore data (your test account) | Firestore + JSON exports | TIER 3 | §7 |
| Business records (legal docs, payouts, acceptance logs) | Multiple places | TIER 2 | §8 |
| Contact info (lawyer, CA, banker) | Encrypted notes | TIER 2 | §9 |

---

## 3. TIER 1 — Release keystore

**This is the single most important asset for Fynlo's survival.**

### 3.1 Storage locations

- **Primary:** `C:\Users\user\AndroidStudioProjects\Fynlo\secrets\fynlo-release.jks` (gitignored, on dev machine)
- **Backup 1:** Encrypted ZIP on personal Google Drive (folder: "Fynlo / Critical")
- **Backup 2:** Encrypted ZIP on USB drive stored at home (physical safe or locked drawer)
- **Backup 3 (recommended):** Encrypted ZIP shared with a trusted family member (parent, sibling) with instructions sealed in an envelope marked "Fynlo recovery — open if I'm unreachable for 30+ days"

### 3.2 Encryption

Use AES-256 via 7-Zip or VeraCrypt. Password stored in a password manager (1Password, Bitwarden, KeePass) with master password also backed up.

### 3.3 Verification cadence

- **Monthly:** Verify primary keystore still works (`./gradlew :app:bundleProdRelease` succeeds)
- **Quarterly:** Restore the Drive backup to a temp folder, verify it matches primary (file hash compare)
- **Annually:** Refresh the USB backup, replace if drive shows wear

### 3.4 If keystore is lost

If you discover the keystore is gone:
1. Don't panic; check all backup locations first
2. If truly lost: contact Google Play developer support immediately
3. Google may allow upload of a NEW key with proof of identity, but this is complex and not guaranteed
4. Worst case: app effectively can never be updated; new release would require new package name + listing migration

**Prevention >>> recovery here. Don't lose this.**

---

## 4. TIER 2 — Source code

### 4.1 Storage locations

- **Primary:** Local disk (Windows dev machine)
- **Cloud backup 1:** `github.com/Avinashr999/Fynlo` (private repo)
- **Cloud backup 2 (recommended):** Mirror to GitLab or Bitbucket via Git remote
  - One-line setup: `git remote add gitlab git@gitlab.com:avinashr999/fynlo.git`
  - Push: `git push gitlab --all && git push gitlab --tags`

### 4.2 GitHub-specific safety

- Enable 2FA on GitHub account
- Use a personal access token, not password, for git operations
- Token stored in password manager
- If account compromised: GitHub support can help recover, but a mirror is faster

### 4.3 Local disk safety

Windows dev machine should be backed up:
- Windows File History to external drive (weekly)
- OR cloud backup (Backblaze, IDrive, etc.)
- OR at minimum, the `AndroidStudioProjects\Fynlo` folder copied to Google Drive monthly

### 4.4 Verification

Monthly: clone the GitHub repo to a temp folder and verify it builds.

---

## 5. TIER 2 — Shipped AAB files

Every signed AAB you upload to Play Store should be backed up:

- **Primary:** `app/build/outputs/bundle/prodRelease/*.aab` (lives in build folder, may be deleted)
- **Permanent local:** `releases/` folder in repo (gitignored, but local)
  - Naming: `releases/fynlo-v3.2.2-vc124-2026-05-25.aab`
- **Cloud:** Google Drive folder "Fynlo / Releases"

Tag in git: `git tag -a v3.2.2-released -m "Released to Play Store on 2026-05-25"`

Why: lets you reproduce issues from any past version, or roll back to a known-good build.

---

## 6. TIER 2 — Firebase credentials

### 6.1 What to backup

- `google-services.json` (in `app/` — already in git, but verify)
- Firebase service account JSON (for any server-side scripts) — gitignored, backup separately
- Firestore security rules (`firestore.rules` — already in git)
- Firebase project configuration screenshots (Console → Project Settings)

### 6.2 Where

- Local: `secrets/` folder
- Cloud: Google Drive "Fynlo / Firebase"

### 6.3 Project credentials

Document the Firebase project ID, project number, and which Google account owns the project. Without this info, future-you can't even find the project in Console.

Save in encrypted notes: `Firebase project: [project-id], owner: avinash999.reddy@gmail.com`

---

## 7. TIER 3 — Firestore data

Your test data in Firestore can be backed up:

- **Manual:** Use the app's JSON export feature on your dev device
- **Automated:** Firebase scheduled exports (paid feature; ~$0.18/GiB-month) → to Cloud Storage bucket
  - Set up once: Firebase Console → Firestore → Backups → Schedule
  - Retention: 30 days minimum

For users' data, see `LEGAL_PROTOCOL.md` §5 — users do their own backups via in-app export.

---

## 8. TIER 2 — Business records

| Record | Location | Backup |
|---|---|---|
| Legal documents (final ToS, Privacy, etc.) | Repo + Drive | Versioned in git; Drive has signed PDFs |
| Lawyer correspondence | Email folder "Fynlo Legal" | Email backup via Google Takeout quarterly |
| Payment records (Google Play payouts) | Email + Drive | Drive folder "Fynlo / Payouts / YYYY" |
| OPC registration documents | Drive "Fynlo / Entity" | Physical copies in home safe |
| GST registration | Same | Same |
| CA correspondence | Email "Fynlo Tax" | Quarterly email export |
| User acceptance records | Firestore | Firestore scheduled backup (§7) |
| Incident reports | Repo `incidents/` | Git-versioned automatically |

---

## 9. TIER 2 — Critical contacts

If you're unreachable for 30+ days, someone needs to be able to:
- Pay the OPC's compliance fees
- Respond to legal notices
- Communicate shutdown to users
- Access the Firebase project to extend / pay for services

### 9.1 Contact emergency kit

Create a sealed envelope or a single password-protected document containing:

```
=== Fynlo Emergency Kit ===
For: [Trusted family member name]

If Avinash is unreachable for 30+ days, follow these steps:

1. Access master password manager: [URL] using password [redacted, in separate envelope]
2. From there, find:
   - Google account credentials (for Play Console, Firebase, Gmail)
   - Keystore password
   - GitHub access token

3. Contacts to notify:
   - Lawyer: [Name, phone, email] — to pause legal obligations
   - CA: [Name, phone, email] — to handle tax filings
   - Bank: [Bank, branch, account number] — to manage business account

4. Critical actions (within first 30 days):
   - Notify Play Console: Settings → Account details → mark account as on hold
   - Pause Firebase billing OR keep paying minimum to avoid service shutdown
   - Communicate to users via in-app banner: "Fynlo is paused; updates will resume when [reason]"

5. If permanent: follow `SUNSET_PROTOCOL.md` from the repo

Repo location: github.com/Avinashr999/Fynlo (login with credentials above)
```

Store in physical safe with trusted family member.

### 9.2 Contact list to keep updated

| Role | Name | Phone | Email | Last verified |
|---|---|---|---|---|
| Family contact (emergency) | TBD | TBD | TBD | — |
| Lawyer | TBD | TBD | TBD | — |
| CA | TBD | TBD | TBD | — |
| Bank manager | TBD | TBD | TBD | — |
| Google support contact | Play Console support | — | — | — |

Update this list when contacts change. Re-verify annually.

---

## 10. Backup verification cadence

| What | Frequency | Method |
|---|---|---|
| Keystore primary works | Monthly | Build AAB, verify signature |
| Keystore Drive backup works | Quarterly | Download, decrypt, hash compare |
| Source code GitHub | Daily (automatic) | Every git push |
| Source code mirror | Weekly | `git push gitlab --all` |
| Local disk backup | Weekly | Windows File History or equivalent |
| Firebase exports running | Monthly | Firebase Console → Backups → check status |
| Business records sync | After every release | Save AAB, update tracking |
| Emergency kit currency | Annually | Re-seal envelope with current contacts |
| Restore drill (full disaster recovery) | Annually | Section §11 |

---

## 11. Disaster recovery drill

Once a year, simulate losing everything except your backups:

1. Set up a fresh laptop (or clean VM)
2. Install Android Studio, Git, JDK
3. Clone the repo from GitHub
4. Restore keystore from Drive backup (decrypt, verify hash)
5. Build a release AAB
6. Verify signature matches the keystore
7. Do NOT actually upload to Play Store

If anything fails, fix the backup before declaring the drill complete. Document any gaps.

---

## 12. Cost summary

Free tier:
- GitHub free private repo
- Google Drive 15 GB free
- Gmail / Google Takeout
- Local disk backup

Optional paid:
- Google Drive 200 GB (₹130/month) — recommended once business records grow
- Firebase scheduled exports (~₹15/month for small project)
- VeraCrypt — free
- 7-Zip — free
- Password manager (Bitwarden free tier works) — ₹0
- USB drive — ~₹500 one-time
- Physical safe (small fireproof) — ~₹2,000-5,000

Total recurring: ~₹200/month for proper backup hygiene. Cheap relative to "lose everything."

---

## 13. The one rule

**The release keystore is the most important file in this project. If you lose it, the project ends. Back it up like your life depends on it — because the project's life does.**

---

**End of BACKUP_PROTOCOL.md v1.0**
