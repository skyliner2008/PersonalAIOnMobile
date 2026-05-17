# V25.0 — Trade Flow Diagrams

> **Companion to:** [[25_RealTimeWallEngine_V25]]
> **Purpose:** เห็นภาพ flow ในใจเดียว ก่อนลงมือ implement

---

## 1) Master Loop — Tick + Bar-Close Hybrid

```
                         ┌──── MT5 Bridge ────┐
                         │  WS tick stream    │
                         │  REST candles      │
                         └─────────┬──────────┘
                                   │
                  ┌────────────────┴────────────────┐
                  │                                 │
            tick (200ms)                      bar close
                  │                                 │
        ┌─────────▼─────────┐               ┌───────▼────────┐
        │  TickBuffer (L1)  │               │ BarCloseEventBus│
        │  M1 micro-wall    │               │  per-TF emit    │
        └─────────┬─────────┘               └───────┬─────────┘
                  │                                 │
                  │ price update                    │ TF-specific calc
                  │                                 │
                  │      ┌──────────────────────────┘
                  │      │
                  ▼      ▼
        ┌─────────────────────────┐
        │ WallProximityIndex (L4) │  ← rebuild on bar-close
        │ binary-search nearest   │  ← lookup per tick
        └────────────┬────────────┘
                     │
                     ▼
        ┌─────────────────────────┐
        │ Wall State Machine (L4) │  IDLE / APPROACH / REACT / BREAK / RETEST / COMMITTED
        └────────────┬────────────┘
                     │ REACT only
                     ▼
        ┌─────────────────────────┐
        │ Playbook Selector (L5)  │  PB1..PB5
        └────────────┬────────────┘
                     │ trade plan
                     ▼
        ┌─────────────────────────┐
        │ Hard Risk Gate (existing)│
        └────────────┬────────────┘
                     │
                     ▼
        ┌─────────────────────────┐
        │ MT5 OrderSend           │
        └─────────────────────────┘
```

---

## 2) Wall State Machine — ASCII

```
        ┌─────────────────┐
        │      IDLE       │  ◄── default; ราคาห่าง wall > 1.5×ATR
        │  (no monitor)   │      LLM ปิด, calc ปิด
        └────────┬────────┘
                 │ price drift toward wall
                 │ |Δ| ≤ 1.5 × ATR(M15)
                 ▼
        ┌─────────────────┐
        │    APPROACH     │  ◄── start watching
        │  +tick subscribe│      M1 calc, no LLM
        │  +M1 wall track │
        └────────┬────────┘
                 │ |Δ| ≤ 0.5 × ATR(M15)
                 │ AND velocity drops 30%
                 │ (decel near wall)
                 ▼
        ┌─────────────────┐
        │     REACT       │  ◄── Playbook Selector triggered
        │  *** EVENT ***  │      LLM allowed (slTpAgent)
        └──┬───────────┬──┘
           │           │
   M1 reverses    M1 closes through wall
   pattern         body ≥ 60%
           │           │
           ▼           ▼
    ┌──────────┐ ┌──────────────┐
    │ CONFIRM  │ │  BREAK_OUT   │
    │ (entry)  │ │ (no entry)   │
    └────┬─────┘ └──────┬───────┘
         │              │ pull-back ≤ wall ± 0.3×ATR
         │              ▼
         │       ┌──────────────┐
         │       │   RETEST     │  ◄── PB5 candidate
         │       │  (entry on   │
         │       │   continuation)│
         │       └──────┬───────┘
         │              │
         ▼              ▼
    ┌──────────────────────┐
    │     COMMITTED        │  ◄── lock state until exit
    │  (manage by L6)      │
    └──────────────────────┘
```

---

## 3) Playbook Decision Tree

```
                    REACT state entered
                            │
                            ▼
                ┌───────────────────────┐
                │ ราคา wicked ผ่าน wall  │
                │ และปิดกลับ?           │── yes ─► PB3 SWEEP_REVERSAL
                └──────────┬────────────┘            (RRR target 2–5)
                           │ no
                           ▼
                ┌───────────────────────┐
                │ wall มี OB+FVG fresh? │── yes ─► PB2 FVG_FILL
                └──────────┬────────────┘            (RRR 2–4)
                           │ no
                           ▼
                ┌───────────────────────┐
                │ มาจาก BREAK→RETEST?   │── yes ─► PB5 BREAKOUT_RETEST
                └──────────┬────────────┘            (RRR 2–3)
                           │ no
                           ▼
                ┌───────────────────────┐
                │ regime = RANGING?     │── yes ─► PB4 RANGE_FADE
                └──────────┬────────────┘            (RRR 1.5–2.5)
                           │ no
                           ▼
                ┌───────────────────────┐
                │ wall ≥ 2★ + M1 reverse│── yes ─► PB1 WALL_TOUCH ⭐
                └──────────┬────────────┘            (default; RRR 1.5–3)
                           │ no
                           ▼
                       SKIP (back to APPROACH)
```

---

## 4) PB1 Wall-Touch Scalp — sequence diagram (BUY)

```
Time   Event                                State
─────  ───────────────────────────────────  ─────────
t0     price 80386.74 (mid-channel)         IDLE
t1     price 80386 → 80371 (drift)          IDLE
t2     |80371 − 80361.87| = 9.13            APPROACH ◄─┐
       (0.5×ATR(M15)=15.0, ≤ 1.5×ATR=45)              │
                                                       │ subscribe M1 calc
t3     velocity drops to 2 pip/sec          REACT  ◄───┘
       (was 6 pip/sec)
       *** PlaybookSelector fires ***
       PB1 selected: 2★+ wall, M15 in TFs
t4     M1 closes: bullish engulfing         CONFIRM
       FVG bear opposite still fresh? YES
       compute:
         entry = 80361.88   (wall + 1tick)
         SL    = swingLow_M5_5 − 1×ATR(M1)
               = 80335 (e.g.)
         TP    = 80483.36 − 1tick − spread
               = 80477   (≈ 115 pip)
         RRR   = 115/27 = 4.26 ✅
t5     OrderSend BUY 80361.88 → COMMITTED   COMMITTED
       (Trade Manager takes over)
```

---

## 5) FVG-Fresh Tracker State (ตอบข้อ 5 ของ user)

```
M15 close detects FVG bull at 80100-80115
─────────────────────────────────────────►
fvgList.bull += { top: 80115, bot: 80100, ts: t0 }

t1: price = 80140  (above)               fresh ✓
t2: price = 80108  (entered zone)        partial fill — still fresh ✓
                                          (until close < bottom)
t3: M1 close = 80098  (below bottom)     filled — drop ✗
                                          fvgList.bull -= this fvg

REACT state for SELL queries:
  hasFreshOpposite('SELL') → bullFvg present?
  used as PB2/PB1 confirmation gate
```

---

## 6) BAR_CLOSE Event Bus (ตอบ R4 — calc per TF only when bar closes)

```
Time (UTC)        Event                     Calculators triggered
──────────        ─────                     ─────────────────────
14:00:00          M1 close                  RSI(M1), microFVG, microOB
14:01:00          M1 close                  ↑ same
14:01:00          (ยัง ไม่ใช่ M5 close)
14:02:00          M1 close
14:03:00          M1 close
14:04:00          M1 close
14:05:00          M1 close + M5 close       M1: RSI; M5: full SMC + ATR
14:05:00          (ยัง ไม่ใช่ M15 close)
14:10:00          M1 + M5 close             M1, M5
14:15:00          M1 + M5 + M15 close       *** PriceMap rebuild ***
                                            M1, M5, M15 calc all
14:30:00          M1 + M5 + M15 + M30
14:45:00          M1 + M5 + M15
15:00:00          M1 + M5 + M15 + M30 + H1  *** Heaviest tick — bias refresh
```

CPU footprint:
- ทุก tick: O(log n) wall lookup + O(1) state check
- ทุก M1 close: ~5ms
- ทุก M5/M15 close: ~50ms
- ทุก H1 close: ~150ms
- เปรียบเทียบ V24 (รันทุก 60s ทุก TF) = ~200ms × 60 cycles/h = 12s/h CPU
- V25 ≈ 60×5ms + 12×50ms + 4×150ms = 1.5s/h CPU = **−87%**

---

## 7) State Diagram — รวมทั้ง symbol

```
                ┌─ XBTUSD ──┐    ┌─ XAUUSD ──┐
                │ wallAbove │    │ wallAbove │
                │ wallBelow │    │ wallBelow │
                └────┬──────┘    └────┬──────┘
                     │                 │
                     │   tick stream   │
                     ▼                 ▼
              ┌──────────────────────────┐
              │ symbol-isolated worker    │
              │ each runs own state mach │
              └──────────────────────────┘
                     │
                     ▼
              ┌──────────────────────────┐
              │ shared Risk Gate          │  ← max positions, daily loss cap
              │ shared Order Sender       │
              └──────────────────────────┘
```

---

## 8) Decision Boundary Comparison (V24 vs V25)

```
PRICE LADDER เดียวกันจาก log:

  80483.36 ★★★★★  ← target SELL TP / next resistance
  80407.20 ★★★★
  80358.47 ★★★★
  80300.52 ★★★
  80267.66 ★★
  80199.85 ★★
  80159.17 ★★
─────── PRICE ───────
  80113.37 ★★★★★  ← target BUY entry (wall hit) / SELL near "no man" zone

V24 behavior:
  state: AT 80175 (mid-channel)
  proximity: "near 5★ resistance @ 80483 (337 pip)"  ← ผิด — เกิน 100pip
  action: SELL
  result: SL 80551 (next 5★), TP capped 80119 → RRR 0.16 → SKIP

V25 behavior:
  state: at 80175 → wallAbove nearest = 80199.85 (★★, 24 pip) → APPROACH
                  → wallBelow nearest = 80113.37 (★★★★★, 62 pip) → IDLE
  action: ไม่เข้าเทรด — รอราคาแตะ wall จริง
  next: ถ้าราคาขึ้นไปแตะ 80199.85, REACT, PB1
        SELL entry 80198, SL 80212 (swingHigh+ATR), TP 80114 → RRR 6.0 ✅
        ถ้าราคาลงไปแตะ 80113.37, REACT, PB1
        BUY entry 80114, SL 80100 (swingLow−ATR), TP 80198 → RRR 6.0 ✅
```

---

## 9) Failure Modes & Guards

| Failure | Detection | Guard |
|---------|-----------|-------|
| Tick stream stall (latency > 5s) | TickBuffer.lastTs < now − 5s | freeze state machine, fall back to V24 cycle |
| Whipsaw (REACT → BREAK → REACT 3× ใน 5 min) | event counter | force IDLE 10min |
| Wall list stale (PriceMap > 30min ไม่ update) | timestamp | force rebuild on next M5 close |
| LLM timeout in REACT | 10s timeout | use deterministic SL/TP from PB rules ทันที |
| Multiple positions same wall | symbol+wallPrice key | `oneTradePerWall` guard 30min |

---

**Last updated:** 2026-05-10 with V25 design
