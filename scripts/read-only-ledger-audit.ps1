param(
    [string]$Package = "app.fynlo",
    [string]$DatabaseName = "Fynlo_database",
    [string]$OutDir = ".codex-db-dumps"
)

$ErrorActionPreference = "Stop"

function Run-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    & adb @Args
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Args -join ' ') failed with exit code $LASTEXITCODE"
    }
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$remoteTmp = "/sdcard/Download/${Package}-${DatabaseName}-${stamp}"
$localDb = Join-Path $OutDir "${Package}-${DatabaseName}-${stamp}"

Write-Host "Copying $Package/$DatabaseName to $localDb (read-only audit copy)..."
Run-Adb shell run-as $Package cp "databases/$DatabaseName" $remoteTmp
Run-Adb pull $remoteTmp $localDb | Out-Null
Run-Adb shell rm $remoteTmp

$python = @'
import sqlite3, pathlib, sys

db_path = pathlib.Path(sys.argv[1])
con = sqlite3.connect(f"file:{db_path.as_posix()}?mode=ro", uri=True)
con.row_factory = sqlite3.Row
cur = con.cursor()

def rows(q, *args):
    return list(cur.execute(q, args))

def money(value):
    return f"Rs.{float(value):,.2f}"

def table_exists(name):
    return bool(rows("select 1 from sqlite_master where type='table' and name=?", name))

print(f"READ-ONLY LEDGER AUDIT: {db_path}")
print()

tables = [
    "accounts", "transactions", "borrowers", "payments", "debts",
    "debt_payments", "investments", "investment_valuations", "sync_conflicts",
]
print("Counts")
for table in tables:
    if table_exists(table):
        print(f"- {table}: {rows(f'select count(*) c from {table}')[0]['c']}")
    else:
        print(f"- {table}: missing")

print("\nBorrower aggregate mismatches")
count = 0
if table_exists("borrowers") and table_exists("payments"):
    for b in rows("select * from borrowers order by name"):
        ps = rows("select * from payments where loanId=?", b["id"])
        principal = sum(0 if (p["type"] or "").lower() == "interest only" else (p["principal"] if p["principal"] > 0 else p["amount"]) for p in ps)
        interest = sum((p["amount"] if (p["type"] or "").lower() == "interest only" and p["interest"] == 0 else p["interest"]) for p in ps)
        if abs(principal - b["paidPrincipal"]) > 0.01 or abs(interest - b["paidInterest"]) > 0.01:
            print(f"- {b['name']}: stored principal {money(b['paidPrincipal'])}, rows {money(principal)}; stored interest {money(b['paidInterest'])}, rows {money(interest)}")
            count += 1
print("none" if count == 0 else f"{count} borrower aggregate issues")

print("\nDebt aggregate mismatches")
count = 0
if table_exists("debts") and table_exists("debt_payments"):
    for d in rows("select * from debts order by name"):
        ps = rows("select * from debt_payments where debtId=?", d["id"])
        principal = sum(0 if (p["type"] or "").lower() == "interest only" else (p["principal"] if p["principal"] > 0 else p["amount"]) for p in ps)
        interest = sum((p["amount"] if (p["type"] or "").lower() == "interest only" and p["interest"] == 0 else p["interest"]) for p in ps)
        if abs(principal - d["paidPrincipal"]) > 0.01 or abs(interest - d["paidInterest"]) > 0.01:
            print(f"- {d['name']}: stored principal {money(d['paidPrincipal'])}, rows {money(principal)}; stored interest {money(d['paidInterest'])}, rows {money(interest)}")
            count += 1
print("none" if count == 0 else f"{count} debt aggregate issues")

print("\nInvestments with invested amount but zero value")
count = 0
if table_exists("investments"):
    for inv in rows("select * from investments where invested > 0 and currentVal = 0 order by name"):
        print(f"- {inv['name']}: invested {money(inv['invested'])}, current value {money(inv['currentVal'])}, source {inv['sourceType']} {inv['fundingSource']}")
        count += 1
print("none" if count == 0 else f"{count} investments with missing value")

print("\nLinked debt investment status")
missing = 0
linked = []
if table_exists("investments") and table_exists("debts"):
    linked = rows("select * from investments where linkedDebtId != '' order by name")
    for inv in linked:
        ds = rows("select * from debts where id=?", inv["linkedDebtId"])
        if not ds:
            print(f"- {inv['name']}: linked debt missing {inv['linkedDebtId']}")
            missing += 1
        else:
            d = ds[0]
            remaining = max(0.0, d["amount"] - d["paidPrincipal"])
            print(f"- {inv['name']}: linked debt {d['name']}, remaining principal {money(remaining)}, investment current {money(inv['currentVal'])}")
if not linked:
    print("no linked debt investments")
elif missing == 0:
    print("no missing linked debts")

print("\nTransactions with weak account trail")
count = 0
if table_exists("transactions"):
    query = """
        select * from transactions
        where amount > 0
          and (type in ('Transfer','Expense','Income') or category in ('Lending','Debt Received','Investment'))
        order by date, createdAt
    """
    for t in rows(query):
        ty = (t["type"] or "").lower()
        cat = (t["category"] or "").lower()
        need_from = ty in ("expense", "transfer") or cat in ("lending", "investment")
        need_to = ty in ("income", "transfer") or cat == "debt received"
        has_from = bool(t["fromAcct"] or t["fromAcctId"])
        has_to = bool(t["toAcct"] or t["toAcctId"])
        if (need_from and not has_from) or (need_to and not has_to):
            print(f"- {t['date']} {t['type']}/{t['category']} {money(t['amount'])}: from='{t['fromAcct']}' to='{t['toAcct']}' ref={t['ref']}")
            count += 1
print("none" if count == 0 else f"{count} weak account trails")

print("\nOpen sync conflicts")
count = 0
if table_exists("sync_conflicts"):
    for c in rows("select * from sync_conflicts where resolution = '' or resolution = 'OPEN' or resolution is null"):
        print(f"- {c['collection']} {c['entityId']}: {c['fieldSummary']}")
        count += 1
print("none" if count == 0 else f"{count} open sync conflicts")
'@

$python | python - $localDb
