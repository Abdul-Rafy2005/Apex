package com.abdulrafy.backend.organization.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record JoinOrganizationRequest(
    @NotNull UUID organizationId
) {}
