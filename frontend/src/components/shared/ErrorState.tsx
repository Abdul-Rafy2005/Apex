interface ErrorStateProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export function ErrorState({ title = 'Something went wrong', message, onRetry }: ErrorStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <div className="w-8 h-8 rounded-full bg-loss-dim flex items-center justify-center mb-3">
        <svg className="w-4 h-4 text-loss" viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-5a.75.75 0 01.75.75v4.5a.75.75 0 01-1.5 0v-4.5A.75.75 0 0110 5zm0 10a1 1 0 100-2 1 1 0 000 2z" clipRule="evenodd" />
        </svg>
      </div>
      <h3 className="text-sm font-medium text-neutral-300">{title}</h3>
      <p className="text-xs text-neutral-500 mt-1 max-w-xs">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="mt-3 text-xs text-brand-400 hover:text-brand-500 transition-colors"
        >
          Try again
        </button>
      )}
    </div>
  );
}
