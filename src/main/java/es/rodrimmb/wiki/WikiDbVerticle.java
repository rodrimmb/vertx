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
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

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
            case "get-page-by-id":
                fetchPageById(message);
                break;
            case "get-page-by-name":
                fetchPageByName(message);
                break;
            case "create-page":
                createPage(message);
                break;
            case "save-page":
                savePage(message);
                break;
            case "delete-page":
                deletePage(message);
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

    private void fetchPageById(final Message<JsonObject> message) {
        JsonObject body = message.body();
        JsonArray params = new JsonArray().add(body.getString("id"));

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE_BY_ID), params, query -> {
            if(query.succeeded()) {
                Optional<JsonArray> rowOpt = query.result().getResults().stream().findFirst();
                JsonObject response = new JsonObject();
                if(rowOpt.isPresent()) {
                    JsonArray row = rowOpt.get();
                    response.put("found", true);
                    response.put("id", row.getString(0));
                    response.put("name", row.getString(1));
                    response.put("content", row.getString(2));
                    response.put("creation_date", row.getString(3));
                    response.put("update_date", row.getString(4));
                    response.put("delete_date", row.getString(5));
                } else {
                    response.put("found", false);
                }
                message.reply(response);
            } else {
                reportQueryError(message, query.cause());
            }
        });
    }

    private void fetchPageByName(final Message<JsonObject> message) {
        JsonObject body = message.body();
        JsonArray params = new JsonArray().add(body.getString("name"));

        dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE_BY_NAME), params, query -> {
            if(query.succeeded()) {
                Optional<JsonArray> rowOpt = query.result().getResults().stream().findFirst();
                JsonObject response = new JsonObject();
                if(rowOpt.isPresent()) {
                    JsonArray row = rowOpt.get();
                    response.put("found", true);
                    response.put("id", row.getString(0));
                    response.put("name", row.getString(1));
                    response.put("content", row.getString(2));
                    response.put("creation_date", row.getString(3));
                    response.put("update_date", row.getString(4));
                    response.put("delete_date", row.getString(5));
                } else {
                    response.put("found", false);
                }
                message.reply(response);
            } else {
                reportQueryError(message, query.cause());
            }
        });
    }

    private void createPage(final Message<JsonObject> message) {
        JsonObject body = message.body();

        JsonArray params = new JsonArray()
                .add(body.getString("id"))
                .add(body.getString("name"))
                .add(body.getString("content"))
                .add(body.getString("creation_date"));

        dbClient.updateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), params, update -> {
            if(update.succeeded()) {
                message.reply("ok");
            } else {
                reportQueryError(message, update.cause());
            }
        });
    }

    private void savePage(final Message<JsonObject> message) {
        JsonObject body = message.body();

        JsonArray params = new JsonArray()
                .add(body.getString("content"))
                .add(body.getString("update_date"))
                .add(body.getString("id"));

        dbClient.updateWithParams(sqlQueries.get(SqlQuery.UPDATE_PAGE), params, update -> {
            if(update.succeeded()) {
                message.reply("ok");
            } else {
                reportQueryError(message, update.cause());
            }
        });
    }

    private void deletePage(final Message<JsonObject> message) {
        JsonObject body = message.body();

        JsonArray params = new JsonArray()
                .add(body.getString("name"))
                .add(body.getString("delete_date"))
                .add(body.getString("id"));

        dbClient.updateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), params, update -> {
            if(update.succeeded()) {
                message.reply("ok");
            } else {
                reportQueryError(message, update.cause());
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
        sqlQueries.put(SqlQuery.UPDATE_PAGE, queriesProps.getProperty("save-page"));
        sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
    }

}
