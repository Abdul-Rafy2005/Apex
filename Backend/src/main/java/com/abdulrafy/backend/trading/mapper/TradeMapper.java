package com.abdulrafy.backend.trading.mapper;

import com.abdulrafy.backend.trading.dto.TradeResponse;
import com.abdulrafy.backend.trading.entity.Trade;

public final class TradeMapper {

    private TradeMapper() {}

    public static TradeResponse toResponse(Trade trade) {
        return new TradeResponse(
            trade.getId(),
            trade.getPortfolioId(),
            trade.getAssetId(),
            trade.getSide(),
            trade.getQuantity(),
            trade.getPrice(),
            trade.getFee(),
            trade.getIdempotencyKey(),
            trade.getExecutedAt()
        );
    }
}
