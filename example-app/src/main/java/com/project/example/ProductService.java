package com.project.example;

import com.project.pgcache.PgCache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Cache-aside example: the "database" lookup is deliberately slow (2s),
 * so a cache hit is easy to see in the response time.
 * Publishes hit/miss counters and a lookup timer per source to Micrometer.
 */
@Service
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final PgCache cache;
    private final Counter hits;
    private final Counter misses;
    private final Timer cacheLookups;
    private final Timer databaseLookups;

    public ProductService(PgCache cache, MeterRegistry registry) {
        this.cache = cache;
        this.hits = Counter.builder("pgcache.example.lookups").tag("result", "hit")
            .description("Product lookups answered by the cache").register(registry);
        this.misses = Counter.builder("pgcache.example.lookups").tag("result", "miss")
            .description("Product lookups that went to the slow source").register(registry);
        this.cacheLookups = Timer.builder("pgcache.example.lookup").tag("source", "cache")
            .description("Product lookup duration").register(registry);
        this.databaseLookups = Timer.builder("pgcache.example.lookup").tag("source", "database")
            .description("Product lookup duration").register(registry);
    }

    public Lookup findProduct(String id) {
        String key = "product:" + id;
        Timer.Sample sample = Timer.start();
        Optional<Product> cached = cache.get(key, Product.class);
        if (cached.isPresent()) {
            log.info("Cache HIT for {}", key);
            hits.increment();
            sample.stop(cacheLookups);
            return new Lookup(cached.get(), "cache");
        }
        log.info("Cache MISS for {}, loading from slow source", key);
        misses.increment();
        Product product = loadFromSlowSource(id);
        cache.set(key, product); // uses pg-cache.default-ttl
        sample.stop(databaseLookups);
        return new Lookup(product, "database");
    }

    public boolean evict(String id) {
        return cache.delete("product:" + id);
    }

    private Product loadFromSlowSource(String id) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new Product(id, "Product " + id, new BigDecimal("19.99"));
    }

    public record Lookup(Product product, String source) {}
}
