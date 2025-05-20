package com.project.pgcache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * PostgreSQL-based cache implementation.
 */
@Slf4j
@RequiredArgsConstructor
public class PgCache {
    private final JdbcTemplate jdbcTemplate;
    private final PgCacheProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Sets a value in the cache.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttl The time-to-live duration (optional)
     */
    public void set(String key, Object value, Duration ttl) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            Integer ttlSeconds = ttl != null ? (int) ttl.getSeconds() : null;
            
            jdbcTemplate.update(
                "SELECT pg_cache_set(?, ?::jsonb, ?)",
                key, jsonValue, ttlSeconds
            );
        } catch (Exception e) {
            log.error("Failed to set cache value for key: {}", key, e);
            throw new RuntimeException("Failed to set cache value", e);
        }
    }

    /**
     * Gets a value from the cache.
     *
     * @param key The cache key
     * @param type The expected type of the value
     * @return An Optional containing the value if found and not expired
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String jsonValue = jdbcTemplate.queryForObject(
                "SELECT pg_cache_get(?)",
                String.class,
                key
            );

            if (jsonValue == null) {
                return Optional.empty();
            }

            T value = objectMapper.readValue(jsonValue, type);
            return Optional.of(value);
        } catch (Exception e) {
            log.error("Failed to get cache value for key: {}", key, e);
            throw new RuntimeException("Failed to get cache value", e);
        }
    }

    /**
     * Deletes a value from the cache.
     *
     * @param key The cache key
     * @return true if the value was deleted, false otherwise
     */
    public boolean delete(String key) {
        try {
            Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT pg_cache_delete(?)",
                Boolean.class,
                key
            );
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.error("Failed to delete cache value for key: {}", key, e);
            throw new RuntimeException("Failed to delete cache value", e);
        }
    }

    /**
     * Cleans up expired entries from the cache.
     *
     * @return The number of entries deleted
     */
    public int cleanup() {
        try {
            Integer deleted = jdbcTemplate.queryForObject(
                "SELECT pg_cache_cleanup()",
                Integer.class
            );
            return deleted != null ? deleted : 0;
        } catch (Exception e) {
            log.error("Failed to cleanup cache", e);
            throw new RuntimeException("Failed to cleanup cache", e);
        }
    }

    /**
     * Gets cache statistics.
     *
     * @return Cache statistics
     */
    public CacheStats getStats() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT * FROM pg_cache_stats()",
                (rs, rowNum) -> new CacheStats(
                    rs.getLong("total_entries"),
                    rs.getLong("expired_entries"),
                    rs.getLong("active_entries"),
                    rs.getDouble("avg_ttl_seconds")
                )
            );
        } catch (Exception e) {
            log.error("Failed to get cache stats", e);
            throw new RuntimeException("Failed to get cache stats", e);
        }
    }

    /**
     * Cache statistics.
     */
    public record CacheStats(
        long totalEntries,
        long expiredEntries,
        long activeEntries,
        double avgTtlSeconds
    ) {}
} 