import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthPage } from './AuthPage';
import * as authApi from '../api/auth.api';
import { useAuthStore } from '@/store/auth.store';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

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
    mockNavigate.mockClear();
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

  it('shows error on failed login with 401', async () => {
    vi.mocked(authApi.authApi.login).mockRejectedValue(
      new (authApi.ApiError)('Invalid credentials', 401),
    );

    renderWithProviders(<AuthPage />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(screen.getByText('Invalid email or password')).toBeInTheDocument();
    });
  });

  it('shows error on failed login with 429', async () => {
    vi.mocked(authApi.authApi.login).mockRejectedValue(
      new (authApi.ApiError)('Too many attempts', 429),
    );

    renderWithProviders(<AuthPage />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(screen.getByText('Too many login attempts. Please wait and try again.')).toBeInTheDocument();
    });
  });

  it('shows generic error on other login failure', async () => {
    vi.mocked(authApi.authApi.login).mockRejectedValue(
      new (authApi.ApiError)('Internal server error', 500),
    );

    renderWithProviders(<AuthPage />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(screen.getByText('Something went wrong. Please try again.')).toBeInTheDocument();
    });
  });

  it('redirects to dashboard on successful login', async () => {
    vi.mocked(authApi.authApi.login).mockResolvedValue({
      accessToken: 'token',
      user: { id: '1', email: 'test@test.com', displayName: 'Test User', role: 'TRADER', createdAt: new Date().toISOString() },
    });

    renderWithProviders(<AuthPage />);

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/');
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

  it('shows conflict error on 409 register', async () => {
    vi.mocked(authApi.authApi.register).mockRejectedValue(
      new (authApi.ApiError)('Email already exists', 409),
    );

    renderWithProviders(<AuthPage />);
    fireEvent.click(screen.getByText('Create one'));

    fireEvent.change(screen.getByLabelText('Display Name'), { target: { value: 'Test User' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => {
      expect(screen.getByText('An account with this email already exists')).toBeInTheDocument();
    });
  });

  it('redirects to dashboard on successful register', async () => {
    vi.mocked(authApi.authApi.register).mockResolvedValue({
      accessToken: 'token',
      user: { id: '1', email: 'test@test.com', displayName: 'Test User', role: 'TRADER' as const, createdAt: '2026-01-01T00:00:00Z' },
    });

    renderWithProviders(<AuthPage />);
    fireEvent.click(screen.getByText('Create one'));

    fireEvent.change(screen.getByLabelText('Display Name'), { target: { value: 'Test User' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'test@test.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/');
    });
  });
});
