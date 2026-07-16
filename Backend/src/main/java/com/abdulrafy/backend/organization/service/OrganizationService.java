package com.abdulrafy.backend.organization.service;

import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.entity.UserRole;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.common.exception.ConflictException;
import com.abdulrafy.backend.common.exception.ForbiddenException;
import com.abdulrafy.backend.common.exception.NotFoundException;
import com.abdulrafy.backend.organization.dto.*;
import com.abdulrafy.backend.organization.entity.AuditLog;
import com.abdulrafy.backend.organization.entity.Membership;
import com.abdulrafy.backend.organization.entity.Organization;
import com.abdulrafy.backend.organization.mapper.MembershipMapper;
import com.abdulrafy.backend.organization.mapper.OrganizationMapper;
import com.abdulrafy.backend.organization.repository.AuditLogRepository;
import com.abdulrafy.backend.organization.repository.MembershipRepository;
import com.abdulrafy.backend.organization.repository.OrganizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, UUID creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Organization org = Organization.builder()
                .name(request.name())
                .type(request.type() != null ? request.type() : "INDIVIDUAL")
                .createdBy(creator)
                .build();
        org = organizationRepository.save(org);

        Membership membership = Membership.builder()
                .user(creator)
                .organization(org)
                .role(UserRole.ORG_ADMIN)
                .build();
        membershipRepository.save(membership);

        return OrganizationMapper.toResponse(org);
    }

    @Transactional
    public MembershipResponse joinOrganization(UUID organizationId, UUID userId) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (membershipRepository.existsByUserIdAndOrganizationId(userId, organizationId)) {
            throw new ConflictException("Already a member of this organization");
        }

        Membership membership = Membership.builder()
                .user(user)
                .organization(organizationRepository.getReferenceById(organizationId))
                .role(UserRole.TRADER)
                .build();
        membership = membershipRepository.save(membership);

        return MembershipMapper.toResponse(membership);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listOrganizations(UUID userId) {
        return membershipRepository.findByUserId(userId).stream()
                .map(m -> OrganizationMapper.toResponse(m.getOrganization()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrgDetailResponse getOrganization(UUID organizationId, UUID userId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        Membership membership = membershipRepository
                .findByUserIdAndOrganizationId(userId, organizationId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this organization"));

        List<MembershipResponse> members = membershipRepository.findByOrganizationId(organizationId).stream()
                .map(MembershipMapper::toResponse)
                .toList();

        return new OrgDetailResponse(
                org.getId(),
                org.getName(),
                org.getType(),
                org.getCreatedBy().getId(),
                org.getCreatedAt(),
                membership.getRole(),
                members.size()
        );
    }

    @Transactional
    public List<MembershipResponse> listMembers(UUID organizationId, UUID requesterId) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        Membership requesterMembership = membershipRepository
                .findByUserIdAndOrganizationId(requesterId, organizationId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this organization"));

        if (requesterMembership.getRole() != UserRole.ORG_ADMIN &&
            requesterMembership.getRole() != UserRole.INSTRUCTOR) {
            throw new ForbiddenException("Only ORG_ADMIN or INSTRUCTOR can list members");
        }

        logAudit(organizationId, requesterId, "LIST_MEMBERS", null, null);

        return membershipRepository.findByOrganizationId(organizationId).stream()
                .map(MembershipMapper::toResponse)
                .toList();
    }

    @Transactional
    public MembershipResponse updateMemberRole(UUID organizationId, UUID targetUserId, UserRole newRole, UUID requesterId) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        Membership requesterMembership = membershipRepository
                .findByUserIdAndOrganizationId(requesterId, organizationId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this organization"));

        if (requesterMembership.getRole() != UserRole.ORG_ADMIN) {
            throw new ForbiddenException("Only ORG_ADMIN can update member roles");
        }

        Membership targetMembership = membershipRepository
                .findByUserIdAndOrganizationId(targetUserId, organizationId)
                .orElseThrow(() -> new NotFoundException("Member not found in this organization"));

        if (targetMembership.getRole() == UserRole.ORG_ADMIN && newRole != UserRole.ORG_ADMIN) {
            long adminCount = membershipRepository.findByOrganizationId(organizationId).stream()
                    .filter(m -> m.getRole() == UserRole.ORG_ADMIN)
                    .count();
            if (adminCount <= 1) {
                throw new ConflictException("Cannot demote the last ORG_ADMIN");
            }
        }

        targetMembership.setRole(newRole);
        targetMembership = membershipRepository.save(targetMembership);

        logAudit(organizationId, requesterId, "ROLE_CHANGE", targetUserId,
                "Role changed to " + newRole);

        return MembershipMapper.toResponse(targetMembership);
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID targetUserId, UUID requesterId) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        Membership requesterMembership = membershipRepository
                .findByUserIdAndOrganizationId(requesterId, organizationId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this organization"));

        if (requesterMembership.getRole() != UserRole.ORG_ADMIN) {
            throw new ForbiddenException("Only ORG_ADMIN can remove members");
        }

        if (requesterId.equals(targetUserId)) {
            throw new ConflictException("Cannot remove yourself from the organization");
        }

        Membership targetMembership = membershipRepository
                .findByUserIdAndOrganizationId(targetUserId, organizationId)
                .orElseThrow(() -> new NotFoundException("Member not found in this organization"));

        if (targetMembership.getRole() == UserRole.ORG_ADMIN) {
            throw new ConflictException("Cannot remove an ORG_ADMIN");
        }

        membershipRepository.delete(targetMembership);

        logAudit(organizationId, requesterId, "MEMBER_REMOVED", targetUserId, null);
    }

    @Transactional
    public void toggleLeaderboardVisibility(UUID userId, boolean visible) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setLeaderboardVisible(visible);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLog(UUID organizationId, UUID requesterId, int page, int size) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        Membership requesterMembership = membershipRepository
                .findByUserIdAndOrganizationId(requesterId, organizationId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this organization"));

        if (requesterMembership.getRole() != UserRole.ORG_ADMIN) {
            throw new ForbiddenException("Only ORG_ADMIN can view audit log");
        }

        Page<AuditLog> logPage = auditLogRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId, PageRequest.of(page, size));

        return logPage.map(entry -> new AuditLogResponse(
                entry.getId(),
                entry.getUser().getId(),
                entry.getUser().getEmail(),
                entry.getAction(),
                entry.getTargetUser() != null ? entry.getTargetUser().getId() : null,
                entry.getTargetUser() != null ? entry.getTargetUser().getEmail() : null,
                entry.getDetail(),
                entry.getCreatedAt()
        ));
    }

    private void logAudit(UUID organizationId, UUID userId, String action, UUID targetUserId, String detail) {
        User actor = userRepository.findById(userId).orElse(null);
        User target = targetUserId != null ? userRepository.findById(targetUserId).orElse(null) : null;
        Organization org = organizationRepository.findById(organizationId).orElse(null);
        if (actor == null || org == null) return;

        AuditLog log = AuditLog.builder()
                .user(actor)
                .organization(org)
                .action(action)
                .targetUser(target)
                .detail(detail)
                .build();
        auditLogRepository.save(log);
    }
}
