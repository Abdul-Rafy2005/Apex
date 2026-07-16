package com.abdulrafy.backend.leaderboard.service;

import com.abdulrafy.backend.auth.entity.User;
import com.abdulrafy.backend.auth.repository.UserRepository;
import com.abdulrafy.backend.common.exception.NotFoundException;
import com.abdulrafy.backend.leaderboard.dto.LeaderboardEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final String LEADERBOARD_PREFIX = "leaderboard:org:";

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    /**
     * Update a user's score on their org leaderboard.
     * Called after PerformanceSnapshot creation.
     * Respects leaderboard-visibility opt-out: if the user has opted out,
     * their entry is removed from the sorted set.
     */
    @Transactional
    public void updateScore(UUID orgId, UUID userId, double score) {
        if (orgId == null) return;

        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getLeaderboardVisible())) {
            // User opted out — remove from leaderboard
            redisTemplate.opsForZSet().remove(leaderboardKey(orgId), userId.toString());
            return;
        }

        redisTemplate.opsForZSet().add(leaderboardKey(orgId), userId.toString(), score);
        log.info("Leaderboard updated: orgId={}, userId={}, score={}", orgId, userId, score);
    }

    /**
     * Get the leaderboard for an organization, top N entries.
     */
    public List<LeaderboardEntry> getLeaderboard(UUID orgId, int top) {
        Set<ZSetOperations.TypedTuple<String>> entries =
                redisTemplate.opsForZSet().reverseRangeWithScores(
                        leaderboardKey(orgId), 0, top - 1);

        if (entries == null) return List.of();

        int rank = 1;
        List<LeaderboardEntry> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> entry : entries) {
            UUID memberId = UUID.fromString(Objects.requireNonNull(entry.getValue()));
            double score = Objects.requireNonNull(entry.getScore());

            User user = userRepository.findById(memberId).orElse(null);
            String displayName = user != null ? user.getDisplayName() : "Unknown";

            result.add(new LeaderboardEntry(rank++, memberId, displayName, score));
        }
        return result;
    }

    /**
     * Get a user's rank on their org leaderboard.
     */
    public Optional<LeaderboardEntry> getUserRank(UUID orgId, UUID userId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(leaderboardKey(orgId), userId.toString());
        Double score = redisTemplate.opsForZSet().score(leaderboardKey(orgId), userId.toString());

        if (rank == null || score == null) return Optional.empty();

        User user = userRepository.findById(userId).orElse(null);
        String displayName = user != null ? user.getDisplayName() : "Unknown";

        return Optional.of(new LeaderboardEntry(
                (int) (rank + 1), userId, displayName, score));
    }

    private String leaderboardKey(UUID orgId) {
        return LEADERBOARD_PREFIX + orgId;
    }
}
