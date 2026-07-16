import { type ReactNode, Component } from 'react';
import { Button } from '@/components/ui/Button';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) return this.props.fallback;

      return (
        <div className="min-h-screen bg-neutral-950 flex items-center justify-center p-6">
          <div className="max-w-md text-center">
            <div className="text-6xl mb-4 text-loss">!</div>
            <h1 className="text-xl font-semibold text-neutral-100 mb-2">
              Something went wrong
            </h1>
            <p className="text-sm text-neutral-400 mb-6">
              {this.state.error?.message ?? 'An unexpected error occurred.'}
            </p>
            <Button
              variant="secondary"
              onClick={() => {
                this.setState({ hasError: false, error: null });
                window.location.reload();
              }}
            >
              Reload page
            </Button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
