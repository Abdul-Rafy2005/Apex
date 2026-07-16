import { useMemo } from 'react';
import { useAnalyticsSummary, useAnalyticsHistory } from '../hooks/useAnalytics';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { ErrorState } from '@/components/shared/ErrorState';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { PercentageDisplay } from '@/components/shared/PercentageDisplay';
import {
  buildEquityCurveData,
  buildDrawdownData,
  buildDailyPnlHistogram,
  computeWinRate,
  computeRiskLevel,
  formatCurrency,
} from '../lib/chartTransforms';
import { EquityCurveChart } from './EquityCurveChart';
import { DrawdownChart } from './DrawdownChart';
import { DailyPnlChart } from './DailyPnlChart';

function StatCard({
  label,
  children,
  className = '',
}: {
  label: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <Card className={className}>
      <p className="text-xs text-neutral-400 mb-1">{label}</p>
      {children}
    </Card>
  );
}

function RiskGauge({ score }: { score: number }) {
  const level = computeRiskLevel(score);
  const levelVariant =
    score <= 20
      ? 'gain'
      : score <= 40
        ? 'brand'
        : score <= 60
          ? 'warning'
          : 'loss';

  return (
    <div className="flex items-center gap-3">
      <div className="flex-1">
        <div className="h-2 rounded-full bg-neutral-800 overflow-hidden">
          <div
            className="h-full rounded-full transition-all duration-500"
            style={{
              width: `${score}%`,
              backgroundColor:
                score <= 20
                  ? '#22c55e'
                  : score <= 40
                    ? '#3b82f6'
                    : score <= 60
                      ? '#f59e0b'
                      : '#ef4444',
            }}
          />
        </div>
      </div>
      <Badge variant={levelVariant}>{level}</Badge>
    </div>
  );
}

export function AnalyticsPage() {
  const {
    data: summary,
    isLoading: summaryLoading,
    error: summaryError,
    refetch: refetchSummary,
  } = useAnalyticsSummary();

  const {
    data: history,
    isLoading: historyLoading,
    error: historyError,
    refetch: refetchHistory,
  } = useAnalyticsHistory();

  const equityCurveData = useMemo(
    () => buildEquityCurveData(history?.history ?? []),
    [history],
  );

  const drawdownData = useMemo(
    () => buildDrawdownData(history?.history ?? []),
    [history],
  );

  const dailyPnlData = useMemo(
    () => buildDailyPnlHistogram(summary?.dailyPnlSeries ?? []),
    [summary],
  );

  const isLoading = summaryLoading || historyLoading;
  const error = summaryError || historyError;

  if (isLoading) {
    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold text-neutral-100">Analytics</h1>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Card key={i}>
              <Skeleton className="h-3 w-20 mb-2" />
              <Skeleton className="h-6 w-24" />
            </Card>
          ))}
        </div>
        <Skeleton className="h-[280px] w-full rounded-lg" />
        <Skeleton className="h-[200px] w-full rounded-lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold text-neutral-100">Analytics</h1>
        <ErrorState
          message="Failed to load analytics data"
          onRetry={() => {
            refetchSummary();
            refetchHistory();
          }}
        />
      </div>
    );
  }

  const s = summary;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-neutral-100">Analytics</h1>
        {s && (
          <span className="text-xs text-neutral-500">
            Snapshot: {s.snapshotDate}
          </span>
        )}
      </div>

      {s && (
        <>
          {/* Top stat cards */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="Total Return">
              <PercentageDisplay
                value={s.totalReturnPct}
                className="text-lg font-semibold"
              />
            </StatCard>
            <StatCard label="Portfolio Value">
              <p className="text-lg font-semibold tabular-nums font-mono text-neutral-100">
                {formatCurrency(s.portfolioValue)}
              </p>
            </StatCard>
            <StatCard label="Sharpe Ratio">
              <p className="text-lg font-semibold tabular-nums font-mono text-neutral-100">
                {s.sharpeRatio.toFixed(4)}
              </p>
            </StatCard>
            <StatCard label="Risk Score">
              <RiskGauge score={s.riskScore} />
            </StatCard>
          </div>

          {/* Second row of stats */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="Win Rate">
              <p className="text-lg font-semibold tabular-nums font-mono text-neutral-100">
                {computeWinRate(s.winningTrades, Math.max(s.winningTrades + s.losingTrades, 1))}%
              </p>
              <p className="text-xs text-neutral-500 mt-0.5">
                {s.winningTrades}W / {s.losingTrades}L
              </p>
            </StatCard>
            <StatCard label="Max Drawdown">
              <PercentageDisplay
                value={-Math.abs(s.maxDrawdownPct)}
                className="text-lg font-semibold"
              />
            </StatCard>
            <StatCard label="Best Asset">
              {s.bestAssetSymbol ? (
                <div>
                  <span className="text-lg font-semibold text-neutral-100">
                    {s.bestAssetSymbol}
                  </span>
                  <PercentageDisplay
                    value={s.bestAssetReturnPct}
                    className="ml-2 text-sm"
                  />
                </div>
              ) : (
                <p className="text-neutral-500">—</p>
              )}
            </StatCard>
            <StatCard label="Worst Asset">
              {s.worstAssetSymbol ? (
                <div>
                  <span className="text-lg font-semibold text-neutral-100">
                    {s.worstAssetSymbol}
                  </span>
                  <PercentageDisplay
                    value={s.worstAssetReturnPct}
                    className="ml-2 text-sm"
                  />
                </div>
              ) : (
                <p className="text-neutral-500">—</p>
              )}
            </StatCard>
          </div>

          {/* Equity curve chart */}
          <Card padding="none">
            <div className="px-4 py-3 border-b border-neutral-800">
              <h2 className="text-sm font-semibold text-neutral-100">
                Equity Curve
              </h2>
            </div>
            <div className="p-4">
              <EquityCurveChart data={equityCurveData} />
            </div>
          </Card>

          {/* Drawdown chart */}
          <Card padding="none">
            <div className="px-4 py-3 border-b border-neutral-800">
              <h2 className="text-sm font-semibold text-neutral-100">
                Drawdown
              </h2>
            </div>
            <div className="p-4">
              <DrawdownChart data={drawdownData} />
            </div>
          </Card>

          {/* Daily P/L histogram */}
          {dailyPnlData.length > 0 && (
            <Card padding="none">
              <div className="px-4 py-3 border-b border-neutral-800">
                <h2 className="text-sm font-semibold text-neutral-100">
                  Daily P/L
                </h2>
              </div>
              <div className="p-4">
                <DailyPnlChart data={dailyPnlData} />
              </div>
            </Card>
          )}

          {/* Additional metrics row */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard label="Realized P/L">
              <PriceDisplay
                value={s.realizedPnl}
                className="text-lg font-semibold"
              />
            </StatCard>
            <StatCard label="Unrealized P/L">
              <PriceDisplay
                value={s.unrealizedPnl}
                className="text-lg font-semibold"
              />
            </StatCard>
            <StatCard label="Avg Win">
              <PercentageDisplay
                value={s.avgWinPct}
                className="text-lg font-semibold"
              />
            </StatCard>
            <StatCard label="Avg Loss">
              <PercentageDisplay
                value={s.avgLossPct}
                className="text-lg font-semibold"
              />
            </StatCard>
          </div>
        </>
      )}
    </div>
  );
}
