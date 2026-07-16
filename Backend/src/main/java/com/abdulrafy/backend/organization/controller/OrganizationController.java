package com.abdulrafy.backend.organization.controller;

import com.abdulrafy.backend.common.security.AuthenticatedUser;
import com.abdulrafy.backend.organization.dto.*;
import com.abdulrafy.backend.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Organization management and membership")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @Operation(summary = "Create a new organization (any authenticated user)")
    public ResponseEntity<OrganizationResponse> create(
            @Valid @RequestBody CreateOrganizationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createOrganization(request, user.id()));
    }

    @PostMapping("/join")
    @Operation(summary = "Join an existing organization")
    public ResponseEntity<MembershipResponse> join(
            @Valid @RequestBody JoinOrganizationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizationService.joinOrganization(request.organizationId(), user.id()));
    }

    @GetMapping
    @Operation(summary = "List organizations the current user belongs to")
    public ResponseEntity<List<OrganizationResponse>> listOrganizations(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizationService.listOrganizations(user.id()));
    }

    @GetMapping("/{organizationId}")
    @Operation(summary = "Get organization details (member only)")
    public ResponseEntity<OrgDetailResponse> getOrganization(
            @PathVariable java.util.UUID organizationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizationService.getOrganization(organizationId, user.id()));
    }

    @GetMapping("/{organizationId}/members")
    @Operation(summary = "List organization members (ORG_ADMIN/INSTRUCTOR only)")
    public ResponseEntity<List<MembershipResponse>> listMembers(
            @PathVariable java.util.UUID organizationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizationService.listMembers(organizationId, user.id()));
    }

    @PutMapping("/{organizationId}/members/{memberId}/role")
    @Operation(summary = "Update a member's role (ORG_ADMIN only)")
    public ResponseEntity<MembershipResponse> updateMemberRole(
            @PathVariable java.util.UUID organizationId,
            @PathVariable java.util.UUID memberId,
            @Valid @RequestBody UpdateMembershipRoleRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizationService.updateMemberRole(
                organizationId, memberId, request.role(), user.id()));
    }

    @DeleteMapping("/{organizationId}/members/{memberId}")
    @Operation(summary = "Remove a member from the organization (ORG_ADMIN only)")
    public ResponseEntity<Void> removeMember(
            @PathVariable java.util.UUID organizationId,
            @PathVariable java.util.UUID memberId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        organizationService.removeMember(organizationId, memberId, user.id());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/leaderboard/visibility")
    @Operation(summary = "Toggle leaderboard visibility for the current user")
    public ResponseEntity<Void> toggleLeaderboardVisibility(
            @RequestBody java.util.Map<String, Boolean> body,
            @AuthenticationPrincipal AuthenticatedUser user) {
        boolean visible = Boolean.TRUE.equals(body.get("visible"));
        organizationService.toggleLeaderboardVisibility(user.id(), visible);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{organizationId}/audit-log")
    @Operation(summary = "Get organization audit log (ORG_ADMIN only)")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLog(
            @PathVariable java.util.UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizationService.getAuditLog(organizationId, user.id(), page, size));
    }
}
