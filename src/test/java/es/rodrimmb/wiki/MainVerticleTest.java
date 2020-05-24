package es.rodrimmb.wiki;

import es.rodrimmb.wiki.database.WikiDbVerticle;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;


@ExtendWith(VertxExtension.class)
class MainVerticleTest {

    private Vertx vertx;

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        vertx = Vertx.vertx();
        JsonObject jsonConfig = new JsonObject()
                .put(WikiDbVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:wiki;shutdown=true")
                .put(WikiDbVerticle.CONFIG_WIKIDB_JDBC_DRIVER, "org.hsqldb.jdbcDriver")
                .put(WikiDbVerticle.CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE, "src/test/resources/db-queries-test.properties")
                .put(WikiDbVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);
        DeploymentOptions options = new DeploymentOptions().setConfig(jsonConfig);

        // Arrancamos la DB vacia con las configuraciones de HSQLDB y desplegamos el servidor
        vertx.deployVerticle(new MainVerticle(), options, testContext.completing());
    }

    @AfterEach
    void cleanup() {
        assertThat(vertx.deploymentIDs().size(), is(3));
        vertx.close();
    }

    @Test
    @DisplayName("🚀 Start a server and perform requests to /hello")
    void start_server(VertxTestContext testContext) {
        WebClient.create(vertx).get(8080, "localhost", "/hello")
                .as(BodyCodec.string())
                .send(testContext.succeeding(resp -> {
                    testContext.verify(() -> {
                        assertThat(resp.statusCode(), is(200));
                        assertThat(resp.body(), containsString("Hello Vert.x!"));
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    @DisplayName("🏠‍️ Start a server and perform requests main page")
    void return_html(VertxTestContext testContext) {
        WebClient.create(vertx).get(8080, "localhost", "/")
                .as(BodyCodec.string())
                .send(testContext.succeeding(resp -> {
                    testContext.verify(() -> {
                        assertThat(resp.statusCode(), is(200));
                        assertThat(resp.body(), containsString("<title>Wiki Home</title>"));
                        assertThat(resp.body(), containsString("The Wiki is empty! Create new page"));
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    @DisplayName("📃️ Add new page")
    void add_page(VertxTestContext testContext) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.set("name", "test");

        WebClient.create(vertx).post(8080, "localhost", "/create")
                .followRedirects(true)
                .as(BodyCodec.string())
                .sendForm(form, testContext.succeeding(resp -> {
                    testContext.verify(() -> {
                        assertThat(resp.statusCode(), is(200));
                        assertThat(resp.body(), containsString("<title>Edit page</title>"));
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    @DisplayName("📃️ Add new page and go to 🏠‍️ main page with list of pages")
    void add_page_and_go_home(VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);
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
    }
}