# User Product Preferences

These preferences came repeatedly from the user and should guide future work.

## Product Feel

- The app should feel clean, premium, minimal, and trustworthy.
- It should not look like a developer/debug tool.
- It should help normal users understand money records quickly.
- Financial correctness matters more than visual novelty.
- Designs should match the approved template language: compact cards, clear typography, muted green identity, strong but restrained buttons, and a floating bottom navigation feel.

## Language

Use plain user language:

- Principal Outstanding
- Interest Due
- Interest Payable
- Total Receivable
- Total Payable
- Paid / Collected
- Account used
- Loan date
- Due date
- Interest paid ahead
- Needs review

Avoid exposing internal terms in normal screens:

- allocation
- stale period
- derived bucket
- calculated window
- sync state internals
- ledger mismatch jargon

## Interaction Preferences

- Buttons should be pale/disabled until required data is valid.
- After save/delete/edit, show clear feedback: saved, deleted, updated, synced, failed, etc.
- Dangerous changes need confirmation.
- Financial repairs should not happen silently.
- Book Check should explain what happened, why it matters, and what the user can do.
- Tapping a Book Check issue should ideally open the related record.
- Date selectors should be smooth and easy to clear if optional.
- Keyboard should not cover important form results.

## Accounting Visibility

Users should be able to answer:

- Where did this money come from?
- Where did it go?
- Which account changed?
- What was the balance before and after?
- How much principal is pending?
- How much interest is due?
- Why is interest zero?
- Is this old interest, current interest, or advance interest?

## Release Process Preferences

- Use prod debug compile/unit tasks, not plain debug, for release gate checks.
- Install on phone before release AAB when possible.
- Build AAB only after manual phone smoke for critical accounting flows.
- Version code/name must be bumped for Play Console uploads.
- Keep `PROJECT_STATE_FOR_AI.md` and internal testing knowledge hub updated after meaningful changes.

## Play Store / Public Identity

- Public name: Fynlo Ledger.
- Package name remains `app.fynlo`.
- App is free and no ads.
- Store listing should avoid overpromising banking/lending services; it is a manual-entry personal finance ledger.
