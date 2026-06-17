param(
    [Parameter(Mandatory = $true)]
    [string]$PdfPath
)

$ErrorActionPreference = "Stop"
$resolved = Resolve-Path -LiteralPath $PdfPath
$env:PYTHONIOENCODING = "utf-8"

@'
import json
import os
import re
import sys

try:
    import pdfplumber
except Exception as exc:
    raise SystemExit(f"pdfplumber is required for PDF layout audit: {exc}")

path = sys.argv[1]
section_re = re.compile(r"^[1-5]\. ")
problems = []

with pdfplumber.open(path) as pdf:
    for page_index, page in enumerate(pdf.pages, 1):
        words = page.extract_words(x_tolerance=2, y_tolerance=2, keep_blank_chars=False)
        lines = []
        for word in words:
            if not lines or abs(lines[-1]["top"] - word["top"]) > 3:
                lines.append({"top": word["top"], "bottom": word["bottom"], "text": word["text"]})
            else:
                lines[-1]["text"] += " " + word["text"]
                lines[-1]["bottom"] = max(lines[-1]["bottom"], word["bottom"])

        for previous, current in zip(lines, lines[1:]):
            gap = current["top"] - previous["bottom"]
            if gap < -1:
                problems.append({
                    "type": "text_overlap",
                    "page": page_index,
                    "gap": round(gap, 1),
                    "previous": previous["text"][:100],
                    "current": current["text"][:100],
                })
            if gap > 120:
                problems.append({
                    "type": "large_gap",
                    "page": page_index,
                    "gap": round(gap, 1),
                    "previous": previous["text"][:100],
                    "current": current["text"][:100],
                })
            if section_re.match(previous["text"]) and gap < 8:
                problems.append({
                    "type": "section_to_table_too_close",
                    "page": page_index,
                    "gap": round(gap, 1),
                    "section": previous["text"][:100],
                    "next": current["text"][:100],
                })

        indexes = {}
        for index, line in enumerate(lines):
            for key in ("Income Expense", "Net Worth Trend"):
                if key in line["text"] and key not in indexes:
                    indexes[key] = index
        if "Income Expense" in indexes and "Net Worth Trend" in indexes:
            legend = lines[indexes["Income Expense"]]
            heading = lines[indexes["Net Worth Trend"]]
            gap = heading["top"] - legend["bottom"]
            if gap < 18:
                problems.append({
                    "type": "chart_heading_gap",
                    "page": page_index,
                    "gap": round(gap, 1),
                    "legend": legend["text"],
                    "heading": heading["text"],
                })

if problems:
    print(json.dumps(problems, ensure_ascii=False, indent=2))
    raise SystemExit(1)

print(f"PDF layout audit passed: {os.path.basename(path)}")
'@ | python - $resolved.Path
