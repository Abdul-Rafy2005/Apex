package com.abdulrafy.backend.market.controller;

import com.abdulrafy.backend.market.dto.*;
import com.abdulrafy.backend.market.service.MarketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market")
@Tag(name = "Market", description = "Live prices, historical charts, and market overview")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/assets")
    @Operation(summary = "List all tradable assets")
    public List<AssetResponse> listAssets() {
        return marketService.listAssets();
    }

    @GetMapping("/prices")
    @Operation(summary = "Fetch live prices for given symbols")
    public List<LivePriceResponse> getPrices(@RequestParam List<String> symbols) {
        return marketService.fetchLivePrices(symbols);
    }

    @GetMapping("/overview")
    @Operation(summary = "Market overview: top gainers, losers, and trending")
    public MarketOverviewResponse getOverview() {
        return marketService.getMarketOverview();
    }

    @GetMapping("/{symbol}/history")
    @Operation(summary = "Historical OHLCV data for a given asset")
    public List<HistoricalPriceResponse> getHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int days) {
        return marketService.getHistory(symbol, days);
    }
}
