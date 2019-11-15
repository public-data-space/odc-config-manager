package de.fraunhofer.fokus.ids.services;

import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import de.fraunhofer.fokus.ids.services.docker.DockerService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

public class InitService {
    private final Logger LOGGER = LoggerFactory.getLogger(InitService.class.getName());

    private DatabaseService databaseService;
    private DockerService dockerService;

    public InitService(Vertx vertx, Handler<AsyncResult<Void>> resultHandler){
        this.databaseService = DatabaseService.createProxy(vertx, "de.fraunhofer.fokus.ids.databaseService");
        this.dockerService = DockerService.createProxy(vertx, "de.fraunhofer.fokus.ids.dockerService");
        initDB(reply -> {
            if(reply.succeeded()){
                resultHandler.handle(Future.succeededFuture());
            }
            else{
                LOGGER.error("Table creation failed.", reply.cause());
                resultHandler.handle(Future.failedFuture(reply.cause()));
            }
        });
    }

    private void initDB(Handler<AsyncResult<Void>> resultHandler) {
        Future<JsonObject> creation = Future.succeededFuture();
        creation.compose(id1 -> {
            Future<List<JsonObject>> adapter = Future.future();
            databaseService.update("CREATE TABLE IF NOT EXISTS adapters (created_at, updated_at, name, host, port)", new JsonArray(), adapter.completer());
            return adapter;
        }).compose( id2 -> {
            Future<List<JsonObject>> container = Future.future();
            databaseService.update("CREATE TABLE IF NOT EXISTS containers (created_at, updated_at, imageId, containerId)", new JsonArray(), container.completer());
            return container;
        })
        .setHandler( ac -> {
            if(ac.succeeded()){
                resultHandler.handle(Future.succeededFuture());
            }
            else{
                LOGGER.error("Table creation failed.", ac.cause());
                resultHandler.handle(Future.failedFuture(ac.cause()));
            }
        });
    }

}
