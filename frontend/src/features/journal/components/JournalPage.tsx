import { useState, useMemo } from 'react';
import { useJournalEntries, useGenerateJournal } from '../hooks/useJournal';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { ErrorState } from '@/components/shared/ErrorState';
import type { JournalEntryResponse } from '../types/journal';

const EXPECTED_ERROR_TYPES = [
  'https://api.apex.com/errors/insufficient-trades',
  'https://api.apex.com/errors/journal-already-generated',
  'https://api.apex.com/errors/rate-limit-exceeded',
];

function isExpectedError(error: unknown): boolean {
  if (!error || typeof error !== 'object' || !('body' in error)) return false;
  const body = (error as { body: unknown }).body;
  if (!body || typeof body !== 'object') return false;
  const type = (body as { type?: string }).type;
  return typeof type === 'string' && EXPECTED_ERROR_TYPES.includes(type);
}

function getErrorDetail(error: unknown): string {
  if (!error || typeof error !== 'object') return 'An unexpected error occurred';
  if ('message' in error) return (error as { message: string }).message;
  return 'An unexpected error occurred';
}

function TodayEntry({ entry }: { entry: JournalEntryResponse | undefined }) {
  if (!entry) {
    return (
      <Card>
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-sm font-semibold text-neutral-100">
              Today's Journal
            </h2>
            <p className="text-xs text-neutral-500 mt-1">
              No entry generated yet for today.
            </p>
          </div>
        </div>
      </Card>
    );
  }

  return (
    <Card>
      <div className="flex items-start justify-between mb-3">
        <div>
          <h2 className="text-sm font-semibold text-neutral-100">
            Today's Journal
          </h2>
          <p className="text-xs text-neutral-500 mt-0.5">
            {entry.entryDate}
          </p>
        </div>
        <Badge variant="brand">Generated</Badge>
      </div>
      <div className="bg-neutral-850 rounded-md p-4 border border-neutral-800">
        <p className="text-sm text-neutral-200 leading-relaxed whitespace-pre-wrap">
          {entry.narrativeText}
        </p>
      </div>
      <p className="text-xs text-neutral-500 mt-2">
        Generated at {new Date(entry.generatedAt).toLocaleTimeString()}
      </p>
    </Card>
  );
}

function JournalHistory() {
  const [page, setPage] = useState(0);
  const { data, isLoading, error, refetch } = useJournalEntries(page, 10);

  if (isLoading) {
    return (
      <Card>
        <div className="space-y-3">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <ErrorState message="Failed to load journal history" onRetry={refetch} />
      </Card>
    );
  }

  const entries = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  if (entries.length === 0) {
    return (
      <Card>
        <EmptyState
          title="No journal entries"
          description="Generate your first journal entry to see your trading behavioral analysis."
        />
      </Card>
    );
  }

  return (
    <Card padding="none">
      <div className="px-4 py-3 border-b border-neutral-800">
        <h2 className="text-sm font-semibold text-neutral-100">
          Journal History
        </h2>
      </div>
      <div className="divide-y divide-neutral-800/50">
        {entries.map((entry) => (
          <div key={entry.id} className="px-4 py-3">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-medium text-neutral-300">
                {entry.entryDate}
              </span>
              <span className="text-xs text-neutral-500">
                {new Date(entry.generatedAt).toLocaleTimeString()}
              </span>
            </div>
            <p className="text-sm text-neutral-400 line-clamp-3">
              {entry.narrativeText}
            </p>
          </div>
        ))}
      </div>
      {totalPages > 1 && (
        <div className="flex items-center justify-between px-4 py-3 border-t border-neutral-800">
          <span className="text-xs text-neutral-500">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="ghost"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

export function JournalPage() {
  const generateMutation = useGenerateJournal();
  const { data: entriesData } = useJournalEntries(0, 1);

  const today = new Date().toISOString().split('T')[0];
  const todayEntry = entriesData?.content?.find((e) => e.entryDate === today);

  const errorObj = generateMutation.isError ? generateMutation.error : null;

  const expectedMessage = useMemo(
    () => (errorObj && isExpectedError(errorObj) ? getErrorDetail(errorObj) : null),
    [errorObj],
  );

  const realError = useMemo(
    () => (errorObj && !isExpectedError(errorObj) ? getErrorDetail(errorObj) : null),
    [errorObj],
  );

  const handleGenerate = () => {
    generateMutation.mutate();
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-neutral-100">Trade Journal</h1>
        <Button
          variant="primary"
          size="sm"
          isLoading={generateMutation.isPending}
          disabled={generateMutation.isPending}
          onClick={handleGenerate}
        >
          Generate Today's Entry
        </Button>
      </div>

      {expectedMessage && (
        <Card>
          <div className="flex items-center gap-2">
            <Badge variant="warning">Note</Badge>
            <p className="text-sm text-neutral-400">{expectedMessage}</p>
          </div>
        </Card>
      )}

      {realError && (
        <Card>
          <ErrorState title="Generation failed" message={realError} />
        </Card>
      )}

      {generateMutation.isSuccess && (
        <Card>
          <div className="flex items-center gap-2">
            <Badge variant="gain">Success</Badge>
            <p className="text-sm text-neutral-400">
              Journal entry generated for today.
            </p>
          </div>
        </Card>
      )}

      <TodayEntry entry={todayEntry} />
      <JournalHistory />
    </div>
  );
}
