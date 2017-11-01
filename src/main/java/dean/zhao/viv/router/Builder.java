package dean.zhao.viv.router;

import dean.zhao.viv.handler.AbcHandler;
import dean.zhao.viv.handler.HandlerGetter;
import dean.zhao.viv.handler.IndexHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

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

        router.get("/index").handler(HandlerGetter.getHandler(IndexHandler.class)::accept);
        router.get("/abc").handler(HandlerGetter.getHandler(AbcHandler.class)::accept);
        return router;
    }
}
