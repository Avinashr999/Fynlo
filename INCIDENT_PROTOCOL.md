# Fynlo Incident Protocol

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** Defines how to respond when something goes wrong in production.

---

## 0. Why this document exists

When users report bugs, data loss, crashes, or unusual behavior, you need a defined response. Without one, panic produces inconsistent communications, missed evidence, and (worst case) legal exposure. This protocol defines what to do.

---

## 1. Incident severity

| Level | Definition | Examples | Initial response time |
|---|---|---|---|
| **P0** | Data loss / breach / security; safety-of-funds | C01 destruction in production; Firestore data leak; user can't access their financial data | Within 4 hours |
| **P1** | Core feature broken for multiple users | Recalculate crashes app; export produces empty file; sync stops working | Within 24 hours |
| **P2** | Single-user issue or partial outage | Specific user reports unusual behavior; Firebase Analytics down (non-user-facing) | Within 48 hours |
| **P3** | Cosmetic or minor inconvenience | UI alignment, typo, minor inconsistency | Within 7 days |

Default: treat unknown reports as P1 until investigated.

---

## 2. Triage decision tree

When a report comes in:

```
Is anyone's data at risk RIGHT NOW?
├── Yes → P0
└── No → Are multiple users affected?
         ├── Yes → P1
         └── No → Is the user blocked from using core features?
                   ├── Yes → P1
                   └── No → Is there a real bug or just confusion?
                            ├── Bug → P2 (or P3 if cosmetic)
                            └── Confusion → respond with guidance, no incident
```

---

## 3. P0 response — within 4 hours

### 3.1 Acknowledge

Reply to the reporter immediately:

```
Subject: Re: [User's subject] — We're investigating

Hi [Name],

I received your report. This is being treated as urgent. I'm investigating now and will update you within the next few hours.

In the meantime, please:
• Don't [specific action that may worsen the situation, e.g., "tap Recalculate again"]
• Take screenshots of what you're seeing
• If you have any recent backup files (JSON or XLSX), keep them safe

Avinash
Fynlo
```

### 3.2 Investigate

Within 4 hours, determine:
- Is this affecting just the reporter or potentially all users?
- What's the root cause?
- Is there a temporary mitigation (e.g., halt rollout, disable feature flag)?

### 3.3 Mitigate

If it's affecting more than one user OR is severe:
- Halt Play Store rollout (Play Console → Releases → halt)
- Communicate via in-app banner if technically possible
- Promote previous stable version to 100%

### 3.4 Communicate widely (if widespread)

If multiple users affected, draft a public statement. See `DATA_RECOVERY_PROTOCOL.md` §5.4 for template.

### 3.5 Document

Create `incidents/YYYY-MM-DD-{short-name}.md` immediately. Even before resolution. Append as you investigate.

---

## 4. P1 response — within 24 hours

### 4.1 Acknowledge

```
Subject: Re: [User's subject]

Hi [Name],

Thanks for reporting this. I'm looking into it. Will get back to you within 24 hours with what I've found.

Avinash
```

### 4.2 Investigate
- Reproduce locally if possible
- Check Crashlytics for related crashes
- Check Firestore audit log for related anomalies
- Determine if other users affected (search support emails, Play Store reviews)

### 4.3 Plan fix
- If reproducible → add to UX_AUDIT_2026-05-25.md as a cluster (or audit point under existing cluster)
- Assign to current sprint OR next sprint based on priority
- If P1 and ship-blocking → hotfix branch, fast-track release

### 4.4 Update reporter
- Within 24 hours: tell them what you found
- Within 48 hours: tell them the plan
- When fix ships: tell them and ask them to verify

---

## 5. P2 response — within 48 hours

Standard support flow:
- Acknowledge
- Reproduce or ask for more info
- Add to UX_AUDIT or fix directly
- Notify when resolved

---

## 6. P3 response — within 7 days

Acknowledge, add to backlog, fix during normal sprint work.

---

## 7. Incident report template

`incidents/YYYY-MM-DD-{shortname}.md`:

```markdown
# Incident: [Brief title]

**Date detected:** YYYY-MM-DD HH:MM IST
**Date reported:** YYYY-MM-DD HH:MM IST
**Detected by:** [User report / monitoring / dev]
**Severity:** P0/P1/P2/P3
**Status:** Open / Mitigated / Resolved
**Cluster:** C__ (link to audit)

## Summary
[1-paragraph description]

## Timeline
- HH:MM — first report received
- HH:MM — acknowledged to user
- HH:MM — investigation started
- HH:MM — root cause identified
- HH:MM — mitigation deployed
- HH:MM — fix deployed
- HH:MM — verified by reporter
- HH:MM — closed

## Root cause
[Technical explanation]

## Impact
- Users affected: [count, or "1 known", or "unknown"]
- Data loss: [yes/no, specifics]
- Duration: [from when to when]
- Financial impact: [refunds issued, downtime, etc.]

## Response actions
- [What you did, in order]

## Communication log
- [What you said to whom, when]

## Compensation
- [Refunds, free upgrades, apologies]

## Prevention
- Cluster added to UX_AUDIT: [C__]
- Regression test added: [test name + file path]
- Lint rule added (if applicable): [FY___]
- Doc updated: [which file, what change]

## Lessons learned
[Honest reflection. What could you have done differently? What systemic gap allowed this?]
```

---

## 8. Communication discipline

Rules for any external communication during an incident:

### 8.1 Speed > polish (within reason)
A quick honest acknowledgement at hour 1 is worth more than a polished update at hour 8.

### 8.2 Don't speculate
If you don't know the cause, say "I'm investigating," not "I think it might be X."

### 8.3 Don't blame third parties without proof
Don't say "this is Google's fault" or "Firebase did it" unless you've confirmed it. Even then, you're the one users paid; deflection looks weak.

### 8.4 Don't promise timelines you can't keep
"I'll update within 24 hours" is better than "fix coming tomorrow" if you don't know.

### 8.5 Honest about limitations
If something can't be recovered, say so plainly. Don't oversell what you can do.

### 8.6 No legal speculation
If a user threatens legal action, do NOT respond on the merits. Acknowledge their concern, escalate to lawyer (see `LEGAL_PROTOCOL.md` §11.1), continue normal recovery efforts.

---

## 9. Monitoring & detection

Active monitoring channels (set up as Fynlo grows):

| Channel | Watch for | How to monitor |
|---|---|---|
| Crashlytics | Spikes in crash rate | Firebase Console daily |
| Play Console reviews | New reviews, especially 1-2 stars | Play Console + email alerts |
| Support email | Inbound messages | Filter: "Fynlo" subject → priority inbox |
| Play Console policy alerts | Policy violations | Email alerts enabled |
| Firebase Analytics | Active users dropping | Weekly review |
| Firestore usage | Unusual spikes (potential abuse) | Firebase Console weekly |

---

## 10. Postmortem cadence

After every P0 or P1 incident:

- Within 7 days of resolution: write the postmortem (use §7 template)
- Within 14 days: share with [trusted reviewer if applicable] for blameless review
- Add to backlog: any improvements identified

Quarterly: review all postmortems and look for patterns. If 3 incidents trace to "I forgot to test X," that's a systemic gap, not three individual mistakes.

---

## 11. The "first paying user" problem

Before Fynlo has paying users, incident handling is low-stakes. The first paying user changes everything:

- Incidents become legal exposure
- Refund expectations are real
- Reviews can sink the app
- Word-of-mouth matters more

When you onboard your first paid user, re-read this protocol. Tighten response times if needed.

---

## 12. Cross-references

- `DATA_RECOVERY_PROTOCOL.md` — for data loss specifically
- `RELEASE_PROTOCOL.md` §7 — for release rollback
- `LEGAL_PROTOCOL.md` §9 — legal angle
- `BACKUP_PROTOCOL.md` — what's available to restore from
- `UX_AUDIT_2026-05-25.md` — where prevention work lives

---

## 13. The one rule

**Acknowledge fast. Investigate honestly. Communicate plainly. Document everything. Fix permanently.**

---

**End of INCIDENT_PROTOCOL.md v1.0**
