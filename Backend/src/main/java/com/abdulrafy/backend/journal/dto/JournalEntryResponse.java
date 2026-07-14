package com.abdulrafy.backend.journal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JournalEntryResponse(
        UUID id,
        LocalDate entryDate,
        String narrativeText,
        Instant generatedAt
) {}
