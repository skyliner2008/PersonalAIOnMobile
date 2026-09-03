package com.example.personalaibot

import kotlinx.datetime.*
import kotlin.test.Test
import kotlin.test.assertEquals

class EconomicCalendarTimeTest {

    @Test
    fun testIsmServicesPmiThailandTimeConversion() {
        // ForexFactory JSON date format: "2026-09-03T10:00:00-04:00" (10:00am EDT = 14:00 UTC)
        val isoDateStr = "2026-09-03T10:00:00-04:00"
        val instant = Instant.parse(isoDateStr)
        val bkk = instant.toLocalDateTime(TimeZone.of("Asia/Bangkok"))

        assertEquals(2026, bkk.year)
        assertEquals(9, bkk.monthNumber)
        assertEquals(3, bkk.dayOfMonth)
        assertEquals(DayOfWeek.THURSDAY, bkk.dayOfWeek)
        assertEquals(21, bkk.hour, "Should be 21:00 (9:00pm Thailand time)")
        assertEquals(0, bkk.minute)
    }

    @Test
    fun testNonFarmPayrollsThailandTimeConversion() {
        // NFP released at 8:30am EDT (UTC-4) = 12:30 UTC = 19:30 (7:30pm) in Thailand (UTC+7)
        val isoDateStr = "2026-09-04T08:30:00-04:00"
        val instant = Instant.parse(isoDateStr)
        val bkk = instant.toLocalDateTime(TimeZone.of("Asia/Bangkok"))

        assertEquals(2026, bkk.year)
        assertEquals(9, bkk.monthNumber)
        assertEquals(4, bkk.dayOfMonth)
        assertEquals(DayOfWeek.FRIDAY, bkk.dayOfWeek)
        assertEquals(19, bkk.hour, "Should be 19:30 (7:30pm Thailand time)")
        assertEquals(30, bkk.minute)
    }

    @Test
    fun testIsmManufacturingPmiThailandTimeConversion() {
        // ISM Manufacturing released at 10:00am EDT on Sep 1 = 14:00 UTC = 21:00 on Sep 1 in Thailand
        val isoDateStr = "2026-09-01T10:00:00-04:00"
        val instant = Instant.parse(isoDateStr)
        val bkk = instant.toLocalDateTime(TimeZone.of("Asia/Bangkok"))

        assertEquals(2026, bkk.year)
        assertEquals(9, bkk.monthNumber)
        assertEquals(1, bkk.dayOfMonth, "Must be Sep 1, NOT Sep 2")
        assertEquals(DayOfWeek.TUESDAY, bkk.dayOfWeek)
        assertEquals(21, bkk.hour, "Should be 21:00 (9:00pm)")
        assertEquals(0, bkk.minute)
    }

    @Test
    fun testXmlUtcTimeParsingToBangkokTime() {
        // In ForexFactory XML feed, date is "09-03-2026" and time is "2:00pm" (which is 14:00 UTC)
        val date = "09-03-2026"
        val time = "2:00pm"
        val dp = date.split("-")
        val iso = "${dp[2]}-${dp[0]}-${dp[1]}"
        val ldt = LocalDateTime.parse("${iso}T14:00:00")
        val instant = ldt.toInstant(TimeZone.UTC)
        val bkk = instant.toLocalDateTime(TimeZone.of("Asia/Bangkok"))

        assertEquals(3, bkk.dayOfMonth)
        assertEquals(21, bkk.hour, "14:00 UTC + 7 = 21:00 Bangkok")
    }
}
