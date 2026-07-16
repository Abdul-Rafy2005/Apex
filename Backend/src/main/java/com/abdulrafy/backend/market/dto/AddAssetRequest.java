package com.abdulrafy.backend.market.dto;

import jakarta.validation.constraints.NotBlank;

public record AddAssetRequest(
    @NotBlank String symbol,
    @NotBlank String name,
    @NotBlank String providerSource
) {}
