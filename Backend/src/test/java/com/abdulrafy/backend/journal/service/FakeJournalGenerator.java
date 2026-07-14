package com.abdulrafy.backend.journal.service;

import java.math.BigDecimal;
import java.util.Map;

public class FakeJournalGenerator implements AiJournalGenerator {

    private JournalMetrics lastMetrics = null;
    private String narrativeToReturn;

    public FakeJournalGenerator() {
        this.narrativeToReturn = "Test narrative: You executed 5 trades today with a 60% win rate. Your best performer was BTC with a 12.3% return. Keep monitoring your risk exposure.";
    }

    public FakeJournalGenerator(String narrativeToReturn) {
        this.narrativeToReturn = narrativeToReturn;
    }

    @Override
    public String generateNarrative(JournalMetrics metrics) {
        this.lastMetrics = metrics;
        return narrativeToReturn;
    }

    public JournalMetrics getLastMetrics() {
        return lastMetrics;
    }
}
