import { useEffect, useState, useCallback, createContext, useContext, type ReactNode } from 'react';

type ToastVariant = 'success' | 'error' | 'warning' | 'info';

interface Toast {
  id: string;
  message: string;
  variant: ToastVariant;
}

interface ToastContextValue {
  addToast: (message: string, variant?: ToastVariant) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within <ToastProvider>');
  return ctx;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((message: string, variant: ToastVariant = 'info') => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, message, variant }]);
  }, []);

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return (
    <ToastContext.Provider value={{ addToast }}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 pointer-events-none">
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} onDismiss={removeToast} />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

const variantClasses: Record<ToastVariant, string> = {
  success: 'border-gain/30 bg-neutral-900',
  error: 'border-loss/30 bg-neutral-900',
  warning: 'border-warning/30 bg-neutral-900',
  info: 'border-neutral-700 bg-neutral-900',
};

const dotClasses: Record<ToastVariant, string> = {
  success: 'bg-gain',
  error: 'bg-loss',
  warning: 'bg-warning',
  info: 'bg-brand-500',
};

function ToastItem({ toast, onDismiss }: { toast: Toast; onDismiss: (id: string) => void }) {
  useEffect(() => {
    const timer = setTimeout(() => onDismiss(toast.id), 5000);
    return () => clearTimeout(timer);
  }, [toast.id, onDismiss]);

  return (
    <div
      className={[
        'pointer-events-auto w-80 p-3 rounded-lg border shadow-lg',
        'flex items-start gap-3 animate-in slide-in-from-right',
        variantClasses[toast.variant],
      ].join(' ')}
    >
      <span className={['mt-1 h-2 w-2 rounded-full shrink-0', dotClasses[toast.variant]].join(' ')} />
      <p className="text-sm text-neutral-200 flex-1">{toast.message}</p>
      <button
        onClick={() => onDismiss(toast.id)}
        className="text-neutral-500 hover:text-neutral-300 transition-colors shrink-0"
        aria-label="Dismiss"
      >
        <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
          <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
        </svg>
      </button>
    </div>
  );
}
