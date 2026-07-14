package com.abdulrafy.backend.organization.controller;

import com.abdulrafy.backend.common.security.AuthenticatedUser;
import com.abdulrafy.backend.organization.dto.*;
import com.abdulrafy.backend.organization.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/{organizationId}/members")
    @Operation(summary = "List organization members (ORG_ADMIN/INSTRUCTOR only)")
    public ResponseEntity<List<MembershipResponse>> listMembers(
            @PathVariable java.util.UUID organizationId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(organizationService.listMembers(organizationId, user.id()));
    }
}
