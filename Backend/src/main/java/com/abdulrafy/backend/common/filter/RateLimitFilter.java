package com.abdulrafy.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import com.abdulrafy.backend.common.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final StringRedisTemplate redisTemplate;
    private final int registerLimit;
    private final int loginLimit;
    private final int tradeLimit;
    private final int journalLimit;
    private final int windowSeconds;

    public RateLimitFilter(
            StringRedisTemplate redisTemplate,
            @Value("${apex.ratelimit.register:10}") int registerLimit,
            @Value("${apex.ratelimit.login:20}") int loginLimit,
            @Value("${apex.ratelimit.trade:30}") int tradeLimit,
            @Value("${apex.ratelimit.journal:5}") int journalLimit,
            @Value("${apex.ratelimit.window-seconds:60}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.registerLimit = registerLimit;
        this.loginLimit = loginLimit;
        this.tradeLimit = tradeLimit;
        this.journalLimit = journalLimit;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        RateLimitRule rule = resolveRule(path, method, request);

        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = rule.key();
        try {
            long count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }

            response.setHeader("X-RateLimit-Limit", String.valueOf(rule.maxRequests()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, rule.maxRequests() - count)));
            response.setHeader("X-RateLimit-Window", String.valueOf(windowSeconds));

            if (count > rule.maxRequests()) {
                response.setHeader("Retry-After", String.valueOf(windowSeconds));
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

                String body = """
                    {
                        "type": "https://api.apex.com/errors/rate-limit-exceeded",
                        "title": "rate-limit-exceeded",
                        "status": 429,
                        "detail": "Too many requests. Please try again in %d seconds.",
                        "instance": "%s",
                        "timestamp": "%s"
                    }
                    """.formatted(windowSeconds, path, Instant.now().toString());
                response.getWriter().write(body);
                return;
            }
        } catch (Exception e) {
            log.warn("Rate limiter unavailable for key={}: {}. Failing open — request allowed.", key, e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitRule resolveRule(String path, String method, HttpServletRequest request) {
        String ip = getClientIp(request);
        if (path.startsWith("/api/v1/auth/register")) {
            return new RateLimitRule("rl:auth:register:" + ip, registerLimit);
        }
        if (path.startsWith("/api/v1/auth/login")) {
            return new RateLimitRule("rl:auth:login:" + ip, loginLimit);
        }
        if (path.startsWith("/api/v1/trading/execute") && "POST".equals(method)) {
            String userId = extractUserId(request);
            String scope = userId != null ? userId : ip;
            return new RateLimitRule("rl:trade:" + scope, tradeLimit);
        }
        if (path.startsWith("/api/v1/journal/generate") && "POST".equals(method)) {
            String userId = extractUserId(request);
            String scope = userId != null ? userId : ip;
            return new RateLimitRule("rl:journal:" + scope, journalLimit);
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id().toString();
        }
        return null;
    }

    record RateLimitRule(String key, int maxRequests) {}
}
