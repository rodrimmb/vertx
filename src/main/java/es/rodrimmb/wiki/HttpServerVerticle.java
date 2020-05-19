package es.rodrimmb.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpServerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerVerticle.class);

    // Parametros de configuracion del Verticle
    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private String wikiDbQueue = "wikidb.queue";

    private FreeMarkerTemplateEngine templateEngine;

    @Override
    public void start(final Promise<Void> promise) throws Exception {
        wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

        HttpServer server = vertx.createHttpServer();

        templateEngine = FreeMarkerTemplateEngine.create(vertx);

        Router router = Router.router(vertx);
        router.get("/hello").handler(this::helloHandler);
        router.get("/").handler(this::allPagesHandler);
        router.get("/wiki/:id").handler(this::pageHandler);

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
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");

        vertx.eventBus().request(wikiDbQueue, new JsonObject(), options, reply -> {
            if(reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.put("title", "Wiki Home");
                context.put("pages", body.getJsonArray("pages").getList());
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
                LOG.error("La cola {} no ha respondido correctamente", wikiDbQueue, reply.cause());
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
        JsonObject message = new JsonObject().put("id", id);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-page-by-id");

        vertx.eventBus().request(wikiDbQueue, message, options, reply -> {
            if(reply.succeeded()) {
                JsonObject body = (JsonObject) reply.result().body();
                context.put("title", "Edit page");
                context.put("id", body.getString("id"));
                context.put("name", body.getString("name"));
                context.put("content", Processor.process(body.getString("content")));
                context.put("rawContent", body.getString("content").isEmpty() ? EMPTY_PAGE_MARKDOWN : body.getString("content"));
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
                LOG.error("La cola {} no ha respondido correctamente", wikiDbQueue, reply.cause());
                context.fail(reply.cause());
            }
        });
    }
}
