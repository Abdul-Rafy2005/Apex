package com.abdulrafy.backend.trading.dto;

import com.abdulrafy.backend.trading.entity.OrderSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ExecuteTradeRequest(
    @NotNull UUID assetId,
    @NotNull OrderSide side,
    @NotNull @DecimalMin("0.00000001") BigDecimal quantity,
    @NotBlank String idempotencyKey
) {}
