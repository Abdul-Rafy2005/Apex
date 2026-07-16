import { useEffect, useRef, useState } from 'react';

interface PriceDisplayProps {
  value: number;
  prefix?: string;
  className?: string;
}

export function PriceDisplay({ value, prefix = '$', className = '' }: PriceDisplayProps) {
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

  const flashClass =
    flash === 'up'
      ? 'text-gain'
      : flash === 'down'
        ? 'text-loss'
        : '';

  return (
    <span className={`tabular-nums ${flashClass} ${className}`}>
      {prefix}
      {value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
    </span>
  );
}
