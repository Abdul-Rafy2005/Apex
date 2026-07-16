import { useState, useEffect } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Skeleton } from '@/components/ui/Skeleton';
import { useGlobalSearch, useAddAsset } from '../hooks/useMarket';

interface AddAssetModalProps {
  open: boolean;
  onClose: () => void;
}

export function AddAssetModal({ open, onClose }: AddAssetModalProps) {
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');

  // Simple debounce so we don't spam the API on every keystroke
  useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 500);
    return () => clearTimeout(t);
  }, [query]);

  const { data, isLoading } = useGlobalSearch(debounced);
  const addAsset = useAddAsset();

  const handleAdd = (coin: { id: string; symbol: string; name: string; thumb: string }) => {
    addAsset.mutate(
      {
        symbol: coin.symbol,
        name: coin.name,
        providerSource: coin.id,
      },
      {
        onSuccess: () => {
          onClose();
          setQuery('');
        },
      }
    );
  };

  return (
    <Modal open={open} onClose={onClose} title="Search Global Markets">
      <div className="space-y-4">
        <Input
          placeholder="Search by name or symbol (e.g., Solana)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />

        <div className="max-h-[300px] overflow-y-auto space-y-2">
          {isLoading && query.length > 1 && <Skeleton className="h-12 w-full" />}
          
          {!isLoading && data?.map((coin) => (
            <div key={coin.id} className="flex justify-between items-center p-3 rounded-md bg-neutral-800">
              <div className="flex items-center gap-3">
                {coin.thumb && (
                  <img src={coin.thumb} alt={coin.name} className="w-8 h-8 rounded-full bg-neutral-700" />
                )}
                <div>
                  <p className="font-semibold text-neutral-100">{coin.name}</p>
                  <p className="text-xs text-neutral-400 uppercase">{coin.symbol}</p>
                </div>
              </div>
              <Button
                variant="primary"
                size="sm"
                onClick={() => handleAdd(coin)}
                isLoading={addAsset.isPending}
                disabled={addAsset.isPending}
              >
                Add Coin
              </Button>
            </div>
          ))}

          {!isLoading && data?.length === 0 && debounced.length > 1 && (
            <p className="text-center text-neutral-400 py-4">No coins found on CoinGecko.</p>
          )}
        </div>
      </div>
    </Modal>
  );
}
