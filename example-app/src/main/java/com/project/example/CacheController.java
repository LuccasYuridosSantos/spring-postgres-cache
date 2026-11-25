package com.project.example;

import com.project.pgcache.PgCache;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Direct access to the cache: store any JSON under a key, with an optional TTL
 * (e.g. {@code ?ttl=10s}, {@code ?ttl=PT1M}, {@code ?ttl=never}; default is pg-cache.default-ttl).
 */
@RestController
@RequestMapping("/cache")
public class CacheController {
    private final PgCache cache;

    public CacheController(PgCache cache) {
        this.cache = cache;
    }

    @PutMapping("/{key}")
    public ResponseEntity<Void> put(@PathVariable String key,
                                    @RequestBody Object value,
                                    @RequestParam(required = false) String ttl) {
        if (ttl == null) {
            cache.set(key, value);
        } else if (ttl.equalsIgnoreCase("never")) {
            cache.set(key, value, null);
        } else {
            cache.set(key, value, DurationStyle.detectAndParse(ttl));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Returned as a JsonNode so every value, including a plain JSON string, is written as JSON
     * (a raw String would go through the text converter and lose its quotes).
     */
    @GetMapping("/{key}")
    public ResponseEntity<JsonNode> get(@PathVariable String key) {
        return ResponseEntity.of(cache.get(key, JsonNode.class));
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        return cache.delete(key) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/stats")
    public PgCache.CacheStats stats() {
        return cache.getStats();
    }

    @PostMapping("/cleanup")
    public Map<String, Integer> cleanup() {
        return Map.of("removed", cache.cleanup());
    }
}
