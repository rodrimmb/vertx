package es.rodrimmb.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public final class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> promise) {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.onComplete(promise);
    }

    private Future<Void> prepareDatabase() {
        Promise<Void> promise = Promise.promise();
        // (...)
        promise.complete();
        return promise.future();
    }

    private Future<Void> startHttpServer() {
        Promise<Void> promise = Promise.promise();
        // (...)
        vertx.createHttpServer()
                .requestHandler(req -> req.response().end("Hello Vert.x!"))
                .listen(8080);
        promise.complete();
        return promise.future();
    }
}
