package de.fraunhofer.fokus.ids.services.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import de.fraunhofer.fokus.ids.models.DockerImage;
import de.fraunhofer.fokus.ids.services.database.DatabaseService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DockerServiceImpl implements DockerService {

    DockerClient dockerClient;
    Set<String> knownImages = new HashSet<>();
    DatabaseService databaseService;

    public DockerServiceImpl(DatabaseService databaseService, DockerClient dockerClient, Handler<AsyncResult<DockerService>> readyHandler){
        this.dockerClient = dockerClient;
        this.databaseService = databaseService;
        knownImages.add("<none>");
        knownImages.add("maven");
        knownImages.add("node");
        readyHandler.handle(Future.succeededFuture(this));
    }

    @Override
    public DockerService findImages(Handler<AsyncResult<JsonArray>> resultHandler) {
        List<Image> images = dockerClient.listImagesCmd().exec();
        List<Container> containers = dockerClient.listContainersCmd().exec();
        databaseService.query("SELECT imageId FROM images", new JsonArray(), reply -> {
            if(reply.succeeded()){
                List<JsonObject> imageList = images.stream()
                        .filter(i -> !knownImages.contains(i.getRepoTags()[0].split(":")[0]))
                        .filter(i -> !reply.result()
                                    .stream()
                                    .map(ki -> ki.getString("imageId"))
                                    .collect(Collectors.toSet())
                                .contains(i.getId()))
                        .map(i -> {
                            DockerImage image = new DockerImage();
                            image.setName(i.getRepoTags()[0].split(":")[0]);
                            image.setId(i.getId());
                            image.setContainerIds(containers.stream()
                                    .filter(c ->c.getImageId().equals(i.getId()))
                                    .map(c -> c.getId())
                                    .collect(Collectors.toList()));
                            return new JsonObject(Json.encode(image));
                        }).collect(Collectors.toList());
                resultHandler.handle(Future.succeededFuture(new JsonArray(imageList)));
            }
            else{

            }
        });
        return this;
    }

    @Override
    public DockerService startContainer(String imageId, Handler<AsyncResult<JsonObject>> resultHandler) {
        Network idsNetwork = dockerClient.listNetworksCmd().withNameFilter("ids_connector").exec().get(0);
        Volume v = new Volume("/ids/repo/");
        CreateContainerResponse container = dockerClient.createContainerCmd(imageId).withVolumes(v).exec();
        dockerClient.startContainerCmd(container.getId()).exec();
        dockerClient.connectToNetworkCmd()
                .withNetworkId(idsNetwork.getId())
                .withContainerId(container.getId())
                .exec();
        String imageName = dockerClient.listImagesCmd().exec().stream().filter(i -> i.getId().equals(imageId)).findFirst().get().getRepoTags()[0].split(":")[0];
        JsonObject jO = new JsonObject();
        JsonObject address = new JsonObject();
        address.put("host",container.getId().substring(0,12));
        address.put("port", 8080);
        jO.put("name", imageName.toUpperCase());
        jO.put("address", address);

        resultHandler.handle(Future.succeededFuture(jO));
        return this;
    }

    @Override
    public DockerService stopContainer(JsonArray containerIds, Handler<AsyncResult<Void>> resultHandler) {
        for(Object containerId : containerIds) {
            dockerClient.stopContainerCmd(containerId.toString()).exec();
        }
        resultHandler.handle(Future.succeededFuture());
        return this;
    }

    @Override
    public DockerService findContainersInNetwork(Handler<AsyncResult<JsonArray>> resultHandler) {
        List<Container> containers = dockerClient.listContainersCmd().withNetworkFilter(Arrays.asList("ids_connector")).exec();
        resultHandler.handle(Future.succeededFuture(new JsonArray(containers.stream().map(c -> c.getImageId()).collect(Collectors.toList()))));
        return this;
    }

}
