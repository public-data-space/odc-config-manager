package de.fraunhofer.fokus.ids.services.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;
import io.vertx.serviceproxy.ServiceBinder;
import org.apache.commons.lang.SystemUtils;
/**
 * @author Vincent Bohlen, vincent.bohlen@fokus.fraunhofer.de
 */
public class DockerServiceVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) {
        ConfigStoreOptions confStore = new ConfigStoreOptions()
                .setType("env");
        ConfigRetrieverOptions options = new ConfigRetrieverOptions().addStore(confStore);
        ConfigRetriever retriever = ConfigRetriever.create(vertx, options);
        retriever.getConfig(ar -> {
            if (ar.succeeded()) {
                DatabaseService databaseService = DatabaseService.createProxy(vertx, "de.fraunhofer.fokus.ids.databaseService");
                WebClient webClient = WebClient.create(vertx);
                String dockerServiceHost = ar.result().getString("DOCKER_SERVICE_HOST");
                int dockerServicePort = ar.result().getInteger("DOCKER_SERVICE_PORT");
                DockerService.create(webClient, databaseService,dockerServicePort, dockerServiceHost, ready -> {
                    if (ready.succeeded()) {
                        ServiceBinder binder = new ServiceBinder(vertx);
                        binder
                                .setAddress("de.fraunhofer.fokus.ids.dockerService")
                                .register(DockerService.class, ready.result());
                        startFuture.complete();
                    } else {
                        startFuture.fail(ready.cause());
                    }
                });
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }
}

