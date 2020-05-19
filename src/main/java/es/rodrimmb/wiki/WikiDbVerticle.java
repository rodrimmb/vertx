package es.rodrimmb.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public final class WikiDbVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(WikiDbVerticle.class);
    private static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "db-queries.properties";

    public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
    public static final String CONFIG_WIKIDB_JDBC_DB = "wikidb.jdbc.db";
    public static final String CONFIG_WIKIDB_JDBC_USER = "wikidb.jdbc.user";
    public static final String CONFIG_WIKIDB_JDBC_PASSWORD = "wikidb.jdbc.password";
    public static final String CONFIG_WIKIDB_JDBC_DRIVER = "wikidb.jdbc.driver";
    public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";

    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private JDBCClient dbClient;

    @Override
    public void start(final Promise<Void> promise) throws Exception {
        // Usamos APIs bloqueantes para cargar las queries de un fichero, pero son pocos datos...
        loadSqlQueries();

        JsonObject config = new JsonObject()
                .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:postgresql://localhost:5432/rainbow_database"))
                .put("user", config().getString(CONFIG_WIKIDB_JDBC_USER, "unicorn_user"))
                .put("password", config().getString(CONFIG_WIKIDB_JDBC_PASSWORD, "magical_password"))
                .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER, "org.postgresql.Driver"))
                .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30));

        dbClient = JDBCClient.createShared(vertx, config);
        dbClient.getConnection(asyncResult -> {
            if(asyncResult.succeeded()) {
                //Creamos la tabla que vamos a usar si no existe
                SQLConnection connection = asyncResult.result();
                connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {
                    connection.close();
                    if (create.failed()) {
                        LOG.error("Fallo al prapara la DB", create.cause());
                        promise.fail(create.cause());
                    } else {
                        vertx.eventBus().consumer(config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"), this::onMessage);
                        LOG.info("Despliegue correcto de la DB {}", config().getString(CONFIG_WIKIDB_JDBC_DB, "rainbow_database"));
                        promise.complete();
                    }
                });
            } else {
                LOG.error("No se ha podido abrir la conexion a la DB", asyncResult.cause());
                promise.fail(asyncResult.cause());
            }
        });
    }

    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    private void onMessage(final Message<JsonObject> message) {
        if (!message.headers().contains("action")) {
            LOG.error("No hay una accion que podamos realizar para {} con el body {}",
                    message.headers(), message.body().encodePrettily());
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }

        String action = message.headers().get("action");

        switch (action) {
            case "all-pages":
                fetchAllPages(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
        }
    }

    private void fetchAllPages(final Message<JsonObject> message) {
        dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), result -> {
            if(result.succeeded()) {
                List<JsonObject> pages = result.result()
                        .getResults()
                        .stream()
                        .map(json -> new JsonObject()
                                .put("key", json.getString(0))
                                .put("value", json.getString(1))
                        ).collect(Collectors.toList());
                message.reply(new JsonObject().put("pages", new JsonArray(pages)));
            } else {
                reportQueryError(message, result.cause());
            }
        });
    }

    private void reportQueryError(final Message<JsonObject> message, final Throwable cause) {
        LOG.error("Error al ejecutar query en la DB", cause);
        message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }

    private enum SqlQuery {
        CREATE_PAGES_TABLE,
        ALL_PAGES,
        GET_PAGE_BY_NAME,
        GET_PAGE_BY_ID,
        CREATE_PAGE,
        UPDATE_PAGE,
        DELETE_PAGE
    }
    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {

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

        sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
        sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
        sqlQueries.put(SqlQuery.GET_PAGE_BY_NAME, queriesProps.getProperty("get-page-by-name"));
        sqlQueries.put(SqlQuery.GET_PAGE_BY_ID, queriesProps.getProperty("get-page-by-id"));
        sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
        sqlQueries.put(SqlQuery.UPDATE_PAGE, queriesProps.getProperty("update-page"));
        sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
    }

}
