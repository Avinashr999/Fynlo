"""Read a dated SQLite backup; export a private calculation input, never edit the DB.

The date must come from backup evidence, not a requested historical projection.
Run on an extracted COPY with its matching WAL/SHM files when present.
"""
import argparse
import hashlib
import json
import sqlite3
from datetime import date
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("database", type=Path)
parser.add_argument("capture_date", type=date.fromisoformat)
parser.add_argument("output", type=Path)
args = parser.parse_args()
db = args.database.resolve()
if args.output.resolve() == db:
    raise SystemExit("Output must not replace the database")
connection = sqlite3.connect(db.as_uri() + "?mode=ro", uri=True)
connection.row_factory = sqlite3.Row
if connection.execute("pragma integrity_check").fetchone()[0] != "ok":
    raise SystemExit("Backup integrity check failed")
tables = {
    "accounts": "accounts", "borrowers": "borrowers", "debts": "debts",
    "investments": "investments", "payments": "payments", "debtPayments": "debt_payments",
}
ledger = {}
for field, table in tables.items():
    ledger[field] = [dict(row) for row in connection.execute(f'SELECT * FROM "{table}" ORDER BY id')]
    for row in ledger[field]:
        if "stopInterestAfterDue" in row:
            row["stopInterestAfterDue"] = bool(row["stopInterestAfterDue"])
originals = [dict(row) for row in connection.execute("select * from net_worth_snapshots order by date")]
digest = hashlib.sha256(json.dumps(ledger, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
payload = {"date": args.capture_date.isoformat(), "sourceSha256": digest,
           "sourceFile": db.name, "ledger": ledger, "originals": originals}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(payload, indent=2), encoding="utf-8")
print(json.dumps({"date": payload["date"], "sourceSha256": digest,
                  "counts": {key: len(value) for key, value in ledger.items()}}))
