package com.abdulrafy.backend.auth.repository;

import com.abdulrafy.backend.auth.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    Optional<Portfolio> findByUserId(UUID userId);

    @Query("SELECT p.user.id FROM Portfolio p WHERE p.id = :portfolioId")
    Optional<UUID> findUserIdByPortfolioId(@Param("portfolioId") UUID portfolioId);
}
