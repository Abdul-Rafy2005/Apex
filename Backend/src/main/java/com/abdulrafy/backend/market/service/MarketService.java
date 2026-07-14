package com.abdulrafy.backend.market.service;

import com.abdulrafy.backend.common.exception.NotFoundException;
import com.abdulrafy.backend.market.dto.*;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.mapper.AssetMapper;
import com.abdulrafy.backend.market.mapper.MarketMapper;
import com.abdulrafy.backend.market.provider.MarketDataProvider;
import com.abdulrafy.backend.market.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);
    private static final Duration PRICE_CACHE_TTL = Duration.ofSeconds(30);
    private static final String PRICE_CACHE_PREFIX = "market:price:";
    private static final String CHANGE_CACHE_PREFIX = "market:change24h:";

    private final AssetRepository assetRepository;
    private final MarketDataProvider marketDataProvider;
    private final StringRedisTemplate redisTemplate;

    public MarketService(AssetRepository assetRepository,
                         MarketDataProvider marketDataProvider,
                         StringRedisTemplate redisTemplate) {
        this.assetRepository = assetRepository;
        this.marketDataProvider = marketDataProvider;
        this.redisTemplate = redisTemplate;
    }

    public List<AssetResponse> listAssets() {
        return assetRepository.findByTradableTrue().stream()
                .map(AssetMapper::toResponse)
                .toList();
    }

    public List<LivePriceResponse> fetchLivePrices(List<String> symbols) {
        List<Asset> assets = assetRepository.findBySymbolIn(symbols);
        if (assets.isEmpty()) {
            return List.of();
        }

        // Map symbol -> providerId
        Map<String, String> symbolToProviderId = assets.stream()
                .collect(Collectors.toMap(Asset::getSymbol, Asset::getProviderSource));

        List<String> providerIds = new ArrayList<>(symbolToProviderId.values());
        Map<String, LivePriceResponse> providerPrices = marketDataProvider.fetchPrices(providerIds);

        // Try cache first, fallback to provider, store in cache
        List<LivePriceResponse> result = new ArrayList<>();
        for (Asset asset : assets) {
            String cachedPrice = redisTemplate.opsForValue().get(PRICE_CACHE_PREFIX + asset.getSymbol());
            String cachedChange = redisTemplate.opsForValue().get(CHANGE_CACHE_PREFIX + asset.getSymbol());

            LivePriceResponse providerPrice = providerPrices.get(asset.getProviderSource());
            if (providerPrice != null) {
                // Got fresh price from provider — cache it
                redisTemplate.opsForValue().set(
                        PRICE_CACHE_PREFIX + asset.getSymbol(),
                        providerPrice.priceUsd().toPlainString(),
                        PRICE_CACHE_TTL
                );
                redisTemplate.opsForValue().set(
                        CHANGE_CACHE_PREFIX + asset.getSymbol(),
                        providerPrice.change24hPct().toPlainString(),
                        PRICE_CACHE_TTL
                );
                result.add(new LivePriceResponse(
                        asset.getSymbol(),
                        providerPrice.priceUsd(),
                        providerPrice.change24hPct(),
                        providerPrice.timestamp()
                ));
            } else if (cachedPrice != null && cachedChange != null) {
                // Provider failed — serve from cache (fallback)
                log.warn("Serving cached price for {} (provider unavailable)", asset.getSymbol());
                result.add(new LivePriceResponse(
                        asset.getSymbol(),
                        new BigDecimal(cachedPrice),
                        new BigDecimal(cachedChange),
                        Instant.now()
                ));
            } else {
                log.warn("No price data available for {}", asset.getSymbol());
            }
        }
        return result;
    }

    public MarketOverviewResponse getMarketOverview() {
        List<Asset> assets = assetRepository.findByTradableTrue();
        if (assets.isEmpty()) {
            return new MarketOverviewResponse(List.of(), List.of(), List.of(), 0);
        }

        List<String> providerIds = assets.stream()
                .map(Asset::getProviderSource)
                .toList();

        Map<String, LivePriceResponse> prices = marketDataProvider.fetchPrices(providerIds);

        // Map providerId back to symbol
        Map<String, String> providerIdToSymbol = assets.stream()
                .collect(Collectors.toMap(Asset::getProviderSource, Asset::getSymbol));

        List<LivePriceResponse> allPrices = prices.entrySet().stream()
                .map(entry -> {
                    String symbol = providerIdToSymbol.getOrDefault(entry.getKey(), entry.getKey());
                    LivePriceResponse p = entry.getValue();
                    return new LivePriceResponse(symbol, p.priceUsd(), p.change24hPct(), p.timestamp());
                })
                .sorted(Comparator.comparing(LivePriceResponse::change24hPct).reversed())
                .toList();

        List<LivePriceResponse> topGainers = allPrices.stream()
                .filter(p -> p.change24hPct().compareTo(BigDecimal.ZERO) > 0)
                .limit(5)
                .toList();

        List<LivePriceResponse> topLosers = allPrices.stream()
                .filter(p -> p.change24hPct().compareTo(BigDecimal.ZERO) < 0)
                .sorted(Comparator.comparing(LivePriceResponse::change24hPct))
                .limit(5)
                .toList();

        List<LivePriceResponse> trending = allPrices.stream()
                .limit(5)
                .toList();

        return new MarketOverviewResponse(topGainers, topLosers, trending, assets.size());
    }

    public List<HistoricalPriceResponse> getHistory(String symbol, int days) {
        Asset asset = assetRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NotFoundException("Asset not found: " + symbol));

        CoinGeckoMarketChartResponse chart = marketDataProvider.fetchHistory(asset.getProviderSource(), days);
        return MarketMapper.toHistoricalPrices(chart.prices(), chart.volumes());
    }
}
