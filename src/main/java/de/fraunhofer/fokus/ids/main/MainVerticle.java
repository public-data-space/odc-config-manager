package de.fraunhofer.fokus.ids.main;

import de.fraunhofer.fokus.ids.services.InitService;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import de.fraunhofer.fokus.ids.services.database.DatabaseServiceVerticle;
import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.apache.http.entity.ContentType;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class MainVerticle extends AbstractVerticle {

    private static Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class.getName());
    private Router router;
    private DatabaseService databaseService;

    @Override
    public void start(Future<Void> startFuture) {
        this.router = Router.router(vertx);

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setWorker(true);

        vertx.deployVerticle(DatabaseServiceVerticle.class.getName(), deploymentOptions, reply -> {
            if(reply.succeeded()) {
                LOGGER.info("DatabaseService started");
                this.databaseService = DatabaseService.createProxy(vertx, "de.fraunhofer.fokus.ids.databaseService");
                new InitService(vertx, reply2 -> {
                    if(reply.succeeded()){
                        LOGGER.info("Initialization complete.");
                    }
                    else{
                        LOGGER.info("Initialization failed.");
                    }
                });
            }
            else{
                LOGGER.info("DatabaseService init failed.", reply.cause());
            }
        });

        createHttpServer();
    }

    private void createHttpServer() {
        HttpServer server = vertx.createHttpServer();

        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("origin");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("accept");
        allowedHeaders.add("X-PINGARUNER");

        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(HttpMethod.GET);
        allowedMethods.add(HttpMethod.POST);

        router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
        router.route().handler(BodyHandler.create());

        router.route("/getAdapter/:name").handler(routingContext ->
                getAdapter(routingContext.request().getParam("name"), reply ->
                        reply(reply, routingContext.response())));

        router.post("/register").handler(routingContext -> register(routingContext.getBodyAsJson(), reply -> reply(reply, routingContext.response())));

        router.post("/edit/:name").handler(routingContext -> edit(routingContext.request().getParam("name"), routingContext.getBodyAsJson(), reply -> reply(reply, routingContext.response())));

        LOGGER.info("Starting Config manager");
        server.requestHandler(router).listen(8094);
        LOGGER.info("Config manager successfully started.");
    }

    private void reply(AsyncResult result, HttpServerResponse response) {
        if (result.succeeded()) {
            if (result.result() != null) {
                String entity = result.result().toString();
                response.putHeader("content-type", ContentType.APPLICATION_JSON.toString());
                response.end(entity);
            } else {
                response.setStatusCode(404).end();
            }
        } else {
            response.setStatusCode(404).end();
        }
    }

    private void edit(String name, JsonObject jsonObject, Handler<AsyncResult<JsonObject>> resultHandler ){
        this.databaseService.update("UPDATE adapters SET updated_at = ?, address = ? WHERE name = ? ", new JsonArray().add(new Date().toInstant()).add(jsonObject).add(name), reply -> {
            if(reply.succeeded()){
                JsonObject jO = new JsonObject();
                jO.put("status", "success");
                jO.put("text", "Adapter wurde geändert.");
                resultHandler.handle(Future.succeededFuture(jO));
            } else {
                JsonObject jO = new JsonObject();
                jO.put("status", "error");
                jO.put("text", "Der Adapter konnte nicht geändert werden!");
                resultHandler.handle(Future.succeededFuture(jO));
            }
        });
    }

    private void getAdapter(String name, Handler<AsyncResult<JsonObject>> resultHandler){
        this.databaseService.query("SELECT address FROM adapters WHERE name= ?", new JsonArray().add(name), reply -> {
           if(reply.succeeded()){
               resultHandler.handle(Future.succeededFuture(new JsonObject(reply.result().get(0).getString("address"))));
           }
           else{
               LOGGER.info("Information for "+name+" could not be retrieved.", reply.cause());
               resultHandler.handle(Future.failedFuture(reply.cause()));
           }
        });

    }

    private void register(JsonObject jsonObject, Handler<AsyncResult<JsonObject>> resultHandler){

        Date d = new Date();
        this.databaseService.update("INSERT INTO adapters values(?,?,?,?)", new JsonArray()
                .add(d.toInstant())
                .add(d.toInstant())
                .add(jsonObject.getString("name"))
                .add(jsonObject.getJsonObject("address").toString()), reply -> {
                    if(reply.succeeded()){
                        JsonObject jO = new JsonObject();
                        jO.put("status", "success");
                        jO.put("text", "Adapter wurde registriert");
                        resultHandler.handle(Future.succeededFuture(jO));
                    } else {
                        JsonObject jO = new JsonObject();
                        jO.put("status", "error");
                        jO.put("text", "Der Adapter konnte nicht registriert werden!");
                        resultHandler.handle(Future.succeededFuture(jO));
                    }
                });
    }

    public static void main(String[] args) {
        String[] params = Arrays.copyOf(args, args.length + 1);
        params[params.length - 1] = MainVerticle.class.getName();
        Launcher.executeCommand("run", params);
    }

}
