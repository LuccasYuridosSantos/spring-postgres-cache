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

## 🐳 Running the Example App (Docker Compose)

The repository ships a sample Spring Boot app (`example-app/`) that uses the library, plus a local
observability stack. Everything runs on your machine; no cloud account or API key is needed.

Requirements: Docker with Docker Compose v2. Java is not needed on the host: the app is built inside Docker.

```bash
docker compose up -d --build     # build the app image and start all services
docker compose ps                # all services should be "Up" (postgres "healthy")
```

| Service | URL | Purpose |
| --- | --- | --- |
| `app` | http://localhost:8080 | Example app (`/actuator/health`, `/actuator/prometheus`) |
| `postgres` | *(internal only)* | PostgreSQL 17 holding the `pg_cache` table |
| `grafana` | http://localhost:3000/d/pg-cache | Dashboard with cache metrics and logs (no login) |
| `prometheus` | http://localhost:9090 | Metrics scraped from the app every 5s |
| `loki` | http://localhost:3100 | Log storage |
| `alloy` | http://localhost:12345 | Collects container logs and ships them to Loki |

PostgreSQL has no port published on the host, so it never clashes with a local database.
To inspect the cache table:

```bash
docker compose exec postgres psql -U pgcache -c "SELECT key, value, expires_at FROM pg_cache;"
```

### Example endpoints

The product lookup is deliberately slow (2s) so cache hits are easy to spot:

```bash
curl localhost:8080/products/42        # 1st call: "source":"database", ~2000 ms
curl localhost:8080/products/42        # 2nd call: "source":"cache", a few ms
curl -X DELETE localhost:8080/products/42
```

Direct cache access (`ttl` accepts `10s`, `5m`, `PT1H`, `never`, or can be omitted to use `pg-cache.default-ttl`):

| Method | Path | Description |
| --- | --- | --- |
| `PUT` | `/cache/{key}?ttl=10s` | Store any JSON body |
| `GET` | `/cache/{key}` | Read a value (404 if missing or expired) |
| `DELETE` | `/cache/{key}` | Delete a value |
| `GET` | `/cache/stats` | Total / active / expired entries and average TTL |
| `POST` | `/cache/cleanup` | Remove expired entries now |

```bash
curl -X PUT -H 'Content-Type: application/json' -d '{"name":"Ana"}' "localhost:8080/cache/user:1?ttl=30s"
curl localhost:8080/cache/user:1
```

The compose file sets `pg-cache.default-ttl=30s` and `pg-cache.cleanup-interval=20s` so expiry and
background cleanup can be observed quickly (see the `app` environment in `docker-compose.yml`).

### Validating the setup

```bash
bash example-app/validate.sh     # end-to-end checks: cache-aside, JSONB storage, TTL, cleanup, restart
bash example-app/load.sh 120 4   # generate traffic for 120s with 4 workers to feed the dashboard
```

A Postman collection covering the cache API, metrics (Prometheus) and logs (Loki) is available at
`example-app/postman/pg-cache-example.postman_collection.json`. Import it in Postman and run it with
the Collection Runner, or from the command line:

```bash
npx -y newman@6 run example-app/postman/pg-cache-example.postman_collection.json
```

### Metrics and logs

- **Metrics** (exposed by the example app, not by the library itself): `pgcache_example_lookups_total{result="hit|miss"}`,
  `pgcache_example_lookup_seconds{source="cache|database"}`, `pgcache_entries{state="active|expired"}`,
  `pgcache_avg_ttl_seconds`, plus the standard JVM, HTTP and HikariCP metrics.
- **Logs**: Alloy collects the logs of containers labelled `logs: loki` (`app` and `postgres`) from the Docker socket,
  extracts the `level` label and keeps stack traces as a single entry. Query them in Grafana → Explore, e.g.
  `{service_name="app", level="ERROR"}`.

Grafana runs with anonymous admin access to keep local use simple; do not reuse this configuration anywhere exposed.

### Stopping

```bash
docker compose down        # stop and remove containers (data volume is kept)
docker compose down -v     # also remove the PostgreSQL volume
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
