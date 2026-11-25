package com.project.pgcache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PgCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PgCacheAutoConfiguration.class))
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withPropertyValues("pg-cache.initialize-schema=false");

    @Test
    void registersCacheBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(PgCache.class);
            assertThat(context).hasSingleBean(PgCacheCleanupScheduler.class);
        });
    }

    @Test
    void backsOffWhenDisabled() {
        runner.withPropertyValues("pg-cache.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(PgCache.class));
    }

    @Test
    void backsOffWithoutJdbcTemplate() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PgCacheAutoConfiguration.class))
            .run(context -> assertThat(context).doesNotHaveBean(PgCache.class));
    }

    @Test
    void rejectsUnsafeTableName() {
        runner.withPropertyValues("pg-cache.table-name=cache; DROP TABLE users")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveTtl() {
        runner.run(context -> assertThatThrownBy(
            () -> context.getBean(PgCache.class).set("k", "v", java.time.Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class));
    }
}
