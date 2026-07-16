import { useState, type FormEvent } from 'react';
import { useAuthStore } from '@/store/auth.store';
import { ApiError } from '@/features/auth/api/auth.api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function LoginForm({ onSwitch }: { onSwitch: () => void }) {
  const login = useAuthStore((s) => s.login);
  const isLoading = useAuthStore((s) => s.isLoading);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await login(email, password);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('An unexpected error occurred');
      }
    }
  }

  return (
    <div className="w-full max-w-sm mx-auto">
      <div className="mb-8 text-center">
        <h1 className="text-2xl font-bold text-neutral-100 tracking-tight">Apex</h1>
        <p className="text-sm text-neutral-400 mt-1">Sign in to your account</p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && (
          <div className="p-3 rounded-md bg-loss/10 border border-loss/20 text-sm text-loss">
            {error}
          </div>
        )}

        <Input
          label="Email"
          type="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          autoComplete="email"
        />

        <Input
          label="Password"
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          autoComplete="current-password"
        />

        <Button
          type="submit"
          isLoading={isLoading}
          className="w-full mt-2"
        >
          Sign in
        </Button>
      </form>

      <p className="text-sm text-neutral-400 text-center mt-6">
        Don&apos;t have an account?{' '}
        <button
          onClick={onSwitch}
          className="text-brand-400 hover:text-brand-500 transition-colors font-medium"
        >
          Create one
        </button>
      </p>
    </div>
  );
}
