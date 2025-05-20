package com.project.pgcache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
     * Default time-to-live for cache entries in seconds.
     */
    private int defaultTtlSeconds = 3600; // 1 hour

    /**
     * Whether to create indexes automatically.
     */
    private boolean createIndexes = true;

    /**
     * Whether to use UNLOGGED table for better performance.
     */
    private boolean useUnloggedTable = true;
} 