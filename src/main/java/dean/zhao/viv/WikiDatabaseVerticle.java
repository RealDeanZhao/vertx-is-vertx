package dean.zhao.viv;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class WikiDatabaseVerticle extends AbstractVerticle {
    public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
    public static final String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver.class";
    public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max.pool_size";
    public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";

    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    public enum ErrorCodes {
        NO_ACTION_SPECIFIED,
        BAD_ACTION,
        DB_ERROR
    }

    public void onMessage(final Message<JsonObject> message) {
        if (!message.headers().contains("action")) {
            LOGGER.error("No action header specified");
            message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
            return;
        }

        final String action = message.headers().get("action");

        switch (action) {
            case "all-pages":
                fetchAllPages(message);
                break;
            case "get-page":
                fetchPage(message);
                break;
            case "create-page":
                createPage(message);
                break;
            case "save-page":
                savePage(message);
                break;
            case "delete-page":
                deletePage(message);
                break;
            default:
                message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action:" + action);
        }
    }

    @Override
    public void start(final Future<Void> startFuture) throws Exception {
        loadSqlQueries();

        this.dbClient = JDBCClient.createShared(this.vertx, new JsonObject()
                .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
                .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
                .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)));

        this.dbClient.getConnection(car -> {
            if (car.failed()) {
                LOGGER.error("Could not open a db conneciton", car.cause());
                startFuture.fail(car.cause());
            } else {
                final SQLConnection connection = car.result();
                connection.execute(this.sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {
                    connection.close();
                    if (create.failed()) {
                        LOGGER.error("Cannot create pages table", create.cause());
                        startFuture.fail(create.cause());
                    } else {
                        this.vertx.eventBus().consumer(config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"),
                                this::onMessage);
                        startFuture.complete();
                    }
                });
            }
        });

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseVerticle.class);
    private JDBCClient dbClient;

    private enum SqlQuery {
        CREATE_PAGES_TABLE,
        ALL_PAGES,
        GET_PAGE,
        CREATE_PAGE,
        SAVE_PAGE,
        DELETE_PAGE
    }

    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    private void loadSqlQueries() throws IOException {
        final String queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE);

        final InputStream queriesInputStream;
        if (null != queriesFile) {
            queriesInputStream = new FileInputStream(queriesFile);
        } else {
            queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
        }

        final Properties queriesProps = new Properties();
        queriesProps.load(queriesInputStream);
        queriesInputStream.close();

        this.sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
        this.sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
        this.sqlQueries.put(SqlQuery.GET_PAGE, queriesProps.getProperty("get-page"));
        this.sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
        this.sqlQueries.put(SqlQuery.SAVE_PAGE, queriesProps.getProperty("save-page"));
        this.sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
    }

    private void fetchAllPages(final Message<JsonObject> message) {
        this.dbClient.query(this.sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
            if (res.failed()) {
                reportQueryError(message, res.cause());
            } else {
                final List<String> pages = res.result()
                        .getResults()
                        .stream()
                        .map(json -> json.getString(0))
                        .sorted()
                        .collect(Collectors.toList());
                message.reply(new JsonObject().put("pages", new JsonArray(pages)));
            }
        });
    }

    private void fetchPage(final Message<JsonObject> message) {
        final String page = message.body().getString("page");
        final JsonArray params = new JsonArray().add(page);

        this.dbClient.queryWithParams(this.sqlQueries.get(SqlQuery.GET_PAGE), params, fetch -> {
            if (fetch.failed()) {
                reportQueryError(message, fetch.cause());
            } else {
                final JsonObject response = new JsonObject();
                final ResultSet resultSet = fetch.result();
                if (resultSet.getNumRows() == 0) {
                    response.put("found", false);
                } else {
                    response.put("found", true);
                    final JsonArray row = resultSet.getResults().get(0);
                    response.put("id", row.getInteger(0));
                    response.put("rawContent", row.getString(1));
                }
                message.reply(response);
            }
        });
    }

    private void createPage(final Message<JsonObject> message) {
        final JsonObject request = message.body();
        final JsonArray data = new JsonArray()
                .add(request.getString("title"))
                .add(request.getString("markdown"));

        this.dbClient.updateWithParams(this.sqlQueries.get(SqlQuery.CREATE_PAGE), data, res -> {
            if (res.failed()) {
                message.reply("ok");
            } else {
                reportQueryError(message, res.cause());
            }
        });
    }


    private void savePage(final Message<JsonObject> message) {
        final JsonObject request = message.body();
        final JsonArray data = new JsonArray()
                .add(request.getString("markdown"))
                .add((request.getString("id")));

        this.dbClient.updateWithParams(this.sqlQueries.get(SqlQuery.SAVE_PAGE), data, res -> {
            if (res.failed()) {
                reportQueryError(message, res.cause());
            } else {
                message.reply("ok");
            }
        });
    }

    private void deletePage(final Message<JsonObject> message) {
        final JsonArray data = new JsonArray().add(message.body().getString("id"));

        this.dbClient.updateWithParams(this.sqlQueries.get(SqlQuery.DELETE_PAGE), data, res -> {
            if (res.failed()) {
                reportQueryError(message, res.cause());
            } else {
                message.reply("ok");
            }
        });
    }

    private void reportQueryError(final Message<JsonObject> message, final Throwable cause) {
        LOGGER.error("Database query error", cause);
        message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
    }
}
