package bio.anode.ai.agent;

import bio.anode.ai.config.LabaiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for autonomous agent work. Resolves the agent profile of a {@code claude-code} model and
 * runs it through {@link ClaudeCodeAdapter}.
 *
 * <p>Phase 3: runs are stateless, each in a throw-away workspace. Sessions (Phase 5) and asynchronous
 * executions (Phase 6) build on this class.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LabAgentService {

    private final LabaiProperties labaiProperties;
    private final AgentProperties agentProperties;
    private final ClaudeCodeAdapter adapter;

    /** Runs {@code prompt} with the agent profile of model {@code modelName}. Blocking. */
    public AgentOutcome run(String modelName, String prompt) {
        Path workspace = createWorkspace();
        try {
            var outcome = adapter.run(new ClaudeCodeAdapter.Invocation(
                    prompt,
                    labaiProperties.getModelId(modelName),
                    labaiProperties.getPrompt(modelName),
                    workspace,
                    labaiProperties.getAgent(modelName)));
            log.info("Agent run model={} turns={} costUsd={} durationMs={}",
                    modelName, outcome.numTurns(), outcome.costUsd(), outcome.durationMs());
            return outcome;
        } finally {
            deleteQuietly(workspace);
        }
    }

    private Path createWorkspace() {
        try {
            Files.createDirectories(agentProperties.workspaceRoot());
            return Files.createTempDirectory(agentProperties.workspaceRoot(), "run-");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create agent workspace under " + agentProperties.workspaceRoot(), e);
        }
    }

    private static void deleteQuietly(Path workspace) {
        try {
            FileSystemUtils.deleteRecursively(workspace);
        } catch (IOException e) {
            log.warn("Could not delete agent workspace {}", workspace, e);
        }
    }
}
