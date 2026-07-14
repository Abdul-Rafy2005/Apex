package com.abdulrafy.backend.journal.repository;

import com.abdulrafy.backend.journal.entity.TradeJournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TradeJournalEntryRepository extends JpaRepository<TradeJournalEntry, UUID> {

    Optional<TradeJournalEntry> findByUserIdAndEntryDate(UUID userId, LocalDate entryDate);

    Page<TradeJournalEntry> findByUserIdOrderByEntryDateDesc(UUID userId, Pageable pageable);

    @Query("SELECT COUNT(j) FROM TradeJournalEntry j WHERE j.userId = :userId AND j.generatedAt >= :since")
    long countByUserIdAndGeneratedAtAfter(
            @Param("userId") UUID userId,
            @Param("since") java.time.Instant since);
}
