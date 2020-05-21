package es.rodrimmb.wiki;

import es.rodrimmb.wiki.database.WikiDbVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> promise) {
        //Como el verticle que conecta con la DB debe estar arrancado antes que el servidor HTTP y los arranques son
        //asincronos debemos asegurarnos de que WikiDbVerticle se despliega antes que HttpServerVerticle. Para ello
        //usamos Promise y su metodo compose() que hace que hasta que una promesa no se a acabado no se ejecuta lo que
        //tenemos en el metodo compose() (future() asegura que la Promise a acabado de ejecutarse y ejecuta compose())
        Promise<String> dbVerticleDeployment = Promise.promise();
        vertx.deployVerticle(new WikiDbVerticle(), dbVerticleDeployment);

        dbVerticleDeployment.future().compose(id -> {
            Promise<String> httpVerticleDeployment = Promise.promise();
            vertx.deployVerticle(
                    "es.rodrimmb.wiki.http.HttpServerVerticle",
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
