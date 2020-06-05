package de.fraunhofer.fokus.ids.main;

import de.fraunhofer.fokus.ids.services.InitService;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import de.fraunhofer.fokus.ids.services.database.DatabaseServiceVerticle;
import de.fraunhofer.fokus.ids.services.docker.DockerService;
import de.fraunhofer.fokus.ids.services.docker.DockerServiceVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
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
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.apache.http.entity.ContentType;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
/**
 * @author Vincent Bohlen, vincent.bohlen@fokus.fraunhofer.de
 */
public class MainVerticle extends AbstractVerticle {

    private static Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class.getName());
    private Router router;
    private DatabaseService databaseService;
    private DockerService dockerService;
    private int servicePort;
    private String apikey;

    private static final String LISTADAPTERS_QUERY = "SELECT name FROM adapters";
    private static final String EDIT_QUERY = "UPDATE adapters SET updated_at = ?, host = ?, port = ? WHERE name = ? ";
    private static final String FINDBYNAME_QUERY = "SELECT host, port FROM adapters WHERE name= ?";
    private static final String UNREGISTER_QUERY = "DELETE FROM adapters WHERE name=?";
    private static final String ADD_QUERY = "INSERT INTO adapters values(?,?,?,?,?)";

    @Override
    public void start(Future<Void> startFuture) {
        this.router = Router.router(vertx);

        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setWorker(true);

        Future<String> deployment = Future.succeededFuture();
        deployment
                .compose(id1 -> {
                    Future<String> databaseDeploymentFuture = Future.future();
                    vertx.deployVerticle(DatabaseServiceVerticle.class.getName(), deploymentOptions, databaseDeploymentFuture.completer());
                    return databaseDeploymentFuture;
                })
                .compose(id2 -> {
                    Future<String> dockerServiceFuture = Future.future();
                    vertx.deployVerticle(DockerServiceVerticle.class.getName(), deploymentOptions, dockerServiceFuture.completer());
                    return dockerServiceFuture;
                })
                .compose(id3 -> {
                    Future<String> envFuture = Future.future();
                    ConfigStoreOptions confStore = new ConfigStoreOptions()
                            .setType("env");
                    ConfigRetrieverOptions options = new ConfigRetrieverOptions().addStore(confStore);
                    ConfigRetriever retriever = ConfigRetriever.create(vertx, options);
                    retriever.getConfig(ar -> {
                        if (ar.succeeded()) {
                            servicePort = ar.result().getInteger("SERVICE_PORT");
                            apikey = ar.result().getString("API_KEY");
                            envFuture.complete();
                        } else {
                            envFuture.fail(ar.cause());
                        }
                    });
                    return envFuture;
                }).setHandler( ar -> {
                    if(ar.succeeded()){
                        this.databaseService = DatabaseService.createProxy(vertx, "de.fraunhofer.fokus.ids.databaseService");
                        this.dockerService = DockerService.createProxy(vertx, "de.fraunhofer.fokus.ids.dockerService");
                        new InitService(vertx, reply -> {
                            if(reply.succeeded()){
                                LOGGER.info("Initialization complete.");
                                createHttpServer();
                                startFuture.complete();
                            }
                            else{
                                LOGGER.error("Initialization failed.");
                                startFuture.fail(reply.cause());
                            }
                        });
                    }
                    else{
                        startFuture.fail(ar.cause());
                    }
                });
    }

    private void createHttpServer() {
        HttpServer server = vertx.createHttpServer();

        Set<String> allowedHeaders = new HashSet<>();
        allowedHeaders.add("x-requested-with");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("origin");
        allowedHeaders.add("authorization");
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("accept");
        allowedHeaders.add("X-PINGARUNER");

        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(HttpMethod.GET);
        allowedMethods.add(HttpMethod.POST);

        router.route().handler(CorsHandler.create("*").allowedHeaders(allowedHeaders).allowedMethods(allowedMethods));
        router.route().handler(BodyHandler.create());

        router.route("/getAdapter/:name").handler(routingContext ->
                auth(routingContext.request().headers().get("Authorization"), auth ->
                        getAdapter(auth, routingContext.request().getParam("name"), reply ->
                        reply(reply, routingContext.response()))));

        router.post("/register").handler(routingContext ->  register(routingContext.getBodyAsJson(), reply -> reply(reply, routingContext.response())));

        router.post("/edit/:name").handler(routingContext -> edit(routingContext.request().getParam("name"), routingContext.getBodyAsJson(), reply -> reply(reply, routingContext.response())));

        router.route("/images").handler(routingContext ->  auth(routingContext.request().headers().get("Authorization"),auth -> findImages(auth, reply -> reply(reply, routingContext.response()))));

        router.post("/images/start/").handler(routingContext ->  auth(routingContext.request().headers().get("Authorization"),auth -> startContainer(auth, routingContext.getBodyAsString(), reply -> reply(reply, routingContext.response()))));

        router.route("/listAdapters").handler(routingContext ->  auth(routingContext.request().headers().get("Authorization"),auth -> listAdapters(auth, reply -> reply(reply, routingContext.response()))));

        router.post("/images/stop/").handler(routingContext ->  auth(routingContext.request().headers().get("Authorization"), auth -> stopContainer(auth, routingContext.getBodyAsString(), reply -> reply(reply, routingContext.response()))));

        LOGGER.info("Starting Config manager");
        server.requestHandler(router).listen(servicePort);
        LOGGER.info("Config manager successfully started om port "+servicePort);
    }

    private void auth(String header, Handler<AsyncResult<Void>> next){
        LOGGER.info(header);
        if(header != null && header.startsWith("Bearer")){
            String passedKey = header.substring(header.indexOf(" ")).trim();
            if(passedKey.equals(apikey)){
                next.handle(Future.succeededFuture());
            } else {
                LOGGER.error("Apikey not valid.");
                next.handle(Future.failedFuture("Apikey not valid."));
            }
        } else {
            LOGGER.error("Wrong Apikey format.");
            next.handle(Future.failedFuture("Wrong Apikey format."));
        }
    }

    private void listAdapters(AsyncResult<Void> auth, Handler<AsyncResult<JsonArray>> resultHandler){
        if(auth.succeeded()) {
            this.databaseService.query(LISTADAPTERS_QUERY, new JsonArray(), reply -> {
                if (reply.succeeded()) {
                    resultHandler.handle(Future.succeededFuture(new JsonArray(reply.result())));
                } else {
                    LOGGER.error("Adapter names could not be retrieved.", reply.cause());
                    resultHandler.handle(Future.failedFuture(reply.cause()));
                }
            });
        } else {
            resultHandler.handle(Future.failedFuture(auth.cause()));
        }
    }

    private void startContainer(AsyncResult<Void> auth, String uuid, Handler<AsyncResult<JsonObject>> resultHandler){
        if(auth.succeeded()) {
            dockerService.startContainer(uuid, reply -> {
                if (reply.succeeded()) {
                    register(reply.result(), res -> {
                    });
                    JsonObject jO = new JsonObject();
                    jO.put("status", "success");
                    jO.put("text", "App wird gestartet...");
                    resultHandler.handle(Future.succeededFuture(jO));
                } else {
                    JsonObject jO = new JsonObject();
                    jO.put("status", "error");
                    jO.put("text", "App konnte nicht gestartet werden.");
                    resultHandler.handle(Future.succeededFuture(jO));
                }
            });
        } else {
            resultHandler.handle(Future.failedFuture(auth.cause()));
        }
    }

    private void stopContainer(AsyncResult<Void> auth, String uuid, Handler<AsyncResult<JsonObject>> resultHandler){
        if(auth.succeeded()) {
            dockerService.stopContainer(uuid, reply -> {
                if (reply.succeeded()) {
                    JsonObject jO = new JsonObject();
                    unregister(new JsonArray().add(reply.result().getJsonObject(0).getString("data")));
                    jO.put("status", "success");
                    jO.put("text", "App wird gestoppt...");
                    resultHandler.handle(Future.succeededFuture(jO));
                } else {
                    JsonObject jO = new JsonObject();
                    jO.put("status", "error");
                    jO.put("text", "App konnte nicht gestoppt werden.");
                    resultHandler.handle(Future.succeededFuture(jO));
                }
            });
        } else {
            resultHandler.handle(Future.failedFuture(auth.cause()));
        }
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

    private void findImages(AsyncResult<Void> auth, Handler<AsyncResult<JsonArray>> resultHandler){
        if(auth.succeeded()) {
            this.dockerService.findImages(reply -> {
                if (reply.succeeded()) {
                    resultHandler.handle(reply);
                } else {
                    LOGGER.error("Images could not be loaded.", reply.cause());
                    resultHandler.handle(Future.failedFuture(reply.cause()));
                }
            });
        } else {
            resultHandler.handle(Future.failedFuture(auth.cause()));
        }
    }

    private void edit(String name, JsonObject jsonObject, Handler<AsyncResult<JsonObject>> resultHandler ){
            this.databaseService.update(EDIT_QUERY, new JsonArray().add(new Date().toInstant()).add(jsonObject.getString("host")).add(jsonObject.getLong("port")).add(name), reply -> {
                if (reply.succeeded()) {
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

    private void getAdapter(AsyncResult<Void> auth, String name, Handler<AsyncResult<JsonObject>> resultHandler){
        if(auth.succeeded()) {
            this.databaseService.query(FINDBYNAME_QUERY, new JsonArray().add(name), reply -> {
                if (reply.succeeded()) {
                    try {
                        JsonObject jsonObject = new JsonObject()
                                .put("host", reply.result().get(0).getString("host"))
                                .put("port", reply.result().get(0).getLong("port"));
                        resultHandler.handle(Future.succeededFuture(jsonObject));
                    } catch (IndexOutOfBoundsException e) {
                        LOGGER.info("Queried adapter not registered.");
                        resultHandler.handle(Future.failedFuture("Queried adapter not registered."));
                    }
                } else {
                    LOGGER.error("Information for " + name + " could not be retrieved.", reply.cause());
                    resultHandler.handle(Future.failedFuture(reply.cause()));
                }
            });
        } else {
            resultHandler.handle(Future.failedFuture(auth.cause()));
        }
    }

    private void unregister(JsonArray jsonArray){
            this.databaseService.update(UNREGISTER_QUERY, jsonArray, reply -> {
                if (reply.succeeded()) {
                    LOGGER.info("delete succeeded");
                } else {
                    LOGGER.error(reply.cause());
                }
            });
    }

    private void register(JsonObject jsonObject, Handler<AsyncResult<JsonObject>> resultHandler){
            databaseService.query(FINDBYNAME_QUERY, new JsonArray().add(jsonObject.getString("name")), reply -> {
                if (reply.succeeded()) {
                    if (reply.result().size() > 0) {
                        edit(jsonObject.getString("name"), jsonObject.getJsonObject("address"), reply2 -> {
                            if (reply2.succeeded()) {
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
                    } else {
                        Date d = new Date();
                        this.databaseService.update(ADD_QUERY, new JsonArray()
                                .add(d.toInstant())
                                .add(d.toInstant())
                                .add(jsonObject.getString("name"))
                                .add(jsonObject.getJsonObject("address").getString("host"))
                                .add(jsonObject.getJsonObject("address").getLong("port")), reply3 -> {
                            if (reply3.succeeded()) {
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
                }
            });
    }
}
