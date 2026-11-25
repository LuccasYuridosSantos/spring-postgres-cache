package com.project.pgcache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the PostgreSQL cache.
 */
@Data
@ConfigurationProperties(prefix = "pg-cache")
public class PgCacheProperties {
    /**
     * Whether the cache is enabled.
     */
    private boolean enabled = true;

    /**
     * The name of the cache table.
     */
    private String tableName = "pg_cache";

    /**
     * The schema where the cache table will be created.
     */
    private String schema = "public";

    /**
     * Default time-to-live used by {@link PgCache#set(String, Object)}.
     */
    private Duration defaultTtl = Duration.ofHours(1);

    /**
     * Whether to create the cache table and indexes on startup if they do not exist.
     */
    private boolean initializeSchema = true;

    /**
     * Whether to create indexes automatically.
     */
    private boolean createIndexes = true;

    /**
     * Whether to use an UNLOGGED table for better write performance.
     */
    private boolean useUnloggedTable = true;

    /**
     * Interval between automatic removals of expired entries. Zero or negative disables it.
     */
    private Duration cleanupInterval = Duration.ofMinutes(5);
}
