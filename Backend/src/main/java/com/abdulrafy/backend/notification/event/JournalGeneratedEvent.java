package com.abdulrafy.backend.notification.event;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public record JournalGeneratedEvent(
    UUID userId,
    UUID journalEntryId,
    String entryDate
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
