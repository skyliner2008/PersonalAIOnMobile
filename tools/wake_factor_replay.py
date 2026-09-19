"""ตรวจค่าของปัจจัยปลุก AI ทั้ง 115 ตัว + ภาพ 5TF ด้วยการเล่นซ้ำบนแท่งเทียนจริงจากมือถือ

ขั้นตอน:
  1. ดึงฐานข้อมูลจากมือถือ (adb, debug build) → ส่งออกแท่ง 1m/5m/15m/1h/4h ของ symbol
  2. รัน `WakeFactorReplayTest` (โค้ดจริงของแอป) ย้อนหลัง N แท่งของ TF หลัก — ใช้เฉพาะแท่งที่ปิดแล้ว ณ เวลานั้น
  3. คำนวณใหม่ด้วยสูตรอิสระใน Python แล้วเทียบ:
     - อินดิเคเตอร์พื้นฐานทุก TF (EMA20/50/200, RSI14, ATR14, MACD hist, ADX, BB%B) ทั้งชุดที่ trigger ใช้และชุดในภาพ 5TF
     - ปัจจัยที่มีสูตรใน device_wake_audit.trigger_checks: เทียบทุกแท่ง ทั้งตอนเกิดและตอนไม่เกิด (จับทั้งยิงผิดและพลาด)
     - ปัจจัยที่เหลือ: อัตราการเกิด, error, ทิศ — หาตัวที่ไม่เคยเกิดหรือเกิดแทบทุกแท่ง

ใช้:  python tools/wake_factor_replay.py [SYMBOL] [--tf 15m] [--bars 300] [--db jarvis.db]
"""
import argparse
import json
import math
import os
import shutil
import sqlite3
import subprocess
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import device_wake_audit as A  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORK = os.path.join(ROOT, "composeApp", "build", "wake_replay")
TFS = ["1m", "5m", "15m", "1h", "4h"]
TF_MS = {"1m": 60_000, "5m": 300_000, "15m": 900_000, "1h": 3_600_000, "4h": 14_400_000}
LIMITS = {"1m": 300, "5m": 500, "15m": 800, "1h": 800, "4h": 400}   # เหมือน AnticipationEngine
DIGEST_TF = {"D1": "1d", "H4": "4h", "H1": "1h", "M15": "15m", "M5": "5m", "M1": "1m"}


# ─── สูตรอ้างอิง (เขียนแยกจากแอป) ─────────────────────────────────────────────

def ema_last(v, p):
    e = A.ema(v, p)
    return e[-1] if e else None


def rsi_last(cl, n=14):
    r = A._rsi_series(cl, n)
    return r[-1] if r else None


def macd_hist_last(cl):
    h = A.macd_hist(cl)
    return h[-1] if h else None


def adx_last(b, n=14):
    """Wilder ADX มาตรฐาน (เหมือน TradingView): DX แรกที่แท่ง n, ADX = RMA ของ DX seed ด้วยค่าเฉลี่ย n ตัวแรก"""
    if len(b) < 4 * n:
        return None
    tr, pdm, ndm = [0.0], [0.0], [0.0]
    for i in range(1, len(b)):
        h, l, ph, pl, pc = b[i][2], b[i][3], b[i - 1][2], b[i - 1][3], b[i - 1][4]
        tr.append(max(h - l, abs(h - pc), abs(l - pc)))
        up, dn = h - ph, pl - l
        pdm.append(up if up > dn and up > 0 else 0.0)
        ndm.append(dn if dn > up and dn > 0 else 0.0)
    s_tr, s_p, s_n = sum(tr[1:n + 1]), sum(pdm[1:n + 1]), sum(ndm[1:n + 1])
    dx = []

    def rec():
        if s_tr == 0:
            return
        pdi, ndi = 100 * s_p / s_tr, 100 * s_n / s_tr
        dx.append(0.0 if pdi + ndi == 0 else 100 * abs(pdi - ndi) / (pdi + ndi))
    rec()
    for i in range(n + 1, len(b)):
        s_tr = s_tr - s_tr / n + tr[i]; s_p = s_p - s_p / n + pdm[i]; s_n = s_n - s_n / n + ndm[i]
        rec()
    if len(dx) < n:
        return None
    a = sum(dx[:n]) / n
    for x in dx[n:]:
        a = (a * (n - 1) + x) / n
    return a


def bb_pb(cl, n=20, k=2.0):
    if len(cl) < n:
        return None
    w = cl[-n:]; m = sum(w) / n; sd = (sum((x - m) ** 2 for x in w) / n) ** 0.5
    up, lo = m + k * sd, m - k * sd
    return 50.0 if up == lo else (cl[-1] - lo) / (up - lo) * 100


def reference(b):
    cl = [x[4] for x in b]
    return {
        "ema20": ema_last(cl, 20), "ema50": ema_last(cl, 50), "ema200": ema_last(cl, 200),
        "rsi14": rsi_last(cl), "atr14": A.atr14(b), "macd_hist": macd_hist_last(cl),
        "adx": adx_last(b), "bb_pb": bb_pb(cl),
    }


def structure(b, L=5):
    """swing ยืนยัน (ค่าอยู่ที่แท่งยืนยัน = จุดยอด + L) → BOS/CHoCH/trend ตามนิยามของแอป"""
    n = len(b); sh = [None] * n; sl = [None] * n
    for cen in range(L, n - L):
        win = b[cen - L:cen + L + 1]
        if all(x[2] <= b[cen][2] for x in win): sh[cen + L] = b[cen][2]
        if all(x[3] >= b[cen][3] for x in win): sl[cen + L] = b[cen][3]
    ev = [None] * n; trend = [0] * n
    last_h = last_l = None; state = 0
    for i in range(n):
        if sh[i] is not None: last_h = sh[i]
        if sl[i] is not None: last_l = sl[i]
        if last_h is not None and b[i][4] > last_h:
            ev[i] = "CHoCH↑" if state == -1 else "BOS↑"; state = 1; last_h = None
        elif last_l is not None and b[i][4] < last_l:
            ev[i] = "CHoCH↓" if state == 1 else "BOS↓"; state = -1; last_l = None
        trend[i] = state
    return trend, ev, sh, sl


DAY = 86_400_000


def resample_daily(h4, shift_ms):
    """แท่งวันจาก H4 ตามขอบวันเทรด (shift_ms = device_wake_audit.day_shift_ms) — คืน [(แท่งวัน, ปิดแล้วไหม)]"""
    if not h4:
        return []
    groups = defaultdict(list)
    half = 2 * 3_600_000   # จัดกลุ่มด้วยจุดกึ่งกลางแท่ง (OANDA วาง H4 ตาม 17:00 นิวยอร์ก)
    for x in h4:
        groups[(x[0] + half + shift_ms) // DAY].append(x)
    last_end = h4[-1][0] + 4 * 3_600_000
    last_day = max(groups)
    out = []
    for k in sorted(groups):
        g = groups[k]; start = k * DAY - shift_ms
        out.append(((start, g[0][1], max(x[2] for x in g), min(x[3] for x in g), g[-1][4], sum(x[5] for x in g)),
                    k != last_day or last_end + half >= start + DAY))
    return out


def atr_series(b, n=14):
    out = [None] * len(b)
    if len(b) < n + 1:
        return out
    tr = [0.0] + [max(b[i][2] - b[i][3], abs(b[i][2] - b[i - 1][4]), abs(b[i][3] - b[i - 1][4])) for i in range(1, len(b))]
    a = sum(tr[1:n + 1]) / n; out[n] = a
    for i in range(n + 1, len(b)):
        a = (a * (n - 1) + tr[i]) / n; out[i] = a
    return out


def candle_text(b, prev, atr):
    """เหมือน MarketContextDigest.describeCandle"""
    o, h, l, c = b[1], b[2], b[3], b[4]
    rng = h - l; body = abs(c - o)
    color = "เขียว" if c > o else "แดง" if c < o else "doji"
    if rng <= 0:
        return f"{color} (ไม่มีช่วงราคา)"
    tags = []
    uw = (h - max(o, c)) / rng; lw = (min(o, c) - l) / rng
    if uw >= 0.5: tags.append(f"ไส้บน {int(uw * 100)}%")
    if lw >= 0.5: tags.append(f"ไส้ล่าง {int(lw * 100)}%")
    if body / rng >= 0.7: tags.append("ตัวตัน")
    if body / rng <= 0.1 and color != "doji": tags.append("doji")
    if prev is not None and body > 0:
        pb = abs(prev[4] - prev[1])
        if (c - o) * (prev[4] - prev[1]) < 0 and pb > 0 and max(o, c) >= max(prev[1], prev[4]) and min(o, c) <= min(prev[1], prev[4]):
            tags.append("กลืนกินแท่งก่อน")
    return f"{color} {rng / atr:.1f}×ATR" + ("" if not tags else " " + " ".join(tags))


def detail_ref(b):
    """รายละเอียดต่อ TF แบบอิสระ — เทียบกับ TfDetail ของแอป"""
    i = len(b) - 1; L = 5
    cl = [x[4] for x in b]
    atrs = atr_series(b); atr = atrs[i]
    _, _, sh, sl = structure(b)
    highs = [(k, v) for k, v in enumerate(sh) if v is not None]
    lows = [(k, v) for k, v in enumerate(sl) if v is not None]
    seq = None
    if len(highs) >= 2 and len(lows) >= 2:
        seq = ("HH" if highs[-1][1] > highs[-2][1] else "LH") + "+" + ("HL" if lows[-1][1] > lows[-2][1] else "LL")
    w = b[-50:]; hi, lo = max(x[2] for x in w), min(x[3] for x in w)
    rsi = A._rsi_series(cl, 14)

    def div(sw, is_high):
        if len(sw) < 2: return None
        (ia, pa), (ib, pb) = sw[-2], sw[-1]
        if i - ib > 30: return None
        ra = rsi[ia - L] if ia - L >= 0 else None
        rb = rsi[ib - L] if ib - L >= 0 else None
        if ra is None or rb is None: return None
        if is_high and pb > pa and rb < ra: return ib, f"Bearish div (ราคา HH แต่ RSI {ra:.0f}→{rb:.0f})"
        if not is_high and pb < pa and rb > ra: return ib, f"Bullish div (ราคา LL แต่ RSI {ra:.0f}→{rb:.0f})"
        return None
    ds = [d for d in (div(highs, True), div(lows, False)) if d]
    streak = 0
    sgn = lambda x: (x > 0) - (x < 0)
    d0 = sgn(b[i][4] - b[i][1])
    if d0:
        k = i
        while k >= 0 and sgn(b[k][4] - b[k][1]) == d0:
            streak += 1; k -= 1
        streak *= d0
    aw = [a for a in atrs[max(0, i - 99):i + 1] if a is not None]
    e20, e200 = A.ema(cl, 20), A.ema(cl, 200)
    return {
        "swing_high": highs[-1][1] if highs else None, "swing_low": lows[-1][1] if lows else None,
        "seq": seq, "pos50": (cl[i] - lo) / (hi - lo) * 100 if len(b) >= 50 and hi > lo else None,
        "ema20": e20[i] if e20 else None, "ema200": e200[i] if e200 else None,
        "rsi_prev3": rsi[i - 3] if i >= 3 else None,
        "div": " · ".join(d[1] for d in sorted(ds, key=lambda d: -d[0])) if ds else None,
        "move12": (cl[i] - cl[i - 12]) / atr if i >= 12 else None,
        "streak": streak,
        "atr_pct": sum(1 for a in aw if a <= atr) * 100.0 / len(aw) if len(aw) >= 50 else None,
        "candle": candle_text(b[i], b[i - 1] if i >= 1 else None, atr),
    }


def latest_event(ev, i, window=12):
    for k in range(i, max(0, i - window) - 1, -1):
        if ev[k]:
            return ev[k] + (" (แท่งล่าสุด)" if k == i else f" ({i - k} แท่งก่อน)")
    return "–"


def rel(a, b):
    if a is None or b is None:
        return None if a is None and b is None else float("inf")
    return abs(a - b) / max(abs(b), 1e-9)


# ─── main ────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("symbol", nargs="?", default="BTCUSDT")
    ap.add_argument("--tf", default="15m")
    ap.add_argument("--bars", type=int, default=300, help="จำนวนแท่ง TF หลักที่เล่นซ้ำ")
    ap.add_argument("--db", help="ใช้ไฟล์ jarvis.db ที่มีอยู่แทนการดึงจากมือถือ")
    ap.add_argument("--skip-gradle", action="store_true", help="ใช้ out.jsonl เดิม")
    args = ap.parse_args()
    sym = args.symbol.upper()

    path = args.db or A.pull_db(A.find_adb())
    c = sqlite3.connect(path)
    try:
        bars = {tf: A.load_bars(c, sym, tf) for tf in TFS}
    finally:
        c.close()
        if not args.db:
            os.remove(path)   # มีแชท/API key — ไม่เก็บไว้
    for tf, (rows, src) in bars.items():
        print(f"{tf:>4}: {len(rows):>5} แท่ง [{src}]" + (f" {A.ts(rows[0][0])} → {A.ts(rows[-1][0])}" if rows else ""))
    bars = {tf: rows for tf, (rows, _) in bars.items()}
    if len(bars[args.tf]) < 200:
        sys.exit(f"แท่ง {args.tf} ไม่พอ")

    # แท่งล่าสุดในฐานข้อมูลอาจยังไม่ปิด → ตัดทิ้ง (เล่นซ้ำเฉพาะเวลาที่ M1 ครอบคลุมถึง)
    m1_end = bars["1m"][-1][0] + TF_MS["1m"] if bars["1m"] else 0
    for tf in TFS:
        bars[tf] = [x for x in bars[tf] if x[0] + TF_MS[tf] <= m1_end]

    if not args.skip_gradle:
        shutil.rmtree(WORK, ignore_errors=True); os.makedirs(WORK)
        for tf in TFS:
            with open(os.path.join(WORK, f"{tf}.csv"), "w") as f:
                for x in bars[tf]:
                    f.write(f"{x[0]},{x[1]},{x[2]},{x[3]},{x[4]},{x[5]}\n")
        with open(os.path.join(WORK, "meta.txt"), "w") as f:
            f.write(f"{sym}\n{args.tf}\n{args.bars}\n")
        gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
        print("รันโค้ดของแอป (WakeFactorReplayTest)…")
        r = subprocess.run([gradlew, ":composeApp:testDebugUnitTest", "--tests", "*WakeFactorReplayTest*", "-q", "--rerun"],
                           cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if r.returncode != 0:
            print(r.stdout[-3000:], r.stderr[-3000:]); sys.exit("gradle ล้มเหลว")

    rows = [json.loads(l) for l in open(os.path.join(WORK, "out.jsonl"), encoding="utf-8")]
    reg = {r["id"]: r for r in rows if r["k"] == "trigger"}
    points = sorted({r["t"] for r in rows if "t" in r})
    print(f"\nเล่นซ้ำ {len(points)} จุดเวลา ({A.ts(points[0])} → {A.ts(points[-1])}) · ปัจจัยในทะเบียน {len(reg)} ตัว")

    shift = A.day_shift_ms(sym)

    def cut(tf, t):
        if tf == "1d":   # แอปใช้แท่งวันที่ปิดแล้ว ซึ่งรวมจาก H4 400 แท่งล่าสุด
            return [d for d, closed in resample_daily(cut("4h", t), shift) if closed]
        return [x for x in bars[tf] if x[0] + TF_MS[tf] <= t][-LIMITS[tf]:]

    # ── 1) อินดิเคเตอร์พื้นฐาน ────────────────────────────────────────────────
    print("\n══ 1) อินดิเคเตอร์พื้นฐานต่อ TF — ค่าจากแอป เทียบสูตรอิสระ (ความต่างสัมพัทธ์สูงสุด) ══")
    worst = defaultdict(float); worst_at = {}
    ref_cache = {}
    for r in rows:
        if r["k"] not in ("series", "digest"):
            continue
        tf = r["tf"] if r["k"] == "series" else DIGEST_TF[r["tf"]]
        key = (tf, r["t"])
        if key not in ref_cache:
            ref_cache[key] = reference(cut(tf, r["t"]))
        ref = ref_cache[key]
        if r["k"] == "digest":
            b = cut(tf, r["t"])
            if abs(r["close"] - b[-1][4]) > 1e-9:
                worst[("digest", tf, "close")] = float("inf"); worst_at[("digest", tf, "close")] = (r["t"], r["close"], b[-1][4])
        for f in ("ema20", "ema50", "ema200", "rsi14", "atr14", "macd_hist", "adx", "bb_pb"):
            if f not in r:
                continue
            d = rel(r[f], ref[f])
            if d is None:
                continue
            k = (r["k"], tf, f)
            if d > worst[k] or k not in worst_at:
                worst[k] = max(worst[k], d); worst_at[k] = (r["t"], r[f], ref[f])
    bad = 0
    for k in sorted(worst, key=lambda k: (k[0], (["1d"] + TFS).index(k[1]), k[2])):
        d = worst[k]; t, a, b = worst_at[k]
        # MACD hist เข้าใกล้ 0 ได้ → ใช้ความต่างสัมบูรณ์เทียบ ATR แทน
        flag = "✓" if d < 1e-6 else ("≈" if d < 1e-3 else "✗")
        if flag == "✗" and k[2] == "macd_hist" and a is not None and b is not None:
            atr = ref_cache[(k[1], t)]["atr14"] or 1
            if abs(a - b) / atr < 1e-6:
                flag = "✓"
        if flag == "✗":
            bad += 1
        src = "trigger" if k[0] == "series" else "ภาพ 5TF"
        print(f"  {flag} [{src:>7}] {k[1]:>4} {k[2]:<9} ต่างสูงสุด {d:.2e}" + ("" if flag != "✗" else f"  @ {A.ts(t)} แอป={a} อ้างอิง={b}"))
    print(f"  → ไม่ตรง {bad} รายการ")

    # ── 1b) โครงสร้างในภาพ 5TF (trend + เหตุการณ์ล่าสุด) ──────────────────────
    print("\n══ 1b) โครงสร้างในภาพ 5TF (trend / ล่าสุด=) — เทียบสูตรอิสระ ══")
    sc = Counter(); sbad = []
    for r in rows:
        if r["k"] != "digest":
            continue
        tf = DIGEST_TF[r["tf"]]; b = cut(tf, r["t"])
        tr, ev, _, _ = structure(b)
        want_t, want_e = tr[-1], latest_event(ev, len(b) - 1)
        ok = r["trend"] == want_t and r["event"] == want_e
        sc[(r["tf"], ok)] += 1
        if not ok and len(sbad) < 5:
            sbad.append(f"{A.ts(r['t'])} {r['tf']}: แอป trend={r['trend']} ล่าสุด={r['event']} | อ้างอิง trend={want_t} ล่าสุด={want_e}")
    for tf in DIGEST_TF:
        good, bad_ = sc[(tf, True)], sc[(tf, False)]
        if good + bad_:
            print(f"  {'✓' if not bad_ else '✗'} {tf:>3}: ตรง {good} · ไม่ตรง {bad_}")
    for x in sbad:
        print(f"     {x}")

    # ── 1c) รายละเอียดต่อ TF (swing/ตำแหน่งในกรอบ/EMA/RSI/divergence/แรง/ATR/แท่งเทียน) ──
    print("\n══ 1c) รายละเอียดต่อ TF ในภาพตลาด — เทียบสูตรอิสระ ══")
    dc = defaultdict(Counter); dbad = []
    for r in rows:
        if r["k"] != "digest" or "seq" not in r:
            continue
        ref = detail_ref(cut(DIGEST_TF[r["tf"]], r["t"]))
        for f, want in ref.items():
            got = r.get(f)
            if isinstance(want, float) and isinstance(got, (int, float)):
                ok = abs(got - want) <= 1e-9 * max(1.0, abs(want))
            else:
                ok = got == want
            dc[f][ok] += 1
            if not ok and len(dbad) < 8:
                dbad.append(f"{A.ts(r['t'])} {r['tf']} {f}: แอป={got} อ้างอิง={want}")
    for f, cnt in dc.items():
        print(f"  {'✓' if not cnt[False] else '✗'} {f:<10} ตรง {cnt[True]} · ไม่ตรง {cnt[False]}")
    for x in dbad:
        print(f"     {x}")

    # ── 1d) ระดับราคาจาก D1: PDH/PDL/PWH/PWL ────────────────────────────────
    lc = Counter(); lbad = []
    wk = lambda d: ((d[0] + 12 * 3_600_000) // DAY + 3) // 7
    for r in rows:
        if r["k"] != "levels":
            continue
        d1 = cut("1d", r["t"])
        if not d1:
            continue
        want = {"PDH": d1[-1][2], "PDL": d1[-1][3]}
        allday = resample_daily(cut("4h", r["t"]), shift)
        cur = wk(allday[-1][0])
        prev = [d for d in d1 if wk(d) < cur]
        if prev:
            pw = [d for d in prev if wk(d) == wk(prev[-1])]
            want.update({"PWH": max(d[2] for d in pw), "PWL": min(d[3] for d in pw)})
        for lv in r["levels"]:
            if lv["kind"] in want:
                ok = abs(lv["price"] - want[lv["kind"]]) < 1e-9
                lc[ok] += 1
                if not ok and len(lbad) < 5:
                    lbad.append(f"{A.ts(r['t'])} {lv['kind']}: แอป={lv['price']} อ้างอิง={want[lv['kind']]}")
    print("\n══ 1d) PDH/PDL/PWH/PWL ในรายการแนวรับ/ต้าน ══")
    print(f"  {'✓' if not lc[False] else '✗'} ตรง {lc[True]} · ไม่ตรง {lc[False]}")
    for x in lbad:
        print(f"     {x}")

    # ── 2) ปัจจัยที่มีสูตรอิสระ — เทียบทุกแท่ง ───────────────────────────────
    fires = defaultdict(dict)   # t -> id -> row
    for r in rows:
        if r["k"] == "fire":
            fires[r["t"]][r["id"]] = r
    errors = [r for r in rows if r["k"] == "error"]
    ptf = args.tf
    stats = defaultdict(Counter); samples = defaultdict(list)
    h1_all = bars["1h"]
    for t in points:
        b = cut(ptf, t)
        checks = A.trigger_checks(b, sym, [x for x in h1_all if x[0] + TF_MS["1h"] <= t], t)
        for fid, fn in checks.items():
            try:
                detail, want = fn()
            except Exception as ex:   # ข้อมูลไม่พอ
                want, detail = "ERR", str(ex)
            got = fires[t].get(fid, {}).get("dir")
            # ปัจจัยที่ดูแท่งอื่นที่ไม่ใช่ TF หลักจะไม่อยู่ในชุดนี้
            if want == "ERR":
                stats[fid]["err"] += 1
            elif got == want:
                stats[fid]["both_fire" if want else "both_quiet"] += 1
            else:
                kind = "app_only" if want is None else "ref_only" if got is None else "dir_diff"
                stats[fid][kind] += 1
                if len(samples[fid]) < 3:
                    samples[fid].append(f"{A.ts(t)} แอป={got} อ้างอิง={want} | {detail}")
    print(f"\n══ 2) ปัจจัยที่มีสูตรอิสระ ({len(stats)} ตัว) — เทียบทุก {len(points)} แท่ง ══")
    mism = 0
    for fid in sorted(stats):
        s = stats[fid]; wrong = s["app_only"] + s["ref_only"] + s["dir_diff"]
        mism += wrong > 0
        print(f"  {'✓' if not wrong else '✗'} {fid:<27} เกิดตรงกัน {s['both_fire']:>3} · ไม่เกิดตรงกัน {s['both_quiet']:>3}"
              + (f" · แอปเกิดแต่สูตรไม่เกิด {s['app_only']} · สูตรเกิดแต่แอปไม่เกิด {s['ref_only']} · ทิศต่าง {s['dir_diff']}" if wrong else "")
              + (f" · คำนวณไม่ได้ {s['err']}" if s["err"] else ""))
        for x in samples[fid]:
            print(f"       {x}")
    print(f"  → ไม่ตรง {mism} ตัว")

    # ── 3) ภาพรวมทุกปัจจัย ────────────────────────────────────────────────────
    print(f"\n══ 3) ทุกปัจจัย {len(reg)} ตัว — อัตราการเกิดบน {len(points)} แท่ง ══")
    cnt = Counter(); dirs = defaultdict(Counter); tfs_used = defaultdict(Counter)
    for t in points:
        for fid, r in fires[t].items():
            cnt[fid] += 1; dirs[fid][r["dir"]] += 1; tfs_used[fid][r["tf"]] += 1
    by_group = defaultdict(list)
    for fid, r in reg.items():
        by_group[r["group"]].append(fid)
    never = []; always = []
    for g, ids in by_group.items():
        print(f"  [{g}]")
        for fid in ids:
            n = cnt[fid]; r = reg[fid]
            pct = 100 * n / len(points)
            mark = "  " if fid not in stats else "✓ " if not (stats[fid]["app_only"] + stats[fid]["ref_only"] + stats[fid]["dir_diff"]) else "✗ "
            if n == 0:
                never.append(fid)
            if r["kind"] == "EVENT" and pct > 40:
                always.append(fid)
            d = " ".join(f"{k}:{v}" for k, v in dirs[fid].most_common())
            tf = ",".join(tfs_used[fid])
            print(f"    {mark}{fid:<27} {r['kind']:<5} {n:>4} ({pct:4.1f}%) {tf:<14} {d}")
    print(f"\n  ไม่เกิดเลยใน {len(points)} แท่ง: {len(never)} ตัว — {', '.join(never)}")
    if always:
        print(f"  EVENT ที่เกิด > 40% ของแท่ง (น่าสงสัยว่าเงื่อนไขหลวม): {', '.join(always)}")
    if errors:
        e = Counter(f"{x['id']}: {x['msg']}" for x in errors)
        print(f"  ❌ error ระหว่างประเมิน {len(errors)} ครั้ง:")
        for k, v in e.most_common(10):
            print(f"     {v}× {k}")
    else:
        print("  error ระหว่างประเมิน: ไม่มี")

    last = [r for r in rows if r["k"] == "digest_text"]
    if last:
        print(f"\n══ 4) ภาพ 5TF ที่ AI เห็น ณ {A.ts(last[0]['t'])} ══\n{last[0]['text']}")


if __name__ == "__main__":
    main()
