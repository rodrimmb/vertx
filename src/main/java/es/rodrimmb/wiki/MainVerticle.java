package es.rodrimmb.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private static final String SQL_GET_ALL_PAGES = "SELECT id, name FROM pages";
    private static final String SQL_GET_PAGE_BY_NAME = "SELECT * FROM pages WHERE name = ?";
    private static final String SQL_GET_PAGE_BY_ID = "SELECT * FROM pages WHERE id = uuid(?)";
    private static final String SQL_CREATE_PAGE = "INSERT INTO pages VALUES (uuid(?), ?, ?, TO_TIMESTAMP(?, 'YYYY-MM-DD HH24:MI:SS.US'))";

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
        router.get("/edit/:id").handler(this::editPageHandler);
        // Todas las peticiones POST pasan primero por BodyHandler.create() que decodifica los body de estas peticiones,
        // es util para el envio de formularios
        router.post().handler(BodyHandler.create());
        router.post("/create").handler(this::createNewPageHandler);

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
                        List<Map.Entry<String, String>> pages = result.result()
                                .getResults()
                                .stream()
                                .map(json -> Map.entry(json.getString(0), json.getString(1)))
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

    private void createNewPageHandler(final RoutingContext context) {
        String name = context.request().formAttributes().get("name").toLowerCase();

        dbClient.getConnection(asyncResult -> {
            // Buscamos en la BD si ya existe
            if(asyncResult.succeeded()) {
                SQLConnection connection = asyncResult.result();
                JsonArray params = new JsonArray();
                params.add(name);
                connection.queryWithParams(SQL_GET_PAGE_BY_NAME, params, query -> {
                    if(query.succeeded()) {
                        Optional<JsonArray> rowOptional = query.result().getResults().stream().findFirst();
                        if(rowOptional.isPresent()) {
                            connection.close();
                            // Si existe redirigimos a /edit
                            JsonArray row = rowOptional.get();
                            String id = row.getString(0);
                            context.response()
                                    .setStatusCode(303)
                                    .putHeader("Location", "/edit/"+id)
                                    .end();
                        } else {
                            // Si no exite lo creamos y redirigimos a /edit
                            String id = UUID.randomUUID().toString();
                            JsonArray paramsCreate = new JsonArray();
                            paramsCreate.add(id).add(name).add("")
                                    .add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")));
                            connection.updateWithParams(SQL_CREATE_PAGE, paramsCreate, create -> {
                                connection.close();
                                if(create.succeeded()) {
                                    context.response()
                                            .setStatusCode(303)
                                            .putHeader("Location", "/edit/"+id)
                                            .end();
                                } else {
                                    LOG.error("Error al insertar en la BD", create.cause());
                                    context.fail(create.cause());
                                }
                            });
                        }
                    } else {
                        LOG.error("Error al consultar en la BD", query.cause());
                        context.fail(query.cause());
                    }
                });
            } else {
                LOG.error("No se han podido conectar con la BD", asyncResult.cause());
                context.fail(asyncResult.cause());
            }
        });
    }

    private void editPageHandler(final RoutingContext context) {
        //Obtener la page con el id de la URL
        String id = context.request().getParam("id");

        //Generar plantilla
        dbClient.getConnection(asyncResult -> {
            if(asyncResult.succeeded()) {
                SQLConnection connection = asyncResult.result();
                JsonArray params = new JsonArray();
                params.add(id);
                connection.queryWithParams(SQL_GET_PAGE_BY_ID, params, query -> {
                    connection.close();
                    if(query.succeeded()) {
                        Optional<JsonArray> rowOpt = query.result().getResults().stream().findFirst();
                        if(rowOpt.isPresent()) {
                            JsonArray row = rowOpt.get();

                            context.put("title", "Edit page");
                            context.put("id", row.getString(0));
                            context.put("name", row.getString(1));
                            context.put("content", row.getString(2));
                            templateEngine.render(context.data(), "templates/edit.ftl", html -> {
                                if(html.succeeded()) {
                                    context.response()
                                            .putHeader("Content-Type", "text/html")
                                            .end(html.result());
                                } else {
                                    LOG.error("No se ha generado bien la pagina de edicion", html.cause());
                                    context.fail(html.cause());
                                }
                            });
                        } else {
                            LOG.warn("La pagina con id {} no existe", id);
                            context.response()
                                    .setStatusCode(303)
                                    .putHeader("Location", "/")
                                    .end();
                        }
                    } else {
                        LOG.error("Error al consultar en la BD", query.cause());
                        context.fail(query.cause());
                    }
                });
            } else {
                LOG.error("No se han podido conectar con la BD", asyncResult.cause());
                context.fail(asyncResult.cause());
            }
        });
    }
}
