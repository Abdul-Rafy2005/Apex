package com.abdulrafy.backend.trading.controller;

import com.abdulrafy.backend.common.security.AuthenticatedUser;
import com.abdulrafy.backend.trading.dto.*;
import com.abdulrafy.backend.trading.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trading")
@Tag(name = "Trading", description = "Trade execution, portfolio, and trade history")
public class TradingController {

    private final TradingService tradingService;

    public TradingController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute a market buy or sell trade")
    public ResponseEntity<TradeResponse> executeTrade(
            @Valid @RequestBody ExecuteTradeRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        UUID portfolioId = tradingService.resolvePortfolioId(user.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tradingService.executeTrade(portfolioId, request));
    }

    @GetMapping("/portfolio")
    @Operation(summary = "Get current portfolio with holdings and unrealized P/L")
    public PortfolioResponse getPortfolio(@AuthenticationPrincipal AuthenticatedUser user) {
        UUID portfolioId = tradingService.resolvePortfolioId(user.id());
        return tradingService.getPortfolio(portfolioId, user.id());
    }

    @GetMapping("/trades")
    @Operation(summary = "Get paginated trade history")
    public Page<TradeResponse> getTrades(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        UUID portfolioId = tradingService.resolvePortfolioId(user.id());
        return tradingService.getTradeHistory(portfolioId, PageRequest.of(page, size));
    }
}
