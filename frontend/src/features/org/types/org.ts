export type UserRole = 'SUPER_ADMIN' | 'ORG_ADMIN' | 'INSTRUCTOR' | 'TRADER';

export interface OrganizationResponse {
  id: string;
  name: string;
  type: string;
  createdBy: string;
  createdAt: string;
}

export interface OrgDetailResponse {
  id: string;
  name: string;
  type: string;
  createdBy: string;
  createdAt: string;
  currentUserRole: UserRole;
  memberCount: number;
}

export interface MembershipResponse {
  id: string;
  userId: string;
  userEmail: string;
  userDisplayName: string;
  role: UserRole;
  createdAt: string;
}

export interface LeaderboardEntry {
  userId: string;
  displayName: string;
  totalReturnPct: number;
  portfolioValue: number;
  rank: number;
}

export interface AuditLogEntry {
  id: string;
  userId: string;
  userEmail: string;
  action: string;
  targetUserId: string | null;
  targetUserEmail: string | null;
  detail: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLogEntry[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export interface CreateOrganizationRequest {
  name: string;
  type?: string;
}

export interface JoinOrganizationRequest {
  organizationId: string;
}

export interface UpdateMembershipRoleRequest {
  role: UserRole;
}
