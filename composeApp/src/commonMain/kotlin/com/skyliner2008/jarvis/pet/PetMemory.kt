package com.skyliner2008.jarvis.pet

import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.logDebug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * InteractionEntry — บันทึกประวัติ interaction หนึ่งรายการ (สำหรับ PetMemory)
 */
@Serializable
data class InteractionEntry(
    val type: String,             // "poke", "pet_head", "tickle", "feed", "clean", "shake"
    val zone: String,             // "forehead", "left_cheek", "right_cheek", "chin", "face_center"
    val timestamp: Long,
    val resultingEmotion: String  // "happy", "angry", "love", etc.
)

/**
 * PetMemory — หน่วยความจำถาวรของ Pet ที่จดจำทุกอย่างข้ามเซสชัน
 *
 * เก็บ:
 * - สถิติเซสชันปัจจุบัน (reset ทุกครั้งที่เปิดแอป)
 * - สถิติตลอดชีวิต (สะสมถาวร)
 * - ประวัติอารมณ์ (จำ happy/sad streaks)
 * - ความชอบที่เรียนรู้จาก pattern (favorite interaction)
 * - ประวัติ interaction ล่าสุด 20 รายการ
 * - PetNeedsState ที่ save ไว้ข้ามเซสชัน
 */
@Serializable
data class PetMemory(
    // ─── Session Stats (รีเซ็ตทุกเซสชัน) ───────────────────────────────
    val sessionStartTime: Long = System.currentTimeMillis(),
    val sessionInteractions: Int = 0,
    val sessionPokes: Int = 0,
    val sessionPets: Int = 0,
    val sessionFeeds: Int = 0,
    val sessionCleans: Int = 0,
    val sessionPlays: Int = 0,

    // ─── Lifetime Stats (สะสมตลอดชีวิต Pet) ─────────────────────────────
    val totalInteractions: Long = 0L,
    val totalFeeds: Long = 0L,
    val totalCleans: Long = 0L,
    val totalPlays: Long = 0L,
    val totalPets: Long = 0L,
    val totalPokes: Long = 0L,
    val totalSleepMinutes: Long = 0L,
    val daysAlive: Int = 1,
    val birthdayTimestamp: Long = System.currentTimeMillis(),

    // ─── Emotional Memory (จำอารมณ์ที่ผ่านมา) ──────────────────────────
    val lastMoodBeforeSleep: String = "idle",
    val consecutiveHappyDays: Int = 0,
    val consecutiveSadDays: Int = 0,
    val longestHappyStreak: Int = 0,
    val timesGotAngry: Int = 0,
    val timesGotSurprised: Int = 0,

    // ─── Preference Learning (เรียนรู้ความชอบ) ──────────────────────────
    val favoriteInteraction: String = "pet_head",
    val leastFavoriteInteraction: String = "poke",

    // ─── Recent Interaction Ring Buffer (20 ล่าสุด) ─────────────────────
    val recentInteractions: List<InteractionEntry> = emptyList(),

    // ─── Persistent NeedsState ─────────────────────────────────────────
    val savedNeedsState: PetNeedsState = PetNeedsState(),
    val lastSaveTimestamp: Long = 0L
) {
    /** อายุ Pet เป็นวัน */
    val ageInDays: Int
        get() = ((System.currentTimeMillis() - birthdayTimestamp) / 86_400_000L).toInt().coerceAtLeast(1)

    /** สรุปสถิติสั้นๆ สำหรับ UI */
    val lifetimeSummary: String
        get() = "อายุ ${ageInDays} วัน | ให้อาหาร ${totalFeeds} ครั้ง | เล่น ${totalPlays} ครั้ง"
}

/**
 * PetMemoryStore — จัดการ save/load PetMemory ลง SQLite
 */
object PetMemoryStore {
    private const val TAG = "PetMemoryStore"
    private const val KEY = "pet.memory"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private val _memoryFlow = MutableStateFlow(PetMemory())
    val memoryState: StateFlow<PetMemory> = _memoryFlow.asStateFlow()
    val memory: PetMemory get() = _memoryFlow.value

    /**
     * โหลด PetMemory จาก SQLite (เรียกตอน start)
     */
    fun load(): PetMemory {
        try {
            val db = JarvisDatabaseHolder.database ?: return _memoryFlow.value
            val stored = db.jarvisDatabaseQueries.getSetting(KEY).executeAsOneOrNull()
            if (!stored.isNullOrBlank()) {
                val loaded = json.decodeFromString<PetMemory>(stored)
                val newSession = loaded.copy(
                    sessionStartTime = System.currentTimeMillis(),
                    sessionInteractions = 0,
                    sessionPokes = 0,
                    sessionPets = 0,
                    sessionFeeds = 0,
                    sessionCleans = 0,
                    sessionPlays = 0
                )
                _memoryFlow.value = newSession
                logDebug(TAG, "✅ Loaded PetMemory: age=${newSession.ageInDays}d, totalInteractions=${newSession.totalInteractions}")
            }
        } catch (e: Exception) {
            logDebug(TAG, "⚠️ Failed to load PetMemory: ${e.message}")
        }
        return _memoryFlow.value
    }

    /**
     * บันทึก PetMemory ลง SQLite
     */
    fun save(memory: PetMemory = _memoryFlow.value) {
        val updated = memory.copy(lastSaveTimestamp = System.currentTimeMillis())
        _memoryFlow.value = updated
        try {
            val db = JarvisDatabaseHolder.database ?: return
            val jsonStr = json.encodeToString(updated)
            db.jarvisDatabaseQueries.insertSetting(KEY, jsonStr)
            logDebug(TAG, "💾 Saved PetMemory (${jsonStr.length} bytes)")
        } catch (e: Exception) {
            logDebug(TAG, "⚠️ Failed to save PetMemory: ${e.message}")
        }
    }

    /**
     * บันทึก interaction ลง memory
     */
    fun logInteraction(
        type: InteractionType,
        zone: TouchZone = TouchZone.FACE_CENTER,
        resultingEmotion: String = "idle"
    ) {
        val entry = InteractionEntry(
            type = type.name.lowercase(),
            zone = zone.name.lowercase(),
            timestamp = System.currentTimeMillis(),
            resultingEmotion = resultingEmotion
        )

        val cur = _memoryFlow.value
        val recentList = (cur.recentInteractions + entry).takeLast(20)

        var updated = cur.copy(
            sessionInteractions = cur.sessionInteractions + 1,
            totalInteractions = cur.totalInteractions + 1,
            recentInteractions = recentList,
            sessionPokes = if (type == InteractionType.POKE) cur.sessionPokes + 1 else cur.sessionPokes,
            sessionPets = if (type == InteractionType.PET_HEAD || type == InteractionType.CHIN_SCRATCH) cur.sessionPets + 1 else cur.sessionPets,
            sessionFeeds = if (type == InteractionType.FEED) cur.sessionFeeds + 1 else cur.sessionFeeds,
            sessionCleans = if (type == InteractionType.CLEAN) cur.sessionCleans + 1 else cur.sessionCleans,
            sessionPlays = if (type == InteractionType.PLAY) cur.sessionPlays + 1 else cur.sessionPlays,
            totalPokes = if (type == InteractionType.POKE) cur.totalPokes + 1 else cur.totalPokes,
            totalPets = if (type == InteractionType.PET_HEAD || type == InteractionType.CHIN_SCRATCH) cur.totalPets + 1 else cur.totalPets,
            totalFeeds = if (type == InteractionType.FEED) cur.totalFeeds + 1 else cur.totalFeeds,
            totalCleans = if (type == InteractionType.CLEAN) cur.totalCleans + 1 else cur.totalCleans,
            totalPlays = if (type == InteractionType.PLAY) cur.totalPlays + 1 else cur.totalPlays,
            timesGotAngry = if (resultingEmotion == "angry") cur.timesGotAngry + 1 else cur.timesGotAngry,
            timesGotSurprised = if (resultingEmotion == "surprised") cur.timesGotSurprised + 1 else cur.timesGotSurprised
        )

        // อัปเดต favorite interaction
        val counts = mapOf(
            "pet_head" to updated.totalPets,
            "poke" to updated.totalPokes,
            "feed" to updated.totalFeeds,
            "clean" to updated.totalCleans,
            "play" to updated.totalPlays
        )
        val max = counts.maxByOrNull { it.value }
        val min = counts.minByOrNull { it.value }
        if (max != null) updated = updated.copy(favoriteInteraction = max.key)
        if (min != null) updated = updated.copy(leastFavoriteInteraction = min.key)

        _memoryFlow.value = updated
    }

    /**
     * อัปเดต NeedsState ที่บันทึกไว้
     */
    fun saveNeedsState(needs: PetNeedsState) {
        _memoryFlow.value = _memoryFlow.value.copy(savedNeedsState = needs)
    }

    /**
     * รีเซ็ต PetMemory ทั้งหมด (สำหรับ New Game)
     */
    fun reset() {
        _memoryFlow.value = PetMemory()
        save()
    }
}
