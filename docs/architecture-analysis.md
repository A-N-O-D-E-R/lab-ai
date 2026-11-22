# Lab AI: Architecture Analysis

Status: Phase 1 deliverable (analysis only, no code changed).
Date: 2026-11-21.
Inputs:
- `lab-ai` at commit `4cf17fb` (this repository).
- `spring-ai-claude-code-starter` at commit `cd85496` (`main`, single commit).
- Claude Code CLI `2.1.330`, installed locally.

---

## 1. Repository layout

The repository is **not a Maven multi-module build**. It holds three independent Maven projects and one git submodule. There is no aggregator POM.

| Path | What it is | Boot / Java | Build |
|---|---|---|---|
| `labAI/` | The Lab AI application: an OpenAI-compatible gateway | Boot **4.1.0**, `java.version` **21**, Spring AI **2.0.0** | `labAI/pom.xml` |
| `spring-boot-starter-openapi-toolserver/` | Starter that turns any Spring MVC app into an "OpenAPI toolserver" (`/openapi.json`) | Boot **3.3.1**, Java **17**, springdoc 2.5.0 | standalone |
| `toolserver-test-app/` | Demo app using the toolserver starter (`/api/users` CRUD) | Boot 3.3.1, Java 17 | standalone |
| `third_party/open-webui` | Open WebUI `v0.10.2`, git submodule | n/a | not built (compose pulls the image) |
| `.github/modernize/java-upgrade` | Leftover hook scripts from a Java-upgrade tool | n/a | not CI |

There is no CI workflow. `labAI/target/` holds a previous build. Its jar contains `spring-boot-4.1.0`, `spring-ai-model-2.0.0` and Jackson 3.1.4, and all **7 tests passed** (3 classes). So **Boot 4.1 + Spring AI 2.0 already builds and runs here**, but on Java 21 bytecode.

---

## 2. labAI: current architecture

labAI implements the OpenAI REST surface (`/api/v1/**`) in front of several LLM providers. Open WebUI is its primary client (see `labAI/compose.yaml`). The stack is WebFlux (reactive), with `spring.threads.virtual.enabled=true`.

```
Open WebUI ──HTTP (OpenAI API, Bearer key, X-OpenWebUI-User-Email)──▶ labAI
  labAI
   ├─ auth/        API-key auth (WebFlux security filter)
   ├─ controller/  OpenAI-shaped controllers (+ many 501 stubs)
   ├─ service/     ChatService, Embedding/Image/Audio/Moderation, knowledge/*
   ├─ chat/        ChatClientRegistry (1 ChatClient per provider), QaAdvisorFactory (RAG)
   ├─ dao/         JPA: files, vector stores, models
   └─ config/      LabaiProperties (labai.*), models.yaml registry
        │
        ├─ Spring AI ChatModels: Bedrock Converse, Anthropic, OpenAI
        └─ PostgreSQL + pgvector (files, vector-store metadata, okf_vectors)
```

### 2.1 Components

| Area | Classes | Notes |
|---|---|---|
| Entry point | `App` | Enables `LabaiProperties` and `KnowledgeProperties` |
| Security | `auth/SecurityConfig`, `ApiKeyServerAuthenticationConverter`, `ApiKeyReactiveAuthenticationManager`, `ApiKeyCredentials` | `Bearer <key>` checked against the `labai.apikeys` map (key to username). Only `/api/**` is authenticated. One role: `ROLE_API`. Stateless. |
| Model registry | `LabaiProperties`, `models.yaml` | Maps each OpenAI-facing model id to a provider, a native model id, a type, a prompt, an optional knowledge base and tools |
| Chat | `ChatCompletionController` → `ChatService` → `ChatClientRegistry` | Each provider has its own `ChatClient` with `MessageChatMemoryAdvisor` and `SimpleLoggerAdvisor`. Streaming uses SSE. |
| Memory | `CaffeineChatMemoryRepository` (1000 entries, 5 min) | In-memory only |
| RAG | `QaAdvisorFactory`, `service/knowledge/*` (`FileService`, `KnowledgeStoreService`, `OkfParserService`) | OKF markdown/zip ingestion into pgvector. Uses `QuestionAnswerAdvisor` per knowledge base. |
| Other OpenAI APIs | `Embeddings`, `Images`, `Audio` (OpenAI and Polly), `Moderations`, `Files`, `VectorStores`, `Models` | Implemented |
| Stubs (501) | `Assistants`, `Threads/Runs`, `Responses`, `Batches`, `FineTuning`, `Uploads`, `LegacyCompletion` | Routing and DTOs only |
| Canned replies | `function/AutoResponder` | Short-circuits greetings |
| Errors | `support/GlobalExceptionHandler` | Returns OpenAI-shaped error envelopes |
| Persistence | `dao/jpa/*`, PostgreSQL (`compose.yaml`: `pgvector/pgvector:pg17`) | Schema comes from `ddl-auto: update` (local) and `initializeSchema(true)` for pgvector. No migrations. |

### 2.2 Existing AI integrations
- Three Spring AI chat providers: Bedrock Converse, Anthropic API and OpenAI. They are dispatched per model id through `ChatClientRegistry.forModel`.
- Spring AI runs its own **tool-calling loop** inside `ChatClient` (`ChatService.getClient(...).tools(getTools(...))`).
- Embedding, image, transcription, speech (Polly) and moderation models.
- RAG through `QuestionAnswerAdvisor` on pgvector.
- There is **no Claude Code integration** in labAI today.

### 2.3 Existing tool integrations: the plumbing exists, but no tools do
- `ChatService.getTools()` resolves tool **names** from the model config (`tools:`) and `labai.global-tools` through Spring AI's `ToolCallbackResolver`.
- **No tool is registered.** There is no `@Tool`, no `ToolCallbackProvider` bean, and `global-tools` is empty. In practice `getTools()` always returns `[]`.
- **The OpenAPI toolserver client does not exist yet.** `labAI/IMPROVEMENT.md` ("PLANNED FEATURE: Tools …") holds a complete, verified design: `OpenApiSpecClient`, `OpenApiToolParser`, `OpenApiToolCallback` and `OpenApiToolRegistry`, using Jackson 3 and `RestClient` with no new dependencies. None of it is implemented.

> Consequence: "preserve existing tool implementations" covers two things: the toolserver starter, and the REST APIs of the lab apps that use it. No labAI-side tool code exists to preserve. The planned OpenAPI bridge in `IMPROVEMENT.md` becomes the **shared tool layer**. The MCP server and the existing Spring AI chat path will both use it.

### 2.4 Sessions today
- Each `/chat/completions` call creates a **random UUID** as the conversation id. `resetConversationMemory` then rebuilds the memory from the request's message list (Open WebUI resends the full history every time).
- So labAI is effectively **stateless per request**. No conversation is persisted, and nothing ties a session to a user.

### 2.5 Execution model today
- Non-streaming chat is computed inside the controller call. Because WebFlux runs it on an event-loop or virtual thread, long blocking work is a concern.
- Streaming uses `Flux` SSE.
- The only async-shaped feature planned is OKF ingestion (`IMPROVEMENT.md` #1). There is no executor, queue, cancellation or execution status anywhere.

### 2.6 Security observations (relevant to an agent that can execute things)
1. **The end-user identity is spoofable.** `ApiKeyReactiveAuthenticationManager` uses the `X-OpenWebUI-User-Email` header as the principal. Any API-key holder can claim to be any user. That is fine for a single trusted Open WebUI, but not for per-user agent sessions or workspaces.
2. There is a single role and no authorization beyond "has a key".
3. API keys sit in plain config (`application-local.yml`: `magic-key`).
4. Knowledge bases have no tenant isolation (`IMPROVEMENT.md` #6).
5. `compose.yaml` runs Open WebUI with `WEBUI_AUTH=false` and `BYPASS_MODEL_ACCESS_CONTROL=true`. That is acceptable for local dev, but **must not** be used once an agent model exists.

---

## 3. spring-boot-starter-openapi-toolserver

**What it does:** it is auto-configuration for **servlet** Spring Boot 3 apps. It adds:
- `GET /openapi.json`, which proxies springdoc's `/v3/api-docs` with a loopback `RestTemplate` call.
- `OpenApiEnhancer`, which prefixes summaries with `"Use this to "`, fills empty parameter descriptions, and adds a `servers[0]` of `http://localhost:<port>`.
- CORS on that path. The default is `*`.
- A startup banner.

**Does it support MCP? No.** It only publishes an OpenAPI document. Open WebUI consumes that natively as an "OpenAPI tool server". The starter does not expose MCP, does not invoke tools itself, and has no auth.

Observations:
- It works on Boot 3 only (springdoc 2.5 and servlet only). It **cannot go on labAI's classpath** (Boot 4, WebFlux). That is fine, because labAI only consumes it over HTTP.
- `servers[0].url` is hard-coded to `http://localhost:<port>`. That is wrong behind Docker or a reverse proxy, so labAI must resolve operation URLs against the **configured** toolserver URL. `IMPROVEMENT.md` already plans this.
- It has no notion of which operations are safe for an agent. It exposes every endpoint, including `DELETE /api/users/{id}`.
- The test app's spec gives 4 operations: `getAllUsers`, `getUserById`, `createUser` and `deleteUser`. That is a good fixture.

---

## 4. spring-ai-claude-code-starter

### 4.1 Modules and API
| Module | Content |
|---|---|
| `spring-ai-claude-dependencies` | BOM. Imports `spring-ai-bom` **2.0.2**. |
| `spring-ai-claude-core` | `ClaudeCodeAgent` (`run(ClaudeCodeRequest)`), `ClaudeCodeRequest(prompt, sessionId, workingDirectory, model, systemPrompt)`, `ClaudeCodeResult(text, sessionId, model, isError, raw JsonNode)`, `ClaudeCodeConfig`, `ClaudeCodeEnvironmentProvider`, `ClaudeCodeException`, `DefaultClaudeCodeAgent` (ProcessBuilder runner, no shell) |
| `spring-ai-claude-chat` | `ClaudeCodeChatModel`: a text-only Spring AI `ChatModel` adapter that flattens messages into one prompt |
| `spring-ai-claude-autoconfigure` | `spring.ai.claude-code.*` properties, and the `ClaudeCodeAgent` and `ChatModel` beans |
| `spring-boot-starter-ai-claude` / `-chat` | The starters |
| `examples/claude-chatbot` | Example app: OpenAI-compatible controller, credential store, Dockerfile with the CLI installed |

Coordinates: `com.anode.ai:*:0.1.0-SNAPSHOT`. Parent: Boot **4.0.1**, Java **21**. The artifacts are **not published** and are not in `~/.m2`.

### 4.2 Fit against the requirements (§4 of the mission)

| Requirement | Supported by the starter? |
|---|---|
| Agent execution | Yes, `run()`. It is blocking. |
| Session continuation | Partial: `--resume <id>` works. `--session-id <uuid>` (pre-assigning the id) is not supported. |
| Working directory | Yes, per request |
| Model | Yes, per request |
| Permission management | Global `permission-mode` only. There is no per-request `--allowedTools`, `--disallowedTools`, `--tools`, `--mcp-config`, `--strict-mcp-config` or `--add-dir`. |
| Timeouts | Global only |
| Error handling | `ClaudeCodeException` with a parsed failure message. `is_error` is reported. |
| Cancellation | Not exposed. Interrupting the calling thread does kill the CLI (`InterruptedException` → `destroyForcibly`), but the interrupt is reported as a generic "failed to execute", and **child processes** (Bash, stdio MCP servers) are not killed. |
| Structured results | Partial. Text, session id, model and `is_error` are typed. Cost, usage, `num_turns`, `duration_ms` and `subtype` are only in `raw`. |

### 4.3 Defects and risks found
1. **Possible pipe deadlock (bug).** The runner calls `waitFor(timeout)` and only reads **stdout after** that. If the JSON result is larger than the OS pipe buffer (~64 KB on Linux), the CLI blocks on write and the call ends as a spurious **timeout**. stderr is drained asynchronously, but stdout is not.
2. **Prompt passed as argv.** The prompt is limited to `MAX_ARG_STRLEN` (128 KB per argument on Linux) and is visible in `ps` to other local users. Stdin is the safer channel.
3. **Jackson 2.** The core uses `com.fasterxml.jackson`, but Boot 4 auto-configures Jackson 3 (`tools.jackson`). The auto-config's `ObjectProvider<com.fasterxml…ObjectMapper>` never finds a bean, so it creates `new ObjectMapper()`. This works, because labAI already ships Jackson 2.21 transitively through the AWS SDK, but it is a second JSON stack.
4. `ClaudeCodeChatModelAutoConfiguration` registers a `ChatModel` under `@ConditionalOnMissingBean(ChatModel.class)`. labAI has three `ChatModel`s, so whether it registers depends on ordering. labAI must set `spring.ai.claude-code.expose-chat-model=false` or use the agent-only starter.
5. The default `permission-mode: default` is **not in the CLI 2.1.330 documented choices**: `acceptEdits`, `auto`, `bypassPermissions`, `manual`, `dontAsk`, `plan`. A probe showed that `default` is still accepted as a hidden alias. For headless runs, `dontAsk` is the right mode: anything not pre-approved is denied.
6. Auth environment diagnostics are logged at INFO on every run. They show the first 12 characters of the key. That is noisy and should be DEBUG.
7. There is no concurrency limit, no streaming, and no health indicator. The README acknowledges all three.

### 4.4 Verified CLI capabilities (2.1.330, `claude --help`)
The integration plan relies on these flags:
- `-p`
- `--output-format json|stream-json`
- `--resume <id>`
- `--session-id <uuid>`
- `--fork-session`
- `--no-session-persistence`
- `--model`
- `--append-system-prompt`
- `--permission-mode`
- `--allowedTools` / `--disallowedTools`
- `--tools` (restricts built-in tools; `""` gives none)
- `--mcp-config <files|json>`
- `--strict-mcp-config`
- `--add-dir`
- `--settings`
- `--setting-sources`
- `--max-budget-usd`
- `--bare`

`--max-turns` is accepted but hidden.

---

## 5. Licenses

| Component | License | Impact |
|---|---|---|
| `lab-ai` (this repo) | **None** (no LICENSE file) | Defaults to "all rights reserved". Add one if it will be shared. |
| `spring-ai-claude-code-starter` | **None** | Same. The author is the same owner, so reuse is fine internally. |
| `spring-boot-starter-openapi-toolserver` | **None** | Same |
| Open WebUI v0.10.2 | **Open WebUI License** (BSD-3 style plus a branding-preservation clause) | We use the unmodified image. Do not strip the Open WebUI branding if it is ever rebranded beyond `WEBUI_NAME`. The clause has user-count and enterprise-license conditions, so review it before a large deployment. |
| Claude Code CLI | Proprietary, governed by Anthropic's terms | Installing it from npm in our image is normal usage. For a **multi-user server, authenticate with a Console API key** (`ANTHROPIC_API_KEY`) under commercial terms. Do not share one person's consumer subscription OAuth token across users. |
| Spring Boot / Spring AI / MCP Java SDK | Apache-2.0 | No constraints |

---

## 6. Summary: reuse, refactor, keep

**Reuse as-is**
- The whole OpenAI gateway: controllers, DTOs, mapper, chat for Bedrock/Anthropic/OpenAI, RAG, knowledge base, embeddings, images, audio and moderation.
- `LabaiProperties` / `models.yaml` as the single model and profile registry.
- API-key security as the outer authentication layer.
- PostgreSQL. It is already present, so it also stores agent sessions and executions.
- The toolserver starter and the lab apps' REST APIs, consumed over HTTP.
- `ClaudeCodeAgent` from the starter as the only process runner.

**Refactor or extend**
- **Starter (upstream, small):**
  - per-request options (allowed/disallowed tools, built-in tools, MCP config, timeout, permission mode, `--session-id`, extra dirs, budget);
  - stdout drained concurrently;
  - prompt via stdin;
  - cancellation that kills the process tree;
  - typed cost/turn fields;
  - log level.
- **labAI:**
  - Implement the planned OpenAPI tool layer.
  - Add an MCP server endpoint over it with an allowlist.
  - Add `LabAgentService` with sessions, executions and an executor.
  - Add a `CLAUDE_CODE` provider route in `ChatService`.
  - Make user identity trustworthy for agent use.
  - Bump to Java 25.
  - Change the Docker image so it contains the CLI.

**Keep unchanged**
- The Spring AI tool loop for non-Claude-Code models (no nesting).
- The OpenAI DTOs and error envelopes.
- The 501 stubs.
- The Open WebUI integration contract.
- The toolserver starter's code. Optional fixes are listed in `mcp-tool-integration.md` but are not required.

Detailed plans:
- [migration-plan.md](migration-plan.md)
- [claude-code-integration.md](claude-code-integration.md)
- [mcp-tool-integration.md](mcp-tool-integration.md)
