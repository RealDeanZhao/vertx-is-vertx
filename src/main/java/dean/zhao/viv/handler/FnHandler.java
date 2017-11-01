package dean.zhao.viv.handler;

import io.vertx.ext.web.RoutingContext;

import java.util.function.Consumer;

@FunctionalInterface
public interface FnHandler extends Consumer<RoutingContext> {

}
