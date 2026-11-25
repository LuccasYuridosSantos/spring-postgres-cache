package com.project.pgcache;

import java.util.regex.Pattern;

/**
 * Validated, quoted SQL identifiers for the cache table.
 */
record PgCacheTable(String schema, String table) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");

    PgCacheTable {
        requireIdentifier("pg-cache.schema", schema);
        requireIdentifier("pg-cache.table-name", table);
    }

    static PgCacheTable from(PgCacheProperties properties) {
        return new PgCacheTable(properties.getSchema(), properties.getTableName());
    }

    /** Schema-qualified, quoted table name, safe to embed in SQL. */
    String qualifiedName() {
        return quote(schema) + "." + quote(table);
    }

    /** Quoted index name derived from the table name (PostgreSQL limits names to 63 chars). */
    String indexName(String suffix) {
        String name = "idx_" + table + "_" + suffix;
        return quote(name.length() > 63 ? name.substring(0, 63) : name);
    }

    private static String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    private static void requireIdentifier(String property, String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(property
                + " must be a valid SQL identifier ([A-Za-z_][A-Za-z0-9_]*, max 63 chars) but was: " + value);
        }
    }
}
