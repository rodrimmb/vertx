package es.rodrimmb.wiki.database;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ExtendWith(VertxExtension.class)
class WikiDbServiceTest {

    private Vertx vertx;
    private WikiDbService service;

    @BeforeEach
    void setUp(VertxTestContext testContext) {
        vertx = Vertx.vertx();
        JsonObject jsonConfig = new JsonObject()
                .put(WikiDbVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:wiki;shutdown=true")
                .put(WikiDbVerticle.CONFIG_WIKIDB_JDBC_DRIVER, "org.hsqldb.jdbcDriver")
                .put(WikiDbVerticle.CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE, "src/test/resources/db-queries-test.properties")
                .put(WikiDbVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);
        DeploymentOptions options = new DeploymentOptions().setConfig(jsonConfig);

        // Si hemos desplegado bien el verticle obtenemos el servicio
        vertx.deployVerticle(new WikiDbVerticle(), options, testContext.completing());
        service = WikiDbService.createProxy(vertx, WikiDbVerticle.CONFIG_WIKIDB_QUEUE);
    }

    @AfterEach
    public void finish() {
        vertx.close();
    }

    @Test
    @DisplayName("♻️ CRUD opetrations over DB service")
    void crud_operations(VertxTestContext testContext) {
        String id = UUID.randomUUID().toString();
        String name = "name";
        service.createPage(id, name, testContext.succeeding(v1 -> {
            service.fetchPageById(id, testContext.succeeding(json1 -> {
                testContext.verify(() -> {
                    assertThat(json1.getBoolean("found"), is(true));
                    assertThat(json1.getString("id"), is(id));
                    assertThat(json1.getString("name"), is(name));
                    assertThat(json1.getString("creation_date"), is(notNullValue()));
                });

                String content = "Some content";
                service.savePage(id, content, testContext.succeeding(v2 -> {
                    service.fetchAllPages(testContext.succeeding(array1 -> {
                        testContext.verify(() -> {
                            assertThat(array1.getList().size(), is(1));
                        });

                        service.fetchPageByName(name, testContext.succeeding(json2 -> {
                            testContext.verify(() -> {
                                assertThat(json2.getBoolean("found"), is(true));
                                assertThat(json2.getString("id"), is(id));
                                assertThat(json2.getString("name"), is(name));
                                assertThat(json2.getString("creation_date"), is(notNullValue()));
                                assertThat(json2.getString("update_date"), is(notNullValue()));
                            });

                            service.deletePage(id, testContext.succeeding(v3 -> {
                                service.fetchAllPages(testContext.succeeding(array2 -> {
                                    testContext.verify(() -> {
                                        assertThat(array2.isEmpty(), is(true));
                                        testContext.completeNow();
                                    });
                                }));
                            }));
                        }));
                    }));
                }));
            }));
        }));
    }
}