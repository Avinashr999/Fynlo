"""Prepare a private, before-image-guarded history-only request from reviewed results."""
import argparse
import json
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument("current_input", type=Path)
p.add_argument("output", type=Path)
p.add_argument("results", type=Path, nargs="+")
args = p.parse_args()
current = json.loads(args.current_input.read_text(encoding="utf-8"))
originals = {row["date"]: row for row in current["originals"]}
repairs = []
for path in args.results:
    result = json.loads(path.read_text(encoding="utf-8"))
    if result["reasons"] or not result["snapshot"]:
        raise SystemExit(f"Refusing unresolved candidate: {path.name}")
    row = result["snapshot"]
    if row["date"] >= current["date"]:
        raise SystemExit("Recovery is for past dates only; today's normal capture is authoritative")
    original = originals.get(row["date"])
    if original and original.get("captureSource", "LEGACY") != "LEGACY":
        raise SystemExit("Refusing to replace a complete history entry")
    repairs.append({"recovered": row, "expectedOriginal": original})
args.output.write_text(json.dumps({"expectedLedger": current["ledger"], "repairs": repairs}, indent=2), encoding="utf-8")
print(json.dumps({"dates": [r["recovered"]["date"] for r in repairs], "financialWrites": 0}))
