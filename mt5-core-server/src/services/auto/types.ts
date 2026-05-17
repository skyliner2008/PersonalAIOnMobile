export type Phase = 'IDLE' | 'ANALYZING' | 'STRATEGIZING' | 'EXECUTING' | 'MANAGING' | 'LEARNING' | 'STOPPED' | 'SLEEPING';
export type Bias = 'BULL' | 'BEAR' | 'NEUTRAL';
export type MarketRegime = 'TRENDING_UP' | 'TRENDING_DOWN' | 'RANGING' | 'VOLATILE_BREAKOUT' | 'QUIET' | 'UNKNOWN';
export type StrategyType =
  | 'SCALPING'
  | 'SWING'
  | 'GRID'
  | 'TRAILING'
  | 'TREND_FOLLOW'
  | 'MEAN_REVERSION'
  | 'BREAKOUT'
  | 'RANGE'
  | 'SMC_FVG_SCALP'
  | 'SMC_FVG_REVERSAL'
  | 'SMC_FVG_CONTINUATION'
  | 'SMC_FVG_MAGNET_SCALP'
  | 'SMC_RSI_DIVERGENCE'
  | 'HOLD_CASH';


export interface AgentModelChoice {
  provider: 'gemini' | 'openai' | 'claude' | 'openrouter' | 'ollama' | 'native' | 'minimax' | 'vertexai';
  model: string;
  useSmartFree?: boolean;
}

export type AutoTradingConfig = {
  provider?: string;
  watchlist: string[];
  timeframe: string;
  tickIntervalMs: number;
  manageIntervalMs: number;
  learnIntervalMs: number;
  minConfluence: number;
  minFitness: number;
  minRRR: number;
  enableLiveTrading: boolean;
  /**
   * V23.0 - EA-only mode flag
   * true (default) = AI agents approve/reject EA signals (AI Supervisor mode)
   * false          = EA runs fully autonomously - no AI calls, no token cost
   *                  signals execute when deterministic confidence >= threshold
   */
  enableAiMode: boolean;
  preferredStrategies: StrategyType[];
  symbolBlacklist: string[];
  breakEvenTriggerR: number;
  trailAfterR: number;
  // V24.1 (2026-05-08) — time-based stale-position exit
  stalePositionMaxAgeMs?: number;
  stalePositionMinR?: number;
  stalePositionMaxR?: number;
  autoTune: boolean;
  autoBlacklistAfterLosses: number;
  risk: {
    riskPerTradePct: number;
    maxTotalExposurePct: number;
    maxOpenPositions: number;
    maxDailyLossPct: number;
    maxDrawdownPct: number;
    minFreeMarginPct: number;
    correlationCap: number;
    maxCorrelation?: number;
    minLotStep: number;
    maxLot: number;
    minLot: number;
    maxSpread?: number;
    maxSpreadOverride: Record<string, number>;
    pointValueOverride: Record<string, number>;
    maxSpreadIncreasePct?: number;
    maxPositionsPerSymbol?: Record<string, number>;
    defaultMaxPositionsPerSymbol?: number;
    riskPerTradePctOverride?: Record<string, number>;
    clusterProfitProtectionActivationR?: number;
    clusterProfitProtectionDropPct?: number;
    clusterProfitProtectionMinDollar?: number;
  };
  strategy: {
    minRRR: number;
    minConfluence: number;
    scalpingRRR: number;
    swingRRR: number;
    gridLegs: number;
    gridStepPct: number;
    trailingAtrMult: number;
    breakoutBufferPct: number;
  };
  adaptive?: {
    allowSameSymbolPositions?: boolean;
    allowOverLimitDefense?: boolean;
    allowCounterHedge?: boolean;
    allowScaleInRecovery?: boolean;
    maxDefensePositionsPerSymbol?: number;
    maxSameSymbolPositions?: number;
    adverseConfluence?: number;
    defenseConfidence?: number;
    hedgeRatioMin?: number;
    hedgeRatioMax?: number;
    defaultHedgeRatio?: number;
    scaleInRatioMin?: number;
    scaleInRatioMax?: number;
    defaultScaleInRatio?: number;
    scalpBreakEvenTriggerR?: number;
    scalpTrailAfterR?: number;
    scalpStackBreakEvenTriggerR?: number;
    scalpStackTrailAfterR?: number;
    reduceLossThresholdR?: number;
    closeLossThresholdR?: number;
    scaleInLossThresholdR?: number;
    maxScaleInStepsPerSymbol?: number;
    hedgeCooldownMs?: number;
    scaleInCooldownMs?: number;
    hardCapPositionMultiplier?: number;
    hardCapPerSymbolMultiplier?: number;
    mtfDowngradeBlock?: boolean;
    mtfDowngradeConfluenceMin?: number;
    counterTrendConfluenceMin?: number;
    decisionOverrideConfidenceMin?: number;
    maxHeatForScaleIn?: number;
    useMultiAgent?: boolean;
    hedgeTriggerHeatR?: number;
    scaleWinnerMinProfit?: number;
    netBeProfitBand?: number;
    hedgedExitMaxHeatR?: number;
    enableZoneAwareGate?: boolean;
    enableFvgSlBuffer?: boolean;
    enableStagedPartials?: boolean;
    enableSmartScaleIn?: boolean;
    enableCloseWeakest?: boolean;
    closeWeakestMinConfluence?: number;
    closeWeakestCooldownMs?: number;
    hedgeOverrideMinConfidence?: number;
    deterministicHedgeMinConfluence?: number;
    goldenTrailMultiplier?: number;
    enableFlipCluster?: boolean;
    flipClusterCooldownMs?: number;
    flipOppositeFraction?: number;
    maxClusterConcentration?: number;
    enablePreAnalysisPrior?: boolean;
    staleAgentPriceMaxPoints?: number;
    allowBreakoutThroughZone?: boolean;
    goldenThresholdR?: number;
    recoveryTriggerPct?: number;
    recoveryMaxAgeMs?: number;
    recoveryTier2AgeMs?: number;
    recoveryTier3AgeMs?: number;
    enableSequentialOffset?: boolean;
    clusterMaxHeatR?: number;
    clusterMaxLossPct?: number;
    preventNearDuplicateEntries?: boolean;
    sequentialMinEntryDistanceXAU?: number;
    sequentialMinEntryDistanceAtrMul?: number;
    sequentialDuplicateIntentTtlMs?: number;
    scaleInMinEntryDistanceXAU?: number;
    scaleInMinEntryDistanceAtrMul?: number;
    scaleInRequiresSafeOrLossGate?: boolean;
    // V24.0 - Fade-the-Level Proximity Gate
    enableProximityGate?: boolean;
    proximityMaxPip?: number;
    proximityUnconditionalStars?: number;
    proximityConditionalStars?: number;
    proximityContinuationConfluenceMin?: number;
    proximityStrongWallRelaxMultiplier?: number;
    zoneContinuationConfluenceMin?: number;
    zoneBreakoutConfluenceMin?: number;
    zoneHardBlockPct?: number;
    zoneLocalAlignmentConfluenceMin?: number;
    zoneFvgProximityAtrMul?: number;
    zoneWallFadeMinStars?: number;
    zoneWallFadeConfluenceMin?: number;
    // V24.0 - Sequential Entry
    enableSequentialEntry?: boolean;
    sequentialAddOnLossPct?: number;
    // V24.0 - Basket BE Guard
    requireBasketBeBeforeClose?: boolean;
    // V25.0 - Real-Time Wall Engine
    v25?: {
      enable?: boolean;
      enableV25Only?: boolean;
      allowV24DirectWhenV25Aligned?: boolean;
      allowV24DirectOnV25Approach?: boolean;
      allowV24TrendFollowDirectWhenV25Stale?: boolean;
      hybridDirectMinWallStars?: number;
      hybridDirectMaxStateAgeMs?: number;
      hybridApproachMinWallStars?: number;
      hybridApproachMaxAtrMul?: number;
      hybridApproachMinConfluence?: number;
      hybridTrendFollowMinConfluence?: number;
      hybridTrendFollowMaxOpposingWallAtrMul?: number;
      compactScalpBracket?: boolean;
      compactScalpMaxRiskAtrMul?: number;
      compactScalpMinRiskXAU?: number;
      compactScalpRrr?: number;
      skipMidChannelLLM?: boolean;
      tickThrottleMs?: number;
      m1MicroWallSampleTicks?: number;
      approachAtrMultiplier?: number;
      reactAtrMultiplier?: number;
      breakBodyPercent?: number;
      swingLookback?: number;
      slBufferAtrMul?: number;
      slMaxAtrM15?: number;
      minRrrXBT?: number;
      minRrrXAU?: number;
      minWallStarsScalp?: number;
      minWallStarsSwing?: number;
      requireFreshOppositeFvg?: boolean;
      barCloseEventBus?: boolean;
    };
  };
  aiModel?: string;
  apiKey?: string;
  agentPrompt?: string;
  agentModels?: {
    analyst?: AgentModelChoice | string;
    riskOfficer?: AgentModelChoice | string;
    executionTrader?: AgentModelChoice | string;
    postMortem?: AgentModelChoice | string;
    reasoning?: AgentModelChoice | string;
    embedding?: AgentModelChoice | string;
    slTpAgent?: AgentModelChoice | string;
  };
  providerKeys?: {
    gemini?: string;
    openai?: string;
    claude?: string;
    openrouter?: string;
    ollama?: string;
    native?: string;
    minimax?: string;
    vertexai?: string;
  };
  providerBaseUrls?: {
    openai?: string;
    ollama?: string;
  };
  preferFreeOnly?: boolean;
  notificationSettings?: {
    onAnalysis?: boolean;
    onOrder?: boolean;
    onLoss?: boolean;
    onClose?: boolean;
  };
  newsRisk?: {
    enabled: boolean;
    activeEvent?: string;
    impactLevel?: 'LOW' | 'MEDIUM' | 'HIGH';
    minutesToEvent?: number;
  };
};

export type EngineState = {
  running: boolean;
  phase: Phase;
  lastTickAt: string | null;
  cycleCount: number;
  openTradesTracked: number;
  message: string;
  status: 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' | 'ERROR' | 'PAUSED';
};

export type CycleDecision = {
  symbol: string;
  regime: MarketRegime;
  overallBias: Bias;
  confluence: number;
  fitness: number;
  strategy: StrategyType;
  side: 'BUY' | 'SELL' | 'SKIP';
  entry: number | null;
  sl: number | null;
  tp: number | null;
  volume: number | null;
  rrr: number;
  rationale: string;
  translatedTh?: string | null;
  analyzersUsed: string[];
  riskGate: string;
  executed: boolean;
  mt5Ticket: number | null;
  at: string;
  decisionId: string;
  decisionType?: 'ORDER_EXECUTED' | 'ORDER_ALLOWED' | 'ORDER_BLOCKED' | 'NO_SIGNAL' | 'PAPER' | 'MANAGEMENT' | 'UNKNOWN';
  blockCategory?: string | null;
  deterministic?: Record<string, unknown>;
  analysisSnapshot?: Record<string, unknown>;
  signals?: Record<string, unknown>;
  gateTrace?: Array<Record<string, unknown>>;
  marketSnapshotLog?: Record<string, unknown>;
  order?: Record<string, unknown>;
  outcome?: Record<string, unknown>;
};

export type ManagementMode = 'HOLD' | 'TRAIL' | 'BREAKEVEN' | 'REDUCE' | 'CLOSE' | 'HEDGE' | 'SCALE_IN' | 'PARTIAL_CLOSE';

export type PlaybookScores = {
  dimensions: {
    trendStrength: number;
    floatingLossPressure: number;
    exposureImbalance: number;
    recoveryProbability: number;
    marginHeadroom: number;
  };
  actions: Record<ManagementMode, number>;
  recommendedMode: ManagementMode;
  summary: string;
  worstLossR: number;
  scaleInSteps: number;
};

export type ManagementPlan = {
  mode: ManagementMode;
  summary: string;
  reason: string;
  side?: 'BUY' | 'SELL';
  volume?: number;
  ticket?: number;
  closeVolume?: number;
  fraction?: number;
  rotateTicket?: number;
  rotateVolume?: number;
  isAiDefense?: boolean;
  allowOverLimitDefense?: boolean;
  playbook?: string | PlaybookScores;
  sl?: number | null;
  tp?: number | null;
  tickets?: number[];
  triggerOpposite?: { side: 'BUY' | 'SELL'; volume: number; rationale: string };
};

export type LearnSummary = {
  totalDeals: number;
  wins: number;
  losses: number;
  winRate: number;
  totalProfit: number;
  eligibleDeals?: number;
  excludedDeals?: number;
  dataQuality?: {
    eligible: number;
    excluded: number;
    avgQualityScore: number;
    flags: Record<string, number>;
  };
  blockStats?: Array<{ blockCategory: string; count: number }>;
  managementStats?: Array<{ mode: string; status: string; count: number; wins?: number; losses?: number; avgR?: number | null }>;
  bestStrategy: string | null;
  worstStrategy: string | null;
  bestAnalyzerCombo: string | null;
  worstAnalyzerCombo: string | null;
  strategyStats: StrategyStat[];
  analyzerStats: AnalyzerStat[];
  aiNote: string;
  atIso: string;
};

export type StrategyStat = {
  strategy: string;
  total: number;
  wins: number;
  losses: number;
  winRate: number;
  totalProfit: number;
  avgR: number;
};

export type AnalyzerStat = {
  analyzers: string;
  total: number;
  wins: number;
  winRate: number;
  totalProfit: number;
};

export type AccountSnapshot = {
  balance: number;
  equity: number;
  margin: number;
  freeMargin: number;
  todayPnL: number;
  openPositions: number;
  currency: string;
};

export type PositionRow = {
  ticket: number;
  symbol: string;
  side: 'BUY' | 'SELL';
  volume: number;
  priceOpen: number;
  priceCurrent: number;
  sl: number;
  tp: number;
  profit: number;
  openedAtMs?: number;
};

export type PositionCluster = {
  symbol: string;
  positions: PositionRow[];
  winningPositions: PositionRow[];
  losingPositions: PositionRow[];
  buyVolume: number;
  sellVolume: number;
  totalVolume: number;
  netVolume: number;
  totalProfit: number;
  avgPrice: number;
  netSide: 'BUY' | 'SELL' | 'FLAT';
  bias: Bias;
};

export type AnalysisSignal = {
  name: string;
  bias: Bias;
  score: number;
  evidence: string;
};

export type AnalyzerModuleResult = {
  name: string;
  bias: Bias;
  strengthScore: number;
  signals: AnalysisSignal[];
  notes?: string;
};

export type AnalysisSummary = {
  bias: Bias;
  regime: MarketRegime;
  confluence: number;
  fitness: number;
  strategy: StrategyType;
  rationale: string;
  signals: AnalysisSignal[];
  atr?: number | null;
  rsi?: number | null;
  sma20?: number | null;
  sma50?: number | null;
  ema20?: number | null;
  smc?: any;
};

export type JournalRow = {
  id: number;
  decisionId: string;
  symbol: string;
  timeframe: string;
  side: string;
  strategy: string;
  analyzersUsed: string;
  signalsJson: string;
  confluenceScore: number;
  entry: number | null;
  sl: number | null;
  tp: number | null;
  volume: number | null;
  riskPct: number | null;
  rrr: number | null;
  regime: string | null;
  marketSnapshot: string | null;
  wasExecuted: boolean;
  mt5Ticket: number | null;
  closeReason: string | null;
  closePrice: number | null;
  closeAt: number | null;
  profit: number | null;
  profitR: number | null;
  outcome: string;
  aiReview: string | null;
  createdAt: number;
  updatedAt: number;
  at?: string | null;
  modelId?: string | null;
  decisionFeedId?: number | null;
  managementCount?: number;
  qualityScore?: number;
  qualityFlags?: string[];
  learningEligible?: boolean;
  dataVersion?: number;
};

export type ManagementJournalRow = {
  id?: number;
  managementId: string;
  relatedDecisionId?: string;
  symbol: string;
  timeframe: string;
  mode: ManagementMode | string;
  status: 'PLANNED' | 'EXECUTED' | 'SKIPPED' | string;
  side?: 'BUY' | 'SELL' | null;
  targetTicket?: number | null;
  createdTicket?: number | null;
  volume?: number | null;
  sizeFraction?: number | null;
  reason: string;
  playbook?: string | null;
  marketContext?: any;
  aiContext?: any;
  result?: any;
  createdAt?: number;
};

export type AutoTradingSnapshot = {
  config: AutoTradingConfig;
  state: EngineState;
  lastDecisions: CycleDecision[];
  openJournal: JournalRow[];
  learnSummary: LearnSummary | null;
  serverMode?: string;
};
