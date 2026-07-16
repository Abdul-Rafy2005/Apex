import { useRef, useEffect } from 'react';
import { createChart, AreaSeries, type IChartApi, ColorType } from 'lightweight-charts';
import type { ChartPoint } from '../lib/chartTransforms';

interface EquityCurveChartProps {
  data: ChartPoint[];
  className?: string;
}

export function EquityCurveChart({ data, className = '' }: EquityCurveChartProps) {
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
        scaleMargins: { top: 0.1, bottom: 0.1 },
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
      height: 280,
    });

    const series = chart.addSeries(AreaSeries, {
      topColor: 'rgba(59, 130, 246, 0.3)',
      bottomColor: 'rgba(59, 130, 246, 0.02)',
      lineColor: '#3b82f6',
      lineWidth: 2,
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
      <div className={`flex items-center justify-center h-[280px] text-neutral-500 text-sm ${className}`}>
        No data available
      </div>
    );
  }

  return <div ref={containerRef} className={className} />;
}
