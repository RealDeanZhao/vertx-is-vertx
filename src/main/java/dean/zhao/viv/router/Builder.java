package dean.zhao.viv.router;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;

public class Builder {
    static Router router;
    private Builder(){}
    public static Router build(Vertx vertx){
        if (router == null){
            router = Router.router(vertx);
        }

        router.route().handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response.putHeader("conetent-type", "text/plain");

            response.end("Yo, lai kankan vertx");
        });

        return router;
    }
}
