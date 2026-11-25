package com.project.pgcache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically removes expired entries from the cache table.
 */
@Slf4j
public class PgCacheCleanupScheduler implements SmartLifecycle {
    private final PgCache cache;
    private final Duration interval;
    private ScheduledExecutorService executor;

    public PgCacheCleanupScheduler(PgCache cache, Duration interval) {
        this.cache = cache;
        this.interval = interval;
    }

    @Override
    public synchronized void start() {
        if (executor != null || interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("pg-cache-cleanup").daemon(true).factory());
        long millis = interval.toMillis();
        executor.scheduleWithFixedDelay(this::runCleanup, millis, millis, TimeUnit.MILLISECONDS);
    }

    private void runCleanup() {
        try {
            int deleted = cache.cleanup();
            if (deleted > 0) {
                log.debug("Removed {} expired cache entries", deleted);
            }
        } catch (RuntimeException e) {
            // Already logged by PgCache; keep the schedule alive.
        }
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }
}
