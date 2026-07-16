package com.abdulrafy.backend.market.provider;

import com.abdulrafy.backend.market.dto.CoinGeckoMarketChartResponse;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import java.util.List;
import java.util.Map;

public interface MarketDataProvider {

    /**
     * Fetch current prices for the given CoinGecko IDs.
     * Returns map of providerId -> LivePriceResponse.
     */
    Map<String, LivePriceResponse> fetchPrices(List<String> providerIds);

    /**
     * Fetch historical OHLCV chart data for a single CoinGecko ID.
     * @param providerId CoinGecko ID (e.g. "bitcoin")
     * @param days number of days of history
     */
    CoinGeckoMarketChartResponse fetchHistory(String providerId, int days);

    List<com.abdulrafy.backend.market.dto.GlobalAssetResponse> searchGlobalAssets(String query);
}
