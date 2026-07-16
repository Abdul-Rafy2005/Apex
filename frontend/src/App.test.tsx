import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect } from 'vitest';
import App from './App';

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

describe('App', () => {
  it('renders the Apex brand name', () => {
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <App />
      </QueryClientProvider>,
    );
    expect(screen.getAllByText('Apex').length).toBeGreaterThan(0);
  });

  it('renders the login form on the auth route', () => {
    window.history.pushState({}, '', '/auth');
    render(
      <QueryClientProvider client={createTestQueryClient()}>
        <App />
      </QueryClientProvider>,
    );
    expect(screen.getByText('Sign in to your account')).toBeInTheDocument();
  });
});
