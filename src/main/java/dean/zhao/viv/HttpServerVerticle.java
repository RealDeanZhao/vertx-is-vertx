package dean.zhao.viv;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;

import java.util.Date;

/**
 * @author Dean Zhao
 */
public class HttpServerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);
    private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();


    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private String wikiDbQueue = "wikidb.queue";

    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        this.wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

        final HttpServer server = this.vertx.createHttpServer();

        final Router router = Router.router(this.vertx);
        router.get("/").handler(this::indexHandler);
        router.get("/wiki/:page").handler(this::pageRenderingHandler);
        router.post().handler(BodyHandler.create());
        router.post("/save").handler(this::pageUpdateHandler);
        router.post("/create").handler(this::pageCreateHandler);
        router.post("/delete").handler((this::pageDeletionHandler));

        final int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 7777);

        server.requestHandler(router::accept)
                .listen(portNumber, ar -> {
                    if (ar.failed()) {
                        LOGGER.error("Cannot start the http server", ar.cause());
                        startFuture.fail(ar.cause());
                    } else {
                        LOGGER.info("Http server running on port {}", portNumber);
                        startFuture.complete();
                    }
                });
    }

    @Override
    public void stop() throws Exception {
        System.out.println("HTTP server stopped");
    }

    private void indexHandler(final RoutingContext context) {

        final DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");

        this.vertx.eventBus().send(this.wikiDbQueue, new JsonObject(), options, reply -> {
            if (reply.failed()) {
                context.fail(reply.cause());
            } else {
                final JsonObject body = (JsonObject) reply.result().body();

                context.put("title", "Wiki home");
                context.put("pages", body.getJsonArray("pages").getList());

                this.templateEngine.render(context, "templates", "/index.ftl", rar -> {

                    if (rar.failed()) {
                        context.fail(rar.cause());
                    } else {
                        context.response()
                                .putHeader("Content-Type", "text/html")
                                .end(rar.result());
                    }
                });

            }

        });
    }

    private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" +
            "\n" +
            "Feel-free to write in Markdown!\n";

    private void pageRenderingHandler(final RoutingContext context) {
        final String page = context.request().getParam("page");
        final JsonObject request = new JsonObject().put("page", page);

        final DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-page");

        this.vertx.eventBus().send(this.wikiDbQueue, request, options, reply -> {

            if (reply.failed()) {
                context.fail(reply.cause());
            } else {
                final JsonObject body = (JsonObject) reply.result().body();

                final boolean found = body.getBoolean("found");
                final String rawContent = body.getString("rawContent", EMPTY_PAGE_MARKDOWN);
                context.put("title", page)
                        .put("id", body.getInteger("id", -1))
                        .put("newPage", found ? "no" : "yes")
                        .put("rawContent", rawContent)
                        .put("content", Processor.process(rawContent))
                        .put("timestamp", new Date().toString());

                this.templateEngine.render(context, "templates", "/page.ftl", rar -> {
                    if (rar.failed()) {
                        context.fail(rar.cause());
                    } else {
                        context.response().putHeader("Content-Type", "text/html");
                        context.response().end(rar.result());
                    }
                });
            }
        });
    }

    private void pageCreateHandler(final RoutingContext context) {
        final String pageName = context.request().getParam("name");
        String location = "/wiki/" + pageName;
        if (null == pageName || pageName.isEmpty()) {
            location = "/";
        }
        context.response().setStatusCode(303);
        context.response().putHeader("Location", location);
        context.response().end();
    }

    private void pageUpdateHandler(final RoutingContext context) {
        final String id = context.request().getParam("id");
        final String title = context.request().getParam("title");
        final String markdown = context.request().getParam("markdown");
        final String newPage = context.request().getParam("newPage");

        final JsonObject request = new JsonObject()
                .put("id", id)
                .put("title", title)
                .put("markdown", markdown);

        final DeliveryOptions options = new DeliveryOptions();

        if ("yes".equals(newPage)) {
            options.addHeader("action", "create-page");
        } else {
            options.addHeader("action", "save-page");
        }

        this.vertx.eventBus().send(this.wikiDbQueue, request, options, reply -> {
            if (reply.failed()) {
                context.fail(reply.cause());
            } else {
                context.response()
                        .setStatusCode(303)
                        .putHeader("Location", "/wiki/" + title)
                        .end();
            }
        });
    }

    private void pageDeletionHandler(final RoutingContext context) {
        final String id = context.request().getParam("id");
        final JsonObject request = new JsonObject().put("id", id);
        final DeliveryOptions options = new DeliveryOptions().addHeader("action", "delete-page");
        this.vertx.eventBus().send(this.wikiDbQueue, request, options, reply -> {
            if (reply.failed()) {
                context.fail(reply.cause());
            } else {
                context.response()
                        .setStatusCode(303)
                        .putHeader("Location", "/")
                        .end();
            }
        });
    }
}
