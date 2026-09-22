package com.skyliner2008.jarvis.controller

import com.skyliner2008.jarvis.ai.LiveIntentMatchers
import com.skyliner2008.jarvis.ui.component.avatar.AvatarEmotion
import com.skyliner2008.jarvis.ui.component.avatar.LooiMoodsetCatalog

/**
 * คำสั่งลัดฝั่งเครื่อง (ไม่รอ model) ที่ VoiceController ตรวจจากประโยคของผู้ใช้
 *
 * ต้องเรียกกับ "ประโยคที่พูดจบแล้ว" เท่านั้น — เดิมตรวจทุกชิ้นของ transcript ระหว่างพูด
 * ทำให้คำสั่งยิงซ้ำหลายรอบ (เช่น ส่งข้อความตอบกลับ LINE เป็นท่อนๆ) (review 2026-09-16)
 *
 * การเทียบคำใช้ [LiveIntentMatchers.matchesAny] เพราะ Live transcription แทรกช่องว่างระหว่างคำไทย
 * ("เปิด โหมด สัตว์ เลี้ยง") ทำให้การ contains ตรงๆ พลาด
 *
 * คำสั่งที่มีผลภายนอก (ส่งข้อความตอบกลับ) ไม่อยู่ที่นี่ — ให้ model เรียก tool เองเพื่อยืนยันกับผู้ใช้
 */
object LiveLocalCommandParser {

    sealed interface AvatarCommand {
        data object PlayAll : AvatarCommand
        data class Page(val page: Int) : AvatarCommand
        data object Demo : AvatarCommand
        data object Reset : AvatarCommand
        data class Emotion(val emotion: AvatarEmotion) : AvatarCommand
    }

    data class AlwaysLiveCommand(val turnOn: Boolean, val mode: String)

    data class Result(
        val avatar: AvatarCommand? = null,
        val alwaysLive: AlwaysLiveCommand? = null,
        /** true = ลืมตา/เปิดกล้อง, false = หลับตา/ปิดกล้อง, null = ไม่มีคำสั่ง */
        val eyeOpen: Boolean? = null
    )

    private fun hit(text: String, vararg terms: String): Boolean =
        LiveIntentMatchers.matchesAny(text, terms.toList())

    fun parse(text: String): Result {
        if (text.isBlank()) return Result()
        return Result(
            avatar = parseAvatar(text),
            alwaysLive = parseAlwaysLive(text),
            eyeOpen = parseEye(text)
        )
    }

    private fun parseAvatar(text: String): AvatarCommand? {
        val compact = LiveIntentMatchers.normalize(text)
        val isPlayAll = LooiMoodsetCatalog.isPlayAllCommand(text) || LooiMoodsetCatalog.isPlayAllCommand(compact)
        val pageNumber = LooiMoodsetCatalog.parsePageNumber(text) ?: LooiMoodsetCatalog.parsePageNumber(compact)
        val isDemo = isPlayAll || hit(
            text,
            "ทดสอบเดโม", "เดโม", "demo", "ทดสอบระบบ", "ทดสอบหุ่นยนต์", "โชว์หุ่นยนต์",
            "แสดงเดโม", "แสดงอารมณ์ทั้งหมด", "โชว์อารมณ์", "ทดสอบอารมณ์", "avatar demo"
        )
        val isReset = hit(
            text,
            "หยุดเดโม", "หยุดทดสอบ", "รีเซ็ต", "avatar reset", "กลับสู่โหมดปกติ", "โหมดปกติ"
        ) || compact.startsWith("หยุด")

        return when {
            isPlayAll -> AvatarCommand.PlayAll
            pageNumber != null -> AvatarCommand.Page(pageNumber)
            isDemo -> AvatarCommand.Demo
            isReset -> AvatarCommand.Reset
            hit(text, "ทำหน้า", "สีหน้า", "ยิ้มหน่อย") -> {
                val emotion = when {
                    hit(text, "ดีใจ", "ยิ้ม", "มีความสุข") -> AvatarEmotion.HAPPY
                    hit(text, "ตื่นเต้น", "ดาว") -> AvatarEmotion.EXCITED
                    hit(text, "รัก", "หัวใจ") -> AvatarEmotion.LOVE
                    hit(text, "โกรธ", "โมโห") -> AvatarEmotion.ANGRY
                    hit(text, "เศร้า", "เสียใจ", "ร้องไห้") -> AvatarEmotion.SAD
                    hit(text, "ง่วง", "นอน", "หลับ") -> AvatarEmotion.SLEEPING
                    hit(text, "กำลังคิด", "คิด", "สงสัย") -> AvatarEmotion.THINKING
                    else -> null
                }
                emotion?.let { AvatarCommand.Emotion(it) }
            }
            else -> null
        }
    }

    private fun parseAlwaysLive(text: String): AlwaysLiveCommand? {
        // "เปิด" มี "ปิด" เป็น substring — เดิม "เปิดโหมดสัตว์เลี้ยง" จึงถูกตีความเป็นคำสั่งปิดโหมด
        val withoutOpen = LiveIntentMatchers.normalize(text).replace("เปิด", "")
        val isOff = listOf("ปิดโหมด", "ออกจากโหมด").any { prefix ->
            listOf("ควบคุม", "ขับขี่", "รถยนต์", "สัตว์เลี้ยง").any { withoutOpen.contains(prefix + it) }
        }
        val isOn = !isOff && hit(
            text,
            "โหมดควบคุม", "โหมดขับขี่", "โหมดขับรถ", "โหมดรถยนต์", "โหมดสัตว์เลี้ยง", "โหมดแก้เบื่อ", "pet mode", "drive mode"
        )
        if (!isOn && !isOff) return null
        val mode = when {
            hit(text, "สัตว์เลี้ยง", "แก้เบื่อ", "pet") -> "pet"
            hit(text, "ขับขี่", "ขับรถ", "รถยนต์", "drive") -> "drive"
            else -> "control"
        }
        return AlwaysLiveCommand(turnOn = isOn, mode = mode)
    }

    private val EYE_OPEN_TERMS = listOf(
        "ลืมตา", "เปิดกล้อง", "เปิดตา",
        "นี่คืออะไร", "นี้คืออะไร", "นี่อะไร", "นี้อะไร", "อะไรนี่", "อะไรนี้",
        "ดูนี่", "ดูนี้", "ดูอันนี้", "มองอันนี้",
        "ช่วยดู", "ดูหน่อย", "มองหน่อย", "มองซิ", "มองดู", "ส่องดู", "ส่องหน่อย", "ตรวจดู",
        // ("อ่านข้อความ" ถูกตัดออก — ชนกับคำสั่งอ่านแจ้งเตือน)
        "อ่านนี่", "อ่านตรงนี้",
        "เห็นมั้ย", "เห็นไหม", "เห็นอะไร",
        "กี่นิ้ว", "ชูกี่นิ้ว", "ชูนิ้ว", "โชว์กี่นิ้ว",
        "ดูมาอีก", "ดูอีก", "ดูใหม่",
        "อันนี้กี่นิ้ว", "อันนี้คืออะไร", "อันนี้อะไร",
        "ถืออยู่", "ถืออะไร", "ถืออะไรอยู่",
        "สีอะไร", "ตัวอะไร", "ท่าอะไร",
        "what is this", "what's this", "look at this", "see this", "open camera", "open your eyes",
        "how many fingers"
    )

    private val EYE_CLOSE_TERMS = listOf(
        "หลับตา", "ปิดกล้อง", "ปิดตา", "พอแล้ว", "หยุดดู", "close camera", "close your eyes"
    )

    private fun parseEye(text: String): Boolean? {
        // "ปิดตา" / "ปิดกล้อง" เป็น substring ของ "เปิดตา" / "เปิดกล้อง" — ตัด "เปิด" ออกก่อนตรวจคำสั่งปิด
        val withoutOpen = LiveIntentMatchers.normalize(text).replace("เปิด", "")
        return when {
            EYE_CLOSE_TERMS.any { withoutOpen.contains(LiveIntentMatchers.normalize(it)) } -> false
            LiveIntentMatchers.matchesAny(text, EYE_OPEN_TERMS) -> true
            else -> null
        }
    }
}
