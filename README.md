### spring-postgres-cache

# PG Cache: A High-Performance PostgreSQL-Based Caching Library for Spring Boot  

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)  
[![Java](https://img.shields.io/badge/java-17%2B-blue)](https://openjdk.org/)  
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.0-green)](https://spring.io/projects/spring-boot)  
[![PostgreSQL](https://img.shields.io/badge/postgresql-14%2B-blue)](https://www.postgresql.org/)  

PG Cache is a **high-performance, persistent caching solution** for Spring Boot applications using **PostgreSQL UNLOGGED tables**.  
It provides **Redis-like expiration**, **automatic cleanup**, and **local caching** with Caffeine for **maximum performance**.  

## 🚀 Features  

✅ **Spring Boot Starter** – Easy integration via `@EnablePgCache`.  
✅ **Local Caffeine Cache** – Reduces database queries.  
✅ **PostgreSQL Expiry Support** – Data expires automatically like Redis.  
✅ **Compact JSON Storage** – Uses GZIP for efficient storage.  
✅ **Cluster-Ready** – Works with PostgreSQL distributed systems (Citus).  
✅ **High Performance** – Optimized for low-latency lookups.  

---

## 📦 Installation  

Add the dependency to your **Maven `pom.xml`**:  

```xml
<dependency>
    <groupId>com.yourorg</groupId>
    <artifactId>pg-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or with **Gradle**:  

```gradle
implementation 'com.yourorg:pg-cache:1.0.0'
```

---

## ⚙️ Configuration  

Add the following properties to your `application.yml` or `application.properties`:  

```yaml
cache:
  enabled: true
  database:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypassword
  app:
    name: myapp-cache
  expiry:
    default-ttl-seconds: 60
```

---

## 🛠️ How to Use  

### **Basic Usage**  

Inject `PgCacheManager` into your service and use it:  

```java
@Service
public class MyService {
    
    @Autowired
    private PgCacheManager cacheManager;

    public void exampleUsage() {
        // Store data in cache
        cacheManager.put("user:123", "{\"name\":\"John Doe\"}", 120);

        // Retrieve from cache
        String json = cacheManager.get("user:123").orElse("Not Found");

        // Delete cache entry
        cacheManager.delete("user:123");
    }
}
```

---

## 🔥 PostgreSQL Table Structure  

PG Cache automatically creates the necessary cache table when the application starts.  

```sql
CREATE UNLOGGED TABLE IF NOT EXISTS cache_myapp (
    cache_key TEXT PRIMARY KEY,
    cache_value TEXT NOT NULL,
    expiry_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);
```

To enable **automatic expiration**, use `pg_cron`:  

```sql
SELECT cron.schedule('*/5 * * * *', 'DELETE FROM cache_myapp WHERE expiry_at <= NOW()');
```

---

## 📈 Performance  

PG Cache is optimized for high performance:  

- **Local cache (Caffeine) to reduce DB hits**  
- **GZIP compression for compact storage**  
- **UNLOGGED PostgreSQL tables for faster writes**  
- **Asynchronous cleanup with `pg_cron`**  

---

## 📌 Roadmap  

- [ ] Add support for distributed caching across multiple instances  
- [ ] Implement LRU eviction policy at the database level  
- [ ] Provide integration with Prometheus for monitoring  
- [ ] Support for custom serialization formats (e.g., ProtoBuf)  

---

## 🤝 Contributing  

We welcome contributions! Please open an issue or submit a pull request.  

### **Running Tests**  

To run the tests, use:  

```sh
mvn clean test
```

---

## 📄 License  

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.  

---

## 🏗️ Related Projects  

- [Spring Cache](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache)  
- [Caffeine](https://github.com/ben-manes/caffeine)  
- [pg_cron](https://github.com/citusdata/pg_cron)  

---

## 💬 Questions?  

For any questions or support, feel free to open an issue or reach out.  
```
