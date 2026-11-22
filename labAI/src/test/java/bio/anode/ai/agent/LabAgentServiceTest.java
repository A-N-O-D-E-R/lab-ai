package bio.anode.ai.agent;

import bio.anode.ai.config.LabaiProperties.AgentProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabAgentServiceTest {

    @TempDir Path tmp;
    StubClaude stub;
    Path workspaces;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubClaude(Files.createDirectory(tmp.resolve("stub")));
        workspaces = tmp.resolve("workspaces");
    }

    private LabAgentService service(AgentProfile profile) {
        return new LabAgentService(StubClaude.properties(profile),
                new AgentProperties(workspaces, null, null, null),
                new ClaudeCodeAdapter(stub.agent));
    }

    @Test
    void runsHardenedHeadlessCliAndReturnsOutcome() throws Exception {
        var outcome = service(new AgentProfile(null, null, 7, new BigDecimal("0.50"))).run(StubClaude.MODEL, "hello");

        assertThat(outcome.text()).isEqualTo("echo:hello");
        assertThat(outcome.costUsd()).isEqualByComparingTo("0.012");
        assertThat(outcome.numTurns()).isEqualTo(2);

        List<String> args = stub.args();
        assertThat(args).doesNotContain("hello");
        assertThat(args.get(args.indexOf("--permission-mode") + 1)).isEqualTo("dontAsk");
        assertThat(args.get(args.indexOf("--tools") + 1)).isEmpty();
        assertThat(args).contains("--strict-mcp-config");
        assertThat(args.get(args.indexOf("--setting-sources") + 1)).isEmpty();
        assertThat(args.get(args.indexOf("--model") + 1)).isEqualTo("sonnet");
        assertThat(args.get(args.indexOf("--append-system-prompt") + 1)).isEqualTo("You are labAI.");
        assertThat(args.get(args.indexOf("--max-turns") + 1)).isEqualTo("7");
        assertThat(args.get(args.indexOf("--max-budget-usd") + 1)).isEqualTo("0.50");
        assertThat(args).doesNotContain("--mcp-config", "--resume", "--add-dir");
    }

    @Test
    void runsInsideWorkspaceRootAndCleansUp() throws Exception {
        service(AgentProfile.defaults()).run(StubClaude.MODEL, "hi");

        assertThat(stub.cwd().startsWith(workspaces.toRealPath())).isTrue();
        assertThat(stub.cwd()).doesNotExist();
    }

    @Test
    void cliFailureMapsToFailed() {
        stub.env.put("STUB_MODE", "fail");
        assertThatThrownBy(() -> service(AgentProfile.defaults()).run(StubClaude.MODEL, "hi"))
                .isInstanceOfSatisfying(AgentException.class,
                        e -> assertThat(e.reason()).isEqualTo(AgentException.Reason.FAILED))
                .hasMessageContaining("Not logged in");
    }

    @Test
    void errorResultWithZeroExitMapsToFailed() {
        stub.env.put("STUB_MODE", "is_error");
        assertThatThrownBy(() -> service(AgentProfile.defaults()).run(StubClaude.MODEL, "hi"))
                .isInstanceOfSatisfying(AgentException.class,
                        e -> assertThat(e.reason()).isEqualTo(AgentException.Reason.FAILED))
                .hasMessageContaining("error_max_turns");
    }

    @Test
    void profileTimeoutMapsToTimeout() {
        stub.env.put("STUB_MODE", "sleep");
        var profile = new AgentProfile(null, Duration.ofSeconds(1), null, null);
        assertThatThrownBy(() -> service(profile).run(StubClaude.MODEL, "hi"))
                .isInstanceOfSatisfying(AgentException.class,
                        e -> assertThat(e.reason()).isEqualTo(AgentException.Reason.TIMEOUT));
    }
}
