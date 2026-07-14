package com.abdulrafy.backend.journal.controller;

import com.abdulrafy.backend.common.security.AuthenticatedUser;
import com.abdulrafy.backend.journal.dto.JournalEntryResponse;
import com.abdulrafy.backend.journal.entity.TradeJournalEntry;
import com.abdulrafy.backend.journal.service.JournalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/journal")
@RequiredArgsConstructor
@Tag(name = "Trade Journal", description = "AI-generated trade journal entries")
public class JournalController {

    private final JournalService journalService;

    @PostMapping("/generate")
    @Operation(summary = "Generate a journal entry for the current day")
    public ResponseEntity<JournalEntryResponse> generateJournal(
            @AuthenticationPrincipal AuthenticatedUser user) {
        TradeJournalEntry entry = journalService.generateJournal(user.id());
        return ResponseEntity.ok(toResponse(entry));
    }

    @GetMapping
    @Operation(summary = "Get paginated journal history")
    public ResponseEntity<Page<JournalEntryResponse>> getJournalEntries(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TradeJournalEntry> entries = journalService.getJournalEntries(user.id(), PageRequest.of(page, size));
        return ResponseEntity.ok(entries.map(this::toResponse));
    }

    private JournalEntryResponse toResponse(TradeJournalEntry entry) {
        return new JournalEntryResponse(
                entry.getId(),
                entry.getEntryDate(),
                entry.getNarrativeText(),
                entry.getGeneratedAt()
        );
    }
}
