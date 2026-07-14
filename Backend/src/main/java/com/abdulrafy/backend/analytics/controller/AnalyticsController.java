package com.abdulrafy.backend.analytics.controller;

import com.abdulrafy.backend.analytics.dto.AnalyticsHistoryResponse;
import com.abdulrafy.backend.analytics.dto.AnalyticsSummaryResponse;
import com.abdulrafy.backend.analytics.service.AnalyticsService;
import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics", description = "Portfolio performance analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final PortfolioRepository portfolioRepository;

    public AnalyticsController(AnalyticsService analyticsService,
                               PortfolioRepository portfolioRepository) {
        this.analyticsService = analyticsService;
        this.portfolioRepository = portfolioRepository;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get latest analytics summary (cached, recomputed async on trade)")
    public ResponseEntity<AnalyticsSummaryResponse> getSummary(
            @AuthenticationPrincipal AuthenticatedUser user) {
        UUID portfolioId = resolvePortfolioId(user);
        return ResponseEntity.ok(analyticsService.getSummary(portfolioId));
    }

    @GetMapping("/history")
    @Operation(summary = "Get analytics history for charting (daily P/L, return over time)")
    public ResponseEntity<AnalyticsHistoryResponse> getHistory(
            @AuthenticationPrincipal AuthenticatedUser user) {
        UUID portfolioId = resolvePortfolioId(user);
        return ResponseEntity.ok(analyticsService.getHistory(portfolioId));
    }

    private UUID resolvePortfolioId(AuthenticatedUser user) {
        return portfolioRepository.findByUserId(user.id())
                .map(Portfolio::getId)
                .orElseThrow(() -> new com.abdulrafy.backend.common.exception.NotFoundException(
                        "Portfolio not found for user " + user.id()));
    }
}
