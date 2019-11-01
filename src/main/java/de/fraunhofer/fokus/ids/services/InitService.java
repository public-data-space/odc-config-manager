package de.fraunhofer.fokus.ids.services;

import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import de.fraunhofer.fokus.ids.services.database.DatabaseServiceVerticle;
import de.fraunhofer.fokus.ids.services.docker.DockerService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

public class InitService {
    final io.vertx.core.logging.Logger LOGGER = LoggerFactory.getLogger(InitService.class.getName());

    DatabaseService databaseService;
    DockerService dockerService;

    public InitService(Vertx vertx, Handler<AsyncResult<Void>> resultHandler){
        this.databaseService = DatabaseService.createProxy(vertx, "de.fraunhofer.fokus.ids.databaseService");
        this.dockerService = DockerService.createProxy(vertx, "de.fraunhofer.fokus.ids.dockerService");
        initDB(reply -> {
            if(reply.succeeded()){
                resultHandler.handle(Future.succeededFuture());
//                initDockerService(reply2 -> {
//                    if(reply2.succeeded()){
//                        resultHandler.handle(Future.succeededFuture());
//                    }
//                    else{
//                        LOGGER.info("Docker preparations failed.", reply2.cause());
//                        resultHandler.handle(Future.failedFuture(reply2.cause()));
//                    }
//                });
            }
            else{
                LOGGER.info("Table creation failed.", reply.cause());
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
        })
//        .compose(id2 -> {
//            Future<List<JsonObject>> containers = Future.future();
//            databaseService.update("CREATE TABLE IF NOT EXISTS images (created_at, updated_at, imageId)", new JsonArray(), containers.completer());
//            return containers;
//    })
        .setHandler( ac -> {
            if(ac.succeeded()){
                resultHandler.handle(Future.succeededFuture());
            }
            else{
                LOGGER.info("Table creation failed.", ac.cause());
                resultHandler.handle(Future.failedFuture(ac.cause()));
            }
        });
    }

//    private void initDockerService(Handler<AsyncResult<Void>> resultHandler){
//            dockerService.findContainersInNetwork(reply -> {
//            if(reply.succeeded()){
//                for(Object containerId :reply.result().getList()){
//                    databaseService.update("INSERT INTO images (created_at, updated_at, imageId) values (DateTime('now'), DateTime('now'), ?)", new JsonArray().add(containerId.toString()), reply2 ->{});
//                }
//                resultHandler.handle(Future.succeededFuture());
//            }
//            else{
//                LOGGER.info("Table creation failed.", reply.cause());
//                resultHandler.handle(Future.failedFuture(reply.cause()));
//            }
//        });
//    }
}
