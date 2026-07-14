package com.abdulrafy.backend.trading.service;

import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.common.exception.*;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.market.service.MarketService;
import com.abdulrafy.backend.trading.dto.ExecuteTradeRequest;
import com.abdulrafy.backend.trading.dto.TradeResponse;
import com.abdulrafy.backend.trading.entity.OrderSide;
import com.abdulrafy.backend.trading.entity.Trade;
import com.abdulrafy.backend.trading.event.TradeEventPublisher;
import com.abdulrafy.backend.trading.repository.HoldingRepository;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingServiceTest {

    @Mock private PortfolioRepository portfolioRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private HoldingRepository holdingRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private MarketService marketService;
    @Mock private TradeEventPublisher tradeEventPublisher;

    private TradingService tradingService;

    private UUID portfolioId = UUID.randomUUID();
    private UUID assetId = UUID.randomUUID();
    private Asset btcAsset;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        tradingService = new TradingService(portfolioRepository, tradeRepository, holdingRepository,
                assetRepository, marketService, tradeEventPublisher);

        btcAsset = Asset.builder()
                .id(assetId)
                .symbol("BTC")
                .name("Bitcoin")
                .precision(8)
                .providerSource("bitcoin")
                .tradable(true)
                .build();

        portfolio = Portfolio.builder()
                .id(portfolioId)
                .cashBalance(new BigDecimal("100000"))
                .version(0)
                .build();
    }

    @Test
    void executeTrade_buy_sufficientFunds_executesSuccessfully() {
        when(tradeRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(btcAsset));
        when(marketService.fetchLivePrices(List.of("BTC"))).thenReturn(List.of(
                new LivePriceResponse("BTC", new BigDecimal("42000"), BigDecimal.ZERO, Instant.now())));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> {
            Trade t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        ExecuteTradeRequest request = new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("1"), "key-1");
        TradeResponse response = tradingService.executeTrade(portfolioId, request);

        assertThat(response).isNotNull();
        assertThat(response.side()).isEqualTo(OrderSide.BUY);
        assertThat(response.quantity()).isEqualByComparingTo(new BigDecimal("1.00000000"));
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("42000"));

        // Verify portfolio balance updated: 100000 - 42000 - fee(42) = 57958
        verify(portfolioRepository).save(argThat(p ->
                p.getCashBalance().compareTo(new BigDecimal("57958")) == 0));

        verify(tradeEventPublisher).publish(any());
    }

    @Test
    void executeTrade_buy_insufficientFunds_throws() {
        when(tradeRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(btcAsset));
        when(marketService.fetchLivePrices(List.of("BTC"))).thenReturn(List.of(
                new LivePriceResponse("BTC", new BigDecimal("42000"), BigDecimal.ZERO, Instant.now())));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));

        // Try to buy 3 BTC: 3 * 42000 = 126000 > 100000
        ExecuteTradeRequest request = new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("3"), "key-2");

        assertThatThrownBy(() -> tradingService.executeTrade(portfolioId, request))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void executeTrade_sell_insufficientHoldings_throws() {
        when(tradeRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(btcAsset));
        when(marketService.fetchLivePrices(List.of("BTC"))).thenReturn(List.of(
                new LivePriceResponse("BTC", new BigDecimal("42000"), BigDecimal.ZERO, Instant.now())));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId)).thenReturn(Optional.empty());

        ExecuteTradeRequest request = new ExecuteTradeRequest(assetId, OrderSide.SELL, new BigDecimal("1"), "key-3");

        assertThatThrownBy(() -> tradingService.executeTrade(portfolioId, request))
                .isInstanceOf(InsufficientHoldingsException.class)
                .hasMessageContaining("No holdings");
    }

    @Test
    void executeTrade_duplicateIdempotencyKey_returnsExistingTrade() {
        Trade existingTrade = Trade.builder()
                .id(UUID.randomUUID())
                .portfolioId(portfolioId)
                .assetId(assetId)
                .side(OrderSide.BUY)
                .quantity(new BigDecimal("1"))
                .price(new BigDecimal("42000"))
                .fee(new BigDecimal("42"))
                .idempotencyKey("key-dup")
                .executedAt(Instant.now())
                .build();
        when(tradeRepository.findByIdempotencyKey("key-dup")).thenReturn(Optional.of(existingTrade));

        ExecuteTradeRequest request = new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("1"), "key-dup");
        TradeResponse response = tradingService.executeTrade(portfolioId, request);

        assertThat(response.id()).isEqualTo(existingTrade.getId());
        // Verify no new trade was saved
        verify(tradeRepository, never()).save(any());
    }

    @Test
    void executeTrade_nonTradableAsset_throws() {
        Asset nonTradable = Asset.builder()
                .id(assetId)
                .symbol(" locked ")
                .tradable(false)
                .providerSource("locked")
                .build();
        when(tradeRepository.findByIdempotencyKey("key-4")).thenReturn(Optional.empty());
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(nonTradable));

        ExecuteTradeRequest request = new ExecuteTradeRequest(assetId, OrderSide.BUY, new BigDecimal("1"), "key-4");

        assertThatThrownBy(() -> tradingService.executeTrade(portfolioId, request))
                .isInstanceOf(InvalidAssetException.class)
                .hasMessageContaining("not tradable");
    }

    @Test
    void executeTrade_sell_updatesHoldingCorrectly() {
        com.abdulrafy.backend.trading.entity.Holding holding =
                com.abdulrafy.backend.trading.entity.Holding.builder()
                        .id(UUID.randomUUID())
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(new BigDecimal("2"))
                        .avgEntryPrice(new BigDecimal("40000"))
                        .build();

        when(tradeRepository.findByIdempotencyKey("key-5")).thenReturn(Optional.empty());
        when(assetRepository.findById(assetId)).thenReturn(Optional.of(btcAsset));
        when(marketService.fetchLivePrices(List.of("BTC"))).thenReturn(List.of(
                new LivePriceResponse("BTC", new BigDecimal("42000"), BigDecimal.ZERO, Instant.now())));
        when(portfolioRepository.findById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId)).thenReturn(Optional.of(holding));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(inv -> {
            Trade t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        ExecuteTradeRequest request = new ExecuteTradeRequest(assetId, OrderSide.SELL, new BigDecimal("1"), "key-5");
        TradeResponse response = tradingService.executeTrade(portfolioId, request);

        assertThat(response.side()).isEqualTo(OrderSide.SELL);

        // Proceeds: 1 * 42000 = 42000, fee = 42, net = 41958
        // Portfolio: 100000 + 41958 = 141958
        verify(portfolioRepository).save(argThat(p ->
                p.getCashBalance().compareTo(new BigDecimal("141958")) == 0));

        // Holding: 2 - 1 = 1 remaining
        verify(holdingRepository).save(argThat(h ->
                h.getQuantity().compareTo(new BigDecimal("1")) == 0));
    }
}
