package de.fraunhofer.fokus.ids.models;

import java.util.List;

public class DockerImage {

    String imageId;
    String name;
    List<String> containerIds;

    public List<String> getContainerIds() {
        return containerIds;
    }

    public void setContainerIds(List<String> containerIds) {
        this.containerIds = containerIds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return imageId;
    }

    public void setId(String id) {
        this.imageId = id;
    }
}
