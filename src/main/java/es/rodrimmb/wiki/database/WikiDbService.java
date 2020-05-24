package es.rodrimmb.wiki.database;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;


@ProxyGen
@VertxGen
public interface WikiDbService {

    @Fluent
    WikiDbService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler);

    @Fluent
    WikiDbService fetchPageById(String id, Handler<AsyncResult<JsonObject>> resultHandler);

    @Fluent
    WikiDbService fetchPageByName(String name, Handler<AsyncResult<JsonObject>> resultHandler);

    @Fluent
    WikiDbService createPage(String id, String name, String creationDate, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDbService savePage(String id, String content, String updateDate, Handler<AsyncResult<Void>> resultHandler);

    @Fluent
    WikiDbService deletePage(String id, Handler<AsyncResult<Void>> resultHandler);

    @GenIgnore
    static WikiDbService create(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDbService>> readyHandler) {
        return new WikiDbServicePostgres(dbClient, sqlQueries, readyHandler);
    }

    @GenIgnore
    static WikiDbService createProxy(Vertx vertx, String address) {
        return new WikiDbServiceVertxEBProxy(vertx, address);
    }
}
