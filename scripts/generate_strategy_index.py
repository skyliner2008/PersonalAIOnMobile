#!/usr/bin/env python3
"""สร้าง index.json จาก strategies/*.py (Quantpedia/QuantConnect)
— parse header comment เป็น description + จัดหมวดจากชื่อไฟล์
— output: composeApp/src/commonMain/composeResources/files/strategies/index.json (+ copy .py)
"""
import json, re, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "strategies"
DST = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "files" / "strategies"

CATEGORY_RULES = [
    ("Crypto",            ["bitcoin", "cryptocurrenc"]),
    ("Momentum & Trend",  ["momentum", "trend", "52-weeks-high", "residual"]),
    ("Reversal",          ["reversal"]),
    ("Value & Fundamental", ["value", "book-to-market", "fed-model", "accrual", "rd-expenditures",
                             "asset-growth", "roa-effect", "fscore", "lexical", "esg", "earnings",
                             "short-interest"]),
    ("Carry & FX",        ["carry", "currency", "dollar"]),
    ("Calendar & Seasonality", ["seasonality", "january", "payday", "turn-of-the-month",
                                "option-expiration-week", "12-month-cycle", "overnight"]),
    ("Volatility & Risk", ["volatility", "dispersion", "skewness", "betting-against-beta",
                           "asymmetry", "term-structure"]),
    ("Pairs & Arbitrage", ["pairs", "paired", "arbitrage", "spread", "soccer"]),
    ("Asset Allocation & Macro", ["asset-class", "sector", "country", "crude-oil",
                                  "synthetic-lending", "sentiment", "small-capitalization"]),
]

def categorize(slug: str) -> str:
    for cat, keys in CATEGORY_RULES:
        if any(k in slug for k in keys):
            return cat
    return "Other"

def humanize(slug: str) -> str:
    return slug.replace("-", " ").title()

def parse_header(text: str):
    """ดึง URL + description จาก comment block ต้นไฟล์"""
    lines = text.splitlines()
    url = ""
    desc_lines = []
    in_header = True
    skip_prefixes = ("# region", "# endregion", "# QC implementation", "#   -", "# NOTE")
    for i, line in enumerate(lines[:60]):
        s = line.strip()
        if i == 0 and "quantpedia.com/strategies/" in s:
            url = s.lstrip("# ").strip()
            continue
        if not in_header:
            break
        if s.startswith("#"):
            body = s.lstrip("#").strip()
            if any(s.startswith(p) or body.startswith(p.lstrip("# ")) for p in skip_prefixes):
                continue
            if "quantpedia.com" in body and not url:
                url = body
                continue
            if body:
                desc_lines.append(body)
        elif s == "" and desc_lines:
            # จบ block แรก
            break
        elif s and not s.startswith("#"):
            break
    desc = " ".join(desc_lines).strip()
    desc = re.sub(r"\s+", " ", desc)
    return url, desc[:800]

def main():
    DST.mkdir(parents=True, exist_ok=True)
    entries = []
    for py in sorted(SRC.glob("*.py")):
        slug = py.stem
        text = py.read_text(encoding="utf-8", errors="replace")
        url, desc = parse_header(text)
        entries.append({
            "name": slug,
            "title": humanize(slug),
            "file": f"{slug}.py",
            "category": categorize(slug),
            "description": desc,
            "url": url,
        })
        shutil.copy2(py, DST / py.name)
    (DST / "index.json").write_text(
        json.dumps(entries, ensure_ascii=False, indent=1), encoding="utf-8")
    cats = {}
    for e in entries:
        cats.setdefault(e["category"], 0)
        cats[e["category"]] += 1
    print(f"✅ {len(entries)} strategies → {DST}")
    for c, n in sorted(cats.items()):
        print(f"  {c}: {n}")
    missing_desc = [e["name"] for e in entries if len(e["description"]) < 40]
    if missing_desc:
        print(f"⚠️ description สั้น/ว่าง ({len(missing_desc)}): {missing_desc}")

if __name__ == "__main__":
    sys.exit(main())
