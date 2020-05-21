package es.rodrimmb.wiki.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

public final class WikiDbVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(WikiDbVerticle.class);

    public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
    public static final String CONFIG_WIKIDB_JDBC_DB = "wikidb.jdbc.db";
    public static final String CONFIG_WIKIDB_JDBC_USER = "wikidb.jdbc.user";
    public static final String CONFIG_WIKIDB_JDBC_PASSWORD = "wikidb.jdbc.password";
    public static final String CONFIG_WIKIDB_JDBC_DRIVER = "wikidb.jdbc.driver";
    public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
    public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";

    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private JDBCClient dbClient;

    @Override
    public void start(final Promise<Void> promise) throws Exception {
        JsonObject config = new JsonObject()
                .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:postgresql://localhost:5432/rainbow_database"))
                .put("user", config().getString(CONFIG_WIKIDB_JDBC_USER, "unicorn_user"))
                .put("password", config().getString(CONFIG_WIKIDB_JDBC_PASSWORD, "magical_password"))
                .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER, "org.postgresql.Driver"))
                .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30));

        HashMap<SqlQuery, String> sqlQueries = loadSqlQueries();

        dbClient = JDBCClient.createShared(vertx, config);

        WikiDbService.create(dbClient, sqlQueries, ready -> {
            if(ready.succeeded()) {
                ServiceBinder binder = new ServiceBinder(vertx);
                binder
                        .setAddress(CONFIG_WIKIDB_QUEUE)
                        .register(WikiDbService.class, ready.result());
                promise.complete();
            } else {
                promise.fail(ready.cause());
            }
        });
    }

    /*
     * Usamos APIs bloqueantes para cargar las queries de un fichero, pero son pocos datos...
     */
    private HashMap<SqlQuery, String> loadSqlQueries() throws IOException {
        String queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE);
        InputStream queriesInputStream;
        if (queriesFile != null) {
            queriesInputStream = new FileInputStream(queriesFile);
        } else {
            queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
        }

        Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        HashMap<SqlQuery, String> sqlQueries = new HashMap<>();
        sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
        sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
        sqlQueries.put(SqlQuery.GET_PAGE_BY_NAME, queriesProps.getProperty("get-page-by-name"));
        sqlQueries.put(SqlQuery.GET_PAGE_BY_ID, queriesProps.getProperty("get-page-by-id"));
        sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
        sqlQueries.put(SqlQuery.UPDATE_PAGE, queriesProps.getProperty("save-page"));
        sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
        return sqlQueries;
    }

}
