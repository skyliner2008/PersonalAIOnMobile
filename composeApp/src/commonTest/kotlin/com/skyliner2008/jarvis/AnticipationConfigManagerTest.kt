package com.skyliner2008.jarvis

import com.skyliner2008.jarvis.automation.AnticipationConfigManager
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
    fun testDefaultFactors_containsAllThirteenFactors() {
        val factors = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(13, factors.size)
        assertTrue(factors.contains("KEYZONE_PROXIMITY"))
        assertTrue(factors.contains("WICK_SWEEP_REJECTION"))
        assertTrue(factors.contains("RSI_EXTREME"))
        assertTrue(factors.contains("EMA_NEAR_CROSS"))
        assertTrue(factors.contains("VEYRA_SHIFT"))
        assertTrue(factors.contains("BB_KC_SQUEEZE"))
        assertTrue(factors.contains("FAST_RSI_REVERSAL"))
    }

    @Test
    fun testAddFactor_validFactorAfterRemoval() {
        AnticipationConfigManager.removeFactor("XAUUSD", "BOLLINGER_SQUEEZE")
        assertEquals(12, AnticipationConfigManager.getActiveFactors("XAUUSD").size)
        val success = AnticipationConfigManager.addFactor("XAUUSD", "BOLLINGER_SQUEEZE")
        assertTrue(success, "Should successfully add valid factor from whitelist")
        val factors = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(13, factors.size)
        assertTrue(factors.contains("BOLLINGER_SQUEEZE"))
    }

    @Test
    fun testAddFactor_invalidArbitraryFactorRejected() {
        val success = AnticipationConfigManager.addFactor("XAUUSD", "RANDOM_HALLUCINATED_FACTOR")
        assertFalse(success, "Should reject factor not in curated whitelist")
        val factors = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(13, factors.size)
    }

    @Test
    fun testRemoveFactor_removesFactor() {
        val success = AnticipationConfigManager.removeFactor("XAUUSD", "RSI_EXTREME")
        assertTrue(success)
        val factors = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(12, factors.size)
        assertFalse(factors.contains("RSI_EXTREME"))
    }

    @Test
    fun testResetToDefaults_restoresAllFactors() {
        AnticipationConfigManager.removeFactor("XAUUSD", "EMA_NEAR_CROSS")
        AnticipationConfigManager.removeFactor("XAUUSD", "VOLUME_ABSORPTION")
        assertEquals(11, AnticipationConfigManager.getActiveFactors("XAUUSD").size)

        AnticipationConfigManager.resetToDefaults("XAUUSD")
        val restored = AnticipationConfigManager.getActiveFactors("XAUUSD")
        assertEquals(13, restored.size)
        assertTrue(restored.contains("EMA_NEAR_CROSS"))
        assertTrue(restored.contains("VOLUME_ABSORPTION"))
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
        assertTrue(bbInfo.second) // active by default
    }

    @Test
    fun testGetRecommendedFactors() {
        val recs = AnticipationConfigManager.getRecommendedFactors("XAUUSD", "VOLATILE_BREAKOUT")
        assertTrue(recs.isNotEmpty())
        assertTrue(recs.contains("SESSION_OPEN_SWEEP") || recs.contains("FIBONACCI_GOLDEN_POCKET"))
    }
}
