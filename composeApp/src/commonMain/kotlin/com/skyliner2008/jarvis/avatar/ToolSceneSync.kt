package com.skyliner2008.jarvis.avatar

import com.skyliner2008.jarvis.data.LiveProtocol
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Which tool scene the robot shows while the AI answers in Avatar mode. The scenes are held on their last
 * picture (pet_godot "hold") until released:
 * - a result goes up when the AI talks about it, at the moment its words are heard, never before: results
 *   that come back while the AI is still thinking wait (the answers to questions asked in a row used to
 *   flash by one after another before the AI said a word);
 * - the words that bring a result up are the ones only it has ("ราคา" is every price's, the name tells them
 *   apart); if the AI talks a while without naming the only new result, that one goes up anyway;
 * - a result the AI tells later (a job handed to the assistant — all the AI says now is "รับทราบ…" — a call
 *   the user talked over, a result the report queue took) waits for its report: when the app hands it to the
 *   AI ([onReport]), it goes up with the AI's first words;
 * - a scene stays until the AI's voice has stopped, then it is released;
 * - an answer's text and voice arrive seconds before the voice is heard: a word is heard at the voice's
 *   start plus the time the words before it take to say ([LiveProtocol.speechUnits] × the ms per unit
 *   measured in the session). How much voice has arrived does not tell — it runs ahead of the words by
 *   different amounts.
 *
 * (logcat 2026-10-01 21:32–21:38: cards ended before the AI did, the weather card stayed up while the AI
 * talked about gold, the gold card never came, Tesla and Microsoft shared one card. 2026-10-02 00:18: the
 * calendar job's card would have gone up during "กำลังให้ผู้ช่วยดึงข้อมูล" and been gone when the AI told the
 * calendar. 09:48: gold and weather flashed by while the AI was still thinking (8 s), then came up again with
 * its voice; the weather card, timed from the voice that had arrived, came ~1 s after the AI got to it.)
 *
 * Not thread-safe: one caller (Avatar mode, main thread).
 */
class ToolSceneSync(
    private val show: (Item) -> Unit,
    private val release: () -> Unit,
    /** how long a result the AI hasn't talked about may still come up (the report queue keeps one 5 minutes) */
    private val keepMs: Long = 300_000L,
    /** the voice stopped this long = done with the scene */
    private val quietMs: Long = 700L,
    /** a scene nobody talks about goes after this */
    private val unspokenMs: Long = 15_000L,
    /** the AI has talked this long without naming the only new result: it goes up anyway */
    private val unnamedMs: Long = 1_500L,
    /** a result this new may be the one the AI is answering */
    private val newMs: Long = 30_000L,
) {
    class Item(
        val tool: String,
        val data: JsonElement,
        val words: List<String>,
        val atMs: Long,
        /** the AI tells it later, when the app hands it over from the report queue */
        val later: Boolean = false,
        /** the result's first line: which report it is */
        val subject: String = "",
    ) {
        /** the AI's voice got to it */
        var spoken = false
    }

    /** a scene change waiting for its moment; [heard] = its words were said (it is being told) */
    private class Switch(val atMs: Long, val item: Item, val heard: Boolean)

    /** results the AI may talk about now: its words bring them up */
    private val waiting = ArrayList<Item>()
    /** results only their report brings up: told later, or taken down before the AI talked about them */
    private val queued = ArrayList<Item>()
    /** the report the AI was just handed: up with the first words of its next answer */
    private var reporting: Item? = null
    /** a report was handed over (with a scene or not): the AI's next answer is that report */
    private var handed = false
    private var shown: Item? = null
    private var shownAtMs = 0L
    private var speaking = false
    private var speakingSinceMs = 0L
    private var spokeSinceShown = false
    private var quietSinceMs = 0L
    private var answer = ""
    private var answerStartedAtMs = 0L
    private var answerIsReport = false
    private var scanned = 0
    private val due = ArrayList<Switch>()

    val current: Item? get() = shown

    fun onResult(item: Item, nowMs: Long) {
        prune(nowMs)
        if (item.later) {
            queued.add(item)
            return
        }
        // the user asked something new: the AI's next words are about this, not the report it was handed
        reporting?.let { queued.add(it) }
        reporting = null
        handed = false
        waiting.add(item)
    }

    /** The report queue took a result ([tool], its first line [subject]): the AI tells it later, when it is handed over. */
    fun onDeferred(tool: String, subject: String) {
        val item = pick(waiting, tool, subject) ?: return
        waiting.remove(item)
        queued.add(item)
    }

    /** The app hands the AI a result to tell now ([tool], its first line [subject]): its card goes up with the AI's first words. */
    fun onReport(tool: String, subject: String, nowMs: Long) {
        reporting?.let { queued.add(it) }
        reporting = pick(queued + waiting, tool, subject)?.also {
            queued.remove(it)
            waiting.remove(it)
        }
        handed = true
        prune(nowMs)
    }

    /**
     * The AI's answer so far ([text]). Its voice started (or starts) at [startMs] and says a unit of
     * [LiveProtocol.speechUnits] in [msPerUnit] ms.
     */
    fun onSpeech(text: String, startMs: Long, msPerUnit: Double, nowMs: Long) {
        val norm = ToolSceneWords.normalize(text)
        if (answer.isEmpty() || !norm.startsWith(answer)) {          // a new answer
            scanned = 0
            answerStartedAtMs = nowMs
            answerIsReport = handed
            handed = false
            reporting?.let { due.add(Switch(maxOf(startMs, nowMs), it, heard = false)) }
            reporting = null
        }
        answer = norm
        val from = (scanned - OVERLAP).coerceIn(0, norm.length)
        val window = norm.substring(from)
        scanned = norm.length
        val candidates = waiting + due.map { it.item } + listOfNotNull(shown)
        // where an item is first named, by the words only it has ("ราคา" is every price's — the name tells them apart)
        fun namedAt(item: Item): Int? {
            val others = candidates.filter { it !== item }.flatMap { it.words }.toSet()
            return item.words.filter { it !in others }
                .mapNotNull { w -> window.indexOf(w).takeIf { i -> i >= 0 } }
                .minOrNull()
        }
        shown?.let { if (namedAt(it) != null) it.spoken = true }
        // each waiting result named in these words goes up when its name is heard
        waiting.mapNotNull { item -> namedAt(item)?.let { item to from + it } }.forEach { (item, at) ->
            waiting.remove(item)
            val heardAt = startMs + (LiveProtocol.speechUnits(norm.substring(0, at)) * msPerUnit).toLong()
            due.add(Switch(maxOf(heardAt, nowMs), item, heard = true))
        }
    }

    fun onSpeaking(on: Boolean, nowMs: Long) {
        if (on && !speaking) speakingSinceMs = nowMs
        if (on && shown != null) spokeSinceShown = true
        if (!on && speaking) quietSinceMs = nowMs
        speaking = on
    }

    fun tick(nowMs: Long) {
        // this answer's voice has ended: what it named was heard, even where the estimate said later
        val voiceEnded = !speaking && quietSinceMs >= answerStartedAtMs && nowMs - quietSinceMs >= quietMs
        if (voiceEnded && due.isNotEmpty()) {
            due.forEach { if (it.heard) it.item.spoken = true }
            due.clear()
        }
        due.sortBy { it.atMs }
        while (due.isNotEmpty() && due.first().atMs <= nowMs) {
            val switch = due.removeAt(0)
            shown?.let { if (!it.spoken) waiting.add(it) }
            put(switch.item, nowMs)
            if (switch.heard) switch.item.spoken = true
        }
        // talking a while and naming nothing: the one new result is what the AI is answering
        if (shown == null && due.isEmpty() && speaking && !answerIsReport && nowMs - speakingSinceMs >= unnamedMs) {
            waiting.singleOrNull { nowMs - it.atMs <= newMs }?.let {
                waiting.remove(it)
                put(it, nowMs)
            }
        }
        val up = shown ?: return
        if (due.isNotEmpty()) return
        val doneTalking = !speaking && spokeSinceShown && nowMs - quietSinceMs >= quietMs
        val nobodyTalked = !spokeSinceShown && nowMs - shownAtMs >= unspokenMs
        if (doneTalking || nobodyTalked) {
            release()
            shown = null
            when {
                nobodyTalked -> waiting.add(up)                // a queued answer may still tell it
                !up.spoken -> queued.add(up)                   // the AI talked, not about it (it said it handed the job over…)
            }
            prune(nowMs)
        }
    }

    private fun pick(pool: List<Item>, tool: String, subject: String): Item? =
        pool.firstOrNull { it.tool == tool && it.subject == subject } ?: pool.singleOrNull { it.tool == tool }

    private fun put(item: Item, nowMs: Long) {
        show(item)
        shown = item
        shownAtMs = nowMs
        spokeSinceShown = speaking
    }

    private fun prune(nowMs: Long) {
        waiting.removeAll { nowMs - it.atMs > keepMs }
        queued.removeAll { nowMs - it.atMs > keepMs }
    }

    private companion object {
        /** words split across two transcript chunks ("ราคาทอง" + "คำ") */
        const val OVERLAP = 16
    }
}

/** What the AI says when it talks about a tool's result: the words that bring its scene up. */
object ToolSceneWords {
    private val byTool = mapOf(
        "device_weather" to listOf("อากาศ", "ฝน", "องศา", "อุณหภูมิ", "แดด", "เมฆ", "ร่ม", "weather"),
        "trading_price" to listOf("ราคา", "ดอลลาร์", "price"),
        "trading_technical_analysis" to listOf("rsi", "macd", "ema", "แนวรับ", "แนวต้าน", "เทคนิค", "อินดิเคเตอร์", "สัญญาณ"),
        "trading_fundamental_analysis" to listOf("งบ", "สินทรัพย์", "หนี้สิน", "กำไร", "รายได้", "ปัจจัยพื้นฐาน"),
        "trading_top_gainers" to listOf("ขึ้นแรง", "บวกแรง", "ขึ้นมากที่สุด", "gainer", "อันดับ"),
        "trading_top_losers" to listOf("ลงแรง", "ร่วง", "ลบมากที่สุด", "loser", "อันดับ"),
        "trading_fear_greed" to listOf("fear", "greed", "กลัว", "โลภ", "อารมณ์ตลาด"),
        "trading_crypto_overview" to listOf("ตลาดคริปโต", "คริปโต", "dominance", "มาร์เก็ตแคป", "marketcap", "มูลค่ารวม"),
        "device_battery_status" to listOf("แบต", "battery", "ชาร์จ"),
        "trading_news" to listOf("ข่าว", "news"),
        "search_web" to listOf("ค้นหา", "ผลการค้น", "จากเว็บ"),
        "summarize_text" to listOf("สรุป"),
        "translate_text" to listOf("แปล"),
        "calculate" to listOf("คำนวณ", "เท่ากับ", "ผลลัพธ์"),
        "convert_units" to listOf("แปลง", "เท่ากับ"),
        "device_location" to listOf("อยู่ที่", "ตำแหน่ง", "แถว"),
        "trading_sentiment" to listOf("sentiment", "ความเชื่อมั่น", "อารมณ์ตลาด"),
        "trading_macro_calendar" to listOf("ปฏิทิน", "ตัวเลขเศรษฐกิจ"),
    )

    /** what the AI calls a ticker (it says "ทองคำ", "Tesla" — not the code) */
    private val names = mapOf(
        "XAU" to listOf("ทอง", "gold"), "GOLD" to listOf("ทอง", "gold"), "GC" to listOf("ทอง", "gold"),
        "XAG" to listOf("แร่เงิน", "silver"), "SILVER" to listOf("แร่เงิน", "silver"),
        "BTC" to listOf("bitcoin", "บิตคอยน์", "บิทคอยน์"), "ETH" to listOf("ethereum", "อีเธอเรียม", "อีเทอเรียม"),
        "SOL" to listOf("solana", "โซลานา"), "XRP" to listOf("ripple", "ริปเปิล"), "DOGE" to listOf("doge", "ด็อกคอยน์"),
        "ADA" to listOf("cardano", "คาร์ดาโน"), "BNB" to listOf("bnb"),
        "TSLA" to listOf("tesla", "เทสลา"), "MSFT" to listOf("microsoft", "ไมโครซอฟท์", "ไมโครซอฟต์"),
        "AAPL" to listOf("apple", "แอปเปิล", "แอปเปิ้ล"), "NVDA" to listOf("nvidia", "เอ็นวิเดีย", "เอ็นวีเดีย"),
        "GOOGL" to listOf("google", "กูเกิล", "alphabet"), "GOOG" to listOf("google", "กูเกิล", "alphabet"),
        "AMZN" to listOf("amazon", "อเมซอน", "แอมะซอน"), "META" to listOf("meta", "เมตา", "facebook"),
        "NFLX" to listOf("netflix", "เน็ตฟลิกซ์"), "AMD" to listOf("amd"), "INTC" to listOf("intel", "อินเทล"),
        "WTI" to listOf("น้ำมัน", "oil"), "CL" to listOf("น้ำมัน", "oil"), "USOIL" to listOf("น้ำมัน", "oil"),
        "SET" to listOf("ดัชนี", "set"), "EURUSD" to listOf("ยูโร", "euro"), "USDJPY" to listOf("เยน", "yen"),
        "GBPUSD" to listOf("ปอนด์", "pound"), "USDTHB" to listOf("ค่าเงินบาท"),
    )

    fun of(tool: String, data: JsonElement?): List<String> {
        val out = LinkedHashSet<String>()
        byTool[tool]?.let(out::addAll)
        val fields = data as? JsonObject
        (fields?.get("symbol") as? JsonPrimitive)?.content?.let { out.addAll(symbolWords(it)) }
        if (tool == "device_weather") (fields?.get("location") as? JsonPrimitive)?.content?.let { out.addAll(placeWords(it)) }
        return out.map(::normalize).filter { it.length >= 2 }.distinct()
    }

    /** "XAUUSD=X" → ทอง/gold, "BTC-USD" → bitcoin/บิตคอยน์, "TSLA" → tesla/เทสลา (and the code itself) */
    fun symbolWords(symbol: String): List<String> {
        val code = symbol.uppercase().trim().removePrefix("^").substringBefore('=').substringBefore('-')
            .substringBefore('.').substringBefore('/')
        val base = code.removeSuffix("USDT").removeSuffix("USD")
        return listOf(code, base).filter { it.length >= 2 }.flatMap { listOf(it) + names[it].orEmpty() }
    }

    /** "ตำบล บานา, ปัตตานี, ประเทศไทย" → บานา, ปัตตานี */
    fun placeWords(location: String): List<String> = location.split(',', '·')
        .map { part ->
            listOf("ตำบล", "อำเภอ", "จังหวัด", "เขต", "แขวง").fold(part.trim()) { s, prefix -> s.removePrefix(prefix).trim() }
        }
        .filter { it.length >= 3 && it != "ประเทศไทย" && !it.equals("thailand", ignoreCase = true) }

    fun normalize(text: String): String = text.lowercase().filterNot { it.isWhitespace() || it in PUNCTUATION }

    private const val PUNCTUATION = ".,!?…:;\"'()“”"
}
