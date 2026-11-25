package com.project.pgcache;

/**
 * Thrown when a cache operation fails.
 */
public class PgCacheException extends RuntimeException {
    public PgCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
