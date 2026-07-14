package com.abdulrafy.backend.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinGeckoMarketChartResponse(
    List<List<Double>> prices,
    List<List<Double>> volumes
) {}
