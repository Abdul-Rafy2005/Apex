package com.abdulrafy.backend.trading.service;

import com.abdulrafy.backend.auth.entity.Portfolio;
import com.abdulrafy.backend.auth.repository.PortfolioRepository;
import com.abdulrafy.backend.common.exception.*;
import com.abdulrafy.backend.market.entity.Asset;
import com.abdulrafy.backend.market.repository.AssetRepository;
import com.abdulrafy.backend.market.dto.LivePriceResponse;
import com.abdulrafy.backend.trading.dto.*;
import com.abdulrafy.backend.trading.entity.*;
import com.abdulrafy.backend.trading.event.TradeEventPublisher;
import com.abdulrafy.backend.trading.event.TradeExecutedEvent;
import com.abdulrafy.backend.trading.mapper.TradeMapper;
import com.abdulrafy.backend.trading.repository.HoldingRepository;
import com.abdulrafy.backend.trading.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);
    private static final BigDecimal FEE_RATE = new BigDecimal("0.001"); // 0.1% fee

    private final PortfolioRepository portfolioRepository;
    private final TradeRepository tradeRepository;
    private final HoldingRepository holdingRepository;
    private final AssetRepository assetRepository;
    private final com.abdulrafy.backend.market.service.MarketService marketService;
    private final TradeEventPublisher tradeEventPublisher;

    public TradingService(PortfolioRepository portfolioRepository,
                          TradeRepository tradeRepository,
                          HoldingRepository holdingRepository,
                          AssetRepository assetRepository,
                          com.abdulrafy.backend.market.service.MarketService marketService,
                          TradeEventPublisher tradeEventPublisher) {
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
        this.holdingRepository = holdingRepository;
        this.assetRepository = assetRepository;
        this.marketService = marketService;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    public UUID resolvePortfolioId(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .map(Portfolio::getId)
                .orElseThrow(() -> new NotFoundException("Portfolio not found for user: " + userId));
    }

    @Transactional
    public TradeResponse executeTrade(UUID portfolioId, ExecuteTradeRequest request) {
        // 1. Idempotency check
        Optional<Trade> existing = tradeRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent replay for key={}", request.idempotencyKey());
            return TradeMapper.toResponse(existing.get());
        }

        // 2. Validate asset
        Asset asset = assetRepository.findById(request.assetId())
                .orElseThrow(() -> new InvalidAssetException("Asset not found: " + request.assetId()));
        if (!asset.getTradable()) {
            throw new InvalidAssetException("Asset is not tradable: " + asset.getSymbol());
        }

        // 3. Get current price
        List<LivePriceResponse> prices = marketService.fetchLivePrices(List.of(asset.getSymbol()));
        BigDecimal currentPrice = prices.stream()
                .filter(p -> p.symbol().equals(asset.getSymbol()))
                .map(LivePriceResponse::priceUsd)
                .findFirst()
                .orElseThrow(() -> new InvalidAssetException("No price available for: " + asset.getSymbol()));

        // 4. Calculate fee and totals
        BigDecimal quantity = request.quantity().setScale(8, RoundingMode.HALF_UP);
        BigDecimal tradeValue = quantity.multiply(currentPrice);
        BigDecimal fee = tradeValue.multiply(FEE_RATE).setScale(4, RoundingMode.HALF_UP);

        // 5. Load portfolio with optimistic lock
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new NotFoundException("Portfolio not found: " + portfolioId));

        // 6. Validate
        if (request.side() == OrderSide.BUY) {
            BigDecimal totalCost = tradeValue.add(fee);
            if (portfolio.getCashBalance().compareTo(totalCost) < 0) {
                throw new InsufficientFundsException(
                    "Insufficient funds: required " + totalCost + ", available " + portfolio.getCashBalance());
            }
        } else {
            Holding holding = holdingRepository.findByPortfolioIdAndAssetId(portfolioId, request.assetId())
                    .orElseThrow(() -> new InsufficientHoldingsException("No holdings for this asset"));
            if (holding.getQuantity().compareTo(quantity) < 0) {
                throw new InsufficientHoldingsException(
                    "Insufficient holdings: required " + quantity + ", available " + holding.getQuantity());
            }
        }

        // 7. Execute
        Trade trade = Trade.builder()
                .portfolioId(portfolioId)
                .assetId(request.assetId())
                .side(request.side())
                .quantity(quantity)
                .price(currentPrice)
                .fee(fee)
                .idempotencyKey(request.idempotencyKey())
                .executedAt(Instant.now())
                .build();

        try {
            if (request.side() == OrderSide.BUY) {
                portfolio.setCashBalance(portfolio.getCashBalance().subtract(tradeValue).subtract(fee));
            } else {
                portfolio.setCashBalance(portfolio.getCashBalance().add(tradeValue).subtract(fee));
            }
            portfolioRepository.save(portfolio);
            portfolioRepository.flush();

            updateHoldings(portfolioId, request.assetId(), request.side(), quantity, currentPrice);

            Trade savedTrade = tradeRepository.save(trade);

            tradeEventPublisher.publish(new TradeExecutedEvent(
                    savedTrade.getId(),
                    portfolioId,
                    request.assetId(),
                    request.side().name(),
                    quantity,
                    currentPrice,
                    fee,
                    savedTrade.getExecutedAt()
            ));

            return TradeMapper.toResponse(savedTrade);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock conflict on portfolio {} for key={}", portfolioId, request.idempotencyKey());
            throw new ConflictException("Trade conflict: portfolio was modified concurrently. Please retry.");
        }
    }

    private void updateHoldings(UUID portfolioId, UUID assetId, OrderSide side,
                                BigDecimal quantity, BigDecimal price) {
        Optional<Holding> existingHolding = holdingRepository.findByPortfolioIdAndAssetId(portfolioId, assetId);

        if (side == OrderSide.BUY) {
            if (existingHolding.isPresent()) {
                Holding h = existingHolding.get();
                BigDecimal newQty = h.getQuantity().add(quantity);
                BigDecimal newAvgPrice = h.getQuantity().multiply(h.getAvgEntryPrice())
                        .add(quantity.multiply(price))
                        .divide(newQty, 4, RoundingMode.HALF_UP);
                h.setQuantity(newQty);
                h.setAvgEntryPrice(newAvgPrice);
                holdingRepository.save(h);
            } else {
                Holding h = Holding.builder()
                        .portfolioId(portfolioId)
                        .assetId(assetId)
                        .quantity(quantity)
                        .avgEntryPrice(price)
                        .build();
                holdingRepository.save(h);
            }
        } else {
            Holding h = existingHolding.get();
            h.setQuantity(h.getQuantity().subtract(quantity));
            if (h.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                holdingRepository.delete(h);
            } else {
                holdingRepository.save(h);
            }
        }
    }

    public PortfolioResponse getPortfolio(UUID portfolioId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new NotFoundException("Portfolio not found"));

        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        List<String> symbols = holdings.stream()
                .map(h -> assetRepository.findById(h.getAssetId()).map(Asset::getSymbol).orElse(""))
                .toList();

        List<LivePriceResponse> prices = marketService.fetchLivePrices(symbols);

        List<HoldingResponse> holdingResponses = holdings.stream().map(h -> {
            Asset asset = assetRepository.findById(h.getAssetId()).orElse(null);
            BigDecimal currentPrice = prices.stream()
                    .filter(p -> p.symbol() != null && asset != null && p.symbol().equals(asset.getSymbol()))
                    .map(LivePriceResponse::priceUsd)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            BigDecimal unrealizedPnl = currentPrice.subtract(h.getAvgEntryPrice())
                    .multiply(h.getQuantity());

            return new HoldingResponse(
                    h.getAssetId(),
                    asset != null ? asset.getSymbol() : "",
                    asset != null ? asset.getName() : "",
                    h.getQuantity(),
                    h.getAvgEntryPrice(),
                    currentPrice,
                    unrealizedPnl
            );
        }).toList();

        BigDecimal totalUnrealizedPnl = holdingResponses.stream()
                .map(HoldingResponse::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getCashBalance(),
                holdingResponses,
                totalUnrealizedPnl
        );
    }

    public Page<TradeResponse> getTradeHistory(UUID portfolioId, Pageable pageable) {
        return tradeRepository.findByPortfolioIdOrderByExecutedAtDesc(portfolioId, pageable)
                .map(TradeMapper::toResponse);
    }
}
