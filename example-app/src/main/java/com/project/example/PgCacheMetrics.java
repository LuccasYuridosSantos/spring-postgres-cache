package com.project.example;

import com.project.pgcache.PgCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.function.ToDoubleFunction;

/**
 * Exposes {@link PgCache#getStats()} as gauges. The stats query is cached for a
 * couple of seconds so one Prometheus scrape runs it once instead of once per gauge.
 */
@Component
public class PgCacheMetrics implements MeterBinder {
    private static final Duration STATS_MAX_AGE = Duration.ofSeconds(2);

    private final PgCache cache;
    private PgCache.CacheStats lastStats;
    private Instant lastRead = Instant.EPOCH;

    public PgCacheMetrics(PgCache cache) {
        this.cache = cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "pgcache.entries", "active", s -> s.activeEntries());
        gauge(registry, "pgcache.entries", "expired", s -> s.expiredEntries());
        Gauge.builder("pgcache.avg.ttl", this, m -> m.stats().avgTtlSeconds())
            .description("Average TTL of the cached entries").baseUnit("seconds").register(registry);
    }

    private void gauge(MeterRegistry registry, String name, String state, ToDoubleFunction<PgCache.CacheStats> value) {
        Gauge.builder(name, this, m -> value.applyAsDouble(m.stats()))
            .tag("state", state).description("Entries in the pg_cache table").register(registry);
    }

    private synchronized PgCache.CacheStats stats() {
        Instant now = Instant.now();
        if (lastStats == null || lastRead.plus(STATS_MAX_AGE).isBefore(now)) {
            lastStats = cache.getStats();
            lastRead = now;
        }
        return lastStats;
    }
}
