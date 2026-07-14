package com.abdulrafy.backend.analytics.repository;

import com.abdulrafy.backend.analytics.entity.PerformanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PerformanceSnapshotRepository extends JpaRepository<PerformanceSnapshot, UUID> {

    Optional<PerformanceSnapshot> findTopByPortfolioIdOrderBySnapshotDateDesc(UUID portfolioId);

    List<PerformanceSnapshot> findByPortfolioIdOrderBySnapshotDateAsc(UUID portfolioId);

    Optional<PerformanceSnapshot> findByPortfolioIdAndSnapshotDate(UUID portfolioId, LocalDate date);

    List<PerformanceSnapshot> findBySnapshotDate(LocalDate date);

    @Query("SELECT ps FROM PerformanceSnapshot ps WHERE ps.portfolioId = :portfolioId " +
           "AND ps.snapshotDate BETWEEN :from AND :to ORDER BY ps.snapshotDate ASC")
    List<PerformanceSnapshot> findByPortfolioIdAndDateRange(
            @Param("portfolioId") UUID portfolioId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
