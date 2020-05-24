package es.rodrimmb.wiki.http;

import com.github.rjeschke.txtmark.Processor;
import es.rodrimmb.wiki.database.WikiDbService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class HttpServerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerVerticle.class);

    // Parametros de configuracion del Verticle
    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private String wikiDbQueue = "wikidb.queue";

    private FreeMarkerTemplateEngine templateEngine;
    private WikiDbService dbService;

    @Override
    public void start(final Promise<Void> promise) throws Exception {
        wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
        dbService = WikiDbService.createProxy(vertx, wikiDbQueue);

        HttpServer server = vertx.createHttpServer();

        templateEngine = FreeMarkerTemplateEngine.create(vertx);

        Router router = Router.router(vertx);
        router.get("/hello").handler(this::helloHandler);
        router.get("/").handler(this::allPagesHandler);
        router.get("/wiki/:id").handler(this::pageHandler);
        // Todas las peticiones POST pasan primero por BodyHandler.create() que decodifica los body de estas peticiones,
        // es util para el envio de formularios
        router.post().handler(BodyHandler.create());
        router.post("/create").handler(this::createNewPageHandler);
        router.post("/save").handler(this::pageUpdateHandler);
        router.post("/delete").handler(this::pageDeleteHandler);

        int portNumbre = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        server
            .requestHandler(router)
            .listen(portNumbre, asyncResult -> {
                if(asyncResult.failed()) {
                    LOG.error("No se ha podido arrancar el servidor HTTP", asyncResult.cause());
                    promise.fail(asyncResult.cause());
                } else {
                    LOG.info("Servidor HTTP corriendo en el puerto 8080");
                    promise.complete();
                }
            });
    }

    private void helloHandler(final RoutingContext context) {
        context.response()
                .putHeader("Content-Type", "text/text")
                .end("Hello Vert.x!");
    }

    private void allPagesHandler(final RoutingContext context) {
        dbService.fetchAllPages(reply -> {
            if(reply.succeeded()) {
                context.put("title", "Wiki Home");
                context.put("pages", reply.result().getList());
                templateEngine.render(context.data(), "templates/index.ftl", asyncResult -> {
                    if(asyncResult.succeeded()) {
                        context.response()
                                .putHeader("Content-Type", "text/html")
                                .end(asyncResult.result());
                    } else {
                        LOG.error("No se ha podido renderizar bien la pagina de inicio", asyncResult.cause());
                        context.fail(asyncResult.cause());
                    }
                });
            } else {
                LOG.error("El servicio de DB no ha respondido correctamente", reply.cause());
                context.fail(reply.cause());
            }
        });
    }

    private static final String EMPTY_PAGE_MARKDOWN =
            "# A new page\n" +
                    "\n" +
                    "Feel-free to write in Markdown!\n";

    private void pageHandler(final RoutingContext context) {
        //Obtener la page con el id de la URL
        String id = context.request().getParam("id");
        dbService.fetchPageById(id, reply -> {
           if(reply.succeeded()) {
               JsonObject json = reply.result();
               String content = json.getString("content") == null ? "" : json.getString("content");
               context.put("title", "Edit page");
               context.put("id", json.getString("id"));
               context.put("name", json.getString("name"));
               context.put("content", content.isEmpty() ? Processor.process(EMPTY_PAGE_MARKDOWN) : Processor.process(content));
               context.put("rawContent", content.isEmpty() ? EMPTY_PAGE_MARKDOWN : content);
               templateEngine.render(context.data(), "templates/page.ftl", html -> {
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
               LOG.error("El servicio de DB no ha respondido correctamente", reply.cause());
               context.fail(reply.cause());
           }
        });
    }

    private void createNewPageHandler(final RoutingContext context) {
        String name = context.request().getParam("name").toLowerCase();

        //Primero buscamos si ya existe la pagina
        dbService.fetchPageByName(name, reply -> {
            if(reply.succeeded()) {
                JsonObject body = reply.result();
                if(body.getBoolean("found")) {
                    //Si existe dirigimos a su pagina
                    context.reroute(HttpMethod.GET, "/wiki/"+body.getString("id"));
                } else {
                    //Si no existe creamos la pagina
                    String id = UUID.randomUUID().toString();
                    String creationDate = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
                    dbService.createPage(id, name, creationDate, create -> {
                        if(create.succeeded()) {
                            context.reroute(HttpMethod.GET, "/wiki/"+id);
                        } else {
                            LOG.error("El servicio de DB no ha respondido correctamente", reply.cause());
                            context.fail(create.cause());
                        }
                    });
                }
            } else {
                LOG.error("El servicio de DB no ha respondido correctamente", reply.cause());
                context.fail(reply.cause());
            }
        });
    }

    private void pageUpdateHandler(final RoutingContext context) {
        String id = context.request().getParam("id");
        String content = context.request().getParam("markdown");
        String updateDate = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));

        dbService.savePage(id, content, updateDate, reply -> {
            if(reply.succeeded()) {
                context.response()
                        .setStatusCode(303)
                        .putHeader("Location", "/wiki/"+id)
                        .end();
            } else {
                LOG.error("El servicio de DB no ha respondido correctamente", reply.cause());
                context.fail(reply.cause());
            }
        });
    }

    private void pageDeleteHandler(final RoutingContext context) {
        String id = context.request().getParam("id");

        dbService.deletePage(id, reply -> {
            if(reply.succeeded()) {
                context.response()
                        .setStatusCode(303)
                        .putHeader("Location", "/")
                        .end();
            } else {
                LOG.error("El servicio de DB no ha respondido correctamente", reply.cause());
                context.fail(reply.cause());
            }
        });
    }
}
