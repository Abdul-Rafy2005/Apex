package com.abdulrafy.backend.trading.repository;

import com.abdulrafy.backend.trading.entity.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    Optional<Trade> findByIdempotencyKey(String idempotencyKey);

    Page<Trade> findByPortfolioIdOrderByExecutedAtDesc(UUID portfolioId, Pageable pageable);

    List<Trade> findByPortfolioIdOrderByExecutedAtAsc(UUID portfolioId);
}
