package com.project.pgcache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PgCacheCleanupSchedulerTest {

    private static final Duration FAST = Duration.ofMillis(20);

    private final PgCache cache = mock(PgCache.class);
    private PgCacheCleanupScheduler scheduler;

    @AfterEach
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    @Test
    void periodicallyRemovesExpiredEntries() {
        when(cache.cleanup()).thenReturn(3, 0);
        scheduler = new PgCacheCleanupScheduler(cache, FAST);

        scheduler.start();

        assertThat(scheduler.isRunning()).isTrue();
        verify(cache, timeout(2000).atLeast(2)).cleanup();
    }

    @Test
    void keepsRunningWhenCleanupFails() {
        when(cache.cleanup())
            .thenThrow(new PgCacheException("Failed to cleanup cache", new RuntimeException("db down")))
            .thenReturn(0);
        scheduler = new PgCacheCleanupScheduler(cache, FAST);

        scheduler.start();

        verify(cache, timeout(2000).atLeast(2)).cleanup();
        assertThat(scheduler.isRunning()).isTrue();
    }

    @Test
    void startingTwiceDoesNotCreateASecondSchedule() throws InterruptedException {
        scheduler = new PgCacheCleanupScheduler(cache, FAST);
        scheduler.start();
        scheduler.start();
        verify(cache, timeout(2000).atLeast(1)).cleanup();

        scheduler.stop();
        Thread.sleep(50); // let a run that was already in flight finish
        clearInvocations(cache);
        Thread.sleep(200);

        // A leaked second executor would still be calling cleanup() after stop()
        assertThat(scheduler.isRunning()).isFalse();
        verifyNoInteractions(cache);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PT0S", "PT-1S"})
    void doesNotStartWhenIntervalIsNotPositive(Duration interval) throws InterruptedException {
        scheduler = new PgCacheCleanupScheduler(cache, interval);

        scheduler.start();
        Thread.sleep(100);

        assertThat(scheduler.isRunning()).isFalse();
        verifyNoInteractions(cache);
    }

    @Test
    void stopWithoutStartIsANoOp() {
        scheduler = new PgCacheCleanupScheduler(cache, FAST);

        scheduler.stop();

        assertThat(scheduler.isRunning()).isFalse();
    }
}
