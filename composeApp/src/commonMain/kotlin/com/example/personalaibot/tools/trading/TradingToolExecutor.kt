package com.example.personalaibot.tools.trading

import io.ktor.client.HttpClient
import com.example.personalaibot.data.GeminiService

/** Stable public facade for trading tools; implementation is isolated in TradingToolExecutionBackend. */
class TradingToolExecutor(client: HttpClient, geminiService: GeminiService) {
    private val backend = TradingToolExecutionBackend(client, geminiService)

    suspend fun execute(toolName: String, args: Map<String, String>): String =
        backend.execute(toolName, args)
}
