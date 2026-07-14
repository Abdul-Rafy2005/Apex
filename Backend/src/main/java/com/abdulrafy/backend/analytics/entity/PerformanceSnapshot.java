package com.abdulrafy.backend.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "performance_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "portfolio_id", nullable = false)
    private UUID portfolioId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    // Return metrics
    @Column(name = "total_return_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal totalReturnPct;

    @Column(name = "daily_return_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal dailyReturnPct;

    // Trade metrics
    @Column(name = "total_trades", nullable = false)
    private Integer totalTrades;

    @Column(name = "winning_trades", nullable = false)
    private Integer winningTrades;

    @Column(name = "losing_trades", nullable = false)
    private Integer losingTrades;

    @Column(name = "win_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal winRate;

    @Column(name = "avg_win_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal avgWinPct;

    @Column(name = "avg_loss_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal avgLossPct;

    @Column(name = "largest_gain_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal largestGainPct;

    @Column(name = "largest_loss_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal largestLossPct;

    // Risk metrics
    @Column(name = "sharpe_ratio", nullable = false, precision = 10, scale = 4)
    private BigDecimal sharpeRatio;

    @Column(name = "max_drawdown_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal maxDrawdownPct;

    @Column(name = "avg_holding_period_hours", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgHoldingPeriodHours;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    // Portfolio snapshot values
    @Column(name = "portfolio_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal portfolioValue;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;

    @Column(name = "invested_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal investedValue;

    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 4)
    private BigDecimal realizedPnl;

    @Column(name = "unrealized_pnl", nullable = false, precision = 19, scale = 4)
    private BigDecimal unrealizedPnl;

    // Best/worst asset
    @Column(name = "best_asset_symbol", length = 20)
    private String bestAssetSymbol;

    @Column(name = "best_asset_return_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal bestAssetReturnPct;

    @Column(name = "worst_asset_symbol", length = 20)
    private String worstAssetSymbol;

    @Column(name = "worst_asset_return_pct", nullable = false, precision = 10, scale = 4)
    private BigDecimal worstAssetReturnPct;

    // JSON-serialized data
    @Column(name = "allocation_breakdown", nullable = false)
    private String allocationBreakdown;

    @Column(name = "daily_pnl_series", nullable = false)
    private String dailyPnlSeries;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
