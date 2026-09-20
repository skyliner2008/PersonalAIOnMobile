"""
ตรวจการแจ้งเตือนของระบบปลุก AI (คาดการณ์ล่วงหน้า) จากมือถือจริงผ่าน adb

ทำอะไร
  1. ดึงฐานข้อมูลแอป (jarvis.db) จากมือถือ ด้วย `adb exec-out run-as <pkg> cat databases/jarvis.db`
     — อ่านอย่างเดียว ไม่แก้อะไรในเครื่อง / ต้องเป็น debug build (run-as ใช้กับ release ไม่ได้)
  2. แสดง alert job, การตั้งค่าเสียง/AI/งบการปลุก
  3. แสดงการปลุกแต่ละครั้ง (จาก AnticipationFactorOutcome) + การ์ดที่ส่งเข้าแชท
  4. คำนวณค่าหลักใหม่จากแท่งเทียนดิบ (TvCandle) ของแท่งที่ปลุก เพื่อเทียบกับตัวเลขในการ์ด:
     ATR14, High/Low วันก่อน (PDH/PDL), high/low 10 แท่งก่อน (sweep), ไส้เทียน, CCI20,
     MACD histogram, แรงซื้อขายสุทธิ (delta) 10 แท่ง
  5. (--logcat) สรุป log ล่าสุดของแอป + ระยะห่างระหว่างการดึงแท่งเทียนแต่ละครั้ง
  6. ลบสำเนาฐานข้อมูลทิ้งตอนจบ (มีประวัติแชทส่วนตัว) ยกเว้นใส่ --keep

ใช้งาน (ต่อมือถือแล้วเปิด USB debugging)
  python tools/device_wake_audit.py                 # การปลุก 5 ครั้งล่าสุด
  python tools/device_wake_audit.py --last 10 --logcat
  python tools/device_wake_audit.py --db path/to/jarvis.db   # ใช้ไฟล์ที่ดึงมาแล้ว

หมายเหตุการตีความ
  - เวลาในฐานข้อมูลเป็น UTC (ms) — เวลาไทย = UTC + 7
  - signal_id = "SYMBOL|TF|เวลาเปิดแท่ง TF หลักที่ปิดล่าสุด" ตอนปลุก
  - วันเทรดของทอง/FX เริ่ม 22:00 UTC (เหมือนในแอป) คริปโตใช้วัน UTC
"""
import argparse
import datetime as dt
import json
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
from collections import defaultdict

PKG = "com.skyliner2008.jarvis"
UTC = dt.timezone.utc
# ลำดับความน่าเชื่อถือของแหล่งแท่งเทียน — ตรงกับ OhlcvCentralStore.SOURCE_PRIORITY ในแอป
SOURCE_PRIORITY = ["TV:OANDA", "TV:FX_IDC", "TV:TVC", "TV:BINANCE", "TV:NASDAQ", "TV:NYSE", "TV:", "BINANCE"]

# stderr ด้วย — ข้อความ error ของ sys.exit ไปทาง stderr (เดิมภาษาไทยออกมาเป็นรหัส escape บน Windows)
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8")
    except Exception:
        pass


# ─── adb ─────────────────────────────────────────────────────────────────────

def find_adb():
    exe = "adb.exe" if os.name == "nt" else "adb"
    if shutil.which("adb"):
        return shutil.which("adb")
    cands = []
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(env):
            cands.append(os.path.join(os.environ[env], "platform-tools", exe))
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    lp = os.path.join(root, "local.properties")
    if os.path.exists(lp):
        for line in open(lp, encoding="utf-8"):
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                cands.append(os.path.join(sdk, "platform-tools", exe))
    if os.environ.get("LOCALAPPDATA"):
        cands.append(os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk", "platform-tools", exe))
    for c in cands:
        if os.path.exists(c):
            return c
    sys.exit("❌ หา adb ไม่เจอ — ติดตั้ง Android SDK platform-tools หรือใส่ adb ใน PATH")


def pull_db(adb):
    devices = subprocess.run([adb, "devices"], capture_output=True, text=True).stdout
    if "\tdevice" not in devices:
        sys.exit("❌ ไม่พบมือถือ — เสียบสาย/เปิด USB debugging แล้วกดอนุญาตบนมือถือ\n" + devices)
    fd, path = tempfile.mkstemp(prefix="jarvis_", suffix=".db")
    with os.fdopen(fd, "wb") as f:
        r = subprocess.run([adb, "exec-out", "run-as", PKG, "cat", "databases/jarvis.db"], stdout=f, stderr=subprocess.PIPE)
    with open(path, "rb") as f:
        head = f.read(512)
    # run-as ส่ง error ออกทาง stdout และ exit code 0 (เช่น "package not debuggable") → ตรวจ header ของ SQLite แทน
    if not head.startswith(b"SQLite format 3\x00"):
        os.remove(path)
        reason = (head.decode(errors="replace") + r.stderr.decode(errors="replace")).strip() or "ไฟล์ว่าง"
        sys.exit(f"❌ ดึงฐานข้อมูลไม่ได้: {reason}\n   (run-as ใช้ได้เฉพาะ debug build ที่ลงผ่าน Android Studio / gradle installDebug)")
    return path


# ─── helpers ─────────────────────────────────────────────────────────────────

def ts(ms):
    u = dt.datetime.fromtimestamp(ms / 1000, UTC)
    return f"{u:%m-%d %H:%M} UTC ({(u + dt.timedelta(hours=7)):%H:%M} ไทย)"


def day_shift_ms(symbol):
    """ขอบวันเทรด — ตรงกับ TaIndicators.sessionOffsetHoursFor: ทอง/เงิน/FX เริ่ม 22:00 UTC, อื่นๆ วัน UTC"""
    s = symbol.upper().replace("/", "").replace("-", "")
    fiat = {"USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD"}
    metal = any(k in s for k in ("XAU", "XAG", "GOLD", "SILVER"))
    fx = len(s) == 6 and s[:3] in fiat and s[3:] in fiat
    return 2 * 3_600_000 if (metal or fx) else 0


def load_bars(c, symbol, interval):
    """แท่งของแหล่งที่แอปเลือกใช้: ตามลำดับความน่าเชื่อถือก่อน แล้วจึงดูจำนวนแท่ง"""
    sources = c.execute("select source, count(*) from TvCandle where symbol=? and interval=? group by source",
                        (symbol, interval)).fetchall()
    if not sources:
        return [], None
    def rank(src):
        return next((i for i, p in enumerate(SOURCE_PRIORITY) if src.upper().startswith(p)), len(SOURCE_PRIORITY))
    src = min(sources, key=lambda x: (rank(x[0]), -x[1]))[0]
    rows = c.execute("select ts,open,high,low,close,volume from TvCandle where symbol=? and interval=? and source=? order by ts",
                     (symbol, interval, src)).fetchall()
    return rows, src


def atr14(b):
    tr = [max(b[i][2] - b[i][3], abs(b[i][2] - b[i - 1][4]), abs(b[i][3] - b[i - 1][4])) for i in range(1, len(b))]
    if len(tr) < 14:
        return None
    a = sum(tr[:14]) / 14
    for x in tr[14:]:
        a = (a * 13 + x) / 14
    return a


def ema(v, p):
    out = [None] * len(v)
    if len(v) < p:
        return out
    k = 2 / (p + 1); s = sum(v[:p]) / p; out[p - 1] = s
    for i in range(p, len(v)):
        s = v[i] * k + s * (1 - k); out[i] = s
    return out


def macd_hist(closes):
    e12, e26 = ema(closes, 12), ema(closes, 26)
    m = [a - b for a, b in zip(e12, e26) if a is not None and b is not None]
    sig = ema(m, 9)
    return [a - b for a, b in zip(m, sig) if b is not None]


def cci(b, n=20):
    tp = [(x[2] + x[3] + x[4]) / 3 for x in b]
    def at(k):
        w = tp[k - n + 1:k + 1]; m = sum(w) / n; md = sum(abs(x - m) for x in w) / n
        return (tp[k] - m) / (0.015 * md) if md else 0.0
    return at(len(tp) - 2), at(len(tp) - 1)


def delta(x):
    r = x[2] - x[3]
    return 0.0 if r <= 0 else x[5] * (x[4] - x[1]) / r


def recompute(c, symbol, tf, bar_ts, detected_ms):
    bars, src = load_bars(c, symbol, tf)
    upto = [b for b in bars if b[0] <= bar_ts]
    if len(upto) < 40 or upto[-1][0] != bar_ts:
        print(f"   ⚠️ ไม่มีแท่ง {tf} ที่ {ts(bar_ts)} ในฐานข้อมูล (อาจถูกลบ/ยังไม่ได้ดึง)")
        return
    last, prev = upto[-1], upto[-2]
    a = atr14(upto)
    rng = last[2] - last[3]; body = abs(last[4] - last[1])
    uw = last[2] - max(last[1], last[4]); lw = min(last[1], last[4]) - last[3]
    prior = upto[-11:-1]
    print(f"   แท่ง {tf} [{src}] O={last[1]} H={last[2]} L={last[3]} C={last[4]} | แท่งก่อน H={prev[2]} L={prev[3]}")
    print(f"   ATR14={a:.5f} | ช่วงแท่ง {rng:.2f} ({rng / a:.2f} ATR) body {body:.2f} ไส้บน {uw:.2f} ({uw / rng * 100 if rng else 0:.0f}%) ไส้ล่าง {lw:.2f}")
    print(f"   high/low 10 แท่งก่อน = {max(b[2] for b in prior)} / {min(b[3] for b in prior)} | high/low 20 แท่ง = {max(b[2] for b in upto[-20:])} / {min(b[3] for b in upto[-20:])}")
    c0, c1 = cci(upto)
    h = macd_hist([b[4] for b in upto])
    rec = sum(delta(x) for x in upto[-10:]); bef = sum(delta(x) for x in upto[-20:-10]); rp = sum(delta(x) for x in upto[-11:-1])
    print(f"   CCI20 {c0:.1f} → {c1:.1f} | MACD hist {h[-2]:.3f} → {h[-1]:.3f} | delta 10 แท่ง {rp:,.0f} → {rec:,.0f} (ช่วงก่อน {bef:,.0f})")
    # วันก่อนหน้า (ตามขอบวันเทรดของตลาด)
    h1, _ = load_bars(c, symbol, "1h")
    shift = day_shift_ms(symbol)
    key = lambda ms: (ms + shift) // 86_400_000
    days = defaultdict(list)
    for b in h1:
        if b[0] < detected_ms:
            days[key(b[0])].append(b)
    prevs = [k for k in days if k < key(detected_ms)]
    if prevs:
        pd = days[max(prevs)]
        print(f"   วันก่อน ({ts(pd[0][0])} → {ts(pd[-1][0])}) High={max(b[2] for b in pd)} Low={min(b[3] for b in pd)}")



# ─── ตรวจเงื่อนไขของปัจจัยที่ปลุก (สูตรเดียวกับ triggers ในแอป) ─────────────────

def _rsi_series(cl, n=14):
    """Wilder RSI — เหมือน TaIndicators.rsiSeries"""
    out = [None] * len(cl)
    if len(cl) < n + 1:
        return out
    g = sum(max(cl[i] - cl[i - 1], 0) for i in range(1, n + 1)) / n
    l = sum(max(cl[i - 1] - cl[i], 0) for i in range(1, n + 1)) / n
    out[n] = 100.0 if l == 0 else 100 - 100 / (1 + g / l)
    for i in range(n + 1, len(cl)):
        d = cl[i] - cl[i - 1]
        g = (g * (n - 1) + max(d, 0)) / n; l = (l * (n - 1) + max(-d, 0)) / n
        out[i] = 100.0 if l == 0 else 100 - 100 / (1 + g / l)
    return out


def _williams(b, n=14):
    hh = max(x[2] for x in b[-n:]); ll = min(x[3] for x in b[-n:])
    return -50.0 if hh == ll else (hh - b[-1][4]) / (hh - ll) * -100


def _vwap(b, offset_hours):
    """VWAP ของ session ล่าสุด — เหมือน lastSessionAnchor + vwapAnchored (typical price)"""
    day = 86_400_000; off = offset_hours * 3_600_000
    anchor = ((b[-1][0] - off) // day) * day + off
    sess = [x for x in b if x[0] >= anchor]
    vol = sum(x[5] for x in sess)
    if vol <= 0 or len(sess) < 3:
        return None
    return sum((x[2] + x[3] + x[4]) / 3 * x[5] for x in sess) / vol


def _pivots(b, L=5):
    """swing ที่ยืนยันแล้ว ตามตำแหน่งจุดยอดจริง (center) — เหมือน confirmedSwings + swing*Pivots"""
    hs, ls = [], []
    for cen in range(L, len(b) - L):
        hc, lc = b[cen][2], b[cen][3]
        win = b[cen - L:cen + L + 1]
        if all(x[2] <= hc for x in win): hs.append((cen, hc))
        if all(x[3] >= lc for x in win): ls.append((cen, lc))
    return hs, ls


# ปัจจัยที่ต้องใช้ข้อมูลที่สคริปต์ไม่มี: โครงสร้างตลาด (BOS/CHoCH), ภาพ 5TF (digest), engine เฉพาะ, ข้อมูลภายนอก
_NEEDS_APP = {
    "BOS", "CHOCH", "HTF_CHOCH_H1", "HTF_CHOCH_H4", "SWING_FAILURE", "HL_LH_CONFIRMED", "INTERNAL_EXTERNAL_MISMATCH",
    "KEY_LEVEL_TOUCH", "OB_TOUCH", "OB_MITIGATED", "FVG_ENTER", "FVG_FILLED", "BREAKER_RETEST", "SR_FLIP_RETEST",
    "VOLUME_PROFILE_LEVEL", "ENGULFING_AT_LEVEL", "VOLUME_DRYUP_AT_LEVEL", "OBV_DIVERGENCE", "RSI_DIVERGENCE",
    "RSI_HIDDEN_DIVERGENCE", "MACD_DIVERGENCE", "HEAD_SHOULDERS", "TRIANGLE_WEDGE_BREAK", "CHANNEL_TOUCH",
    "TRENDLINE_BREAK", "HARMONIC_COMPLETION", "ELLIOTT_STAGE_CHANGE", "ALL_TF_ALIGNED", "M5_CONFIRM_DIVERGENCE",
    "HTF_CONFLICT", "LTF_PULLBACK_END", "TF_CONTINUATION", "INSTITUTIONAL_SHIFT", "DEEP_SCORE_CROSS",
    "DXY_MOVE", "YIELD_SPIKE", "CORRELATION_BREAK", "CRYPTO_BROAD_MOVE", "HIGH_IMPACT_NEWS_SOON", "POST_NEWS_SPIKE",
    "FEAR_GREED_EXTREME", "EQ_POOL_FORMED", "EQ_POOL_SWEPT",
}


def trigger_checks(b, symbol, h1=None, detected_ms=None):
    """สูตรของปัจจัยที่ตรวจนอกแอปได้ — คืน {id: fn() -> (รายละเอียด, ทิศที่คำนวณได้ | None)}
    b = แท่งปิดของ TF หลัก (แท่งสุดท้าย = แท่งที่ตรวจ), h1 = แท่ง H1 (ใช้กับ PDH/PDL)"""
    cl = [x[4] for x in b]
    atr = atr14(b)
    last, prev = b[-1], b[-2]
    e = {p: ema(cl, p) for p in (12, 20, 26, 50, 200)}
    hist = macd_hist(cl)
    rsi14 = _rsi_series(cl, 14); rsi5 = _rsi_series(cl, 5)
    shift_h = -day_shift_ms(symbol) // 3_600_000

    def ribbon(k):
        a, m, z = e[20][k], e[50][k], e[200][k]
        if None in (a, m, z): return None
        return 1 if a > m > z else -1 if a < m < z else 0

    checks = {}
    checks["MACD_SIGNAL_CROSS"] = lambda: (f"hist {hist[-2]:.3f} → {hist[-1]:.3f}",
        "BUY" if hist[-2] <= 0 < hist[-1] else "SELL" if hist[-2] >= 0 > hist[-1] else None)
    def zero():
        m1 = e[12][-1] - e[26][-1]; m0 = e[12][-2] - e[26][-2]
        return (f"MACD line {m0:.3f} → {m1:.3f}", "BUY" if m0 <= 0 < m1 else "SELL" if m0 >= 0 > m1 else None)
    checks["MACD_ZERO_CROSS"] = zero
    def turn():
        now, p1, p2 = hist[-1], hist[-2], hist[-3]
        d = "BUY" if now < 0 and p1 < 0 and p1 < p2 and now > p1 else "SELL" if now > 0 and p1 > 0 and p1 > p2 and now < p1 else None
        return (f"hist {p2:.3f}, {p1:.3f}, {now:.3f}", d)
    checks["MACD_HISTOGRAM_TURN"] = turn
    def cci_x():
        c0, c1 = cci(b)
        return (f"CCI {c0:.1f} → {c1:.1f}", "BUY" if c0 <= 100 < c1 else "SELL" if c0 >= -100 > c1 else None)
    checks["CCI_100_CROSS"] = cci_x
    checks["RSI_50_CROSS"] = lambda: (f"RSI {rsi14[-2]:.1f} → {rsi14[-1]:.1f}",
        "BUY" if rsi14[-2] <= 50 < rsi14[-1] else "SELL" if rsi14[-2] >= 50 > rsi14[-1] else None)
    checks["RSI_EXIT_EXTREME"] = lambda: (f"RSI {rsi14[-2]:.1f} → {rsi14[-1]:.1f}",
        "BUY" if rsi14[-2] <= 30 < rsi14[-1] else "SELL" if rsi14[-2] >= 70 > rsi14[-1] else None)
    checks["FAST_RSI_THRUST"] = lambda: (f"RSI(5) {rsi5[-2]:.1f} → {rsi5[-1]:.1f}",
        "BUY" if rsi5[-2] <= 35 < rsi5[-1] else "SELL" if rsi5[-2] >= 65 > rsi5[-1] else None)
    def wr():
        w1, w0 = _williams(b), _williams(b[:-1])
        return (f"%R {w0:.1f} → {w1:.1f}", "BUY" if w0 <= -80 < w1 else "SELL" if w0 >= -20 > w1 else None)
    checks["WILLIAMS_R_EXIT"] = wr
    def vol_spike():
        avg = sum(x[5] for x in b[-21:-1]) / 20
        r = last[5] / avg if avg > 0 else 0
        d = ("BUY" if last[4] > last[1] else "SELL" if last[4] < last[1] else "NEUTRAL") if r >= 2 else None
        return (f"volume {r:.2f}x ค่าเฉลี่ย 20 แท่ง", d)
    checks["VOLUME_SPIKE"] = vol_spike
    def dshift():
        rec = sum(delta(x) for x in b[-10:]); rp = sum(delta(x) for x in b[-11:-1]); bef = sum(delta(x) for x in b[-20:-10])
        d = "BUY" if rec > 0 >= rp and bef < 0 else "SELL" if rec < 0 <= rp and bef > 0 else None
        return (f"delta {rp:,.1f} → {rec:,.1f} (ก่อนหน้า {bef:,.1f})", d)
    checks["DELTA_SHIFT"] = dshift
    def pullback():
        t = ribbon(-1)
        if t == 1:
            lv = next((x for x in (e[20][-1], e[50][-1]) if last[3] <= x + 0.1 * atr and last[4] > x), None)
            return (f"ribbon ขาขึ้น EMA20 {e[20][-1]:.2f} EMA50 {e[50][-1]:.2f} low {last[3]}", "BUY" if lv else None)
        if t == -1:
            lv = next((x for x in (e[20][-1], e[50][-1]) if last[2] >= x - 0.1 * atr and last[4] < x), None)
            return (f"ribbon ขาลง EMA20 {e[20][-1]:.2f} EMA50 {e[50][-1]:.2f} high {last[2]}", "SELL" if lv else None)
        return ("EMA20/50/200 ไม่เรียงตัว", None)
    checks["EMA_PULLBACK"] = pullback
    def ema_cross():
        f0, f1, s0, s1 = ema(cl, 14)[-2], ema(cl, 14)[-1], ema(cl, 60)[-2], ema(cl, 60)[-1]
        return (f"EMA14 {f0:.2f}→{f1:.2f} vs EMA60 {s0:.2f}→{s1:.2f}",
                "BUY" if f0 <= s0 and f1 > s1 else "SELL" if f0 >= s0 and f1 < s1 else None)
    checks["EMA_CROSS"] = ema_cross
    def vwap_x():
        v1, v0 = _vwap(b, shift_h), _vwap(b[:-1], shift_h)
        if v1 is None or v0 is None: return ("VWAP คำนวณไม่ได้", None)
        return (f"close {prev[4]}→{last[4]} vs VWAP {v0:.2f}→{v1:.2f}",
                "BUY" if prev[4] <= v0 and last[4] > v1 else "SELL" if prev[4] >= v0 and last[4] < v1 else None)
    checks["VWAP_RECLAIM"] = vwap_x
    def obv_lead():
        acc, obv = 0.0, [0.0]
        for i in range(1, len(b)):
            acc += b[i][5] if cl[i] > cl[i - 1] else -b[i][5] if cl[i] < cl[i - 1] else 0.0
            obv.append(acc)
        win = obv[-21:-1]; hi = max(x[2] for x in b[-21:-1]); lo = min(x[3] for x in b[-21:-1])
        d = "BUY" if obv[-1] > max(win) >= obv[-2] and last[4] < hi else "SELL" if obv[-1] < min(win) <= obv[-2] and last[4] > lo else None
        return (f"OBV {obv[-2]:,.0f} → {obv[-1]:,.0f} (กรอบ 20 แท่ง {min(win):,.0f}..{max(win):,.0f})", d)
    checks["OBV_LEADING_BREAKOUT"] = obv_lead
    def dtop():
        hs, ls = _pivots(b)
        c1, c0 = last[4], prev[4]
        if len(hs) >= 2:
            (i1, h1), (i2, h2) = hs[-2], hs[-1]
            if abs(h1 - h2) <= 0.3 * atr and i2 - i1 >= 5:
                neck = min(x[3] for x in b[i1:i2 + 1])
                if c0 >= neck > c1: return (f"double top {h1}/{h2} neckline {neck}", "SELL")
        if len(ls) >= 2:
            (i1, l1), (i2, l2) = ls[-2], ls[-1]
            if abs(l1 - l2) <= 0.3 * atr and i2 - i1 >= 5:
                neck = max(x[2] for x in b[i1:i2 + 1])
                if c0 <= neck < c1: return (f"double bottom {l1}/{l2} neckline {neck}", "BUY")
        return ("ไม่เข้ารูปยอดคู่/ก้นคู่หลุด neckline", None)
    checks["DOUBLE_TOP_BOTTOM"] = dtop
    def ibb():
        m, i, l = b[-3], b[-2], b[-1]
        if not (i[2] < m[2] and i[3] > m[3]): return ("แท่งก่อนหน้าไม่ใช่ inside bar", None)
        return (f"mother H{m[2]} L{m[3]} close {l[4]}", "BUY" if l[4] > m[2] else "SELL" if l[4] < m[3] else None)
    checks["INSIDE_BAR_BREAK"] = ibb

    body = abs(last[4] - last[1]); rng = last[2] - last[3]
    uw = last[2] - max(last[1], last[4]); lw = min(last[1], last[4]) - last[3]
    color = "BUY" if last[4] > last[1] else "SELL" if last[4] < last[1] else "NEUTRAL"
    avg20 = sum(x[5] for x in b[-21:-1]) / 20

    checks["IMPULSE_CANDLE"] = lambda: (f"body {body:.2f} = {body / atr:.2f} ATR (เกณฑ์ ≥ 1.5)", color if body >= 1.5 * atr else None)
    def range_bo():
        hi = max(x[2] for x in b[-21:-1]); lo = min(x[3] for x in b[-21:-1])
        if hi - lo > 3 * atr: return (f"กรอบ 20 แท่ง {hi - lo:.2f} = {(hi - lo) / atr:.1f} ATR (กว้างเกิน 3)", None)
        return (f"close {last[4]} vs กรอบ {lo}..{hi}", "BUY" if last[4] > hi else "SELL" if last[4] < lo else None)
    checks["RANGE_BREAKOUT"] = range_bo
    def failed_bo():
        brk = b[-2]; win = b[-22:-2]
        hi = max(x[2] for x in win); lo = min(x[3] for x in win)
        d = "SELL" if brk[4] > hi > last[4] else "BUY" if brk[4] < lo < last[4] else None
        return (f"แท่งหลุด close {brk[4]} → กลับ {last[4]} กรอบ {lo}..{hi}", d)
    checks["FAILED_BREAKOUT"] = failed_bo
    def pin():
        if rng <= 0 or rng < 0.5 * atr: return (f"แท่งเล็กเกิน ({rng / atr:.2f} ATR)", None)
        d = "BUY" if lw >= 2 * body and lw >= 0.6 * rng else "SELL" if uw >= 2 * body and uw >= 0.6 * rng else None
        return (f"ไส้บน {uw / rng * 100:.0f}% ไส้ล่าง {lw / rng * 100:.0f}% body {body:.2f}", d)
    checks["PIN_BAR"] = pin
    def doji():
        if rng <= 0 or body > 0.1 * rng: return (f"body {body:.2f} > 10% ของแท่ง", None)
        hi = max(x[2] for x in b[-20:]); lo = min(x[3] for x in b[-20:])
        return (f"doji ที่ high {last[2]} / low {last[3]} (20 แท่ง {lo}..{hi})", "SELL" if last[2] >= hi else "BUY" if last[3] <= lo else None)
    checks["DOJI_AT_EXTREME"] = doji
    def nr7():
        is_nr7 = all(x[2] - x[3] >= rng for x in b[-7:])
        inside = last[2] < prev[2] and last[3] > prev[3]
        return (f"NR7={is_nr7} inside={inside}", "NEUTRAL" if is_nr7 or inside else None)
    checks["NR7_INSIDE_BAR"] = nr7
    def sweep():
        prior = b[-11:-1]; ph = max(x[2] for x in prior); pl = min(x[3] for x in prior)
        floor = 1.5 * max(body, 0.2 * atr)
        d = "BUY" if last[3] < pl and lw >= floor else "SELL" if last[2] > ph and uw >= floor else None
        return (f"high {last[2]} vs {ph}, low {last[3]} vs {pl}, ไส้ ≥ {floor:.2f}? บน {uw:.2f} ล่าง {lw:.2f}", d)
    checks["LIQUIDITY_SWEEP_REJECTION"] = sweep
    def absorb():
        r = last[5] / avg20 if avg20 > 0 else 0
        ok = r >= 1.8 and body <= 0.35 * atr
        return (f"volume {r:.2f}x body {body / atr:.2f} ATR", ("BUY" if last[4] <= last[1] else "SELL") if ok else None)
    checks["VOLUME_ABSORPTION"] = absorb
    def accel():
        def slope(v):
            n = len(v); xm = (n - 1) / 2; ym = sum(v) / n
            den = sum((i - xm) ** 2 for i in range(n))
            return sum((i - xm) * (v[i] - ym) for i in range(n)) / den
        fast = slope(cl[-10:]); slow = slope(cl[-30:-10]); fast_p = slope(cl[-11:-1])
        acc = abs(fast) > 0.3 * atr and abs(fast) > 2 * abs(slow) and (slow == 0 or fast * slow > 0)
        was = abs(fast_p) > 0.3 * atr and abs(fast_p) > 2 * abs(slow)
        return (f"slope {fast_p / atr:.2f}→{fast / atr:.2f} ATR/แท่ง (ก่อนหน้า {slow / atr:.2f})",
                ("BUY" if fast > 0 else "SELL") if acc and not was else None)
    checks["TREND_ACCELERATION"] = accel
    def bb(data):
        w = data[-20:]; m = sum(w) / 20; sd = (sum((x - m) ** 2 for x in w) / 20) ** 0.5
        return m + 2 * sd, m - 2 * sd
    def band_rej():
        up_p, lo_p = bb(cl[:-1]); up, lo = bb(cl)
        d = "SELL" if prev[2] >= up_p and last[4] < up and last[4] < last[1] else \
            "BUY" if prev[3] <= lo_p and last[4] > lo and last[4] > last[1] else None
        return (f"BB ก่อน {lo_p:.2f}..{up_p:.2f} แท่งก่อน H{prev[2]} L{prev[3]} close {last[4]}", d)
    checks["BAND_REJECTION"] = band_rej
    def mfi(bb_):
        tp = [(x[2] + x[3] + x[4]) / 3 for x in bb_]
        pos = neg = 0.0
        for i in range(len(bb_) - 14, len(bb_)):
            f = tp[i] * bb_[i][5]
            if tp[i] > tp[i - 1]: pos += f
            elif tp[i] < tp[i - 1]: neg += f
        return (50.0 if pos == 0 else 100.0) if neg == 0 else 100 - 100 / (1 + pos / neg)
    def mfi_x():
        m0, m1 = mfi(b[:-1]), mfi(b)
        return (f"MFI {m0:.1f} → {m1:.1f}", "SELL" if m0 < 80 <= m1 else "BUY" if m0 > 20 >= m1 else None)
    checks["MFI_EXTREME"] = mfi_x
    def stoch(bb_):
        raw = []
        for i in range(13, len(bb_)):
            hh = max(x[2] for x in bb_[i - 13:i + 1]); ll = min(x[3] for x in bb_[i - 13:i + 1])
            raw.append((bb_[i][4] - ll) / (hh - ll) * 100 if hh > ll else 50.0)
        slow = [sum(raw[i - 2:i + 1]) / 3 for i in range(2, len(raw))]
        return slow[-1], sum(slow[-3:]) / 3
    def stoch_x():
        k0, d0 = stoch(b[:-1]); k1, d1 = stoch(b)
        d = "BUY" if k0 <= d0 and k1 > d1 and d1 < 20 else "SELL" if k0 >= d0 and k1 < d1 and d1 > 80 else None
        return (f"%K/%D {k0:.1f}/{d0:.1f} → {k1:.1f}/{d1:.1f}", d)
    checks["STOCH_EXTREME_CROSS"] = stoch_x
    def vwap_band():
        day = 86_400_000; off = shift_h * 3_600_000
        anchor = ((last[0] - off) // day) * day + off
        sess = [x for x in b if x[0] >= anchor]; vol = sum(x[5] for x in sess)
        if vol <= 0 or len(sess) < 3: return ("VWAP คำนวณไม่ได้", None)
        tp = [(x[2] + x[3] + x[4]) / 3 for x in sess]
        vw = sum(t * x[5] for t, x in zip(tp, sess)) / vol
        sd = (sum(x[5] * (t - vw) ** 2 for t, x in zip(tp, sess)) / vol) ** 0.5
        up, dn = vw + 2 * sd, vw - 2 * sd
        touch = lambda x, lv: x[3] <= lv <= x[2]
        d = "SELL" if touch(last, up) and not touch(prev, up) else "BUY" if touch(last, dn) and not touch(prev, dn) else None
        return (f"VWAP {vw:.2f} ±2σ {dn:.2f}..{up:.2f} แท่ง {last[3]}..{last[2]}", d)
    checks["VWAP_BAND_TOUCH"] = vwap_band
    def pdh():
        if detected_ms is None or not h1: return ("ไม่มีเวลาตรวจพบ/แท่ง H1", None)
        shift = day_shift_ms(symbol); key = lambda ms: (ms + shift) // 86_400_000
        days = defaultdict(list)
        for x in h1:
            if x[0] + 3_600_000 <= detected_ms: days[key(x[0])].append(x)
        prevs = sorted(k for k in days if k < key(detected_ms))
        if len(prevs) < 2: return ("ข้อมูลวันก่อนไม่พอ", None)
        pd = days[prevs[-1]]; hi = max(x[2] for x in pd); lo = min(x[3] for x in pd)
        touch = lambda x, lv: x[3] <= lv <= x[2]
        hit = (touch(last, hi) and not touch(prev, hi)) or (touch(last, lo) and not touch(prev, lo))
        return (f"PDH {hi} PDL {lo} แท่ง {last[3]}..{last[2]}", "NEUTRAL" if hit else None)
    checks["PDH_PDL_TOUCH"] = pdh
    return checks


TF_MS = {"1m": 60_000, "5m": 300_000, "15m": 900_000, "30m": 1_800_000, "1h": 3_600_000, "4h": 14_400_000}
TF_LABEL = {"1m": "M1", "5m": "M5", "15m": "M15", "30m": "M30", "1h": "H1", "4h": "H4"}


def last_closed_bar(c, symbol, tf, at_ms):
    """แท่งปิดล่าสุดของ tf ณ เวลา at_ms (แอปใช้แท่งล่าสุด 800 แท่ง = Warmup.FULL_SET ทุก TF)"""
    bars, _ = load_bars(c, symbol, tf)
    return [x for x in bars if x[0] + TF_MS.get(tf, 900_000) <= at_ms][-800:]


def verify_triggers(c, symbol, tf, bar_ts, woke, detected_ms=None):
    """พิมพ์ ✓/✗ ต่อปัจจัยที่ปลุก — '—' = ตรวจนอกแอปไม่ได้ (ต้องใช้ engine/ข้อมูลภายนอกของแอป)
    woke = "ID@tf(SIDE), …" (ตั้งแต่ P16 ปัจจัยเกิดได้หลาย TF) หรือ "ID(SIDE), …" (รุ่นก่อน = TF หลัก)
    แต่ละปัจจัยตรวจบนแท่งปิดล่าสุดของ TF ที่มันเกิด ณ เวลาที่ตรวจพบ"""
    h1 = load_bars(c, symbol, "1h")[0]
    cache = {}

    def checks_for(ftf):
        if ftf not in cache:
            if detected_ms is not None:
                b = last_closed_bar(c, symbol, ftf, detected_ms)
            else:
                bars, _ = load_bars(c, symbol, ftf)
                b = [x for x in bars if x[0] <= bar_ts][-800:]
            cache[ftf] = trigger_checks(b, symbol, h1, detected_ms) if len(b) >= 60 else None
        return cache[ftf]

    print("   ตรวจเงื่อนไขปัจจัย (คำนวณใหม่จากแท่งดิบของ TF ที่ปัจจัยเกิด):")
    for item in (woke or "").split(", "):
        if "(" not in item:
            continue
        key, side = item[:-1].split("(")
        fid, _, ftf = key.partition("@")
        ftf = ftf or tf
        checks = checks_for(ftf)
        if checks is None:
            print(f"     ?  {fid}@{TF_LABEL.get(ftf, ftf)}({side}) ไม่มีแท่ง {ftf} พอในฐานข้อมูล")
            continue
        fn = checks.get(fid)
        if fn is None:
            why = "ต้องใช้ข้อมูลภายในแอป (โครงสร้าง/digest/engine/ข้อมูลภายนอก)" if fid in _NEEDS_APP else "ยังไม่มีสูตรในสคริปต์"
            print(f"     —  {fid}@{TF_LABEL.get(ftf, ftf)}({side}) {why}")
            continue
        try:
            detail, got = fn()
        except Exception as ex:  # ข้อมูลไม่พอ ฯลฯ
            print(f"     ?  {fid}@{TF_LABEL.get(ftf, ftf)}({side}) คำนวณไม่ได้: {ex}")
            continue
        ok = got == side
        print(f"     {'✓' if ok else '✗'}  {fid}@{TF_LABEL.get(ftf, ftf)}({side}) {detail}" + ("" if ok else f" → คำนวณได้ {got}"))

# ─── main ────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", help="ใช้ไฟล์ jarvis.db ที่ดึงมาแล้ว (ไม่ต้องต่อมือถือ)")
    ap.add_argument("--last", type=int, default=5, help="จำนวนการปลุกล่าสุดที่แสดง")
    ap.add_argument("--logcat", action="store_true", help="สรุป log ล่าสุดจากมือถือ")
    ap.add_argument("--keep", action="store_true", help="ไม่ลบสำเนาฐานข้อมูลตอนจบ")
    args = ap.parse_args()

    adb = None if (args.db and not args.logcat) else find_adb()
    path = args.db or pull_db(adb)
    c = None
    try:
        c = sqlite3.connect(path)
        print(f"ฐานข้อมูล: {path} | quick_check={c.execute('pragma quick_check').fetchone()[0]} | schema={c.execute('pragma user_version').fetchone()[0]}")

        print("\n══ Alert jobs ══")
        for r in c.execute("select id,name,symbol,tool_name,condition_json,interval_minutes,is_active,is_triggered,last_value,last_run_at from AlertJob"):
            print(f" #{r[0]} {r[1]} | {r[2]} | {r[3]} | {r[4]} | ทุก {r[5]} นาที | active={r[6]} triggered={r[7]} last_value={r[8]} | เช็กล่าสุด {ts(r[9]) if r[9] else '-'}")

        print("\n══ การตั้งค่า ══")
        for k in ["alert_voice", "alert_voice_engine", "alert_ai_summary", "model_name",
                  "wake.usage", "wake.budget.hourly", "wake.budget.daily", "wake.cooldown.bars", "wake.min_gap.minutes", "wake.disabled"]:
            v = c.execute("select value from AppSetting where key=?", (k,)).fetchone()
            print(f" {k} = {v[0] if v else '(ไม่ได้ตั้ง → ค่าเริ่มต้น)'}")
        print(" (alert_voice_engine: device = Android TTS · live = Gemini Live · ถ้ากำลังคุย Live อยู่ session นั้นพูดแทน)")

        print(f"\n══ การปลุก {args.last} ครั้งล่าสุด ══")
        # ai_confidence / ai_reason เพิ่มใน migration 14 — มือถือที่ยังใช้แอปรุ่นเก่าจะไม่มีคอลัมน์นี้
        cols = {r[1] for r in c.execute("pragma table_info(AnticipationFactorOutcome)")}
        has_reason = {"ai_confidence", "ai_reason"} <= cols
        # AiWakeView เพิ่มใน migration 15 — ติดตามผลมุมมองของ AI (ชน TP/SL)
        has_views = c.execute("select count(*) from sqlite_master where type='table' and name='AiWakeView'").fetchone()[0] == 1
        reason_sql = "max(ai_confidence), max(ai_reason)" if has_reason else "null, null"
        # factor_tf เพิ่มใน migration 16 — ปัจจัยเกิดได้หลาย TF ในการสแกนเดียว
        fkey = "factor_id||'@'||factor_tf" if "factor_tf" in cols else "factor_id"
        # ATR ของ TF หลัก (แถวของ TF อื่นเก็บ ATR ของ TF ตัวเอง — H4 ใหญ่กว่า M15 หลายเท่า)
        atr_sql = ("coalesce(max(case when woke=1 and factor_tf=interval then ref_atr end), "
                   "max(case when factor_tf=interval then ref_atr end))") if "factor_tf" in cols else "max(case when woke=1 then ref_atr end)"
        wakes = c.execute(f"""
            select signal_id, min(created_at), max(ai_decision), max(ai_bias),
                   group_concat(case when woke=1 then {fkey}||'('||side||')' end, ', '),
                   group_concat(case when woke=0 then {fkey}||'['||kind||']' end, ', '),
                   max(case when woke=1 then ref_price end), {atr_sql},
                   group_concat(distinct status), {reason_sql}
            from AnticipationFactorOutcome group by signal_id
            having sum(woke) > 0 order by min(created_at) desc limit ?""", (args.last,)).fetchall()
        if not has_reason:
            print(" (ฐานข้อมูลยังไม่มีคอลัมน์เหตุผล/ความมั่นใจของ AI — แอปรุ่นก่อน migration 14)")
        cards = c.execute("select timestamp, content, metadata from ChatMessage where metadata like '%\"wake_%' order by timestamp").fetchall()
        for sig, created, dec, bias, woke, states, ref, atr, status, conf, reason in reversed(wakes):
            sym, tf, sid_ts = sig.split("|"); sid_ts = int(sid_ts)
            # ตั้งแต่ P16 รหัส = นาทีที่สแกน (ปลุกได้ทุกนาที) — แท่ง TF หลัก = แท่งปิดล่าสุด ณ เวลาที่ตรวจพบ
            prim = last_closed_bar(c, sym, tf, created)
            bar = prim[-1][0] if prim else sid_ts
            conf_txt = f" {conf}%" if conf is not None else ""
            print(f"\n▶ {sig}  แท่ง {tf} ล่าสุด {ts(bar)} | ตรวจพบ {ts(created)} | AI: {dec or '-'} {bias or ''}{conf_txt} | ราคาอ้างอิง {ref} ATR {tf} {f"{atr:.3f}" if atr else "-"} | สถานะวัดผล {status}")
            if reason:
                print(f"   เหตุผล AI: {reason}")
            if has_views:
                v = c.execute("select status, result_r, move_atr, mfe_atr, mae_atr, bars, level_sl, level_tp from AiWakeView where signal_id=?", (sig,)).fetchone()
                if v:
                    st, rr, mv, mfe, mae, nb, vsl, vtp = v
                    if st == "OPEN":
                        print(f"   ผลมุมมอง AI: ⏳ ยังติดตามอยู่" + (f" (SL {vsl} / TP {vtp})" if vsl and vtp else ""))
                    elif st in ("TP", "SL", "EXPIRED"):
                        print(f"   ผลมุมมอง AI: {'✅' if st == 'TP' else '❌' if st == 'SL' else '⌛'} {st} {rr:+.2f}R ใน {nb} แท่ง (ไปตามทิศสูงสุด {mfe:.1f} ATR / สวนทิศสูงสุด {mae:.1f} ATR)")
                    else:
                        print(f"   ผลมุมมอง AI: ครบ {nb} แท่ง ราคาเคลื่อน {mv:+.1f} ATR (สูงสุด {mfe:.1f} ATR)")
            elif dec and has_reason:
                print("   เหตุผล AI: (ไม่มี — การปลุกก่อนติดตั้งเวอร์ชันที่เก็บเหตุผล)")
            print(f"   ปัจจัยที่ปลุก: {woke}")
            if states:
                # woke=0: สภาวะที่เพิ่งเป็นจริง [STATE] หรือเหตุการณ์ที่เกิดทีหลังในแท่งเดียวกัน [EVENT]
                # (แท่งนั้นปลุกไปแล้ว — ปลุกได้แท่งละครั้ง) บันทึกเข้าการเรียนรู้แต่ไม่ได้ส่งให้ AI
                print(f"   บันทึกเพิ่ม (ไม่ได้ปลุก AI): {states}")
            woke_ids = {w.split("(")[0] for w in (woke or "").split(", ") if w}   # "ID@tf" (หรือ "ID" รุ่นก่อน)
            card = None
            if dec != "SKIP":
                for x in cards:
                    if not (0 <= x[0] - created <= 15 * 60_000):
                        continue
                    zone = set((json.loads(x[2]).get("zone") or "").split(","))
                    # การ์ดรุ่นก่อน P16 เก็บแค่ "ID" — เทียบแบบไม่มี TF ด้วย
                    if woke_ids <= zone or {w.split("@")[0] for w in woke_ids} <= {z.split("@")[0] for z in zone}:
                        card = x
                        break
            if card:
                meta = json.loads(card[2])
                print(f"   การ์ดแชท {ts(card[0])} · side={meta.get('side')} conf={meta.get('confidence')} ราคา={meta.get('price')} เสียง={meta.get('voice')}")
                lv = re.search(r"ระดับที่ AI จับตา: ([^\n]+)", card[1])
                if lv:
                    print(f"   ระดับที่ AI ให้: {lv.group(1)}")
                if meta.get("summary"):
                    print(f"   สรุป AI: {meta['summary'][:220]}")
            elif dec == "SKIP":
                print("   (AI ตอบ SKIP — ไม่แจ้งผู้ใช้ ไม่มีการ์ด)")
            else:
                print("   ⚠️ ไม่พบการ์ดในแชทของการปลุกนี้ (AI ไม่ตอบ/ถูกลบแชท/แจ้งแบบ direct ไม่สำเร็จ)")
            recompute(c, sym, tf, bar, created)
            verify_triggers(c, sym, tf, bar, woke, created)

        if args.logcat:
            print("\n══ logcat ล่าสุด ══")
            pid = subprocess.run([adb, "shell", "pidof", PKG], capture_output=True, text=True).stdout.strip()
            if not pid:
                print(" แอปไม่ได้ทำงานอยู่")
            else:
                out = subprocess.run([adb, "logcat", "-d", f"--pid={pid}"], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
                lines = [l for l in out.splitlines() if re.search(r"SmcApiService|TvHistoryBridge|AutomationService|WakeEngine|WakeLearning|OhlcvMaintenance", l)]
                for l in lines[-30:]:
                    print(" " + l[:230])
                fetch_t = []
                for l in lines:
                    m = re.match(r"(\d\d-\d\d \d\d:\d\d:\d\d\.\d+).*TV (delta|cold fetch)", l)
                    if m:
                        # logcat ไม่มีปี — ใส่ปีปัจจุบันเพื่อ parse (ใช้แค่หาระยะห่าง)
                        fetch_t.append(dt.datetime.strptime(f"{dt.date.today().year}-{m.group(1)}", "%Y-%m-%d %H:%M:%S.%f"))
                gaps = [(b - a).total_seconds() for a, b in zip(fetch_t, fetch_t[1:]) if (b - a).total_seconds() < 30]
                if gaps:
                    print(f" ระยะห่างการดึงแท่งเทียนต่อเนื่อง: เฉลี่ย {sum(gaps) / len(gaps):.1f} วิ (n={len(gaps)}) — ปกติควร ≤ 2 วิ")
                errs = [l for l in lines if re.search(r"protocol_error|critical_error|symbol_error|series_error", l)]
                if errs:
                    print(f" ⚠️ TradingView ตอบ error {len(errs)} ครั้ง — ล่าสุด: {errs[-1][:200]}")
    finally:
        # ปิด connection ก่อนลบ — บน Windows ไฟล์ที่ยังเปิดอยู่ลบไม่ได้ (สำเนาที่มีข้อมูลส่วนตัวจะค้างในเครื่อง)
        if c is not None:
            c.close()
        if not args.db and not args.keep:
            os.remove(path)
            print("\n(ลบสำเนาฐานข้อมูลแล้ว)")


if __name__ == "__main__":
    main()
