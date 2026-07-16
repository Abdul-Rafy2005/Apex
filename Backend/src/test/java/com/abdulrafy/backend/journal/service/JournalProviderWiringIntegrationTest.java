package com.abdulrafy.backend.journal.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "apex.journal.provider=gemini",
        "gemini.api-key=test-key",
        "anthropic.api-key=test-key"
})
class JournalProviderWiringIntegrationTest {

    @Autowired
    private AiJournalGenerator journalGenerator;

    @Test
    void geminiProvider_injectsGeminiJournalGenerator() {
        assertThat(journalGenerator).isInstanceOf(GeminiJournalGenerator.class);
    }
}
