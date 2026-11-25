package com.project.pgcache;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Failure handling of {@link PgCache}: every database error must surface as a {@link PgCacheException}
 * that keeps the original cause. The happy paths run against a real PostgreSQL in {@link PgCacheIntegrationTest}.
 */
class PgCacheTest {

    private static final DataAccessResourceFailureException DB_DOWN = new DataAccessResourceFailureException("database is down");

    /** JdbcTemplate whose every call fails, as when the database is unreachable. */
    private final JdbcTemplate failingJdbc = mock(JdbcTemplate.class, invocation -> {
        throw DB_DOWN;
    });

    private final PgCache cache = new PgCache(failingJdbc, new PgCacheProperties(), JsonMapper.shared());

    @Test
    void setWrapsDatabaseFailure() {
        assertThatThrownBy(() -> cache.set("k", "v", Duration.ofMinutes(1)))
            .isInstanceOf(PgCacheException.class)
            .hasMessage("Failed to set cache value for key: k")
            .hasCause(DB_DOWN);
    }

    @Test
    void getWrapsDatabaseFailure() {
        assertThatThrownBy(() -> cache.get("k", String.class))
            .isInstanceOf(PgCacheException.class)
            .hasMessage("Failed to get cache value for key: k")
            .hasCause(DB_DOWN);
    }

    @Test
    void deleteWrapsDatabaseFailure() {
        assertThatThrownBy(() -> cache.delete("k"))
            .isInstanceOf(PgCacheException.class)
            .hasMessage("Failed to delete cache value for key: k")
            .hasCause(DB_DOWN);
    }

    @Test
    void cleanupWrapsDatabaseFailure() {
        assertThatThrownBy(cache::cleanup)
            .isInstanceOf(PgCacheException.class)
            .hasMessage("Failed to cleanup cache")
            .hasCause(DB_DOWN);
    }

    @Test
    void getStatsWrapsDatabaseFailure() {
        assertThatThrownBy(cache::getStats)
            .isInstanceOf(PgCacheException.class)
            .hasMessage("Failed to get cache stats")
            .hasCause(DB_DOWN);
    }

    @Test
    void rejectsNegativeTtlWithoutTouchingTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PgCache pgCache = new PgCache(jdbc, new PgCacheProperties(), JsonMapper.shared());

        assertThatThrownBy(() -> pgCache.set("k", "v", Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ttl must be positive or null");
        verifyNoInteractions(jdbc);
    }
}
