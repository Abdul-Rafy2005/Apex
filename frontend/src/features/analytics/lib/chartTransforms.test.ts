import { describe, it, expect } from 'vitest';
import {
  buildEquityCurveData,
  buildDrawdownData,
  buildDailyPnlHistogram,
  computeWinRate,
  computeRiskLevel,
  formatCurrency,
  formatPercent,
} from './chartTransforms';
import type { SnapshotPoint } from '../types/analytics';

describe('chartTransforms', () => {
  describe('buildEquityCurveData', () => {
    it('maps snapshot history to chart points', () => {
      const history: SnapshotPoint[] = [
        {
          date: '2025-01-01',
          totalReturnPct: 0,
          portfolioValue: 100000,
          dailyPnl: 0,
          sharpeRatio: 0,
          maxDrawdownPct: 0,
        },
        {
          date: '2025-01-02',
          totalReturnPct: 2.5,
          portfolioValue: 102500,
          dailyPnl: 2500,
          sharpeRatio: 1.2,
          maxDrawdownPct: 0,
        },
        {
          date: '2025-01-03',
          totalReturnPct: 1.0,
          portfolioValue: 101000,
          dailyPnl: -1500,
          sharpeRatio: 0.8,
          maxDrawdownPct: 1.46,
        },
      ];

      const result = buildEquityCurveData(history);

      expect(result).toEqual([
        { time: '2025-01-01', value: 100000 },
        { time: '2025-01-02', value: 102500 },
        { time: '2025-01-03', value: 101000 },
      ]);
    });

    it('returns empty array for empty history', () => {
      expect(buildEquityCurveData([])).toEqual([]);
    });

    it('handles single point', () => {
      const history: SnapshotPoint[] = [
        {
          date: '2025-06-15',
          totalReturnPct: 5.25,
          portfolioValue: 105250.789,
          dailyPnl: 5250.789,
          sharpeRatio: 2.1,
          maxDrawdownPct: 0,
        },
      ];

      const result = buildEquityCurveData(history);
      expect(result).toEqual([{ time: '2025-06-15', value: 105250.79 }]);
    });

    it('rounds portfolio value to 2 decimal places', () => {
      const history: SnapshotPoint[] = [
        {
          date: '2025-01-01',
          totalReturnPct: 0,
          portfolioValue: 100000.556,
          dailyPnl: 0,
          sharpeRatio: 0,
          maxDrawdownPct: 0,
        },
      ];

      const result = buildEquityCurveData(history);
      expect(result[0].value).toBe(100000.56);
    });
  });

  describe('buildDrawdownData', () => {
    it('computes drawdown from equity peak', () => {
      // Portfolio: 100k → 110k → 105k → 115k → 108k
      // Peak progression: 100k → 110k → 110k → 115k → 115k
      // Drawdown: 0%, -4.5455%, -6.5217%, 0%, -6.0870%
      const history: SnapshotPoint[] = [
        { date: '2025-01-01', totalReturnPct: 0, portfolioValue: 100000, dailyPnl: 0, sharpeRatio: 0, maxDrawdownPct: 0 },
        { date: '2025-01-02', totalReturnPct: 10, portfolioValue: 110000, dailyPnl: 10000, sharpeRatio: 0, maxDrawdownPct: 0 },
        { date: '2025-01-03', totalReturnPct: 5, portfolioValue: 105000, dailyPnl: -5000, sharpeRatio: 0, maxDrawdownPct: 0 },
        { date: '2025-01-04', totalReturnPct: 15, portfolioValue: 115000, dailyPnl: 10000, sharpeRatio: 0, maxDrawdownPct: 0 },
        { date: '2025-01-05', totalReturnPct: 8, portfolioValue: 108000, dailyPnl: -7000, sharpeRatio: 0, maxDrawdownPct: 0 },
      ];

      const result = buildDrawdownData(history);

      expect(result).toEqual([
        { time: '2025-01-01', value: 0 },
        { time: '2025-01-02', value: 0 },
        { time: '2025-01-03', value: -4.5455 },  // (105k - 110k) / 110k * 100
        { time: '2025-01-04', value: 0 },          // new peak at 115k
        { time: '2025-01-05', value: -6.087 },     // (108k - 115k) / 115k * 100
      ]);
    });

    it('returns empty array for empty history', () => {
      expect(buildDrawdownData([])).toEqual([]);
    });

    it('returns all zeros for monotonically increasing values', () => {
      const history: SnapshotPoint[] = [
        { date: '2025-01-01', totalReturnPct: 0, portfolioValue: 100000, dailyPnl: 0, sharpeRatio: 0, maxDrawdownPct: 0 },
        { date: '2025-01-02', totalReturnPct: 5, portfolioValue: 105000, dailyPnl: 5000, sharpeRatio: 0, maxDrawdownPct: 0 },
        { date: '2025-01-03', totalReturnPct: 10, portfolioValue: 110000, dailyPnl: 5000, sharpeRatio: 0, maxDrawdownPct: 0 },
      ];

      const result = buildDrawdownData(history);
      expect(result.every((p) => p.value === 0)).toBe(true);
    });

    it('handles single point as zero drawdown', () => {
      const history: SnapshotPoint[] = [
        { date: '2025-01-01', totalReturnPct: 0, portfolioValue: 100000, dailyPnl: 0, sharpeRatio: 0, maxDrawdownPct: 0 },
      ];

      const result = buildDrawdownData(history);
      expect(result).toEqual([{ time: '2025-01-01', value: 0 }]);
    });
  });

  describe('buildDailyPnlHistogram', () => {
    it('maps P/L to colored histogram bars', () => {
      const dailyPnl = [
        { date: '2025-01-01', pnl: 2500 },
        { date: '2025-01-02', pnl: -1200 },
        { date: '2025-01-03', pnl: 0 },
      ];

      const result = buildDailyPnlHistogram(dailyPnl);

      expect(result).toEqual([
        { time: '2025-01-01', value: 2500, color: 'rgba(34, 197, 94, 0.7)' },
        { time: '2025-01-02', value: -1200, color: 'rgba(239, 68, 68, 0.7)' },
        { time: '2025-01-03', value: 0, color: 'rgba(34, 197, 94, 0.7)' },
      ]);
    });

    it('returns empty array for empty input', () => {
      expect(buildDailyPnlHistogram([])).toEqual([]);
    });

    it('rounds P/L to 2 decimal places', () => {
      const dailyPnl = [{ date: '2025-01-01', pnl: 1234.567 }];
      const result = buildDailyPnlHistogram(dailyPnl);
      expect(result[0].value).toBe(1234.57);
    });
  });

  describe('computeWinRate', () => {
    it('calculates win rate correctly', () => {
      expect(computeWinRate(7, 10)).toBe(70);
    });

    it('returns 0 for zero trades', () => {
      expect(computeWinRate(0, 0)).toBe(0);
    });

    it('returns 100 for all wins', () => {
      expect(computeWinRate(5, 5)).toBe(100);
    });

    it('returns 0 for no wins', () => {
      expect(computeWinRate(0, 5)).toBe(0);
    });

    it('handles fractional win rates', () => {
      expect(computeWinRate(1, 3)).toBe(33.33);
    });
  });

  describe('computeRiskLevel', () => {
    it('returns Low for score ≤ 20', () => {
      expect(computeRiskLevel(0)).toBe('Low');
      expect(computeRiskLevel(20)).toBe('Low');
    });

    it('returns Moderate for score 21–40', () => {
      expect(computeRiskLevel(21)).toBe('Moderate');
      expect(computeRiskLevel(40)).toBe('Moderate');
    });

    it('returns Elevated for score 41–60', () => {
      expect(computeRiskLevel(41)).toBe('Elevated');
      expect(computeRiskLevel(60)).toBe('Elevated');
    });

    it('returns High for score 61–80', () => {
      expect(computeRiskLevel(61)).toBe('High');
      expect(computeRiskLevel(80)).toBe('High');
    });

    it('returns Critical for score > 80', () => {
      expect(computeRiskLevel(81)).toBe('Critical');
      expect(computeRiskLevel(100)).toBe('Critical');
    });
  });

  describe('formatCurrency', () => {
    it('formats with dollar sign and 2 decimals', () => {
      expect(formatCurrency(100000)).toBe('$100,000.00');
    });

    it('formats small values', () => {
      expect(formatCurrency(42.5)).toBe('$42.50');
    });

    it('formats zero', () => {
      expect(formatCurrency(0)).toBe('$0.00');
    });
  });

  describe('formatPercent', () => {
    it('formats positive with + sign', () => {
      expect(formatPercent(5.25)).toBe('+5.25%');
    });

    it('formats negative without + sign', () => {
      expect(formatPercent(-3.14)).toBe('-3.14%');
    });

    it('formats zero without sign', () => {
      expect(formatPercent(0)).toBe('0.00%');
    });
  });
});
