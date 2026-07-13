# labAI — Improvements Backlog

Honest assessment of gaps in labAI, focused on the OKF Knowledge Base + RAG feature
(Files API, Vector Stores API, OKF ingestion, `QuestionAnswerAdvisor` RAG). The happy path is
verified up to the embedding-provider call; several gaps below are unverified or known-wrong.

Status legend: 🔴 correctness/blocker · 🟠 security · 🟡 robustness/scale · 🔵 quality · ⚪ testing/ops

---

## Top 3 to do next
1. **Async attach with OpenAI-shaped status** (🔴 #1) — real clients expect `in_progress` polling, not a blocking 500.
2. **Delete-before-add on re-attach** (🔴 #2) — quick correctness fix; prevents duplicate chunks.
3. **Testcontainers integration test** (⚪ #14) — the embed→search→RAG path has never run green.

---

## Correctness / spec-compliance

### 🔴 1. Attach is synchronous and returns 500 on failure
OpenAI's `POST /vector_stores/{id}/files` is async: returns `200` immediately with `status: in_progress`;
the client polls `GET .../files/{id}` until `completed`/`failed`. Ours blocks until embedding finishes and
throws `500` on any embed error, so large files or transient provider errors break clients (incl. OpenWebUI).
- **Fix:** return `200` with the file object (`status`, `last_error`); run ingestion on a background
  executor; expose progress via the existing `VectorStoreFileEntity.status`.
- Files: `controller/VectorStoresController.attachFile`, `service/knowledge/KnowledgeStoreService.attachFile`.

### 🔴 2. Re-attaching a file duplicates chunks
`attachFile` does find-or-create the link then `vectorStore.add(chunks)` without removing the file's prior
vectors. Attaching the same file twice doubles the chunks and skews retrieval.
- **Fix:** delete-by-metadata (`vector_store_id` + `file_id`) before re-adding (reuse `deleteChunks(...)`).
- File: `service/knowledge/KnowledgeStoreService.attachFile`.

### 🔴 3. No pagination on list endpoints
`GET /files`, `GET /vector_stores`, `GET /vector_stores/{id}/files` return everything. OpenAI uses cursor
pagination (`limit`, `after`, `before`, `has_more`).
- **Fix:** add `limit`/`after` query params + `has_more` in the list envelope.

### 🔴 4. Provider errors collapse to generic `500 api_error`
An upstream `401`/`429`/timeout from the embedding provider surfaces as "An internal error occurred."
- **Fix:** map provider exceptions to meaningful status/type/code in `GlobalExceptionHandler`.

### 🔵 5. `GET /vector_stores/{id}/files/{fileId}/content` is 501
Spec returns the parsed/chunked content. Currently NotImplemented — acceptable for v1 but a gap.

---

## Security

### 🟠 6. No tenant isolation on knowledge bases
Any valid API key can read/search/delete **any** vector store; the client-supplied `vector_store_id` filter
is the only boundary. Data-leak risk if labAI is multi-user.
- **Fix:** scope `VectorStoreEntity`/`FileObjectEntity` to the authenticated principal (available from
  `ApiKeyCredentials` / `X-OpenWebUI-User-Email`); enforce ownership on every store/file operation.

### 🟠 7. No upload validation or quotas
No size cap beyond the 80MB reactive-codec limit, no content-type/extension allowlist, no per-key quota.
- **Fix:** explicit max-size + allowlist (`.md`/`.markdown`/`.zip`), reject early with `400`.

---

## Robustness / scale

### 🟡 8. Everything is in-memory + blocking
`@Lob byte[]` loads whole files into heap; multipart `readAllBytes()` buffers the entire upload; no streaming.
Large bundles OOM or hit the codec limit.
- **Fix:** stream uploads to storage; process bundle entries incrementally.

### 🟡 9. No embedding batching / rate-limit handling
A large bundle becomes one `vectorStore.add(hundreds of chunks)` → provider rate limits with no retry/backoff.
- **Fix:** batch `add` calls; add retry/backoff (Spring AI retry is on the classpath — `spring-ai-retry`).

### 🟡 10. `.tar`/`.tar.gz` bundles unsupported
OKF distributes as git repos / tarballs; only `.md` + `.zip` implemented (`.tar` needs `commons-compress`).

---

## Retrieval quality

### 🔵 11. Chunks lose their context
Raw body chunks are embedded without prepending OKF `title`/`type`/nearest heading, so a chunk like
`SELECT * FROM orders` has no anchor.
- **Fix:** prepend frontmatter title + section heading to each chunk before embedding.
- File: `service/knowledge/OkfParserService.parse`.

### 🔵 12. OKF structure is discarded
`resource`, cross-links, `# Citations`, and the `index.md` hierarchy are dropped — they could enrich
metadata and enable richer filtering/citation.

### 🔵 13. `similarityThreshold: 0.5` is a guess
Cosine-distance semantics mean this may filter out everything or nothing depending on the model; needs
tuning against real embeddings. Consider making it per-model and measuring.

### 🔵 14. QA-advisor vs memory-advisor ordering unvalidated with real retrieval
Only exercised up to the dummy-key boundary. Confirm the injected context lands before the model call and
composes correctly with `MessageChatMemoryAdvisor`.

---

## Testing / ops

### ⚪ 15. Only a parser unit test exists
No integration test (Testcontainers pgvector) exercising upload → attach → search → RAG-chat with a real
embedding model. The actual embedding + retrieval + injection path has never run green — biggest confidence gap.
- **Fix:** Testcontainers `pgvector/pgvector:pg17` + a stub/real EmbeddingModel; assert chunks persisted,
  search returns them, and chat injects retrieved context.

### ⚪ 16. `initializeSchema(true)` + fixed 1536 dimension
Auto-DDL is convenient for dev but risky in prod, and there's no migration path if the embedding model
changes (requires dropping `okf_vectors`).
- **Fix:** manage schema via migrations (Flyway/Liquibase) for non-dev; document the dimension constraint.

---

## Carry-over (pre-existing labAI gaps, not OKF-specific)
- MapStruct mapper emits "Unmapped target property" warnings (`function`, `id`) — harmless but noisy.
- `EmbeddingService` now uses `getIfUnique()`; if a second embedding provider is ever added it will 501
  rather than route per-model. Mirror the `ChatClientRegistry` provider-selection pattern when needed.

---

# PLANNED FEATURE: Tools (local `@Tool`) + OpenAPI toolserver discovery & usage

Not yet implemented — design ready to execute.

## Why
labAI already has the full Spring AI tool plumbing (`ChatService.getTools()` merges per-model + global tool
names, resolves via `ToolCallbackResolver`, attaches per-request with `.tools(List<ToolCallback>)`; the chat
response DTO + MapStruct mapper already surface `tool_calls`) — but **no tools are registered** (business tools
were stripped from atsia-igor). Add two capabilities:
1. **Local `@Tool` example tools** (atsia-igor pattern), generic / no business logic.
2. **OpenAPI toolserver discovery + invocation** — fetch a remote OpenAPI 3.0.1 spec (e.g. the sibling
   `toolserver-test-app` served by `spring-boot-starter-openapi-toolserver`), turn each operation into a Spring
   AI tool, and call it over HTTP when the model invokes it.

## Decisions
- Discovered tools are **global**: available to every chat model automatically (appended in `getTools()`
  regardless of per-model config). Local tools stay name-selected via `models.yaml tools:` + `labai.global-tools`.
- Discovery is **lazy with per-toolserver TTL refresh** from a config list, plus a manual refresh endpoint.
- labAI executes tools **server-side** (Spring AI runs the tool-call loop internally and returns the final
  assistant message) → **no inbound `tools` request-DTO change needed**; the gateway owns the tools.

## Verified APIs / facts (Spring AI 2.0, labAI classpath)
- Local tools: `@Tool`/`@ToolParam` methods → `@Bean ToolCallbackProvider` via
  `MethodToolCallbackProvider.builder().toolObjects(obj).build()`; default resolver resolves by method name.
- Dynamic tools: implement `ToolCallback` directly — `getToolDefinition()` →
  `ToolDefinition.builder().name(..).description(..).inputSchema(<JSON-schema string>).build()`;
  `call(String argsJson, ToolContext)` executes the HTTP request, returns the response body string.
  `ToolCallbackProvider` = `ToolCallback[] getToolCallbacks()` (+ `from(List)` factory).
- **HTTP client = `RestClient`** (spring-web 7.0.8, present via webflux) for discovery AND invocation.
  Synchronous `RestClient` is safe on the Netty event loop; **never `WebClient.block()`** (Spring AI runs tool
  callbacks synchronously — on the event loop during streaming — so `block()` would throw).
- OpenAPI contract (verified against `toolserver-test-app`): `GET {url}/openapi.json` (fallback `/v3/api-docs`)
  → OpenAPI 3.0.1 JSON. Each `paths.<path>.<method>` = one tool: name=`operationId`,
  description=`summary||description`, inputSchema from `parameters[]` (path/query/header) + `requestBody`
  `application/json` schema (resolve local `$ref` against `components.schemas`). Invoke: method + `servers[0].url`
  (resolve relative against config url) + path (substitute `{param}`) + query/header params + JSON body +
  configured static headers. No auth in contract → labAI adds per-toolserver static `headers`.
- **No new dependencies**: parse the spec with the existing Jackson 3 `JsonNode` (do NOT add swagger-parser —
  Jackson 2, heavy). `RestClient` + Spring AI tool classes already on the classpath.

## New files
- `config/LabaiToolserverProperties.java` — `@ConfigurationProperties("labai")` record exposing
  `List<Toolserver> toolservers`; `Toolserver(id, url, discoveryPath=/openapi.json, headers, ttl=10m)`
  (second `@ConfigurationProperties("labai")` bean binding disjoint keys — same pattern as `KnowledgeProperties`).
- `config/ToolConfig.java` — `@Bean ToolCallbackProvider exampleToolsProvider(ExampleTools)` via
  `MethodToolCallbackProvider` (mirror atsia-igor `ToolConfig`).
- `tools/ExampleTools.java` — `@Component`, 2-3 generic `@Tool` methods (`getCurrentTime`, `echo`, `calculate`).
- `tools/openapi/OpenApiOperation.java` — record: toolName, description, inputSchema (JSON string), httpMethod,
  baseUrl, pathTemplate, params (name/in/required), hasBody, staticHeaders.
- `tools/openapi/OpenApiSpecClient.java` — `RestClient` fetch of the spec (configured headers; discoveryPath then
  `/v3/api-docs` fallback) → `JsonNode`.
- `tools/openapi/OpenApiToolParser.java` — `JsonNode spec → List<OpenApiOperation>`: resolve server base
  (relative → `URI.resolve`), iterate paths×methods, build inputSchema (flatten `application/json` object body
  properties + params; resolve local `$ref` with visited-set/max-depth guard), sanitize names (`[a-zA-Z0-9_-]`),
  prefix with `serverId_` for cross-server uniqueness.
- `tools/openapi/OpenApiToolCallback.java` — `implements ToolCallback`; builds+executes the `RestClient` request;
  on HTTP error returns a JSON error string (model gets a usable result, chat never fails).
- `tools/openapi/OpenApiToolRegistry.java` — `@Component`, lazy discovery + per-server TTL:
  `ConcurrentHashMap<serverId, CacheEntry(discoveredAt, callbacks, lastError)>` + per-server `ReentrantLock`
  (double-checked inside lock). `allToolCallbacks()` (refresh-if-stale, flatten), `refresh()` (force), `status()`.
  Discovery failure → keep stale-or-empty entry, log, never propagate.
- `controller/ToolserversController.java` — `GET /api/v1/toolservers` (status), `POST /api/v1/toolservers/refresh`
  (wrap network call in `Mono.fromCallable(...).subscribeOn(boundedElastic())`). Auto-secured by `/api/**`.
- `src/test/java/bio/anode/ai/tools/openapi/OpenApiToolParserTest.java` — parse a sample spec (test-app shape:
  `/api/users` GET/POST, `/api/users/{id}` GET/DELETE, `User{id,name,email}`); assert 4 ops, path-param
  requiredness, flattened body props, resolved baseUrl.

## Edited files
- `App.java` — add `LabaiToolserverProperties.class` to `@EnableConfigurationProperties`.
- `service/ChatService.java` — inject `OpenApiToolRegistry`; rewrite `getTools()` = local tools (resolved by name,
  existing) **+** `openApiToolRegistry.allToolCallbacks()` (global), deduped by `getToolDefinition().name()`
  (local wins). Empty/unreachable → same no-op as today.
- `application.yml` — add `labai.global-tools: [getCurrentTime, echo, calculate]` and a `labai.toolservers:`
  example (`id: testapp, url: http://localhost:8080`).

Unchanged: pom.xml, ChatClientRegistry, SecurityConfig, chat request/response DTOs, mapper.

## Verification
1. `./mvnw -q test` (OpenApiToolParserTest needs no network/keys).
2. Run `toolserver-test-app` on :8080; configure `labai.toolservers`; boot labAI.
3. `GET /api/v1/toolservers` (Bearer magic-key) → 4 discovered tool names + lastRefresh — **verifies
   discovery/parsing without LLM keys**. `POST /api/v1/toolservers/refresh` → re-discovery.
4. With a provider key set, `POST /api/v1/chat/completions` prompt "list all users" → confirm (SimpleLoggerAdvisor
   / test-app access log) `testapp_getAllUsers` invoked and users JSON reached the model.
5. Local tools: prompt "what time is it?" → `getCurrentTime` invoked.

## Sequencing
1. `LabaiToolserverProperties` + register in `App.java` + `application.yml`.
2. `ExampleTools` + `ToolConfig` + add names to `global-tools`; build; verify local tool resolves.
3. OpenAPI subsystem: `OpenApiOperation` → `OpenApiToolParser` (+ unit test) → `OpenApiSpecClient` →
   `OpenApiToolCallback` → `OpenApiToolRegistry`.
4. Wire `ChatService.getTools()`; add `ToolserversController`.
5. End-to-end verify against `toolserver-test-app`.

## Risks
Relative server URLs (resolve against config url); `$ref` recursion (visited-set + max depth); event-loop
blocking (use `RestClient`, never `WebClient.block()`); tool-name collisions (sanitize + `serverId_` prefix +
`getTools()` dedupe); down toolserver (cache empty/stale + TTL backoff, never fail chat); TTL races (per-server
lock + double-check).
