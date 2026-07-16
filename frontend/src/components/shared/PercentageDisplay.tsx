import { useEffect, useRef, useState } from 'react';

interface PercentageDisplayProps {
  value: number;
  className?: string;
  showSign?: boolean;
}

export function PercentageDisplay({ value, className = '', showSign = true }: PercentageDisplayProps) {
  const [flash, setFlash] = useState<'none' | 'up' | 'down'>('none');
  const prevRef = useRef(value);

  useEffect(() => {
    if (value > prevRef.current) {
      setFlash('up');
    } else if (value < prevRef.current) {
      setFlash('down');
    }
    prevRef.current = value;

    if (value !== prevRef.current) {
      const timer = setTimeout(() => setFlash('none'), 400);
      return () => clearTimeout(timer);
    }
  }, [value]);

  const isGain = value > 0;
  const isLoss = value < 0;

  const flashClass =
    flash === 'up'
      ? 'text-gain'
      : flash === 'down'
        ? 'text-loss'
        : isGain
          ? 'text-gain'
          : isLoss
            ? 'text-loss'
            : 'text-neutral-400';

  const sign = showSign && value > 0 ? '+' : '';

  return (
    <span className={`tabular-nums ${flashClass} ${className}`}>
      {sign}
      {value.toFixed(2)}%
    </span>
  );
}
