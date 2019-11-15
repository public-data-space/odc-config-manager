package de.fraunhofer.fokus.ids.models;

import java.util.List;
/**
 * @author Vincent Bohlen, vincent.bohlen@fokus.fraunhofer.de
 */
public class DockerImage {

    private String imageId;
    private String name;
    private List<String> containerIds;

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
