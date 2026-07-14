package com.abdulrafy.backend.market.mapper;

import com.abdulrafy.backend.market.dto.HistoricalPriceResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketMapperTest {

    @Test
    void toHistoricalPrices_emptyInput_returnsEmpty() {
        assertThat(MarketMapper.toHistoricalPrices(List.of(), List.of())).isEmpty();
        assertThat(MarketMapper.toHistoricalPrices(null, null)).isEmpty();
    }

    @Test
    void toHistoricalPrices_singlePricePoint() {
        List<List<Double>> prices = List.of(
            List.of(1700000000000.0, 42000.50)
        );
        List<List<Double>> volumes = List.of(
            List.of(1700000000000.0, 123456789.0)
        );

        List<HistoricalPriceResponse> result = MarketMapper.toHistoricalPrices(prices, volumes);

        assertThat(result).hasSize(1);
        HistoricalPriceResponse h = result.get(0);
        assertThat(h.open()).isEqualByComparingTo(new BigDecimal("42000.50"));
        assertThat(h.close()).isEqualByComparingTo(new BigDecimal("42000.50"));
        assertThat(h.high()).isEqualByComparingTo(new BigDecimal("42000.50"));
        assertThat(h.low()).isEqualByComparingTo(new BigDecimal("42000.50"));
        assertThat(h.volume()).isEqualByComparingTo(new BigDecimal("123456789"));
    }

    @Test
    void toHistoricalPrices_multiplePoints_usesPreviousAsOpen() {
        List<List<Double>> prices = List.of(
            List.of(1700000000000.0, 42000.0),
            List.of(1700000060000.0, 42500.0),
            List.of(1700000120000.0, 41800.0)
        );

        List<HistoricalPriceResponse> result = MarketMapper.toHistoricalPrices(prices, List.of());

        assertThat(result).hasSize(3);
        // First point: open = close (no previous)
        assertThat(result.get(0).open()).isEqualByComparingTo(new BigDecimal("42000"));
        assertThat(result.get(0).close()).isEqualByComparingTo(new BigDecimal("42000"));
        // Second point: open = 42000, close = 42500
        assertThat(result.get(1).open()).isEqualByComparingTo(new BigDecimal("42000"));
        assertThat(result.get(1).close()).isEqualByComparingTo(new BigDecimal("42500"));
        assertThat(result.get(1).high()).isEqualByComparingTo(new BigDecimal("42500"));
        assertThat(result.get(1).low()).isEqualByComparingTo(new BigDecimal("42000"));
        // Third point: open = 42500, close = 41800
        assertThat(result.get(2).open()).isEqualByComparingTo(new BigDecimal("42500"));
        assertThat(result.get(2).close()).isEqualByComparingTo(new BigDecimal("41800"));
        assertThat(result.get(2).high()).isEqualByComparingTo(new BigDecimal("42500"));
        assertThat(result.get(2).low()).isEqualByComparingTo(new BigDecimal("41800"));
    }

    @Test
    void toHistoricalPrices_missingVolumes_defaultsToZero() {
        List<List<Double>> prices = List.of(
            List.of(1700000000000.0, 42000.0)
        );

        List<HistoricalPriceResponse> result = MarketMapper.toHistoricalPrices(prices, List.of());

        assertThat(result.get(0).volume()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
