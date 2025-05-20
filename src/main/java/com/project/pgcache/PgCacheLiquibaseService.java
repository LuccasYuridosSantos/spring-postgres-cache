package com.project.pgcache;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Service to manage Liquibase migrations for the cache table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgCacheLiquibaseService {
    private final DataSource dataSource;
    private final PgCacheProperties properties;

    /**
     * Creates the cache table and its indexes using Liquibase.
     */
    public void createCacheTable() {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            // Set custom properties for Liquibase
            database.setDefaultSchemaName(properties.getSchema());
            database.setLiquibaseSchemaName(properties.getSchema());

            // Create Liquibase instance
            Liquibase liquibase = new Liquibase(
                "db/changelog/db.changelog-master.yaml",
                new ClassLoaderResourceAccessor(),
                database
            );

            // Set custom properties
            liquibase.getChangeLogParameters().set("tableName", properties.getTableName());
            liquibase.getChangeLogParameters().set("createIndexes", String.valueOf(properties.isCreateIndexes()));
            liquibase.getChangeLogParameters().set("useUnloggedTable", String.valueOf(properties.isUseUnloggedTable()));

            // Execute migration
            liquibase.update("");
            log.info("Cache table '{}' created successfully in schema '{}'", 
                properties.getTableName(), properties.getSchema());
        } catch (Exception e) {
            log.error("Failed to create cache table", e);
            throw new RuntimeException("Failed to create cache table", e);
        }
    }
} 