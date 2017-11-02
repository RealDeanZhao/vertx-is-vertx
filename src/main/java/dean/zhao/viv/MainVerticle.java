package dean.zhao.viv;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {
    @Override
    public void start(final Future<Void> startFutre) throws Exception {
        final Future<String> dbVerticleDeployment = Future.future();

        this.vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment.completer());

        dbVerticleDeployment.compose(id -> {
            final Future<String> httpVerticleDeployment = Future.future();
            this.vertx.deployVerticle("dean.zhao.viv.HttpServerVerticle",
                    new DeploymentOptions().setInstances(2),
                    httpVerticleDeployment.completer());

            return httpVerticleDeployment;
        }).setHandler(ar -> {
            if (ar.failed()) {
                startFutre.fail(ar.cause());
            } else {
                startFutre.complete();
            }
        });
    }
}
