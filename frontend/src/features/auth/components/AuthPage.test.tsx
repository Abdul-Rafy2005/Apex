import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthPage } from './AuthPage';
import * as authApi from '../api/auth.api';
import { useAuthStore } from '@/store/auth.store';

vi.mock('../api/auth.api', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    refresh: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    constructor(message: string, status: number) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
    }
  },
}));

function renderWithProviders(ui: React.ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('AuthPage', () => {
  beforeEach(() => {
    useAuthStore.setState({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isLoading: false,
    });
    vi.clearAllMocks();
  });

  it('renders login form by default', () => {
    renderWithProviders(<AuthPage />);
    expect(screen.getByText('Sign in to your account')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('switches to register form', () => {
    renderWithProviders(<AuthPage />);
    fireEvent.click(screen.getByText('Create one'));
    expect(screen.getByText('Create your account')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create account' })).toBeInTheDocument();
  });

  it('switches back to login from register', () => {
    renderWithProviders(<AuthPage />);
    fireEvent.click(screen.getByText('Create one'));
    fireEvent.click(screen.getByText('Sign in'));
    expect(screen.getByText('Sign in to your account')).toBeInTheDocument();
  });

  it('shows error on failed login', async () => {
    vi.mocked(authApi.authApi.login).mockRejectedValue(
      new (authApi.ApiError)('Invalid credentials', 401),
    );

    renderWithProviders(<AuthPage />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(screen.getByText('Invalid credentials')).toBeInTheDocument();
    });
  });

  it('shows validation error on register with short password', async () => {
    renderWithProviders(<AuthPage />);
    fireEvent.click(screen.getByText('Create one'));

    fireEvent.change(screen.getByLabelText('Display Name'), { target: { value: 'Test User' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'short' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => {
      expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument();
    });
  });
});
