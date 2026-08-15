# -*- coding: utf-8 -*-
"""สร้างคู่มือการใช้งาน Tools ของ Personal AI Bot (PDF, ภาษาไทย)"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, PageBreak,
                                KeepTogether, Table, TableStyle)

pdfmetrics.registerFont(TTFont('Thai', r'C:\Windows\Fonts\LeelawUI.ttf'))
pdfmetrics.registerFont(TTFont('Thai-Bold', r'C:\Windows\Fonts\LeelaUIb.ttf'))
pdfmetrics.registerFontFamily('Thai', normal='Thai', bold='Thai-Bold',
                              italic='Thai', boldItalic='Thai-Bold')

NAVY = HexColor('#1a3a5c'); GRAY = HexColor('#555555'); LIGHT = HexColor('#f2f5f8')
ACCENT = HexColor('#0e6e8c')

S_NAME = ParagraphStyle('name', fontName='Thai-Bold', fontSize=11.5, leading=15,
                        textColor=NAVY, spaceBefore=10, spaceAfter=2)
S_DESC = ParagraphStyle('desc', fontName='Thai', fontSize=10, leading=14,
                        textColor=HexColor('#333333'))
S_EX = ParagraphStyle('ex', fontName='Thai', fontSize=9.5, leading=13.5,
                      textColor=GRAY, leftIndent=14)
S_H1 = ParagraphStyle('h1', fontName='Thai-Bold', fontSize=16, leading=22,
                      textColor=NAVY, spaceBefore=6, spaceAfter=8)
S_BODY = ParagraphStyle('body', fontName='Thai', fontSize=10.5, leading=15,
                        textColor=HexColor('#333333'))
S_TITLE = ParagraphStyle('title', fontName='Thai-Bold', fontSize=26, leading=34,
                         textColor=NAVY, alignment=1)
S_SUB = ParagraphStyle('sub', fontName='Thai', fontSize=13, leading=18,
                       textColor=GRAY, alignment=1)

def esc(t): return t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')

def tool_block(name, desc, chat, voice):
    return KeepTogether([
        Paragraph(esc(name), S_NAME),
        Paragraph(esc(desc), S_DESC),
        Paragraph('พิมพ์ในแชท: <font color="#0e6e8c">"%s"</font>' % esc(chat), S_EX),
        Paragraph('พูดด้วยเสียง: <font color="#7a4f01">"%s"</font>' % esc(voice), S_EX),
        Spacer(1, 4),
    ])

SECTIONS = [
("1. ราคาและภาพรวมตลาด", [
("trading_price", "ราคาเรียลไทม์ของสินทรัพย์ (ทอง หุ้น คริปโต forex) พร้อมเปลี่ยนแปลงและแหล่งข้อมูล",
 "ราคาทองล่าสุด", "จาวิส ราคาทองตอนนี้เท่าไร"),
("trading_market_snapshot", "ภาพรวมตลาดครั้งเดียว: ทอง เงิน น้ำมัน ดัชนีหลัก คริปโต",
 "ขอภาพรวมตลาดตอนนี้", "สรุปภาพรวมตลาดให้หน่อย"),
("trading_top_gainers", "หุ้น/คริปโตที่ขึ้นแรงสุดของวัน",
 "หุ้นขึ้นแรงสุดวันนี้", "วันนี้ตัวไหนขึ้นแรงสุด"),
("trading_top_losers", "หุ้น/คริปโตที่ลงแรงสุดของวัน",
 "คริปโตลงแรงสุดวันนี้", "วันนี้ตัวไหนลงหนักสุด"),
("trading_sentiment", "สำรวจความรู้สึกตลาด (bullish/bearish) ของสินทรัพย์",
 "sentiment ทองตอนนี้เป็นยังไง", "ตลาดตอนนี้ bullish หรือ bearish"),
("trading_news", "ข่าวการเงินล่าสุดของสินทรัพย์/ตลาด",
 "ข่าวทองคำวันนี้", "มีข่าวอะไรเกี่ยวกับทองบ้าง"),
("trading_fear_greed", "ดัชนี Fear & Greed (คริปโต) — วัดความกลัว/โลภของตลาด",
 "fear greed index ตอนนี้", "ตอนนี้ตลาดกลัวหรือโลภ"),
("trading_crypto_overview", "ภาพรวมตลาดคริปโต: market cap, BTC dominance, volume",
 "ภาพรวมคริปโตวันนี้", "ตลาดคริปโตโดยรวมเป็นยังไง"),
("trading_macro_calendar", "ปฏิทินเหตุการณ์เศรษฐกิจสำคัญ (นอนฟาร์ม CPI FOMC ฯลฯ)",
 "สัปดาห์นี้มีข่าวเศรษฐกิจอะไร", "ช่วงนี้มีข่าวแรงๆ อะไรไหม"),
("trading_economic_data", "ข้อมูลเศรษฐกิจมหภาค (GDP, CPI, อัตราดอกเบี้ย) จาก FRED",
 "CPI สหรัฐล่าสุด", "เงินเฟ้ออเมริกาตอนนี้เท่าไร"),
("trading_correlation_matrix", "เมทริกซ์ความสัมพันธ์ราคาระหว่างสินทรัพย์หลายตัว",
 "ทองกับดอลลาร์สัมพันธ์กันไหม", "ทองกับบิทคอยน์ไปด้วยกันไหม"),
]),
("2. เทคนิคัลและอินดิเคเตอร์", [
("trading_technical_analysis", "วิเคราะห์เทคนิคัลครบชุด: RSI, MACD, EMA, Bollinger, Stochastic, ADX พร้อมสรุปสัญญาณ",
 "วิเคราะห์เทคนิคัล XAUUSD 1h", "วิเคราะห์เทคนิคัลทองให้หน่อย"),
("trading_multi_timeframe", "วิเคราะห์หลาย timeframe พร้อมกัน (15m/1h/4h/1D) ดูความสอดคล้องของเทรนด์",
 "เช็คทองหลาย timeframe หน่อย", "ทองในแต่ละ timeframe เป็นยังไงบ้าง"),
("trading_bollinger_scan", "สแกนหาสินทรัพย์ที่ราคาแตะ/ทะลุ Bollinger Bands",
 "สแกนหุ้นที่แตะ Bollinger ล่าง", "มีตัวไหนแตะโบลินเจอร์บ้าง"),
("trading_oversold_scan", "สแกนหาตัวที่ RSI ขายมากเกิน (oversold)",
 "หาตัว oversold ให้หน่อย", "มีตัวไหน oversold น่าเด้งบ้าง"),
("trading_overbought_scan", "สแกนหาตัวที่ RSI ซื้อมากเกิน (overbought)",
 "หาตัว overbought หน่อย", "มีตัวไหนซื้อมากเกินแล้วบ้าง"),
("trading_volume_breakout", "หาจุด breakout พร้อม volume สนับสนุน",
 "หา breakout ที่มี volume", "มี breakout ตัวไหนน่าสนใจบ้าง"),
("trading_harmonic_scan", "สแกนรูปแบบ Harmonic (Bat, Gartley, Butterfly) หาจุดกลับตัว PRZ",
 "สแกน harmonic ทอง 1h", "มี harmonic pattern บนทองไหม"),
("trading_elliot_modern_analysis", "วิเคราะห์ Elliott Wave สมัยใหม่ นับคลื่นจากโครงสร้างราคา",
 "วิเคราะห์ elliot wave ทอง", "นับคลื่นเอลเลียตของทองให้หน่อย"),
("trading_position_sizing", "คำนวณขนาดไม้จากความเสี่ยง: units = (ทุน × risk%) / |entry − SL|",
 "คำนวณไม้ ทุน 10000 เสี่ยง 1% เข้า 4350 SL 4340", "ช่วยคำนวณขนาดไม้ให้หน่อย"),
]),
("3. SMC (Smart Money Concepts)", [
("trading_smc_analysis", "วิเคราะห์ SMC ครบ: โครงสร้างตลาด, OB, FVG, Liquidity, Premium/Discount พร้อม Bias",
 "วิเคราะห์ smc ทอง 15m", "วิเคราะห์ smc ทองให้หน่อย"),
("trading_smc_sweeps", "ตรวจจุดกวาด liquidity (stop hunt) ที่เพิ่งเกิด",
 "มี sweep บนทองไหม", "ทองมีการกวาด liquidity ไหม"),
("trading_smc_liquidity", "แมปโซน liquidity เหนือ/ใต้ราคา (EQH/EQL, session high/low)",
 "โซน liquidity ทองอยู่ตรงไหน", " liquidity ของทองอยู่แถวไหนบ้าง"),
("trading_smc_orderblocks", "หา Order Blocks ที่ยัง active (Demand/Supply zones)",
 "หา order block ทอง 1h", "มี order block น่าสนใจไหม"),
("trading_smc_structure", "อ่านโครงสร้างตลาด: BOS/CHoCH, swing high/low, เทรนด์",
 "โครงสร้างตลาดทองตอนนี้", "โครงสร้างทองเป็นขาขึ้นหรือขาลง"),
("trading_smc_flow", "SMC Flow System: EMA14/60 cross, UT Bot, confluence recipe A/B/C, Auto Fib golden zone พร้อมสัญญาณ Buy/Sell",
 "ขอสัญญาณ smc flow ทอง 15m", "เช็ค smc flow ทองให้หน่อย"),
]),
("4. กลยุทธ์และสัญญาณเทรด", [
("trading_strategy_signal", "สัญญาณจาก Strategy Library 5 กลยุทธ์ (TSMOM, Trend EMA50/200, Reversal, Donchian, 52W High) พร้อม consensus",
 "ขอสัญญาณกลยุทธ์ทอง 15m", "ตอนนี้กลยุทธ์ให้สัญญาณอะไรบ้าง"),
("trading_signal_stats", "สถิติสัญญาณ: source=backtest จำลอง win-rate ย้อนหลัง / source=live สถิติจาก alert ที่ยิงจริงพร้อมผล TP/SL",
 "วันนี้มี signal อะไรบ้าง โดน tp หรือ sl", "สถิติสัญญาณวันนี้เป็นยังไงบ้าง"),
("strategy_list", "แสดงรายการกลยุทธ์ทั้งหมดใน Strategy Library",
 "มีกลยุทธ์อะไรให้ใช้บ้าง", "เรามีกลยุทธ์อะไรบ้าง"),
("strategy_search", "ค้นหากลยุทธ์จากคีย์เวิร์ด/เงื่อนไข",
 "หากลยุทธ์ที่ใช้ RSI", "มีกลยุทธ์แนว mean reversion ไหม"),
("strategy_explain", "อธิบายกลยุทธ์ละเอียด: เงื่อนไขเข้า/ออก, ตรรกะ, จุดแข็งจุดอ่อน",
 "อธิบายกลยุทธ์ donchian หน่อย", "กลยุทธ์ donchian ทำงานยังไง"),
]),
("5. การวิเคราะห์ขั้นสูง", [
("trading_deep_analysis_suite", "ชุดวิเคราะห์เชิงลึกหลายมิติ (confluence score) — หนักแต่ครบ เหมาะกับงานวิเคราะห์จริงจัง",
 "วิเคราะห์เชิงลึกทองแบบเต็มสูตร", "ขอวิเคราะห์ทองแบบละเอียดที่สุด"),
("trading_combined", "รวมราคา + เทคนิคัล + sentiment + ข่าว ในคำสั่งเดียว",
 "สรุปทองแบบรวมทุกอย่าง", "อัปเดตทองแบบครบๆ ให้หน่อย"),
("trading_fundamental_analysis", "วิเคราะห์ปัจจัยพื้นฐาน (หุ้น/คริปโต)",
 "วิเคราะห์พื้นฐาน AAPL", "พื้นฐานของแอปเปิลเป็นยังไง"),
]),
("6. MT5 — บัญชีและคำสั่งเทรด", [
("trading_mt5_account_info", "ข้อมูลบัญชี MT5: ยอดเงิน, equity, margin, กำไรลอยตัว",
 "บัญชี mt5 ตอนนี้เป็นยังไง", "เช็คยอดบัญชีให้หน่อย"),
("trading_mt5_list_positions", "ดู position ที่เปิดอยู่ทั้งหมด",
 "มี position เปิดอยู่ไหม", "ตอนนี้ถือออเดอร์อะไรอยู่บ้าง"),
("trading_mt5_list_orders", "ดู pending orders ทั้งหมด",
 "มี pending order อะไรค้างไว้", "มีออเดอร์รออยู่ไหม"),
("trading_mt5_list_history", "ประวัติการเทรดที่ปิดแล้ว",
 "ประวัติเทรดวันนี้", "วันนี้ปิดออเดอร์อะไรไปบ้าง"),
("trading_mt5_order", "เปิดออเดอร์จริง (market/pending) พร้อม SL/TP — ต้องระบุ symbol, ฝั่ง, volume",
 "เปิด buy ทอง 0.01 ไม้ SL 4340 TP 4370", "เปิดออเดอร์บายทอง 0.01 ไม้"),
("trading_mt5_close_position", "ปิด position ตาม ticket/symbol",
 "ปิด position ทองทั้งหมด", "ปิดออเดอร์ทองให้หน่อย"),
("trading_mt5_modify_position", "แก้ SL/TP ของ position ที่เปิดอยู่",
 "เลื่อน SL ของทองไปที่ 4350", "ขยับสต็อปลอสให้หน่อย"),
("trading_mt5_close_all", "ปิดทุก position ที่เปิดอยู่",
 "ปิดออเดอร์ทั้งหมดเดี๋ยวนี้", "ปิดทุกออเดอร์เลย"),
("trading_mt5_break_even_all", "เลื่อน SL ทุก position ไปที่จุดคุ้มทุน (break-even)",
 "กันเหนียวทุกไม้", "ตั้ง break even ให้ทุกออเดอร์"),
("trading_mt5_candles", "ดึงแท่งเทียนจาก MT5 โดยตรง",
 "ดึงแท่งเทียน 1h ทองจาก mt5 100 แท่ง", "ขอแท่งเทียนทองจาก mt5"),
("trading_mt5_analyze", "วิเคราะห์สัญลักษณ์ด้วยข้อมูลจาก MT5",
 "วิเคราะห์ EURUSD จาก mt5", "วิเคราะห์ยูโรจาก mt5 หน่อย"),
("trading_mt5_symbol_info", "รายละเอียดสัญลักษณ์: spread, contract size, เวลาเทรด",
 "spread ทองตอนนี้เท่าไร", "เช็คสเปรดทองให้หน่อย"),
("trading_mt5_symbol_search", "ค้นหาสัญลักษณ์ที่โบรกเกอร์มีให้เทรด",
 "โบรกมีคู่เงินอะไรบ้าง", "หา symbol น้ำมันใน mt5"),
("trading_mt5_snapshot", "ภาพรวมบัญชี + positions + สรุปพอร์ตครั้งเดียว",
 "snapshot พอร์ตตอนนี้", "สรุปพอร์ตให้หน่อย"),
("trading_mt5_trade_actions", "ดำเนินการเทรดแบบรวม (ปิดบางส่วน/ย้าย SL เป็นชุด)",
 "จัดการออเดอร์ตามแผนที่วางไว้", "ทำตามแผนจัดการออเดอร์"),
("trading_mt5_trade_journal", "บันทึก/ดู trading journal ของบัญชี",
 "สรุป journal การเทรดสัปดาห์นี้", "เปิดเทรดเจอร์นัลให้หน่อย"),
]),
("7. MT5 — ข้อมูลเชิงลึก (Intelligence)", [
("trading_mt5_market_scanner", "สแกนตลาดทั้งหมดของโบรกหาโอกาสเทรด",
 "สแกนตลาดหาโอกาสหน่อย", "มีคู่ไหนน่าเทรดบ้างตอนนี้"),
("trading_mt5_correlation_radar", "เรดาร์ correlation ของสินทรัพย์ในบัญชี/วอชลิสต์",
 "เช็ค correlation ของคู่ที่ถืออยู่", "ออเดอร์ที่ถืออยู่ correlate กันไหม"),
("trading_mt5_sentiment_gauge", "เกจวัด sentiment จากข้อมูล MT5/ตลาด",
 "sentiment ตลาดตอนนี้", "วัด sentiment ให้หน่อย"),
("trading_mt5_institutional_flow", "ประเมินแรงซื้อขายเชิงสถาบัน (order flow proxy)",
 "institutional flow ทองเป็นยังไง", "มีแรงสถาบันเข้าทองไหม"),
("trading_mt5_economic_radar", "เรดาร์เหตุการณ์เศรษฐกิจที่กระทบสัญลักษณ์ในมือ",
 "มีข่าวอะไรกระทบออเดอร์ที่ถืออยู่ไหม", "เช็คข่าวเศรษฐกิจที่กระทบพอร์ต"),
]),
("8. การแจ้งเตือนและงานตามเวลา (Automation)", [
("automation_manage_alerts", "สร้าง/ลบ/แก้/ดูการแจ้งเตือนอัตโนมัติ — ราคา, อินดิเคเตอร์, SMC, signal เทรด; เลือกโหมด AI วิเคราะห์ก่อนแจ้ง หรือแจ้งตรง",
 "ตั้งแจ้งเตือนทองถ้าทะลุ 4400", "ตั้ง signal แจ้งเตือนทอง M15"),
("automation_manage_schedule", "ตั้งงานตามเวลา (ครั้งเดียว/รายวัน) ให้ AI ทำอัตโนมัติ เช่น สรุปตลาดเช้า",
 "ทุกวัน 8 โมงเช้า สรุปภาพรวมทองให้หน่อย", "ตั้งให้สรุปตลาดทุกเช้าตอนแปดโมง"),
]),
("9. กราฟและแดชบอร์ด", [
("chart_dashboard_control", "เปิด/ปรับกราฟ Chart Dashboard: symbol, timeframe, layout (rsi/macd/volume/full), overlay (ema, bb, donchian, smc, signals) — สั่งเปิดใหม่จะรีเซ็ตอินดิเคเตอร์เก่า",
 "เปิดกราฟ XAUUSD 1h ใส่ RSI กับ MACD", "เปิดกราฟทอง 15 นาที ใส่ smc"),
]),
("10. ไฟล์และเอกสาร", [
("file_list", "แสดงรายการไฟล์/โฟลเดอร์ในเครื่อง",
 "ดูไฟล์ใน Download หน่อย", "มีไฟล์อะไรในโฟลเดอร์ดาวน์โหลดบ้าง"),
("file_read", "อ่านเนื้อหาไฟล์ (txt, csv, json, pdf ฯลฯ)",
 "อ่านไฟล์ bill.txt ให้หน่อย", "อ่านไฟล์บิลให้ฟังหน่อย"),
("file_write", "สร้าง/เขียนไฟล์ใหม่ลงเครื่อง",
 "สร้างไฟล์ bill.txt บันทึกรายการนี้ลง Download", "บันทึกข้อมูลนี้เป็นไฟล์ให้หน่อย"),
("file_delete", "ลบไฟล์/โฟลเดอร์",
 "ลบไฟล์ test.txt ทิ้ง", "ลบไฟล์ทดสอบทิ้งให้หน่อย"),
("file_analyze", "วิเคราะห์ไฟล์เอกสาร/ตาราง สรุปเนื้อหา",
 "วิเคราะห์ไฟล์ report.pdf หน่อย", "สรุปเอกสารนี้ให้ฟังหน่อย"),
("file_move", "ย้าย/เปลี่ยนชื่อไฟล์",
 "ย้าย bill.txt ไปโฟลเดอร์ Documents", "ย้ายไฟล์บิลไปโฟลเดอร์เอกสาร"),
("file_search", "ค้นหาไฟล์ตามชื่อ/นามสกุล",
 "หาไฟล์ pdf ทั้งหมดใน Download", "หาไฟล์ pdf ให้หน่อย"),
]),
("11. กล้องและ Vision", [
("camera_analyze_scene", "เปิดกล้องวิเคราะห์ภาพที่เห็น (ตอบคำถามจากภาพจริงได้)",
 "เปิดกล้องดูหน่อย ตอนนี้เห็นอะไร", "เปิดตาดูสิ่งที่เห็นหน่อย"),
("camera_detect_objects", "ตรวจหาวัตถุเฉพาะที่ระบุในภาพ",
 "หาว่ามีแมวในภาพไหม", "มองหาโทรศัพท์ให้หน่อย"),
("camera_read_text", "อ่านข้อความจากภาพกล้อง (OCR) เช่น ป้าย เอกสาร สลิป",
 "อ่านข้อความบนกระดาษแผ่นนี้", "อ่านตัวหนังสือที่เห็นให้หน่อย"),
("camera_switch_provider", "สลับผู้ให้บริการวิเคราะห์ภาพ (on-device/cloud)",
 "สลับ vision ไปใช้ on-device", "เปลี่ยนโหมดการมองเห็น"),
("camera_switch_mode", "สลับโหมดกล้อง (หน้า/หลัง)",
 "สลับไปกล้องหน้า", "เปลี่ยนเป็นกล้องหลัง"),
]),
("12. ระบบและเครื่องมือเสริม", [
("search_web", "ค้นหาเว็บแบบเรียลไทม์ (ข่าวสาร ข้อมูลที่ไม่มีใน tools)",
 "ค้นหาข่าวล่าสุดเรื่องดอกเบี้ย Fed", "ค้นเว็บเรื่องทองคำล่าสุด"),
("system_create_agent_tool", "สร้าง custom tool ใหม่ด้วยคำสั่งภาษาคน (AI เขียน workflow ให้)",
 "สร้าง tool เช็คทองครบวงจรชื่อ custom_gold_check", "สร้าง tool ใหม่สำหรับเช็คทอง"),
("system_list_agent_tools", "แสดง custom tools ทั้งหมดที่สร้างไว้",
 "มี custom tool อะไรบ้าง", "ลิสต์ tool ที่สร้างไว้ให้หน่อย"),
("system_delete_agent_tool", "ลบ custom tool ที่สร้างไว้",
 "ลบ tool ชื่อ custom_gold_check", "ลบ tool ที่ไม่ใช้แล้วออก"),
("system_run_diagnostics", "ตรวจสุขภาพระบบทั้งหมด (API key, โมเดล, การเชื่อมต่อ, DB)",
 "ตรวจสอบระบบหน่อย", "เช็คสุขภาพระบบทั้งหมด"),
("system_check_connectivity", "เช็กการเชื่อมต่อเครือข่าย/API ภายนอก",
 "เช็คเน็ตกับ API หน่อย", "ตอนนี้เชื่อมต่อปกติไหม"),
("system_self_review", "AI อ่านสรุปความสามารถของตัวเองจาก README ให้ฟัง",
 "รีวิวตัวเองให้ฟังหน่อย", "แนะนำตัวเองหน่อยว่าทำอะไรได้บ้าง"),
("voice_set_profile", "(โหมด Live เท่านั้น) เปลี่ยนโปรไฟล์เสียงพูดของ AI — เพศ/น้ำเสียง/คำลงท้ายเปลี่ยนตามอัตโนมัติและจำไว้ถาวร",
 "(ใช้ผ่านเสียงในโหมด Live)", "เปลี่ยนเสียงเป็น Leda"),
]),
]

story = []
# ── Cover ──
story.append(Spacer(1, 5 * cm))
story.append(Paragraph("Personal AI Bot", S_TITLE))
story.append(Spacer(1, 0.4 * cm))
story.append(Paragraph("คู่มือการใช้งาน Tools ทั้งหมด", ParagraphStyle(
    't2', parent=S_TITLE, fontSize=20, textColor=ACCENT)))
story.append(Spacer(1, 1 * cm))
total = sum(len(t) for _, t in SECTIONS)
story.append(Paragraph("รวม %d tools ・ 12 หมวดหมู่" % total, S_SUB))
story.append(Paragraph("อัปเดต: 13 สิงหาคม 2026", S_SUB))
story.append(Spacer(1, 1.5 * cm))
story.append(Paragraph(
    "แต่ละ tool ประกอบด้วย: ชื่อ tool / รายละเอียดสั้น / ตัวอย่างพิมพ์ในแชท / ตัวอย่างพูดด้วยเสียง<br/>"
    "AI จะเลือก tool ที่เหมาะสมเองจากคำสั่งภาษาคน — ไม่จำเป็นต้องจำชื่อ tool", S_SUB))
story.append(PageBreak())

for title, tools in SECTIONS:
    story.append(Paragraph(esc(title), S_H1))
    story.append(Table([['']], colWidths=[16.5 * cm], rowHeights=[1],
                       style=TableStyle([('LINEBELOW', (0, 0), (-1, -1), 1.5, NAVY)])))
    story.append(Spacer(1, 6))
    for name, desc, chat, voice in tools:
        story.append(tool_block(name, desc, chat, voice))
    story.append(PageBreak())

def header_footer(canvas, doc):
    canvas.saveState()
    w, h = A4
    canvas.setFont('Thai', 8.5)
    canvas.setFillColor(GRAY)
    canvas.drawString(2.5 * cm, h - 1.4 * cm, "Personal AI Bot — คู่มือ Tools")
    canvas.line(2.5 * cm, h - 1.55 * cm, w - 2.5 * cm, h - 1.55 * cm)
    canvas.drawCentredString(w / 2, 1.2 * cm, "หน้า %d" % doc.page)
    canvas.restoreState()

def first_page(canvas, doc):
    pass

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                   'คู่มือการใช้งาน_Tools_PersonalAIBot.pdf')
out = os.path.abspath(out)
doc = SimpleDocTemplate(out, pagesize=A4, topMargin=2.2 * cm, bottomMargin=2 * cm,
                        leftMargin=2.5 * cm, rightMargin=2.5 * cm,
                        title="Personal AI Bot — คู่มือการใช้งาน Tools")
doc.build(story, onFirstPage=first_page, onLaterPages=header_footer)
print("OK", out)
