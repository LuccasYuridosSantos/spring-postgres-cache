# PG Cache: A High-Performance PostgreSQL-Based Caching Library for Spring Boot

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.5-green.svg)](https://gradle.org/)

PG Cache is a **high-performance, persistent caching solution** for Spring Boot applications using **PostgreSQL UNLOGGED tables**.
It provides **Redis-like expiration**, **automatic cleanup**, and **local caching** with Caffeine for **maximum performance**.

## 🚀 Features

✅ **Spring Boot Starter** – Easy integration via `@EnablePgCache`.
✅ **Local Caffeine Cache** – Reduces database queries.
✅ **PostgreSQL Expiry Support** – Data expires automatically like Redis.
✅ **Compact JSON Storage** – Uses JSONB for efficient storage.
✅ **Cluster-Ready** – Works with PostgreSQL distributed systems (Citus).
✅ **High Performance** – Optimized for low-latency lookups.
✅ **Docker Support** – Easy deployment with Docker images.
✅ **Configurable Table and Index** – Flexible configuration for optimal performance.
✅ **Real-time Cache Statistics** – Provides insights into cache usage.

## 📦 Installation

### Using Gradle

Add the dependency to your `build.gradle`:

```groovy
dependencies {
    implementation 'com.project:pg-cache:1.0.0'
}
```

### Using Maven

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.project</groupId>
    <artifactId>pg-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

## ⚙️ Configuration

Add the following properties to your `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypassword
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml

pg-cache:
  enabled: true
  table-name: pg_cache
  schema: public
  default-ttl-seconds: 3600
  create-indexes: true
  use-unlogged-table: true
```

## 🛠️ How to Use

### Basic Usage

Inject `PgCache` into your service and use it:

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final PgCache cache;

    public User getUser(String id) {
        return cache.get("user:" + id, User.class)
            .orElseGet(() -> {
                User user = // ... buscar usuário do banco
                cache.set("user:" + id, user, Duration.ofHours(1));
                return user;
            });
    }
}
```

## 🐳 Docker Support

### Building the Docker Image

```bash
./gradlew jib
```

### Running with Docker

```bash
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/db \
  -e SPRING_DATASOURCE_USERNAME=user \
  -e SPRING_DATASOURCE_PASSWORD=pass \
  com.project/pg-cache:latest
```

## 🔥 PostgreSQL Table Structure

PG Cache automatically creates the necessary cache table when the application starts:

```sql
CREATE UNLOGGED TABLE pg_cache (
    key VARCHAR(255) PRIMARY KEY,
    value JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for better performance
CREATE INDEX idx_pg_cache_expires_at ON pg_cache (expires_at);
CREATE INDEX idx_pg_cache_created_at ON pg_cache (created_at);
CREATE INDEX idx_pg_cache_value_gin ON pg_cache USING GIN (value);
```

## 📈 Performance

PG Cache is optimized for high performance:

- **UNLOGGED PostgreSQL tables** for faster writes
- **JSONB storage** for efficient JSON handling
- **GIN indexes** for fast JSON queries
- **Automatic cleanup** of expired entries
- **Optimized autovacuum** settings
- **PostgreSQL cache plans** for improved query performance

## 🏗️ Development

### Building the Project

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Publishing to Maven Local

```bash
./gradlew publishToMavenLocal
```

### Publishing to Maven Central

```bash
- [ ] Add support for distributed caching across multiple instances
- [ ] Implement LRU eviction policy at the database level
- [ ] Provide integration with Prometheus for monitoring
- [ ] Support for custom serialization formats (e.g., ProtoBuf)
- [ ] Add support for Redis-like commands (INCR, DECR, etc.)

## 🤝 Contributing

We welcome contributions! Please open an issue or submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🏗️ Related Projects

- [Spring Cache](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache)
- [Caffeine](https://github.com/ben-manes/caffeine)
- [pg_cron](https://github.com/citusdata/pg_cron)

## 💬 Questions?

For any questions or support, feel free to open an issue or reach out.
```
