"""ตรวจผลของ tool ข้อมูลตลาดด้วยข้อมูลจริงจากแหล่งอิสระ — "tool ตอบตัวเลขถูกไหม"

ขั้นตอน:
  1. รัน `AvatarSceneDataLiveDump` (โค้ดจริงของแอปบน JVM, ต่อเน็ต) — เรียก 32 tool แล้วเก็บข้อความที่ AI ได้รับ
  2. ดึงข้อมูลอ้างอิงทันที: Binance (ราคา + แท่งเทียน), gold-api, Yahoo, alternative.me, CoinGecko, ForexFactory
  3. คำนวณอินดิเคเตอร์ใหม่ด้วยสูตรอิสระ (แบบ TradingView) แล้วเทียบกับตัวเลขในข้อความของแต่ละ tool

ใช้:  python tools/tool_accuracy_audit.py [--skip-gradle] [--out composeApp/build/tool_audit]
      --skip-gradle = ใช้ผล tool เดิมใน <out>/raw (ข้อมูลอ้างอิงยังดึงใหม่ — เทียบได้เฉพาะเมื่อเพิ่งรัน)

ความคลาดเคลื่อนที่ยอมรับ: tool กับข้อมูลอ้างอิงดึงห่างกัน ~1 นาที ราคาขยับได้ → ใช้เกณฑ์เป็นช่วง ไม่ใช่เท่ากันเป๊ะ
ข้อจำกัด: บน PC ไม่มีฐานข้อมูลแท่งเทียนแบบมือถือ — tool SMC ดึง TradingView ใหม่ทุกครั้ง ถ้าโดนจำกัดจะตอบ
"Strict TV candle source violation" (บนมือถือใช้แท่งที่สะสมไว้) — สคริปต์แจ้งเป็น "แหล่งข้อมูล" ไม่ใช่ค่าผิด
"""
import argparse
import json
import math
import os
import re
import subprocess
import sys
import time
import urllib.request
from datetime import datetime, timedelta, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36"}
results = []   # (status, tool, check, detail)


def ok(tool, check, detail=""):
    results.append(("✓", tool, check, detail))


def bad(tool, check, detail=""):
    results.append(("✗", tool, check, detail))


def note(tool, check, detail=""):
    results.append(("–", tool, check, detail))


def near(tool, check, app, ref, tol, unit=""):
    if app is None or ref is None:
        note(tool, check, f"ไม่มีค่าให้เทียบ (แอป={app} อ้างอิง={ref})")
        return
    d = abs(app - ref)
    (ok if d <= tol else bad)(tool, check, f"แอป {app:g}{unit} · อ้างอิง {ref:.6g}{unit} · ต่าง {d:.4g} (เกณฑ์ ≤ {tol:g})")


# ─── ข้อมูลอ้างอิง ────────────────────────────────────────────────────────────

def get_json(url, timeout=25):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


def safe(fn, *a):
    try:
        return fn(*a)
    except Exception as e:   # noqa: BLE001
        print(f"  ! ดึงข้อมูลอ้างอิงไม่ได้: {a[0] if a else ''} — {e}")
        return None


def klines(sym, tf, n=1000):
    k = safe(get_json, f"https://api.binance.com/api/v3/klines?symbol={sym}&interval={tf}&limit={n}")
    return [(int(x[0]), float(x[1]), float(x[2]), float(x[3]), float(x[4]), float(x[5])) for x in k] if k else []


def yahoo_daily(sym):
    d = safe(get_json, f"https://query1.finance.yahoo.com/v8/finance/chart/{urllib.request.quote(sym)}?interval=1d&range=10d")
    if not d:
        return None
    r = d["chart"]["result"][0]
    cl = [c for c in r["indicators"]["quote"][0]["close"] if c is not None]
    return {"price": r["meta"].get("regularMarketPrice"), "closes": cl}


# ─── สูตรอิสระ (แบบ TradingView) ─────────────────────────────────────────────

def sma(v, n):
    out, s = [None] * len(v), 0.0
    for i, x in enumerate(v):
        s += x
        if i >= n:
            s -= v[i - n]
        if i >= n - 1:
            out[i] = s / n
    return out


def ema(v, n):
    out = [None] * len(v)
    if len(v) < n:
        return out
    e = sum(v[:n]) / n
    out[n - 1] = e
    a = 2 / (n + 1)
    for i in range(n, len(v)):
        e = a * v[i] + (1 - a) * e
        out[i] = e
    return out


def rma(v, n):
    out = [None] * len(v)
    if len(v) < n:
        return out
    r = sum(v[:n]) / n
    out[n - 1] = r
    for i in range(n, len(v)):
        r = (r * (n - 1) + v[i]) / n
        out[i] = r
    return out


def rsi(cl, n=14):
    g = [max(cl[i] - cl[i - 1], 0) for i in range(1, len(cl))]
    l = [max(cl[i - 1] - cl[i], 0) for i in range(1, len(cl))]
    ag, al = rma(g, n), rma(l, n)
    return [None] + [None if a is None else (100.0 if b == 0 else 100 - 100 / (1 + a / b)) for a, b in zip(ag, al)]


def indicators(b):
    """ค่าล่าสุดของแท่งสุดท้าย (แท่งที่กำลังก่อตัว เหมือน TradingView scanner)"""
    cl = [x[4] for x in b]
    e12, e26 = ema(cl, 12), ema(cl, 26)
    m = [None if (x is None or y is None) else x - y for x, y in zip(e12, e26)]
    f = next(i for i, x in enumerate(m) if x is not None)
    sig = [None] * f + ema(m[f:], 9)
    tr = [b[0][2] - b[0][3]] + [max(b[i][2] - b[i][3], abs(b[i][2] - b[i - 1][4]), abs(b[i][3] - b[i - 1][4])) for i in range(1, len(b))]
    atr = rma(tr, 14)
    pdm = [0.0] + [max(b[i][2] - b[i - 1][2], 0) if (b[i][2] - b[i - 1][2]) > (b[i - 1][3] - b[i][3]) else 0.0 for i in range(1, len(b))]
    ndm = [0.0] + [max(b[i - 1][3] - b[i][3], 0) if (b[i - 1][3] - b[i][3]) > (b[i][2] - b[i - 1][2]) else 0.0 for i in range(1, len(b))]
    tr1, p1, n1 = rma(tr[1:], 14), rma(pdm[1:], 14), rma(ndm[1:], 14)
    dx = [None if (a is None or a == 0) else 100 * abs(p - q) / (p + q) if (p + q) else 0 for a, p, q in zip(tr1, [x / y if (x is not None and y) else 0 for x, y in zip(p1, tr1)], [x / y if (x is not None and y) else 0 for x, y in zip(n1, tr1)])]
    f2 = next(i for i, x in enumerate(dx) if x is not None)
    adx = rma(dx[f2:], 14)
    basis = sma(cl, 20)
    r = rsi(cl)
    return {"close": cl[-1], "rsi": r[-1], "macd": m[-1], "signal": sig[-1], "hist": m[-1] - sig[-1],
            "atr": atr[-1], "adx": adx[-1], "bb_basis": basis[-1]}


def pearson(x, y):
    mx, my = sum(x) / len(x), sum(y) / len(y)
    num = sum((a - mx) * (b - my) for a, b in zip(x, y))
    den = math.sqrt(sum((a - mx) ** 2 for a in x) * sum((b - my) ** 2 for b in y))
    return num / den if den else 0.0


# ─── อ่านข้อความของ tool ─────────────────────────────────────────────────────

def num(pattern, text, group=1):
    m = re.search(pattern, text)
    return float(m.group(group).replace(",", "")) if m else None


def read(raw, tool):
    p = os.path.join(raw, f"{tool}.txt")
    return open(p, encoding="utf-8").read() if os.path.exists(p) else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-gradle", action="store_true")
    ap.add_argument("--out", default=os.path.join(ROOT, "composeApp", "build", "tool_audit"))
    args = ap.parse_args()
    raw = os.path.join(args.out, "raw")

    if not args.skip_gradle:
        os.makedirs(raw, exist_ok=True)
        for f in os.listdir(raw):
            os.remove(os.path.join(raw, f))
        print("เรียก tool จริง (AvatarSceneDataLiveDump)…")
        env = dict(os.environ, RUN_LIVE_TESTS="1", SCENE_SAMPLES_DIR=raw)
        gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
        r = subprocess.run([gradlew, ":composeApp:testDebugUnitTest", "--tests", "*AvatarSceneDataLiveDump*", "--rerun", "-q"],
                           cwd=ROOT, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if r.returncode != 0:
            print(r.stdout[-2000:], r.stderr[-2000:])
            sys.exit("gradle ล้มเหลว")
    ran_at = datetime.now(timezone.utc)
    print(f"ดึงข้อมูลอ้างอิง ({ran_at:%H:%M:%S} UTC)…")

    btc = {tf: klines("BTCUSDT", tf) for tf in ("15m", "1h", "4h", "1d", "1w")}
    btc_px = safe(get_json, "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT")
    gold = safe(get_json, "https://api.gold-api.com/price/XAU")
    gc = yahoo_daily("GC=F")
    fng = safe(get_json, "https://api.alternative.me/fng/?limit=1")
    cg = safe(get_json, "https://api.coingecko.com/api/v3/global")
    ff = safe(get_json, "https://nfs.faireconomy.media/ff_calendar_thisweek.json")
    idx = {name: yahoo_daily(sym) for name, sym in
           [("S&P500", "^GSPC"), ("NASDAQ", "^IXIC"), ("DOW", "^DJI"), ("VIX", "^VIX"), ("Oil", "CL=F")]}
    aapl = yahoo_daily("AAPL")
    daily = {s: klines(s, "1d", 60) for s in ("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT")}

    # ── เวลา / คำนวณ / แปลงหน่วย ──
    t = read(raw, "get_current_datetime")
    if t:
        m = re.search(r"(\d{2}):(\d{2}):(\d{2})", t)
        if m:
            bkk = (ran_at + timedelta(hours=7)).replace(tzinfo=None)
            app = bkk.replace(hour=int(m.group(1)), minute=int(m.group(2)), second=int(m.group(3)))
            diff = abs((bkk - app).total_seconds())
            (ok if diff < 300 else bad)("get_current_datetime", "เวลาไทย", f"แอป {m.group(0)} · ห่างจากตอนตรวจ {diff:.0f} วิ")
    t = read(raw, "calculate")
    if t:
        (ok if t.strip().endswith("= 62.75") else bad)("calculate", "(4185.10 − 4160) × 2.5 = 62.75", t.strip()[-40:])
    t = read(raw, "convert_units")
    if t:
        (ok if re.search(r"=\s*86(\.0)?\b", t) else bad)("convert_units", "30°C = 86°F", t.strip())

    # ── ราคา ──
    t = read(raw, "trading_price")
    if t:
        near("trading_price", "ทอง XAUUSD เทียบ gold-api (spot)", num(r"Price:\s*([\d.,]+)", t), gold and gold.get("price"), 0.004 * (gold or {}).get("price", 1))
        chg = num(r"Change:\s*\w+\s*[-+]?[\d.,]+\s*\(([-+]?[\d.]+)%\)", t)
        if gc and len(gc["closes"]) >= 2:
            ref = (gc["closes"][-1] / gc["closes"][-2] - 1) * 100
            near("trading_price", "% เปลี่ยนแปลงรายวัน เทียบทองล่วงหน้า GC=F", chg, ref, 0.6, "%")
        ny = ran_at.astimezone(timezone(timedelta(hours=-4)))   # ตุลาคม = EDT
        closed = ny.weekday() == 5 or (ny.weekday() == 4 and ny.hour >= 17) or (ny.weekday() == 6 and ny.hour < 18)
        says_closed = "CLOSED" in t
        (ok if says_closed == closed else bad)("trading_price", "สถานะตลาดทอง", f"แอป {'CLOSED' if says_closed else 'LIVE'} · ควรเป็น {'CLOSED' if closed else 'LIVE'}")

    # ── อินดิเคเตอร์ BTC 1h ──
    ref1h = indicators(btc["1h"]) if btc["1h"] else None
    t = read(raw, "trading_technical_analysis")
    if t and ref1h:
        tool = "trading_technical_analysis"
        atr = ref1h["atr"]
        near(tool, "RSI 1h", num(r"RSI:\s*([-\d.]+)", t), ref1h["rsi"], 3.0)
        near(tool, "MACD 1h", num(r"MACD:\s*([-\d.]+)", t), ref1h["macd"], 0.15 * atr)
        near(tool, "MACD signal 1h", num(r"Signal:\s*([-\d.]+)\s*\|\s*Hist", t), ref1h["signal"], 0.15 * atr)
        near(tool, "MACD hist 1h", num(r"Hist:\s*([-\d.]+)", t), ref1h["hist"], 0.1 * atr)
        near(tool, "ATR 1h", num(r"ATR:\s*([-\d.]+)", t), atr, 0.08 * atr)
        near(tool, "ADX 1h", num(r"ADX:\s*([-\d.]+)", t), ref1h["adx"], 3.0)
        near(tool, "BB basis 1h", num(r"BB Basis:\s*([-\d.]+)", t), ref1h["bb_basis"], 0.5 * atr)
        m, s, h = num(r"MACD:\s*([-\d.]+)", t), num(r"Signal:\s*([-\d.]+)\s*\|\s*Hist", t), num(r"Hist:\s*([-\d.]+)", t)
        if None not in (m, s, h):
            (ok if abs((m - s) - h) <= 0.05 * atr else bad)(tool, "Hist = MACD − Signal (ค่าเดียวกันทั้งชุด)", f"{m:g} − {s:g} = {m - s:.4g} · Hist {h:g}")
        up, lo, ba = num(r"BB Upper:\s*([-\d.]+)", t), num(r"BB Lower:\s*([-\d.]+)", t), num(r"BB Basis:\s*([-\d.]+)", t)
        if None not in (up, lo, ba):
            (ok if lo < ba < up else bad)(tool, "BB: ล่าง < กลาง < บน", f"{lo:g} / {ba:g} / {up:g}")
    t = read(raw, "trading_strategy_signal")
    if t and ref1h:
        near("trading_strategy_signal", "RSI14 1h", num(r"\[1h\] Momentum: RSI14=([-\d.]+)", t), ref1h["rsi"], 3.0)
        near("trading_strategy_signal", "ATR14 1h", num(r"\[1h\] Volatility: ATR14=([-\d.]+)", t), ref1h["atr"], 0.08 * ref1h["atr"])
    t = read(raw, "trading_multi_timeframe")
    if t:
        for tf, key in [("1W", "1w"), ("1D", "1d"), ("4h", "4h"), ("1h", "1h"), ("15m", "15m")]:
            if btc[key]:
                near("trading_multi_timeframe", f"RSI {tf}", num(rf"{re.escape(tf)}: [A-Z ]+\| RSI: ([-\d.]+)", t), indicators(btc[key])["rsi"], 3.5)

    # ── ภาพรวมตลาด ──
    t = read(raw, "trading_fear_greed")
    if t and fng:
        near("trading_fear_greed", "Crypto F&G เทียบ alternative.me", num(r"ค่าปัจจุบัน:\s*(\d+)/100", t), float(fng["data"][0]["value"]), 0)
    t = read(raw, "trading_crypto_overview")
    if t and cg:
        d = cg["data"]
        near("trading_crypto_overview", "Market cap รวม (T USD) เทียบ CoinGecko", num(r"\$([\d.]+)T", t), d["total_market_cap"]["usd"] / 1e12, 0.05)
        near("trading_crypto_overview", "BTC dominance", num(r"BTC Dominance:\s*([\d.]+)%", t), d["market_cap_percentage"]["btc"], 0.5, "%")
    t = read(raw, "trading_market_snapshot")
    if t:
        for name, y in idx.items():
            if not y or len(y["closes"]) < 2:
                continue
            m = re.search(rf"\*\*{re.escape(name)}\*\*:\s*([\d.,]+)\s*\(([-+]?[\d.]+)%\)", t)
            if not m:
                note("trading_market_snapshot", name, "ไม่พบในผล")
                continue
            near("trading_market_snapshot", f"{name} ราคา", float(m.group(1).replace(",", "")), y["closes"][-1], 0.005 * y["closes"][-1])
            near("trading_market_snapshot", f"{name} % รายวัน", float(m.group(2)), (y["closes"][-1] / y["closes"][-2] - 1) * 100, 0.3, "%")
    t = read(raw, "trading_fundamental_analysis")
    if t and aapl and len(aapl["closes"]) >= 2:
        m = re.search(r"ราคาล่าสุด: \*\*([\d.,]+) USD\*\* \(([-+]?[\d.]+), ([-+]?[\d.]+)%\)", t)
        if m:
            c0, c1 = aapl["closes"][-2], aapl["closes"][-1]
            near("trading_fundamental_analysis", "AAPL ราคา", float(m.group(1)), c1, 0.01 * c1)
            near("trading_fundamental_analysis", "AAPL เปลี่ยน (USD)", float(m.group(2)), c1 - c0, 0.3)
            near("trading_fundamental_analysis", "AAPL เปลี่ยน (%)", float(m.group(3)), (c1 / c0 - 1) * 100, 0.1, "%")

    # ── correlation ──
    t = read(raw, "trading_correlation_matrix")
    if t:
        if t.startswith("❌"):
            bad("trading_correlation_matrix", "ดึงข้อมูล", t.strip()[:120])
        elif all(daily.values()):
            days = sorted(set.intersection(*[{x[0] // 86_400_000 for x in v[:-1]} for v in daily.values()]))[-30:]
            cl = {s: {x[0] // 86_400_000: x[4] for x in v} for s, v in daily.items()}
            ret = {s: [(cl[s][b] - cl[s][a]) / cl[s][a] for a, b in zip(days, days[1:])] for s in daily}
            for a, b in [("BTCUSDT", "ETHUSDT"), ("BTCUSDT", "SOLUSDT"), ("ETHUSDT", "XRPUSDT")]:
                row = re.search(rf"^\| {a} \|([^\n]+)", t, re.M)   # a body row (the header row names it too)
                vals = [float(v) for v in re.findall(r"(-?\d\.\d\d)", row.group(1))] if row else []
                order = ["BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT"]
                if len(vals) == len(order):
                    near("trading_correlation_matrix", f"{a}–{b} (คำนวณเองจาก Binance รายวัน)", vals[order.index(b)], pearson(ret[a], ret[b]), 0.06)

    # ── สแกน ──
    for tool, rule in [("trading_top_gainers", "desc"), ("trading_top_losers", "asc")]:
        t = read(raw, tool)
        if not t:
            continue
        rows = re.findall(r"^\d+\. (\S+) - [\d.]+ ([-+]?[\d.]+)%", t, re.M)
        syms = [s for s, _ in rows]
        chg = [float(c) for _, c in rows]
        (ok if rows else bad)(tool, "มีรายการ", f"{len(rows)} รายการ")
        if rows:
            (ok if all(s.endswith("USDT") and not s.endswith(".P") for s in syms) else bad)(tool, "คู่ USDT spot เท่านั้น", ", ".join(syms[:6]))
            (ok if len(set(syms)) == len(syms) else bad)(tool, "ไม่ซ้ำ", "")
            (ok if chg == sorted(chg, reverse=(rule == "desc")) else bad)(tool, "เรียงตาม %", "")
    for tool, cmp in [("trading_overbought_scan", lambda r: r > 70), ("trading_oversold_scan", lambda r: r < 30)]:
        t = read(raw, tool)
        if t:
            rs = [float(x) for x in re.findall(r"RSI: ([\d.]+)", t)]
            (ok if rs and all(cmp(r) for r in rs) else (note if not rs else bad))(tool, "RSI ทุกตัวเข้าเงื่อนไข", f"{len(rs)} รายการ")
    t = read(raw, "trading_bollinger_scan")
    if t:
        rows = re.findall(r"^\d+\. (\S+) - ([\d.]+) [-+]?[\d.]+% \| BB.width: ([\d.]+)", t, re.M)
        widths = [float(w) for *_, w in rows]
        (ok if rows else bad)("trading_bollinger_scan", "มีรายการ (เดิมตอบ No data เสมอ)", f"{len(rows)} รายการ")
        if rows:
            (ok if widths == sorted(widths) else bad)("trading_bollinger_scan", "เรียงจากแคบไปกว้าง", "")
            pegs = [s for s, p, w in rows if 0.97 <= float(p) <= 1.03 and float(w) < 1.0]
            (ok if not pegs else bad)("trading_bollinger_scan", "ไม่มี stablecoin", ", ".join(pegs))

    # ── ปฏิทิน / sentiment ──
    t = read(raw, "trading_macro_calendar")
    if t and ff:
        titles = set(re.findall(r"\*\*(.+?)\*\* \(([A-Z]{3})\)", t))
        feed = {(e["title"], e["country"]) for e in ff}
        missing = [x for x in titles if x not in feed]
        (ok if titles and not missing else bad)("trading_macro_calendar", "ทุกข่าวมีจริงใน ForexFactory สัปดาห์นี้", f"{len(titles)} ข่าว" + (f" · ไม่พบ {missing[:3]}" if missing else ""))
        if any(e.get("actual") for e in ff):
            note("trading_macro_calendar", "แหล่งข้อมูลมีตัวเลขจริงแล้ว", "ควรแสดง actual")
        elif "ไม่มีตัวเลขจริง" in t:
            ok("trading_macro_calendar", "บอกชัดว่าไม่มีตัวเลขจริง (กัน AI เอาค่าคาดการณ์ไปพูดเป็นผล)", "")
        else:
            bad("trading_macro_calendar", "บอกชัดว่าไม่มีตัวเลขจริง", "")
    t = read(raw, "trading_sentiment")
    if t:
        m = re.search(r"Bias: ([-\d.]+) \((\d+) บวก / (\d+) ลบ\)", t)
        if m:
            bias, b, s = float(m.group(1)), int(m.group(2)), int(m.group(3))
            ref = (b - s) / (b + s) if b + s else 0.0
            near("trading_sentiment", "เสาข่าว: bias = (บวก − ลบ) / (บวก + ลบ)", bias, ref, 0.011)
        (bad if "ไม่มีข้อมูลเทคนิค" in t or "HOLD (Score 0)" in t else ok)("trading_sentiment", "เสาเทคนิคมีข้อมูล", "")

    # ── tool ที่ตอบ error ──
    for f in sorted(os.listdir(raw)):
        if not f.endswith(".txt"):
            continue
        t = open(os.path.join(raw, f), encoding="utf-8").read()
        if "Strict TV candle source violation" in t:
            note(f[:-4], "แหล่งแท่งเทียน", "TradingView ไม่ตอบบน PC (ใช้ Binance แทนไม่ได้ในโหมด strict) — " + t.strip()[:90])
        elif re.search(r"\bnull\b", t):
            bad(f[:-4], "ไม่มีค่า null ในข้อความ", re.search(r".{0,40}\bnull\b.{0,20}", t).group(0))

    # ── สรุป ──
    width = max(len(r[1]) for r in results) if results else 10
    print()
    for st, tool, check, detail in results:
        print(f"  {st} {tool:<{width}}  {check}" + (f"  — {detail}" if detail else ""))
    n_ok = sum(1 for r in results if r[0] == "✓")
    n_bad = sum(1 for r in results if r[0] == "✗")
    n_note = sum(1 for r in results if r[0] == "–")
    print(f"\nสรุป: ผ่าน {n_ok} · ไม่ผ่าน {n_bad} · ตรวจไม่ได้/หมายเหตุ {n_note}")
    if btc_px:
        print(f"(BTC ตอนตรวจ {float(btc_px['price']):,.2f} · ทอง {gold and gold.get('price')})")


if __name__ == "__main__":
    main()
