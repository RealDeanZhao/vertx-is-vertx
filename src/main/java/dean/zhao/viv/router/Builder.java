package dean.zhao.viv.router;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.function.Consumer;

public class Builder {
    static Router router;

    private Builder() {
    }

    public static Router build(final Vertx vertx) {
        if (null == router) {
            synchronized (Builder.class) {
                if (null == router) {
                    router = Router.router(vertx);
                }
            }
        }

        final Consumer<RoutingContext> indexHandler = routingContext -> {
            final HttpServerResponse response = routingContext.response();
            response.putHeader("conetent-type", "text/plain");

            response.end("Yo, lai kankan vertx");
        };

        router.route().handler(indexHandler::accept);

        return router;
    }
}
