package com.example.personalaibot.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.personalaibot.ui.theme.JarvisTheme

// ─── Data Models ──────────────────────────────────────────────────────────────

data class ToolInfo(
    val name: String,
    val displayName: String,
    val category: ToolCategory,
    val icon: String,
    val shortDesc: String,
    val capabilities: List<String>,
    val examplePrompts: List<String>
)

enum class ToolCategory(val label: String, val color: Color) {
    BUILTIN("Built-in",     Color(0xFF00E5FF)),
    TRADING("Trading",      Color(0xFF00C853)),
    SMC    ("SMC",          Color(0xFFFF6D00)),
    FILE   ("Files",        Color(0xFFFDD835)),
    SYSTEM ("System",       Color(0xFF7C4DFF))
}

// ─── Tool Catalogue ───────────────────────────────────────────────────────────

private val ALL_TOOLS = listOf(

    // ── Built-in Tools ──────────────────────────────────────────────────────
    ToolInfo("get_current_datetime", "Date & Time", ToolCategory.BUILTIN, "🕐",
        "บอกวันที่และเวลาปัจจุบัน",
        listOf("แสดงวัน/เวลาปัจจุบัน", "คำนวณวันในสัปดาห์"),
        listOf("วันนี้วันที่เท่าไหร่?", "ตอนนี้กี่โมง?")
    ),
    ToolInfo("calculate", "Calculator", ToolCategory.BUILTIN, "🧮",
        "คำนวณนิพจน์ทางคณิตศาสตร์ทุกรูปแบบ",
        listOf("บวก ลบ คูณ หาร", "sqrt, pow, abs, ceil, floor", "เปอร์เซ็นต์, Pi, E"),
        listOf("คำนวณ 15% ของ 8500", "sqrt(144) * 2 + 100")
    ),
    ToolInfo("convert_units", "Unit Converter", ToolCategory.BUILTIN, "📐",
        "แปลงหน่วยวัดทุกประเภท",
        listOf("ระยะทาง (km↔mile↔ft)", "น้ำหนัก (kg↔lb↔oz)", "อุณหภูมิ (°C↔°F↔K)", "พื้นที่ (m²↔acre↔ha)", "ความเร็ว (km/h↔mph↔knot)"),
        listOf("แปลง 100 ไมล์เป็นกิโลเมตร", "5 ปอนด์ เป็น กิโลกรัม", "แปลง 37°C เป็น Fahrenheit")
    ),
    ToolInfo("remember_fact", "Memory Save", ToolCategory.BUILTIN, "💾",
        "บันทึกข้อมูลสำคัญลงหน่วยความจำระยะยาว",
        listOf("จำ API keys, preferences", "จำข้อมูลส่วนตัว", "จำกลยุทธ์การเทรด"),
        listOf("จำไว้ว่า port ชอบ BTC ระยะยาว", "จำว่า stop loss ปกติ 2%")
    ),
    ToolInfo("recall_memory", "Memory Recall", ToolCategory.BUILTIN, "🧠",
        "ค้นหาและเรียกข้อมูลจากหน่วยความจำ",
        listOf("ค้นหาข้อมูลที่บันทึกไว้", "อ้างอิงการสนทนาที่ผ่านมา"),
        listOf("จำ stop loss ที่ฉันตั้งได้ไหม?", "ฉันบอกอะไรเกี่ยวกับ BTC?")
    ),
    ToolInfo("translate_text", "Translator", ToolCategory.BUILTIN, "🌐",
        "แปลภาษาระหว่าง Thai / English / Japanese และอื่นๆ",
        listOf("แปลทุกภาษาหลัก", "อธิบายความหมาย", "ปรับ formal/informal"),
        listOf("แปล 'Bullish momentum' เป็นไทย", "แปลประโยคนี้เป็น English")
    ),
    ToolInfo("summarize_text", "Summarizer", ToolCategory.BUILTIN, "📄",
        "สรุปเนื้อหายาวให้กระชับ",
        listOf("สรุปบทความ", "สรุปข่าว", "ระดับ: สั้น / กลาง / ละเอียด"),
        listOf("สรุปบทความนี้แบบสั้น", "สรุปรายงานผลประกอบการ")
    ),
    ToolInfo("search_web", "Web Search", ToolCategory.BUILTIN, "🔍",
        "ค้นหาข้อมูลจากอินเทอร์เน็ตแบบ Real-time",
        listOf("ข่าวล่าสุด", "ข้อมูลทั่วไป", "ราคา, เหตุการณ์ปัจจุบัน"),
        listOf("ค้นหาข่าว Fed วันนี้", "SEC กฎใหม่ crypto คืออะไร?")
    ),
    ToolInfo("set_reminder", "Reminder", ToolCategory.BUILTIN, "⏰",
        "ตั้งแจ้งเตือนและ TODO",
        listOf("แจ้งเตือนตามเวลา", "บันทึก TODO", "ติดตามงาน"),
        listOf("เตือนฉันดู BTC พรุ่งนี้เช้า", "จด: ต้องปิด position ศุกร์นี้")
    ),
    ToolInfo("analyze_and_display_report", "Report Display", ToolCategory.SYSTEM, "📊",
        "แสดงรายงานวิเคราะห์แบบ Markdown พร้อมตาราง",
        listOf("รายงาน Markdown ครบรูปแบบ", "ตาราง + กราฟ text", "สรุปเสียง"),
        listOf("(ใช้อัตโนมัติเมื่อวิเคราะห์ข้อมูลจำนวนมาก)")
    ),

    // ── Trading Tools ────────────────────────────────────────────────────────
    ToolInfo("trading_price", "Real-time Price", ToolCategory.TRADING, "💰",
        "ดึงราคาหุ้น, Crypto, ETF, Index แบบ Real-time จาก Yahoo Finance",
        listOf("ราคาปัจจุบัน + % เปลี่ยน", "52-Week High/Low", "Pre/After-market price", "รองรับ: หุ้น, Crypto, Gold, FX"),
        listOf("BTC ราคาเท่าไหร่?", "AAPL อยู่ที่เท่าไหร่?", "ราคาทอง GC=F")
    ),
    ToolInfo("trading_market_snapshot", "Market Snapshot", ToolCategory.TRADING, "🌍",
        "ภาพรวมตลาดโลก — ดัชนี, กลุ่มหุ้น, รายชื่อหุ้น",
        listOf("ดัชนีโลก (S&P, Nasdaq, SET ฯลฯ)", "หุ้นรายกลุ่มอุตสาหกรรม", "ตลาด: US, TH, Crypto, Global"),
        listOf("ตลาดวันนี้เป็นยังไง?", "หุ้นกลุ่ม Technology US", "รายชื่อหุ้นไทยกลุ่มธนาคาร")
    ),
    ToolInfo("trading_top_gainers", "Top Gainers", ToolCategory.TRADING, "📈",
        "สแกนหุ้น/Crypto ที่ราคาขึ้นมากที่สุดในตลาด",
        listOf("Top N gainers แบบ Real-time", "Exchange: Binance, Nasdaq, NYSE ฯลฯ", "% change + ราคา"),
        listOf("Top gainers Binance วันนี้", "หุ้นไหนขึ้นเยอะที่สุด Nasdaq?")
    ),
    ToolInfo("trading_top_losers", "Top Losers", ToolCategory.TRADING, "📉",
        "สแกนหุ้น/Crypto ที่ราคาลงมากที่สุดในตลาด",
        listOf("Top N losers แบบ Real-time", "Exchange: Binance, KuCoin, Bybit ฯลฯ"),
        listOf("Crypto ไหน dump วันนี้?", "Top losers Binance")
    ),
    ToolInfo("trading_technical_analysis", "Technical Analysis", ToolCategory.TRADING, "📊",
        "วิเคราะห์ Technical Analysis ครบถ้วนสำหรับ symbol เดี่ยว",
        listOf("RSI, MACD, Stochastic, CCI", "EMA20/50/200, ADX, ATR", "Bollinger Bands (Upper/Basis/Lower)", "สัญญาณ: STRONG BUY → STRONG SELL"),
        listOf("วิเคราะห์ BTC 1h", "AAPL signal เป็นยังไง?", "TA ETH 4h")
    ),
    ToolInfo("trading_multi_timeframe", "Multi-Timeframe", ToolCategory.TRADING, "⏱",
        "วิเคราะห์ทุก Timeframe พร้อมกัน Weekly→Daily→4H→1H→15m",
        listOf("ครอบคลุม 5 timeframe", "Alignment check (ตรง/ขัดแย้ง)", "RSI + Signal ทุก TF"),
        listOf("BTC ทุก timeframe เป็นยังไง?", "Multi TF ETH", "Alignment SOLUSDT")
    ),
    ToolInfo("trading_bollinger_scan", "Bollinger Squeeze", ToolCategory.TRADING, "🔥",
        "สแกน Bollinger Band Squeeze — สัญญาณก่อน Breakout",
        listOf("BBW (Bandwidth) ต่ำ = Squeeze", "Breakout กำลังจะมา", "ค้นทั้ง Exchange"),
        listOf("หาตัวที่กำลัง squeeze Binance", "Bollinger squeeze คืออะไร?")
    ),
    ToolInfo("trading_oversold_scan", "Oversold Scanner", ToolCategory.TRADING, "🔵",
        "สแกน symbols ที่ RSI < 30 — โอกาส Bounce ขึ้น",
        listOf("RSI < 30 (Oversold)", "โอกาส Long / Dip buy", "ค้นทั้ง Exchange"),
        listOf("ตัวไหน oversold Binance?", "RSI ต่ำ KuCoin หาให้หน่อย")
    ),
    ToolInfo("trading_overbought_scan", "Overbought Scanner", ToolCategory.TRADING, "🔴",
        "สแกน symbols ที่ RSI > 70 — ระวัง Correction",
        listOf("RSI > 70 (Overbought)", "โอกาส Short / Take profit", "ค้นทั้ง Exchange"),
        listOf("ตัวไหน overbought Binance?", "RSI สูงเกินไป Nasdaq")
    ),
    ToolInfo("trading_volume_breakout", "Volume Breakout", ToolCategory.TRADING, "💥",
        "สแกน Volume พุ่งสูงผิดปกติ + ราคาขึ้นแรง — Breakout จริง",
        listOf("Volume ระเบิด + Price action", "ไม่ใช่แค่ราคาขึ้น ต้องมี Volume ยืนยัน"),
        listOf("Volume breakout วันนี้", "ตัวไหน volume ระเบิด Binance?")
    ),
    ToolInfo("trading_sentiment", "Reddit Sentiment", ToolCategory.TRADING, "🧠",
        "วิเคราะห์ความรู้สึก Reddit ต่อ symbol (r/wallstreetbets, r/CryptoCurrency)",
        listOf("Bullish/Bearish score", "Posts analyzed count", "Top hot posts", "Community mood"),
        listOf("Reddit มองBTC ยังไง?", "Sentiment AAPL", "ตลาดมอง ETH ว่าอะไร?")
    ),
    ToolInfo("trading_news", "Financial News", ToolCategory.TRADING, "📰",
        "ข่าวการเงินล่าสุดจาก Reuters, CoinDesk, Yahoo Finance",
        listOf("ข่าว Real-time", "กรองตาม symbol ได้", "Multiple RSS sources"),
        listOf("ข่าว BTC วันนี้", "มีข่าวอะไรเกี่ยวกับ AAPL?", "ข่าวตลาดล่าสุด")
    ),
    ToolInfo("trading_combined", "Combined Analysis", ToolCategory.TRADING, "⚡",
        "Power Tool: TA + Sentiment + News รวมกัน พร้อม Confluence Decision",
        listOf("Technical Analysis (TA)", "Reddit Sentiment Score", "Latest Financial News", "Confluence: BUY / SELL / MIXED"),
        listOf("วิเคราะห์ BTC ทุกด้าน", "Full analysis ETH", "Combined BTCUSDT 1h")
    ),

    // ── SMC Tools ────────────────────────────────────────────────────────────
    ToolInfo("trading_smc_analysis", "SMC Dashboard", ToolCategory.SMC, "🧠",
        "SMC วิเคราะห์ครบทุก concept: Structure + OBs + FVG + Liquidity + Premium/Discount",
        listOf("Market Structure (Bullish/Bearish/Neutral)", "Order Blocks + FVG confirmation", "Liquidity Zones พร้อม Stars ★★★★★", "Premium / Discount / Equilibrium zones", "SMC Bias สรุป (Strong Buy/Sell/Neutral)", "Attack Force detection"),
        listOf("วิเคราะห์ SMC BTCUSDT 1h", "SMC ETH 4h", "Zone ไหน strong ที่สุด?")
    ),
    ToolInfo("trading_smc_sweeps", "MTF Sweeps", ToolCategory.SMC, "🌊",
        "ตรวจจับ Liquidity Sweep signals ข้ามทุก Timeframe (M1→M5→M15→M30→H1→H4)",
        listOf("Bullish Sweep: wick ลงใต้ OB → close กลับขึ้น (Long signal)", "Bearish Sweep: wick ขึ้นเหนือ OB → close ลง (Short signal)", "Reclaim factor 50% filter", "ครอบคลุม 6 timeframes"),
        listOf("Sweep BTC ทุก TF มีไหม?", "MTF sweep ETHUSDT", "มี manipulation ไหมวันนี้?")
    ),
    ToolInfo("trading_smc_liquidity", "MTF Liquidity", ToolCategory.SMC, "💧",
        "Liquidity Zones ข้ามหลาย TF — Equal Highs/Lows พร้อม Confluence Stars",
        listOf("Equal Highs = Sell-side Liquidity (resistance)", "Equal Lows = Buy-side Liquidity (support)", "Stars ★★★★★ = OB + Structure + PD + Trend", "Merge ระดับใกล้กันจาก M5/M15/M30/H1/H4"),
        listOf("Liquidity BTC ทุก TF", "Equal highs ETH", "ราคาจะไป sweep ที่ไหน?")
    ),
    ToolInfo("trading_smc_orderblocks", "Order Blocks", ToolCategory.SMC, "📦",
        "Bullish/Bearish Order Blocks พร้อม FVG confirmation (SMC strict logic)",
        listOf("Bullish OB: แหล่ง Demand — ราคาเด้งขึ้น", "Bearish OB: แหล่ง Supply — ราคาร่วงลง", "FVG filter = ต้องมี Displacement ยืนยัน", "แสดงระยะห่างจากราคาปัจจุบัน", "⚡ NEAR tag เมื่อราคาใกล้ถึง OB"),
        listOf("Order blocks BTC 4h", "OB ที่ยังไม่โดน ETHUSDT", "Demand zone SOL 1h")
    ),
    ToolInfo("trading_smc_structure", "Market Structure", ToolCategory.SMC, "📐",
        "BOS / CHoCH detection + Premium/Discount zones (Pure SMC)",
        listOf("BOS: Break of Structure (ยืนยัน trend)", "CHoCH: Change of Character (Trend Reversal!)", "Premium zone ≥75% = แพง, ควร Short", "Discount zone ≤25% = ถูก, ควร Long", "Equilibrium 50% = จุดสมดุล", "% ของ range ที่ราคาอยู่"),
        listOf("Structure BTC 4h", "BOS หรือ CHoCH ETHUSDT?", "ราคาอยู่ใน premium หรือ discount?")
    ),

    // ── File Management Tools ──────────────────────────────────────────────
    ToolInfo("file_list", "File Browser", ToolCategory.FILE, "📂",
        "เรียกดูรายการไฟล์และโฟลเดอร์ในเครื่อง",
        listOf("แสดงไฟล์ทั้งหมดในโฟลเดอร์", "แยกแยะประเภท [DIR] และ [FILE]", "แสดงขนาดไฟล์เบื้องต้น"),
        listOf("ดูไฟล์ในโฟลเดอร์ Download", "มีอะไรอยู่ใน /sdcard/Documents?")
    ),
    ToolInfo("file_read", "File Reader", ToolCategory.FILE, "📄",
        "อ่านเนื้อหาไฟล์ข้อความ (Text, MD, CSV, JSON)",
        listOf("อ่านไฟล์ text ได้ทุกประเภท", "รองรับ UTF-8", "แสดงเนื้อหาทั้งหมดในแชท"),
        listOf("อ่านไฟล์ note.txt ให้หน่อย", "ขอเนื้อหาในไฟล์ logs.csv")
    ),
    ToolInfo("file_write", "File Creator", ToolCategory.FILE, "📝",
        "สร้างไฟล์ใหม่หรือเขียนทับไฟล์เดิม",
        listOf("สร้างไฟล์พร้อมเนื้อหา", "สร้างโฟลเดอร์ให้อัตโนมัติ", "แก้ไขข้อมูลในไฟล์"),
        listOf("สร้างไฟล์ชื่อ hello.txt ใน Download", "เขียนสรุปการประชุมลงไฟล์ summary.md")
    ),
    ToolInfo("file_delete", "File Delete", ToolCategory.FILE, "🗑️",
        "ลบไฟล์หรือโฟลเดอร์ที่ไม่ต้องการ",
        listOf("ลบไฟล์เดี่ยว", "ลบทั้งโฟลเดอร์ (Recursive)", "ประหยัดพื้นที่จัดเก็บ"),
        listOf("ลบไฟล์ขยะใน Download", "ลบโฟลเดอร์ test_data")
    ),
    ToolInfo("file_analyze", "Deep Analyzer", ToolCategory.FILE, "🧠",
        "วิเคราะห์ไฟล์เอกสาร PDF, Word และรูปภาพด้วย Gemini",
        listOf("สรุปเนื้อหา PDF", "อ่านไฟล์ Word (.docx)", "แกะข้อความจากรูปภาพ (OCR)", "ส่งไฟล์วิเคราะห์แบบ Native"),
        listOf("สรุปไฟล์รายงาน.pdf ให้หน่อย", "วิเคราะห์รูปภาพนี้มีอะไรบ้าง?")
    ),
    ToolInfo("file_move", "File Organizer", ToolCategory.FILE, "🚚",
        "ย้ายตำแหน่งไฟล์หรือเปลี่ยนชื่อไฟล์",
        listOf("ย้ายไฟล์ข้ามโฟลเดอร์", "เปลี่ยนชื่อไฟล์ (Rename)", "จัดระเบียบข้อมูล"),
        listOf("ย้ายภาพจาก Download ไปที่ Pictures", "เปลี่ยนชื่อ old_name.txt เป็น new_name.txt")
    ),
    ToolInfo("file_search", "File Search", ToolCategory.FILE, "🔍",
        "ค้นหาไฟล์ทั่วทั้งเครื่องด้วยชื่อหรือนามสกุล",
        listOf("ค้นหาด้วย Keyword", "กรองตามนามสกุลไฟล์", "ค้นในโฟลเดอร์มาตรฐานอัตโนมัติ"),
        listOf("ช่วยหาไฟล์ที่มีคำว่า 'invoice'", "ค้นหาไฟล์ .pdf ในเครื่องให้หน่อย")
    )
)

// ─── Main Dialog ──────────────────────────────────────────────────────────────

@Composable
fun ToolListDialog(onDismiss: () -> Unit) {
    var searchQuery     by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }
    var expandedTool    by remember { mutableStateOf<String?>(null) }

    val filteredTools = remember(searchQuery, selectedCategory) {
        ALL_TOOLS.filter { tool ->
            val matchCategory = selectedCategory == null || tool.category == selectedCategory
            val matchSearch   = searchQuery.isBlank() ||
                tool.displayName.contains(searchQuery, ignoreCase = true) ||
                tool.shortDesc.contains(searchQuery, ignoreCase = true) ||
                tool.capabilities.any { it.contains(searchQuery, ignoreCase = true) }
            matchCategory && matchSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(JarvisTheme.Dark)
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ──────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JarvisTheme.Surface)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "⬡  JARVIS TOOLS",
                                    color = JarvisTheme.Cyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    letterSpacing = 3.sp
                                )
                                Text(
                                    "${ALL_TOOLS.size} tools พร้อมใช้งาน",
                                    color = Color.White.copy(0.5f),
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "ปิด",
                                    tint = Color.White.copy(0.6f)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; expandedTool = null },
                            placeholder = {
                                Text("ค้นหา tool...", color = Color.White.copy(0.3f), fontSize = 14.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, null, tint = JarvisTheme.Cyan.copy(0.7f))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, null, tint = Color.White.copy(0.4f),
                                            modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = JarvisTheme.Cyan.copy(0.5f),
                                unfocusedBorderColor = Color.White.copy(0.15f),
                                focusedTextColor     = Color.White,
                                unfocusedTextColor   = Color.White,
                                cursorColor          = JarvisTheme.Cyan
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        // Category filter chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                CategoryChip(
                                    label = "ทั้งหมด (${ALL_TOOLS.size})",
                                    color = JarvisTheme.Cyan,
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null; expandedTool = null }
                                )
                            }
                            items(ToolCategory.entries) { cat ->
                                val count = ALL_TOOLS.count { it.category == cat }
                                CategoryChip(
                                    label = "${cat.label} ($count)",
                                    color = cat.color,
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = if (selectedCategory == cat) null else cat; expandedTool = null }
                                )
                            }
                        }
                    }
                }

                // ── Tool List ────────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filteredTools.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillParentMaxWidth().padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🔍", fontSize = 40.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "ไม่พบ tool ที่ค้นหา",
                                        color = Color.White.copy(0.5f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredTools, key = { it.name }) { tool ->
                            ToolCard(
                                tool     = tool,
                                expanded = expandedTool == tool.name,
                                onClick  = {
                                    expandedTool = if (expandedTool == tool.name) null else tool.name
                                }
                            )
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ─── Category Chip ────────────────────────────────────────────────────────────

@Composable
private fun CategoryChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) color.copy(alpha = 0.2f) else Color.Transparent,
        animationSpec = tween(150),
        label = "chipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) color else color.copy(alpha = 0.3f),
        animationSpec = tween(150),
        label = "chipBorder"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) color else color.copy(0.6f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ─── Tool Card ────────────────────────────────────────────────────────────────

@Composable
private fun ToolCard(
    tool: ToolInfo,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val catColor  = tool.category.color
    val bgAlpha   by animateColorAsState(
        if (expanded) catColor.copy(0.08f) else JarvisTheme.Card.copy(1f),
        tween(200), label = "cardBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgAlpha),
        border = BorderStroke(
            width = if (expanded) 1.dp else 0.5.dp,
            color = if (expanded) catColor.copy(0.5f) else Color.White.copy(0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Card Header ──────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(tool.icon, fontSize = 18.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tool.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        // Category badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(catColor.copy(0.15f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                tool.category.label,
                                color = catColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tool.shortDesc,
                        color = Color.White.copy(0.55f),
                        fontSize = 12.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Expand indicator
                Text(
                    if (expanded) "▲" else "▼",
                    color = catColor.copy(0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // ── Expanded Detail ──────────────────────────────────────────────
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = catColor.copy(0.2f), thickness = 0.5.dp)
                Spacer(Modifier.height(10.dp))

                // Capabilities
                Text(
                    "✦ ความสามารถ",
                    color = catColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                tool.capabilities.forEach { cap ->
                    Row(
                        modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•  ", color = catColor.copy(0.6f), fontSize = 12.sp)
                        Text(cap, color = Color.White.copy(0.75f), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Example prompts
                Text(
                    "💬 ตัวอย่างคำถาม",
                    color = catColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                tool.examplePrompts.forEach { prompt ->
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(catColor.copy(0.07f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "\"$prompt\"",
                            color = Color.White.copy(0.7f),
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Tool function name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(0.04f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            tool.name,
                            color = Color.White.copy(0.25f),
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
