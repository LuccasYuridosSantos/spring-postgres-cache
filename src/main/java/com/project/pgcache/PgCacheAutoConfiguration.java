package com.project.pgcache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Auto-configuration for PG Cache. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, JacksonAutoConfiguration.class})
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnSingleCandidate(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "pg-cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PgCacheProperties.class)
public class PgCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PgCacheSchemaInitializer pgCacheSchemaInitializer(JdbcTemplate jdbcTemplate, PgCacheProperties properties) {
        PgCacheSchemaInitializer initializer = new PgCacheSchemaInitializer(jdbcTemplate, properties);
        if (properties.isInitializeSchema()) {
            initializer.initialize();
        }
        return initializer;
    }

    @Bean
    @ConditionalOnMissingBean
    public PgCache pgCache(JdbcTemplate jdbcTemplate,
                           PgCacheProperties properties,
                           ObjectProvider<JsonMapper> jsonMapper,
                           PgCacheSchemaInitializer schemaInitializer) {
        // schemaInitializer is injected so the table exists before the cache is used.
        return new PgCache(jdbcTemplate, properties, jsonMapper.getIfAvailable(JsonMapper::shared));
    }

    @Bean
    @ConditionalOnMissingBean
    public PgCacheCleanupScheduler pgCacheCleanupScheduler(PgCache pgCache, PgCacheProperties properties) {
        return new PgCacheCleanupScheduler(pgCache, properties.getCleanupInterval());
    }
}
