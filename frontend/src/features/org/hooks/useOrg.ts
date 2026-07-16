import { useQuery } from '@tanstack/react-query';
import { orgApi } from '../api/org.api';

export function useOrganizations() {
  return useQuery({
    queryKey: ['organizations'],
    queryFn: () => orgApi.listOrganizations(),
  });
}

export function useOrganization(orgId: string | null) {
  return useQuery({
    queryKey: ['organization', orgId],
    queryFn: () => orgApi.getOrganization(orgId!),
    enabled: !!orgId,
  });
}

export function useOrgMembers(orgId: string | null) {
  return useQuery({
    queryKey: ['orgMembers', orgId],
    queryFn: () => orgApi.listMembers(orgId!),
    enabled: !!orgId,
  });
}

export function useLeaderboard(orgId: string | null) {
  return useQuery({
    queryKey: ['leaderboard', orgId],
    queryFn: () => orgApi.getLeaderboard(orgId!),
    enabled: !!orgId,
  });
}

export function useAuditLog(orgId: string | null, page = 0) {
  return useQuery({
    queryKey: ['auditLog', orgId, page],
    queryFn: () => orgApi.getAuditLog(orgId!, page),
    enabled: !!orgId,
  });
}
