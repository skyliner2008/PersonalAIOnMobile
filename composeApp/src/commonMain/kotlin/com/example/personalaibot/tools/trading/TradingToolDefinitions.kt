package com.example.personalaibot.tools.trading

import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.FunctionParameters
import com.example.personalaibot.tools.ParameterProperty

/**
 * TradingToolDefinitions — รายการ Tool definitions ทั้งหมดสำหรับ Gemini Function Calling
 *
 * [CRITICAL GUIDELINES FOR AI]
 * 1. ห้ามสร้างหรือสมมติชื่อ Tool (Function Name) ขึ้นมาเองเด็ดขาด แม้ชื่อนั้นจะดูสมเหตุสมผล (เช่น analyze_and_display_report)
 * 2. หากเรียกใช้ Tool แล้วเกิด Error หรือไม่มีข้อมูล (Empty Result) ให้รายงานผู้ใช้ตามตรง ห้าม "แต่ง" ข้อมูลปลอมขึ้นมาทดแทน
 * 3. ใช้เฉพาะชื่อสัญลักษณ์ที่ระบุใน Tool Parameter เท่านั้น
 */
object TradingToolDefinitions {

    val allDefinitions: List<FunctionDeclaration> = listOf(

        // ── 1. Real-time Price ─────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_price",
            description = """[SOURCE OF TRUTH] ดึงราคา real-time ของหุ้น, Crypto, ETF, Index หรือ FX pair
                |ห้ามคาดเดาราคาเองเด็ดขาด แม้คุณจะคิดว่าคุณรู้ราคาล่าสุดก็ตาม คุณต้องใช้ Tool นี้เพื่อดึงราคาเสมอ
                |ใช้เมื่อผู้ใช้ถามราคา เช่น "BTC ราคาเท่าไหร่", "AAPL อยู่ที่เท่าไหร่"
                |รองรับ: AAPL, BTC-USD, ETH-USD, SPY, ^GSPC, EURUSD=X, GC=F""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty(
                        type = "STRING",
                        description = "Yahoo Finance symbol เช่น AAPL, BTC-USD, ETH-USD, ^GSPC, XAUUSD=X (Gold Spot), GC=F (Gold Futures), EURUSD=X"
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 2. Market Snapshot ─────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_market_snapshot",
            description = """[SOURCE OF TRUTH] ดึงรายชื่อหุ้นและภาพรวมตลาดแบบเจาะจง
                |ห้ามสรุปรายชื่อหุ้นจากความจำของคุณ (Internal Knowledge) โดยเด็ดขาด คุณต้องใช้ Tool นี้เพื่อดึงรายชื่อหุ้นที่เป็นปัจจุบันเสมอ 
                |ใช้เมื่อผู้ใช้ถามหา "รายชื่อหุ้น", "หุ้นกลุ่ม...", "ตลาดตอนนี้เป็นยังไง"
                |Market: US (Default), TH (ไทย), Crypto, Global
                |Sector: Energy, Technology, Financials, Healthcare, Utilities, Real Estate ฯลฯ""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "market"   to ParameterProperty("STRING", "ตลาดที่ต้องการ: US (Stocks), TH (หุ้นไทย), Crypto, Forex, Gold, Global (Default: Global)"),
                    "sector"   to ParameterProperty("STRING", "กลุ่มอุตสาหกรรม (ถ้ามี): Energy, Technology, Finance, Healthcare ฯลฯ"),
                    "limit"    to ParameterProperty("NUMBER", "จำนวนผลลัพธ์ (Default 10)")
                ),
                required = emptyList()
            )
        ),

        // ── 3. Top Gainers ─────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_top_gainers",
            description = """แสดงหุ้นหรือ Crypto ที่ราคาขึ้นมากที่สุดในตลาดที่กำหนด
                |ใช้เมื่อผู้ใช้ถามว่า "วันนี้ตัวไหนขึ้นเยอะ", "top gainers Binance", "หุ้นไหนดีวันนี้"
                |Exchange: BINANCE, KUCOIN, BYBIT, NASDAQ, NYSE, BIST, EGX""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, KUCOIN, BYBIT, NASDAQ, NYSE, BIST, EGX"),
                    "limit"    to ParameterProperty("NUMBER", "จำนวนผลลัพธ์ (default 20, max 50)")
                ),
                required = listOf("exchange")
            )
        ),

        // ── 4. Top Losers ──────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_top_losers",
            description = """แสดงหุ้นหรือ Crypto ที่ราคาลงมากที่สุดในตลาดที่กำหนด
                |ใช้เมื่อผู้ใช้ถามว่า "ตัวไหนลงเยอะ", "top losers วันนี้", "Crypto ที่ dump"
                |Exchange: BINANCE, KUCOIN, BYBIT, NASDAQ, NYSE""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, KUCOIN, BYBIT, NASDAQ, NYSE, BIST, EGX"),
                    "limit"    to ParameterProperty("NUMBER", "จำนวนผลลัพธ์ (default 20)")
                ),
                required = listOf("exchange")
            )
        ),

        // ── 5. Technical Analysis ──────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_technical_analysis",
            description = """วิเคราะห์ Technical Analysis ครบถ้วนสำหรับ symbol เดี่ยว
                |แสดง RSI, MACD, Bollinger Bands, EMA20/50/200, ADX, Stochastic, ATR
                |พร้อม signal สรุป: STRONG BUY / BUY / HOLD / SELL / STRONG SELL
                |ใช้เมื่อผู้ใช้ถามว่า "วิเคราะห์ BTC", "AAPL signal เป็นยังไง", "TA ETH 1H" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น BTCUSDT, XAUUSD, EURUSD, AAPL, DELTA (หุ้นไทย)"),
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE (Crypto), NASDAQ (US), SET (หุ้นไทย), OANDA (บังคับสำหรับ Gold/Forex เสมอ)"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe: 15m, 1h, 4h, 1D, 1W",
                        enum = listOf("15m", "1h", "4h", "1D", "1W")
                    )
                ),
                required = listOf("symbol", "exchange")
            )
        ),

        // ── 6. Multi-Timeframe Analysis ────────────────────────────────────
        FunctionDeclaration(
            name = "trading_multi_timeframe",
            description = """วิเคราะห์ทุก Timeframe พร้อมกัน Weekly→Daily→4H→1H→15m
                |บอก alignment ว่าสัญญาณตรงกันหรือขัดแย้งกัน
                |ใช้เมื่อผู้ใช้ถามว่า "BTC ทุก timeframe เป็นยังไง", "multi timeframe ETH" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น BTCUSDT, XAUUSD, EURUSD, AAPL"),
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, NASDAQ, OANDA (สำหรับ XAU/FX) เสมอ")
                ),
                required = listOf("symbol", "exchange")
            )
        ),

        // ── 7. Bollinger Squeeze ───────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_bollinger_scan",
            description = """สแกนหา symbols ที่ Bollinger Band กำลัง squeeze (BBW ต่ำ)
                |หมายความว่ากำลังจะเกิด breakout ใหญ่
                |ใช้เมื่อผู้ใช้ถามว่า "หาตัวที่กำลัง squeeze", "bollinger squeeze Binance" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, KUCOIN, NASDAQ ฯลฯ"),
                    "limit"    to ParameterProperty("NUMBER", "จำนวนผลลัพธ์ (default 30)")
                ),
                required = listOf("exchange")
            )
        ),

        // ── 8. Oversold Scanner ────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_oversold_scan",
            description = """สแกนหา symbols ที่ RSI < 30 (Oversold) — โอกาส bounce ขึ้น
                |ใช้เมื่อผู้ใช้ถามว่า "ตัวไหน oversold", "RSI ต่ำ Binance", "หาตัว dip" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, KUCOIN, NASDAQ ฯลฯ"),
                    "limit"    to ParameterProperty("NUMBER", "จำนวนผลลัพธ์ (default 20)")
                ),
                required = listOf("exchange")
            )
        ),

        // ── 9. Overbought Scanner ──────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_overbought_scan",
            description = """สแกนหา symbols ที่ RSI > 70 (Overbought) — ระวังการ correction
                |ใช้เมื่อผู้ใช้ถามว่า "ตัวไหน overbought", "RSI สูง", "ตัวที่ร้อนเกินไป" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, KUCOIN, NASDAQ ฯลฯ"),
                    "limit"    to ParameterProperty("NUMBER", "จำนวนผลลัพธ์ (default 20)")
                ),
                required = listOf("exchange")
            )
        ),

        // ── 10. Volume Breakout ────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_volume_breakout",
            description = """สแกนหา symbols ที่ Volume พุ่งสูงผิดปกติ + ราคาขึ้นแรง
                |สัญญาณ breakout จริง ไม่ใช่แค่ราคาขึ้น
                |ใช้เมื่อผู้ใช้ถามว่า "ตัวไหน volume ระเบิด", "volume breakout วันนี้" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, KUCOIN, NASDAQ ฯลฯ"),
                    "limit"    to ParameterProperty("NUMBER", "จำนวนผลลัพธ์ (default 20)")
                ),
                required = listOf("exchange")
            )
        ),

        // ── 11. Reddit Sentiment ───────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_sentiment",
            description = """วิเคราะห์ความรู้สึกของ Reddit community ต่อ symbol ที่กำหนด
                |ดู posts จาก wallstreetbets, investing, CryptoCurrency
                |บอก bullish/bearish score และ top posts ที่ hot
                |ใช้เมื่อผู้ใช้ถามว่า "Reddit พูดถึง BTC ยังไง", "sentiment AAPL", "ตลาดมอง ETH ยังไง" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Symbol เช่น BTC, AAPL, ETH, NVDA, TSLA")
                ),
                required = listOf("symbol")
            )
        ),

        // ── 12. Financial News ─────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_news",
            description = """ดึงข่าวการเงินล่าสุดจาก Reuters, CoinDesk, Yahoo Finance
                |กรองตาม symbol ที่ต้องการได้
                |ใช้เมื่อผู้ใช้ถามว่า "ข่าว BTC วันนี้", "มีข่าวอะไรเกี่ยวกับ AAPL", "ข่าวตลาดล่าสุด" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Symbol ที่ต้องการกรองข่าว (ถ้าไม่ระบุจะดึงข่าวทั่วไป)"),
                    "limit"  to ParameterProperty("NUMBER", "จำนวนข่าว (default 8)")
                ),
                required = emptyList()
            )
        ),

        // ── 13. Combined Analysis (Power Tool) ────────────────────────────
        FunctionDeclaration(
            name = "trading_combined",
            description = """วิเคราะห์ครบทุกด้านพร้อมกัน: TA + Reddit Sentiment + Financial News
                |ให้ Confluence Decision สุดท้าย (BUY/SELL/MIXED) พร้อม reasoning
                |ใช้เมื่อผู้ใช้ถามว่า "วิเคราะห์ BTC ทุกด้าน", "ดู ETH full analysis", "combined BTCUSDT" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น BTCUSDT, XAUUSD, EURUSD, AAPL"),
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, NASDAQ, OANDA, TVC"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe: 15m, 1h, 4h, 1D",
                        enum = listOf("15m", "1h", "4h", "1D")
                    )
                ),
                required = listOf("symbol", "exchange")
            )
        ),

        // ── Phase 1: Advanced Analysis Suite ─────────────────────────────
        
        FunctionDeclaration(
            name = "trading_fundamental_analysis",
            description = """วิเคราะห์ปัจจัยพื้นฐาน (Fundamental) ของหุ้นรายตัว
                |แสดง Revenue Growth, Profit Margins, P/E Ratio, Debt/Equity, ราคาเป้าหมาย (Target Price)
                |และข้อเสนอแนะ (Recommendation) จากนักวิเคราะห์
                |ใช้เมื่อผู้ใช้ถาม: "ดูปัจจัยพื้นฐาน AAPL", "หุ้น NVDA พื้นฐานเป็นยังไง" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Symbol เช่น AAPL, TSLA, NVDA")
                ),
                required = listOf("symbol")
            )
        ),

        FunctionDeclaration(
            name = "trading_fear_greed",
            description = """ดึงดัชนีความกลัวและความโลภ (Crypto Fear & Greed Index)
                |ใช้เพื่อดู Sentiment ภาพรวมของตลาด Crypto ว่าอยู่ในจุดที่กลัวสุดขีด (ซื้อ) หรือโลภสุดขีด (ขาย)
                |ใช้เมื่อผู้ใช้ถาม: "ตลาดคริปโตตอนนี้เป็นยังไง", "กลัวหรือโลภแล้วตอนนี้" """.trimMargin(),
            parameters = null
        ),

        FunctionDeclaration(
            name = "trading_macro_calendar",
            description = """ดึงปฏิทินเศรษฐกิจและเหตุการณ์ Macro สำคัญ
                |แสดงวันที่, เหตุการณ์ (เช่น Fed Meeting, CPI), และระดับผลกระทบ (Impact)
                |ใช้เมื่อผู้ใช้ถาม: "สัปดาห์นี้มีข่าวเศรษฐกิจอะไรบ้าง", "Fed ประชุมวันไหน" """.trimMargin(),
            parameters = null
        ),

        FunctionDeclaration(
            name = "trading_correlation_matrix",
            description = """วิเคราะห์ความสัมพันธ์ (Correlation) ระหว่างสินทรัพย์หลายรายการ
                |แสดงว่าสินทรัพย์เหล่านั้นเคลื่อนที่ไปในทิศทางเดียวกัน (1.0) หรือสวนทางกัน (-1.0) หรือไม่เกี่ยวกัน (0)
                |ใช้เมื่อผู้ใช้ถาม: "ทองกับเงินสัมพันธ์กันแค่ไหน", "เทียบ correlation BTC กับ S&P500" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbols" to ParameterProperty("STRING", "รายชื่อ symbol คั่นด้วย comma เช่น BTC-USD,GC=F,^GSPC,EURUSD=X"),
                    "days"    to ParameterProperty("NUMBER", "จำนวนวันย้อนหลัง (Default 30)")
                ),
                required = emptyList()
            )
        ),

        FunctionDeclaration(
            name = "trading_position_sizing",
            description = """คำนวณขนาดไม้ (Position Sizing) ตามความเสี่ยงที่กำหนด
                |ช่วยคำนวณว่าควรเปิดกี่ Units, มูลค่าสัญญาเท่าไหร่ และใช้ Leverage เท่าไหร่
                |ใช้เมื่อผู้ใช้ถาม: "คำนวณไม้ให้หน่อย", "ถ้าเสี่ยง 2% ต้องเข้ากี่ BTC" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "balance"   to ParameterProperty("NUMBER", "เงินทุนในพอร์ต (Default 10000)"),
                    "risk_pct"  to ParameterProperty("NUMBER", "ความเสี่ยงที่รับได้เป็น % (Default 1)"),
                    "entry"     to ParameterProperty("NUMBER", "ราคาจุดเข้าซื้อ"),
                    "stop_loss" to ParameterProperty("NUMBER", "ราคาจุดตัดขาดทุน")
                ),
                required = listOf("entry", "stop_loss")
            )
        ),

        // ── Phase 5: Automation & Alerts ─────────────────────────────────

        FunctionDeclaration(
            name = "automation_manage_alerts",
            description = """สร้างหรือลบการแจ้งเตือน (Alert) และการเฝ้าติดตามตลาดอัตโนมัติในเบื้องหลัง
                |JARVIS จะทำการดึงข้อมูลมาตรวจสอบเงื่อนไขทุกๆ N นาที และส่ง Notification เมื่อพบจังหวะที่กำหนด
                |Action: 'create' เพื่อสร้างใหม่, 'delete' เพื่อลบ (ระบุ alert_id)
                |Tool Name: ชื่อ tool ที่จะใช้ดึงข้อมูล (เช่น trading_price, trading_sentiment)
                |Condition: ฟิลด์ที่ต้องการตรวจ เช่น 'price', 'rsi', 'bias_score'
                |Operator: >=, <=, ==, >, <
                |ใช้เมื่อผู้ใช้สั่ง: "ช่วยเฝ้าดูทองให้หน่อย ถ้าถึง 4800 บอกฉันด้วย", "ลบการแจ้งเตือน ID 5" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action"   to ParameterProperty("STRING", "การกระทำ: create หรือ delete", enum = listOf("create", "delete")),
                    "alert_id" to ParameterProperty("NUMBER", "ID ของการแจ้งเตือนที่ต้องการลบ (เฉพาะ action=delete)"),
                    "name"     to ParameterProperty("STRING", "ชื่อเรียกของงานแจ้งเตือนนี้ (เช่น 'Gold Alert')"),
                    "symbol"   to ParameterProperty("STRING", "Symbol ที่ต้องการเฝ้าดู เช่น XAUUSD, BTCUSDT"),
                    "tool_name" to ParameterProperty("STRING", "Tool ที่จะใช้ดึงข้อมูล (default: trading_price)"),
                    "condition_field" to ParameterProperty("STRING", "ฟิลด์ที่จะตรวจสอบ (เช่น price)"),
                    "condition_operator" to ParameterProperty("STRING", "เครื่องมือเปรียบเทียบ (>=, <=, ==, >, <)"),
                    "condition_value"    to ParameterProperty("STRING", "ค่าเปรียบเทียบ (เช่น 4800, 30, bullish)"),
                    "interval_minutes"   to ParameterProperty("NUMBER", "ความถี่ในการดึงข้อมูล (1-1440 นาที, default 15)")
                ),
                required = listOf("action")
            )
        ),
        
        // ── 14. Advanced Strategy Suite (LSD/Orderflow/Fibo) ───────────────
        FunctionDeclaration(
            name = "trading_deep_analysis_suite",
            description = """วิเคราะห์ตลาดเชิงลึก 5 มิติ (LSD Trend, Orderflow Delta, Fibo Strength, Momentum Squeeze)
                |เหมาะสำหรับการหาจุดกลับตัวและความต่อเนื่องของแนวโน้มระดับสถาบัน
                |ใช้เมื่อผู้ใช้ต้องการการวิเคราะห์ที่แม่นยำที่สุด หรือถามหา "Institutional Analysis" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe: 15m, 1h, 4h, 1D",
                        enum = listOf("15m", "1h", "4h", "1D")
                    )
                ),
                required = listOf("symbol")
            )
        )
    )
}
