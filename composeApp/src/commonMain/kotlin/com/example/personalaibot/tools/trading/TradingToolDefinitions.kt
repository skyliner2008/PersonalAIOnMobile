package com.example.personalaibot.tools.trading

import com.example.personalaibot.tools.FunctionDeclaration
import com.example.personalaibot.tools.FunctionParameters
import com.example.personalaibot.tools.ParameterProperty

/**
 * TradingToolDefinitions — รายการ Tool definitions ทั้งหมดสำหรับ Gemini Function Calling
 *
 * Tools ที่รองรับ:
 *  1. trading_price           — ราคา real-time จาก Yahoo Finance
 *  2. trading_market_snapshot — ภาพรวมตลาดโลก
 *  3. trading_top_gainers     — หุ้น/Crypto ที่ขึ้นมากที่สุด
 *  4. trading_top_losers      — หุ้น/Crypto ที่ลงมากที่สุด
 *  5. trading_technical_analysis — TA ครบถ้วนสำหรับ symbol เดี่ยว
 *  6. trading_multi_timeframe — วิเคราะห์หลาย timeframe (W→D→4H→1H→15m)
 *  7. trading_bollinger_scan  — Bollinger squeeze scanner
 *  8. trading_oversold_scan   — RSI < 30 scanner
 *  9. trading_overbought_scan — RSI > 70 scanner
 * 10. trading_volume_breakout — Volume breakout scanner
 * 11. trading_sentiment       — Reddit sentiment analysis
 * 12. trading_news            — Financial news จาก RSS
 * 13. trading_combined        — TA + Sentiment + News รวมกัน (Power Tool)
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
                        description = "Yahoo Finance symbol เช่น AAPL, BTC-USD, ETH-USD, ^GSPC, GC=F, EURUSD=X"
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
                    "symbol"   to ParameterProperty("STRING", "Symbol เช่น BTCUSDT, XAUUSD (Gold), EURUSD, AAPL, THYAO"),
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, NASDAQ, NYSE, OANDA (FX), TVC (Gold)"),
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
                    "exchange" to ParameterProperty("STRING", "Exchange: BINANCE, NASDAQ, OANDA, TVC ฯลฯ")
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
        )
    )
}
