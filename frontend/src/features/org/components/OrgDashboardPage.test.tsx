import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { OrgDashboardPage } from './OrgDashboardPage';
import * as orgHooks from '../hooks/useOrg';

vi.mock('../hooks/useOrg', () => ({
  useOrganization: vi.fn(),
  useOrgMembers: vi.fn(),
  useLeaderboard: vi.fn(),
  useAuditLog: vi.fn(),
}));

vi.mock('../hooks/useOrgMutations', () => ({
  useUpdateMemberRole: vi.fn(() => ({ mutate: vi.fn() })),
  useRemoveMember: vi.fn(() => ({ mutate: vi.fn() })),
}));

vi.mock('@/store/auth.store', () => ({
  useAuthStore: vi.fn((selector: (s: Record<string, unknown>) => unknown) =>
    selector({ user: { id: 'user-1', displayName: 'Test User', email: 'test@test.com' } }),
  ),
}));

function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function renderWithProviders(ui: React.ReactElement) {
  const queryClient = createTestQueryClient();
  return render(
    createElement(QueryClientProvider, { client: queryClient },
      createElement(MemoryRouter, { initialEntries: ['/org/org-1'] }, ui),
    ),
  );
}

describe('OrgDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders org name and member count for ORG_ADMIN', () => {
    vi.mocked(orgHooks.useOrganization).mockReturnValue({
      data: {
        id: 'org-1',
        name: 'Test Org',
        type: 'BOOTCAMP',
        createdBy: 'user-1',
        createdAt: '2024-01-01T00:00:00Z',
        currentUserRole: 'ORG_ADMIN',
        memberCount: 5,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrganization>);
    vi.mocked(orgHooks.useOrgMembers).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrgMembers>);
    vi.mocked(orgHooks.useLeaderboard).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useLeaderboard>);
    vi.mocked(orgHooks.useAuditLog).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0 },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useAuditLog>);

    renderWithProviders(createElement(OrgDashboardPage));

    expect(screen.getByText('Test Org')).toBeInTheDocument();
    expect(screen.getByText(/5 members/)).toBeInTheDocument();
  });

  it('shows Audit Log tab for ORG_ADMIN', () => {
    vi.mocked(orgHooks.useOrganization).mockReturnValue({
      data: {
        id: 'org-1',
        name: 'Org',
        type: 'BOOTCAMP',
        createdBy: 'user-1',
        createdAt: '2024-01-01T00:00:00Z',
        currentUserRole: 'ORG_ADMIN',
        memberCount: 2,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrganization>);
    vi.mocked(orgHooks.useOrgMembers).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrgMembers>);
    vi.mocked(orgHooks.useLeaderboard).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useLeaderboard>);
    vi.mocked(orgHooks.useAuditLog).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0 },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useAuditLog>);

    renderWithProviders(createElement(OrgDashboardPage));

    expect(screen.getByText('Audit Log')).toBeInTheDocument();
  });

  it('hides Audit Log tab for INSTRUCTOR', () => {
    vi.mocked(orgHooks.useOrganization).mockReturnValue({
      data: {
        id: 'org-1',
        name: 'Org',
        type: 'BOOTCAMP',
        createdBy: 'user-1',
        createdAt: '2024-01-01T00:00:00Z',
        currentUserRole: 'INSTRUCTOR',
        memberCount: 2,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrganization>);
    vi.mocked(orgHooks.useOrgMembers).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrgMembers>);
    vi.mocked(orgHooks.useLeaderboard).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useLeaderboard>);
    vi.mocked(orgHooks.useAuditLog).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0 },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useAuditLog>);

    renderWithProviders(createElement(OrgDashboardPage));

    expect(screen.queryByText('Audit Log')).not.toBeInTheDocument();
    expect(screen.getByText('Leaderboard')).toBeInTheDocument();
  });

  it('hides Leaderboard tab for TRADER', () => {
    vi.mocked(orgHooks.useOrganization).mockReturnValue({
      data: {
        id: 'org-1',
        name: 'Org',
        type: 'BOOTCAMP',
        createdBy: 'user-1',
        createdAt: '2024-01-01T00:00:00Z',
        currentUserRole: 'TRADER',
        memberCount: 2,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrganization>);
    vi.mocked(orgHooks.useOrgMembers).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrgMembers>);
    vi.mocked(orgHooks.useLeaderboard).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useLeaderboard>);
    vi.mocked(orgHooks.useAuditLog).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0 },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useAuditLog>);

    renderWithProviders(createElement(OrgDashboardPage));

    expect(screen.queryByText('Audit Log')).not.toBeInTheDocument();
    expect(screen.queryByText('Leaderboard')).not.toBeInTheDocument();
    expect(screen.getByText('Members')).toBeInTheDocument();
  });

  it('shows Members tab for all roles', () => {
    vi.mocked(orgHooks.useOrganization).mockReturnValue({
      data: {
        id: 'org-1',
        name: 'Org',
        type: 'BOOTCAMP',
        createdBy: 'user-1',
        createdAt: '2024-01-01T00:00:00Z',
        currentUserRole: 'TRADER',
        memberCount: 2,
      },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrganization>);
    vi.mocked(orgHooks.useOrgMembers).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrgMembers>);
    vi.mocked(orgHooks.useLeaderboard).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useLeaderboard>);
    vi.mocked(orgHooks.useAuditLog).mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0 },
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useAuditLog>);

    renderWithProviders(createElement(OrgDashboardPage));

    expect(screen.getByText('Members')).toBeInTheDocument();
  });

  it('shows loading skeleton while loading', () => {
    vi.mocked(orgHooks.useOrganization).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      refetch: vi.fn(),
    } as ReturnType<typeof orgHooks.useOrganization>);

    renderWithProviders(createElement(OrgDashboardPage));

    expect(document.querySelector('.animate-pulse')).toBeInTheDocument();
  });
});
