package bio.anode.ai.controller.model.models;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Model(String id, int created, String object, String ownedBy) {
    public Model(String id) {
        this(id, 0, "model", "labai");
    }
}
