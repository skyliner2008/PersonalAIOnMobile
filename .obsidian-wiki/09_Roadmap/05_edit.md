****สรุปการแก้รอบนี้ (รวม 6 จุด, 3 ไฟล์)
Fix 1: Pre-AI Position Cap Gate (~บรรทัด 692-707)
ถ้า atHardCap=true AND management ไม่ใช่ HEDGE/CLOSE/REDUCE → SKIP เลยโดยไม่เรียก LLM

Log ใหม่จะเห็น: Pre-AI cap gate → SKIP LLM (cap 7/5, mgmt=BREAK_EVEN)
ประหยัด ~14k tokens/symbol/cycle ในกรณีที่ position cap เต็ม

Fix 2: Pre-AI Zone-Aware Gate (~บรรทัด 711-743)
ใช้ detDecision.action เช็ค zone ก่อนเรียก LLM แทนเดิมที่เช็คหลัง Execution Trader

รองรับ breakout exception เหมือนเดิม
Log ใหม่: Pre-AI zone gate → SKIP LLM (SELL in DISCOUNT, 35.1% of range)

Fix 3: 2-Tier LLM Cache (Strict + Loose, ~บรรทัด 745-769)

Strict (เดิม): full state hash — match price+H4+H1+M15+M5+pos
Loose (ใหม่): HTF-only — match symbol+priceBucket+H4+H1+side+strategy
Strict ตรวจก่อน Loose เพื่อรักษา freshness
Log: LLM cache HIT (strict) หรือ LLM cache HIT (loose HTF)

Fix 4: Adaptive skipLlm Threshold (~บรรทัด 774-794)

ปกติ: ต้องมี deterministic confidence ≥ 70 ถึงจะข้าม LLM
เมื่อ atHardCap && !isDefensiveMgmt: ลด threshold เหลือ 50 (เพราะอย่างไรก็ trade ไม่ได้)

Fix 5: Multi-Agent Cache Set (~บรรทัด 992-1010)
ปัญหาเดิม: llmCache.set อยู่แค่ใน else branch (Single-Agent) ทำให้โหมด Multi-Agent ไม่เคยเซ็ตแคช → cache miss 100%

ย้ายออกมาเป็น block ร่วม
เซ็ตทั้ง strict + loose key พร้อมกัน
ข้าม cache สำหรับ SKIP, สำหรับ source ที่ไม่ใช่ LLM (deterministic, gates) เพื่อกัน loop

Fix 6: New helper marketStateHashLoose ใน deterministicEngine.ts

HTF-only key: LOOSE|XAUUSD@4546|H4:BEAR:70:VOLATILE_BREAKOUT|H1:BEAR:60|side=SELL|strat=TREND_FOLLOW
ทนต่อการเปลี่ยนของ M15/M5/pos count ที่ไม่กระทบ HTF picture


ผลที่คาดหวัง
| สถานการณ์ | ก่อน | หลัง |
| --- | --- | --- |
| Cap เต็ม + neutral mgmt | ~14k tok | 0 tok (Pre-AI cap gate) |
| Zone Gate จะ block อยู่แล้ว | ~14-17k tok | 0 tok (Pre-AI zone gate) |
| HTF เหมือน 2 cycles ก่อน | ~14k tok | 0 tok (Loose cache hit) |
| Det confidence 60% + cap เต็ม | ~14k tok | 0 tok (relaxed threshold 50%) |
| Multi-agent cycle ปกติ | cache miss ตลอด | cache hit รอบ 2-3 |

สถานการณ์ใน log จริง (cap 7/5 ทั้ง 4 cycles):

ก่อน: 4 cycles × 2 symbols × ~14k tok = ~112k tokens
หลัง: ทั้งหมดผ่าน Pre-AI cap gate → ~0 tokens (LLM ถูกข้ามทั้งหมด)
ประหยัด ~99% ในกรณี cap เต็มต่อเนื่อง

สถานการณ์ปกติ (มีพื้นที่เปิด trade):

Cycle แรกใช้ AI ปกติ → cache เซ็ต
Cycle 2-3 ที่ HTF ไม่เปลี่ยน → loose cache hit → ไม่เรียก AI
คาดประหยัด ~50-70% ของโทเคนเดิม

==============================================================

สรุปการแก้รอบนี้ (4 ไฟล์)
Logic ใหม่ที่ใช้ค่าจาก Mobile App
ค่าที่ผู้ใช้ตั้งใน app:
maxOpenPositions = 5  ← ค่าเดียวที่ตั้งใน mobile app
ระบบตีความใหม่ (ไม่มี global cap แล้ว):
per-symbol cap (ปกติ)     = 5  positions/symbol
defense cap (แก้ไม้, hedge) = 10 positions/symbol  (2× ของ user setting)
รวมทั้งบัญชี              = ไม่จำกัด (ขึ้นกับจำนวน symbols ใน watchlist × 5 หรือ 10)
ไฟล์ที่แก้
1. auto/risk.ts — ลบ global cap ออกหมด, เหลือแค่ per-symbol gate
   `const perSymbolCap = maxPositionsPerSymbol[symbol] ?? defaultMaxPositionsPerSymbol ?? maxOpenPositions ?? 5;`
   `const effectiveCap = overLimitDefense ? perSymbolCap * 2 : perSymbolCap;`
   `if (symbolHeld >= effectiveCap) return { allowed: false, reason: ... };`
2. autoTradingService.ts — atHardCap ตรวจ per-symbol แทน totalOpen
   `const perSymbolCap = ... ?? cfg.risk.maxOpenPositions ?? 5; // = 5 จาก mobile app`
   `const defenseSymbolCap = perSymbolCap * 2;`
   `const atHardCap = symbolPositions.length >= perSymbolCap;`
3. Account log line — ตัด "(total 7/5)" ที่กำลังจะหลอกตา → แสดงแค่ open count
   ก่อน: Positions: XAUUSD 4/5, XBTUSD 3/5 (total 7/5)
   หลัง: Positions: XAUUSD 4/5, XBTUSD 3/5 | open=7
4. TradeManagementService.ts — maxSameSymbolPositions fallback เป็น maxOpenPositions, hardCapPerSymbolMultiplier เปลี่ยน 1.5 → 2.0
   `const maxSameSymbolPositions = adaptive.maxSameSymbolPositions ?? ... ?? cfg.risk.maxOpenPositions ?? 5;`
   `const hardCapSymbol = Math.floor(maxSameSymbolPositions * 2.0);`

Pre-AI gate ใหม่ที่จะเห็นใน log
แทนที่จะแสดง "Trade BLOCKED by Risk Gate (global cap 5 reached (7 total))" ที่ผิดความหมาย จะเห็น:
XAUUSD: Pre-AI cap gate → SKIP LLM (per-symbol 5/5, mgmt=BREAK_EVEN)   ← เต็ม cap, ไม่ใช่ defense
XAUUSD: Pre-AI defense-cap gate → SKIP LLM (10/10)                      ← ถึงเพดาน 2x แล้ว
ทั้งสอง gate ตัดก่อน LLM call → ประหยัดโทเคน 100% เมื่อ symbol ตัวนั้นเต็ม

ผลลัพธ์รวมจากสถานการณ์ปัจจุบัน (XAUUSD 4/5, XBTUSD 3/5)
| Symbol | สถานะ | ผลก่อน | ผลหลัง |
| --- | --- | --- | --- |
| XAUUSD | 4/5 (มีพื้นที่ 1) | global block 7/5 → SKIP | เปิดได้อีก 1 ก่อนถึง cap |
| XBTUSD | 3/5 (มีพื้นที่ 2) | global block 7/5 → SKIP | เปิดได้อีก 2 ก่อนถึง cap |
| ทั้ง 2 ในโหมด hedge | block ที่ 7/5 | ขยายได้ถึง 10 ต่อ symbol |

ระบบจะ ไม่ติดกับดัก "7/5" อีกต่อไป — เมื่อรีสตาร์ท XAUUSD/XBTUSD จะเปิด trade ใหม่ได้ตาม signal ที่ deterministic + AI เห็นว่าดี ตราบเท่าที่ยังไม่เกิน cap ของ symbol ตัวนั้นเอง

==============================================================
สรุปการแก้รอบ V19.4 Final Stability (2026-04-30)
1. Risk Officer Prompt Alignment: แก้ไขข้อความใน AI Prompt ให้ระบุชัดเจนว่าเป็น "Max Positions PER SYMBOL" เพื่อป้องกัน AI สั่ง Veto ผิดพลาดเมื่อพอร์ตรวมมีหลายคู่เงิน
2. Sequential Offset Implementation: เพิ่ม Logic การค้นหาคู่ "Worst Loser" และ "Best Non-Golden Winner" มาหักล้างกัน เพื่อลด Risk Exposure ของพอร์ตในจังหวะวิกฤต
3. 3-Tier Recovery Time Decay: เพิ่มระบบสลาย Cluster ตามอายุ 6h/12h/24h เพื่อป้องกันทุนค้างคาในไม้ที่ไม่มีอนาคต
4. TradeManagementService Reference Fix: ย้ายการประกาศตัวแปร `totalHeatR` และ `worst` ไปไว้ด้านบนสุดของฟังก์ชันเพื่อป้องกัน `ReferenceError` เมื่อเกิด Priority 0 Stop
5. Types Hardening: เพิ่มคำนิยามใน `types.ts` ให้รองรับพารามิเตอร์ใหม่ๆ (clusterMaxHeatR, tierAgeMs) และระบบปิดหลาย Ticket พร้อมกัน (tickets array)
6. Multi-Ticket Logging: ปรับปรุง `executeManagementPlan` ให้วนลูปปิดออเดอร์ได้ครบทุกใบตามแผน Sequential Offset และบันทึกลง Journal อย่างถูกต้อง
7. Log Observability Polish: ปรับปรุงการแสดงผล SL/TP ให้โชว์ค่าเป้าหมายแม้ในสถานะ SKIP และอัปเดตบรรทัดสรุปผล (Final Gate) ให้ระบุเหตุผลการข้ามที่แท้จริง (เช่น Cap Gate, Zone Gate) เพื่อความชัดเจนในการมอนิเตอร์