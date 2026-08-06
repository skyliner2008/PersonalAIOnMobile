package com.example.personalaibot.tools.strategy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import personalaibot.composeapp.generated.resources.Res

/**
 * StrategyLibrary — คลังกลยุทธ์การเทรดเชิงวิชาการ (Quantpedia / QuantConnect reference implementations)
 *
 * ไฟล์ .py ทั้งหมด bundle อยู่ใน composeResources/files/strategies/ พร้อม index.json
 * (สร้างโดย scripts/generate_strategy_index.py — รันใหม่เมื่อเพิ่ม/แก้ไฟล์ใน strategies/)
 *
 * หมายเหตุ: นี่คือ "คลังความรู้" ให้ AI อธิบาย/เปรียบเทียบ/ปรับใช้กลยุทธ์
 * ไม่ใช่ backtest engine — โค้ดต้นฉบับรันบน QuantConnect (Lean) เท่านั้น
 */
@OptIn(ExperimentalResourceApi::class)
object StrategyLibrary {

    @Serializable
    data class StrategyInfo(
        val name: String,
        val title: String,
        val file: String,
        val category: String,
        val description: String = "",
        val url: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    // cache หลังโหลดครั้งแรก — resource ไม่เปลี่ยนระหว่างรัน
    private var cachedIndex: List<StrategyInfo>? = null

    private suspend fun loadIndex(): List<StrategyInfo> {
        cachedIndex?.let { return it }
        return try {
            val bytes = Res.readBytes("files/strategies/index.json")
            val list = json.decodeFromString<List<StrategyInfo>>(bytes.decodeToString())
            cachedIndex = list
            list
        } catch (e: Exception) {
            com.example.personalaibot.logError("StrategyLib", "โหลด strategy index ไม่สำเร็จ", e)
            emptyList()
        }
    }

    private fun normalize(s: String): String =
        s.lowercase().trim().replace("_", "-").replace(" ", "-")

    private fun findByName(name: String, list: List<StrategyInfo>): StrategyInfo? {
        val q = normalize(name).removeSuffix(".py")
        return list.firstOrNull { it.name == q }
            ?: list.firstOrNull { normalize(it.title) == q }
            ?: list.firstOrNull { it.name.contains(q) || q.contains(it.name) }
            ?: list.firstOrNull { normalize(it.title).contains(q) }
    }

    /** รายการหมวดทั้งหมด + จำนวนกลยุทธ์ */
    suspend fun listCategories(): String {
        val list = loadIndex()
        if (list.isEmpty()) return "⚠️ ไม่พบคลังกลยุทธ์ (index.json หายหรืออ่านไม่ได้)"
        val byCat = list.groupBy { it.category }
        return buildString {
            append("📚 Strategy Library — ทั้งหมด ${list.size} กลยุทธ์ (Quantpedia)\n\n")
            byCat.toSortedMap().forEach { (cat, items) ->
                append("• $cat (${items.size})\n")
            }
            append("\nเรียก strategy_list พร้อม category เพื่อดูรายชื่อในหมวด หรือ strategy_search เพื่อค้นหา")
        }
    }

    /** รายชื่อกลยุทธ์ (ทั้งหมดหรือเฉพาะหมวด) — แสดงชื่อ + คำอธิบายสั้น */
    suspend fun listStrategies(category: String? = null): String {
        val list = loadIndex()
        if (list.isEmpty()) return "⚠️ ไม่พบคลังกลยุทธ์"
        val filtered = if (category.isNullOrBlank()) list
            else list.filter { it.category.equals(category, ignoreCase = true) }
        if (filtered.isEmpty()) {
            val cats = list.map { it.category }.distinct().sorted().joinToString(", ")
            return "ไม่พบหมวด '$category' — หมวดที่มี: $cats"
        }
        return buildString {
            append("📚 ${if (category.isNullOrBlank()) "กลยุทธ์ทั้งหมด" else "หมวด $category"} (${filtered.size}):\n\n")
            filtered.forEach { s ->
                append("• **${s.name}** — ${s.title}")
                if (s.description.isNotBlank()) append("\n  ${s.description.take(160)}")
                append("\n")
            }
            append("\nใช้ strategy_explain พร้อม name เพื่อดูตรรกะและโค้ดเต็ม")
        }
    }

    /** ค้นหากลยุทธ์จาก keyword (ชื่อ/หมวด/คำอธิบาย) */
    suspend fun search(query: String): String {
        val list = loadIndex()
        if (list.isEmpty()) return "⚠️ ไม่พบคลังกลยุทธ์"
        if (query.isBlank()) return "กรุณาระบุคำค้นหา"
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        val scored = list.mapNotNull { s ->
            val hay = "${s.name} ${s.title} ${s.category} ${s.description}".lowercase()
            val score = terms.count { hay.contains(it) }
            if (score > 0) s to score else null
        }.sortedByDescending { it.second }
        if (scored.isEmpty()) return "ไม่พบกลยุทธ์ที่ตรงกับ '$query' — ลอง strategy_list เพื่อดูทั้งหมด"
        return buildString {
            append("🔍 พบ ${scored.size} กลยุทธ์ที่เกี่ยวกับ '$query':\n\n")
            scored.take(10).forEach { (s, _) ->
                append("• **${s.name}** [${s.category}] — ${s.title}")
                if (s.description.isNotBlank()) append("\n  ${s.description.take(160)}")
                append("\n")
            }
            append("\nใช้ strategy_explain พร้อม name เพื่อดูรายละเอียดเต็ม")
        }
    }

    /**
     * รายละเอียดเต็มของกลยุทธ์: คำอธิบาย + ตรรกะจาก comment + โค้ดต้นฉบับ (QuantConnect Python)
     * — ใช้เพื่ออธิบาย ปรับใช้ หรือแปลงเป็นแนวทางวิเคราะห์ ไม่ใช่การรัน backtest บนเครื่อง
     */
    suspend fun getStrategyDetail(name: String): String {
        val list = loadIndex()
        if (list.isEmpty()) return "⚠️ ไม่พบคลังกลยุทธ์"
        val info = findByName(name, list)
            ?: return "ไม่พบกลยุทธ์ '$name' — ลอง strategy_search หรือ strategy_list ดูชื่อที่ถูกต้อง"
        val source = try {
            Res.readBytes("files/strategies/${info.file}").decodeToString()
        } catch (e: Exception) {
            "(อ่านไฟล์ต้นฉบับไม่ได้: ${e.message})"
        }
        return buildString {
            append("📖 **${info.title}** (`${info.name}`)\n")
            append("หมวด: ${info.category}\n")
            if (info.url.isNotBlank()) append("อ้างอิง: ${info.url}\n")
            if (info.description.isNotBlank()) append("\n${info.description}\n")
            append("\n─── ต้นฉบับ (QuantConnect / Lean Python — reference เท่านั้น ไม่ได้รันบนแอป) ───\n")
            append("```python\n")
            append(source.trim())
            append("\n```")
        }
    }
}
