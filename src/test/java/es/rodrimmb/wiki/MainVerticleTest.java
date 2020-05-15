package es.rodrimmb.wiki;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;


@ExtendWith(VertxExtension.class)
class MainVerticleTest {

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @Test
    @DisplayName("🚀 Start a server and perform requests to /hello")
    void start_server(VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> {
            webClient.get(8080, "localhost", "/hello")
                    .as(BodyCodec.string())
                    .send(testContext.succeeding(resp -> {
                        testContext.verify(() -> {
                            assertEquals(resp.statusCode(), 200);
                            assertTrue("Hello Vert.x!".equalsIgnoreCase(resp.body()));
                            testContext.completeNow();
                        });
                    }));
        }));
    }

    @Test
    @DisplayName("📃 Start a server and perform requests main page")
    void return_html(VertxTestContext testContext) {
        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> {
            webClient.get(8080, "localhost", "/")
                    .as(BodyCodec.string())
                    .send(testContext.succeeding(resp -> {
                        testContext.verify(() -> {
                            assertEquals(resp.statusCode(), 200);
                            assertTrue(resp.body().contains("<h1 class=\"display-1\">Wiki home</h1>"));
                            assertTrue(resp.body().contains("<title>Home</title>"));
                            testContext.completeNow();
                        });
                    }));
        }));
    }

    @AfterEach
    void cleanup() {
        vertx.close();
    }

}