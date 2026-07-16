import type { SnapshotPoint } from '../types/analytics';

export interface ChartPoint {
  time: string;
  value: number;
}

export interface HistogramPoint {
  time: string;
  value: number;
  color: string;
}

const GAIN_COLOR = 'rgba(34, 197, 94, 0.7)';
const LOSS_COLOR = 'rgba(239, 68, 68, 0.7)';

/**
 * Transform snapshot history into equity curve data for a line chart.
 * Each point maps date → portfolioValue.
 * Input is expected sorted by date ascending.
 */
export function buildEquityCurveData(history: SnapshotPoint[]): ChartPoint[] {
  return history.map((p) => ({
    time: p.date,
    value: Number(p.portfolioValue.toFixed(2)),
  }));
}

/**
 * Transform snapshot history into drawdown data for an area chart.
 * Drawdown is always ≤ 0 (peak-to-trough decline as %).
 * Computed from portfolioValue series if maxDrawdownPct is not directly used,
 * so the chart shows the actual equity curve decline path.
 */
export function buildDrawdownData(history: SnapshotPoint[]): ChartPoint[] {
  if (history.length === 0) return [];

  const values = history.map((p) => p.portfolioValue);
  const drawdowns: ChartPoint[] = [];

  let peak = values[0];
  for (let i = 0; i < values.length; i++) {
    if (values[i] > peak) peak = values[i];
    const dd = peak > 0 ? ((values[i] - peak) / peak) * 100 : 0;
    drawdowns.push({
      time: history[i].date,
      value: Number(dd.toFixed(4)),
    });
  }

  return drawdowns;
}

/**
 * Transform daily P/L entries into histogram bar data.
 * Positive P/L = green, negative = red.
 */
export function buildDailyPnlHistogram(
  dailyPnl: { date: string; pnl: number }[],
): HistogramPoint[] {
  return dailyPnl.map((p) => ({
    time: p.date,
    value: Number(p.pnl.toFixed(2)),
    color: p.pnl >= 0 ? GAIN_COLOR : LOSS_COLOR,
  }));
}

/**
 * Compute win rate from total sells and winning sells.
 * Returns percentage (0–100).
 */
export function computeWinRate(winningTrades: number, totalTrades: number): number {
  if (totalTrades === 0) return 0;
  return Number(((winningTrades / totalTrades) * 100).toFixed(2));
}

/**
 * Map a numeric risk score (0–100) to a human-readable level.
 */
export function computeRiskLevel(score: number): string {
  if (score <= 20) return 'Low';
  if (score <= 40) return 'Moderate';
  if (score <= 60) return 'Elevated';
  if (score <= 80) return 'High';
  return 'Critical';
}

/**
 * Format a number as currency string.
 */
export function formatCurrency(value: number): string {
  return `$${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/**
 * Format a number as percentage string with sign.
 */
export function formatPercent(value: number): string {
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}
