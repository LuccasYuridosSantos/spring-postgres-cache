package com.project.pgcache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL-based cache. Values are stored as JSONB and expire like Redis keys.
 */
@Slf4j
public class PgCache {
    private final JdbcTemplate jdbcTemplate;
    private final PgCacheProperties properties;
    private final JsonMapper jsonMapper;

    private final String upsertSql;
    private final String selectSql;
    private final String deleteSql;
    private final String cleanupSql;
    private final String statsSql;

    public PgCache(JdbcTemplate jdbcTemplate, PgCacheProperties properties, JsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.jsonMapper = jsonMapper;

        String table = PgCacheTable.from(properties).qualifiedName();
        this.upsertSql = """
            INSERT INTO %s (key, value, created_at, expires_at)
            VALUES (?, CAST(? AS JSONB), CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP + CAST(? AS BIGINT) * INTERVAL '1 millisecond')
            ON CONFLICT (key) DO UPDATE SET
                value = EXCLUDED.value,
                created_at = EXCLUDED.created_at,
                expires_at = EXCLUDED.expires_at""".formatted(table);
        this.selectSql = """
            SELECT CAST(value AS TEXT) FROM %s
            WHERE key = ? AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)""".formatted(table);
        this.deleteSql = "DELETE FROM %s WHERE key = ?".formatted(table);
        this.cleanupSql = "DELETE FROM %s WHERE expires_at <= CURRENT_TIMESTAMP".formatted(table);
        this.statsSql = """
            SELECT
                COUNT(*) AS total_entries,
                COUNT(*) FILTER (WHERE expires_at <= CURRENT_TIMESTAMP) AS expired_entries,
                COUNT(*) FILTER (WHERE expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) AS active_entries,
                CAST(COALESCE(AVG(EXTRACT(EPOCH FROM (expires_at - created_at))), 0) AS DOUBLE PRECISION)
                    AS avg_ttl_seconds
            FROM %s""".formatted(table);
    }

    /**
     * Sets a value in the cache using the configured default TTL ({@code pg-cache.default-ttl}).
     */
    public void set(String key, Object value) {
        set(key, value, properties.getDefaultTtl());
    }

    /**
     * Sets a value in the cache.
     *
     * @param key   The cache key (max 255 characters)
     * @param value The value to cache (serialized as JSON)
     * @param ttl   The time-to-live, or {@code null} for an entry that never expires
     */
    public void set(String key, Object value, Duration ttl) {
        Objects.requireNonNull(key, "key must not be null");
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("ttl must be positive or null, but was: " + ttl);
        }
        try {
            String json = jsonMapper.writeValueAsString(value);
            Long ttlMillis = ttl != null ? ttl.toMillis() : null;
            jdbcTemplate.update(upsertSql, key, json, ttlMillis);
        } catch (RuntimeException e) {
            log.error("Failed to set cache value for key: {}", key, e);
            throw new PgCacheException("Failed to set cache value for key: " + key, e);
        }
    }

    /**
     * Gets a value from the cache.
     *
     * @param key  The cache key
     * @param type The expected type of the value
     * @return An Optional containing the value if found and not expired
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            List<String> rows = jdbcTemplate.queryForList(selectSql, String.class, key);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(jsonMapper.readValue(rows.getFirst(), type));
        } catch (RuntimeException e) {
            log.error("Failed to get cache value for key: {}", key, e);
            throw new PgCacheException("Failed to get cache value for key: " + key, e);
        }
    }

    /**
     * Deletes a value from the cache.
     *
     * @return true if an entry was deleted, false otherwise
     */
    public boolean delete(String key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            return jdbcTemplate.update(deleteSql, key) > 0;
        } catch (RuntimeException e) {
            log.error("Failed to delete cache value for key: {}", key, e);
            throw new PgCacheException("Failed to delete cache value for key: " + key, e);
        }
    }

    /**
     * Removes expired entries from the cache.
     *
     * @return The number of entries deleted
     */
    public int cleanup() {
        try {
            return jdbcTemplate.update(cleanupSql);
        } catch (RuntimeException e) {
            log.error("Failed to cleanup cache", e);
            throw new PgCacheException("Failed to cleanup cache", e);
        }
    }

    /**
     * Gets cache statistics.
     */
    public CacheStats getStats() {
        try {
            return jdbcTemplate.queryForObject(statsSql, (rs, rowNum) -> new CacheStats(
                rs.getLong("total_entries"),
                rs.getLong("expired_entries"),
                rs.getLong("active_entries"),
                rs.getDouble("avg_ttl_seconds")
            ));
        } catch (RuntimeException e) {
            log.error("Failed to get cache stats", e);
            throw new PgCacheException("Failed to get cache stats", e);
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
