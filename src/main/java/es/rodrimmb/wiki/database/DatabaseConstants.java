package es.rodrimmb.wiki.database;

public interface DatabaseConstants {
    String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
    String CONFIG_WIKIDB_JDBC_DB = "wikidb.jdbc.db";
    String CONFIG_WIKIDB_JDBC_USER = "wikidb.jdbc.user";
    String CONFIG_WIKIDB_JDBC_PASSWORD = "wikidb.jdbc.password";
    String CONFIG_WIKIDB_JDBC_DRIVER = "wikidb.jdbc.driver";
    String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
    String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";

    String DEFAULT_CONFIG_WIKIDB_JDBC_URL = "jdbc:postgresql://localhost:5432/rainbow_database";
    String DEFAULT_CONFIG_WIKIDB_JDBC_DB = "rainbow_database";
    String DEFAULT_CONFIG_WIKIDB_JDBC_USER = "unicorn_user";
    String DEFAULT_CONFIG_WIKIDB_JDBC_PASSWORD = "magical_password";
    String DEFAULT_CONFIG_WIKIDB_JDBC_DRIVER = "org.postgresql.Driver";
    int DEFAULT_CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = 30;
}
