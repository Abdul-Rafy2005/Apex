package com.abdulrafy.backend.market.service;

import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceBroadcastScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcastScheduler.class);

    private final MarketService marketService;
    private final PriceBroadcaster priceBroadcaster;
    private final AssetRepository assetRepository;

    public PriceBroadcastScheduler(MarketService marketService,
                                   PriceBroadcaster priceBroadcaster,
                                   AssetRepository assetRepository) {
        this.marketService = marketService;
        this.priceBroadcaster = priceBroadcaster;
        this.assetRepository = assetRepository;
    }

    @Scheduled(fixedDelayString = "${apex.market.ingestion.interval-ms:60000}",
               initialDelayString = "${apex.market.ingestion.interval-ms:60000}")
    public void broadcastPrices() {
        try {
            List<Asset> assets = assetRepository.findByTradableTrue();
            List<String> symbols = assets.stream().map(Asset::getSymbol).toList();
            if (symbols.isEmpty()) {
                return;
            }

            List<LivePriceResponse> prices = marketService.fetchLivePrices(symbols);
            for (LivePriceResponse price : prices) {
                priceBroadcaster.broadcastPrice(price);
            }
            log.debug("Broadcast {} price updates", prices.size());
        } catch (Exception e) {
            log.error("Failed to broadcast prices", e);
        }
    }
}
