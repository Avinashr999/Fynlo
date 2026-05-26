# Release Notes

One markdown file per Play Store release: `release_notes/<versionName>.md`.

Each file contains:

1. **Status block** — Draft / Approved / Shipped, version codes, link to the
   matching `CHANGELOG.md` entry and any associated ADRs.
2. **Play Store "What's new" copy** — the literal text that goes into the
   Play Console release form. Plain English, user-benefit framing, 500-char
   cap on the first paragraph (truncated on small screens per
   `RELEASE_PROTOCOL.md §5`).
3. **Migration / data-shape notes** — only when the release touches storage
   in a user-visible way (e.g., a new row appears, a field is renamed in
   exports). Drafted in advance so the announcement reads as deliberate
   transparency, not a postmortem.
4. **Optional in-app banner copy** — one-time dialog text for the first
   launch after upgrade, if the release benefits from it (typically
   data-safety releases).
5. **Engineering pre-release checklist** — `versionName` / `versionCode`
   bump, signed AAB build steps, tag-and-push, Play Console upload. Mirrors
   `RELEASE_PROTOCOL.md §4`.

The user-facing copy in (2) is what gets pasted into the Play Console.
Everything else is internal context.

## Index

| Version | Date | Status | Notes |
|---|---|---|---|
| [3.2.2](./3.2.2.md) | 2026-05-26 (draft) | Draft | C01 fix release; skips 3.2.1 |
