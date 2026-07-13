package bio.anode.ai.service;

import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.config.LabaiProperties.ModelProperty;
import bio.anode.ai.dao.ModelRepository;
import bio.anode.ai.dao.jpa.ModelEntity;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;

/**
 * Registers/updates the configured models in the OpenWebUI {@code model} table so they appear in the
 * OpenWebUI frontend. Gated on {@code labai.openwebui.model-sync} (default true); disable it to run
 * the gateway without the OpenWebUI Postgres integration.
 */
@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "labai.openwebui.model-sync", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ModelAutoUpdater {

    private final LabaiProperties labaiProperties;

    private final ModelRepository modelRepository;

    private final JsonMapper jsonMapper;

    @EventListener(ApplicationReadyEvent.class)
    void updateModels() {
        labaiProperties.models().forEach(this::updateModel);
    }

    @SneakyThrows
    void updateModel(String modelId, ModelProperty modelProperty) {
        log.info("Updating model: {}", modelId);

        var m = modelRepository.findById(modelId).orElse(buildDefault(modelId));
        m.setName(StringUtils.defaultIfBlank(modelProperty.name(), modelId));
        var meta = jsonMapper.readTree(m.getMeta()).asObject();
        var params = jsonMapper.readTree(m.getParams()).asObject();
        var icon = new ClassPathResource("icone-" + modelId + ".png");

        if (null == meta.get("capabilities")) {
            var capabilities = meta.putObject("capabilities");
            capabilities.put("vision", true);
            capabilities.put("citations", true);
        }

        meta.put("description", modelProperty.description());
        if (icon.exists()) {
            var data = icon.getContentAsByteArray();
            var content = "data:image/png;base64,%s".formatted(Base64.getEncoder().encodeToString(data));

            meta.put("profile_image_url", content);
            log.info("Model {} has an icon: {}", modelId, icon.getFilename());
        }
        params.put("system", labaiProperties.getPrompt(modelId));
        m.setMeta(jsonMapper.writeValueAsString(meta));
        m.setParams(jsonMapper.writeValueAsString(params));
        var savedModel = modelRepository.save(m);
        log.debug("Model: {}", savedModel);
    }

    private ModelEntity buildDefault(String id) {
        var now = System.currentTimeMillis() / 1_000L;

        return ModelEntity.builder()
                .id(id)
                .name(id)
                .userId("xxx")
                .meta("{}")
                .params("{}")
                .createdAt(now)
                .updatedAt(now)
                .isActive(true)
                .build();
    }
}
