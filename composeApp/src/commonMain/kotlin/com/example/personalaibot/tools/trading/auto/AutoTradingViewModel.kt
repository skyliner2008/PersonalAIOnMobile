package com.example.personalaibot.tools.trading.auto

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoTradingViewModel(
    private val scope: CoroutineScope,
    client: HttpClient,
    bridgeBaseUrlProvider: () -> String,
    authTokenProvider: () -> String,
    pairingStatusProvider: () -> String,
) {
    private val remote = AutoTradingRemoteService(
        client = client,
        bridgeBaseUrlProvider = bridgeBaseUrlProvider,
        authTokenProvider = authTokenProvider
    )
    private val isApproved: () -> Boolean = {
        pairingStatusProvider().equals("APPROVED", ignoreCase = true) && authTokenProvider().isNotBlank()
    }

    private val _config = MutableStateFlow(AutoTradingEngine.Config())
    val config: StateFlow<AutoTradingEngine.Config> = _config.asStateFlow()

    private val _state = MutableStateFlow(
        AutoTradingEngine.EngineState(
            running = false,
            phase = AutoTradingEngine.Phase.STOPPED,
            message = "Waiting for MT5 server approval"
        )
    )
    val state: StateFlow<AutoTradingEngine.EngineState> = _state.asStateFlow()

    private val _lastComposite = MutableStateFlow<Map<String, CompositeAnalysis>>(emptyMap())
    val lastComposite: StateFlow<Map<String, CompositeAnalysis>> = _lastComposite.asStateFlow()

    private val _lastDecisions = MutableStateFlow<List<AutoTradingEngine.CycleDecision>>(emptyList())
    val lastDecisions: StateFlow<List<AutoTradingEngine.CycleDecision>> = _lastDecisions.asStateFlow()

    private val _decisionFeed = MutableStateFlow<List<AutoTradingEngine.CycleDecision>>(emptyList())
    val decisionFeed: StateFlow<List<AutoTradingEngine.CycleDecision>> = _decisionFeed.asStateFlow()

    private val _learnSummary = MutableStateFlow<AutoTradingEngine.LearnSummary?>(null)
    val learnSummary: StateFlow<AutoTradingEngine.LearnSummary?> = _learnSummary.asStateFlow()

    private val _openJournal = MutableStateFlow<List<TradeJournalStore.JournalRow>>(emptyList())
    val openJournal: StateFlow<List<TradeJournalStore.JournalRow>> = _openJournal.asStateFlow()

    private val _qualityMetrics = MutableStateFlow(QualityMetrics())
    val qualityMetrics: StateFlow<QualityMetrics> = _qualityMetrics.asStateFlow()

    init {
        scope.launch {
            var retryDelay = 5_000L
            while (isActive) {
                try {
                    refreshSnapshot()
                    retryDelay = 5_000L // reset on success
                } catch (_: Exception) {
                    // refreshSnapshot already handles errors via runCatching.
                    // If this outer try catches anything, it's truly unexpected.
                }
                delay(retryDelay)
            }
        }
    }

    fun start() = submit { remote.start() }
    fun stop() = submit { remote.stop() }
    fun runOnceNow() = submit { remote.runOnce() }
    fun learnNow() = submit { remote.learn() }

    fun updateWatchlist(list: List<String>) = persist { it.copy(watchlist = list.distinct()) }
    fun updateTimeframe(tf: String) = persist { it.copy(timeframe = tf) }
    fun updateEnableLive(on: Boolean) = persist { it.copy(enableLiveTrading = on) }
    fun updateEnableAiMode(on: Boolean) = persist { it.copy(enableAiMode = on) }  // V23.0
    fun updateMinConfluence(v: Double) = persist { it.copy(minConfluence = v.coerceIn(20.0, 95.0)) }
    fun updateMinRRR(v: Double) = persist { it.copy(minRRR = v.coerceIn(0.5, 5.0)) }
    fun updateRiskPerTrade(v: Double) = persist { it.copy(risk = it.risk.copy(riskPerTradePct = v.coerceIn(0.05, 5.0))) }
    fun updateMaxOpenPositions(n: Int) = persist { it.copy(risk = it.risk.copy(maxOpenPositions = n.coerceIn(1, 20))) }
    fun updateMaxDrawdown(v: Double) = persist { it.copy(risk = it.risk.copy(maxDrawdownPct = v.coerceIn(1.0, 50.0))) }
    fun updateMaxDailyLoss(v: Double) = persist { it.copy(risk = it.risk.copy(maxDailyLossPct = v.coerceIn(0.2, 20.0))) }
    fun updateMinLot(v: Double) = persist { it.copy(risk = it.risk.copy(minLot = v.coerceIn(0.01, 10.0))) }
    fun updateMaxLot(v: Double) = persist { it.copy(risk = it.risk.copy(maxLot = v.coerceIn(0.01, 100.0))) }
    fun updateBreakEvenR(v: Double) = persist { it.copy(breakEvenTriggerR = v.coerceIn(0.2, 5.0)) }
    fun updateTrailAfterR(v: Double) = persist { it.copy(trailAfterR = v.coerceIn(0.5, 10.0)) }
    fun updateAutoTune(on: Boolean) = persist { it.copy(autoTune = on) }
    fun togglePreferredStrategy(t: StrategyType) = persist {
        val cur = it.preferredStrategies.toMutableSet()
        if (t in cur) cur.remove(t) else cur.add(t)
        it.copy(preferredStrategies = cur)
    }
    fun addToBlacklist(symbol: String) = persist { it.copy(symbolBlacklist = it.symbolBlacklist + symbol.uppercase()) }
    fun removeFromBlacklist(symbol: String) = persist { it.copy(symbolBlacklist = it.symbolBlacklist - symbol.uppercase()) }

    fun updateAiModel(model: String) = persist { it.copy(aiModel = model) }
    fun updateApiKey(key: String) = persist { it.copy(apiKey = key) }
    fun updateAgentPrompt(prompt: String) = persist { it.copy(agentPrompt = prompt) }
    fun updatePreferFreeOnly(on: Boolean) = persist { it.copy(preferFreeOnly = on) }
    fun updateNotificationSettings(
        onAnalysis: Boolean? = null,
        onOrder: Boolean? = null,
        onLoss: Boolean? = null,
        onClose: Boolean? = null
    ) = persist { 
        it.copy(
            notificationSettings = it.notificationSettings.copy(
                onAnalysis = onAnalysis ?: it.notificationSettings.onAnalysis,
                onOrder = onOrder ?: it.notificationSettings.onOrder,
                onLoss = onLoss ?: it.notificationSettings.onLoss,
                onClose = onClose ?: it.notificationSettings.onClose
            )
        )
    }

    fun addToWatchlist(symbol: String) {
        val s = symbol.trim().uppercase()
        if (s.isEmpty()) return
        persist { it.copy(watchlist = (it.watchlist + s).distinct()) }
    }

    fun removeFromWatchlist(symbol: String) {
        persist { it.copy(watchlist = it.watchlist - symbol) }
    }

    fun resetToDefaults() = persist { AutoTradingEngine.Config() }

    private fun persist(transform: (AutoTradingEngine.Config) -> AutoTradingEngine.Config) {
        val next = transform(_config.value)
        _config.value = next
        if (!isApproved()) return
        submit { remote.updateConfig(next) }
    }

    private fun submit(block: suspend () -> AutoTradingRemoteService.Snapshot) {
        scope.launch {
            if (!isApproved()) {
                _state.value = _state.value.copy(
                    running = false,
                    phase = AutoTradingEngine.Phase.STOPPED,
                    message = "MT5 server is not approved yet"
                )
                return@launch
            }
            runCatching { block() }
                .onSuccess(::applySnapshot)
                .onFailure { error ->
                    _state.value = _state.value.copy(message = "Auto trading server error: ${error.message}")
                }
        }
    }

    private suspend fun refreshSnapshot() {
        if (!isApproved()) {
            _state.value = _state.value.copy(
                running = false,
                phase = AutoTradingEngine.Phase.STOPPED,
                message = "Waiting for MT5 pairing approval"
            )
            return
        }
        runCatching { remote.fetchSnapshot() }
            .onSuccess(::applySnapshot)
            .onFailure { error ->
                val msg = error.message ?: "Unknown error"
                val display = if (msg.contains("unreachable", ignoreCase = true) || msg.contains("Connection", ignoreCase = true))
                    "⏳ Server offline — retrying..."
                else
                    "Auto trading sync failed: $msg"
                _state.value = _state.value.copy(message = display)
            }
        
        // Also refresh decision feed every cycle
        runCatching { remote.fetchDecisionFeed(50) }
            .onSuccess { _decisionFeed.value = it }

        // P4.2: refresh Smart-Upgrade quality metrics each cycle
        runCatching { remote.fetchQualityMetrics() }
            .onSuccess { _qualityMetrics.value = it }
    }

    private fun applySnapshot(snapshot: AutoTradingRemoteService.Snapshot) {
        // API keys are local-only secrets. Never replace the local credential with
        // a value returned by the remote trading service (and never trust a remote
        // snapshot to carry one).
        val localApiKey = _config.value.apiKey
        _config.value = snapshot.config.copy(apiKey = localApiKey)
        _state.value = snapshot.state
        _lastDecisions.value = snapshot.lastDecisions
        _openJournal.value = snapshot.openJournal
        _learnSummary.value = snapshot.learnSummary
    }
}
