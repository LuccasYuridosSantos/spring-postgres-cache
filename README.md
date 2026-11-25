# PG Cache: A PostgreSQL-Based Caching Library for Spring Boot

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-13+-blue.svg)](https://www.postgresql.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.8-green.svg)](https://gradle.org/)

PG Cache is a **persistent caching solution** for Spring Boot applications backed by **PostgreSQL UNLOGGED tables**.
It provides **Redis-like expiration** and **automatic cleanup** without running a separate cache server.

## 🚀 Features

✅ **Spring Boot auto-configuration** – add the dependency and inject `PgCache`.
✅ **Redis-like expiry** – expired entries are never returned and are removed periodically.
✅ **JSONB storage** – values are serialized with Spring Boot's Jackson `JsonMapper`.
✅ **UNLOGGED table** – fast writes (configurable).
✅ **Configurable schema, table and indexes**.
✅ **Cache statistics**.

## 📦 Installation

Requirements: Java 25+, Spring Boot 4.1+, PostgreSQL 13+.

### Gradle

```groovy
dependencies {
    implementation 'com.project:pg-cache:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.project</groupId>
    <artifactId>pg-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

## ⚙️ Configuration

PG Cache uses your application's `DataSource`/`JdbcTemplate`. All `pg-cache.*` properties are optional:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypassword

pg-cache:
  enabled: true              # set to false to disable the auto-configuration
  schema: public
  table-name: pg_cache
  default-ttl: 1h            # TTL used by set(key, value)
  initialize-schema: true    # create table/indexes on startup if missing
  create-indexes: true
  use-unlogged-table: true
  cleanup-interval: 5m       # 0 disables the background cleanup
```

## 🛠️ How to Use

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final PgCache cache;

    public User getUser(String id) {
        return cache.get("user:" + id, User.class)
            .orElseGet(() -> {
                User user = loadUserFromDatabase(id);
                cache.set("user:" + id, user, Duration.ofHours(1));
                return user;
            });
    }
}
```

| Method | Description |
| --- | --- |
| `set(key, value)` | Stores a value using `pg-cache.default-ttl` |
| `set(key, value, ttl)` | Stores a value; `null` TTL means it never expires |
| `get(key, type)` | Returns `Optional<T>`, empty if missing or expired |
| `delete(key)` | Returns `true` if an entry was removed |
| `cleanup()` | Removes expired entries, returns how many |
| `getStats()` | Total / expired / active entries and average TTL |

Failures are thrown as `PgCacheException`.

## 🔥 PostgreSQL Table Structure

When `pg-cache.initialize-schema` is `true`, the table is created on startup (idempotently):

```sql
CREATE UNLOGGED TABLE IF NOT EXISTS "public"."pg_cache" (
    key        VARCHAR(255) PRIMARY KEY,
    value      JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_pg_cache_expires_at ON pg_cache (expires_at);
CREATE INDEX IF NOT EXISTS idx_pg_cache_created_at ON pg_cache (created_at);
CREATE INDEX IF NOT EXISTS idx_pg_cache_value_gin ON pg_cache USING GIN (value);
```

> UNLOGGED tables are not crash-safe and are not replicated to standbys. That is usually fine for a cache;
> set `use-unlogged-table: false` if you need otherwise.

## 🏗️ Development

The build uses a Java 25 toolchain; Gradle downloads it automatically if it is not installed.

```bash
./gradlew build                 # compile, test and package
./gradlew test                  # integration tests need Docker (Testcontainers); skipped otherwise
./gradlew publishToMavenLocal
```

## 🗺️ Roadmap

- [ ] Local in-memory (Caffeine) cache in front of PostgreSQL
- [ ] Spring `CacheManager` integration (`@Cacheable`)
- [ ] Prometheus / Micrometer metrics
- [ ] Redis-like commands (INCR, DECR, etc.)

## 🤝 Contributing

We welcome contributions! Please open an issue or submit a pull request.

## 📄 License

This project is licensed under the MIT License.
