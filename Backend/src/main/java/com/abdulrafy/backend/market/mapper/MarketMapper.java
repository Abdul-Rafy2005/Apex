package com.abdulrafy.backend.market.mapper;

import com.abdulrafy.backend.market.dto.HistoricalPriceResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MarketMapper {

    private MarketMapper() {}

    /**
     * Convert CoinGecko market_chart response data to HistoricalPriceResponse list.
     * CoinGecko returns: [[timestamp_ms, price], [timestamp_ms, price], ...]
     * For OHLCV approximation, we treat the price as the close price.
     */
    public static List<HistoricalPriceResponse> toHistoricalPrices(
            List<List<Double>> prices,
            List<List<Double>> volumes) {

        List<HistoricalPriceResponse> result = new ArrayList<>();
        if (prices == null || prices.isEmpty()) {
            return result;
        }

        for (int i = 0; i < prices.size(); i++) {
            List<Double> point = prices.get(i);
            if (point == null || point.size() < 2) continue;

            Instant time = Instant.ofEpochMilli(point.get(0).longValue());
            BigDecimal close = BigDecimal.valueOf(point.get(1));

            // Approximate OHLCV: open=previous close (or current if first), high/low close
            BigDecimal open = (i > 0) ? BigDecimal.valueOf(prices.get(i - 1).get(1)) : close;
            BigDecimal high = close.max(open);
            BigDecimal low = close.min(open);

            BigDecimal volume = BigDecimal.ZERO;
            if (volumes != null && i < volumes.size() && volumes.get(i) != null && volumes.get(i).size() >= 2) {
                volume = BigDecimal.valueOf(volumes.get(i).get(1));
            }

            result.add(new HistoricalPriceResponse(time, open, high, low, close, volume));
        }
        return result;
    }
}
