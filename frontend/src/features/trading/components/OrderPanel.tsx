import { useState, useMemo, useCallback } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { PriceDisplay } from '@/components/shared/PriceDisplay';
import { useToast } from '@/components/ui/Toast';
import { useTradeExecution } from '../hooks/useTradeExecution';
import type { AssetResponse } from '@/features/market/types/asset';
import type { PortfolioResponse } from '@/features/portfolio/types/portfolio';

const FEE_RATE = 0.001;
const MIN_QUANTITY = 0.00000001;

interface OrderPanelProps {
  asset: AssetResponse;
  currentPrice: number;
  portfolio: PortfolioResponse | undefined;
}

export function OrderPanel({ asset, currentPrice, portfolio }: OrderPanelProps) {
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY');
  const [quantity, setQuantity] = useState('');
  const [quantityError, setQuantityError] = useState('');
  const { addToast } = useToast();
  const { executeTradeAsync, isPending } = useTradeExecution();

  const holding = portfolio?.holdings?.find((h) => h.assetId === asset.id);
  const cashBalance = portfolio?.cashBalance ?? 0;
  const heldQuantity = holding?.quantity ?? 0;

  const quantityNum = parseFloat(quantity) || 0;
  const tradeValue = quantityNum * currentPrice;
  const fee = tradeValue * FEE_RATE;

  const validation = useMemo(() => {
    if (quantityNum <= 0) return { valid: false, error: '' };
    if (quantityNum < MIN_QUANTITY) return { valid: false, error: 'Minimum quantity is 0.00000001' };

    if (side === 'BUY') {
      const totalCost = tradeValue + fee;
      if (totalCost > cashBalance) {
        return {
          valid: false,
          error: `Insufficient funds: need $${totalCost.toFixed(2)}, have $${cashBalance.toFixed(2)}`,
        };
      }
    } else {
      if (quantityNum > heldQuantity) {
        return {
          valid: false,
          error: `Insufficient holdings: need ${quantityNum.toFixed(8)}, have ${heldQuantity.toFixed(8)}`,
        };
      }
    }

    return { valid: true, error: '' };
  }, [quantityNum, side, tradeValue, fee, cashBalance, heldQuantity]);

  const validateQuantity = useCallback(
    (value: string) => {
      if (value === '' || value === '.') {
        setQuantityError('');
        return;
      }
      const num = parseFloat(value);
      if (isNaN(num)) {
        setQuantityError('Invalid quantity');
        return;
      }
      if (num < MIN_QUANTITY) {
        setQuantityError('Minimum quantity is 0.00000001');
        return;
      }
      if (side === 'BUY') {
        const totalCost = num * currentPrice * (1 + FEE_RATE);
        if (totalCost > cashBalance) {
          setQuantityError(`Insufficient funds: need $${totalCost.toFixed(2)}, have $${cashBalance.toFixed(2)}`);
          return;
        }
      } else {
        if (num > heldQuantity) {
          setQuantityError(`Insufficient holdings: have ${heldQuantity.toFixed(8)}`);
          return;
        }
      }
      setQuantityError('');
    },
    [side, currentPrice, cashBalance, heldQuantity],
  );

  function handleQuantityChange(e: React.ChangeEvent<HTMLInputElement>) {
    const value = e.target.value;
    if (value === '' || /^\d*\.?\d{0,8}$/.test(value)) {
      setQuantity(value);
      validateQuantity(value);
    }
  }

  function handleMaxClick() {
    if (side === 'BUY') {
      const maxQty = cashBalance / (currentPrice * (1 + FEE_RATE));
      setQuantity(maxQty.toFixed(8));
      validateQuantity(maxQty.toFixed(8));
    } else {
      setQuantity(heldQuantity.toFixed(8));
      validateQuantity(heldQuantity.toFixed(8));
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validation.valid || isPending) return;

    try {
      await executeTradeAsync({
        assetId: asset.id,
        side,
        quantity: quantityNum,
      });
      addToast(`Trade executed: ${side} ${quantity} ${asset.symbol}`, 'success');
      setQuantity('');
      setQuantityError('');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Trade failed';
      if (message.includes('conflict') || message.includes('409')) {
        addToast('Trade conflict: portfolio was modified concurrently. Please retry.', 'warning');
      } else {
        addToast(message, 'error');
      }
    }
  }

  return (
    <Card padding="md">
      <form onSubmit={handleSubmit} className="space-y-4">
        {/* Side selector */}
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => { setSide('BUY'); setQuantityError(''); }}
            className={`flex-1 h-10 rounded-md text-sm font-semibold transition-colors ${
              side === 'BUY'
                ? 'bg-gain text-white'
                : 'bg-neutral-800 text-neutral-400 hover:bg-neutral-750'
            }`}
          >
            Buy
          </button>
          <button
            type="button"
            onClick={() => { setSide('SELL'); setQuantityError(''); }}
            className={`flex-1 h-10 rounded-md text-sm font-semibold transition-colors ${
              side === 'SELL'
                ? 'bg-loss text-white'
                : 'bg-neutral-800 text-neutral-400 hover:bg-neutral-750'
            }`}
          >
            Sell
          </button>
        </div>

        {/* Live price */}
        <div className="flex items-center justify-between">
          <span className="text-xs text-neutral-500">Price</span>
          <PriceDisplay value={currentPrice} className="text-sm text-neutral-100" />
        </div>

        {/* Quantity input */}
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="text-sm font-medium text-neutral-300">Quantity</label>
            <button
              type="button"
              onClick={handleMaxClick}
              className="text-xs text-brand-400 hover:text-brand-500 transition-colors"
            >
              Max
            </button>
          </div>
          <input
            type="text"
            inputMode="decimal"
            value={quantity}
            onChange={handleQuantityChange}
            placeholder="0.00000000"
            className={`h-10 px-3 text-sm rounded-md bg-neutral-900 border text-neutral-100 placeholder:text-neutral-600 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent w-full tabular-nums ${
              quantityError ? 'border-loss' : 'border-neutral-700'
            }`}
          />
          {quantityError && <p className="text-xs text-loss mt-1">{quantityError}</p>}
        </div>

        {/* Order summary */}
        {quantityNum > 0 && (
          <div className="space-y-1.5 text-xs">
            <div className="flex justify-between">
              <span className="text-neutral-500">Subtotal</span>
              <span className="text-neutral-300 tabular-nums">${tradeValue.toFixed(2)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-neutral-500">Fee (0.1%)</span>
              <span className="text-neutral-300 tabular-nums">${fee.toFixed(4)}</span>
            </div>
            <div className="flex justify-between border-t border-neutral-800 pt-1.5">
              <span className="text-neutral-400 font-medium">
                {side === 'BUY' ? 'Total cost' : 'Proceeds'}
              </span>
              <span className="text-neutral-100 font-medium tabular-nums">
                ${side === 'BUY' ? (tradeValue + fee).toFixed(2) : (tradeValue - fee).toFixed(2)}
              </span>
            </div>
          </div>
        )}

        {/* Balance info */}
        <div className="flex items-center justify-between text-xs">
          <span className="text-neutral-500">
            {side === 'BUY' ? 'Available cash' : `Available ${asset.symbol}`}
          </span>
          <span className="text-neutral-300 tabular-nums">
            {side === 'BUY'
              ? `$${cashBalance.toFixed(2)}`
              : `${heldQuantity.toFixed(8)} ${asset.symbol}`}
          </span>
        </div>

        {/* Submit */}
        <Button
          type="submit"
          variant={side === 'BUY' ? 'primary' : 'danger'}
          isLoading={isPending}
          disabled={!validation.valid || isPending}
          className="w-full"
        >
          {isPending
            ? 'Submitting...'
            : `${side} ${asset.symbol}`}
        </Button>
      </form>
    </Card>
  );
}
