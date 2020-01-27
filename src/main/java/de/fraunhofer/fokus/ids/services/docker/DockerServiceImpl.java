package de.fraunhofer.fokus.ids.services.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.*;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
/**
 * @author Vincent Bohlen, vincent.bohlen@fokus.fraunhofer.de
 */
public class DockerServiceImpl implements DockerService {
    private Logger LOGGER = LoggerFactory.getLogger(DockerServiceImpl.class.getName());

    private WebClient webClient;
    String dockerServiceHost;
    int dockerServicePort;
    DatabaseService databaseService;

    public DockerServiceImpl(DatabaseService databaseService, WebClient webClient, int dockerServicePort, String dockerServiceHost, Handler<AsyncResult<DockerService>> readyHandler){
        this.webClient = webClient;
        this.dockerServiceHost = dockerServiceHost;
        this.dockerServicePort = dockerServicePort;
        this.databaseService = databaseService;
        readyHandler.handle(Future.succeededFuture(this));
    }

    private void post(int port, String host, String path, JsonObject payload, Handler<AsyncResult<JsonObject>> resultHandler) {
        webClient
                .post(port, host, path)
                .sendJsonObject(payload, ar -> {
                    if (ar.succeeded()) {
                        resultHandler.handle(Future.succeededFuture(ar.result().bodyAsJsonObject()));
                    } else {
                        LOGGER.error(ar.cause());
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
    }

    private void get(int port, String host, String path, Handler<AsyncResult<JsonObject>> resultHandler) {

        webClient
                .get(port, host, path)
                .send(ar -> {
                    if (ar.succeeded()) {
                        resultHandler.handle(Future.succeededFuture(ar.result().bodyAsJsonObject()));
                    } else {
                        LOGGER.error(ar.cause());
                        resultHandler.handle(Future.failedFuture(ar.cause()));
                    }
                });
    }

    @Override
    public DockerService findImages(Handler<AsyncResult<JsonArray>> resultHandler) {
        get(dockerServicePort, dockerServiceHost,"/images/", reply -> {
            if(reply.succeeded()) {
                resultHandler.handle(Future.succeededFuture(reply.result().getJsonArray("data")));
            } else {
                LOGGER.error(reply.cause());
                resultHandler.handle(Future.failedFuture(reply.cause()));
            }
        });
        return this;
    }

    @Override
    public DockerService startContainer(String uuid, Handler<AsyncResult<JsonObject>> resultHandler) {

        post(dockerServicePort, dockerServiceHost, "/images/start/", new JsonObject().put("data",uuid), reply -> {
            if (reply.succeeded()) {
                resultHandler.handle(Future.succeededFuture(reply.result()));
            } else {
                LOGGER.error(reply.cause());
                resultHandler.handle(Future.failedFuture(reply.cause()));
            }
        });
        return this;
    }

    @Override
    public DockerService stopContainer(String uuid, Handler<AsyncResult<JsonArray>> resultHandler) {
        post(dockerServicePort, dockerServiceHost, "/images/stop/", new JsonObject().put("data",uuid), reply -> {
            if (reply.succeeded()) {
                resultHandler.handle(Future.succeededFuture(new JsonArray().add(reply.result())));
            } else {
                LOGGER.error(reply.cause());
                resultHandler.handle(Future.failedFuture(reply.cause()));
            }
        });
        return this;
    }

}
