import { useEffect, useRef } from 'react';
import { Card } from '@/components/ui/Card';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { usePriceHistory } from '../hooks/useMarket';
import { createChart, CandlestickSeries, type IChartApi, ColorType } from 'lightweight-charts';

interface CandlestickChartProps {
  symbol: string | null;
}

export function CandlestickChart({ symbol }: CandlestickChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const seriesRef = useRef<any>(null);

  const { data: history, isLoading } = usePriceHistory(symbol ?? '');

  useEffect(() => {
    if (!chartContainerRef.current) return;

    const chart = createChart(chartContainerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: '#71777a',
      },
      grid: {
        vertLines: { color: '#1c1c1f' },
        horzLines: { color: '#1c1c1f' },
      },
      crosshair: {
        vertLine: { color: '#3a3a40', labelBackgroundColor: '#1c1c1f' },
        horzLine: { color: '#3a3a40', labelBackgroundColor: '#1c1c1f' },
      },
      rightPriceScale: {
        borderColor: '#1c1c1f',
      },
      timeScale: {
        borderColor: '#1c1c1f',
        timeVisible: true,
      },
      width: chartContainerRef.current.clientWidth,
      height: 300,
    });

    const series = chart.addSeries(CandlestickSeries, {
      upColor: '#22c55e',
      downColor: '#ef4444',
      borderUpColor: '#22c55e',
      borderDownColor: '#ef4444',
      wickUpColor: '#22c55e',
      wickDownColor: '#ef4444',
    });

    chartRef.current = chart;
    seriesRef.current = series;

    const handleResize = () => {
      if (chartContainerRef.current) {
        chart.applyOptions({ width: chartContainerRef.current.clientWidth });
      }
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!seriesRef.current || !history?.length) return;

    const candles = history.map((h) => ({
      time: new Date(h.time).getTime() / 1000 as unknown as string,
      open: h.open,
      high: h.high,
      low: h.low,
      close: h.close,
    }));

    seriesRef.current.setData(candles);
    chartRef.current?.timeScale().fitContent();
  }, [history]);

  if (!symbol) {
    return (
      <Card padding="md">
        <EmptyState
          title="Select an asset"
          description="Click on an asset in the price table to view its chart"
          icon={
            <svg className="w-8 h-8" viewBox="0 0 20 20" fill="currentColor">
              <path d="M15.5 2A1.5 1.5 0 0014 3.5v13a1.5 1.5 0 001.5 1.5h1a1.5 1.5 0 001.5-1.5v-13A1.5 1.5 0 0016.5 2h-1zM9.5 6A1.5 1.5 0 008 7.5v9A1.5 1.5 0 009.5 18h1a1.5 1.5 0 001.5-1.5v-9A1.5 1.5 0 0010.5 6h-1zM3.5 10A1.5 1.5 0 002 11.5v5A1.5 1.5 0 003.5 18h1A1.5 1.5 0 006 16.5v-5A1.5 1.5 0 004.5 10h-1z" />
            </svg>
          }
        />
      </Card>
    );
  }

  if (isLoading) {
    return (
      <Card padding="md">
        <Skeleton className="h-3 w-20 mb-3" />
        <Skeleton className="h-[300px] w-full rounded" />
      </Card>
    );
  }

  return (
    <Card padding="md">
      <p className="text-xs text-neutral-500 uppercase tracking-wider mb-2">
        {symbol} — 30 Day Chart
      </p>
      <div ref={chartContainerRef} className="w-full" />
    </Card>
  );
}
