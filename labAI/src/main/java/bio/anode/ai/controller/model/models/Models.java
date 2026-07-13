package bio.anode.ai.controller.model.models;

import java.util.List;

public record Models(String object, List<Model> data) {
    public Models(List<Model> data) {
        this("list", data);
    }
}
