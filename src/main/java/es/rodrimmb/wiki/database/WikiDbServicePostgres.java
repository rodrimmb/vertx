package es.rodrimmb.wiki.database;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class WikiDbServicePostgres implements WikiDbService {

    private static final Logger LOG = LoggerFactory.getLogger(WikiDbServicePostgres.class);

    private final HashMap<SqlQuery, String> sqlQueries;
    private final JDBCClient dbClient;

    public WikiDbServicePostgres(final JDBCClient dbClient, final HashMap<SqlQuery, String> sqlQueries,
                                 final Handler<AsyncResult<WikiDbService>> readyHandler) {
        this.dbClient = dbClient;
        this.sqlQueries = sqlQueries;

        dbClient.getConnection(asyncResult -> {
            if(asyncResult.succeeded()) {
                //Creamos la tabla que vamos a usar si no existe
                SQLConnection connection = asyncResult.result();
                connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {
                    connection.close();
                    if (create.failed()) {
                        LOG.error("Fallo al prapara la DB", create.cause());
                        readyHandler.handle(Future.failedFuture(asyncResult.cause()));
                    } else {
                        LOG.info("Preparacion correcto de la DB");
                        readyHandler.handle(Future.succeededFuture(this));
                    }
                });
            } else {
                LOG.error("No se ha podido abrir la conexion a la DB", asyncResult.cause());
                readyHandler.handle(Future.failedFuture(asyncResult.cause()));
            }
        });
    }

    @Override
    public WikiDbService fetchAllPages(final Handler<AsyncResult<JsonArray>> resultHandler) {
        dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), result -> {
            if(result.succeeded()) {
                List<JsonObject> pages = result.result()
                        .getResults()
                        .stream()
                        .map(json -> new JsonObject()
                                .put("key", json.getString(0))
                                .put("value", json.getString(1))
                        ).collect(Collectors.toList());
                resultHandler.handle(Future.succeededFuture(new JsonArray(pages)));
            } else {
                LOG.error("Error al ejecutar query {}", sqlQueries.get(SqlQuery.ALL_PAGES), result.cause());
                resultHandler.handle(Future.failedFuture(result.cause()));
            }
        });
        return this;
    }

    @Override
    public WikiDbService fetchPageById(final String id, final Handler<AsyncResult<JsonObject>> resultHandler) {
        String sqlQuery = sqlQueries.get(SqlQuery.GET_PAGE_BY_ID);
        JsonArray params = new JsonArray().add(id);
        dbClient.queryWithParams(sqlQuery, params, query -> {
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
                resultHandler.handle(Future.succeededFuture(response));
            } else {
                LOG.error("Error al ejecutar query {}", sqlQueries.get(SqlQuery.GET_PAGE_BY_ID), query.cause());
                resultHandler.handle(Future.failedFuture(query.cause()));
            }
        });
        return this;
    }

    @Override
    public WikiDbService fetchPageByName(final String name, final Handler<AsyncResult<JsonObject>> resultHandler) {
        String sqlQuery = sqlQueries.get(SqlQuery.GET_PAGE_BY_NAME);
        JsonArray params = new JsonArray().add(name);
        dbClient.queryWithParams(sqlQuery, params, query -> {
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
                resultHandler.handle(Future.succeededFuture(response));
            } else {
                LOG.error("Error al ejecutar query {}", sqlQueries.get(SqlQuery.GET_PAGE_BY_NAME), query.cause());
                resultHandler.handle(Future.failedFuture(query.cause()));
            }
        });
        return this;
    }

    @Override
    public WikiDbService createPage(final String id, final String name, final String creationDate,
                                    final Handler<AsyncResult<Void>> resultHandler) {
        String sqlQuery = sqlQueries.get(SqlQuery.CREATE_PAGE);
        JsonArray params = new JsonArray()
                .add(id)
                .add(name)
                .add("")
                .add(creationDate);

        dbClient.updateWithParams(sqlQuery, params, update -> {
            if(update.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                LOG.error("Error al ejecutar query {}", sqlQueries.get(SqlQuery.CREATE_PAGE), update.cause());
                resultHandler.handle(Future.failedFuture(update.cause()));
            }
        });
        return this;
    }

    @Override
    public WikiDbService savePage(final String id, final String content, final String updateDate,
                                  final Handler<AsyncResult<Void>> resultHandler) {
        JsonArray params = new JsonArray()
                .add(content)
                .add(updateDate)
                .add(id);

        dbClient.updateWithParams(sqlQueries.get(SqlQuery.UPDATE_PAGE), params, update -> {
            if(update.succeeded()) {
                resultHandler.handle(Future.succeededFuture());
            } else {
                LOG.error("Error al ejecutar query {}", sqlQueries.get(SqlQuery.UPDATE_PAGE), update.cause());
                resultHandler.handle(Future.failedFuture(update.cause()));
            }
        });
        return this;
    }

    @Override
    public WikiDbService deletePage(final String id, final Handler<AsyncResult<Void>> resultHandler) {
        fetchPageById(id, pageToDelete -> {
            if(pageToDelete.succeeded()) {
                JsonObject json = pageToDelete.result();

                LocalDateTime now = LocalDateTime.now();
                String name = json.getString("name") + "_deleted_"+now.hashCode();
                String deleteDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));

                JsonArray params = new JsonArray()
                        .add(name)
                        .add(deleteDate)
                        .add(id);

                dbClient.updateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), params, update -> {
                    if(update.succeeded()) {
                        resultHandler.handle(Future.succeededFuture());
                    } else {
                        LOG.error("Error al ejecutar query {}", sqlQueries.get(SqlQuery.DELETE_PAGE), update.cause());
                        resultHandler.handle(Future.failedFuture(update.cause()));
                    }
                });
            } else {
                LOG.error("Error al intentar obtener la pagina a borrar con id {}", id);
                resultHandler.handle(Future.failedFuture(pageToDelete.cause()));
            }
        });
        return this;
    }
}
