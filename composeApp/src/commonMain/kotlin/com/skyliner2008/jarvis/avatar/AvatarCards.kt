package com.skyliner2008.jarvis.avatar

/**
 * The robot's markdown card for a tool that has no scene of its own (pet_godot answer_card): which tools get
 * one, and their text made short enough for a card. The AI still gets the whole text; the card is what the
 * robot holds up while the AI talks about it.
 *
 * Every tool that answers a question gets a card (analysis, scans, look-ups, calculations); tools that do
 * something (orders, the phone's settings, files, the app's own faces) do not — their result is "done".
 */
object AvatarCards {
    /** Answers outside trading_* that are worth a card. */
    private val INFO_TOOLS = setOf(
        "search_web", "device_search_web", "summarize_text", "translate_text", "calculate", "convert_units",
        "device_location", "get_current_datetime", "device_wifi_status", "recall_memory",
        "strategy_explain", "strategy_list", "strategy_search",
        "camera_analyze_scene", "camera_read_text", "camera_detect_objects",
    )

    /** trading_* tools that act (orders, settings, imports) instead of answering. */
    private val TRADING_ACTIONS = setOf(
        "trading_mt5_order", "trading_mt5_close_all", "trading_mt5_close_position", "trading_mt5_modify_position",
        "trading_mt5_break_even_all", "trading_mt5_trade_actions", "trading_mix_config",
        "trading_signal_config_import", "trading_signal_data_export", "trading_mt5_symbol_search",
    )

    const val MAX_LINES = 10
    const val MAX_CHARS = 560

    /** Does [tool]'s text go on a card when it has no scene data? */
    fun wants(tool: String): Boolean =
        tool in INFO_TOOLS || (tool.startsWith("trading_") && tool !in TRADING_ACTIONS)

    /** Where a tool's text stops being worth a card: the indicator dump, the AI's long write-up … */
    private val STOP_AT = listOf(
        "### Indicator Matrix", "🧠 **JARVIS Intelligence", "🔦 **AI Strategic", "### 🧠", "_Recipes:",
    )

    /** Lines that say nothing on a card: notes for the AI, data-source tags, tips. */
    private val DROP_STARTS = listOf("[Data]", "💡 *Tip", "Source:", "(โหมดเสียง:", "ISO:")

    private val DECORATION = Regex("""^[\s=\-─═_*#>|:~`.]*$""")
    private val TABLE_RULE = Regex("""^\|?\s*:?-{2,}.*$""")
    private val LONG_DECIMALS = Regex("""\b([1-9]\d*\.\d{2})\d{3,}\b""")
    /** "## …", or a line that is all bold after an emoji: "🌊 **Modern Elliot Wave Insight - BTCUSDT (1h)**" */
    private val BOLD_LINE = Regex("""^(\S+\s+)?\*\*[^*]+\*\*$""")
    /** a plain title: "Combined Analysis - BTCUSDT (1h)" */
    private val TITLE_LINE = Regex("""^[^:|]{3,50} [-—] [^:|]{2,30}\)$""")
    /** a field with no value: " | MACD hist: null", "EMA20: null vs " */
    private val EMPTY_FIELD = Regex("""\s*(\||vs\b)?\s*[\w .+\-/%]+:\s*(null|N/A)\b""")

    /**
     * [text] made into a short card: no rule lines, no notes for the AI, tables as bullet lines, long decimals
     * cut to two places, at most [MAX_LINES] lines / [MAX_CHARS] characters ("…" when cut).
     */
    fun text(text: String): String {
        val out = ArrayList<String>()
        var chars = 0
        var cut = false
        for (raw in text.replace("\r", "").lines()) {
            val line = raw.trim()
            if (STOP_AT.any { line.startsWith(it) }) { cut = true; break }
            if (line.isEmpty() || DECORATION.matches(line) || TABLE_RULE.matches(line)) continue
            if (DROP_STARTS.any { line.startsWith(it) } || line == ">") continue
            var shown = if (line.startsWith("|")) tableRow(line) ?: continue else line
            shown = LONG_DECIMALS.replace(shown) { it.groupValues[1] }
            shown = EMPTY_FIELD.replace(shown, "").trim().removePrefix("|").removePrefix("vs").trim()
            if (shown.isEmpty()) continue
            if (out.size >= MAX_LINES || chars + shown.length > MAX_CHARS) { cut = true; break }
            out += shown
            chars += shown.length + 1
        }
        // a heading left at the end has nothing under it
        while (out.size > 1 && isHeading(out.last())) out.removeAt(out.lastIndex)
        // the tool's own title line ("🌊 **Modern Elliot Wave Insight - BTCUSDT (1h)**") heads the card
        if (out.isNotEmpty() && (isHeading(out.first()) || TITLE_LINE.matches(out.first()))) {
            out[0] = "## " + out[0].trimStart('#', ' ').replace("**", "")
        }
        if (cut && out.isNotEmpty()) out += "…"
        return out.joinToString("\n")
    }

    /** "| a | b | c |" → "- a · b · c"; the header row of a table is dropped by [text] only when it is a rule. */
    private fun tableRow(line: String): String? {
        val cells = line.trim('|').split('|').map { it.trim() }.filter { it.isNotEmpty() }
        return if (cells.isEmpty()) null else "- " + cells.joinToString(" · ")
    }

    private fun isHeading(line: String): Boolean = line.startsWith("#") || (line.length <= 80 && BOLD_LINE.matches(line))
}
