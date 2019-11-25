package de.fraunhofer.fokus.ids.services.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.model.*;
import de.fraunhofer.fokus.ids.models.DockerImage;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
/**
 * @author Vincent Bohlen, vincent.bohlen@fokus.fraunhofer.de
 */
public class DockerServiceImpl implements DockerService {
    private Logger LOGGER = LoggerFactory.getLogger(DockerServiceImpl.class.getName());

    private DockerClient dockerClient;
    private Set<String> knownImages = new HashSet<>();
    private DatabaseService databaseService;
    private String prefix = "";

    public DockerServiceImpl(DatabaseService databaseService, DockerClient dockerClient, Handler<AsyncResult<DockerService>> readyHandler){
        this.dockerClient = dockerClient;
        this.databaseService = databaseService;
        knownImages.add("<none>");
        knownImages.add("maven");
        knownImages.add("node");
        knownImages.add("postgres");
        knownImages.add("nginx");
        readyHandler.handle(Future.succeededFuture(this));
    }

    @Override
    public DockerService findImages(Handler<AsyncResult<JsonArray>> resultHandler) {
        List<Image> images = dockerClient.listImagesCmd().exec();
        for(Image i : images){
            for(String t : i.getRepoTags()){
                if(t.contains("odc-config-manager")){
                    prefix = t.substring(0, t.indexOf("_"));
                }
            }
        }
        List<Container> containers = dockerClient.listContainersCmd().exec();
                List<JsonObject> imageList = images.stream()
                        .filter(i -> !knownImages.contains(i.getRepoTags()[0].split(":")[0]))
                        .filter(i -> !(i.getRepoTags().length==1 && i.getRepoTags()[0].startsWith(prefix)))
                        .map(i -> {
                            DockerImage image = new DockerImage();
                            image.setName(i.getRepoTags()[0].split(":")[0].toUpperCase());
                            image.setId(i.getId());
                            image.setContainerIds(containers.stream()
                                    .filter(c ->c.getImageId().equals(i.getId()))
                                    .map(c -> c.getId())
                                    .collect(Collectors.toList()));
                            return new JsonObject(Json.encode(image));
                        }).collect(Collectors.toList());
                resultHandler.handle(Future.succeededFuture(new JsonArray(imageList)));
        return this;
    }

    private void getContainer(String imageId, Handler<AsyncResult<String>> resultHandler){
        databaseService.query("SELECT * FROM containers WHERE imageId = ?", new JsonArray().add(imageId), reply -> {
            if(reply.succeeded()) {
                if(reply.result().size() == 0) {
                    Volume v = new Volume("/ids/repo/");
                    String containerId = dockerClient.createContainerCmd(imageId).withVolumes(v).withEnv("REPOSITORY=/ids/repo/").exec().getId();
                    Network idsNetwork = dockerClient.listNetworksCmd().withNameFilter("ids_connector").exec().get(0);
                    dockerClient.connectToNetworkCmd()
                            .withNetworkId(idsNetwork.getId())
                            .withContainerId(containerId)
                            .exec();
                    resultHandler.handle(Future.succeededFuture(containerId));
                    Instant d = new Date().toInstant();
                    databaseService.update("INSERT INTO containers values(?,?,?,?)", new JsonArray().add(d).add(d).add(imageId).add(containerId), insertReply -> {
                        if(insertReply.failed()){
                            LOGGER.error("Container could not be inserted into database.");
                        }
                    });
                } else{
                    resultHandler.handle(Future.succeededFuture(reply.result().get(0).getString("containerId")));
                }
            } else{
                LOGGER.error(reply.cause());
                resultHandler.handle(Future.failedFuture(reply.cause()));
            }
        });
    }

    @Override
    public DockerService startContainer(String imageId, Handler<AsyncResult<JsonObject>> resultHandler) {

        Future<String> containerIdFuture = Future.future();
        getContainer(imageId, containerIdFuture.completer());
        containerIdFuture.setHandler(ac -> {
            if(ac.succeeded()) {
                dockerClient.startContainerCmd(containerIdFuture.result()).exec();
                String imageName = dockerClient.listImagesCmd().exec().stream().filter(i -> i.getId().equals(imageId)).findFirst().get().getRepoTags()[0].split(":")[0];
                JsonObject jO = new JsonObject();
                JsonObject address = new JsonObject();
                address.put("host", containerIdFuture.result().substring(0, 12));
                address.put("port", 8080);
                jO.put("name", imageName.toUpperCase());
                jO.put("address", address);

                resultHandler.handle(Future.succeededFuture(jO));
            }
            else{
                resultHandler.handle(Future.failedFuture(ac.cause()));
            }
        });
        return this;
    }

    @Override
    public DockerService stopContainer(JsonArray containerIds, Handler<AsyncResult<JsonArray>> resultHandler) {
        JsonArray array = new JsonArray();
        for(Object containerId : containerIds) {
            dockerClient.stopContainerCmd(containerId.toString()).exec();
            array.add(containerId.toString().substring(0,12));
        }
        resultHandler.handle(Future.succeededFuture(array));
        return this;
    }

    @Override
    public DockerService findContainersInNetwork(Handler<AsyncResult<JsonArray>> resultHandler) {
        List<Container> containers = dockerClient.listContainersCmd().withNetworkFilter(Arrays.asList("ids_connector")).exec();
        resultHandler.handle(Future.succeededFuture(new JsonArray(containers.stream().map(c -> c.getImageId()).collect(Collectors.toList()))));
        return this;
    }

}
