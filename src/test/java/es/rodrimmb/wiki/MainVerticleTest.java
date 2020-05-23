package es.rodrimmb.wiki;

import es.rodrimmb.wiki.database.WikiDbVerticle;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;


@ExtendWith(VertxExtension.class)
class MainVerticleTest {

    private Vertx vertx;

    private DeploymentOptions options;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        JsonObject jsonConfig = new JsonObject()
                .put(WikiDbVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki")
                .put(WikiDbVerticle.CONFIG_WIKIDB_JDBC_DRIVER, "org.hsqldb.jdbcDriver")
                .put(WikiDbVerticle.CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE, "src/test/resources/db-queries-test.properties");
        options = new DeploymentOptions().setConfig(jsonConfig);
    }

    @Test
    @DisplayName("🚀 Start a server and perform requests to /hello")
    void start_server(VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new MainVerticle(), options, testContext.succeeding(id -> {
            webClient.get(8080, "localhost", "/hello")
                    .as(BodyCodec.string())
                    .send(testContext.succeeding(resp -> {
                        testContext.verify(() -> {
                            assertThat(resp.statusCode(), is(200));
                            assertThat(resp.body(), containsString("Hello Vert.x!"));
                            testContext.completeNow();
                        });
                    }));
        }));
    }

    @Test
    @DisplayName("🏠‍️ Start a server and perform requests main page")
    void return_html(VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new MainVerticle(), options, testContext.succeeding(id -> {
            webClient.get(8080, "localhost", "/")
                    .as(BodyCodec.string())
                    .send(testContext.succeeding(resp -> {
                        testContext.verify(() -> {
                            assertThat(resp.statusCode(), is(200));
                            assertThat(resp.body(), containsString("<title>Wiki Home</title>"));
                            assertThat(resp.body(), containsString("The Wiki is empty! Create new page"));
                            testContext.completeNow();
                        });
                    }));
        }));
    }

    @Test
    @DisplayName("📃️ Add new page")
    void add_page(VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new MainVerticle(), options, testContext.succeeding(id -> {
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.set("name", "test");

            webClient.post(8080, "localhost", "/create")
                    .followRedirects(true)
                    .as(BodyCodec.string())
                    .sendForm(form, testContext.succeeding(resp -> {
                        testContext.verify(() -> {
                            assertThat(resp.statusCode(), is(200));
                            assertThat(resp.body(), containsString("<title>Edit page</title>"));
                            testContext.completeNow();
                        });
                    }));
        }));
    }

    @Test
    @DisplayName("📃️ Add new page and go to 🏠‍️ main page with list of pages")
    void add_page_and_go_home(VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);
        //Desplegamos el servidor y arrancamos la DB vacia
        vertx.deployVerticle(new MainVerticle(), options, testContext.succeeding(id -> {
            //Comprobamos que en la pagina principal el listado de paginas esta vacio
            webClient.get(8080, "localhost", "/")
                    .as(BodyCodec.string())
                    .send(testContext.succeeding(respMainPage -> {
                        testContext.verify(() -> {
                            assertThat(respMainPage.statusCode(), is(200));
                            assertThat(respMainPage.body(), containsString("<title>Wiki Home</title>"));
                            assertThat(respMainPage.body(), containsString("The Wiki is empty! Create new page"));
                        });

                        //Añadimos una pagina que se llama "test"
                        MultiMap form = MultiMap.caseInsensitiveMultiMap();
                        form.set("name", "test");
                        webClient.post(8080, "localhost", "/create")
                                .followRedirects(true)
                                .as(BodyCodec.string())
                                .sendForm(form, testContext.succeeding(respPost -> {
                                    testContext.verify(() -> {
                                        assertThat(respPost.statusCode(), is(200));
                                        assertThat(respPost.body(), containsString("<title>Edit page</title>"));
                                    });

                                    //Comprobamos que en la pagina principal el listado de paginas tiene la pagina que hemos añadido
                                    webClient.get(8080, "localhost", "/")
                                            .as(BodyCodec.string())
                                            .send(testContext.succeeding(resp -> {
                                                testContext.verify(() -> {
                                                    assertThat(resp.statusCode(), is(200));
                                                    assertThat(resp.body(), containsString("<title>Wiki Home</title>"));
                                                    assertThat(resp.body(), containsString(">test</a>"));
                                                    testContext.completeNow();
                                                });
                                            }));
                                }));
                    }));
        }));
    }

    @AfterEach
    void cleanup() {
        vertx.close();
    }

}