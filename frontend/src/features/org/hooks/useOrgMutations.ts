import { useMutation, useQueryClient } from '@tanstack/react-query';
import { orgApi } from '../api/org.api';
import type { CreateOrganizationRequest, JoinOrganizationRequest, UpdateMembershipRoleRequest } from '../types/org';

export function useCreateOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateOrganizationRequest) => orgApi.createOrganization(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] });
    },
  });
}

export function useJoinOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: JoinOrganizationRequest) => orgApi.joinOrganization(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] });
    },
  });
}

export function useUpdateMemberRole(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ memberId, data }: { memberId: string; data: UpdateMembershipRoleRequest }) =>
      orgApi.updateMemberRole(orgId, memberId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orgMembers', orgId] });
    },
  });
}

export function useRemoveMember(orgId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (memberId: string) => orgApi.removeMember(orgId, memberId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orgMembers', orgId] });
    },
  });
}

export function useToggleLeaderboardVisibility() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (visible: boolean) => orgApi.toggleLeaderboardVisibility(visible),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['organizations'] });
    },
  });
}
