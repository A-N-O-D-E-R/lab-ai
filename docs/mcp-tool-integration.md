# MCP Tool Integration Design

Covers how Claude Code discovers and calls the lab's existing OpenAPI tools, and how tool calls are authorized.

---

## 1. Finding: the toolserver does not speak MCP

`spring-boot-starter-openapi-toolserver` only serves an OpenAPI 3 document at `/openapi.json`. It has no MCP transport, no tool invocation and no auth. It is a Boot 3 servlet starter, so it cannot be embedded in labAI (Boot 4, WebFlux).

labAI also has **no OpenAPI tool client yet**. The design exists in `labAI/IMPROVEMENT.md` ("PLANNED FEATURE: Tools"), but is not implemented.

## 2. Decision: an MCP server inside labAI, over the planned OpenAPI tool layer

```
Claude Code CLI ──MCP (Streamable HTTP, Bearer exec-token)──▶ labAI /mcp
   labAI
    ├─ McpToolGateway      authz (token → execution → allowed tools), audit, dispatch
    ├─ OpenApiToolRegistry discovery + TTL cache (from IMPROVEMENT.md)
    └─ OpenApiToolCallback RestClient call (+ toolserver credentials)
                               │ HTTP
                               ▼
               Lab apps with spring-boot-starter-openapi-toolserver (unchanged)
```

The same `OpenApiToolRegistry` also feeds `ChatService.getTools()` for the non-Claude providers, exactly as `IMPROVEMENT.md` planned. **One tool layer serves two consumers, and no agent loop is nested:**
- Spring AI providers: Spring AI runs the tool loop.
- `claude-code` profiles: Claude Code runs the loop and calls the tools through MCP.

Spring AI `@Tool` beans are **not** visible to Claude unless they are explicitly published on the MCP endpoint.

### Alternatives considered
| Option | Why not (now) |
|---|---|
| Add MCP to each lab app through the toolserver starter | The starter is Boot 3 only, and auth and allowlisting would be duplicated in every app. Claude would also need N MCP servers and N credentials. Revisit once the lab apps are on Boot 4. |
| A separate "lab-mcp-server" microservice | The tool layer already lives in labAI. Another deployable adds ops cost for no gain. Splitting it out later is easy, because the classes are self-contained. |
| A generic OpenAPI-to-MCP proxy (third-party) | It would expose every operation, offer no per-execution authz, and add a new dependency. |

## 3. Implementation

**Dependency:** `org.springframework.ai:spring-ai-starter-mcp-server-webflux`, from the Spring AI 2.0 BOM, which is built on MCP Java SDK 2.0.0. Spring AI 2.0 moved the WebFlux/WebMVC MCP transports into Spring AI. Confirm the exact artifact id at resolution time (Phase 4 gate).

```yaml
spring:
  ai:
    mcp:
      server:
        name: lab
        protocol: STATELESS        # stateless Streamable HTTP: no MCP session state to manage
        # endpoint: /mcp
```

### 3.1 Tool exposure: deny by default
- The Spring AI MCP server auto-exposes `ToolCallbackProvider` beans. To keep control, **OpenAPI callbacks are not declared as `ToolCallbackProvider` beans**. Instead, `McpToolGateway` registers MCP tool specifications **explicitly**, and only for allowlisted operations. When the registry refreshes, it adds or removes tools at runtime and notifies `tools/list_changed`.
- The allowlist is per toolserver:

```yaml
labai:
  toolservers:
    - id: testapp
      url: http://testapp:8080
      headers: { Authorization: "Bearer ${TESTAPP_TOKEN}" }   # never sent to Claude
      ttl: 10m
      expose:                      # operationIds; anything not listed is NOT an MCP tool
        - operation: getAllUsers
        - operation: getUserById
        - operation: createUser
          mutating: true           # audited with full arguments; profiles must opt in
      # deleteUser not listed → never reachable by the agent
```

- Every tool name is `<serverId>_<operationId>`, sanitized. In Claude it appears as `mcp__lab__testapp_getAllUsers`.

### 3.2 Per-execution authorization
1. When an execution starts, `LabAgentService` issues an **execution token**: 256-bit random, held in memory as `token → {executionId, owner, allowedTools, expiresAt}`, and revoked when the execution ends.
2. `ClaudeCodeAdapter` writes `<exec-tmp>/mcp.json` (mode `0600`, outside the workspace):

   ```json
   {"mcpServers":{"lab":{"type":"http","url":"http://127.0.0.1:8080/mcp",
     "headers":{"Authorization":"Bearer <execution-token>"}}}}
   ```

   It then passes `--strict-mcp-config --mcp-config <file> --permission-mode dontAsk --allowedTools mcp__lab__<each allowed tool>`.
3. A dedicated security chain for `/mcp` accepts **only** execution tokens, not user API keys. `/mcp` is bound so that only the local CLI can reach it. In Docker that is the same container, so `127.0.0.1` works.
4. `McpToolGateway.call(tool, args)` does three things:
   - It re-checks that `tool ∈ allowedTools` for that token. **The server is authoritative**: the CLI flag is defence in depth.
   - It writes the audit line.
   - It invokes `OpenApiToolCallback`.

   HTTP errors come back as an MCP tool error result. The execution is not failed.

`tools/list` returns the global allowlist, not the per-profile one. Claude still cannot *call* outside its profile, because `dontAsk` plus `--allowedTools` blocks it, and the server check blocks it again.
> `ponytail:` one shared MCP tool list, per-profile filtering at call time; split into per-profile endpoints if list leakage across profiles matters.

### 3.3 Tool authorization matrix
| Layer | Enforces |
|---|---|
| Toolserver config `expose` | Which operations exist as tools at all |
| Profile `agent.mcp-tools` | Which of those this profile may use. `mutating` tools need an explicit listing. |
| Profile `roles` | Who may run the profile |
| Execution token | Binds a call to one execution and owner, with a short lifetime |
| CLI `--allowedTools` and `dontAsk` | Claude does not attempt tools outside the set |

## 4. Changes to existing components
- **`spring-boot-starter-openapi-toolserver`:** no change required. Optional, recommended fixes:
  - a configurable public server URL instead of hard-coded `localhost`;
  - a default CORS of none instead of `*`;
  - serving the springdoc model directly instead of the loopback HTTP call.
- **`toolserver-test-app`:** unchanged. It is used as the integration fixture.
- **labAI:** new `tools/openapi/*` (as in `IMPROVEMENT.md`), `mcp/McpToolGateway`, `mcp/McpSecurityConfig`, the `LabaiToolserverProperties` additions (`expose`), and the `ChatService.getTools()` change. For Spring AI providers, tool exposure follows the same `expose` allowlist.

## 5. Tests
- `OpenApiToolParserTest`: parses a spec fixture captured from `toolserver-test-app`. Checks 4 operations, path-param requiredness, the flattened body and the base URL.
- `OpenApiToolCallbackTest`: runs against a JDK `HttpServer` stub. Checks path, query and body mapping, error to JSON error, and that static headers are sent.
- `McpServerIT`: boots labAI on a random port and connects with the MCP Java SDK client (Streamable HTTP). It checks that:
  - only allowlisted tools are listed;
  - a call succeeds with a valid token;
  - the request is rejected with no token or an expired token;
  - a call outside the profile is rejected;
  - an audit line is written.
- End to end with the stub CLI: the stub reads `--mcp-config` and performs one real MCP `tools/call` with the token (using `curl`, or a tiny Java helper when `curl` is unavailable). This proves the wiring from Claude flags to MCP to the OpenAPI stub without Anthropic credentials.
