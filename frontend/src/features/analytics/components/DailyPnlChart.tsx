import { useRef, useEffect } from 'react';
import { createChart, HistogramSeries, type IChartApi, ColorType } from 'lightweight-charts';
import type { HistogramPoint } from '../lib/chartTransforms';

interface DailyPnlChartProps {
  data: HistogramPoint[];
  className?: string;
}

export function DailyPnlChart({ data, className = '' }: DailyPnlChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<IChartApi | null>(null);

  useEffect(() => {
    if (!containerRef.current || data.length === 0) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { type: ColorType.Solid, color: 'transparent' },
        textColor: '#71717a',
        fontFamily: 'Inter, sans-serif',
        fontSize: 11,
      },
      grid: {
        vertLines: { color: 'rgba(42, 42, 46, 0.5)' },
        horzLines: { color: 'rgba(42, 42, 46, 0.5)' },
      },
      rightPriceScale: {
        borderColor: '#2a2a2e',
      },
      timeScale: {
        borderColor: '#2a2a2e',
        timeVisible: false,
      },
      crosshair: {
        vertLine: { color: 'rgba(59, 130, 246, 0.3)', width: 1, style: 2 },
        horzLine: { color: 'rgba(59, 130, 246, 0.3)', width: 1, style: 2 },
      },
      width: containerRef.current.clientWidth,
      height: 200,
    });

    const series = chart.addSeries(HistogramSeries, {
      priceFormat: { type: 'price', precision: 2, minMove: 0.01 },
    });

    series.setData(data);
    chart.timeScale().fitContent();
    chartRef.current = chart;

    const handleResize = () => {
      if (containerRef.current) {
        chart.applyOptions({ width: containerRef.current.clientWidth });
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
      chartRef.current = null;
    };
  }, [data]);

  if (data.length === 0) {
    return (
      <div className={`flex items-center justify-center h-[200px] text-neutral-500 text-sm ${className}`}>
        No data available
      </div>
    );
  }

  return <div ref={containerRef} className={className} />;
}
