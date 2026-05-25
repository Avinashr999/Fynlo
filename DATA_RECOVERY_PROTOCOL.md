# Fynlo Data Recovery Protocol

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** Defines procedures for recovering user data after corruption, loss, or destructive bugs.

---

## 0. Why this document exists

Fynlo has at least one known data-destruction bug (C01 — Recalculate Balances). Until C01 ships, users who tap Recalculate lose payment history. After 3.2.2 ships, edge cases may still produce corruption.

This document defines:
- How to recognize data loss has occurred
- How to recover from local backups, cloud, and exports
- How to forensically reconstruct missing data
- How to communicate with affected users
- How to prevent the same loss happening twice

---

## 1. Types of data loss

| Type | Example | Recoverability |
|---|---|---|
| **Field-level destruction** | `paid` field wiped (C01) | High — recoverable if backup exists |
| **Record deletion** | User accidentally deleted a transaction | Medium — recoverable from cloud or local backup |
| **Schema corruption** | Migration script broke records | High — recoverable from backup |
| **Cloud sync conflict (wrong winner)** | Two devices, older record overwrote newer | Medium — depends on conflict log |
| **Local DB corruption** | Room database file corrupted | High — cloud sync still has data |
| **Cloud + local both wiped** | Catastrophic — backup is only hope | Low — only from user's exported files |
| **No backup available** | User never exported, cloud + local both gone | None — communicate honestly |

---

## 2. Sources of recoverable data, in priority order

1. **In-app cloud sync (Firestore)** — primary source of truth if local is corrupted
2. **In-app local DB (Room)** — primary source if cloud is corrupted
3. **User's most recent JSON backup export** — full snapshot at export time
4. **User's most recent XLSX backup export** — same as JSON but Excel format
5. **User's most recent PDF report** — partial; can extract some values manually
6. **Firestore version history** (if enabled) — point-in-time recovery
7. **Crashlytics breadcrumbs** — may contain field values for affected records
8. **Transaction reconstruction** — derive lost data from related records

---

## 3. Recovery procedure (by scenario)

### 3.1 C01 — Recalculate destroyed `paid` field

**Symptoms:** User reports loan/debt now showing full original amount as outstanding, when partial repayments had been made.

**Recovery steps:**

1. Ask user: "Do you have a backup from before the issue? Settings → Backup & Export → Export JSON Backup"
2. If yes:
   - Parse the JSON backup
   - Extract `borrowers[].paid` and `debts[].paid` values
   - Verify timestamps — backup must be from BEFORE the Recalculate tap
   - Apply paid values via admin tool (manual Firestore edit) or restore flow
3. If no JSON backup, try XLSX backup (same data, different format)
4. If no backup at all:
   - Check Firestore version history (Firebase Console → Firestore → document → revisions)
   - If versioning enabled: roll back the document to pre-bug version
   - If not enabled: data is unrecoverable from cloud
5. Last resort — reconstruct from related records:
   - If user has `loanRepayments[]` audit history (post-Sprint 1 schema): sum up payments per loan
   - If they have transaction records mentioning repayments: extract and re-apply
   - Communicate honestly: "We recovered ₹X of ₹Y; the rest cannot be recovered. Sorry."

**Document the case in:** `incidents/YYYY-MM-DD-c01-recovery-{shortUserId}.md`

### 3.2 User deleted a transaction by mistake

**Symptoms:** User reports "I deleted a transaction but didn't mean to."

**Recovery steps:**
1. If transaction was deleted within last 24 hours, check Firestore — Firestore deletions may have a soft-delete grace period (verify in your security rules)
2. Check user's most recent JSON backup
3. Restore the single transaction by editing Firestore directly OR using a future "restore single record" feature

**Long-term fix (UX_AUDIT item):** Add a Trash bin / Undo for delete actions — 30-day soft delete before purge.

### 3.3 Local DB corrupted

**Symptoms:** App crashes on launch, reports SQLite error.

**Recovery steps:**
1. Ask user to clear app data (Settings → Apps → Fynlo → Clear data)
2. Reinstall if needed
3. Sign in with same Google account
4. Cloud sync pulls everything back

If user's cloud account is also affected, fall through to backup-based recovery.

### 3.4 Cloud + local both gone

**Symptoms:** Catastrophic — Firestore data missing, local data wiped.

**Recovery steps:**
1. Ask: "Do you have any export files?" JSON/XLSX/PDF anywhere?
2. If yes: import via Settings → Restore from JSON
3. If no: communicate honestly that data is unrecoverable. Offer:
   - Full refund (if paid user)
   - Free lifetime upgrade (if free user, as goodwill)
   - Apology + commitment to learn

### 3.5 Sync conflict resolved wrong

**Symptoms:** User reports "I edited X on my phone, then opened my tablet, and now my edit is gone."

**Recovery steps:**
1. Check Firestore document history — was there a revision where the user's edit existed?
2. If yes: restore that revision
3. Long-term fix: improve `updatedAt` correctness (C03) so conflicts resolve to genuinely newest version

---

## 4. JSON backup parsing procedure

Most recovery scenarios involve parsing a user-supplied JSON backup. Here's the standard procedure:

```python
import json

with open('user_backup.json', 'r', encoding='utf-8') as f:
    backup = json.load(f)

# Verify schema version
schema_version = backup.get('schemaVersion', 1)
app_version = backup.get('appVersion', 'unknown')
exported_at = backup.get('exportedAt', 'unknown')

print(f"Backup from app v{app_version}, schema v{schema_version}, at {exported_at}")

# Extract paid values for C01 recovery
for borrower in backup.get('borrowers', []):
    if borrower.get('paid', 0) > 0:
        print(f"  Borrower {borrower['name']}: paid ₹{borrower['paid']:.0f}")

for debt in backup.get('debts', []):
    if debt.get('paid', 0) > 0:
        print(f"  Debt {debt['name']}: paid ₹{debt['paid']:.0f}")
```

Save this as `tools/parse_backup.py` in the repo.

---

## 5. Communication with affected users

### 5.1 Acknowledge fast

Within 24 hours of receiving a report:

```
Subject: Re: [User's subject] — We're looking into this

Hi [Name],

Thanks for reaching out. I'm looking into what happened with [specific issue].

I'm going to investigate and respond with a clear answer within [reasonable timeframe — usually 48 hours]. In the meantime:

1. Please don't tap Recalculate Balances again (if relevant)
2. If you have any backups from earlier (JSON or XLSX exports), please send them to me
3. Take a screenshot of what you're seeing now

I'm sorry this happened. I'll get back to you soon.

Avinash
Fynlo
```

### 5.2 Investigation response

Within 48 hours:

```
Subject: Re: [Issue] — Investigation update

Hi [Name],

I've looked into your issue. Here's what I found:

[Honest technical explanation in user-friendly terms]

What I can recover:
- [List specifically what can be recovered]

What I cannot recover:
- [List specifically what cannot be recovered]

What I'm doing about it:
- [Specific actions you're taking]

[If paid user: refund / extension offer]

I'm sorry this happened. The fix that prevents this from happening again is shipping in version [X.Y.Z].

Avinash
```

### 5.3 Resolution

Once recovery is complete:

```
Subject: Re: [Issue] — Recovered

Hi [Name],

Good news — I've recovered the following data:
- [Specifics]

Open the app, pull-to-refresh, and you should see it restored.

I'm offering [refund / free upgrade / other compensation] as an apology for the disruption. Please let me know if you accept.

Thanks for your patience.

Avinash
```

### 5.4 Mass communication (multiple users affected)

If a bug affects multiple users, in-app banner + email broadcast:

```
We discovered an issue in Fynlo version [X.Y.Z] that affected [feature]. 
Here's what happened, what we've done, and what you should do:

What happened: [Honest explanation]
What we did: [Fix shipped in version Z]
What you should do: [Steps to verify your data]
How to get help: [Contact]

We're sorry for the disruption.
```

---

## 6. C01-specific recovery preparation (do this NOW)

Before any external user could be affected by C01:

1. **Verify C01 fix in Sprint 1.** No 3.2.x → production until fixed.

2. **Build the `parse_backup.py` tool** so recovery is fast when needed.

3. **Document Firestore version history setting** — is it enabled? If not, enable it now for free disaster recovery.

4. **Test recovery end-to-end** before any paid user:
   - Create test data with `paid` values
   - Take a backup
   - Tap Recalculate (lose paid)
   - Restore from backup
   - Verify `paid` values returned

5. **Prepare templates** (§5 above) as drafts you can fill in fast.

---

## 7. Records to keep

For each recovery case:

`incidents/YYYY-MM-DD-{shortname}.md`:

```markdown
# Incident: [Brief description]

**Date reported:** YYYY-MM-DD
**Reported by:** [User identifier]
**Severity:** P0 / P1 / P2 / P3
**Root cause cluster:** C__

## What happened
[Description]

## How we found out
[User report / monitoring / etc.]

## Data lost
[Specifics]

## Data recovered
[Specifics]

## What we did
- [Timeline of actions]

## Communication
- [What was said to user, when]

## Compensation offered
[Refund / extension / etc.]

## Prevention
- [What in UX_AUDIT or future work prevents recurrence]

## Lessons learned
[Honest assessment]
```

Save in repo under `incidents/` so they're version-controlled and accessible to future-you / lawyer if needed.

---

## 8. Prevention is recovery's best friend

Every recovery scenario above has a prevention measure in the audit:

| Scenario | Prevention cluster |
|---|---|
| C01 destruction | C01 (Sprint 1) |
| Stale exports | C02 (Sprint 1) |
| Schema corruption | C03 (Sprints 2 + 6) |
| Sync conflict | C03b (Sprint 6) |
| Local DB corruption | Auto-recovery from cloud (existing) |
| Both wiped | User backup habit (UX: nudge to export) |

Recovery should be the safety net, not the daily driver. If you're recovering frequently, the prevention pipeline is broken.

---

## 9. Legal angle

Data loss = potential consumer claim:

- For paid users: ₹999 cap in T&C §11.2 applies, but Consumer Protection Act can override (see `LEGAL_PROTOCOL.md` §10.2)
- For free users: no monetary claim but reputation damage real
- Document everything — recovery attempts, user communications, compensation offered. Use `incidents/` folder for audit trail.

If a user threatens legal action:
- Stop normal communication
- Contact lawyer (see `LEGAL_PROTOCOL.md` §13)
- Preserve all data and logs related to the case

---

## 10. Cross-references

- `LEGAL_PROTOCOL.md` §9 — incident response legal angle
- `INCIDENT_PROTOCOL.md` — operational incident handling
- `UX_AUDIT_2026-05-25.md` — clusters C01, C02, C03 — prevention
- `BACKUP_PROTOCOL.md` — what backups exist on your side
- `RELEASE_PROTOCOL.md` §7 — rollback procedure

---

## 11. The one rule

**If you can't fully recover a user's data, tell them honestly what's recoverable and what isn't. Don't promise what you can't deliver. Apologize specifically. Offer specific compensation. Document everything.**

---

**End of DATA_RECOVERY_PROTOCOL.md v1.0**
