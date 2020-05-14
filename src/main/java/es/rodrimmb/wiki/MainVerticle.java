package es.rodrimmb.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;

public final class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.onComplete(startFuture);
    }

    private Future<Void> prepareDatabase() {
        Promise<Void> promise = Promise.promise();
        // (...)
        return promise.future();
    }

    private Future<Void> startHttpServer() {
        Promise<Void> promise = Promise.promise();
        // (...)
        return promise.future();
    }
}
