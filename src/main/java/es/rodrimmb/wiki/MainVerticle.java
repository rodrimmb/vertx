package es.rodrimmb.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> promise) {
        Promise<String> dbVerticleDeployment = Promise.promise();
        vertx.deployVerticle(new WikiDbVerticle(), dbVerticleDeployment);

        dbVerticleDeployment.future().compose(id -> {
            Promise<String> httpVerticleDeployment = Promise.promise();
            vertx.deployVerticle(
                    "es.rodrimmb.wiki.HttpServerVerticle",
                    new DeploymentOptions().setInstances(2),
                    httpVerticleDeployment
            );

            return httpVerticleDeployment.future();
        }).onComplete(asyncResult -> {
            if (asyncResult.succeeded()) {
                LOG.info("Despliegue correcto");
                promise.complete();
            } else {
                LOG.error("Fallo al desplegar", asyncResult.cause());
                promise.fail(asyncResult.cause());
            }
        });
    }
}
