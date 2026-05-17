# Smart Fallback & Performance Ranking (V19.5)

ระบบจัดการความทนทาน (Resilience) และการเพิ่มประสิทธิภาพ (Self-Optimization) ของ AI Trading Engine เพื่อให้สามารถทำงานได้ 24/7 พร้อมผลลัพธ์ที่ดีที่สุด

## 1. Performance-Driven Ranking Engine (New)
เปลี่ยนจากระบบ Hardcoded List เป็นระบบจัดลำดับแบบ **Dynamic Ranking** ตามประสิทธิภาพการใช้งานจริง โดยบันทึกลงในตาราง `ai_model_stats`

### ตัวชี้วัดที่ใช้จัดอันดับ (Scoring Metrics)
- **Profit R (Weight: 10x)**: ความสามารถในการสร้างกำไร (R-units) จากสัญญาณที่โมเดลให้มา
- **Success Rate (Weight: 100x)**: ความเสถียรในการตอบกลับ (Non-error responses)
- **Latency Penalty**: บทลงโทษตามความช้าของโมเดล (ยิ่งช้ายิ่งคะแนนลด)
- **Failure Penalty (V19.3: 15x)**: บทลงโทษหนักเมื่อเกิดข้อผิดพลาดหรือ JSON Parse Error เพื่อให้ระบบคัดโมเดลที่ใช้งานไม่ได้ออกอย่างรวดเร็ว
- **Empty Response Tracking**: ระบบตรวจจับและลงโทษโมเดลที่ตอบกลับมาว่างเปล่า (Empty Response)

## 2. Smart Free Fallback (Auto-Rotation)
เมื่อเปิดใช้งาน `useSmartFree` ในแต่ละ Agent ระบบจะสลับไปใช้ **โมเดลฟรีที่มีอันดับสูงสุด (Top Ranked Free Model)** ณ ขณะนั้นโดยอัตโนมัติ

### ตรรกะการทำงาน (Routing Logic)
- **Automatic Selection**: คัดเลือกโมเดลที่ดีที่สุดจากฐานข้อมูล `ai_model_stats` ที่เป็นโมเดลฟรี
- **Exploration Factor**: ระบบจะสุ่มเลือกโมเดลฟรีใหม่ๆ (RANDOM) เข้ามาทดสอบเป็นระยะเพื่อหาตัวเลือกที่ดีกว่าเดิม
- **Session-based Circuit Breaker (V19.3)**: เมื่อโมเดลใดๆ เกิดข้อผิดพลาด (Execution/Parse Error) ระบบจะทำการ "กาหัว" (Blacklist) โมเดลนั้นไว้ในหน่วยความจำทันที และจะไม่นำมาใช้อีกจนกว่าจะวนใช้โมเดลฟรีที่มีทั้งหมดจนครบ
- **Pre-AI Token Saving Gates (New V19.4)**: ระบบ "ด่านตรวจ" ที่จะตัดการเรียก AI ทันทีหากพอร์ตเต็ม (Per-symbol Cap) หรือราคาอยู่ในโซนที่ไม่ควรเทรด (Zone-Aware Gate) ช่วยประหยัดค่าใช้จ่ายและเวลา
- **2-Tier LLM Caching (New V19.4)**: ระบบแคช 2 ระดับ แบ่งเป็น **Strict Cache** (ต้องเหมือนกันทุกอย่าง) และ **Loose Cache** (เหมือนกันแค่ภาพใหญ่ HTF) เพื่อลดการถาม AI ซ้ำซ้อน

## V19.5 AI Model Leaderboard & MTF Dash (2026-04-30)

### 1. Model Leaderboard (Real-time Ranking)
- **Visualization**: ระบบแสดงตารางอันดับโมเดล (Leaderboard) บนหน้า Dashboard โดยแบ่งฝั่งซ้าย-ขวาคู่กับ Market Symbols
- **Transparency**: แสดงคะแนน (Score), อัตราความสำเร็จ (Success Rate), ความเร็ว (Latency) และกำไรรวม (Profit R) ของแต่ละโมเดลอย่างชัดเจน
- **Rank Badges**: ระบบแจกเหรียญรางวัล (Gold, Silver, Bronze) ให้กับ Top 3 โมเดลที่มีคะแนนสูงสุดในระบบ

### 2. Refined Ranking Scorer (V19.5)
- **Min Calls to Rank**: ป้องกันโมเดลใหม่ที่ยังไม่มีสถิติ (Cold Start) ขึ้นมาครองอันดับ 1 โดยการบังคับให้ต้องมีประวัติการใช้งานอย่างน้อย **5 ครั้ง** ก่อนจะถูกนำมาจัดอันดับ
- **Incapable Pattern Filtering**: ระบบกรองโมเดลที่ไม่สามารถวิเคราะห์เหตุผลได้ (เช่น OCR, Vision-only, Audio, Embedding) ออกจากลิสต์การจัดอันดับอัตโนมัติ เพื่อป้องกันการสลับไปใช้โมเดลที่ไม่เหมาะสม
- **ATR-Aware Risk Officer**: ปรับปรุงด่านตรวจความเสี่ยง (Risk Officer) ให้รองรับกลยุทธ์ Scalping/Range โดยอนุญาตให้ใช้ RRR >= 1.0 และไม่มีการ Hard-coded ระยะ SL/TP ทำให้การเข้าเทรดมีความยืดหยุ่นสูงขึ้น

### 3. SL/TP Sanity Guard & Model Penalty (V19.6 — 2026-05-01)
- **Distance-vs-Price Detection**: ระบบตรวจจับเมื่อ AI ส่ง SL/TP เป็น "ระยะห่าง" (เช่น 0.17) แทน "ราคาจริง" (เช่น 4617.83) โดยเช็คว่าค่า SL/TP น้อยกว่า 0.1% หรือมากกว่า 200% ของราคา Entry → ปฏิเสธและ fallback ไป ATR-based
- **Sanity Guard V5:** ระบบจะตรวจสอบทั้งค่าที่ "น้อยเกินไป" (< 0.1% ของราคาเข้า) เพื่อกรองการหลอนระยะห่าง และ "มากเกินไป" (> 200%) เพื่อป้องกันค่าหลุดโลก หากพบความผิดปกติ AI Model จะถูกเรียกใช้ `recordContentFailure` ทันที และระบบจะสลับไปใช้ค่าจาก ATR (Fallback) แทน 
- **Auto-Repair System:** หากมีออเดอร์ค้างใน MT5 ที่มีปัญหา `10016` (SL/TP invalid) Trade Manager จะทำการ Audit เจอและส่งคำสั่ง Modify ยัดค่า ATR ที่คำนวณจากราคาปัจจุบันเข้าไปรักษาออเดอร์ให้กลับมามีชีวิตอีกครั้งทันที
- **Database Synchronization Fix:** ฟังก์ชัน `cleanModelId` จะหั่นคำนำหน้า Provider (เช่น `openrouter `) ออกก่อนทำการอัปเดต SQL เสมอ ทำให้การบันทึกคะแนน Trade Outcome และ Penalty ลง Database แม่นยำ 100% ไม่มีปัญหาหา Model ID ไม่เจออีกต่อไป
- **Auto-Persistence on Swap:** เมื่อระบบ Smart Free Ranker ตัดสินใจสลับโมเดล (Swap) เนื่องจากโมเดลเก่าติด Blacklist หรือคะแนนตก ระบบจะทำการแก้ไขค่า Settings (`AutoTradingConfig`) ทันที (Dynamically Update) เพื่อให้หน้าต่าง Settings บน Dashboard อัปเดตชื่อโมเดลปัจจุบันที่ AI กำลังใช้งานอยู่แบบ Real-time และจำค่านั้นไว้ใช้ในรอบถัดไป

### 1. Per-Symbol Capacity Architecture
- **Isolation**: The engine now tracks capacity per symbol (e.g., XAUUSD, BTCUSD) independently.
- **Quota**: Default is **5 positions per symbol**.
- **Defense Multiplier**: Automated recovery actions (Hedge/Scale-in) are granted an **x2 ceiling** (up to 10 positions per symbol) to ensure liquidity for account protection.
- **Benefits**: Prevents a saturated Gold portfolio from blocking high-quality Bitcoin entries.

### 2. Sequential Offset Logic (Cluster Cleaning)
- **Strategy**: Instead of closing just the latest trades, the engine searches the entire cluster for the **Worst Loser** and the **Best Non-Golden Winner**.
- **Execution**: If the Net PnL of the pair is >= 0, both are closed instantly.
- **Goal**: Reduces portfolio heat and frees up margin without waiting for a total cluster break-even.

### 3. 3-Tier Recovery Time Decay
- **Tier 1 (6h)**: Exit cluster if Net >= -0.5R (Near BE).
- **Tier 2 (12h)**: Exit cluster if Net >= -1.0R (Wider BE).
- **Tier 3 (24h)**: **Force Close** the cluster regardless of PnL to prevent "Zombie Positions".

- **Hotfix (2026-04-30)**: แก้ไข `ReferenceError` ใน `TradeManagementService.ts` เรื่องการเรียกใช้ลำดับตัวแปรคำนวณความร้อนพอร์ตก่อนการประกาศค่า
- **Model Discovery**: ระบบจะสแกนหาโมเดลฟรีใหม่ๆ จาก OpenRouter อัตโนมัติทุกรอบการเรียนรู้ (Learn Cycle)

## 3. Outcome Attribution & Feedback Loop
ระบบเชื่อมโยงข้อมูลแบบ End-to-End เพื่อให้ AI เรียนรู้จากผลลัพธ์การเทรด:
1. **Decision**: บันทึกว่าออเดอร์นั้นๆ ถูกวิเคราะห์โดยโมเดล ID ใดลงใน `auto_trading_journal`
2. **Execution**: บันทึกความเร็วและความสำเร็จของการเรียก API
3. **Closing**: เมื่อออเดอร์ปิด (WIN/LOSS) ระบบจะส่งค่า P&L กลับมาอัปเดตคะแนนให้โมเดลนั้นๆ ทันที

## 4. Token Runaway Guard
- **Max Tokens Cap**: กำหนดค่า `max_tokens` เริ่มต้นที่ **4,096 tokens** เพื่อป้องกันการ Loop ของโมเดลบางประเภท
- **Timeout Protection**: กำหนด Timeout แยกตามบทบาทของ Agent เพื่อไม่ให้ระบบค้างนานเกินไป

## 5. V20.5 Token Pruning & MiniMax Integration (2026-05-04)

### 1. Token Usage Optimization (Pruning Gate)
ระบบประหยัดค่าใช้จ่ายและป้องกัน Context Overflow โดยการควบคุมปริมาณข้อมูลที่ส่งให้ LLM:
- **Conversation History Truncation**: จำกัดประวัติการสนทนาย้อนหลังไว้ที่ **20 turns** ล่าสุดเท่านั้น (ช่วยลด Input Tokens ได้มหาศาล)
- **Core Memory Context Capping**: จำกัดขนาดของความจำระยะยาว (Core Context) ที่ดึงมาจาก GraphRAG/SQLite ไว้ที่ **15,000 characters** โดยจะเลือกเฉพาะส่วนที่สำคัญที่สุด
- **Tool Result Truncation**: ผลลัพธ์จากเครื่องมือ (Tool Results) ที่มีความยาวเกิน **8,000 characters** จะถูกตัดท้ายทิ้งและใส่คำเตือนไว้ เพื่อป้องกันโมเดล "หลอน" จากข้อมูลที่มากเกินไป
- **Anthropic Prompt Caching**: สำหรับโมเดลตระกูล Claude ระบบจะส่ง Header `anthropic-beta: prompt-caching-2024-07-31` เพื่อใช้งาน cache สำหรับ System Prompt และ Tool Definitions ทำให้ประหยัดค่าใช้จ่ายลง 90% ในการเรียกซ้ำ

### 2. MiniMax Provider Expansion
เพิ่มการรองรับ **MiniMax (abab6.5s)** เข้ามาเป็นหนึ่งใน Primary Provider:
- **abab6.5s Integration**: โมเดลตัวท็อปที่มีความสามารถด้าน Logic และ Function Calling ใกล้เคียง GPT-4o ในราคาที่คุ้มค่า
- **Mobile & Server Support**: รองรับทั้งการเรียกใช้งานตรงจากแอปมือถือ (Kotlin Native) และผ่านระบบ Orchestration ของ Server (Node.js)
- **Registry Support**: ลงทะเบียนใน `LlmProviderRegistry` พร้อมระบบตรวจสอบ API Key และดึงรายการ Model อัตโนมัติ

## 6. การบันทึกค่า (Persistence)
- **Smart Free Toggle**: บันทึกสถานะการเปิด/ปิด Smart Free แยกตามบทบาท (Analyst, Risk Officer, Execution)
- **DB Statistics**: บันทึกสถิติรายโมเดลอย่างละเอียด รวมถึงค่า Latency เฉลี่ยและจำนวน Token ที่ใช้

## 7. V20.6 Model Lifecycle Management (2026-05-05)

### 1. Aggressive Dead Model Purge
ปัญหาเดิม: Model ที่ถูกถอดจาก OpenRouter (เช่น `deepseek/deepseek-chat-v3.1:free`) ยังอยู่ใน DB เพราะมี `total_calls > 0` → Smart Ranker เลือกมาซ้ำๆ → HTTP 404 ทุกครั้ง

**แก้ไข: `syncDiscoveredModels()` v2**
- **(a) total_calls=0 + เก่า >1h** → ลบจริง (เหมือนเดิม)
- **(b) total_calls>0 แต่ไม่อยู่ใน free list** → **blacklist 30 days + consecutive_failures=99**
  → Smart Ranker จะข้ามโมเดลนี้ไปทันที

### 2. HTTP 404 Instant Permanent Ban
เมื่อ `invoke()` ได้ HTTP 404 ("No endpoints found"):
- ระบบจะ **record 10 content failures ติดกัน** → trigger 30-day ban (permanent)
- Log: `[Dispatcher] 🪦 Model "xxx" returned 404 — permanently banned`
- ป้องกันไม่ให้ fallback chain retry model ที่ตายแล้ว

### 3. Sync ทุก 6 ชั่วโมง (ลดจาก 24h)
- OpenRouter เปลี่ยน free list บ่อยมาก — log พบ 2+ models ถูกถอดภายใน session เดียว
- `scheduleDailyModelSync()` → ลดรอบ **24h → 6h**
- Boot sync ยังทำงานตอน startup เหมือนเดิม

### 4. Seed List Cleanup
ลบ dead models ออกจาก `KNOWN_FREE_OPENROUTER`:
- ❌ `deepseek/deepseek-chat-v3.1:free` (HTTP 404)
- ❌ `openrouter/sonoma-sky-alpha` (HTTP 404)
- ❌ `inclusionai/ling-2.6-1t:free` (persistent rate-limit)
- ✅ เพิ่ม `google/gemma-4-26b-a4b-it:free` (proven reliable)
- ✅ เพิ่ม `openrouter/owl-alpha` (primary model)

