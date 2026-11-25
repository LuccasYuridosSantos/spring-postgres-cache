package com.project.pgcache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates the cache table and its indexes if they do not exist. Every statement is idempotent,
 * so it is safe to run on each startup.
 */
@Slf4j
public class PgCacheSchemaInitializer {
    private final JdbcTemplate jdbcTemplate;
    private final PgCacheProperties properties;
    private final PgCacheTable table;

    public PgCacheSchemaInitializer(JdbcTemplate jdbcTemplate, PgCacheProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.table = PgCacheTable.from(properties);
    }

    public void initialize() {
        String name = table.qualifiedName();
        String unlogged = properties.isUseUnloggedTable() ? "UNLOGGED " : "";

        jdbcTemplate.execute("""
            CREATE %sTABLE IF NOT EXISTS %s (
                key        VARCHAR(255) PRIMARY KEY,
                value      JSONB NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP WITH TIME ZONE
            )""".formatted(unlogged, name));

        jdbcTemplate.execute("""
            ALTER TABLE %s SET (
                autovacuum_vacuum_scale_factor = 0.1,
                autovacuum_analyze_scale_factor = 0.05,
                autovacuum_vacuum_threshold = 50,
                autovacuum_analyze_threshold = 50
            )""".formatted(name));

        if (properties.isCreateIndexes()) {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS %s ON %s (expires_at)"
                .formatted(table.indexName("expires_at"), name));
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS %s ON %s (created_at)"
                .formatted(table.indexName("created_at"), name));
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS %s ON %s USING GIN (value)"
                .formatted(table.indexName("value_gin"), name));
        }

        log.info("PG Cache table {} is ready", name);
    }
}
