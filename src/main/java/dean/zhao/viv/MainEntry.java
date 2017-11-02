package dean.zhao.viv;

import io.vertx.core.Vertx;

public class MainEntry {
    public static void main(final String[] args) {
        final Vertx vtx = Vertx.vertx();
        vtx.deployVerticle(new MainVerticle());
    }
}
