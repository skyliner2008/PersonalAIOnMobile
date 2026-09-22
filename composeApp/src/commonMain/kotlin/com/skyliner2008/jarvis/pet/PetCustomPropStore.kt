package com.skyliner2008.jarvis.pet

import com.skyliner2008.jarvis.db.JarvisDatabaseHolder
import com.skyliner2008.jarvis.logDebug
import com.skyliner2008.jarvis.ui.component.avatar.DynamicVectorProp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * PetCustomPropStore — ระบบจัดเก็บอุปกรณ์เสริมเวกเตอร์ SVG ไดนามิกแบบถาวร (Persistent Storage)
 * บันทึกลงตาราง AppSetting (SQLite / SQLDelight) เพื่อให้พร็อพที่ AI เสกหรือสร้างไว้คงอยู่ข้ามการปิด-เปิดแอป
 * และสามารถนำกลับมาสวมใส่ซ้ำได้ทันทีโดยไม่ต้องวาดโค้ด SVG ซ้ำ
 */
object PetCustomPropStore {
    private const val TAG = "PetCustomPropStore"
    private const val SETTING_KEY = "pet.custom_props"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _savedCustomProps = MutableStateFlow<List<DynamicVectorProp>>(emptyList())
    val savedCustomProps: StateFlow<List<DynamicVectorProp>> = _savedCustomProps.asStateFlow()

    init {
        loadCustomProps()
    }

    /**
     * โหลดรายการพร็อพที่เคยบันทึกไว้ทั้งหมดจากฐานข้อมูล SQLite
     */
    fun loadCustomProps(): List<DynamicVectorProp> {
        return try {
            val db = JarvisDatabaseHolder.database ?: return _savedCustomProps.value
            val rawJson = db.jarvisDatabaseQueries.getSetting(SETTING_KEY).executeAsOneOrNull()
            if (!rawJson.isNullOrBlank()) {
                val list = json.decodeFromString<List<DynamicVectorProp>>(rawJson)
                _savedCustomProps.value = list
                logDebug(TAG, "📂 Loaded ${list.size} custom props from SQLite")
                list
            } else {
                _savedCustomProps.value = emptyList()
                emptyList()
            }
        } catch (e: Exception) {
            logDebug(TAG, "⚠️ Failed to load custom props: ${e.message}")
            _savedCustomProps.value
        }
    }

    /**
     * บันทึกหรืออัปเดต Dynamic Vector Prop ลงฐานข้อมูลอย่างถาวร
     */
    fun saveCustomProp(prop: DynamicVectorProp) {
        try {
            if (_savedCustomProps.value.isEmpty()) {
                loadCustomProps()
            }
            val currentList = _savedCustomProps.value.toMutableList()
            currentList.removeAll { it.id == prop.id || it.name.equals(prop.name, ignoreCase = true) }
            currentList.add(prop)
            _savedCustomProps.value = currentList

            val db = JarvisDatabaseHolder.database
            if (db != null) {
                val encoded = json.encodeToString(currentList)
                db.jarvisDatabaseQueries.insertSetting(SETTING_KEY, encoded)
                logDebug(TAG, "💾 Saved custom prop '${prop.name}' (${prop.id}) to SQLite (Total: ${currentList.size})")
            }
        } catch (e: Exception) {
            logDebug(TAG, "❌ Error saving custom prop: ${e.message}")
        }
    }

    /**
     * ลบ Dynamic Vector Prop ออกจากฐานข้อมูลถาวร
     */
    fun deleteCustomProp(nameOrId: String) {
        try {
            if (_savedCustomProps.value.isEmpty()) {
                loadCustomProps()
            }
            val currentList = _savedCustomProps.value.toMutableList()
            val removed = currentList.removeAll {
                it.id.equals(nameOrId, ignoreCase = true) || it.name.equals(nameOrId, ignoreCase = true)
            }
            if (removed) {
                _savedCustomProps.value = currentList
                val db = JarvisDatabaseHolder.database
                if (db != null) {
                    val encoded = json.encodeToString(currentList)
                    db.jarvisDatabaseQueries.insertSetting(SETTING_KEY, encoded)
                    logDebug(TAG, "🗑️ Deleted custom prop '$nameOrId' from SQLite (Remaining: ${currentList.size})")
                }
            }
        } catch (e: Exception) {
            logDebug(TAG, "❌ Error deleting custom prop: ${e.message}")
        }
    }

    /**
     * ล้างพร็อพที่บันทึกไว้ทั้งหมด
     */
    fun clearCustomProps() {
        try {
            _savedCustomProps.value = emptyList()
            val db = JarvisDatabaseHolder.database
            db?.jarvisDatabaseQueries?.insertSetting(SETTING_KEY, "[]")
            logDebug(TAG, "🧹 Cleared all saved custom props")
        } catch (e: Exception) {
            logDebug(TAG, "❌ Error clearing custom props: ${e.message}")
        }
    }

    /**
     * ค้นหาพร็อพที่บันทึกไว้ตามชื่อหรือ id
     */
    fun findPropByNameOrId(nameOrId: String): DynamicVectorProp? {
        val clean = nameOrId.trim()
        if (clean.isBlank()) return null
        val inMemory = _savedCustomProps.value.firstOrNull {
            it.id.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
        }
        if (inMemory != null) return inMemory
        return loadCustomProps().firstOrNull {
            it.id.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
        }
    }
}
