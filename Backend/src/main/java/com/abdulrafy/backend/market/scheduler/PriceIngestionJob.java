package com.abdulrafy.backend.market.scheduler;

import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.provider.MarketDataProvider;
import com.abdulrafy.backend.market.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class PriceIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(PriceIngestionJob.class);
    private static final Duration PRICE_CACHE_TTL = Duration.ofSeconds(60);
    private static final String PRICE_CACHE_PREFIX = "market:price:";
    private static final String CHANGE_CACHE_PREFIX = "market:change24h:";

    private final AssetRepository assetRepository;
    private final MarketDataProvider marketDataProvider;
    private final StringRedisTemplate redisTemplate;

    public PriceIngestionJob(AssetRepository assetRepository,
                             MarketDataProvider marketDataProvider,
                             StringRedisTemplate redisTemplate) {
        this.assetRepository = assetRepository;
        this.marketDataProvider = marketDataProvider;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedDelayString = "${apex.market.ingestion.interval-ms:60000}")
    public void refreshPrices() {
        List<Asset> assets = assetRepository.findByTradableTrue();
        if (assets.isEmpty()) {
            return;
        }

        List<String> providerIds = assets.stream()
                .map(Asset::getProviderSource)
                .toList();

        log.info("Refreshing prices for {} assets", assets.size());
        Map<String, LivePriceResponse> prices = marketDataProvider.fetchPrices(providerIds);

        for (Asset asset : assets) {
            LivePriceResponse price = prices.get(asset.getProviderSource());
            if (price != null) {
                redisTemplate.opsForValue().set(
                        PRICE_CACHE_PREFIX + asset.getSymbol(),
                        price.priceUsd().toPlainString(),
                        PRICE_CACHE_TTL
                );
                redisTemplate.opsForValue().set(
                        CHANGE_CACHE_PREFIX + asset.getSymbol(),
                        price.change24hPct().toPlainString(),
                        PRICE_CACHE_TTL
                );
            }
        }
        log.info("Price refresh complete: {}/{} assets updated", prices.size(), assets.size());
    }
}
