package bio.anode.ai.controller.model.moderations;

import java.util.List;
import java.util.Map;

public record ModerationResponse(String id, String model, List<Result> results) {

    public record Result(boolean flagged,
                         Map<String, Boolean> categories,
                         Map<String, Double> categoryScores) {
    }
}
