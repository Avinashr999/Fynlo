# Fynlo Release Protocol

**Version:** 1.0
**Last updated:** 2026-05-25
**Authority:** Defines how Fynlo ships from merged code to users' devices. Binding for every release.

---

## 0. Why this document exists

A bug in a personal finance app can erase user data, ship lies to accountants, or trigger legal claims. The release protocol exists to make sure no release reaches production without passing checks that would have caught known categories of bugs.

If you skip this protocol because "it's a small change," that's exactly the kind of release that causes incidents.

---

## 1. Release channels

| Channel | Audience | Promotion criteria | Min stay |
|---|---|---|---|
| **Internal Testing** | Avinash + ≤5 invited testers | Build green, smoke test passes | 24 hours |
| **Closed Alpha** | ≤20 invited users (friends, family) | Internal Testing clean for 24h, no P0 bugs | 7 days |
| **Open Beta** | Public opt-in | Closed Alpha clean for 7d, all P0/P1 closed | 14 days |
| **Production (Staged)** | 5% → 20% → 50% → 100% of users | Open Beta clean for 14d, crash rate <0.5% | 3 days per stage |
| **Production (Full)** | All users | Staged rollout to 100% complete | — |

---

## 2. Version numbering

`MAJOR.MINOR.PATCH` per semver:

- **MAJOR** — breaking schema migrations (e.g., 3.x → 4.0)
- **MINOR** — new features, no breaking changes
- **PATCH** — bug fixes, polish, no new features

`versionCode` increments by 1 every release regardless of semver.

---

## 3. Pre-release checklist

Before promoting from any channel:

### 3.1 Code-level
- [ ] All open P0 audit clusters closed (per `UX_AUDIT_2026-05-25.md` §5)
- [ ] `./gradlew detekt` passes with no new violations
- [ ] `./gradlew test` passes
- [ ] `./gradlew :app:assembleProdRelease` builds cleanly
- [ ] All data-integrity regression tests pass (especially C01 — Recalculate preserves `paid` field)
- [ ] `versionCode` incremented
- [ ] `versionName` follows semver

### 3.2 Documentation
- [ ] `CHANGELOG.md` updated with user-facing changes
- [ ] `PROJECT_STATE_FOR_AI.md` journal entry added
- [ ] Closed audit points marked in `UX_AUDIT_2026-05-25.md`
- [ ] If schema change: migration tested round-trip (export → wipe → restore → diff empty)

### 3.3 Data safety
- [ ] Auto-recalc-before-export verified working (C02)
- [ ] Schema version stamp present in exports (C03)
- [ ] Tests run against a database fixture with non-trivial data

### 3.4 Performance regression
- [ ] Run macrobenchmark module against the new build
- [ ] Compare to last shipped baseline (`bench-baseline-fynlo.json`)
- [ ] No regression >10% in cold start or scroll
- [ ] Baseline profile still present in `app/src/main/baseline-prof.txt`

### 3.5 Manual smoke test (15 minutes)
- [ ] Install fresh on CPH2767 (or other test device)
- [ ] Log in with Google
- [ ] Add a transaction, a loan, a debt, an investment
- [ ] Tap Recalculate Balances — verify `paid` values survive
- [ ] Export JSON backup — verify schema version and `paid` data correct
- [ ] Export PDF — verify version string matches `BuildConfig.VERSION_NAME`
- [ ] Force quit, reopen — data persists
- [ ] Sign out, sign back in — cloud data restored

---

## 4. Release procedure

### 4.1 Build the release AAB

```powershell
cd C:\Users\user\AndroidStudioProjects\Fynlo
./gradlew :app:bundleProdRelease
# Output: app/build/outputs/bundle/prodRelease/app-prod-release.aab
```

Verify:
- AAB size reasonable (compare to last release)
- Contains baseline profile (`BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof`)

### 4.2 Sign

Keystore at `secrets/keystore.properties` + `secrets/fynlo-release.jks` (gitignored).

Verify signature with:
```powershell
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify -verbose -certs app/build/outputs/bundle/prodRelease/app-prod-release.aab
```

### 4.3 Upload to Play Console

1. Go to Play Console → Fynlo → Release → [channel]
2. Create new release
3. Upload AAB
4. Fill release notes (see §5)
5. Save (don't review yet — wait 1 hour to make sure no last-minute issues)
6. Review and roll out

### 4.4 Tag the release in git

```powershell
git tag -a v3.2.2 -m "Release 3.2.2 — C01 fix"
git push origin v3.2.2
```

Tags must match `versionName` exactly.

### 4.5 Update tracking docs

- Mark cluster status in `UX_AUDIT_2026-05-25.md`
- Append journal entry in `PROJECT_STATE_FOR_AI.md`
- Update `lint-baseline-progress.md` if violations changed

---

## 5. Release notes

Every release needs user-facing release notes for Play Store. Format:

```
Fynlo 3.2.2

What's new
• Data safety: Fixed an issue where Recalculate Balances could affect payment history. Your data is safer now.
• Performance: Cold start is ~10% faster thanks to baseline profile optimization.

Bug fixes
• [...specific user-visible fixes...]

Internal improvements
• [...optional, only mention if user-relevant...]
```

**Rules:**
- Plain English, user benefit framing, not technical jargon
- Lead with data safety / performance when applicable
- Don't list every bug fix — group similar ones
- Max 500 characters for the first paragraph (truncated on small screens)

---

## 6. Halt criteria — when to STOP a release

Halt rollout (and roll back if already shipped) if:

- **Crash rate >2%** within first 24 hours of staged rollout
- **Any P0 data-integrity bug** reported by external user
- **Negative net rating change** of more than 0.5 stars in first 48 hours
- **Firebase outage** affecting sync for >2 hours
- **Play Store policy violation flag** received

Halt mechanism:
1. Open Play Console → Releases → halt rollout
2. Increase rollback rollout to 100% on previous stable version
3. Communicate via in-app banner if possible
4. Postmortem within 7 days

---

## 7. Rollback procedure

If a critical issue ships:

### 7.1 Immediate (within 1 hour)
- Halt staged rollout (Play Console)
- If full rollout: promote previous version to 100%
- Tweet/post if you have channels (don't yet, but plan for later)

### 7.2 Within 24 hours
- Identify root cause
- Create UX_AUDIT entry for the regression
- Create hotfix branch
- Notify affected users via in-app banner if they can be identified

### 7.3 Within 7 days
- Ship hotfix release through normal channels (not bypass — fast-tracked but full process)
- Postmortem document (blameless, written, added to repo as `incidents/YYYY-MM-DD-summary.md`)

---

## 8. Specific gates for Sprint 1 (C01 fix)

The 3.2.2 release that closes C01 has additional requirements:

- [ ] C01 regression test exists and passes
- [ ] Manual verification: fixture with `paid: 5000` on a borrower → recalc → verify `paid` survives
- [ ] Schema migration tested: existing users upgrading from 3.2.1 retain `paid` values
- [ ] Documentation: `PROJECT_STATE_FOR_AI.md` updated with C01 closure
- [ ] Communication: prepare announcement that 3.2.2 fixes the Recalculate issue (proactive transparency)

**Hard rule: Do NOT promote 3.2.1 to production. Skip directly to 3.2.2.**

---

## 9. Channel-specific notes

### 9.1 Internal Testing
- Email list managed in Play Console → Testing → Internal Testing
- Add testers by email; they get a link to opt in

### 9.2 Closed Alpha
- Invite by email or Google Group
- Don't post the opt-in link publicly
- 7-day minimum stay

### 9.3 Open Beta
- Opt-in URL can be shared publicly
- 14-day minimum stay
- Watch reviews + Firebase crash reports daily

### 9.4 Production staged rollout
- 5% → wait 24h → 20% → wait 24h → 50% → wait 24h → 100%
- At each stage, check crash rate, reviews, support emails
- Pause if anything anomalous

---

## 10. Records to keep

| Record | Where | Retention |
|---|---|---|
| AAB files | Local + Drive backup + Git tag | Forever |
| Release notes | Git tag annotation | Forever |
| Crash dashboards screenshots (at promotion time) | Drive | 3 years |
| User feedback during beta | Email folder | 3 years |
| Postmortem documents | `incidents/` in repo | Forever |
| Play Console release status screenshots | Drive | 3 years |

---

## 11. Cross-references

- `UX_AUDIT_2026-05-25.md` §5 — ship-blocking matrix
- `AI_AGENT_PROTOCOL.md` §6 — test-first for data-integrity work
- `DATA_RECOVERY_PROTOCOL.md` — if a release causes data loss
- `INCIDENT_PROTOCOL.md` — production incident handling
- `BACKUP_PROTOCOL.md` — AAB and keystore preservation

---

## 12. The one rule

**Never skip the regression test for C01 (Recalculate preserves payment data). Every release that touches Recalculate, exports, or schema must run it and pass. Failed run = no release.**

---

**End of RELEASE_PROTOCOL.md v1.0**
