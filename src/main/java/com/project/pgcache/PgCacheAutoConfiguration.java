package com.project.pgcache;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for PG Cache.
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(PgCacheProperties.class)
public class PgCacheAutoConfiguration {
    private final PgCacheProperties properties;
    private final PgCacheLiquibaseService liquibaseService;

    @Bean
    @ConditionalOnProperty(name = "pg-cache.enabled", havingValue = "true", matchIfMissing = true)
    public PgCache pgCache(JdbcTemplate jdbcTemplate) {
        // Create cache table and functions
        liquibaseService.createCacheTable();
        
        return new PgCache(jdbcTemplate, properties);
    }
} 