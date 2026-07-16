import { apiFetch } from '@/lib/api';
import type {
  OrganizationResponse,
  OrgDetailResponse,
  MembershipResponse,
  LeaderboardEntry,
  AuditLogPage,
  CreateOrganizationRequest,
  JoinOrganizationRequest,
  UpdateMembershipRoleRequest,
} from '../types/org';

export const orgApi = {
  listOrganizations: () =>
    apiFetch<OrganizationResponse[]>('/api/v1/organizations'),

  getOrganization: (orgId: string) =>
    apiFetch<OrgDetailResponse>(`/api/v1/organizations/${orgId}`),

  createOrganization: (data: CreateOrganizationRequest) =>
    apiFetch<OrganizationResponse>('/api/v1/organizations', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  joinOrganization: (data: JoinOrganizationRequest) =>
    apiFetch<MembershipResponse>('/api/v1/organizations/join', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  listMembers: (orgId: string) =>
    apiFetch<MembershipResponse[]>(`/api/v1/organizations/${orgId}/members`),

  updateMemberRole: (orgId: string, memberId: string, data: UpdateMembershipRoleRequest) =>
    apiFetch<MembershipResponse>(`/api/v1/organizations/${orgId}/members/${memberId}/role`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  removeMember: (orgId: string, memberId: string) =>
    apiFetch<void>(`/api/v1/organizations/${orgId}/members/${memberId}`, {
      method: 'DELETE',
    }),

  getLeaderboard: (orgId: string) =>
    apiFetch<LeaderboardEntry[]>(`/api/v1/organizations/${orgId}/leaderboard`),

  toggleLeaderboardVisibility: (visible: boolean) =>
    apiFetch<void>('/api/v1/organizations/leaderboard/visibility', {
      method: 'PUT',
      body: JSON.stringify({ visible }),
    }),

  getAuditLog: (orgId: string, page = 0, size = 20) =>
    apiFetch<AuditLogPage>(`/api/v1/organizations/${orgId}/audit-log?page=${page}&size=${size}`),
};
