---
name: SMC Tools Implementation
description: เพิ่ม Smart Money Concepts tools จาก TradingView indicator เข้า PersonalAIBot
type: project
---

แปลง indicator "SMC & Multi-TF Order Blocks Sweeps V8.3" (Pine Script v6) เป็น Kotlin

**ไฟล์ใหม่:**
- `SmcApiService.kt` — ดึง OHLCV จาก Binance + คำนวณ SMC (swings, OB, FVG, liquidity, structure)
- `SmcToolDefinitions.kt` — 5 tool definitions สำหรับ Gemini
- `SmcToolExecutor.kt` — format output สวยงามสำหรับ Gemini

**5 SMC Tools:**
1. `trading_smc_analysis` — Full SMC dashboard
2. `trading_smc_sweeps` — MTF sweep detection (M1-H4)
3. `trading_smc_liquidity` — Equal H/L + Confluence Stars
4. `trading_smc_orderblocks` — OBs with FVG confirmation
5. `trading_smc_structure` — BOS/CHoCH + Premium/Discount

**Why:** ผู้ใช้มี TradingView indicator SMC และต้องการใช้ใน PersonalAIBot
**How to apply:** Data source = Binance klines API (crypto USDT pairs เท่านั้น)
