package dean.zhao.viv.handler;

import io.vertx.core.http.HttpServerResponse;

public class IndexHandler extends AbstractHandler {
    @Override
    void setHandler() {
        this.handler = routingContext -> {
            final HttpServerResponse response = routingContext.response();
            response.putHeader("conetent-type", "text/plain");

            response.end("Yo, lai kankan vertx");
        };
    }
}
