package com.abdulrafy.backend.organization.dto;

import com.abdulrafy.backend.auth.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMembershipRoleRequest(
    @NotNull UserRole role
) {}
