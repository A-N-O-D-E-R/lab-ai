package bio.anode.ai.service;

import bio.anode.ai.controller.model.moderations.ModerationResponse;
import bio.anode.ai.support.NotImplementedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs {@code POST /api/v1/moderations} via the configured Spring AI {@link ModerationModel}
 * (OpenAI); returns 501 when none is available.
 */
@Service
@Slf4j
public class ModerationService {

    private final ObjectProvider<ModerationModel> moderationModel;

    public ModerationService(ObjectProvider<ModerationModel> moderationModel) {
        this.moderationModel = moderationModel;
    }

    public ModerationResponse moderate(JsonNode input, String model) {
        var mm = moderationModel.getIfAvailable();
        if (mm == null) {
            throw new NotImplementedException("No moderation provider is configured");
        }
        var text = normalize(input);
        var response = mm.call(new ModerationPrompt(text));
        var moderation = response.getResult().getOutput();

        var results = new ArrayList<ModerationResponse.Result>();
        for (ModerationResult r : moderation.getResults()) {
            results.add(new ModerationResponse.Result(
                    r.isFlagged(),
                    toCategories(r.getCategories()),
                    toScores(r.getCategoryScores())));
        }
        return new ModerationResponse(moderation.getId(), moderation.getModel(), results);
    }

    private String normalize(JsonNode input) {
        if (input == null || input.isNull()) {
            throw new IllegalArgumentException("'input' is required");
        }
        if (input.isArray()) {
            var sb = new StringBuilder();
            for (var n : input) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(n.asString());
            }
            return sb.toString();
        }
        return input.asString();
    }

    private Map<String, Boolean> toCategories(Categories c) {
        var m = new LinkedHashMap<String, Boolean>();
        if (c == null) return m;
        m.put("sexual", c.isSexual());
        m.put("hate", c.isHate());
        m.put("harassment", c.isHarassment());
        m.put("sexual/minors", c.isSexualMinors());
        m.put("hate/threatening", c.isHateThreatening());
        m.put("violence/graphic", c.isViolenceGraphic());
        m.put("harassment/threatening", c.isHarassmentThreatening());
        m.put("violence", c.isViolence());
        return m;
    }

    private Map<String, Double> toScores(CategoryScores s) {
        var m = new LinkedHashMap<String, Double>();
        if (s == null) return m;
        m.put("sexual", s.getSexual());
        m.put("hate", s.getHate());
        m.put("harassment", s.getHarassment());
        m.put("sexual/minors", s.getSexualMinors());
        m.put("hate/threatening", s.getHateThreatening());
        m.put("violence/graphic", s.getViolenceGraphic());
        m.put("harassment/threatening", s.getHarassmentThreatening());
        m.put("violence", s.getViolence());
        return m;
    }
}
