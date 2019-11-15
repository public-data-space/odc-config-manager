package de.fraunhofer.fokus.ids.services.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DockerClientBuilder;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.serviceproxy.ServiceBinder;
import org.apache.commons.lang.SystemUtils;
/**
 * @author Vincent Bohlen, vincent.bohlen@fokus.fraunhofer.de
 */
public class DockerServiceVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) {
        String localDockerHost = SystemUtils.IS_OS_WINDOWS ? "tcp://localhost:2375" : "unix:///var/run/docker.sock";
        DockerClient dockerClient = DockerClientBuilder.getInstance(localDockerHost).build();
        DatabaseService databaseService = DatabaseService.createProxy(vertx, "de.fraunhofer.fokus.ids.databaseService");
        DockerService.create(databaseService, dockerClient, ready -> {
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
    }
}

