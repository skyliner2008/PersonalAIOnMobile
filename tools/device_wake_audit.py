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
TF_MS = {"1m": 60_000, "5m": 300_000, "15m": 900_000, "30m": 1_800_000, "1h": 3_600_000, "4h": 14_400_000}

try:
    sys.stdout.reconfigure(encoding="utf-8")
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
    if r.returncode != 0 or os.path.getsize(path) < 1024:
        os.remove(path)
        sys.exit("❌ ดึงฐานข้อมูลไม่ได้ (ต้องเป็น debug build): " + r.stderr.decode(errors="replace"))
    return path


# ─── helpers ─────────────────────────────────────────────────────────────────

def ts(ms):
    u = dt.datetime.fromtimestamp(ms / 1000, UTC)
    return f"{u:%m-%d %H:%M} UTC ({(u + dt.timedelta(hours=7)):%H:%M} ไทย)"


def day_shift_ms(symbol):
    s = symbol.upper()
    fx = len(s) == 6 and s.isalpha() and not s.endswith("USDT")
    return 2 * 3_600_000 if ("XAU" in s or "XAG" in s or fx) else 0


def load_bars(c, symbol, interval):
    src = c.execute("select source from TvCandle where symbol=? and interval=? group by source order by count(*) desc limit 1",
                    (symbol, interval)).fetchone()
    if not src:
        return [], None
    rows = c.execute("select ts,open,high,low,close,volume from TvCandle where symbol=? and interval=? and source=? order by ts",
                     (symbol, interval, src[0])).fetchall()
    return rows, src[0]


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
    try:
        c = sqlite3.connect(path)
        print(f"ฐานข้อมูล: {path} | quick_check={c.execute('pragma quick_check').fetchone()[0]} | schema={c.execute('pragma user_version').fetchone()[0]}")

        print("\n══ Alert jobs ══")
        for r in c.execute("select id,name,symbol,tool_name,condition_json,interval_minutes,is_active,is_triggered,last_value,last_run_at from AlertJob"):
            print(f" #{r[0]} {r[1]} | {r[2]} | {r[3]} | {r[4]} | ทุก {r[5]} นาที | active={r[6]} triggered={r[7]} last_value={r[8]} | เช็กล่าสุด {ts(r[9]) if r[9] else '-'}")

        print("\n══ การตั้งค่า ══")
        for k in ["alert_voice", "alert_voice_engine", "alert_ai_summary", "model_name",
                  "wake.usage", "wake.budget.hourly", "wake.budget.daily", "wake.cooldown.bars", "wake.disabled"]:
            v = c.execute("select value from AppSetting where key=?", (k,)).fetchone()
            print(f" {k} = {v[0] if v else '(ไม่ได้ตั้ง → ค่าเริ่มต้น)'}")
        print(" (alert_voice_engine: device = Android TTS · live = Gemini Live · ถ้ากำลังคุย Live อยู่ session นั้นพูดแทน)")

        print(f"\n══ การปลุก {args.last} ครั้งล่าสุด ══")
        wakes = c.execute("""
            select signal_id, min(created_at), max(ai_decision), max(ai_bias),
                   group_concat(case when woke=1 then factor_id||'('||side||')' end, ', '),
                   group_concat(case when woke=0 then factor_id end, ', '),
                   max(ref_price), max(ref_atr), group_concat(distinct status)
            from AnticipationFactorOutcome group by signal_id
            having sum(woke) > 0 order by min(created_at) desc limit ?""", (args.last,)).fetchall()
        cards = c.execute("select timestamp, content, metadata from ChatMessage where metadata like '%\"wake_%' order by timestamp").fetchall()
        for sig, created, dec, bias, woke, states, ref, atr, status in reversed(wakes):
            sym, tf, bar = sig.split("|"); bar = int(bar)
            print(f"\n▶ {sig}  แท่ง {ts(bar)} | ตรวจพบ {ts(created)} | AI: {dec or '-'} {bias or ''} | ราคาอ้างอิง {ref} ATR {atr:.3f} | สถานะวัดผล {status}")
            print(f"   ปัจจัยที่ปลุก: {woke}")
            if states:
                print(f"   สภาวะใหม่ (บริบท): {states}")
            card = next((x for x in cards if 0 <= x[0] - created <= 15 * 60_000), None)
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
            recompute(c, sym, tf, bar, created)

        if args.logcat:
            print("\n══ logcat ล่าสุด ══")
            pid = subprocess.run([adb, "shell", "pidof", PKG], capture_output=True, text=True).stdout.strip()
            if not pid:
                print(" แอปไม่ได้ทำงานอยู่")
            else:
                out = subprocess.run([adb, "logcat", "-d", f"--pid={pid}"], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
                lines = [l for l in out.splitlines() if re.search(r"SmcApiService|AutomationService|WakeEngine|WakeLearning|OhlcvMaintenance", l)]
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
        c.close()
    finally:
        if not args.db and not args.keep:
            os.remove(path)
            print("\n(ลบสำเนาฐานข้อมูลแล้ว)")


if __name__ == "__main__":
    main()
