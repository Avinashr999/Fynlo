# Contradictions And Uncertainties

## Confirmed Contradictions

### Samanvi Travels principal amount

- Earlier user statement mentioned a ₹10,00,000 loan.
- Later inspected/current DB context said the active row was ₹6,00,000.
- Resolution: treat ₹6,00,000 as authoritative for that inspected row. The ₹10,00,000 statement is documented as user-side confusion unless a separate historical row is found.

### Paid-ahead interest interpretation

- At first, paid-ahead interest looked like a possible bug because interest due was zero.
- Later logic clarified that zero due is correct only when collected/paid current-period interest is ahead of accrued interest.
- Resolution: accrual must continue from the loan/debt date. Paid-ahead is explanation, not freeze.

### Dev versus production Google sign-in

- Production sign-in was reported working after Play/Firebase setup.
- Developer app sign-in can still show setup missing if dev Firebase/OAuth configuration is absent.
- Resolution: production and dev auth must be treated separately.

### Book Check warnings versus errors

- User sometimes saw old interest/payment items as worrying errors.
- Product decision later made unclear old interest a review/warning unless actual data corruption exists.
- Resolution: Book Check should remain calm and actionable.

## Uncertainties

1. Exact raw list of the “17 fully verified” phone items is not all preserved in one local source file.
2. Full cloud round-trip has been discussed and partly tested, but no single clean test transcript is stored in repo docs.
3. Some screenshots and pasted text files are outside tracked repo history and may not be durable.
4. Play Console state can change; this migration captures the state reported through the conversation, not a live console export.
5. Internal testers’ older data mismatch reports need retesting on the latest build/version.

## Handling Rule For Future Agents

If a future agent sees a conflict between:

1. Current database/source rows,
2. Automated tests,
3. User memory/recollection,
4. Old conversation notes,

then prefer current data plus tests, but never silently rewrite financial records. Present the difference plainly and ask for confirmation before changing money history.


## 2026-08-15 Limitation Cleanup

- Local attachment availability improved: 11 `.codex-remote-attachments` image files are now indexed in `10_SOURCE_INDEX.csv` as local cache evidence. They are still not treated as durable release documentation unless copied into tracked docs/assets.
- Cloud round-trip uncertainty was reduced by newer `PROJECT_STATE_FOR_AI.md` evidence: cloud/local parity is now recorded as `PHONE VERIFIED BY USER`. Future agents should still repeat the test if auth/sync code changes.
- Play Console native-symbol/mapping uncertainty was reduced by newer project evidence and user confirmation that mapping/native symbols were uploaded. Future AABs still need matching artifacts.
- Dev Google sign-in remains a configuration gate, not a proven code bug: config files exist, but dev OAuth/SHA correctness must be verified in Firebase/Google Console if real dev sign-in is required.
