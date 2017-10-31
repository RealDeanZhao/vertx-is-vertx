package dean.zhao.viv;

import dean.zhao.viv.router.Builder;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.Router;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        Router router = Builder.build(vertx);

        vertx.createHttpServer().requestHandler(router::accept).listen(7777);
        System.out.println("HTTP server started on port 7777");
    }

    @Override
    public void stop() throws Exception{
        System.out.println("HTTP server stopped");
    }
}
