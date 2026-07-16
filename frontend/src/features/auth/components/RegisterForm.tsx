import { useState, type FormEvent } from 'react';
import { useAuthStore } from '@/store/auth.store';
import { ApiError } from '@/features/auth/api/auth.api';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

export function RegisterForm({ onSwitch }: { onSwitch: () => void }) {
  const register = useAuthStore((s) => s.register);
  const isLoading = useAuthStore((s) => s.isLoading);
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  function validate(): boolean {
    const errs: Record<string, string> = {};
    if (displayName.length < 1 || displayName.length > 100) {
      errs.displayName = 'Display name must be 1-100 characters';
    }
    if (password.length < 8 || password.length > 128) {
      errs.password = 'Password must be at least 8 characters';
    }
    setFieldErrors(errs);
    return Object.keys(errs).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!validate()) return;

    try {
      await register(email, password, displayName);
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
        <p className="text-sm text-neutral-400 mt-1">Create your account</p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && (
          <div className="p-3 rounded-md bg-loss/10 border border-loss/20 text-sm text-loss">
            {error}
          </div>
        )}

        <Input
          label="Display Name"
          type="text"
          placeholder="Your name"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
          error={fieldErrors.displayName}
          autoComplete="name"
        />

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
          placeholder="At least 8 characters"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          error={fieldErrors.password}
          autoComplete="new-password"
        />

        <Button
          type="submit"
          isLoading={isLoading}
          className="w-full mt-2"
        >
          Create account
        </Button>
      </form>

      <p className="text-sm text-neutral-400 text-center mt-6">
        Already have an account?{' '}
        <button
          onClick={onSwitch}
          className="text-brand-400 hover:text-brand-500 transition-colors font-medium"
        >
          Sign in
        </button>
      </p>
    </div>
  );
}
