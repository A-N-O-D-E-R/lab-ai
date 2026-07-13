package bio.anode.ai.function;

import bio.anode.ai.config.LabaiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AutoResponder {

    private final LabaiProperties labaiProperties;

    public Optional<String> detect(String message) {
        if (message == null) return Optional.empty();

        var cleaned = clean(message);

        return labaiProperties.autoResponses().stream()
                .filter(LabaiProperties.AutoResponse::enabled)
                .filter(r -> r.triggers().contains(cleaned))
                .map(LabaiProperties.AutoResponse::response)
                .findFirst();
    }

    private String clean(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[\\p{Punct}\\s]", "")
                .toLowerCase();
    }
}
