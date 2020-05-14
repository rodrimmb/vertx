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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;


@ExtendWith(VertxExtension.class)
class MainVerticleTest {

    private Vertx vertx;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
    }

    @Test
    @DisplayName("🚀 Start a server and perform requests")
    void start_server() {
        VertxTestContext testContext = new VertxTestContext();

        WebClient webClient = WebClient.create(vertx);
        vertx.deployVerticle(new MainVerticle(), testContext.succeeding(id -> {
            webClient.get(8080, "localhost", "/")
                    .as(BodyCodec.string())
                    .send(testContext.succeeding(resp -> {
                        testContext.verify(() -> {
                            assertEquals(resp.statusCode(), 200);
                            assertEquals(resp.body(), "Hello Vert.x!");
                            testContext.completeNow();
                        });
                    }));
        }));

        assertFalse(testContext.failed());
    }

    @AfterEach
    void cleanup() {
        vertx.close();
    }

}