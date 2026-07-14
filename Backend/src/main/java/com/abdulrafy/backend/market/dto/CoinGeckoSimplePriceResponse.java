package com.abdulrafy.backend.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CoinGeckoSimplePriceResponse(
    @JsonProperty("usd") BigDecimal usd,
    @JsonProperty("usd_24h_change") BigDecimal usd24hChange
) {}
