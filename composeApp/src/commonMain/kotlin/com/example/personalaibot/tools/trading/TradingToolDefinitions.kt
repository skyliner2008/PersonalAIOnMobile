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
                        description = "Timeframe: m1, m5, m15, h1, h4, D1, W1 (หรือ 1m, 5m, 15m, 1h, 4h, 1D, 1W)",
                        enum = listOf("m1", "m5", "m15", "h1", "h4", "D1", "W1", "1m", "5m", "15m", "1h", "4h", "1D", "1W")
                    )
                ),
                required = listOf("symbol")
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
                required = listOf("symbol")
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
            description = """ดึงข่าวการเงินล่าสุดจาก Reuters, CoinDesk, Yahoo Finance, MarketWatch
                |พร้อมระบบ AI Analysis เพื่อสรุปนัยสำคัญและ Bias Score (-10 ถึง 10)
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
                        description = "Timeframe: 1m, 5m, 15m, 30m, 1h, 4h, 1D, 1W (หรือ m1, m5, m15, h1, h4, d1, w1)",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D", "1W", "m1", "m5", "m15", "m30", "h1", "h4", "d1", "w1")
                    )
                ),
                required = listOf("symbol")
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
            description = """ดึงดัชนีความกลัวและความโลภ (Crypto Fear & Greed Index ตัวจริงจาก alternative.me — ฟรี real-time)
                |แสดงค่า 0-100, การตีความภาษาไทย และอนุกรมย้อนหลัง 7 วันพร้อมทิศทางอารมณ์ตลาด
                |ใช้เพื่อดู Sentiment ภาพรวมของตลาด Crypto ว่าอยู่ในจุดที่กลัวสุดขีด (ซื้อ) หรือโลภสุดขีด (ขาย)
                |ใช้เมื่อผู้ใช้ถาม: "ตลาดคริปโตตอนนี้เป็นยังไง", "กลัวหรือโลภแล้วตอนนี้" """.trimMargin(),
            parameters = null
        ),

        FunctionDeclaration(
            name = "trading_crypto_overview",
            description = """ภาพรวมตลาดคริปโตทั้งตลาดจาก CoinGecko (ฟรี ไม่ต้องใช้ API Key)
                |Market Cap รวม + %เปลี่ยน 24h, Volume, BTC/ETH Dominance, เหรียญ Trending และ Fear & Greed Index
                |ใช้เมื่อผู้ใช้ถาม: "ภาพรวมตลาดคริปโต", "BTC dominance เท่าไหร่", "เหรียญไหนกำลังมาแรง", "ตลาดคริปโตวันนี้" """.trimMargin(),
            parameters = null
        ),

        FunctionDeclaration(
            name = "trading_macro_calendar",
            description = """ดึงปฏิทินเศรษฐกิจ (Economic Calendar) จาก ForexFactory แบบ Real-time แปลงเป็นเวลาไทยที่ถูกต้อง
                |แสดงผลกระทบ (High/Medium Impact) และสถานะ [รอประกาศ] หรือ [ประกาศแล้ว]
                |ใช้เมื่อผู้ใช้ถาม: "สัปดาห์นี้มีข่าวเศรษฐกิจอะไรบ้าง", "ตัวเลขเศรษฐกิจที่เหลือ", "CPI ออกวันไหน", "คืนนี้มีข่าวอะไร" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "limit" to ParameterProperty("NUMBER", "จำนวนเหตุการณ์ (Default 15)"),
                    "filter" to ParameterProperty("STRING", "ตัวกรองเหตุการณ์: upcoming (เฉพาะที่ยังไม่ประกาศ), today (เฉพาะวันนี้), all (ทั้งหมดของสัปดาห์)"),
                    "currency" to ParameterProperty("STRING", "กรองตามสกุลเงิน เช่น USD, EUR, GBP, CAD, JPY, AUD, NZD, CHF")
                ),
                required = emptyList()
            )
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
            name = "trading_economic_data",
            description = """ดึงตัวเลขเศรษฐกิจมหภาคสหรัฐฯ จาก FRED (Federal Reserve Economic Data) — GDP, เงินเฟ้อ CPI/PCE, การว่างงาน, ดอกเบี้ย Fed, Bond Yield, Nonfarm Payrolls ฯลฯ
                |ใช้เมื่อผู้ใช้ถาม: "GDP อเมริกาล่าสุด", "เงินเฟ้อสหรัฐเท่าไหร่", "อัตราการว่างงาน", "ดอกเบี้ย Fed ตอนนี้", "ตัวเลขเศรษฐกิจสหรัฐ"
                |series presets: gdp, gdp_growth, cpi, core_cpi, pce, unemployment, nfp, fedfunds, 10y, 2y, m2, retail, housing, sentiment, indpro, claims, overview (สรุปตัวชี้วัดหลัก)
                |หรือระบุ FRED series id โดยตรงได้ (เช่น DGS10, UNRATE)""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "series" to ParameterProperty("STRING", "ชื่อ preset (gdp, cpi, unemployment, fedfunds, overview...) หรือ FRED series id โดยตรง"),
                    "limit"  to ParameterProperty("NUMBER", "จำนวนช่วงข้อมูลย้อนหลังที่แสดง (Default 12)"),
                    "api_key" to ParameterProperty("STRING", "FRED API Key (ไม่บังคับ — ไม่มี key ก็ดึงได้ผ่านช่องทางสาธารณะ)")
                ),
                required = listOf("series")
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
            description = """จัดการการแจ้งเตือน (Alert) และการเฝ้าติดตามตลาดอัตโนมัติในเบื้องหลัง
                |JARVIS จะทำการดึงข้อมูลมาตรวจสอบเงื่อนไขทุกๆ N นาที เมื่อเงื่อนไขตรงระบบจะปลุก AI มาสรุปและแจ้งเตือนผู้ใช้ (notification มีปุ่ม "หยุดแจ้งเตือน" / "แจ้งเตือนซ้ำ" ให้ผู้ใช้กด)
                |Action: 'create' สร้างใหม่, 'update' แก้ค่าเปรียบเทียบ (ระบุ alert_id + condition_value ใหม่), 'rename' เปลี่ยนชื่อ (ระบุ alert_id + name ใหม่ — ชื่อนี้ใช้ในหัว notification และเสียงพูด), 'delete' ลบ (ระบุ alert_id), 'list' ดูรายการ
                |
                |[สำคัญ] ตั้งเงื่อนไขได้เฉพาะ tool_name/field ที่ background ดึงค่าได้จริงต่อไปนี้เท่านั้น (ห้ามตั้งมั่ว เช่น EMA cross ที่ไม่มีในรายการ):
                |- trading_price: price, change, change_pct, prev_close, high_52w, low_52w, direction (ใช้กับ ==)
                |- trading_indicators ⭐ USER QUERY / ALERT DATA (TradingView — เลือก TF ด้วย symbol@TF เช่น XAUUSD@15m, default 1h): รองรับค่ารายละเอียดสำหรับตอบคำถามผู้ใช้และสร้าง Alert เช่น close, open, high, low, ema7, ema9, ema20, ema21, ema50, ema100, ema200, sma20, sma50, sma100, sma200, ema_cross_state, ema50_200_spread, ema20_50_spread, rsi7, rsi9, rsi14, rsi21, macd, macd_signal, macd_hist, stoch_k, stoch_d, cci20, mfi14, bb_upper, bb_basis, bb_lower, bb_percent_b, bb_width, atr7, atr14, atr21, atr_pct, adx14, di_plus, di_minus, vwap, vwap_distance_pct, volume, volume_sma20, volume_ratio20, obv, obv_slope20, ichimoku_tenkan, ichimoku_kijun, ichimoku_cloud_top, ichimoku_cloud_bottom, pivot, resistance1, resistance2, support1, support2, supertrend, donchian_upper, donchian_mid, donchian_lower, roc, williams_r, fibonacci_levels
                |- trading_smc ⭐ (Smart Money Concepts — เลือก TF ด้วย symbol@TF): close, smc_zone (PREMIUM/DISCOUNT/EQUILIBRIUM ใช้กับ ==), smc_zone_pct (>=80 พรีเมียม, <=20 ดิสเคาน์), smc_trend, smc_last_event (BOS_UP/BOS_DOWN/CHOCH_UP/CHOCH_DOWN), smc_structure_high/low, smc_equilibrium, smc_premium_bot, smc_discount_top, bull_ob_dist, bear_ob_dist, fvg_dist, liq_above_dist, liq_below_dist, liq_above_stars, liq_below_stars, attack_force, atr
                |- trading_smc_flow ⭐ (สัญญาณ SMC Flow System — เลือก TF ด้วย symbol@TF): smc_signal (BUY/SELL/NONE ใช้กับ == — confluence ครบ: trigger+structure+zone), smc_recipes (A=OB, B=FVG+OB, C=Fib golden ใช้กับ contains), ema14_60_signal (BUY/SELL/NONE ใช้กับ ==), ema14_60_cross (GOLDEN_CROSS/DEATH_CROSS/NONE ใช้กับ ==), ema14_60_state, ema14, ema60, ema14_60_spread, utbot_signal (BUY/SELL/NONE), utbot_trend (BULL/BEAR), utbot_stop, structure_bias, in_fib_golden (0/1), fib_golden_top, fib_golden_bottom, fib_swing_high, fib_swing_low, close
                |- trading_strategy_signal ⭐ (กลยุทธ์เชิงวิชาการจาก Strategy Library — เลือก TF ด้วย symbol@TF): decision (BUY/SELL/WAIT), gate (PASS/NO_CONFLUENCE/MTF_MISALIGNMENT/DATA_QUALITY), confidence_pct, data_quality_pct, mtf_alignment, consensus_signal (STRONG_BUY/BUY/NEUTRAL/SELL/STRONG_SELL ใช้กับ ==), consensus_score (ตัวเลข -5 ถึง 5), tsmom_signal, trend_signal, reversal_signal, donchian_signal, w52_signal (BUY/SELL/NONE ใช้กับ ==), trend_state (UPTREND/DOWNTREND/RANGE), tsmom_roc_pct, reversal_rsi, donchian_upper, donchian_mid, donchian_lower, w52_high, w52_proximity_pct, close
                |- trading_signal_alert 📡 (สัญญาณเทรด "ที่เพิ่งเกิด" ในแท่งปิดล่าสุด จาก Unified SMC Multi-TF (engine หลัก ประเมินบน 15m) + Momentum / Reversal (classic ที่ forensics 2026-08-27 พิสูจน์ว่ามี edge) + SMC Engine — เลือก TF ด้วย symbol@TF เช่น XAUUSD@15m): ตั้ง signal_buy >= 1 หรือ signal_sell >= 1 (สร้าง 2 job ถ้าต้องการทั้งสองฝั่ง) — เมื่อสัญญาณเกิด ระบบส่ง payload ครบ (เหตุผลเงื่อนไข, Entry/SL/TP เฉพาะกลยุทธ์, RR, บริบทกราฟ) ให้ AI quick-check ก่อนแจ้งผู้ใช้ — ใช้เมื่อผู้ใช้ขอ "แจ้งเตือนเมื่อมีสัญญาณ Buy/Sell", "ตั้ง signal alert ทอง 15m"
                |- trading_technical_analysis (เลือก TF ด้วย symbol@TF เช่น XAUUSD@15m, default 1h): close, RSI, MACD.macd, MACD.signal, BB.basis, ATR, ADX, Recommend.All, recommend_score, signal (STRONG BUY/SELL ใช้กับ ==), volume
                |- trading_deep_analysis_suite (วิเคราะห์ 5 มิติ — เลือก TF ด้วย symbol@TF): summaryScore (0-100), lsdState (BULLISH/BEARISH/NEUTRAL ใช้กับ ==/contains), lsdConfluenceTF (1-4), deltaLabel (ใช้กับ ==/contains), deltaValue, fiboScore (0-10), momentum (EXPANSION/SQUEEZE/REVERSAL ใช้กับ ==/contains), isSqueeze (0/1), close
                |- trading_sentiment: sentiment_score, bullish_posts, bearish_posts, posts_analyzed, sentiment_label (ใช้กับ ==) ⚠️ Reddit มักตอบ 403 ช่วงนี้ — หลีกเลี่ยงถ้าไม่จำเป็น
                |- trading_fear_greed: value (0-100), classification (ใช้กับ ==)
                |- trading_crypto_overview: btc_dominance, eth_dominance, market_cap_change_24h, total_market_cap_usd, total_volume_24h_usd, active_cryptocurrencies, markets
                |
                |Operator: >=, <=, ==, >, <, contains
                |[โหมดส่งแจ้งเตือน — delivery] "ai" (default) = alert → AI วิเคราะห์/quick-check → แจ้งผู้ใช้ | "direct" = alert → notification + ส่งข้อความเข้าแชทโดยตรง (ไม่เรียก AI ประหยัดโทเคน — เหมาะตอนผู้ใช้อยากเฝ้าดูความถี่ของสัญญาณก่อน)
                |ตัวอย่าง: RSI Overbought → tool=trading_technical_analysis, field=RSI, >= 70 | RSI Oversold → RSI <= 30 | ราคาทองถึงเป้า → tool=trading_price, field=price, >= 4100
                |ใช้เมื่อผู้ใช้สั่ง: "ช่วยเฝ้าดูทองให้หน่อย ถ้าถึง 4800 บอกฉันด้วย", "แก้ alert ID 5 เป็น 4200", "ลบการแจ้งเตือน ID 5", "มี alert อะไรอยู่บ้าง" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action"   to ParameterProperty("STRING", "การกระทำ: create, update, delete, rename หรือ list", enum = listOf("create", "update", "delete", "rename", "list")),
                    "alert_id" to ParameterProperty("NUMBER", "ID ของการแจ้งเตือน (เฉพาะ action=update/delete/rename)"),
                    "name"     to ParameterProperty("STRING", "ชื่อเรียกของงานแจ้งเตือนนี้ (เช่น 'Gold Alert') — action=rename ใช้เป็นชื่อใหม่"),
                    "symbol"   to ParameterProperty("STRING", "Symbol ที่ต้องการเฝ้าดู เช่น XAUUSD, BTCUSDT"),
                    "tool_name" to ParameterProperty("STRING", "Tool ที่จะใช้ดึงข้อมูล (default: trading_price) — ต้องอยู่ในรายการที่ background รองรับ"),
                    "condition_field" to ParameterProperty("STRING", "ฟิลด์ที่จะตรวจสอบ — ต้องอยู่ในรายการของ tool_name นั้นเท่านั้น"),
                    "condition_operator" to ParameterProperty("STRING", "เครื่องมือเปรียบเทียบ (>=, <=, ==, >, <, contains)"),
                    "condition_value"    to ParameterProperty("STRING", "ค่าเปรียบเทียบ (เช่น 4800, 30, bullish)"),
                    "delivery" to ParameterProperty("STRING", "โหมดส่งแจ้งเตือน: ai = AI วิเคราะห์ก่อนแจ้ง (default) | direct = ส่ง notification+แชทโดยตรง ไม่เรียก AI (ประหยัดโทเคน)", enum = listOf("ai", "direct")),
                    "voice"    to ParameterProperty("STRING", "เปิดแจ้งเตือนด้วยเสียงพูด: true (default) หรือ false"),
                    "interval_minutes"   to ParameterProperty("NUMBER", "ความถี่ในการตรวจสอบสัญญาณ (1-1440 นาที, default 1 นาที)")
                ),
                required = listOf("action")
            )
        ),

        FunctionDeclaration(
            name = "trading_signal_anticipation",
            description = """จัดการระบบคาดการณ์สัญญาณล่วงหน้า (Signal Anticipation) และปรับแต่งปัจจัยการคาดการณ์
                |ใช้เมื่อผู้ใช้สั่ง: "ใช้ tool คาดการณ์ล่วงหน้า [symbol]", "ตั้งแจ้งเตือนคาดการณ์ทองคำ", "ปรับปัจจัยคาดการณ์", "เพิ่มปัจจัย EMA", "ดูปัจจัยคาดการณ์ล่วงหน้า", "แนะนำปัจจัยคาดการณ์"
                |
                |Action:
                |- 'create': ตั้งแจ้งเตือนคาดการณ์ล่วงหน้าสำหรับ symbol ที่ระบุ (สร้าง Alert Job ในระบบ background เฝ้าดู signal_anticipation >= 1 โดยอัตโนมัติ พร้อมตั้งชุดปัจจัยที่ต้องการ หรือใช้ 4 ปัจจัยหลักเป็น default)
                |- 'config': ปรับเปลี่ยน/เพิ่ม/ลบ ปัจจัยที่ใช้ในการคาดการณ์ของ symbol นั้น (บังคับเลือกจากคลัง 10 ปัจจัยมาตรฐานเพื่อกันความผิดพลาด)
                |- 'list_factors': ดูรายการ 10 ปัจจัยมาตรฐานที่ระบบรองรับ (Curated Factor Whitelist) พร้อมสถานะเปิด/ปิด
                |- 'recommend': ให้ AI แนะนำชุดปัจจัยที่เหมาะสมกับพฤติกรรมตลาดของสินทรัพย์นั้น
                |- 'status': ตรวจสอบการตั้งค่าและสถานะการเฝ้าระวังคาดการณ์ของ symbol นั้น
                |
                |Parameters:
                |- symbol: เช่น XAUUSD, BTCUSDT
                |- timeframe: ไทม์เฟรมที่ต้องการเฝ้าระวัง เช่น 5m, 15m, 30m, 1h, 4h หรือ all หรือหลาย TF เช่น '15m,1h' (default คือ 15m หากไม่ระบุ — รองรับ m5 ถึง h4)
                |- factors: รายการปัจจัยที่ต้องการกำหนด (comma-separated เช่น "KEYZONE_PROXIMITY,WICK_SWEEP_REJECTION,RSI_EXTREME,EMA_NEAR_CROSS")
                |- add_factors: ปัจจัยที่ต้องการเพิ่มเข้าไป (เช่น "BOLLINGER_SQUEEZE" หรือ "VOLUME_ABSORPTION")
                |- remove_factors: ปัจจัยที่ต้องการเอาออก (เช่น "RSI_EXTREME")
                |- delivery: 'ai' (default) หรือ 'direct'""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "การกระทำ: create, config, list_factors, recommend, status", enum = listOf("create", "config", "list_factors", "recommend", "status")),
                    "symbol" to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT"),
                    "timeframe" to ParameterProperty("STRING", "Timeframe เช่น 5m, 15m, 30m, 1h, 4h, all หรือระบุหลาย TF เช่น '15m,1h' (default 15m)"),
                    "factors" to ParameterProperty("STRING", "รายการปัจจัยที่ต้องการเปิดใช้ (คั่นด้วยจุลภาค)"),
                    "add_factors" to ParameterProperty("STRING", "ปัจจัยที่ต้องการเพิ่มเข้าไป"),
                    "remove_factors" to ParameterProperty("STRING", "ปัจจัยที่ต้องการเอาออก"),
                    "delivery" to ParameterProperty("STRING", "โหมดส่งแจ้งเตือน: ai (default) หรือ direct", enum = listOf("ai", "direct")),
                    "voice" to ParameterProperty("STRING", "เปิดแจ้งเตือนด้วยเสียงพูด: true (default) หรือ false")
                ),
                required = listOf("action")
            )
        ),

        FunctionDeclaration(
            name = "automation_manage_schedule",
            description = """จัดการงานตามเวลา (Scheduled Tasks) ที่ระบบจะ "ปลุก AI" มาทำงานเมื่อถึงเวลาที่กำหนด
                |ต่างจาก alert ตรงที่ไม่ต้องมีเงื่อนไขราคา — เป็นการสั่งให้ AI ทำงานตามเวลา เช่น สรุปข่าวตอนเช้า เตือนตอน 2 ทุ่ม
                |Action: 'create' สร้างใหม่, 'delete' ลบ (ระบุ task_id), 'list' ดูรายการทั้งหมด
                |schedule_type: 'one_time' (ยิงครั้งเดียว — ระบุ run_at หรือ in_minutes) หรือ 'daily' (ทุกวัน — ระบุ time_hhmm)
                |prompt คือสิ่งที่ AI จะได้รับเมื่อถึงเวลา เช่น "สรุปข่าวคริปโตเช้านี้" — AI จะประมวลผลแล้วแจ้งเตือนผู้ใช้
                |ใช้เมื่อผู้ใช้สั่ง: "ทุกเช้า 8 โมงสรุปข่าวให้หน่อย", "เตือนฉัน 30 นาทีข้างหน้า", "สองทุ่มเตือนดูกราฟทอง" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "การกระทำ: create, delete หรือ list", enum = listOf("create", "delete", "list")),
                    "task_id" to ParameterProperty("NUMBER", "ID ของงานที่ต้องการลบ (เฉพาะ action=delete)"),
                    "name"   to ParameterProperty("STRING", "ชื่อของงาน (เช่น 'สรุปข่าวเช้า')"),
                    "prompt" to ParameterProperty("STRING", "คำสั่งที่จะส่งให้ AI เมื่อถึงเวลา (เช่น 'สรุปข่าวตลาดคริปโตล่าสุด')"),
                    "schedule_type" to ParameterProperty("STRING", "one_time หรือ daily", enum = listOf("one_time", "daily")),
                    "time_hhmm" to ParameterProperty("STRING", "เวลาประจำวันรูปแบบ HH:mm เช่น 08:00, 20:30 (เฉพาะ daily)"),
                    "run_at" to ParameterProperty("STRING", "วันเวลาที่จะยิง เช่น 2026-07-30 20:00 (เฉพาะ one_time)"),
                    "in_minutes" to ParameterProperty("NUMBER", "อีกกี่นาทีให้ยิง (เฉพาะ one_time — ทางเลือกแทน run_at)")
                ),
                required = listOf("action")
            )
        ),
        
        // ── 14. Advanced Strategy Suite (LSD/Orderflow/Fibo) ───────────────
        FunctionDeclaration(
            name = "trading_deep_analysis_suite",
            description = """วิเคราะห์ตลาดเชิงลึก 5 มิติ (LSD Trend, Orderflow Delta, Fibo Strength, Momentum Squeeze)
                |เหมาะสำหรับการหาจุดกลับตัวและความต่อเนื่องของแนวโน้มระดับสถาบัน
                |ใช้เมื่อผู้ใช้ต้องการการวิเคราะห์ที่แม่นยำที่สุด หรือถามหา "Institutional Analysis"
                |หมายเหตุ: ถ้า MT5 bridge ออฟไลน์ tool นี้จะคำนวณบนเครื่องจากข้อมูล TradingView อัตโนมัติ (ไม่ต้องทำอะไรเพิ่ม)""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe: 1m, 5m, 15m, 30m, 1h, 4h, 1D, 1W (หรือ m1, m5, m15, h1, h4, d1, w1)",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D", "1W", "m1", "m5", "m15", "m30", "h1", "h4", "d1", "w1")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 14b. SMC Flow System (port จาก TV "SMC Flow System v2") ─────────
        FunctionDeclaration(
            name = "trading_smc_flow",
            description = """สัญญาณ BUY/SELL จาก SMC Flow System (port จาก TradingView "SMC Flow System v2")
                |ให้ 3 สัญญาณแยกกัน: (1) EMA 14/60 cross signal, (2) UT Bot signal (ATR trailing stop),
                |(3) SMC Confluence signal = trigger (UT Bot/3-Bar Reversal ภายใน 3 แท่ง) + structure bias
                |+ ราคาอยู่ใน OB/FVG/Fib Golden Zone + แท่งยืนยัน — พร้อม Auto Fib levels จาก swing ปัจจุบัน
                |ใช้เมื่อผู้ใช้ถามหา "สัญญาณซื้อขาย", "SMC Flow", "confluence signal", "EMA cross 14/60"
                |คำนวณจากแท่งเทียน TradingView ในเครื่อง (รองรับ TF 1m/5m/15m/30m/1h/4h/1D)""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h)",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 14c. Strategy Signals (แปลงจาก Strategy Library / Quantpedia) ────
        FunctionDeclaration(
            name = "trading_strategy_signal",
            description = """สัญญาณ BUY/SELL จากกลยุทธ์เชิงวิชาการ (แปลงจาก Strategy Library — Quantpedia/QuantConnect มาคำนวณในเครื่องจากแท่งเทียน TV ไม่พึ่ง QuantConnect)
                |5 กลยุทธ์: tsmom (Time-Series Momentum ROC 20 แท่ง), trend (Trend Following EMA50/200),
                |reversal (Short-Term Reversal RSI+BB), donchian (Donchian Breakout 20 แท่ง), w52high (52-Weeks High proximity)
                |— พร้อม consensus_signal (STRONG_BUY/BUY/NEUTRAL/SELL/STRONG_SELL) จากคะแนนรวมทั้ง 5 กลยุทธ์
                |ใช้เมื่อผู้ใช้ถามหา "สัญญาณกลยุทธ์", "strategy signal", "consensus กลยุทธ์", "กลยุทธ์ไหนให้สัญญาณอะไร"
                |รองรับ TF 1m/5m/15m/30m/1h/4h/1D""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h)",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D")
                    ),
                    "strategy" to ParameterProperty(
                        type = "STRING",
                        description = "กลยุทธ์เฉพาะตัว (default all = ทั้ง 5 + consensus)",
                        enum = listOf("all", "tsmom", "trend", "reversal", "donchian", "w52high")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 14d. Signal Stats (backtest win-rate ของสัญญาณย้อนหลัง) ─────────
        FunctionDeclaration(
            name = "trading_signal_stats",
            description = """สถิติสัญญาณเทรด (default: Momentum/Reversal — 2 ตัวที่ forensics 2026-08-27 พิสูจน์ว่ามี edge; ระบุ kind เองเพื่อวัดตัวที่ถูกตัดได้) 2 แหล่ง:
                |source=backtest (default): จำลอง win-rate/avg R ย้อนหลังจากชุดแท่งเทียน — ใช้เมื่อถาม "กลยุทธ์ไหนแม่นสุด", "backtest สัญญาณ"
                |source=live: สถิติจาก Signal Alert ที่ยิงจริง (ระบบบันทึกทุก signal + ติดตามผล TP/SL อัตโนมัติ) — ใช้เมื่อถาม "วันนี้มี signal อะไรบ้าง", "signal ที่แจ้งไปโดน TP หรือ SL", "สถิติ signal จริง"
                |รองรับ TF 1m/5m/15m/30m/1h/4h/1D (เฉพาะ backtest)""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h, ใช้เฉพาะ source=backtest)",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D")
                    ),
                    "strategy" to ParameterProperty(
                        type = "STRING",
                        description = "กลยุทธ์เฉพาะตัว (default all = ทั้ง 8, ใช้เฉพาะ source=backtest)",
                        enum = listOf("all", "tsmom", "trend", "reversal", "donchian", "w52high", "ema1460", "utbot", "threebar")
                    ),
                    "source" to ParameterProperty(
                        type = "STRING",
                        description = "backtest = จำลองย้อนหลัง (default) | live = สถิติ signal ที่ alert ยิงจริงพร้อมผล TP/SL",
                        enum = listOf("backtest", "live")
                    ),
                    "range" to ParameterProperty(
                        type = "STRING",
                        description = "ช่วงเวลาของ source=live (default today)",
                        enum = listOf("today", "7d", "all")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 14e. Backtest Strategies (จำลองเทรดย้อนหลัง 5,000 แท่ง) ─────────
        FunctionDeclaration(
            name = "trading_backtest",
            description = """รัน backtest กลยุทธ์สัญญาณ 9 ตัว (Momentum/Trend/Reversal/Donchian/52W High/EMA14-60/UT Bot/3-Bar Reversal/SMC) + MIX (ผสมหลายกลยุทธ์โหวตเป็น 1 สัญญาณ) บนแท่งเทียนย้อนหลัง 5,000 แท่ง (15m≈52 วัน, 1h≈7 เดือน, 4h≈2.3 ปี, 1D≈13 ปี)
                |SMC = engine จาก mt5-core-server (OB Bounce/CHoCH/SMS-BMS/FVG Fill/Liquidity Sweep/RSI Divergence) SL/TP ตามโครงสร้างตลาด กรอง RRR≥1.2 และ confluence≥2★
                |MIX = state-based voting: เลือกกลยุทธ์ที่จะผสมผ่าน mix_strategies แล้วออกสัญญาณเมื่อคะแนนโหวตรวมข้ามเกณฑ์ mix_min_votes เช่น "backtest mix ทองคำ 1h" หรือ "backtest mix ด้วย tsmom+trend+utbot"
                |จำลอง bar-by-bar: เข้าที่ราคาปิดแท่งสัญญาณ, SL/TP สูตรเดียวกับ signal alert, ถือทีละ 1 ไม้, คิด spread+commission, เสี่ยง 1% ของพอร์ตต่อไม้
                |ให้ผล: win-rate, profit factor, max drawdown, Sharpe, expectancy R, กำไรสุทธิ แยกตามกลยุทธ์
                |ใช้เมื่อผู้ใช้ถาม: "backtest กลยุทธ์", "ทดสอบกลยุทธ์ย้อนหลัง", "กลยุทธ์ไหนทำกำไรได้จริง", "ลองเทรดย้อนหลัง", "backtest smc", "backtest mix ผสมกลยุทธ์"
                |⚡ MULTI-SESSION: tool นี้ตอบ ack ทันทีแล้วรันงานจริงในเบื้องหลัง (ไม่บล็อกการสนทนา) — เมื่อเสร็จระบบจะส่งผลให้ผู้ใช้เองอัตโนมัติ ห้ามบอกผู้ใช้ว่า "เสร็จแล้ว" หรือสรุปผลจาก tool result นี้ ให้บอกแค่ว่ากำลังดำเนินการ
                |ต่างจาก trading_signal_stats ตรงที่จำลองพอร์ตเงินจริง (equity curve, drawdown, ต้นทุน) ไม่ใช่แค่ win-rate""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h) — all = รันครบ 3 TF (15m/1h/4h) พร้อมตารางเทียบผล",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D", "all")
                    ),
                    "strategy" to ParameterProperty(
                        type = "STRING",
                        description = "กลยุทธ์เฉพาะตัว (default all = ทั้ง 9) — mix = ผสมหลายกลยุทธ์โหวตเป็น 1 สัญญาณ (ใช้ร่วมกับ mix_strategies)",
                        enum = listOf("all", "tsmom", "trend", "reversal", "donchian", "w52high", "ema1460", "utbot", "threebar", "smc", "mix")
                    ),
                    "mix_strategies" to ParameterProperty(
                        type = "STRING",
                        description = "เฉพาะ strategy=mix — รายชื่อกลยุทธ์ที่จะโหวต คั่นด้วย comma เช่น \"tsmom,trend,donchian,utbot\" (เลือกจาก tsmom,trend,reversal,donchian,w52high,ema1460,utbot,threebar; ต้อง ≥2 ตัว; default = tsmom,reversal)"
                    ),
                    "mix_min_votes" to ParameterProperty(
                        type = "STRING",
                        description = "เฉพาะ strategy=mix — เกณฑ์คะแนนโหวตขั้นต่ำที่จะออกสัญญาณ (เช่น \"3\"; default = ครึ่งของจำนวนกลยุทธ์ปัดขึ้น)"
                    ),
                    "costs" to ParameterProperty(
                        type = "STRING",
                        description = "คิดต้นทุน spread+commission (default on)",
                        enum = listOf("on", "off")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 14f. Backtest Optimize (ADAPTIVE discovery-only) ──
        FunctionDeclaration(
            name = "trading_backtest_optimize",
            description = """หา params ที่ดีที่สุดของกลยุทธ์สัญญาณแบบ ADAPTIVE 2 scope: (1) sltp = จูน SL/TP: grid 25 combos + mutation รอบ params ปัจจุบันและรอบ params ที่เคยดีในประวัติ (ขยายได้ถึง SL 0.3-6×, TP 0.5-10×) (2) entry = จูน params จุดเข้าของกลยุทธ์ (ROC lookback, EMA fast/slow, RSI threshold, Donchian period, W52 proximity, UT Bot key/ATR) — edge ของกลยุทธ์อยู่ที่จุดเข้า (3) both = จูนจุดเข้าก่อนแล้วจูน SL/TP ต่อบนจุดเข้าใหม่
                |ทุก scope: เรียนรู้จากประวัติการจูน (OptimizationTrial memory) + พิสูจน์ด้วย walk-forward, permutation test, Monte Carlo → คะแนน overfitting + เกรด
                |DISCOVERY-ONLY: หา candidate + validation evidence เท่านั้น ไม่บันทึก production params; หากต้องการ promote ให้ใช้ trading_backtest_evolve แล้วสั่ง apply=on อย่างชัดเจน
                |ใช้เมื่อผู้ใช้ถาม: "หา params ที่ดีที่สุด", "optimize กลยุทธ์", "จูน SL/TP", "จูนจุดเข้า", "จูน entry params", "กลยุทธ์นี้ overfit ไหม", "adaptive optimize"
                |⚡ MULTI-SESSION: tool นี้ตอบ ack ทันทีแล้วรันงานจริงในเบื้องหลัง — เมื่อเสร็จระบบจะส่งผลให้ผู้ใช้เอง ห้ามสรุปผลจาก tool result นี้
                |อาจใช้เวลา 30 วิ-2 นาที (strategy=all หรือ scope=both นานสุด)""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h)",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D")
                    ),
                    "strategy" to ParameterProperty(
                        type = "STRING",
                        description = "กลยุทธ์เฉพาะตัว (default all = ทั้ง 8 — แนะนำเลือกเฉพาะตัวถ้าต้องการเร็ว)",
                        enum = listOf("all", "tsmom", "trend", "reversal", "donchian", "w52high", "ema1460", "utbot", "threebar")
                    ),
                    "apply" to ParameterProperty(
                        type = "STRING",
                        description = "discovery-only: ไม่บันทึก production params (ค่า apply ถูก ignore เพื่อความปลอดภัย)",
                        enum = listOf("off")
                    ),
                    "scope" to ParameterProperty(
                        type = "STRING",
                        description = "sltp = จูน SL/TP (default) | entry = จูน params จุดเข้า (lookback/period/threshold) | both = จูนจุดเข้าก่อนแล้วจูน SL/TP ต่อบนจุดเข้าใหม่ (ลึกสุด)",
                        enum = listOf("sltp", "entry", "both")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 14g. Backtest Evolve (AI สะท้อนผลปรับ params ทีละนิดเป็นช่วงๆ) ──
        FunctionDeclaration(
            name = "trading_backtest_evolve",
            description = """จำลอง "วิวัฒนาการกลยุทธ์": แบ่งข้อมูลย้อนหลังเป็น 8 ช่วง → รัน backtest ทีละช่วง → AI สะท้อนผลแล้วปรับ SL/TP ทีละนิด (≤10%/รอบ, หนีค่าเริ่มต้นไม่เกิน ±30%) → เทียบผล params วิวัฒน์ vs params เดิม
                |ใช้เมื่อผู้ใช้ถาม: "ลอง evolve กลยุทธ์", "ให้ AI ปรับจูนกลยุทธ์เอง", "วิวัฒนาการกลยุทธ์", "ปรับ params ตามสภาพตลาด"
                |⚡ MULTI-SESSION: tool นี้ตอบ ack ทันทีแล้วรันงานจริงในเบื้องหลัง — เมื่อเสร็จระบบจะส่งผลให้ผู้ใช้เอง ห้ามสรุปผลจาก tool result นี้
                |apply=on = บันทึก params สุดท้ายเข้าระบบจริง (เฉพาะเมื่อผลวิวัฒน์ดีกว่าเดิม)
                |🚫 ห้ามตั้ง apply=on อัตโนมัติหลังงาน evolve เสร็จ และห้ามเรียกซ้ำเพื่อ apply จากผลของ tool เอง — ใช้ apply=on เฉพาะเมื่อผู้ใช้สั่ง "บันทึก params" / "เอาผล evolution ไปใช้" / "apply ผลที่วิวัฒน์" อย่างชัดเจนเท่านั้น
                |⭐ ถ้าผู้ใช้ยังไม่ได้สั่งบันทึก ให้ใช้ apply=off และรอคำสั่งใหม่ (ห้ามใช้ remember_fact แทน เพราะ remember_fact ไม่ได้ apply params เข้าระบบจริง)""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT, EURUSD"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h). ใช้ all เพียงครั้งเดียวเมื่อต้องการวิวัฒน์ครบ 15m/1h/4h — ห้ามเรียก tool แยกทีละ TF สำหรับคำสั่งเดียวกัน",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D", "all")
                    ),
                    "strategy" to ParameterProperty(
                        type = "STRING",
                        description = "กลยุทธ์เฉพาะตัว (default all = ทั้ง 8)",
                        enum = listOf("all", "tsmom", "trend", "reversal", "donchian", "w52high", "ema1460", "utbot", "threebar")
                    ),
                    "apply" to ParameterProperty(
                        type = "STRING",
                        description = "บันทึก params สุดท้ายเข้าระบบจริงถ้าดีกว่าเดิม (default off)",
                        enum = listOf("on", "off")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 14h. Mix Strategy config (ตั้งค่าโหวตผสมกลยุทธ์สำหรับ live alert) ──
        FunctionDeclaration(
            name = "trading_mix_config",
            description = """ตั้ง/ดู/ลบ Mix strategy config ต่อ symbol+timeframe — ใช้กับ live signal alert: เลือกกลยุทธ์ ≥2 ตัวมาโหวตรวมกัน (state-based) ออกสัญญาณเมื่อคะแนนโหวตข้ามเกณฑ์ min_votes
                |ใช้เมื่อผู้ใช้ถาม: "ผสมกลยุทธ์", "ตั้ง mix strategy", "mix signal", "ใช้ tsmom+trend+utbot ร่วมกัน", "ดู mix config"
                |action=set ต้องส่ง strategies เช่น "tsmom,trend,donchian,utbot" (min_votes optional, default = ครึ่งจำนวนกลยุทธ์ปัดขึ้น); action=show ดูค่าปัจจุบัน; action=clear ลบ""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTCUSDT"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h)",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D")
                    ),
                    "action" to ParameterProperty(
                        type = "STRING",
                        description = "set / show / clear (default show)",
                        enum = listOf("set", "show", "clear")
                    ),
                    "strategies" to ParameterProperty(
                        type = "STRING",
                        description = "เฉพาะ action=set — กลยุทธ์คั่น comma เช่น \"tsmom,trend,donchian,utbot\" (เลือกจาก tsmom,trend,reversal,donchian,w52high,ema1460,utbot,threebar; ≥2 ตัว)"
                    ),
                    "min_votes" to ParameterProperty(
                        type = "STRING",
                        description = "เฉพาะ action=set — เกณฑ์คะแนนโหวตขั้นต่ำ เช่น \"3\" (default = ครึ่งจำนวนกลยุทธ์ปัดขึ้น)"
                    )
                ),
                required = listOf("symbol")
            )
        ),

        // ── 15. Modern Technical Suite ─────────────────────────────────────
        
        FunctionDeclaration(
            name = "trading_harmonic_scan",
            description = """สแกนหารูปแบบ Harmonic (Bat, Gartley, Butterfly)
                |เน้นการหาจุดกลับตัวในโซน PRZ ที่สอดคล้องกับ Institutional Order Blocks
                |ใช้เมื่อผู้ใช้ถาม: "มี Harmonic pattern ไหม", "หาจุดกลับตัวสวยๆ" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h): 1m, 5m, 15m, 30m, 1h, 4h, 1D, 1W",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D", "1W", "m1", "m5", "m15", "m30", "h1", "h4", "d1", "w1")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        FunctionDeclaration(
            name = "trading_elliot_modern_analysis",
            description = """วิเคราะห์สถานะตลาดตามหลัก Elliot Wave สมัยใหม่
                |ระบุ Stage: IMPULSE (คลื่นส่ง), CORRECTIVE (คลื่นพัก), ACCUMULATION (สะสม)
                |พร้อม Swing-based Wave Counting, Momentum Bias, และ Graduated Confidence
                |ใช้เมื่อผู้ใช้ถาม: "ตอนนี้อยู่คลื่นไหน", "แนวโน้มเป็นยังไงต่อ" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "Symbol"),
                    "interval" to ParameterProperty(
                        type = "STRING",
                        description = "Timeframe (default 1h): 1m, 5m, 15m, 30m, 1h, 4h, 1D, 1W",
                        enum = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1D", "1W", "m1", "m5", "m15", "m30", "h1", "h4", "d1", "w1")
                    )
                ),
                required = listOf("symbol")
            )
        ),

        FunctionDeclaration(
            name = "trading_mt5_order",
            description = """Send a live order command to the MT5 core bridge.
                |Use for real execution: BUY or SELL.
                |Endpoint defaults to the mt5-core-server at http://127.0.0.1:8090/api/mt5/order;
                |at runtime the app rewrites this with the user's configured bridge URL.""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "BUY or SELL", enum = listOf("BUY", "SELL")),
                    "symbol" to ParameterProperty("STRING", "Trading symbol, e.g., XAUUSD, EURUSD, BTCUSD"),
                    "volume" to ParameterProperty("NUMBER", "Lot size, e.g., 0.01"),
                    "sl" to ParameterProperty("NUMBER", "Stop loss price (optional)"),
                    "tp" to ParameterProperty("NUMBER", "Take profit price (optional)"),
                    "comment" to ParameterProperty("STRING", "Optional order comment"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = listOf("action", "symbol", "volume")
            )
        ),

        FunctionDeclaration(
            name = "trading_mt5_close_position",
            description = """Send a close-position command to the MT5 core bridge.
                |Can close by ticket or close all positions for a symbol.
                |Endpoint defaults to http://127.0.0.1:8090/api/mt5/close; rewritten
                |at runtime with the configured bridge URL.""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Trading symbol, e.g., XAUUSD"),
                    "ticket" to ParameterProperty("STRING", "Optional position ticket"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        FunctionDeclaration(
            name = "trading_mt5_modify_position",
            description = """Update the Stop-Loss / Take-Profit of an EXISTING open position
                |without closing it. Use this to move SL to break-even, trail a stop, or adjust TP.
                |Identify the position by `ticket` OR by `symbol` (first match is modified).
                |At least one of `sl` / `tp` must be provided; pass 0 to clear a value.""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Trading symbol, e.g., XAUUSD"),
                    "ticket" to ParameterProperty("STRING", "Position ticket (preferred over symbol)"),
                    "sl" to ParameterProperty("NUMBER", "New Stop-Loss price (0 to clear)"),
                    "tp" to ParameterProperty("NUMBER", "New Take-Profit price (0 to clear)"),
                    "comment" to ParameterProperty("STRING", "Optional comment"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ══════════════════════════════════════════════════════════════════════════
        // ██  MT5 CORE AGENT TOOLS — ดึงข้อมูลจาก MT5 Broker โดยตรง  ██
        // ══════════════════════════════════════════════════════════════════════════

        // ── MT5-1. Account Info ─────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_account_info",
            description = """ดึงข้อมูลบัญชี MT5 จากโบรกเกอร์โดยตรง
                |แสดง: Equity, Balance, Margin, Free Margin, Margin Level %, Profit/Loss
                |ใช้เมื่อถาม: "ดูบัญชี MT5", "equity เท่าไหร่", "margin เหลือเท่าไหร่"
                |ข้อมูลมาจาก broker จริงเสมอ ไม่ใช่ TradingView""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-2. List Positions ──────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_list_positions",
            description = """ลิสต์ positions ที่เปิดอยู่ทั้งหมดใน MT5 จากโบรกเกอร์
                |แต่ละ position แสดง: symbol, ticket, side (BUY/SELL), volume, priceOpen, sl, tp, profit, swap
                |สามารถ filter ด้วย symbol ได้
                |ใช้เมื่อถาม: "position ที่เปิดอยู่", "ลิสต์ออเดอร์", "XAUUSD มีกี่ lot" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Filter by symbol, e.g., XAUUSD (optional — blank = all)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-3. List Orders ─────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_list_orders",
            description = """ลิสต์ pending orders (ออเดอร์ที่ยังไม่ fill) ใน MT5 จากโบรกเกอร์
                |แสดง: symbol, ticket, type (BUY_LIMIT/SELL_STOP/etc.), volume, price, sl, tp
                |ใช้เมื่อถาม: "มี pending orders ไหม", "ดู limit orders" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Filter by symbol (optional)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-4. List History ─────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_list_history",
            description = """ดูประวัติการเทรดจาก MT5 broker (history deals/trades ที่ปิดแล้ว)
                |แสดง: symbol, ticket, side, volume, price, profit, commission, swap, fee
                |ใช้เพื่อ review ผลลัพธ์การเทรดที่ผ่านมา หรือคำนวณสถิติ win/loss
                |ใช้เมื่อถาม: "ประวัติเทรดวันนี้", "deal ที่ปิดไปแล้ว", "ผลกำไร/ขาดทุน" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Filter by symbol (optional)"),
                    "limit" to ParameterProperty("NUMBER", "จำนวน deals ที่ต้องการดู (default 100, max 500)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-5. Candles (OHLC) from Broker ──────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_candles",
            description = """ดึงข้อมูลแท่งเทียน (OHLCV) จาก MT5 broker โดยตรง
                |ข้อมูลจะตรงกับที่โบรกเกอร์ forex ให้ (spread, timing อาจต่างจาก TradingView)
                |ใช้สำหรับวิเคราะห์ราคา, คำนวณอินดิเคเตอร์, หาจุดเข้า/ออก
                |ใช้เมื่อถาม: "ดูกราฟ XAUUSD", "ดึง candle H1", "วิเคราะห์ราคาจาก broker" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Trading symbol, e.g., XAUUSD, EURUSD, BTCUSD"),
                    "timeframe" to ParameterProperty("STRING", "Timeframe: M1, M5, M15, M30, H1, H4, D1, W1, MN1",
                        enum = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1", "W1", "MN1")),
                    "count" to ParameterProperty("NUMBER", "จำนวนแท่งเทียน (default 300, max 2000)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = listOf("symbol", "timeframe")
            )
        ),

        // ── MT5-5b. Broker Technical Analysis ──────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_analyze",
            description = """วิเคราะห์ Technical Analysis และ SMC (FVG, OB, Liquidity) จาก broker candles จริงโดยตรง (ไม่ใช่ TradingView)
                |คำนวณ: Regime, Bias, SMC (Order Blocks, FVG, Liquidity Sweeps), ATR, RSI, MACD
                |สรุป signals และแสดง 20 แท่งล่าสุดแบบ compact เพื่อ pattern reading
                |ใช้เมื่อถาม: "วิเคราะห์ mt5 xauusd", "วิเคราะห์ smc mt5", "ดู indicator broker"
                |⚡ ควรเรียกแทน trading_mt5_candles เสมอเมื่อต้องการ analysis จริง""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"    to ParameterProperty("STRING", "Trading symbol เช่น XAUUSD, EURUSD, BTCUSD"),
                    "timeframe" to ParameterProperty("STRING", "Timeframe: M1, M5, M15, M30, H1, H4, D1, W1",
                        enum = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1", "W1")),
                    "count"     to ParameterProperty("NUMBER", "จำนวน candle ที่ใช้วิเคราะห์ (default 180, min 60, max 500)"),
                    "endpoint"  to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = listOf("symbol", "timeframe")
            )
        ),

        // ── MT5-6. Symbol Info ──────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_symbol_info",
            description = """ดูข้อมูลรายละเอียดของ symbol จาก MT5 broker
                |แสดง: spread, digits, contract size, volume min/max/step, tick size/value, margin required
                |ข้อมูล spec ตรงจากโบรกเกอร์ ใช้คำนวณ position sizing ได้แม่นยำ
                |ระบุ symbol= เพื่อดูเฉพาะ symbol นั้น ถ้าไม่ระบุจะแสดงรายชื่อ symbols ทั้งหมด
                |ใช้เมื่อถาม: "spread XAUUSD เท่าไหร่", "contract size", "symbol info"
                |⚠ หากไม่แน่ใจว่าโบรกใช้ชื่ออะไร ให้เรียก trading_mt5_symbol_search ก่อน""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol"   to ParameterProperty("STRING", "ชื่อ symbol ที่ต้องการดู spec (optional — ถ้าไม่ระบุจะแสดง list ทั้งหมด)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-6b. Symbol Search & Validate ──────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_symbol_search",
            description = """🔍 ค้นหาและตรวจสอบชื่อ symbol ที่ถูกต้องในโบรกเกอร์ MT5
                |ปัญหาที่แก้: แต่ละโบรกมีชื่อ symbol ต่างกัน เช่น BTCUSD อาจชื่อ XBTUSD ในบางโบรก
                |ค้นหาได้ทั้ง ชื่อ symbol (เช่น btc, gold, xau) และ คำอธิบาย (description) เช่น "bitcoin", "dollar index"
                |ผลลัพธ์บอกว่า: exact=true ถ้ามีชื่อนั้นจริง, suggested=ชื่อที่ใกล้เคียงที่สุด
                |⚡ ควรเรียกเมื่อผู้ใช้ถามเรื่อง symbol ของโบรกเกอร์โดยตรง
                |🚫 ห้ามเรียกเพื่อเตรียม trading_signal_alert — Signal Alert ไม่ต้อง probe MT5 ก่อน เพราะ TradingSignalMarketDataRouter จะเลือกแหล่งแท่งเทียนเอง: DEMO/PAPER=TradingView, LIVE+connected=MT5, LIVE+offline=TradingView
                |ใช้เมื่อ: "ค้นหา symbol bitcoin", "โบรกนี้มี BTCUSD ไหม", "gold ชื่อว่าอะไรในโบรก" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "q"        to ParameterProperty("STRING", "คำค้นหา — ชื่อ symbol หรือ keyword จาก description เช่น 'btc', 'gold', 'bitcoin', 'nasdaq'"),
                    "limit"    to ParameterProperty("STRING", "จำนวนผลลัพธ์สูงสุด (default 15, max 50)"),
                    "validate" to ParameterProperty("STRING", "true = ตรวจสอบว่า q เป็นชื่อ exact ของโบรกหรือไม่"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = listOf("q")
            )
        ),

        // ── MT5-7. Close All Positions ──────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_close_all",
            description = """ปิด positions ทั้งหมด หรือเฉพาะ symbol ที่ระบุ
                |ดึงรายการ positions → loop ปิดทีละตัว → รายงานผลสรุป
                |ใช้เมื่อ: "ปิดออเดอร์ทั้งหมด", "close all XAUUSD", "ปิดพอร์ต" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Filter by symbol — blank = close ALL positions (very dangerous)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-8. Break-Even All ──────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_break_even_all",
            description = """ย้าย Stop-Loss ของ positions ที่กำไรอยู่มาที่จุด break-even (ราคาเปิด)
                |จะดำเนินการเฉพาะ positions ที่มี profit > 0 และ priceOpen > 0
                |ใช้เมื่อ: "break-even ทั้งหมด", "ย้าย SL มาต้นทุน", "ล็อคกำไร" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Filter by symbol (optional — blank = all profitable positions)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-9. Full Snapshot ────────────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_snapshot",
            description = """ดึง snapshot รวมครั้งเดียว: account + positions + orders + history
                |ประหยัดการเรียก API เพราะดึงทุกอย่างรวมกัน (delta-based)
                |ใช้เพื่อดูภาพรวมพอร์ตทั้งหมดในครั้งเดียว
                |ใช้เมื่อถาม: "ดูพอร์ต MT5", "สรุปสถานะทั้งหมด", "snapshot" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "limit" to ParameterProperty("NUMBER", "จำนวน history rows (default 200)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-10. Trade Actions Audit ─────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_trade_actions",
            description = """ดูประวัติ trade actions ที่ JARVIS เคยส่งไปยัง MT5 (audit log)
                |แสดง: order/close/modify actions, สถานะ, timestamp, request/response
                |ใช้ตรวจสอบว่า AI เคยสั่งอะไรไปบ้าง
                |ใช้เมื่อถาม: "ดูประวัติการสั่งเทรด", "JARVIS เคยสั่งอะไรไปบ้าง" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ══════════════════════════════════════════════════════════════════════════
        // ██  MT5 ADVANCED INTELLIGENCE — วิเคราะห์ขั้นสูงจากข้อมูล Broker  ██
        // ══════════════════════════════════════════════════════════════════════════

        // ── MT5-ADV-1. Market Scanner ──────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_market_scanner",
            description = """สแกนหา symbols ที่น่าสนใจจาก MT5 broker (ไม่ใช่ TradingView)
                |ดึง candle data จาก broker โดยตรง แล้ววิเคราะห์หาจุดเข้าเทรดด้วย AI
                |ตรวจ: RSI oversold/overbought, Bollinger squeeze, Volume breakout, EMA crossover
                |ใช้เมื่อถาม: "หา symbol น่าเทรด", "สแกนตลาด MT5", "มีจุดเข้าไหม"
                |symbols ที่สแกนมาจากรายการที่โบรกเกอร์ให้ ไม่ใช่ TradingView""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbols" to ParameterProperty("STRING", "Comma-separated symbols to scan, e.g., XAUUSD,EURUSD,GBPUSD (blank = use broker's available symbols)"),
                    "timeframe" to ParameterProperty("STRING", "Timeframe: M1, M5, M15, M30, H1, H4, D1, W1",
                        enum = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1", "W1")),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-ADV-2. Correlation Radar ───────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_correlation_radar",
            description = """วิเคราะห์ความสัมพันธ์ (Correlation) ระหว่าง symbols จากข้อมูล candle ของ broker โดยตรง
                |คำนวณ Pearson correlation จาก close prices ของแต่ละ symbol
                |ใช้ดูว่า symbols เคลื่อนที่ไปทิศทางเดียวกัน (1.0) หรือสวนทาง (-1.0)
                |ใช้เมื่อถาม: "XAUUSD กับ EURUSD สัมพันธ์กันไหม", "correlation ในพอร์ต"
                |ข้อมูลจาก broker จริง ไม่ใช่ Yahoo Finance""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbols" to ParameterProperty("STRING", "Comma-separated symbols, e.g., XAUUSD,EURUSD,GBPUSD,USDJPY"),
                    "timeframe" to ParameterProperty("STRING", "Timeframe: M1, M5, M15, M30, H1, H4, D1, W1 (default H1)",
                        enum = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1", "W1")),
                    "bars" to ParameterProperty("NUMBER", "จำนวนแท่งเทียน (default 200)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = listOf("symbols")
            )
        ),

        // ── MT5-ADV-3. Sentiment Gauge ─────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_sentiment_gauge",
            description = """วัดจิตวิทยา/สุขภาพพอร์ต MT5 จากข้อมูล broker โดยตรง
                |วิเคราะห์: Net exposure (long vs short), Drawdown %, Win rate, Average profit/loss ratio
                |Risk score, Margin health, Overtrading detection, Emotional trading signals
                |ใช้เมื่อถาม: "สุขภาพพอร์ตเป็นยังไง", "กลัวหรือโลภ", "over-trade ไหม"
                |ข้อมูลจากบัญชีจริง ไม่ใช่ sentiment ของ Reddit""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-ADV-4. Institutional Flow ──────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_institutional_flow",
            description = """วิเคราะห์ทิศทางเจ้ามือ/สถาบัน จากข้อมูล candle+volume ของ broker โดยตรง
                |ตรวจ: Volume profile, Large candle detection, Absorption patterns, Price-Volume divergence
                |Accumulation vs Distribution, Smart money footprint, Whale activity indicators
                |ใช้เมื่อถาม: "เจ้ามือกำลัง buy หรือ sell", "institutional flow XAUUSD"
                |ข้อมูล volume จาก broker จริง — ใกล้เคียง tick volume สุทธิ""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "symbol" to ParameterProperty("STRING", "Symbol to analyze, e.g., XAUUSD"),
                    "timeframe" to ParameterProperty("STRING", "Timeframe: M1, M5, M15, M30, H1, H4, D1, W1",
                        enum = listOf("M1", "M5", "M15", "M30", "H1", "H4", "D1", "W1")),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = listOf("symbol")
            )
        ),

        // ── MT5-ADV-5. Economic Radar ──────────────────────────────────────────
        FunctionDeclaration(
            name = "trading_mt5_economic_radar",
            description = """วิเคราะห์ข่าวเศรษฐกิจ + ผลกระทบต่อ positions ที่เปิดอยู่ใน MT5
                |ดึงปฏิทินเศรษฐกิจ แล้ว map กับ positions/symbols ที่เปิดอยู่จริง
                |ให้คะแนนความเสี่ยง: High/Medium/Low สำหรับแต่ละ position
                |แนะนำ: ควรปิด/ลด lot/ย้าย SL ก่อนข่าวหรือไม่
                |ใช้เมื่อถาม: "มีข่าวอะไรกระทบพอร์ต", "ควรปิดก่อนข่าวไหม" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),

        // ── MT5-ADV-6. Trade Journal (Auto-Trading) ────────────────────────────
        // หมายเหตุ: registry/executor เปิดใช้ tool นี้อยู่แล้ว แต่เคยไม่มี declaration
        // ทำให้ Gemini มองไม่เห็น — เพิ่มกลับเข้ามา
        FunctionDeclaration(
            name = "trading_mt5_trade_journal",
            description = """อ่าน Trade Journal ของระบบ Auto-Trading บน MT5 bridge
                |type=decision → journal การตัดสินใจล่าสุด, type=management → บันทึกการจัดการ positions, type=performance → สถิติผลงาน
                |ใช้เมื่อถาม: "bot เทรดอะไรไปบ้าง", "ผลงาน auto trading", "ทำไม bot เข้าออเดอร์นี้" """.trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "type" to ParameterProperty("STRING", "ชนิด journal",
                        enum = listOf("decision", "management", "performance")),
                    "limit" to ParameterProperty("NUMBER", "จำนวนรายการ (default 50)"),
                    "endpoint" to ParameterProperty("STRING", "Optional bridge URL override")
                ),
                required = emptyList()
            )
        ),
        FunctionDeclaration(
            name = "automation_manage_alerts",
            description = """จัดการการแจ้งเตือนอัตโนมัติ (Price Alerts / Condition Alerts)
                |สร้าง แก้ไขค่า เปลี่ยนชื่อ ลบ หรือดูรายการการแจ้งเตือนที่กำลังทำงาน
                |ใช้เมื่อผู้ใช้ต้องการ: "เฝ้าทองถ้าถึง 4100 บอกฉัน", "แจ้งเตือนเมื่อ RSI ต่ำกว่า 30", "แก้ alert ID 5 เป็นราคา 4200", "เปลี่ยนชื่อ alert ID 5 เป็น ทองทะลุเป้า", "ลบ alert หมายเลข 5"
                |[สำคัญ] ตั้งได้เฉพาะ tool_name/field ที่ background ดึงค่าได้จริง:
                |- trading_price: price, change, change_pct, prev_close, high_52w, low_52w, direction
                |- trading_indicators ⭐ (คำนวณจากแท่งเทียนเอง — เลือก TF ด้วย symbol@TF เช่น XAUUSD@15m): close, ema20, ema50, ema200, ema_cross_state (GOLDEN_CROSS/DEATH_CROSS/BULLISH/BEARISH), ema50_200_spread, ema20_50_spread, rsi14, macd, macd_signal, macd_hist, stoch_k, stoch_d, cci20, bb_upper, bb_basis, bb_lower, bb_width, atr14
                |- trading_smc ⭐ (Smart Money Concepts — เลือก TF ด้วย symbol@TF): close, smc_zone (PREMIUM/DISCOUNT/EQUILIBRIUM), smc_zone_pct, smc_trend, smc_last_event, smc_structure_high/low, smc_equilibrium, smc_premium_bot, smc_discount_top, bull_ob_dist, bear_ob_dist, fvg_dist, liq_above_dist, liq_below_dist, liq_above_stars, liq_below_stars, attack_force, atr
                |- trading_smc_flow ⭐ (สัญญาณ SMC Flow System — เลือก TF ด้วย symbol@TF): smc_signal, smc_recipes, ema14_60_signal, ema14_60_cross, ema14_60_state, ema14, ema60, ema14_60_spread, utbot_signal, utbot_trend, utbot_stop, structure_bias, in_fib_golden, fib_golden_top, fib_golden_bottom, fib_swing_high, fib_swing_low, close
                |- trading_strategy_signal ⭐ (กลยุทธ์จาก Strategy Library — เลือก TF ด้วย symbol@TF): consensus_signal, consensus_score, tsmom_signal, trend_signal, reversal_signal, donchian_signal, w52_signal, trend_state, tsmom_roc_pct, reversal_rsi, donchian_upper, donchian_mid, donchian_lower, w52_high, w52_proximity_pct, close
                |- trading_signal_alert 📡 (สัญญาณเทรด "ที่เพิ่งเกิด" จาก Unified SMC Multi-TF (15m) + Momentum/Reversal + SMC Engine — เลือก TF ด้วย symbol@TF เช่น XAUUSD@15m): ตั้ง signal_buy >= 1 (แจ้งเมื่อมีสัญญาณ BUY ใหม่) หรือ signal_sell >= 1 — เมื่อสัญญาณเกิด ระบบส่ง payload ครบ (เหตุผล, Entry/SL/TP เฉพาะกลยุทธ์, RR, บริบทกราฟ) ให้ AI quick-check ก่อนแจ้งผู้ใช้ — ใช้เมื่อผู้ใช้ขอ "แจ้งเตือนเมื่อมีสัญญาณ Buy/Sell", "ตั้ง signal alert ทอง 15m"
                |- trading_technical_analysis (เลือก TF ด้วย symbol@TF เช่น XAUUSD@15m, default 1h): close, RSI, MACD.macd, MACD.signal, BB.basis, ATR, ADX, Recommend.All, recommend_score, signal, volume
                |- trading_deep_analysis_suite (วิเคราะห์ 5 มิติ — เลือก TF ด้วย symbol@TF): summaryScore, lsdState, lsdConfluenceTF, deltaLabel, deltaValue, fiboScore, momentum, isSqueeze, close
                |- trading_sentiment: sentiment_score, bullish_posts, bearish_posts, posts_analyzed, sentiment_label ⚠️ Reddit มักตอบ 403 ช่วงนี้ — หลีกเลี่ยงถ้าไม่จำเป็น
                |- trading_fear_greed: value, classification
                |- trading_crypto_overview: btc_dominance, eth_dominance, market_cap_change_24h, total_market_cap_usd, total_volume_24h_usd, active_cryptocurrencies, markets
                |เมื่อเข้าเงื่อนไข ระบบจะปลุก AI มาสรุปบริบทก่อนแจ้งเตือนผู้ใช้ (notification มีปุ่ม หยุดแจ้งเตือน/แจ้งเตือนซ้ำ)
                |[โหมดส่งแจ้งเตือน — delivery] "ai" (default) = alert → AI วิเคราะห์/quick-check → ผู้ใช้ | "direct" = alert → notification + ส่งเข้าแชทโดยตรง ไม่เรียก AI (ประหยัดโทเคน)""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "create | update | rename | delete | list", enum = listOf("create", "update", "rename", "delete", "list")),
                    "name" to ParameterProperty("STRING", "ชื่อ alert (สำหรับ create) — rename ใช้เป็นชื่อใหม่"),
                    "symbol" to ParameterProperty("STRING", "Symbol เช่น XAUUSD, BTC-USD (สำหรับ create) — รองรับ suffix @TF เช่น XAUUSD@15m"),
                    "timeframe" to ParameterProperty("STRING", "Timeframe ที่ต้องการเฝ้า: m1 | m5 | m15 | 30m | h1 | h4 | D1 | W1 | all (หรือ 1m, 5m, 15m, 1h, 4h, 1D, 1W)"),
                    "tool_name" to ParameterProperty("STRING", "trading_price | trading_indicators | trading_smc | trading_smc_flow | trading_strategy_signal | trading_signal_alert | trading_technical_analysis | trading_sentiment | trading_fear_greed | trading_crypto_overview | trading_deep_analysis_suite (default: trading_price)"),
                    "condition_field" to ParameterProperty("STRING", "ฟิลด์ที่ตรวจสอบ — ต้องอยู่ในรายการของ tool_name นั้น (default: price)"),
                    "condition_operator" to ParameterProperty("STRING", "> | < | >= | <= | == | contains (default: >=)"),
                    "condition_value" to ParameterProperty("STRING", "ค่าเปรียบเทียบ เช่น 4100, 30, 85 (สำหรับ update ใช้เป็นค่าใหม่)"),
                    "delivery" to ParameterProperty("STRING", "โหมดส่งแจ้งเตือน: ai = AI วิเคราะห์ก่อนแจ้ง (default) | direct = ส่ง notification+แชทโดยตรง ไม่เรียก AI (ประหยัดโทเคน)", enum = listOf("ai", "direct")),
                    "voice" to ParameterProperty("STRING", "เปิดแจ้งเตือนด้วยเสียงพูด: true (default) หรือ false"),
                    "interval_minutes" to ParameterProperty("NUMBER", "ความถี่ตรวจสอบเป็นนาที (default: 15, min: 1, max: 1440)"),
                    "alert_id" to ParameterProperty("NUMBER", "ID ของ alert ที่ต้องการลบ/แก้ไข (สำหรับ delete/update)")
                ),
                required = listOf("action")
            )
        ),

        // ── AUTOMATION-2. Manage Schedule ──────────────────────────────────────
        FunctionDeclaration(
            name = "automation_manage_schedule",
            description = """จัดการงานตามเวลา (Scheduled Tasks) — ปลุก AI ทำตาม prompt เมื่อถึงเวลา
                |สร้าง ลบ หรือดูรายการงานตามเวลา
                |ใช้เมื่อผู้ใช้ต้องการ: "ทุกเช้า 8 โมงสรุปข่าวให้หน่อย", "อีก 30 นาทีเตือนฉัน", "ลบงานหมายเลข 3"""".trimMargin(),
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "action" to ParameterProperty("STRING", "create | delete | list", enum = listOf("create", "delete", "list")),
                    "name" to ParameterProperty("STRING", "ชื่องาน (สำหรับ create)"),
                    "prompt" to ParameterProperty("STRING", "คำสั่งที่จะให้ AI ทำเมื่อถึงเวลา (สำหรับ create)"),
                    "schedule_type" to ParameterProperty("STRING", "one_time | daily (default: one_time)"),
                    "time_hhmm" to ParameterProperty("STRING", "เวลาในรูปแบบ HH:mm เช่น 08:00 (สำหรับ daily)"),
                    "run_at" to ParameterProperty("STRING", "วันเวลาในรูปแบบ ISO เช่น 2026-08-03 20:00 (สำหรับ one_time)"),
                    "in_minutes" to ParameterProperty("NUMBER", "จำนวนนาทีจากตอนนี้ (สำหรับ one_time, ทางเลือกแทน run_at)"),
                    "task_id" to ParameterProperty("NUMBER", "ID ของงานที่ต้องการลบ (สำหรับ delete)")
                ),
                required = listOf("action")
            )
        )
    )
}
