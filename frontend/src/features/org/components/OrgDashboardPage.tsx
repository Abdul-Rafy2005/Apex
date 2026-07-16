import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/shared/EmptyState';
import { ErrorState } from '@/components/shared/ErrorState';
import {
  Table, Thead, Tbody, Tr, Th, Td,
} from '@/components/ui/Table';
import { useOrganization, useOrgMembers, useLeaderboard, useAuditLog } from '../hooks/useOrg';
import { useUpdateMemberRole, useRemoveMember } from '../hooks/useOrgMutations';
import { useAuthStore } from '@/store/auth.store';
import type { UserRole } from '../types/org';

const roleBadgeVariant: Record<UserRole, 'brand' | 'default' | 'gain' | 'warning'> = {
  ORG_ADMIN: 'brand',
  INSTRUCTOR: 'gain',
  TRADER: 'default',
  SUPER_ADMIN: 'warning',
};

type Tab = 'members' | 'leaderboard' | 'audit';

export function OrgDashboardPage() {
  const { orgId } = useParams<{ orgId: string }>();
  const user = useAuthStore((s) => s.user);
  const [activeTab, setActiveTab] = useState<Tab>('members');

  const orgQuery = useOrganization(orgId ?? null);
  const membersQuery = useOrgMembers(orgId ?? null);
  const leaderboardQuery = useLeaderboard(orgId ?? null);
  const auditQuery = useAuditLog(orgId ?? null);

  const updateRole = useUpdateMemberRole(orgId ?? '');
  const removeMember = useRemoveMember(orgId ?? '');

  const org = orgQuery.data;
  const userRole = org?.currentUserRole;
  const canManage = userRole === 'ORG_ADMIN';
  const canViewLeaderboard = userRole === 'ORG_ADMIN' || userRole === 'INSTRUCTOR';

  const tabs: { key: Tab; label: string; visible: boolean }[] = [
    { key: 'members', label: 'Members', visible: true },
    { key: 'leaderboard', label: 'Leaderboard', visible: canViewLeaderboard },
    { key: 'audit', label: 'Audit Log', visible: canManage },
  ];

  if (orgQuery.isLoading) {
    return (
      <div className="p-6 space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-6 w-32" />
        <Card><Skeleton className="h-48" /></Card>
      </div>
    );
  }

  if (orgQuery.isError) {
    return (
      <div className="p-6">
        <ErrorState message="Failed to load organization" onRetry={() => orgQuery.refetch()} />
      </div>
    );
  }

  if (!org) {
    return (
      <div className="p-6">
        <EmptyState title="No organization found" description="Create or join an organization to get started." />
      </div>
    );
  }

  function handleRoleChange(memberId: string, newRole: UserRole) {
    updateRole.mutate({ memberId, data: { role: newRole } });
  }

  function handleRemoveMember(memberId: string) {
    if (confirm('Remove this member from the organization?')) {
      removeMember.mutate(memberId);
    }
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-neutral-100">{org.name}</h1>
        <p className="text-sm text-neutral-400 mt-1">
          {org.type} &middot; {org.memberCount} members &middot; Your role:{' '}
          <Badge variant={roleBadgeVariant[userRole ?? 'TRADER']}>{userRole}</Badge>
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-neutral-800">
        {tabs.filter((t) => t.visible).map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={[
              'px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px',
              activeTab === tab.key
                ? 'text-neutral-100 border-brand-500'
                : 'text-neutral-400 border-transparent hover:text-neutral-200',
            ].join(' ')}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Members Tab */}
      {activeTab === 'members' && (
        <Card padding="none">
          {membersQuery.isLoading ? (
            <div className="p-4 space-y-2">
              {[1, 2, 3].map((i) => <Skeleton key={i} className="h-12" />)}
            </div>
          ) : membersQuery.isError ? (
            <ErrorState message="Failed to load members" onRetry={() => membersQuery.refetch()} />
          ) : !membersQuery.data?.length ? (
            <EmptyState title="No members" description="This organization has no members yet." />
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Member</Th>
                  <Th>Role</Th>
                  <Th>Joined</Th>
                  {canManage && <Th className="text-right">Actions</Th>}
                </Tr>
              </Thead>
              <Tbody>
                {membersQuery.data.map((m) => (
                  <Tr key={m.id}>
                    <Td>
                      <div>
                        <p className="text-sm text-neutral-100">{m.userDisplayName}</p>
                        <p className="text-xs text-neutral-500">{m.userEmail}</p>
                      </div>
                    </Td>
                    <Td>
                      <Badge variant={roleBadgeVariant[m.role]}>{m.role}</Badge>
                    </Td>
                    <Td className="text-neutral-400 text-xs">
                      {new Date(m.createdAt).toLocaleDateString()}
                    </Td>
                    {canManage && (
                      <Td className="text-right">
                        {m.userId !== user?.id && m.role !== 'ORG_ADMIN' && (
                          <div className="flex gap-1 justify-end">
                            <select
                              value={m.role}
                              onChange={(e) => handleRoleChange(m.userId, e.target.value as UserRole)}
                              className="text-xs bg-neutral-800 border border-neutral-700 rounded px-2 py-1 text-neutral-200"
                            >
                              <option value="TRADER">TRADER</option>
                              <option value="INSTRUCTOR">INSTRUCTOR</option>
                              <option value="ORG_ADMIN">ORG_ADMIN</option>
                            </select>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => handleRemoveMember(m.userId)}
                            >
                              Remove
                            </Button>
                          </div>
                        )}
                      </Td>
                    )}
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}
        </Card>
      )}

      {/* Leaderboard Tab */}
      {activeTab === 'leaderboard' && (
        <Card padding="none">
          {leaderboardQuery.isLoading ? (
            <div className="p-4 space-y-2">
              {[1, 2, 3].map((i) => <Skeleton key={i} className="h-12" />)}
            </div>
          ) : leaderboardQuery.isError ? (
            <ErrorState message="Failed to load leaderboard" onRetry={() => leaderboardQuery.refetch()} />
          ) : !leaderboardQuery.data?.length ? (
            <EmptyState
              title="No leaderboard data"
              description="Members haven't opted in to the leaderboard yet."
            />
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Rank</Th>
                  <Th>Trader</Th>
                  <Th className="text-right">Portfolio Value</Th>
                </Tr>
              </Thead>
              <Tbody>
                {leaderboardQuery.data.map((entry) => (
                  <Tr key={entry.userId}>
                    <Td>
                      <span className="text-sm font-mono text-neutral-100">#{entry.rank}</span>
                    </Td>
                    <Td className="text-sm text-neutral-100">{entry.displayName}</Td>
                    <Td className="text-right font-mono text-sm text-neutral-100">
                      ${entry.portfolioValue.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}
        </Card>
      )}

      {/* Audit Log Tab */}
      {activeTab === 'audit' && (
        <Card padding="none">
          {auditQuery.isLoading ? (
            <div className="p-4 space-y-2">
              {[1, 2, 3].map((i) => <Skeleton key={i} className="h-12" />)}
            </div>
          ) : auditQuery.isError ? (
            <ErrorState message="Failed to load audit log" onRetry={() => auditQuery.refetch()} />
          ) : !auditQuery.data?.content?.length ? (
            <EmptyState title="No audit entries" description="No actions have been logged yet." />
          ) : (
            <>
              <Table>
                <Thead>
                  <Tr>
                    <Th>Action</Th>
                    <Th>Actor</Th>
                    <Th>Target</Th>
                    <Th>Detail</Th>
                    <Th>Time</Th>
                  </Tr>
                </Thead>
                <Tbody>
                  {auditQuery.data.content.map((entry) => (
                    <Tr key={entry.id}>
                      <Td>
                        <Badge variant="default">{entry.action}</Badge>
                      </Td>
                      <Td className="text-xs text-neutral-300">{entry.userEmail}</Td>
                      <Td className="text-xs text-neutral-400">
                        {entry.targetUserEmail ?? '—'}
                      </Td>
                      <Td className="text-xs text-neutral-500">{entry.detail ?? '—'}</Td>
                      <Td className="text-xs text-neutral-500">
                        {new Date(entry.createdAt).toLocaleString()}
                      </Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
              {auditQuery.data.totalPages > 1 && (
                <div className="flex justify-between items-center px-4 py-3 border-t border-neutral-800">
                  <span className="text-xs text-neutral-500">
                    Page {(auditQuery.data.number ?? 0) + 1} of {auditQuery.data.totalPages}
                  </span>
                  <div className="flex gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={(auditQuery.data.number ?? 0) === 0}
                      onClick={() => auditQuery.refetch()}
                    >
                      Previous
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={(auditQuery.data.number ?? 0) >= auditQuery.data.totalPages - 1}
                      onClick={() => auditQuery.refetch()}
                    >
                      Next
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </Card>
      )}
    </div>
  );
}
