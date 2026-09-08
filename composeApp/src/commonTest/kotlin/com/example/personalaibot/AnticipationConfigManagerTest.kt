package com.example.personalaibot

import com.example.personalaibot.automation.AnticipationConfigManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnticipationConfigManagerTest {

    @BeforeTest
    fun setup() {
        AnticipationConfigManager.resetAll()
    }

    @Test
    fun testDefaultFactors_containsCoreFourFactors() {
        val factors = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(4, factors.size)
        assertTrue(factors.contains("KEYZONE_PROXIMITY"))
        assertTrue(factors.contains("WICK_SWEEP_REJECTION"))
        assertTrue(factors.contains("RSI_EXTREME"))
        assertTrue(factors.contains("EMA_NEAR_CROSS"))
    }

    @Test
    fun testAddFactor_validExtendedFactor() {
        val success = AnticipationConfigManager.addFactor("XAUUSD", "BOLLINGER_SQUEEZE")
        assertTrue(success, "Should successfully add valid factor from whitelist")
        val factors = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(5, factors.size)
        assertTrue(factors.contains("BOLLINGER_SQUEEZE"))
    }

    @Test
    fun testAddFactor_invalidArbitraryFactorRejected() {
        val success = AnticipationConfigManager.addFactor("XAUUSD", "RANDOM_HALLUCINATED_FACTOR")
        assertFalse(success, "Should reject factor not in curated whitelist")
        val factors = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(4, factors.size)
    }

    @Test
    fun testRemoveFactor_removesCoreFactor() {
        val success = AnticipationConfigManager.removeFactor("XAUUSD", "RSI_EXTREME")
        assertTrue(success)
        val factors = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(3, factors.size)
        assertFalse(factors.contains("RSI_EXTREME"))
    }

    @Test
    fun testResetToDefaults_restoresCoreFactors() {
        AnticipationConfigManager.addFactor("XAUUSD", "VOLUME_ABSORPTION")
        AnticipationConfigManager.removeFactor("XAUUSD", "EMA_NEAR_CROSS")
        assertEquals(4, AnticipationConfigManager.getActiveFactors("XAUUSD").size)

        AnticipationConfigManager.resetToDefaults("XAUUSD")
        val restored = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(4, restored.size)
        assertTrue(restored.contains("EMA_NEAR_CROSS"))
        assertFalse(restored.contains("VOLUME_ABSORPTION"))
    }

    @Test
    fun testListFactorsWithStatus() {
        val list = AnticipationConfigManager.listFactorsWithStatus("XAUUSD")
        assertEquals(AnticipationConfigManager.ALL_FACTORS.size, list.size)
        val rsiInfo = list.firstOrNull { it.first.id == "RSI_EXTREME" }
        assertNotNull(rsiInfo)
        assertTrue(rsiInfo.second) // active
        assertEquals("RSI Extreme / Divergence", rsiInfo.first.name)

        val bbInfo = list.firstOrNull { it.first.id == "BOLLINGER_SQUEEZE" }
        assertNotNull(bbInfo)
        assertFalse(bbInfo.second) // inactive by default
    }

    @Test
    fun testGetRecommendedFactors() {
        val recs = AnticipationConfigManager.getRecommendedFactors("XAUUSD", "VOLATILE_BREAKOUT")
        assertTrue(recs.isNotEmpty())
        assertTrue(recs.contains("SESSION_OPEN_SWEEP") || recs.contains("FIBONACCI_GOLDEN_POCKET"))
    }
}
