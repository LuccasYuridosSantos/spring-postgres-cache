package com.project.pgcache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PgCacheIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    record User(String id, String name, List<String> roles) {}

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                DataSourceAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class,
                JacksonAutoConfiguration.class,
                PgCacheAutoConfiguration.class))
            .withPropertyValues(
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword());
    }

    private void withCache(Consumer<PgCache> test) {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            test.accept(context.getBean(PgCache.class));
        });
    }

    @BeforeEach
    void clear() {
        runner().run(context -> context.getBean(JdbcTemplate.class).execute("TRUNCATE public.pg_cache"));
    }

    @Test
    void setGetAndDelete() {
        withCache(cache -> {
            User user = new User("1", "Ana", List.of("admin"));
            cache.set("user:1", user, Duration.ofMinutes(1));

            assertThat(cache.get("user:1", User.class)).contains(user);
            assertThat(cache.get("missing", User.class)).isEmpty();
            assertThat(cache.delete("user:1")).isTrue();
            assertThat(cache.delete("user:1")).isFalse();
            assertThat(cache.get("user:1", User.class)).isEmpty();
        });
    }

    @Test
    void overwritesExistingKey() {
        withCache(cache -> {
            cache.set("k", "first");
            cache.set("k", "second", null);
            assertThat(cache.get("k", String.class)).contains("second");
        });
    }

    @Test
    void expiredEntriesAreHiddenAndCleanedUp() throws Exception {
        withCache(cache -> {
            cache.set("short", 42, Duration.ofMillis(50));
            cache.set("forever", 7, null);
            sleep(200);

            assertThat(cache.get("short", Integer.class)).isEmpty();
            assertThat(cache.getStats().expiredEntries()).isEqualTo(1);
            assertThat(cache.cleanup()).isEqualTo(1);

            PgCache.CacheStats stats = cache.getStats();
            assertThat(stats.totalEntries()).isEqualTo(1);
            assertThat(stats.activeEntries()).isEqualTo(1);
        });
    }

    @Test
    void supportsCustomSchemaAndLoggedTable() {
        runner().run(context -> context.getBean(JdbcTemplate.class).execute("CREATE SCHEMA IF NOT EXISTS cache"));
        runner()
            .withPropertyValues(
                "pg-cache.schema=cache",
                "pg-cache.table-name=my_cache",
                "pg-cache.use-unlogged-table=false",
                "pg-cache.create-indexes=false")
            .run(context -> {
                PgCache cache = context.getBean(PgCache.class);
                cache.set("a", "b");
                assertThat(cache.get("a", String.class)).contains("b");
            });
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
