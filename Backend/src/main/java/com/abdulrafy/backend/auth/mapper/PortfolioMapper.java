package com.abdulrafy.backend.auth.mapper;

import com.abdulrafy.backend.auth.dto.PortfolioSummaryResponse;
import com.abdulrafy.backend.auth.entity.Portfolio;

public final class PortfolioMapper {

    private PortfolioMapper() {}

    public static PortfolioSummaryResponse toSummaryResponse(Portfolio portfolio) {
        return new PortfolioSummaryResponse(
            portfolio.getId(),
            portfolio.getCashBalance()
        );
    }
}
