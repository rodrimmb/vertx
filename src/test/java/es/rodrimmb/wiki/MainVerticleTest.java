package es.rodrimmb.wiki;

import es.rodrimmb.wiki.database.WikiDbVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


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

    @Test
    @DisplayName("🎯 All CRUD operations in API")
    void api_crud(VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx, new WebClientOptions()
                        .setDefaultHost("localhost")
                        .setDefaultPort(8080));
        JsonObject jsonCreatePage = new JsonObject()
                .put("id", UUID.randomUUID().toString())
                .put("name", "CRUD API test");

        //Creo una pagina
        webClient.post("/api/pages")
                .as(BodyCodec.jsonObject())
                .sendJsonObject(jsonCreatePage, testContext.succeeding(createPage -> {
                    testContext.verify(() -> {
                        JsonObject responseCreatePage = createPage.body();
                        assertThat(createPage.statusCode(), is(200));
                        assertThat(responseCreatePage.getBoolean("success"), is(true));
                    });
                    //Obtengo todas las paginas para comprobar que esta la que he creado
                    webClient.get("/api/pages")
                            .as(BodyCodec.jsonObject())
                            .send(testContext.succeeding(getAllPages -> {
                                JsonObject responseGetAllPages = getAllPages.body();
                                String id = responseGetAllPages.getJsonArray("pages").getJsonObject(0).getString("id");
                                testContext.verify(() -> {
                                    assertThat(getAllPages.statusCode(), is(200));
                                    assertThat(responseGetAllPages.getBoolean("success"), is(true));
                                    assertThat(responseGetAllPages.getJsonArray("pages").getList().size(), is(1));
                                    assertThat(responseGetAllPages.getJsonArray("pages").getJsonObject(0).getString("id"), is(notNullValue()));
                                    assertThat(responseGetAllPages.getJsonArray("pages").getJsonObject(0).getString("name"), is("crud api test"));
                                });

                                JsonObject jsonUpdatePage = new JsonObject()
                                        .put("content", "# Content of test");

                                //Actualizo el contenido de la pagina que he creado
                                webClient.put("/api/pages/" + id)
                                        .as(BodyCodec.jsonObject())
                                        .sendJsonObject(jsonUpdatePage, testContext.succeeding(updatePage -> {
                                            testContext.verify(() -> {
                                                JsonObject responseUpdatePage = updatePage.body();
                                                assertThat(updatePage.statusCode(), is(200));
                                                assertThat(responseUpdatePage.getBoolean("success"), is(true));
                                            });

                                            //Obtengo la pagina actualizada
                                            webClient.get("/api/pages/" + id)
                                                    .as(BodyCodec.jsonObject())
                                                    .send(testContext.succeeding(getPage -> {
                                                        JsonObject jsonGetPage = getPage.body().getJsonObject("page");
                                                        testContext.verify(() -> {
                                                            assertThat(getPage.statusCode(), is(200));
                                                            assertThat(getPage.body().getBoolean("success"), is(true));
                                                            assertThat(jsonGetPage.getString("id"), is(id));
                                                            assertThat(jsonGetPage.getString("name"),
                                                                    is("crud api test"));
                                                            assertThat(jsonGetPage.getString("content"),
                                                                    is("# Content of test"));
                                                            assertThat(jsonGetPage.getString("html"),
                                                                    is("<h1>Content of test</h1>\n"));
                                                        });

                                                        //Borro la pagina
                                                        webClient.delete("/api/pages/" + id)
                                                                .as(BodyCodec.jsonObject())
                                                                .send(testContext.succeeding(deletePage -> {
                                                                    testContext.verify(() -> {
                                                                        JsonObject responseDeletePage = deletePage.body();
                                                                        assertThat(deletePage.statusCode(), is(200));
                                                                        assertThat(responseDeletePage.getBoolean("success"), is(true));
                                                                    });

                                                                    //Obtengo todas las paginas para ver que se ha borrado correctamente
                                                                    webClient.get("/api/pages")
                                                                            .as(BodyCodec.jsonObject())
                                                                            .send(testContext.succeeding(getAllPages2 -> {
                                                                                JsonObject responseGetAllPages2 = getAllPages2.body();
                                                                                testContext.verify(() -> {
                                                                                    assertThat(getAllPages2.statusCode(), is(200));
                                                                                    assertThat(responseGetAllPages2.getBoolean("success"), is(true));
                                                                                    assertThat(responseGetAllPages2.getJsonArray("pages").getList().size(), is(0));
                                                                                    testContext.completeNow();
                                                                                });
                                                                            }));
                                                                }));
                                                    }));
                                        }));
                            }));
                }));
    }
}