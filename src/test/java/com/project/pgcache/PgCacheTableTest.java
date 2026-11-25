package com.project.pgcache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgCacheTableTest {

    @Test
    void quotesSchemaAndTable() {
        PgCacheTable table = new PgCacheTable("public", "pg_cache");

        assertThat(table.qualifiedName()).isEqualTo("\"public\".\"pg_cache\"");
        assertThat(table.indexName("expires_at")).isEqualTo("\"idx_pg_cache_expires_at\"");
    }

    @Test
    void truncatesIndexNamesToPostgresLimit() {
        String longTable = "t".repeat(63); // longest identifier PostgreSQL accepts
        PgCacheTable table = new PgCacheTable("public", longTable);

        String index = table.indexName("expires_at");

        String unquoted = index.substring(1, index.length() - 1);
        assertThat(index).startsWith("\"").endsWith("\"");
        assertThat(unquoted).hasSize(63).isEqualTo(("idx_" + longTable + "_expires_at").substring(0, 63));
    }

    @Test
    void rejectsNullSchema() {
        assertThatThrownBy(() -> new PgCacheTable(null, "pg_cache"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pg-cache.schema")
            .hasMessageEndingWith("but was: null");
    }

    @Test
    void rejectsIdentifierLongerThan63Chars() {
        assertThatThrownBy(() -> new PgCacheTable("public", "t".repeat(64)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pg-cache.table-name");
    }
}
