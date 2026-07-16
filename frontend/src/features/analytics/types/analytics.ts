export interface AnalyticsSummaryResponse {
  snapshotDate: string;

  totalReturnPct: number;
  dailyReturnPct: number;

  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRate: number;
  avgWinPct: number;
  avgLossPct: number;
  largestGainPct: number;
  largestLossPct: number;

  sharpeRatio: number;
  maxDrawdownPct: number;
  avgHoldingPeriodHours: number;
  riskScore: number;

  portfolioValue: number;
  cashBalance: number;
  investedValue: number;
  realizedPnl: number;
  unrealizedPnl: number;

  bestAssetSymbol: string;
  bestAssetReturnPct: number;
  worstAssetSymbol: string;
  worstAssetReturnPct: number;

  allocationBreakdown: AllocationEntry[];
  dailyPnlSeries: DailyPnlEntry[];
}

export interface AllocationEntry {
  symbol: string;
  value: number;
  pct: number;
}

export interface DailyPnlEntry {
  date: string;
  pnl: number;
}

export interface AnalyticsHistoryResponse {
  history: SnapshotPoint[];
}

export interface SnapshotPoint {
  date: string;
  totalReturnPct: number;
  portfolioValue: number;
  dailyPnl: number;
  sharpeRatio: number;
  maxDrawdownPct: number;
}
