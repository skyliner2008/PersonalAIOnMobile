package com.example.personalaibot.tools.trading

import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.FunctionParameters
import com.example.personalaibot.tools.ParameterProperty

/**
 * SmcToolDefinitions — SMC (Smart Money Concepts) Tool Definitions สำหรับ Gemini Function Calling
 *
 * แปลงจาก indicator "SMC & Multi-TF Order Blocks Sweeps V8.3" (Pine Script v6)
 * เป็น Kotlin tools สำหรับ PersonalAIBot
 *
 * Tools ที่รองรับ:
 *  1. trading_smc_analysis    — Full SMC dashboard (OBs + Structure + FVG + Liquidity)
 *  2. trading_smc_sweeps      — Multi-Timeframe Sweep detection (M1→H4)
 *  3. trading_smc_liquidity   — MTF Liquidity zones (Equal H/L + Stars)
 *  4. trading_smc_orderblocks — Order Blocks with FVG confirmation
 *  5. trading_smc_structure   — Market Structure detection (BOS / CHoCH)
 */
object SmcToolDefinitions {

    val allDefinitions: List<FunctionDeclaration> = listOf(

        // ── 1. Full SMC Analysis ───────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_smc_analysis",
            description = """
                |[SMC DASHBOARD] วิเคราะห์ครบทุก Smart Money Concept สำหรับ symbol ที่กำหนด
                |แสดง: Market Structure (BOS/CHoCH), Order Blocks + FVG, Liquidity Zones, Premium/Discount Zone
                |พร้อม Confluence Stars (★★★★★) บอก strength ของแต่ละ zone
                |ใช้เมื่อ: "วิเคราะห์ SMC BTC", "Order blocks ETH", "Smart money BTCUSDT 1h"
                |ใช้เมื่อ: "zone ไหน strong ที่สุด", "BOS ล่าสุดเป็นอะไร", "Premium Discount ETHUSDT"
            """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING",
                        "Symbol ที่ต้องการวิเคราะห์ เช่น BTCUSDT, XAUUSD (Gold), EURUSD, AAPL"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe: 1m, 5m, 15m, 30m, 1h, 4h, 1d",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 2. MTF Sweeps ──────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_smc_sweeps",
            description = """
                |[MTF SWEEPS] ตรวจจับ Liquidity Sweep signals ข้ามทุก Timeframe (M1, M5, M15, M30, H1, H4)
                |Sweep = ราคา wick ทะลุ Order Block แล้ว reclaim กลับ (>50%) — สัญญาณ reversal แรง
                |Bullish Sweep: wick ลงใต้ Bearish OB แล้ว close กลับขึ้น (สัญญาณ Long)
                |Bearish Sweep: wick ขึ้นเหนือ Bullish OB แล้ว close กลับลง (สัญญาณ Short)
                |ใช้เมื่อ: "sweep BTC ทุก TF", "MTF sweep ETHUSDT", "มี sweep ไหมวันนี้"
            """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING",
                        "Symbol ที่ต้องการวิเคราะห์ เช่น BTCUSDT, XAUUSD, EURUSD")
                ),
                required = listOf("symbol")
            )
        ),

        // ── 3. MTF Liquidity Zones ─────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_smc_liquidity",
            description = """
                |[MTF LIQUIDITY] แสดง Liquidity Zones ข้ามหลาย Timeframe พร้อม Confluence Stars
                |Equal Highs = แหล่ง Sell-side Liquidity (ราคาจะไป sweep ก่อนลง)
                |Equal Lows  = แหล่ง Buy-side Liquidity  (ราคาจะไป sweep ก่อนขึ้น)
                |Stars ★★★★★ = OB Confluence + Structure + Premium/Discount + Trend alignment
                |ใช้เมื่อ: "liquidity BTC ทุก TF", "Equal highs ETH", "แหล่ง liquidity SOLUSDT"
                |ใช้เมื่อ: "ราคาจะไป sweep ที่ไหน", "MTF liquidity zones"
            """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING",
                        "Symbol ที่ต้องการวิเคราะห์ เช่น BTCUSDT, XAUUSD, AAPL")
                ),
                required = listOf("symbol")
            )
        ),

        // ── 4. Order Blocks ────────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_smc_orderblocks",
            description = """
                |[ORDER BLOCKS] ตรวจจับ Bullish/Bearish Order Blocks พร้อม FVG confirmation
                |Order Block = candle สุดท้ายที่ฝั่ง opposing ก่อนเกิด structural break
                |FVG filter = OB ต้องมี Fair Value Gap ยืนยัน (Displacement)
                |Bullish OB (🟢): แหล่ง demand — ราคามักเด้งขึ้น
                |Bearish OB (🔴): แหล่ง supply   — ราคามักร่วงลง
                |ใช้เมื่อ: "Order blocks BTC 4h", "OB ที่ยังไม่โดน ETHUSDT", "demand zone SOL"
            """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING",
                        "Symbol ที่ต้องการวิเคราะห์ เช่น BTCUSDT, XAUUSD, EURUSD, AAPL"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe: 5m, 15m, 1h, 4h, 1d",
                        enum = listOf("5m", "15m", "30m", "1h", "4h", "1d")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 5. Market Structure ────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_smc_structure",
            description = """
                |[MARKET STRUCTURE] วิเคราะห์ Market Structure: BOS, CHoCH, Premium/Discount zones
                |BOS (Break of Structure): breakout ต่อเนื่องในทิศทางเดิม
                |CHoCH (Change of Character): structure flip — สัญญาณ trend reversal สำคัญ
                |Premium zone (>75%): แพง — suitable สำหรับ Short
                |Discount zone (<25%): ถูก — suitable สำหรับ Long
                |Equilibrium (50%): จุดสมดุล
                |ใช้เมื่อ: "structure BTC", "BOS หรือ CHoCH ETHUSDT", "trend ETH เป็นอะไร"
                |ใช้เมื่อ: "ราคาอยู่ใน premium หรือ discount", "market structure SOL 4h"
            """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING",
                        "Symbol ที่ต้องการวิเคราะห์ เช่น BTCUSDT, XAUUSD, EURUSD, AAPL"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe: 15m, 1h, 4h, 1d",
                        enum = listOf("15m", "1h", "4h", "1d")
                    )
                ),
                required = listOf("symbol")
            )
        )
    )
}
