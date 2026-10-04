"""สร้างไฟล์ฐานข้อมูลแท่งเทียน (ตาราง TvCandle แบบเดียวกับ jarvis.db) จากแท่ง Binance สาธารณะ

ใช้เล่นซ้ำปัจจัยปลุก AI ด้วยข้อมูลตลาดจริง **โดยไม่ต้องดึงฐานข้อมูลจากมือถือ** (ซึ่งมีแชท ความจำ และ API key):

  python tools/binance_bars_db.py composeApp/build/wake_replay_bars.db BTCUSDT
  python tools/wake_factor_replay.py BTCUSDT --bars 200 --db composeApp/build/wake_replay_bars.db

ได้แท่งที่ปิดแล้ว 1m 4000 · 5m 1500 · 15m 1100 · 1h 1000 · 4h 1000 (พอเล่นซ้ำ ~200 แท่ง M15 = 50 ชม.)
ใช้ได้เฉพาะคู่ที่ Binance มี (คริปโต) — ทอง/FX ต้องใช้แท่งจากมือถือ (OANDA ผ่าน TradingView)
"""
import json
import sqlite3
import sys
import time
import urllib.request

WANT = {"1m": 4000, "5m": 1500, "15m": 1100, "1h": 1000, "4h": 1000}
TF_MS = {"1m": 60_000, "5m": 300_000, "15m": 900_000, "1h": 3_600_000, "4h": 14_400_000}


def klines(symbol, tf, n):
    rows, end = [], None
    while len(rows) < n:
        url = f"https://api.binance.com/api/v3/klines?symbol={symbol}&interval={tf}&limit=1000" + (f"&endTime={end}" if end else "")
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=20) as r:
            batch = json.loads(r.read())
        if not batch:
            break
        rows = batch + rows
        end = batch[0][0] - 1
        time.sleep(0.2)
    now = int(time.time() * 1000)
    return [x for x in rows[-n:] if x[0] + TF_MS[tf] <= now]   # แท่งที่ปิดแล้วเท่านั้น


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    out, symbol = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "BTCUSDT").upper()
    c = sqlite3.connect(out)
    c.execute("drop table if exists TvCandle")
    c.execute("create table TvCandle (symbol text, interval text, source text, ts integer, "
              "open real, high real, low real, close real, volume real)")
    for tf, n in WANT.items():
        k = klines(symbol, tf, n)
        c.executemany("insert into TvCandle values (?,?,?,?,?,?,?,?,?)",
                      [(symbol, tf, "BINANCE", int(x[0]), float(x[1]), float(x[2]), float(x[3]), float(x[4]), float(x[5])) for x in k])
        print(f"{tf:>4}: {len(k)} แท่ง  {time.strftime('%m-%d %H:%M', time.gmtime(k[0][0] / 1000))} → "
              f"{time.strftime('%m-%d %H:%M', time.gmtime(k[-1][0] / 1000))} UTC")
    c.commit()
    c.close()


if __name__ == "__main__":
    main()
