package com.example.personalaibot

import com.example.personalaibot.data.GeminiModel
import com.example.personalaibot.data.ModelConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicModelTest {

    @BeforeTest
    fun setUp() {
        ModelConfig.resetForTesting()
    }

    @AfterTest
    fun tearDown() {
        ModelConfig.resetForTesting()
    }

    @Test
    fun testUpdateAvailableModels_FiltersAndPrioritizesFlash() {
        val sampleModels = listOf(
            GeminiModel(
                name = "models/text-embedding-004",
                displayName = "Text Embedding 004",
                supportedGenerationMethods = listOf("embedContent")
            ),
            GeminiModel(
                name = "models/gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                supportedGenerationMethods = listOf("generateContent")
            ),
            GeminiModel(
                name = "models/gemini-3.8-flash",
                displayName = "Gemini 3.8 Flash",
                supportedGenerationMethods = listOf("generateContent", "countTokens")
            ),
            GeminiModel(
                name = "models/gemini-3.6-flash",
                displayName = "Gemini 3.6 Flash",
                supportedGenerationMethods = listOf("generateContent", "countTokens")
            ),
            GeminiModel(
                name = "models/gemini-3.1-flash-lite",
                displayName = "Gemini 3.1 Flash Lite",
                supportedGenerationMethods = listOf("generateContent", "countTokens")
            ),
            GeminiModel(
                name = "models/imagen-3.0-generate-002",
                displayName = "Imagen 3.0",
                supportedGenerationMethods = listOf("predict")
            )
        )

        ModelConfig.updateAvailableModels(sampleModels)

        val active = ModelConfig.getActiveGeminiModels()

        // Embedding and Imagen must be excluded
        assertFalse(active.contains("text-embedding-004"))
        assertFalse(active.contains("imagen-3.0-generate-002"))

        // Flash and Pro with generateContent must be included
        assertTrue(active.contains("gemini-3.8-flash"))
        assertTrue(active.contains("gemini-3.6-flash"))
        assertTrue(active.contains("gemini-3.1-flash-lite"))
        assertTrue(active.contains("gemini-2.5-pro"))

        // Flash models must be prioritized before Pro models, and 3.8 before 3.6
        val flash38Idx = active.indexOf("gemini-3.8-flash")
        val flash36Idx = active.indexOf("gemini-3.6-flash")
        val pro25Idx = active.indexOf("gemini-2.5-pro")

        assertTrue(flash38Idx < flash36Idx, "3.8-flash should appear before 3.6-flash")
        assertTrue(flash36Idx < pro25Idx, "Flash models should appear before Pro models")
    }

    @Test
    fun testDeadModelBlacklisting() {
        // Mark invalid/non-existent model as dead
        ModelConfig.markModelDead("gemini-3.1-pro")

        assertTrue(ModelConfig.isModelDead("gemini-3.1-pro"))
        assertTrue(ModelConfig.isModelDead("models/gemini-3.1-pro"))

        val fallbackChain = ModelConfig.getFallbackChain("gemini-3.1-pro")
        assertFalse(fallbackChain.contains("gemini-3.1-pro"), "Dead model must NOT be in fallback chain")
        assertTrue(fallbackChain.isNotEmpty(), "Fallback chain should have alternative models")
    }

    @Test
    fun testFallbackChainIncludesPrimaryWhenAlive() {
        val primary = "gemini-3.6-flash"
        val chain = ModelConfig.getFallbackChain(primary)
        assertEquals(primary, chain.firstOrNull(), "Live primary model should be first in chain")
    }

    @Test
    fun testGetBestActiveModel() {
        val best = ModelConfig.getBestActiveModel()
        assertTrue(best.isNotBlank())
        assertFalse(ModelConfig.isModelDead(best))
    }

    @Test
    fun testLiveFallbackChain_PrioritizesPrimaryAndExcludesDead() {
        val chain = ModelConfig.getLiveFallbackChain("gemini-3.1-flash-live-preview")
        assertTrue(chain.isNotEmpty(), "Live fallback chain should not be empty")
        assertEquals("gemini-3.1-flash-live-preview", chain.first(), "Primary live model should be first")
        assertTrue(chain.any { it.contains("2.5-flash-native-audio") }, "Fallback chain must contain 2.5 flash native audio fallback")

        // Mark a model as dead and verify it is filtered out
        ModelConfig.markModelDead("gemini-3.1-flash-live-preview")
        val chainAfterDead = ModelConfig.getLiveFallbackChain("gemini-3.1-flash-live-preview")
        assertFalse(chainAfterDead.contains("gemini-3.1-flash-live-preview"), "Dead live model must be excluded from chain")
        assertTrue(chainAfterDead.first().contains("2.5-flash-native-audio"), "Chain must fall back to next live model")
    }

    @Test
    fun testLiveSystemPrompt_ContainsConsistentThaiGreeting() {
        val livePrompt = com.example.personalaibot.ai.JarvisPersona.LIVE_SYSTEM_PROMPT
        assertTrue(livePrompt.contains("จาวิสพร้อมคุยแล้ว"), "Live prompt must instruct fixed Thai greeting")
        assertTrue(livePrompt.contains("การออกเสียงและสำเนียงภาษาไทย"), "Live prompt must enforce natural standard Thai articulation")
    }

    @Test
    fun testDefaultLiveModel_IsGemini31FlashLivePreview() {
        assertEquals("gemini-3.1-flash-live-preview", ModelConfig.DEFAULT_LIVE_MODEL)
    }

    @Test
    fun testPromoteHealthyLiveModel() {
        val model = "gemini-2.5-flash-native-audio-latest"
        ModelConfig.promoteHealthyLiveModel(model)
        val chain = ModelConfig.getLiveFallbackChain()
        assertEquals(model, chain.first(), "Promoted model should be first in chain")
    }

    @Test
    fun testPenalizeLiveModel_DemotesToEnd() {
        val target = "gemini-2.5-flash-native-audio-preview-09-2025"
        ModelConfig.penalizeLiveModel(target, durationMs = 60_000L)
        assertTrue(ModelConfig.isLiveModelPenalized(target))

        val chain = ModelConfig.getLiveFallbackChain(target)
        // Even when passed as primary, penalized model should NOT be first
        assertFalse(chain.first() == target, "Penalized model should not be first in fallback chain")
        assertTrue(chain.contains(target), "Penalized model should still be in chain as fallback")
        assertEquals(target, chain.last(), "Penalized model should be moved to the tail")
    }
}
