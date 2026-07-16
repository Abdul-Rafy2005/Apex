package com.abdulrafy.backend.leaderboard.controller;

import com.abdulrafy.backend.auth.entity.UserRole;
import com.abdulrafy.backend.common.exception.ForbiddenException;
import com.abdulrafy.backend.common.security.AuthenticatedUser;
import com.abdulrafy.backend.leaderboard.dto.LeaderboardEntry;
import com.abdulrafy.backend.leaderboard.service.LeaderboardService;
import com.abdulrafy.backend.organization.entity.Membership;
import com.abdulrafy.backend.organization.repository.MembershipRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{orgId}/leaderboard")
@RequiredArgsConstructor
@Tag(name = "Leaderboard", description = "Organization leaderboard management")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final MembershipRepository membershipRepository;

    @GetMapping
    @Operation(summary = "Get organization leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "20") int top) {

        // Must be a member of this org
        Membership membership = membershipRepository
                .findByUserIdAndOrganizationId(user.id(), orgId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"));

        // Only ORG_ADMIN, INSTRUCTOR, and TRADER who are members can view
        // (TRADER can view their own org's leaderboard)
        if (membership.getRole() != UserRole.ORG_ADMIN &&
            membership.getRole() != UserRole.INSTRUCTOR &&
            membership.getRole() != UserRole.TRADER) {
            throw new ForbiddenException("Insufficient permissions to view leaderboard");
        }

        return ResponseEntity.ok(leaderboardService.getLeaderboard(orgId, top));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's rank on the leaderboard")
    public ResponseEntity<LeaderboardEntry> getMyRank(
            @PathVariable UUID orgId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        membershipRepository.findByUserIdAndOrganizationId(user.id(), orgId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this organization"));

        return leaderboardService.getUserRank(orgId, user.id())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
