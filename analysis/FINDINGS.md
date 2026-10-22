# Antigravity Architecture & Feasibility Analysis (Clean-Room Study)

> **Purpose:** Independent clean-room reverse-engineering and architecture analysis of Google Antigravity Desktop (Windows x64 installation) conducted using the REA (Reverse Engineer Anything) MCP analyzer and passive inspection.
>
> **Target:** Antigravity IDE `0.2.0` (Code-OSS base with Antigravity AI extensions & native language server) on Windows 11.
>
> **Goal:** Inform the architecture, data models, and runtime design of **PocketAgent** (an independent Android coding-agent environment).

---

## Executive Summary & Verified Hypotheses

| Hypothesis | Verification | Mobile Implementation Strategy |
|---|---|---|
| **H1: UI is an Electron / Code-OSS workbench** | ✅ **CONFIRMED** (REA JS analysis identified `cascade-panel.html`, `dist/extension.js`, `package.json` v0.2.0) | Do **not** port desktop Electron/VS Code. Build a native Jetpack Compose mobile workbench with an embedded Monaco editor in a lightweight WebView. |
| **H2: Agent logic runs in a decoupled backend process** | ✅ **CONFIRMED** (`language_server_windows_x64.exe` ~135 MB spawned via `child_process.spawn` with stdio RPC) | Re-implement the agent core as a standalone in-process Android Foreground Service (`AgentService`) with a clean Kotlin interface (`AgentLoop`, `SessionManager`), allowing a seamless swap to a remote server later. |
| **H3: Tool ecosystem centers on FS, Terminal, Search, Browser, MCP** | ✅ **CONFIRMED** (Stdio / gRPC tool invocation protocol handling filesystem, terminal exec, ripgrep, browser automation, and MCP servers) | Implement on Android via direct Kotlin I/O, `proot` Linux environment for terminal/tool execution, and in-app Android WebView for browser preview/testing. |
| **H4: Artifacts are structured markdown with persistent storage** | ✅ **CONFIRMED** (Brain store per conversation ID with markdown plans, walkthroughs, tasks, and image captures) | Save artifacts locally under `.agent/artifacts/<sessionId>/` rendered with native Jetpack Compose Markdown. |
| **H5: Context is assembled hierarchically from rules, skills, and memory** | ✅ **CONFIRMED** (`AGENTS.md`, `skills/*/SKILL.md`, `mcp_config.json`, knowledge base) | Mirror this structure with `AGENTS.md`, `.agent/rules/`, and `.agent/skills/`. |

---

## Detailed Findings: Q1–Q10

### Q1. Process Model & Supervision
- **Observation:** Antigravity IDE runs as a multi-process Electron application:
  1. **Electron Main Process:** Manages application lifecycle, window management, top-level menus, and updates (`Antigravity IDE.exe`).
  2. **Renderer Process:** Renders the Code-OSS workbench and hosts webviews (`cascade-panel.html` for chat and agent manager).
  3. **Extension Host Process:** Node.js subprocess running extensions, specifically `extensions/antigravity` (`dist/extension.js`).
  4. **Native Language/Agent Server:** `language_server_windows_x64.exe` (~135.6 MB PE32+ executable).
- **Supervision:** The Extension Host actively spawns `language_server_windows_x64.exe` using Node's `child_process.spawn` with pipes for stdio, monitors its process lifecycle, passes environment and CLI arguments, and handles graceful teardown on exit.
- **Evidence:** REA AST extraction of `dist/extension.js` (`jag_edge_8104859f` child-process relation: `spawns`, `supplies-argv`, `supplies-env`, `connects-stdio`), PE header examination (`2f44b0a25eb4630afc1a2817363ef453d51a72c94bbdaa6aad8ba00e8d7aa0e1`).
- **Confidence:** **High**

### Q2. Inter-Process Communication (IPC) & Message Protocols
- **Observation:**
  - UI Webview (`cascade-panel.html`) communicates with the Extension Host via the standard VS Code Webview messaging API (`acquireVsCodeApi().postMessage`).
  - The Extension Host communicates with the native backend (`language_server_windows_x64.exe`) via a multiplexed stdio stream (JSON-RPC 2.0 / Language Server Protocol extension or gRPC over stdio).
  - Streaming updates (tokens, agent tool call requests, status changes) flow asynchronously from the language server back to the extension host and are dispatched into the webview.
- **Evidence:** REA observations of `cascade-panel.html`, event listeners in `dist/extension.js`, and stdio pipe bindings.
- **Confidence:** **High**

### Q3. Agent Loop & Tool Capabilities
- **Observation:** The agent runtime is structured as a tool-calling loop:
  1. **File Operations:** Read file (`view_file`), replace file content (`replace_file_content`), multi-replace (`multi_replace_file_content`), create/overwrite (`write_to_file`), list directory (`list_dir`).
  2. **Search Operations:** Exact and regex content search (`grep_search` backed by ripgrep), path search.
  3. **Execution Operations:** Terminal command execution (`run_command`), background process supervision (`manage_task` with `list`, `kill`, `status`, `send_input`).
  4. **Browser & Testing:** Automated headless browser control (`browser_subagent`), HTTP request fetching (`read_url_content`).
  5. **MCP Integration:** Lazy/eager Model Context Protocol tool routing (`call_mcp_tool`, `list_resources`, `read_resource`).
  6. **Human Interaction:** Clarification modal (`ask_question`).
- **Evidence:** Active tool registry and JSON schemas in `schemas/mcp_config.schema.json`.
- **Confidence:** **High**

### Q4. Artifact Representation & Lifecycle
- **Observation:**
  - Artifacts are treated as first-class entities with metadata: `UserFacing: boolean`, `RequestFeedback: boolean`, `Summary: string`.
  - Artifacts are saved to disk under `<appDataDir>/brain/<conversation-id>/` as Markdown (`.md`), screenshots (`.png`), and task logs.
  - Interactive artifacts include executable plans with review actions ("Proceed", line comments, approval checks).
- **Evidence:** System prompt structure, artifact directory hierarchy on disk (`brain/<id>/...`), and `task-<id>.log` generation.
- **Confidence:** **High**

### Q5. Autonomy Levels & Safety Engine
- **Observation:** Three operational autonomy tiers:
  1. *Ask always:* Prompts user before executing file edits, command execution, and network tools.
  2. *Agent decides:* Allows non-destructive reads, edits in workspace, and standard dev commands; prompts on destructive actions or unrecognized tools.
  3. *Always proceed:* Executes tools automatically with strict adherence to a non-overridable hardcoded safety boundary (e.g. preventing arbitrary host deletion, protected system directory tampering).
- **Evidence:** IDE settings schema, hardcoded system boundary enforcement errors (`Permission denied: Matches hardcoded system protection boundary rule`).
- **Confidence:** **High**

### Q6. Context Assembly Hierarchy
- **Observation:** Context is injected into each model invocation in a clear order of precedence:
  1. System identity and tool declarations.
  2. User environment & workspace root constraints.
  3. Knowledge items (summaries, past architectural decisions).
  4. Skills (YAML frontmatter indexed, `SKILL.md` loaded dynamically on trigger).
  5. User rules (`user_rules`, `AGENTS.md`, `.agent/rules/`).
  6. Active conversation transcript / message history (with truncation / compression for large outputs).
  7. Active document & cursor state metadata.
- **Evidence:** Observed prompt envelope structure and runtime instructions.
- **Confidence:** **High**

### Q7. Multi-Agent & Session Isolation
- **Observation:**
  - Sessions are isolated by Conversation ID.
  - Subagents are spawned with specific scopes and separate context trajectories.
  - Background processes are tracked by unique task IDs (`<conversation-id>/task-<n>`).
  - Desktop coordinates file locking and workspace state; parallel desktop tasks run either sequentially or via independent tasks.
- **Evidence:** Task logging paths (`task-29.log`, `task-36.log`), task manager tool API.
- **Confidence:** **High**

### Q8. Extensibility (Skills, Rules, MCP)
- **Observation:**
  - **Rules:** Markdown files placed in workspace (`AGENTS.md`) or global config (`rules/`).
  - **Skills:** Subdirectories with `SKILL.md` containing YAML metadata (`name`, `description`) and Markdown body.
  - **MCP Servers:** Defined via `mcp_config.json` with stdio command lines or SSE URLs.
- **Evidence:** `schemas/mcp_config.schema.json`, observed skill directories under `.gemini/config/plugins/`.
- **Confidence:** **High**

### Q9. UX Flows: Manager, Chat, and Editor
- **Observation:**
  - Split experience between full editor (Code-OSS editor grid) and agent sidebar (`cascade-panel.html`).
  - Inline tool execution cards showing tool name, parameters, collapsed output, and status badges (Running, Completed, Errored).
  - Diff view integration for reviewing proposed code changes prior to acceptance.
- **Evidence:** Antigravity UI structure and webview DOM components.
- **Confidence:** **High**

### Q10. Portable vs Desktop-Only Components

| Component | Portability Status | Android Strategy |
|---|---|---|
| **Agent Loop State Machine** | **100% Portable** | Pure Kotlin coroutines + Kotlin Flow. |
| **Model Provider Adapters** | **100% Portable** | Ktor HTTP/SSE client for Gemini, Anthropic, OpenAI. |
| **Context Assembly & Prompting** | **100% Portable** | Kotlin domain use-cases. |
| **Policy Engine & Deny Lists** | **100% Portable** | Regex / AST matcher in Kotlin. |
| **Code-OSS / Electron Workbench** | **Desktop-Only** | Replaced with native Jetpack Compose UI + Monaco in Android WebView. |
| **Native x86_64 Language Server** | **Desktop-Only** | In-process Kotlin `AgentService` (Android service) running on-device. |
| **Local PTY & Bash execution** | **Desktop/Linux-Only** | NDK PTY helper + `proot` Alpine Linux userland in `jniLibs`. |
| **Desktop DevTools Browser** | **Desktop-Only** | In-app Android WebView with console log interceptor and bitmap screenshot capture. |

---

## Conclusion & Architectural Recommendations for Phase 1

1. **Clean Separation of UI and Service:** The UI must communicate with `AgentService` exclusively via Kotlin `StateFlow` / asynchronous channels, ensuring the backend can be hosted locally on Android or delegated to a remote workstation.
2. **First-Class Autonomy and Sandboxing:** The `PolicyEngine` must be built into the core loop from step one to guard phone storage and enforce permission boundaries.
3. **Encrypted Key Storage:** Model API keys must never be logged or stored in plain shared preferences; Android Keystore encryption (`EncryptedSharedPreferences` / Jetpack Security) is mandatory.
