package bio.anode.ai.agent;

import bio.anode.ai.config.LabaiProperties;
import bio.anode.ai.config.LabaiProperties.AgentProfile;
import bio.anode.ai.config.LabaiProperties.ModelProperty;
import bio.anode.ai.config.LabaiProperties.Provider;
import com.anode.ai.claude.ClaudeCodeConfig;
import com.anode.ai.claude.DefaultClaudeCodeAgent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test fixture: a real {@link DefaultClaudeCodeAgent} driving {@code stub-claude.sh} instead of the CLI. */
final class StubClaude {

    static final String MODEL = "labai-agent";

    final Path dir;
    final Map<String, String> env = new HashMap<>();
    final DefaultClaudeCodeAgent agent;

    StubClaude(Path dir) {
        this.dir = dir;
        Path executable = dir.resolve("claude");
        try (InputStream in = StubClaude.class.getResourceAsStream("/stub-claude.sh")) {
            Files.copy(in, executable);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!executable.toFile().setExecutable(true)) throw new IllegalStateException("cannot chmod stub");
        env.put("STUB_ARGS_FILE", dir.resolve("args").toString());
        env.put("STUB_CWD_FILE", dir.resolve("cwd").toString());
        var config = new ClaudeCodeConfig(executable.toString(), "sonnet", dir, Duration.ofMinutes(1), "default", 30, null);
        agent = new DefaultClaudeCodeAgent(config, new ObjectMapper(), () -> env);
    }

    List<String> args() throws IOException {
        return Files.readAllLines(dir.resolve("args"));
    }

    Path cwd() throws IOException {
        return Path.of(Files.readString(dir.resolve("cwd")).trim());
    }

    static LabaiProperties properties(AgentProfile profile) {
        var agentModel = new ModelProperty("Agent", null, Provider.CLAUDE_CODE, "sonnet", null, false, false,
                null, "You are labAI.", null, null, null, profile);
        var defaultModel = new ModelProperty(null, null, Provider.BEDROCK, "x", null, false, false,
                null, null, null, null, null, null);
        return new LabaiProperties(defaultModel, Map.of(MODEL, agentModel), null, null, null);
    }
}
