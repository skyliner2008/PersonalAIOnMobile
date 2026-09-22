package com.skyliner2008.jarvis.tools.trading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * เวลาเปิด/ปิดตลาด — ใช้ข้ามงานทั้งหมด (ดึงแท่งเทียน / เรียก AI) ตอนตลาดปิด
 *
 * อิงเวลานิวยอร์ก (รองรับ Daylight Saving อัตโนมัติ) ตามรอบของ OANDA / TradingView:
 *  - FX:        เปิดอาทิตย์ 17:00 → ปิดศุกร์ 17:00
 *  - ทอง/เงิน:  เปิดอาทิตย์ 18:00 → ปิดศุกร์ 17:00 + พักทุกวัน จันทร์–พฤหัส 17:00–18:00
 *  - คริปโต:    เปิดตลอด 24/7
 *  - อื่นๆ:     ไม่รู้ตารางเวลา → ถือว่าเปิด (ไม่ข้ามงาน)
 * ยังไม่รวมวันหยุดพิเศษ (คริสต์มาส/ปีใหม่) — วันนั้นระบบจะสแกนแต่ไม่เจอแท่งใหม่
 */
object MarketHours {
    enum class Kind { FX, METAL, CRYPTO, OTHER }

    data class Status(
        val open: Boolean,
        val kind: Kind,
        /** เหตุผลตอนปิด (ภาษาไทย) */
        val reasonTh: String? = null,
        /** นาทีที่เหลือก่อนปิดสิ้นสัปดาห์ (เฉพาะวันศุกร์ที่ยังเปิด) */
        val minutesToWeeklyClose: Long? = null,
        /** เวลาเปิดครั้งถัดไป (UTC ms) ตอนปิดอยู่ */
        val nextOpenMs: Long? = null
    )

    private const val CLOSE_HOUR = 17          // ปิดสิ้นสัปดาห์ / เริ่มพักรายวัน (เวลานิวยอร์ก)
    private const val FX_OPEN_HOUR = 17        // FX เปิดอาทิตย์
    private const val METAL_OPEN_HOUR = 18     // ทองเปิดอาทิตย์ / จบพักรายวัน

    private val NEW_YORK: TimeZone = runCatching { TimeZone.of("America/New_York") }
        .getOrElse { UtcOffset(hours = -5).asTimeZone() }

    private val FIAT = setOf("USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD")

    fun kindOf(symbol: String): Kind {
        val s = symbol.uppercase().substringBefore("@").substringAfter(":").replace("/", "").replace("-", "")
        return when {
            s.contains("XAU") || s.contains("XAG") || s.contains("GOLD") || s.contains("SILVER") -> Kind.METAL
            s.length == 6 && s.take(3) in FIAT && s.drop(3) in FIAT -> Kind.FX
            s.endsWith("USDT") || s.endsWith("USDC") || s.startsWith("BTC") || s.startsWith("ETH") -> Kind.CRYPTO
            else -> Kind.OTHER
        }
    }

    fun status(symbol: String, nowMs: Long): Status {
        val kind = kindOf(symbol)
        if (kind == Kind.CRYPTO || kind == Kind.OTHER) return Status(open = true, kind = kind)
        val ny = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(NEW_YORK)
        val minuteOfDay = ny.hour * 60 + ny.minute
        val openHour = if (kind == Kind.METAL) METAL_OPEN_HOUR else FX_OPEN_HOUR

        fun nextSundayOpen(): Long {
            val daysAhead = (7 - ny.dayOfWeek.ordinal + DayOfWeek.SUNDAY.ordinal) % 7
            val date = ny.date.plus(daysAhead, DateTimeUnit.DAY)
            return LocalDateTime(date, LocalTime(openHour, 0)).toInstant(NEW_YORK).toEpochMilliseconds()
        }
        val weekendReason = "ตลาดปิดสุดสัปดาห์ (เปิดคืนวันอาทิตย์ ${openHour}:00 เวลานิวยอร์ก)"

        return when {
            ny.dayOfWeek == DayOfWeek.SATURDAY ->
                Status(false, kind, weekendReason, nextOpenMs = nextSundayOpen())
            ny.dayOfWeek == DayOfWeek.SUNDAY && ny.hour < openHour ->
                Status(false, kind, weekendReason, nextOpenMs = nextSundayOpen())
            ny.dayOfWeek == DayOfWeek.FRIDAY && ny.hour >= CLOSE_HOUR ->
                Status(false, kind, weekendReason, nextOpenMs = nextSundayOpen())
            kind == Kind.METAL && ny.dayOfWeek != DayOfWeek.FRIDAY && ny.dayOfWeek != DayOfWeek.SUNDAY &&
                ny.hour == CLOSE_HOUR -> Status(
                false, kind, "ทองพักการซื้อขายรายวัน 17:00–18:00 เวลานิวยอร์ก",
                nextOpenMs = LocalDateTime(ny.date, LocalTime(METAL_OPEN_HOUR, 0)).toInstant(NEW_YORK).toEpochMilliseconds()
            )
            ny.dayOfWeek == DayOfWeek.FRIDAY ->
                Status(true, kind, minutesToWeeklyClose = (CLOSE_HOUR * 60 - minuteOfDay).toLong())
            else -> Status(true, kind)
        }
    }
}
