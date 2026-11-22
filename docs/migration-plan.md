# Lab AI: Migration Plan

Target: make Claude Code (through `spring-ai-claude-code-starter`) the autonomous agent engine of Lab AI, expose the lab's OpenAPI tools to it over MCP, and keep everything else intact.

**Gate:** no application code changes until this plan is reviewed (see the open questions in §5).

Background:
- [architecture-analysis.md](architecture-analysis.md)
- [claude-code-integration.md](claude-code-integration.md)
- [mcp-tool-integration.md](mcp-tool-integration.md)

---

## 1. Target architecture

```
Open WebUI / API client
        │  Bearer key (+ trusted user header)
        ▼
labAI (Boot 4.1, Java 25, WebFlux)
  ├─ existing OpenAI gateway (Bedrock/Anthropic/OpenAI via Spring AI, RAG, files…)  ── unchanged
  ├─ ChatService ──(provider = claude-code)──▶ LabAgentService
  ├─ AgentController (/api/v1/agent/**)      ──▶ LabAgentService
  │        LabAgentService → ExecutionRunner → ClaudeCodeAdapter → ClaudeCodeAgent (starter)
  │                                                                     │ process
  │                                                              claude CLI (dontAsk, --tools "", strict MCP)
  │                                                                     │ MCP Streamable HTTP + exec token
  ├─ /mcp  McpToolGateway (authz, audit) ◀──────────────────────────────┘
  │        └─ OpenApiToolRegistry/Callback ──HTTP──▶ lab apps (openapi-toolserver starter)
  └─ PostgreSQL: existing tables + agent_session, agent_execution
```

---

## 2. Java 25 / Spring Boot 4.1 compatibility

| Item | Status | Action |
|---|---|---|
| Spring Boot 4.1.0 | Already used by labAI. A build exists and its tests pass. | None |
| Spring AI 2.0.x | Designed for Boot 4.0 and 4.1. labAI is on 2.0.0 and the starter's BOM on 2.0.2. | Align labAI to **2.0.2**. If labAI imports both BOMs, the first import wins, so pin explicitly. **Do not** use 2.1.0 (still a milestone, 2.1.0-M1). |
| Java 25 | labAI `java.version=21`. Dockerfile is already `temurin:25`. Local default JDK is 21, but 25 is available through sdkman (`25.0.2-oracle`, `25.0.2-graalce`). | Set `java.version` to `25` and build with JDK 25 (`sdk use java 25.0.2-oracle` or the `JAVA_HOME` used by CI) |
| Lombok on JDK 25 | Needs Lombok ≥ 1.18.40. The version managed by Boot 4.1 is expected to qualify. | The Phase 3 build verifies it |
| MapStruct 1.6.3 | Annotation processor, release-agnostic | Verify by build |
| JaCoCo 0.8.15 | Supports Java 25 class files | Verify by build |
| Jackson | Boot 4 uses Jackson 3. The starter uses Jackson 2. labAI already carries Jackson 2.21 transitively (AWS SDK). | Works as is. Migrating the starter to Jackson 3 is recommended but not blocking. |
| Starter parent Boot 4.0.1 / Java 21 | It is a library, so labAI's Boot 4.1 dependency management wins. Java 21 bytecode runs on 25. | `mvn install` the starter, or publish it to GitHub Packages. Pin `0.1.0-SNAPSHOT`, then a release. |
| toolserver starter Boot 3.3 / Java 17 | Never on labAI's classpath. HTTP contract only. | None. If the lab apps move to Boot 4, they need springdoc 3.x. |
| MCP | Spring AI 2.0 ships MCP Java SDK 2.0.0 and the WebFlux server transport | Confirm the artifact id when resolving dependencies |
| Virtual threads | Enabled. Since JDK 24 (JEP 491), `synchronized` no longer pins carriers, which helps blocking process waits. | None |
| Docker | The current image (`temurin:25-alpine` plus the jar) has no Node or Claude CLI | New image: `eclipse-temurin:25-jre` (Debian/Ubuntu), Node 22, the Claude CLI pinned through a build arg, a non-root user, and `/workspaces` and `/claude-home` volumes |

No version is downgraded. Everything requested (Java 25, Boot 4.1.x, Spring AI for Boot 4.1, Maven, PostgreSQL, Docker) is kept.

---

## 3. Phases

Each phase ends with a green `./mvnw verify` (JDK 25) and documentation updates.

### Phase 1: Repository analysis (done)
Deliverables: these four documents. No code changed.

### Phase 2: Architecture and migration plan (this document, under review)
Exit: open questions (§5) answered.

### Phase 3: Claude Code starter integration
- **Starter repo** (upstream, its own PR): the changes listed in `claude-code-integration.md` §6:
  - per-request options;
  - stdout drain;
  - stdin prompt;
  - process-tree cancellation and timeout exceptions;
  - typed cost and turn fields;
  - log level;
  - stub-CLI tests.
- **labAI:**
  - Set Java 25 and Spring AI 2.0.2.
  - Add the starter BOM and `spring-boot-starter-ai-claude` (agent only, no `ChatModel`).
  - Add the `labai.agent.*` properties.
  - Add `ClaudeCodeAdapter` and a minimal synchronous `LabAgentService.run`.
  - Add `Provider.CLAUDE_CODE`, routed in `ChatService`. `ChatClientRegistry` skips it.
- Tests: the stub CLI script, adapter flag mapping, error mapping, and a `chat/completions` contract test for a `claude-code` model.
- Affected: `labAI/pom.xml`, `LabaiProperties`, `ChatService`, `GlobalExceptionHandler`, new `agent/*`, `application.yml`, `models.yaml`.

**Status (2026-11-21): done.**
- Starter (`feat/per-request-options` branch, local, not pushed): `ClaudeCodeOptions`, prompt via stdin, concurrent pipe draining, `ClaudeCodeTimeoutException`/`ClaudeCodeCancelledException` with process-tree kill, typed `costUsd/numTurns/durationMs/subtype`, auth log at DEBUG, parent Boot 4.1.0. 18 tests green, incl. 300 KB output, timeout and interrupt using `stub-claude.sh`. Installed to `~/.m2` with `mvn install`.
- labAI: Java 25 (bytecode v69, Lombok/MapStruct/JaCoCo OK), Spring AI 2.0.2, `spring-boot-starter-ai-claude`, package `bio.anode.ai.agent` (`LabAgentService`, `ClaudeCodeAdapter`, `AgentProperties`, `ClaudeEnvironmentConfig`, `AgentOutcome`, `AgentException`), `Provider.CLAUDE_CODE` + `agent:` profile block, `labai-agent` model, error mapping (502/504/409). 18 tests green.
- Every run: `--permission-mode dontAsk --tools "" --strict-mcp-config --setting-sources ""`, prompt on stdin, throw-away workspace under `labai.agent.workspace-root`.
- Credentials: `CLAUDE_CODE_OAUTH_TOKEN` (preferred; removes the inherited `ANTHROPIC_API_KEY`) or `LABAI_AGENT_ANTHROPIC_API_KEY`. If neither is set, the CLI falls back to the inherited `ANTHROPIC_API_KEY` or its own login.
- Still stateless (history flattened into the prompt); sessions in Phase 5, async in Phase 6.

### Phase 4: MCP integration with existing tools
- Implement the `IMPROVEMENT.md` OpenAPI tool layer, with the `expose` allowlist added.
- Add `spring-ai-starter-mcp-server-webflux`, `McpToolGateway`, the `/mcp` security chain, and execution tokens.
- `ClaudeCodeAdapter` writes the MCP config and passes `--strict-mcp-config`, `--allowedTools` and `dontAsk`.
- `ChatService.getTools()` also uses the registry, so Spring AI providers gain tools too.
- Tests: parser, callback, `McpServerIT`, and the stub CLI MCP round trip.
- Affected: labAI only. The toolserver starter and test app are unchanged; the test app serves as the fixture.

### Phase 5: Session management
- `AgentSession` entity and repository, `--session-id`/`--resume`, workspaces, ownership, the Open WebUI chat-id mapping, the expiry job, and lost-transcript handling.
- Tests: create, resume, multiple sessions, expiry, ownership, Testcontainers PostgreSQL.

### Phase 6: Execution lifecycle
- `AgentExecution` entity, `ExecutionRunner` (bounded pool, per-user limit, 429), timeout guard, cancellation, restart recovery, MDC logging, and the async REST API (`202` plus polling).
- Optional 6b: SSE progress through `stream-json`. This needs streaming support in the starter, so it is deferred until it is asked for.
- Tests: timeout, cancel (process gone), queue full, recovery, concurrency of one run per session.

### Phase 7: Security and testing
- Per-key trusted-user flag and roles, profile roles, environment scrubbing, workspace path validation, audit logger, and the container hardening settings.
- Fill test gaps: the authorization matrix and a negative test for each control.
- Security review using the `security-review` skill.

### Phase 8: Documentation and deployment
- New Dockerfile and `compose.yaml` services (`labai` with volumes, Open WebUI with `WEBUI_AUTH=true`), secrets through env, and a README or runbook (CLI auth, volumes, upgrading the CLI version).
- Update `IMPROVEMENT.md`: close the tool feature and add the agent backlog.

---

## 4. What does not change
- The OpenAI-compatible API, DTOs, mapper, RAG, knowledge base, files, embeddings, images, audio, moderation and the 501 stubs.
- Non-Claude-Code chat providers and their Spring AI tool loop.
- Open WebUI as the UI. Only its auth setting changes for non-local deployments.
- The toolserver starter and lab apps (HTTP and OpenAPI contract).
- No RabbitMQ, no Kubernetes, no new microservice.

## 5. Review decisions (2026-11-21)
1. **Entry points:** both: Open WebUI `claude-code` models via `/chat/completions` AND async `/api/v1/agent/**`.
2. **Built-in tools:** none. Agent gets **MCP lab tools only** (`--tools ""`), secured per §3 of `mcp-tool-integration.md`.
3. **Identity:** forwarded Open WebUI user header accepted as owner, only for keys flagged `trust-forwarded-user`, Open WebUI with auth on.
4. **Schema:** **Liquibase**, starting with agent tables (existing tables baselined later, `IMPROVEMENT.md` #16).
5. **Starter:** changed upstream in `spring-ai-claude-code-starter`.
6. **Credentials:** mainly **OAuth token** (`CLAUDE_CODE_OAUTH_TOKEN`, from `claude setup-token`); API key supported as fallback. Note: consumer-subscription terms may not cover a multi-user server — owner's call.
7. **Mutating tools:** allowed in v1, all lab-data-changing operations exposable; still explicit `expose` allowlist + `mutating: true` + full-argument audit.

## 6. Original open questions
1. **Entry points:** both Open WebUI (`claude-code` model through `/chat/completions`) and the new async `/api/v1/agent/**` API? *Recommended: both. Open WebUI first, because it is the existing UX.*
2. **Built-in tools:** should agents get read-only file tools (`Read`, `Grep`, `Glob`) on their session workspace, or MCP tools only? *Recommended: MCP only by default, with per-profile opt-in. Never Bash.*
3. **Identity:** is the forwarded Open WebUI user header acceptable as the session owner, provided Open WebUI runs with auth and only its key is trusted?
4. **Schema management:** keep `ddl-auto` (the current practice) or introduce Flyway for the new tables (`IMPROVEMENT.md` #16)? *Recommended: Flyway, starting with the agent tables.*
5. **Starter changes upstream:** OK to change `spring-ai-claude-code-starter` (same owner) rather than work around it in labAI? *The mission requires this* ("do not reimplement CLI process management").
6. **Claude credentials:** a Console API key (recommended for a multi-user server) or an OAuth token?
7. **Mutating lab operations** (`createUser`-like): allowed at all in v1, or read-only tools only?
