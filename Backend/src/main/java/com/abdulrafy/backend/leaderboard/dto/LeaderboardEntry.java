package com.abdulrafy.backend.leaderboard.dto;

import java.util.UUID;

public record LeaderboardEntry(
    int rank,
    UUID userId,
    String displayName,
    double score
) {}
