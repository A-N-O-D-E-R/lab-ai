# Claude Code Integration Design

Covers how Lab AI runs Claude Code through `spring-ai-claude-code-starter`: sessions, execution management and security.

Related documents:
- MCP and tools: [mcp-tool-integration.md](mcp-tool-integration.md)
- Order of work: [migration-plan.md](migration-plan.md)

---

## 1. Principles
- **Claude Code owns the agent loop.** Lab AI never wraps it inside a Spring AI `ChatClient` tool loop, and never gives Spring AI tools to it directly. Tools reach Claude only through MCP.
- **The starter owns the process.** Lab AI never builds a `ProcessBuilder`. Missing CLI capabilities are added **upstream** in the starter (§6), not worked around in Lab AI.
- **Claude-specific types stay in one adapter class.** Business code talks to `LabAgentService` using Lab AI types.
- There are no new infrastructure components: no broker and no Kubernetes. It is one JVM, PostgreSQL and a bounded executor.

---

## 2. Components (labAI, new package `bio.anode.ai.agent`)

```
ChatCompletionController ─┐                       (Open WebUI, model provider = claude-code)
AgentController ──────────┤                       (/api/v1/agent/**, async API)
                          ▼
                    LabAgentService  ── profiles (models.yaml), sessions, executions, authz
                          │
            ┌─────────────┼───────────────────────┐
   AgentSessionRepository  AgentExecutionRepository  ExecutionRunner (bounded executor, futures, timeouts)
                          │
                    ClaudeCodeAdapter   ← only class importing com.anode.ai.claude.*
                          │
                    ClaudeCodeAgent (starter)  →  claude CLI  →  MCP (/mcp on labAI)
```

| Class | Responsibility |
|---|---|
| `LabAgentService` | Public API: `submit(user, sessionId?, profile, prompt)`, `get(executionId)`, `cancel(executionId)`, `sessions(user)`, `closeSession(id)`. It resolves the profile, checks ownership, creates or loads the session, and records executions. |
| `ExecutionRunner` | Bounded thread pool, global and per-user concurrency limits, timeout guard, a `Future` registry for cancellation, and recovery at startup |
| `ClaudeCodeAdapter` | Maps a profile, session and execution to a `ClaudeCodeRequest` (per-request options, MCP config file, `--session-id`/`--resume`). Maps `ClaudeCodeResult` or `ClaudeCodeException` to a Lab AI `AgentOutcome`. |
| `AgentSession`, `AgentExecution` | JPA entities, see §3 and §4 |
| `AgentController` | REST endpoints, see §5 |
| `AgentProperties` | Binds `labai.agent.*`: executable defaults, workspace root, limits, TTLs |

We keep exactly one `LabAgentService` class. An interface is added only if a second engine ever appears.

### 2.1 Agent profiles: configuration resolution
Profiles live in the existing `models.yaml`, so Open WebUI sees them as models:

```yaml
labai:
  models:
    labai-agent:
      name: labAI Agent (Claude Code)
      provider: claude-code          # new LabaiProperties.Provider value
      model-id: sonnet               # passed as --model
      type: chat
      prompt: |                      # --append-system-prompt
        You are labAI, the lab assistant. Use the lab tools to answer.
      agent:                         # new optional block, ignored by other providers
        mcp-tools: [testapp_getAllUsers, testapp_getUserById]   # allowlist, see mcp doc
        builtin-tools: []            # --tools ""  (no Bash/Edit/Read by default)
        timeout: 5m
        max-turns: 20
        max-budget-usd: 1.00
        roles: [ROLE_API]            # who may use this profile
```

`LabaiProperties.ModelProperty` gets one new nullable field, `agent`. Existing models are unaffected.

---

## 3. Sessions

### 3.1 Model
Table `agent_session`:

| Column | Notes |
|---|---|
| `id` (uuid, PK) | **Lab AI session id**, returned to clients |
| `owner` | Authenticated principal (§7.1) |
| `profile` | Model or profile id |
| `claude_session_id` (uuid) | **Provider session id**. Never exposed as the Lab AI id. |
| `workspace` | Absolute path, `<root>/<owner-hash>/<session-id>` |
| `status` | `ACTIVE`, `EXPIRED` or `CLOSED` |
| `created_at`, `last_used_at`, `expires_at` | Times |
| `external_ref` | Optional, for example the Open WebUI chat id |

Session history is the list of `agent_execution` rows for the session, each with its prompt and result. We do not keep a separate message table: Claude Code keeps the authoritative transcript itself.

### 3.2 Lifecycle
1. **Create.** Generate the Lab AI `id` and a fresh `claude_session_id`, then create the workspace directory. The first run passes **`--session-id <claude_session_id>`**. Because we choose the id, a failed or cancelled first run still leaves a consistent row.
2. **Continue.** Later runs pass `--resume <claude_session_id>` with the **same working directory and the same `CLAUDE_CONFIG_DIR`**. Claude stores transcripts per config dir and project path, so both live on persistent volumes.
3. **Multiple sessions.** A user can hold N sessions (configurable cap). There is **at most one RUNNING execution per session**, enforced in `LabAgentService` and backed by a partial unique index on `agent_execution(session_id) WHERE status IN ('QUEUED','RUNNING')`. Two concurrent `--resume` runs on one transcript would corrupt it.
4. **Expiry.** A `@Scheduled` job marks sessions `EXPIRED` once they are idle past `labai.agent.session-ttl` (default 7 days). It then deletes the workspace and the Claude transcript. A request on an expired session returns `410 Gone` with code `session_expired`, and the client creates a new one.
5. **Lost transcript.** If `--resume` fails with "no conversation found" (volume wiped), the session is marked `EXPIRED` and the error says so. We do not silently start a fresh context.

### 3.3 Open WebUI mapping
Open WebUI resends the full history and has no session concept in the OpenAI API.
- If the forwarded chat-id header is present (`X-OpenWebUI-Chat-Id`; verify on v0.10.2), labAI maps `(owner, chat-id)` to a session through `external_ref` and resumes it.
- Otherwise each request creates a short-lived session, and the prior messages are flattened into the prompt, which is what `ClaudeCodeChatModel` does today.

---

## 4. Execution management

### 4.1 Model
Table `agent_execution`:
- `id`
- `session_id`
- `owner`
- `status`: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT` or `CANCELLED`
- `prompt` (the first 4 KB is stored; the full text goes to an audit log only if configured)
- `result_text`
- `error_code`, `error_message`
- `is_error`
- `cost_usd`, `num_turns`, `duration_ms` (from the CLI JSON)
- `created_at`, `started_at`, `finished_at`

### 4.2 Flow (no HTTP thread is blocked)
```
POST /api/v1/agent/sessions/{id}/executions
  → LabAgentService.submit: authz, session check, insert QUEUED   (fast, boundedElastic)
  → ExecutionRunner.submit(task) → 202 Accepted {executionId, status: QUEUED}
worker thread:
  RUNNING → ClaudeCodeAdapter.run (blocking on the CLI process) → SUCCEEDED/FAILED/TIMED_OUT
client: GET .../executions/{eid}  (poll)  or  GET .../executions/{eid}/events (SSE, Phase 6b)
```
- For `/chat/completions` with a `claude-code` model, the controller returns `Mono.fromFuture(execution)`. The OpenAI contract is synchronous, but no thread is blocked while waiting. The `stream=true` variant emits a single chunk, the same as the starter example, until stream-json support lands.

### 4.3 Concurrency and resources
- `ExecutionRunner` uses a `ThreadPoolExecutor(max = labai.agent.max-concurrent` (default 2)`, bounded queue = labai.agent.queue-size` (default 10)`)`. When the queue is full, the request is rejected with `429`. Each CLI process is a Node runtime of a few hundred MB, so this is a real resource bound, not a thread bound.
- Each user may run at most one execution at a time (configurable).
- `--max-budget-usd` is set per profile as a cost bound.

### 4.4 Timeouts
- The per-profile timeout is passed to the starter (per-request timeout, §6).
- A guard `future.get(timeout + 30s)` in the runner covers the case where the starter itself hangs. On expiry it cancels and marks `TIMED_OUT`.

### 4.5 Cancellation
- `POST .../executions/{eid}/cancel` calls `future.cancel(true)`. The worker is interrupted, and the starter kills the CLI **and its descendants** (upstream fix). The status becomes `CANCELLED`.
- The `Future` registry is in memory (`ConcurrentHashMap<executionId, Future>`). This is fine for one instance.
  > `ponytail:` in-memory future registry, single labAI instance only; for multiple instances, poll a `cancel_requested` column from the worker.
- **Restart recovery:** at startup, rows in `QUEUED` or `RUNNING` become `FAILED("interrupted by restart")`.

### 4.6 Error mapping
| Cause | Status | API error |
|---|---|---|
| CLI exit ≠ 0, `is_error`, auth failure | `FAILED` | `502 agent_error`, with the message from the starter's `describeFailure` |
| Timeout | `TIMED_OUT` | `504 agent_timeout` |
| Cancelled | `CANCELLED` | n/a |
| Queue full | not created | `429 agent_busy` |
| Session expired | not created | `410 session_expired` |
| Not owner or profile not allowed | not created | `403` |

These are added to `GlobalExceptionHandler` using the existing OpenAI error envelope.

### 4.7 Logging
- MDC carries `executionId`, `sessionId` and `owner` for the run.
- One INFO line at start and one at end, with status, duration, cost and turns.
- Tool calls are audited on the MCP side (see the MCP doc).

---

## 5. API (new, under the existing `/api/**` auth)

| Method and path | Purpose |
|---|---|
| `POST /api/v1/agent/sessions` `{profile}` | Create a session. Returns `201 {id, profile, status}`. |
| `GET /api/v1/agent/sessions` | List the caller's sessions |
| `GET /api/v1/agent/sessions/{id}` | Session details and execution history |
| `DELETE /api/v1/agent/sessions/{id}` | Close the session and delete its workspace |
| `POST /api/v1/agent/sessions/{id}/executions` `{prompt}` | Start an execution. Returns `202 {executionId}`. |
| `GET /api/v1/agent/executions/{eid}` | Status and result |
| `POST /api/v1/agent/executions/{eid}/cancel` | Cancel the execution |

The existing `/api/v1/chat/completions` keeps working, with `claude-code` profiles routed to `LabAgentService` (§4.2).

---

## 6. Changes required upstream in the starter

These are small changes. Each keeps the current API source-compatible.

1. **`ClaudeCodeRequest`** gets an optional `ClaudeCodeOptions` with nullable overrides:
   - `permissionMode`
   - `timeout`
   - `maxTurns`
   - `allowedTools`
   - `disallowedTools`
   - `builtinTools` (passed as `--tools`)
   - `mcpConfig` (paths)
   - `strictMcpConfig`
   - `newSessionId` (passed as `--session-id`)
   - `addDirs`
   - `maxBudgetUsd`
   - `settingSources`
   - `env`
2. **Drain stdout concurrently.** This fixes the >64 KB deadlock.
3. **Send the prompt over stdin** instead of argv.
4. **Cancellation.** On `InterruptedException`, run `process.descendants().forEach(destroyForcibly)` and then destroy the process, restore the interrupt flag, and throw `ClaudeCodeCancelledException`. On timeout, the same process-tree kill and `ClaudeCodeTimeoutException`. Both extend `ClaudeCodeException`.
5. **`ClaudeCodeResult`** gets typed `costUsd`, `numTurns`, `durationMs` and `subtype` (read from the JSON).
6. Lower the auth-env log line to DEBUG.
7. (Recommended) Move to Jackson 3, and add a property-documented `dontAsk` default for server use.
8. Add a test-scope stub CLI and tests for the above in the starter itself.

The starter keeps its Java 21 baseline: a Java 21 library runs on JDK 25.

---

## 7. Security

### 7.1 Authentication and identity
- All agent routes are under `/api/**`, so the existing Bearer key is required.
- **Trusting the forwarded email must be explicit.** Each API key gets an attribute `trust-forwarded-user: true|false`. Only the Open WebUI key gets `true`. For any other key, the principal is the key's configured username and the header is ignored.
- Agent profiles carry `roles`. The API-key config gains optional roles per key. The default is `ROLE_API`, and agent profiles require `ROLE_AGENT`.
- Open WebUI must run with `WEBUI_AUTH=true` in any non-local deployment. Otherwise the forwarded email is meaningless.

### 7.2 Claude Code hardening (per execution)
| Control | How |
|---|---|
| No interactive approval, deny by default | `--permission-mode dontAsk` |
| No built-in shell or file editing unless the profile allows it | `--tools ""` (or e.g. `Read,Grep,Glob`). **Bash is never enabled for API-driven profiles.** |
| Only Lab MCP tools | `--strict-mcp-config --mcp-config <exec-file>` and `--allowedTools mcp__lab__<tool>…` |
| Ignore host user settings, hooks and plugins | `--setting-sources ""` (or `--bare`; verify which fits the pinned CLI) plus a dedicated `CLAUDE_CONFIG_DIR` |
| Filesystem | `cwd` = the session workspace under `labai.agent.workspace-root`. The path is validated as `root.resolve(x).normalize().startsWith(root)`. No `--add-dir`. |
| Cost and runaway | `--max-turns`, `--max-budget-usd`, timeout |
| Process | The container runs as a non-root user with a read-only root filesystem, except the `/workspaces` and `/claude-home` volumes. `cap_drop: ALL` and `no-new-privileges`. |

### 7.3 Credentials
- `ANTHROPIC_API_KEY` reaches the CLI through `ClaudeCodeEnvironmentProvider`, read from env or a secret. It never goes in `application*.yml` and never into a workspace.
- Toolserver credentials stay in labAI (MCP server side). **Claude never sees them.**
- The per-execution MCP token is short-lived and only lives inside the execution's MCP config file (mode `0600`, kept outside the workspace). See the MCP doc.
- The environment passed to the CLI is filtered so that DB passwords, AWS keys and similar values are not inherited. `ClaudeCodeEnvironmentProvider` returns `null` for those keys, which removes them.

### 7.4 Audit
- Every execution is a DB row.
- Every MCP tool call writes an audit log line (structured, logger `labai.audit`) with execution, owner, tool, arguments hash, status and duration. A mutating tool also gets its full arguments recorded.

---

## 8. Testing approach
- **Stub CLI:** `src/test/resources/stub-claude/claude`, a POSIX `sh` script. It:
  - appends its argv to `$STUB_ARGS_FILE`;
  - reads stdin;
  - acts according to `$STUB_MODE`: `ok` prints result JSON with the given `session_id`; `error` exits 1; `is_error` prints `is_error: true`; `sleep` sleeps 60 s (for timeout and cancel); `big` prints more than 200 KB (regression test for the deadlock).
  - Tests point `spring.ai.claude-code.executable` at the stub. No Anthropic credentials are needed.
- **Tests:**
  - adapter flag mapping (session-id vs resume, allowed tools, MCP config);
  - session create and resume;
  - one-run-per-session rule;
  - expiry job;
  - executor limits (429);
  - timeout gives `TIMED_OUT`;
  - cancel gives `CANCELLED` and the process is gone;
  - restart recovery;
  - ownership (403);
  - controller contract (WebTestClient);
  - PostgreSQL through Testcontainers `pgvector/pgvector:pg17`, which also closes `IMPROVEMENT.md` #15.
- **Optional live smoke test**, `@EnabledIfEnvironmentVariable(ANTHROPIC_API_KEY)`, excluded from the default suite.
