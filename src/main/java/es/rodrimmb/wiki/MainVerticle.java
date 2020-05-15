package es.rodrimmb.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> promise) {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.onComplete(promise);
    }

    private JDBCClient dbClient;

    private static final String SQL_CREATE_PAGES_TABLE = "CREATE TABLE IF NOT EXISTS pages (id UUID UNIQUE PRIMARY KEY , name VARCHAR (255) UNIQUE , content TEXT)";

    private Future<Void> prepareDatabase() {
        Promise<Void> promise = Promise.promise();

        // Conexion compartida a la base de datos entre los Verticles que tengamos en Vert.x
        dbClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:postgresql://localhost:5432/rainbow_database")
                .put("user", "unicorn_user")
                .put("password", "magical_password")
                .put("driver_class", "org.postgresql.Driver")
                .put("max_pool_size", 30));

        dbClient.getConnection(asyncResult -> {
           if(asyncResult.failed()) {
               LOG.error("No podemos conectar a la DB", asyncResult.cause());
               promise.fail(asyncResult.cause());
           } else {
               SQLConnection connection = asyncResult.result();
               // A la conexion le pasamos el SQL que creara la tabla de PAGES
               connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
                   // Lo primero tras ejecutar el SQL es cerrar la conexión para liberarla y que otro la pueda usar
                   connection.close();
                   // Vemos si la operacion contra la DB ha ido bien
                   if(create.failed()) {
                       LOG.error("Error al crear la tabla", create.cause());
                       promise.fail(create.cause());
                   } else {
                       promise.complete();
                   }
               });
           }
        });

        return promise.future();
    }

    private Future<Void> startHttpServer() {
        Promise<Void> promise = Promise.promise();
        // (...)
        vertx.createHttpServer()
                .requestHandler(req -> req.response().end("Hello Vert.x!"))
                .listen(8080);
        promise.complete();
        return promise.future();
    }
}
