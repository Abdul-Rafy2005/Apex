package com.abdulrafy.backend.market.dto;

import java.util.List;

public record MarketOverviewResponse(
    List<LivePriceResponse> topGainers,
    List<LivePriceResponse> topLosers,
    List<LivePriceResponse> trending,
    int totalAssets
) {}
