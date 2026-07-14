package com.abdulrafy.backend.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
    @NotBlank @Size(min = 1, max = 200) String name,
    String type
) {}
