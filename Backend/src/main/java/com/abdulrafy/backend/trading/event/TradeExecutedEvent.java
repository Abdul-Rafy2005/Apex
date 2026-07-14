package com.abdulrafy.backend.trading.event;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeExecutedEvent(
    UUID tradeId,
    UUID portfolioId,
    UUID assetId,
    String side,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal fee,
    Instant executedAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
