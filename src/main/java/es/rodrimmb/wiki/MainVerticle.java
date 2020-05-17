package es.rodrimmb.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public final class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> promise) {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.onComplete(promise);
    }

    private JDBCClient dbClient;

    private static final String SQL_CREATE_PAGES_TABLE = "CREATE TABLE IF NOT EXISTS pages " +
            "(id UUID UNIQUE PRIMARY KEY , name VARCHAR (255) UNIQUE , content TEXT, creation_date TIMESTAMP)";
    private static final String SQL_GET_ALL_PAGES = "SELECT name FROM pages";

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

    private FreeMarkerTemplateEngine templateEngine;

    private Future<Void> startHttpServer() {
        Promise<Void> promise = Promise.promise();
        HttpServer server = vertx.createHttpServer();

        templateEngine = FreeMarkerTemplateEngine.create(vertx);

        Router router = Router.router(vertx);
        router.get("/hello").handler(this::helloHandler);
        router.get("/").handler(this::allPagesHandler);

        server
            .requestHandler(router)
            .listen(8080, asyncResult -> {
                if(asyncResult.failed()) {
                    LOG.error("No se ha podido arrancar el servidor HTTP", asyncResult.cause());
                    promise.fail(asyncResult.cause());
                } else {
                    LOG.info("Servidor HTTP corriendo en el puerto 8080");
                    promise.complete();
                }
            });
        return promise.future();
    }

    private void helloHandler(final RoutingContext context) {
        context.response()
                .putHeader("Content-Type", "text/text")
                .end("Hello Vert.x!");
    }

    /**
     * Generar el html de la pagina inicial obteniendo las paginas de la DB
     *
     * @param context
     */
    private void allPagesHandler(final RoutingContext context) {
        dbClient.getConnection(asyncResult -> {
            if(asyncResult.succeeded()) {
                SQLConnection connection = asyncResult.result();
                connection.query(SQL_GET_ALL_PAGES, result -> {
                    connection.close();
                    if(result.succeeded()) {
                        List<String> pages = result.result()
                                .getResults()
                                .stream()
                                .map(json -> json.getString(0))
                                .sorted()
                                .collect(Collectors.toList());

                        context.put("title", "Home");
                        context.put("pages", pages);
                        templateEngine.render(context.data(), "templates/index.ftl", html -> {
                            if(html.succeeded()) {
                                context.response()
                                        .putHeader("Content-Type", "text/html")
                                        .end(html.result());
                            } else {
                                LOG.error("No se ha generado bien la pagina de inicio", html.cause());
                                context.fail(html.cause());
                            }
                        });
                    } else {
                        LOG.error("No se han podido obtener las paginas de la BD", result.cause());
                        context.fail(result.cause());
                    }
                });
            } else {
                context.fail(asyncResult.cause());
            }
        });
    }
}
