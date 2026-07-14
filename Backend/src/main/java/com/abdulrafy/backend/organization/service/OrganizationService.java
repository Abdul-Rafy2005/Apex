package com.abdulrafy.backend.organization.service;

import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.entity.UserRole;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.common.exception.ConflictException;
import com.abdulrafy.backend.common.exception.ForbiddenException;
import com.abdulrafy.backend.common.exception.NotFoundException;
import com.abdulrafy.backend.organization.dto.*;
import com.abdulrafy.backend.organization.entity.Membership;
import com.abdulrafy.backend.organization.entity.Organization;
import com.abdulrafy.backend.organization.mapper.MembershipMapper;
import com.abdulrafy.backend.organization.mapper.OrganizationMapper;
import com.abdulrafy.backend.organization.repository.MembershipRepository;
import com.abdulrafy.backend.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
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
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (membershipRepository.existsByUserIdAndOrganizationId(userId, organizationId)) {
            throw new ConflictException("Already a member of this organization");
        }

        Membership membership = Membership.builder()
                .user(user)
                .organization(org)
                .role(UserRole.TRADER)
                .build();
        membership = membershipRepository.save(membership);

        return MembershipMapper.toResponse(membership);
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> listMembers(UUID organizationId, UUID requesterId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        Membership requesterMembership = membershipRepository
                .findByUserIdAndOrganizationId(requesterId, organizationId)
                .orElseThrow(() -> new ForbiddenException("Not a member of this organization"));

        if (requesterMembership.getRole() != UserRole.ORG_ADMIN &&
            requesterMembership.getRole() != UserRole.INSTRUCTOR) {
            throw new ForbiddenException("Only ORG_ADMIN or INSTRUCTOR can list members");
        }

        return membershipRepository.findByOrganizationId(organizationId).stream()
                .map(MembershipMapper::toResponse)
                .toList();
    }
}
