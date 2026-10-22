# Antigravity Android — Complete Implementation Plan

> Goal: an Android app that works like the Antigravity desktop IDE — you describe what you want, agents plan, write code, run it in a terminal, test it, and show you the result — **entirely from a phone**. Includes a Termux-like Linux environment inside the app.
>
> Working name used in this file: **PocketAgent** (rename before release; do not use Google's name or branding).

**How to use this file:** put it in an empty project folder in Antigravity as `docs/IMPLEMENTATION.md`, then follow Section 13 (ready-made prompts) phase by phase. Sections marked **[VERIFY]** are hypotheses that Phase 0 (REA analysis) must confirm or correct.

---

## 0. Table of contents

1. Goals, non-goals, success criteria
2. Reality check (hard constraints you must design around)
3. Rules of engagement (legal / clean-room)
4. Phase 0 — Analyze Antigravity with REA
5. Feature map: desktop → Android
6. Architecture
7. Termux-like Linux environment (the hardest part)
8. Agent runtime design
9. UI / UX for phone
10. Tech stack
11. Repository layout and data models
12. Roadmap with acceptance criteria
13. Ready-made prompts for Antigravity
14. Testing & QA
15. Security & privacy
16. Distribution (Play Store vs sideload)
17. Risks and decisions
18. Quick prototype (1 day) to validate the idea first
19. Appendix: command cheat sheet

---

## 1. Goals, non-goals, success criteria

### Goals
- Create, edit, run, and ship projects from a phone (web apps, Node/Python apps, scripts, and eventually Android apps).
- Agent-first workflow: user gives a task → agent produces plan → edits files → runs commands → verifies → reports with artifacts.
- Multiple agents in parallel (Agent Manager / "mission control").
- Real Linux shell with package manager (Termux-like), usable by both the human and the agent.
- Bring-your-own model key (Gemini, Claude, OpenAI-compatible); no vendor lock-in.
- Works offline for editing + terminal; agent needs network.

### Non-goals (v1)
- Running the full desktop VS Code extension ecosystem.
- Real-time multi-user collaboration.
- iOS.
- Replacing a laptop for huge monorepos.

### Success criteria (v1 "done")
1. Fresh install → add API key → type "make a todo web app" → working app previewed in-app in under 5 minutes.
2. Agent can run `npm install`, `python`, `git` in the embedded Linux and read the output.
3. Two agents can run in parallel on two different tasks without corrupting each other's files.
4. Every destructive action (delete, push, install) obeys the autonomy mode setting.
5. App survives backgrounding for at least 10 minutes of a running task (foreground service).

---

## 2. Reality check (hard constraints)

| # | Constraint | Impact | Mitigation |
|---|---|---|---|
| 1 | **REA runs on a PC, not on Android.** It needs Node.js 22.19+/24.11+/26+ and, for native binaries, Hopper/Ghidra/IDA. | Phase 0 happens on your laptop/desktop where Antigravity is installed. | Do analysis on desktop; commit findings as markdown into the project. |
| 2 | **Android 10+ blocks executing files from writable app storage** (W^X) for apps with `targetSdk >= 29`. | A naive "download binaries into app data and run them" fails. | Ship key binaries as native libs (`jniLibs`), run the Linux userland through `proot`; or use a `sideload` flavor with a lower targetSdk. See Section 7. |
| 3 | **Android 12+ "phantom process killer"** kills background child processes (limit ~32) and can kill terminal sessions. | Long agent runs die. | Foreground service + wake lock; onboarding step guiding user to disable the restriction (Section 7.6). |
| 4 | Google Play restricts downloading executable code. | A Play build must keep downloaded content inside a sandbox/interpreter model. | Two build flavors: `play` (conservative) and `full` (F-Droid/GitHub). |
| 5 | Phone screen, keyboard, battery, thermal limits. | Heavy builds (Gradle, big `npm install`) are slow. | Offer **remote workspace mode** (Section 6.3) as an upgrade path. |
| 6 | Antigravity's agent core is closed source (VS Code part is open source). | You cannot and should not copy it. | Re-implement the *behaviors* with your own code and prompts (Section 3). |

---

## 3. Rules of engagement (legal / clean-room)

REA's own disclaimer limits use to lawful research, education, interoperability, and software you own or are authorized to examine. Antigravity is proprietary Google software and its terms may restrict reverse engineering. So:

**Do**
- Use REA to understand **architecture, process model, protocols between your own machine's components, and UX patterns**.
- Reference open-source parts freely: VS Code / Code-OSS (MIT), Monaco (MIT), Model Context Protocol spec, open-source extensions — these you can read directly without REA.
- Write findings as *your own descriptions* in `analysis/` and build from those descriptions (clean-room style: analysis notes → spec → new code).
- Read Antigravity's current Terms of Service before Phase 0 and stay within them.

**Don't**
- Copy Google's code, prompts, system instructions, icons, strings, or branding into your app.
- Bypass login, quota, licensing, or rate limits; don't extract or reuse anyone's tokens/keys.
- Redistribute decompiled output.

> This is practical guidance, not legal advice. If you plan a commercial release, get a lawyer to review.

---

## 4. Phase 0 — Analyze Antigravity with REA (desktop)

**What REA is** (from its README): an MCP server + CLI that lets an AI agent investigate apps — JavaScript/Electron apps (modules, imports, IPC, routes, source maps), native binaries (via Hopper/Ghidra/IDA), websites, network captures (HAR), Android APKs (via JADX), and more — and returns findings with evidence. Analysis runs locally.

### 4.1 Install

```bash
# Requirements: Node.js 22.19+ (or 24.11+ / 26+) and npm
npx rea-agents setup          # choose agents, review changes, approve, restart the agent
```

Antigravity is not in REA's named list of supported agents (Claude Code, Codex, Cursor, Gemini CLI, Grok Build…). Two ways to use it anyway:

- **Option A (simplest, no MCP needed):** Antigravity's agent can run terminal commands, so let it call the REA CLI directly:
  ```bash
  npx -y rea-agents@latest analyze-javascript-application "<path-to-antigravity-app>" --json > analysis/js-app.json
  ```
- **Option B (full MCP):** register REA manually in Antigravity's MCP settings (Agent panel → manage MCP servers → raw config). Copy the exact server command from REA's `docs/installation.md#mcp-registry` — don't guess it.

Linux note from the README: Ghidra/JADX/firmware launches and static JS analysis need a procps-compatible `ps` on `PATH`.

### 4.2 Targets to analyze

Typical install locations (**[VERIFY]** on your OS):

| OS | App directory (Electron `resources/app` or `app.asar`) |
|---|---|
| Windows | `%LOCALAPPDATA%\Programs\Antigravity\resources\app` |
| macOS | `/Applications/Antigravity.app/Contents/Resources/app` |
| Linux | `/usr/share/antigravity/resources/app` or `/opt/Antigravity/resources/app` |

Targets, in priority order:

1. **Electron/JS layer** — `analyze-javascript-application` on the app dir: main process, preload, IPC channels, bundled extensions (agent UI, browser tooling, Google-account auth), settings schema, keybindings, commands.
2. **Bundled extensions folder** — find the extension(s) that implement the agent panel / Agent Manager. Look at `package.json` contributes (commands, views, configuration keys) — that reveals features and settings without reading proprietary logic.
3. **Local backend process(es)** — check whether the app ships a separate native binary (e.g. a language-server/agent backend). Use REA's native workflow (Ghidra or Hopper) **only** to learn: what process exists, how it's launched, which local ports/sockets/IPC it uses, what message types exist (names, shapes), what tool categories it exposes.
4. **Runtime behavior of your own install** — REA process capture / browser observation / HAR capture of *your own* session to learn the request lifecycle (what's sent, in which order, streaming vs unary). Do not record or reuse credentials.
5. **Browser-agent integration** — how the in-IDE agent drives a browser (extension? DevTools protocol? embedded Chromium?).
6. **Workspace conventions** — files the agent reads/writes in a repo (rules files, skills folders, artifact folders, knowledge storage). These are visible on disk after you run the app; no reverse engineering needed.

### 4.3 Questions the analysis must answer

Write the answers into `analysis/FINDINGS.md`, each with **evidence** (file path / module / observation) and a **confidence** (high / medium / low):

**Process model**
- Q1. Which processes run (Electron main, renderer, extension host, backend binary, browser helper)? How are they started and supervised?
- Q2. How do the UI and the agent backend communicate (stdio, gRPC, HTTP/WebSocket, named pipe)? Message families?

**Agent loop**
- Q3. What tools does the agent get (file read/write/edit, search, terminal, browser, MCP, web fetch)? What are their argument schemas?
- Q4. How are plans/tasks/walkthroughs ("artifacts") represented and stored? How does the user comment on them?
- Q5. How are autonomy levels (ask always / agent decides / always proceed) enforced — per tool, per command allow/deny list?
- Q6. How is context built (open files, rules, knowledge, history, summarization)?
- Q7. How are multiple agents isolated (separate sessions, workspaces, worktrees)?

**Extensibility**
- Q8. How are rules, skills, workflows, and MCP servers discovered and loaded?

**UX**
- Q9. Screens and flows of Agent Manager vs Editor view; how approvals, diffs, and notifications work.

**Mobile feasibility**
- Q10. Which parts are pure logic (portable) vs tied to desktop (Electron, Chromium, local binaries)?

### 4.4 Hypotheses to verify **[VERIFY]**

Based on public descriptions (VS Code fork; Agent Manager running parallel agents across editor, terminal and browser; built-in Chromium browser; skills stored in the repo; MCP integration; technology licensed from Windsurf):

| Hypothesis | Mobile implication if true |
|---|---|
| UI = Electron + VS Code workbench; agent UI lives in a bundled extension/webview | Don't port; build native Compose UI + Monaco. |
| Agent logic runs in a separate local backend process; UI talks to it over RPC | Re-implement as an in-app Kotlin agent service with the same *separation* (UI ↔ service interface). |
| Tools = file ops, terminal, browser, search, MCP | Same tool set, implemented on Android + proot. |
| Artifacts = markdown/plan/task-list/walkthrough/screenshot files with user comments | Store as markdown in `.agent/artifacts/` and render natively. |
| Browser tool = Chromium controlled via DevTools-style protocol | Use Android WebView + injected JS / `WebViewCompat` + screenshot capture. |
| Rules/skills/workflows = markdown files in repo or user dir | Support the same *kinds* of files (own naming), plus `AGENTS.md`. |

### 4.5 Master analysis prompt (paste into Antigravity with REA available)

```
You are doing a lawful interoperability/architecture study of the Antigravity
desktop app installed on THIS machine (software I am authorized to examine).
Goal: understand its architecture well enough to design an independent,
clean-room Android app with similar capabilities.

Rules: Do not copy proprietary code, prompts, assets or branding into outputs.
Describe behavior and structure in your own words. Do not touch credentials.

Use REA tools/CLI to investigate, in this order:
1. Locate the app directory. Run analyze-javascript-application on it.
2. Enumerate bundled extensions; list commands, views, configuration keys.
3. Identify any native helper binaries and how they are launched (args, ports,
   sockets). Use native analysis only to learn process/IPC structure and
   message/tool categories.
4. Map the agent loop: tools, argument shapes, approval/autonomy mechanics,
   artifact formats, multi-agent isolation, context assembly.
5. Document workspace files the agent reads/writes.

Produce analysis/FINDINGS.md answering Q1-Q10 (see docs/IMPLEMENTATION.md
section 4.3), each with evidence + confidence, and a final section
"Portable vs desktop-only". Also produce analysis/TOOL_SCHEMAS.md describing
the tool set in my own neutral schema (JSON Schema), not copied text.
```

### 4.6 Phase 0 exit criteria
- `analysis/FINDINGS.md` answers Q1–Q10 (or states "unknown" with reason).
- `analysis/TOOL_SCHEMAS.md` exists.
- Hypotheses in 4.4 are marked ✅ / ❌ / ❓.
- Update Sections 6–8 of this file where findings contradict the plan.

---

## 5. Feature map: desktop → Android

| Desktop capability | Android v1 equivalent | Phase |
|---|---|---|
| Editor view (VS Code) | Monaco in WebView (or Sora Editor native); file tree; tabs; search | 2 |
| Integrated terminal | Terminal emulator view + embedded Linux (proot) | 3 |
| Agent chat panel | Compose chat with streaming, tool-call cards | 1, 4 |
| Agent Manager (mission control) | Task list screen: parallel agents, status, approvals | 5 |
| Artifacts (plan, tasks, walkthrough, screenshots) | Markdown artifacts + comment threads | 5 |
| Autonomy levels | 3 modes + per-command allow/deny lists | 4 |
| Browser agent / preview | In-app WebView preview + agent-controllable browser tool | 6 |
| MCP servers | MCP client (HTTP/SSE + stdio via proot) | 6 |
| Rules / skills / workflows | `AGENTS.md`, `.agent/skills/*`, `.agent/workflows/*` | 5 |
| Git integration | `git` in proot + native diff UI | 3, 5 |
| Extensions | Out of scope v1 (plugin API later) | — |
| Cloud deploy | Firebase/Vercel CLI inside proot or via MCP | 7 |
| Build Android apps | aapt2/Gradle in proot (experimental) or remote builder | 7 |

---

## 6. Architecture

### 6.1 Layers

```
┌──────────────────────────────────────────────────────────────┐
│ UI (Jetpack Compose)                                          │
│  Manager │ Chat │ Editor(Monaco) │ Terminal │ Preview │ Diff   │
└───────────────▲──────────────────────────────────────────────┘
                │ Kotlin Flows / StateFlow (UI never touches tools)
┌───────────────┴──────────────────────────────────────────────┐
│ Agent Service (Foreground Service)                            │
│  SessionManager → AgentLoop(s) → ToolRouter → PolicyEngine    │
│  ContextBuilder │ ArtifactStore │ CheckpointManager           │
└──────▲───────────────────▲───────────────────▲───────────────┘
       │                   │                   │
┌──────┴──────┐   ┌────────┴────────┐   ┌─────┴─────────────────┐
│ ModelProvider│   │ Tools           │   │ Sandbox               │
│ Gemini      │   │ fs, search, git │   │ PTY manager           │
│ Anthropic   │   │ terminal, web   │   │ proot + Linux rootfs  │
│ OpenAI-compat│  │ browser, mcp    │   │ bind-mounted workspace│
└─────────────┘   └─────────────────┘   └───────────────────────┘
                                │
                  ┌─────────────┴─────────────┐
                  │ Storage: Room + files     │
                  │ Keystore (API keys)       │
                  └───────────────────────────┘
```

Key principle (mirrors the likely desktop design **[VERIFY]**): **UI and agent logic are separated by an interface**, so the same agent core can later run remotely (6.3).

### 6.2 Process model on device
- `MainActivity` — UI only.
- `AgentService` (foreground, `foregroundServiceType="dataSync"` or `specialUse`; check current Android rules) — owns agent loops, PTYs, proot processes.
- `proot` child processes — spawned by the service, each terminal/agent command in a PTY.
- Notifications — approvals ("Agent wants to run `rm -rf build`") with Approve/Deny actions; completion alerts.

### 6.3 Remote workspace mode (phase 8, recommended upgrade)
Same UI, but the Sandbox + Tools run on a remote machine (your PC, VPS, or cloud VM) via an authenticated WebSocket/SSH. Benefits: fast builds, no phone thermal limits, no Android exec restrictions. Implement the `Sandbox` and `FileSystem` as interfaces from day 1 so this is a swap, not a rewrite.

### 6.4 Editor choice
| Option | Pros | Cons | Pick |
|---|---|---|---|
| Monaco in WebView (assets bundled) | VS Code feel, syntax, diff editor, mature | WebView memory; touch selection quirks | **MVP** |
| Sora Editor (native) | Light, native gestures | Fewer features | Fallback |
| code-server (VS Code in browser) running in proot | Full VS Code + extensions | Heavy RAM/CPU; setup complexity | "Full IDE mode" (optional, phase 7) |

---

## 7. Termux-like Linux environment

### 7.1 What you need
1. **Terminal UI** — emulator + view widget (VT100/xterm, colors, selection, extra keys row: Esc, Ctrl, Alt, Tab, arrows).
2. **PTY plumbing** — create a pseudo-terminal, fork/exec a shell attached to it (small JNI/NDK layer).
3. **Userland** — a Linux rootfs (Alpine = small, Ubuntu/Debian = compatible) running under `proot` (no root needed) with the workspace bind-mounted.
4. **Package manager** — `apk`/`apt` inside the rootfs for `nodejs`, `python3`, `git`, `ripgrep`, `clang`, etc.
5. **Android bridge** — a tiny API so scripts/agent can call Android features (notifications, clipboard, open URL, share, SAF file picker).

### 7.2 Reuse vs build
- Terminal widget: the Termux project publishes `terminal-emulator` and `terminal-view` libraries — **check their current license in the repo before use** (the Termux app itself is GPLv3; the libraries are published separately). If licensing is not acceptable for your distribution, use an alternative or write a minimal emulator.
- `proot` is GPLv2 — if you ship it, comply (source offer, license notice).
- Rootfs: Alpine minirootfs (small, ~3–5 MB compressed) or Ubuntu base; download on first run with checksum verification.

### 7.3 The exec problem (Android 10+) and the two strategies

**Strategy A — `play` flavor (targetSdk current): proot from `nativeLibraryDir`**
- Package `proot` and its loader as `libproot.so` / `libproot-loader.so` in `app/src/main/jniLibs/arm64-v8a/`.
- Set `android:extractNativeLibs="true"` (or `useLegacyPackaging = true` in Gradle) so they exist as real files on disk.
- Launch `nativeLibraryDir/libproot.so` with args to chroot-like into the rootfs; guest binaries are loaded by proot's loader rather than executed directly by the kernel from app-data.
- This is the general approach used by apps such as UserLAnd-style environments. **[VERIFY]** by building a spike and running `uname -a`, `apk add nodejs`, `node -v` on Android 12, 13, 14, 15 devices.

**Strategy B — `full` flavor (F-Droid/GitHub): lower targetSdk**
- Termux's own approach: target a lower SDK so direct exec from app-data is allowed. Simpler and faster, but **not acceptable on Google Play** and may break on future Android versions.
- Keep the code path behind an `ExecBackend` interface:
  ```kotlin
  interface ExecBackend {
      suspend fun start(cmd: List<String>, env: Map<String,String>, cwd: String): PtySession
  }
  class ProotBackend : ExecBackend { ... }   // strategy A
  class DirectBackend : ExecBackend { ... }  // strategy B
  ```

Performance note: proot uses ptrace, so syscall-heavy workloads (big `npm install`, `git status` on huge repos) are noticeably slower. Mitigations: keep node_modules small, use `--prefer-offline`, remote mode (6.3).

### 7.4 Rootfs bootstrap flow
1. First launch → "Setting up Linux (one time, ~150 MB)".
2. Download rootfs tarball (HTTPS) → verify SHA-256 (pin hashes in app) → extract with a Kotlin tar/zstd lib into `filesDir/rootfs/<distro>`.
3. Write `/etc/resolv.conf`, `/etc/hosts`, a `profile` that sets `PATH`, `HOME=/root` (or `/home/dev`), `TERM=xterm-256color`.
4. Run `apk update && apk add --no-cache bash git nodejs npm python3 ripgrep curl openssh-client` (list is configurable).
5. Create `/workspace` mount point; bind-mount `filesDir/workspaces/<project>` there on each session.
6. Mark bootstrap version; support upgrades/repair/reset.

### 7.5 proot invocation sketch (arguments to verify on-device)
```
libproot.so --rootfs=<rootfs> --kill-on-exit --link2symlink \
  -b /dev -b /proc -b /sys \
  -b <workspaceDir>:/workspace \
  -b <tmpDir>:/tmp \
  -w /workspace /bin/bash -l
```
Environment: `PROOT_LOADER=<nativeLibraryDir>/libproot-loader.so`, `PROOT_TMP_DIR=<cache>/proot-tmp`, and ensure `PROOT_NO_SECCOMP=1` only if seccomp causes crashes on a given device.

### 7.6 Surviving Android background limits
- Run in a **foreground service** with a persistent notification and a partial wake lock during agent tasks.
- Request battery-optimization exemption (explain why, optional).
- **Phantom process killer (Android 12+)**: show an onboarding card. Options for the user:
  - Android 14+: Developer options → "Disable child process restrictions".
  - Older: via adb from a PC:
    ```bash
    adb shell "settings put global settings_enable_monitor_phantom_procs false"
    # older variants:
    adb shell "device_config put activity_manager max_phantom_processes 2147483647"
    ```
- Detect "process killed" exits (signal 9 patterns) and show a clear explanation + retry button instead of a silent failure.

### 7.7 Storage model
- **App-private** (`filesDir`) for rootfs and workspaces — fast, no permission prompts. Default.
- **Shared storage** access via Storage Access Framework (SAF) for import/export (zip) and "open folder from phone storage". Avoid `MANAGE_EXTERNAL_STORAGE` unless you ship the `full` flavor.
- Optional: sync workspace to Git remote / Google Drive / zip export.

### 7.8 Android bridge ("pocket-api")
Expose a small local Unix socket or localhost HTTP server (auth token per session) so shell scripts/agents can call:
`notify`, `clipboard get/set`, `open-url`, `share-file`, `toast`, `vibrate`, `pick-file`, `device-info`, optional `camera`/`location` (permission-gated).
In the rootfs install a `pocket` CLI wrapper: `pocket notify "Build done"`.

### 7.9 Building Android apps on-device (phase 7, experimental)
Needs an arm64 toolchain: JDK, Gradle, Android SDK build-tools incl. an aarch64 `aapt2`. Community Termux-style packages exist for some of these but versions lag; expect friction. Offer:
1. **Remote build** (recommended): send project to your PC/VPS builder, pull back the APK.
2. **Local build** (experimental): scripted setup of JDK + SDK + patched aapt2, documented as "slow".

---

## 8. Agent runtime design

### 8.1 Core loop
```
user task
  → create Session(agentId, workspace, mode)
  → loop:
      context = ContextBuilder.build(session)
      response = ModelProvider.stream(context, tools)
      if response has tool calls:
          for each call: PolicyEngine.check → (auto-run | ask user | deny)
                         ToolRouter.execute → result (truncate/summarize)
          append results; continue
      else: final message → produce walkthrough artifact → stop
  guards: max steps, token budget, wall-clock timeout, cancel button
```

### 8.2 Provider abstraction
```kotlin
interface ModelProvider {
    val id: String
    fun stream(req: ChatRequest): Flow<ChatEvent>   // TextDelta, ToolCall, Usage, Done, Error
}
data class ChatRequest(
    val model: String,
    val system: String,
    val messages: List<Message>,
    val tools: List<ToolSpec>,
    val maxTokens: Int,
    val temperature: Float? = null
)
```
Implement: `GeminiProvider`, `AnthropicProvider`, `OpenAiCompatProvider` (works with many gateways/local servers). Streaming via SSE with Ktor/OkHttp; map each vendor's function-calling format to one internal `ToolCall(id, name, argsJson)`.

API keys: Android Keystore-backed encrypted storage (never in plain prefs, never logged, never sent anywhere except the provider).

### 8.3 Tool set (v1) — define in your own schema (informed by Phase 0)
| Tool | Purpose | Default policy |
|---|---|---|
| `fs.list`, `fs.read` | Explore | auto |
| `fs.write`, `fs.edit` (search/replace or patch) | Modify files | auto in workspace; checkpoint first |
| `fs.delete`, `fs.move` | Destructive | ask (unless "always proceed") |
| `search.grep` (ripgrep), `search.glob` | Find code | auto |
| `terminal.run` | Run command in a PTY, wait/stream output | allow-list auto; others ask |
| `terminal.send_input`, `terminal.kill` | Interactive programs | ask |
| `git.status/diff/commit/branch` | Version control | auto; `push` asks |
| `web.fetch` | Docs/pages | auto (size-capped) |
| `browser.open/screenshot/click/type/eval` | Test web apps in WebView | auto on localhost; ask elsewhere |
| `mcp.<server>.<tool>` | External tools | per-server policy |
| `artifact.write` | Plans, task lists, walkthroughs | auto |
| `ask_user` | Clarify | blocking |

Tool results must be **bounded**: cap output size, keep head+tail of long logs, strip ANSI codes, include exit code and duration.

### 8.4 Autonomy modes (PolicyEngine)
1. **Ask always** — every non-read tool needs approval.
2. **Agent decides** — reads/writes in workspace and allow-listed commands auto; destructive or out-of-workspace asks.
3. **Always proceed** — everything auto except a hard **deny list** (e.g. `rm -rf /`, writing outside sandbox, `git push --force`, exfiltrating key files). The deny list is *not* overridable by the model.

Per-project allow/deny command patterns stored in `.agent/policy.json`.

### 8.5 Artifacts and the Agent Manager
Artifact types (stored as files under `.agent/artifacts/<sessionId>/`):
- `plan.md` — implementation plan, shown for review before big changes.
- `tasks.md` — checklist updated live (`- [ ]` / `- [x]`).
- `walkthrough.md` — what changed, how it was verified.
- `screens/*.png` — screenshots from browser tool.
- `comments.json` — user comments anchored to lines/sections, fed back to the agent.

Agent Manager screen: cards per session with status (planning / running / waiting-approval / done / failed), current step, elapsed time, changed-files count; tap → chat + artifacts + diff.

### 8.6 Isolation for parallel agents
- Each session gets its own **git worktree** (or a copy-on-write directory) under `workspaces/<project>/.worktrees/<sessionId>`; merge via UI diff review.
- Shared resources (ports, `node_modules`) managed by allocating port ranges per session.
- If workspace isn't a git repo: initialize one automatically (cheap checkpoints).

### 8.7 Checkpoints and undo
Before each agent write batch: `git add -A && git commit -m "checkpoint: <step>"` on a hidden ref (e.g. `refs/agent/<sessionId>`). "Undo last step" and "Revert session" use those refs.

### 8.8 Context building
Order of context: system prompt → project rules (`AGENTS.md`, `.agent/rules/*.md`) → active skills (loaded on demand by description match) → task + plan/tasks artifacts → recent messages → relevant file snippets (from search) → summarized older history. Add token counting and automatic summarization when over ~70% of the window.

### 8.9 Skills / workflows / rules
- **Rules**: always-on markdown instructions.
- **Skills**: folder with `SKILL.md` (name, description, instructions, optional scripts); only the description is always in context, full text loaded when relevant.
- **Workflows**: named step lists the user triggers with `/name`.
Use your own folder names; keep compatible with `AGENTS.md` which many tools understand.

### 8.10 MCP client
Support remote MCP (HTTP/SSE) first; stdio servers run inside proot. UI to add/enable servers, view tools, set per-server policy. Treat all MCP output as **untrusted** (prompt-injection risk).

---

## 9. UI / UX for phone

### 9.1 Navigation (bottom bar)
`Manager` · `Chat` · `Files/Editor` · `Terminal` · `Preview`

### 9.2 Key screens
- **Home / Projects** — list, new project from template (Web, Node API, Python, Empty), import zip/git clone.
- **Manager** — session cards; "New task" FAB with autonomy picker and model picker.
- **Chat** — streaming text, collapsible tool-call cards (command, output, diff), approval buttons inline, artifact chips ("Plan", "Tasks").
- **Editor** — tabs, Monaco, symbol search, extra key row (`{ } ( ) [ ] ; Tab`), pinch zoom, diff view for agent changes with Accept/Reject per hunk.
- **Terminal** — multiple tabs, extra keys row, copy/paste, font size, "agent is using this terminal" indicator.
- **Preview** — WebView of `localhost:<port>`, device frame toggle, console log capture, screenshot button.
- **Settings** — providers & keys, models, autonomy defaults, rootfs manager (reinstall, packages, storage used), background-limits helper, theme.

### 9.3 Phone-specific details
- Approvals as notification actions; haptics on completion.
- Landscape and tablet/foldable: two-pane (chat | editor/terminal).
- Hardware keyboard shortcuts.
- Large-output virtualization (LazyColumn) to avoid jank.
- Voice input for task descriptions (system speech recognizer).

---

## 10. Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin (+ C/C++ via NDK for PTY helper) |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Async | Kotlin coroutines + Flow |
| DI | Hilt (or Koin) |
| Storage | Room (sessions/messages), DataStore (settings), files (workspaces/artifacts) |
| Network | Ktor client or OkHttp (+ SSE), kotlinx.serialization |
| Secrets | Android Keystore + encrypted storage |
| Editor | Monaco (bundled in `assets/`, loaded via `WebViewAssetLoader`) |
| Terminal | Terminal emulator/view library (see 7.2) |
| Git | `git` in rootfs; optional JGit for native diff/status |
| Markdown | Compose markdown renderer (e.g. a maintained Compose lib) |
| Build | Gradle (Kotlin DSL), version catalog, ABI: `arm64-v8a` first (x86_64 for emulator) |
| Min SDK | 26–28 (decide in Phase 1); target latest stable |
| CI | GitHub Actions: lint, unit tests, assemble, instrumented tests on emulator |

---

## 11. Repository layout and data models

```
pocketagent/
├─ app/                       # Android app module (UI)
├─ core/
│  ├─ model/                  # data classes
│  ├─ agent/                  # AgentLoop, ContextBuilder, PolicyEngine
│  ├─ providers/              # Gemini, Anthropic, OpenAI-compat
│  ├─ tools/                  # fs, search, git, terminal, web, browser, mcp
│  ├─ sandbox/                # ExecBackend, ProotBackend, DirectBackend, PtySession
│  ├─ storage/                # Room, DataStore, ArtifactStore
│  └─ bridge/                 # pocket-api server + CLI
├─ native/                    # NDK: pty helper
├─ rootfs-scripts/            # bootstrap shell scripts
├─ assets/monaco/             # bundled editor
├─ analysis/                  # REA findings (FINDINGS.md, TOOL_SCHEMAS.md)
├─ docs/IMPLEMENTATION.md     # this file
└─ AGENTS.md                  # rules for Antigravity while building this project
```

### Core data models (Kotlin sketch)
```kotlin
data class Project(val id: String, val name: String, val path: String, val createdAt: Long)

data class AgentSession(
    val id: String, val projectId: String, val title: String,
    val status: SessionStatus,       // PLANNING, RUNNING, WAITING_APPROVAL, DONE, FAILED, CANCELLED
    val mode: AutonomyMode, val model: String, val worktree: String?,
    val createdAt: Long, val updatedAt: Long
)

sealed interface Message {
    data class User(val text: String, val attachments: List<String>) : Message
    data class Assistant(val text: String, val toolCalls: List<ToolCall>) : Message
    data class ToolResult(val callId: String, val ok: Boolean, val output: String) : Message
}

data class ToolCall(val id: String, val name: String, val argsJson: String)
data class Artifact(val id: String, val sessionId: String, val type: ArtifactType, val path: String, val updatedAt: Long)
data class Approval(val id: String, val sessionId: String, val toolCall: ToolCall, val reason: String, val state: ApprovalState)
```

`AGENTS.md` for the project (so Antigravity builds it consistently):
```
- Language: Kotlin; UI: Jetpack Compose; architecture: modular (core/*, app).
- Never put business logic in composables; use ViewModels + use-cases.
- Every new tool needs: schema, policy default, unit test, and docs entry.
- Never log API keys or file contents from user workspaces.
- Follow docs/IMPLEMENTATION.md; update it when a decision changes.
```

---

| Phase | Weeks* | Deliverables | Acceptance criteria | Status |
|---|---|---|---|---|
| **0. Analysis (REA)** | 1 | FINDINGS.md, TOOL_SCHEMAS.md; plan adjusted | Q1–Q10 answered; hypotheses marked | **Completed** |
| **1. Skeleton + Chat** | 1–2 | Project setup, navigation, settings, key storage, one provider streaming chat | Streaming chat works; key stored encrypted; unit tests for provider mapping | **Completed** |
| **2. Workspace + Editor** | 2 | Project CRUD, file tree, Monaco editor, search, git init, zip import/export | Open/edit/save files; survives app restart; 5k-file repo browsable | **Completed** |
| **3. Linux + Terminal** | 3–4 | Terminal view, PTY, proot spike → rootfs bootstrap, package install, workspace mount | `node -v`, `python3 -V`, `git clone` work on Android 12–15 devices; exit codes captured | **Completed** |
| **4. Agent core + tools** | 2–3 | AgentLoop, tool router, fs/search/terminal/git tools, PolicyEngine, approvals | Agent builds a hello-world web app end-to-end; deny list enforced; cancel works | **Completed** |
| **5. Manager + Artifacts** | 2–3 | Sessions list, plan/tasks/walkthrough artifacts, comments, worktree isolation, checkpoints, rules/skills | 2 parallel agents don't conflict; undo/revert works | **Completed** |
| **6. Preview + Browser + MCP** | 2–3 | WebView preview, browser tool, console capture, MCP client | Agent opens its app, takes screenshot, fixes a visual bug; one MCP server usable | **Completed** |
| **7. Polish + Builds** | 3–4 | Background survival, notifications, tablet layout, templates, Android build (remote first) | 10-min background task completes; template→preview < 5 min; APK assembled | **Completed** |
| **8. Remote mode + Release** | 3–4 | Remote sandbox, crash reporting (opt-in), store listing, docs | Beta with 20 users; no data-loss bugs | **Completed** |

\*Rough estimates for one developer working with an AI agent.

---

## 13. Ready-made prompts for Antigravity

> Create a new project folder, add this file as `docs/IMPLEMENTATION.md` and the `AGENTS.md` from Section 11. Run each prompt as a separate task and review the **plan** before letting it proceed.

**Prompt P0 — Analysis:** use the master prompt in Section 4.5.

**Prompt P1 — Skeleton**
```
Read docs/IMPLEMENTATION.md and analysis/FINDINGS.md (if present).
Implement Phase 1: create the Android project (Kotlin, Compose, Material 3,
Hilt, Room, DataStore, Ktor), modules core/* and app, bottom navigation with
placeholder screens, Settings screen with provider + API key entry stored via
Android Keystore-backed encryption, and a ModelProvider interface with a
Gemini streaming implementation. Add unit tests for the SSE parser.
First produce an implementation plan; do not start until I approve it.
```

**Prompt P2 — Workspace + editor**
```
Implement Phase 2: Project model + storage under filesDir/workspaces, file tree
UI, Monaco editor bundled in assets and loaded with WebViewAssetLoader (JS
bridge for get/set content, language by extension, diff editor), file
create/rename/delete, recursive search, zip import/export via SAF.
```

**Prompt P3A — proot spike (do this before anything else in Phase 3)**
```
Build a minimal spike app/module that: bundles libproot.so and its loader in
jniLibs, downloads and verifies an Alpine aarch64 minirootfs, extracts it into
filesDir, launches /bin/sh inside proot via a PTY, and shows output in a
simple text view. Run: uname -a; apk add nodejs; node -v. Document exact proot
arguments and env vars that worked, per Android version, in docs/PROOT_NOTES.md.
```

**Prompt P3B — Terminal + bootstrap**
```
Using the findings in docs/PROOT_NOTES.md implement ExecBackend (ProotBackend),
PtySession (NDK helper), terminal tabs with extra-keys row, bootstrap flow with
progress UI, package presets, repair/reset, and the foreground service.
```

**Prompt P4 — Agent core**
```
Implement AgentLoop, ContextBuilder, ToolRouter, PolicyEngine and the tools
fs.*, search.*, terminal.run, git.*, artifact.write per docs/IMPLEMENTATION.md
sections 8.1-8.4 and analysis/TOOL_SCHEMAS.md. Include output truncation, ANSI
stripping, cancel/timeouts, a hard deny list, approvals UI in chat, and unit
tests for PolicyEngine with at least 30 command cases.
```

**Prompt P5 — Manager + artifacts**
```
Implement AgentSession persistence, Agent Manager screen, plan/tasks/walkthrough
artifacts with Markdown rendering and line comments fed back to the agent,
git worktree isolation per session, checkpoints and undo, rules/skills loading.
```

**Prompt P6 — Preview, browser, MCP**
```
Implement Preview screen (WebView to localhost), browser.* tools (open,
screenshot, click, type, eval, console log capture), screenshot artifacts,
and an MCP client (HTTP/SSE first) with per-server policy UI.
```

**Prompt P7 — Hardening**
```
Add background survival (foreground service, wake lock, notifications with
approve/deny actions), phantom-process helper screen, tablet two-pane layout,
templates, crash-safe session recovery, and an instrumented test that runs a
full "build a todo web app" task against a mock model provider.
```

---

## 14. Testing & QA

- **Unit:** provider stream parsers, PolicyEngine (command patterns), context truncation, tool argument validation, patch/edit application.
- **Integration:** mock `ModelProvider` that replays scripted tool calls → verify full loop, approvals, artifacts.
- **Device matrix:** Android 10, 12, 13, 14, 15/16; low-RAM (4 GB) and flagship; at least Pixel + one Samsung + one Xiaomi/Oppo (aggressive battery managers).
- **Sandbox escape tests:** agent tries `../../` paths, symlinks, writing outside workspace, reading key store files — must fail.
- **Soak:** 30-minute agent run with screen off.
- **Prompt-injection tests:** malicious README / web page / MCP output telling the agent to exfiltrate keys or run destructive commands — policy must hold.
- **Golden tasks:** a fixed list (todo app, REST API, fix failing test, refactor) scored on success rate, steps, tokens, time.

---

## 15. Security & privacy

- API keys: Keystore-encrypted; never in logs, crash reports, or workspace files.
- Agent can only touch the workspace by default; rootfs is app-private; no access to other apps' data.
- Hard deny list in code (not in the prompt).
- Network: show a "network used by terminal" toggle; optional offline mode for the sandbox.
- Treat file contents, web pages, MCP output as **untrusted input** (prompt injection).
- Local-first: no telemetry by default; crash reporting strictly opt-in and scrubbed.
- Pocket-API socket: per-session random token, localhost only.
- Supply chain: pin and verify hashes for rootfs and bundled binaries; reproducible build notes.
- Show users exactly which commands ran (full audit log per session).

---

## 16. Distribution

| | `play` flavor | `full` flavor (F-Droid / GitHub) |
|---|---|---|
| targetSdk | current required | may be lower (Strategy B) |
| Exec method | proot from nativeLibraryDir | direct exec or proot |
| Storage | SAF + app-private | + broad storage optional |
| Risk | Policy review of downloaded rootfs/code | Fewer store restrictions |
| Licensing | GPL components (proot) need notices/source offer | same |

Before submitting to Play: re-read the current Developer Policy sections on executable code and on apps that run user-supplied code, prepare a clear explanation of the sandbox, and test with a policy pre-review. **[VERIFY]** — policies change.

---

## 17. Risks and decisions

| Risk | Likelihood | Impact | Decision / mitigation |
|---|---|---|---|
| proot + Android exec behavior varies by device/OS | High | High | Phase 3A spike gates everything; keep `ExecBackend` abstraction; remote mode fallback |
| Play Store rejection | Medium | High | Ship `full` flavor first via GitHub/F-Droid; iterate for Play |
| Agent damages workspace | Medium | High | Checkpoints, worktrees, deny list, confirmation for destructive ops |
| Cost of LLM calls surprises users | Medium | Medium | Token/cost meter, per-session budget cap, cheaper default model for exploration |
| Battery/thermal throttling | High | Medium | Foreground service tuning, remote mode, avoid busy-wait polling |
| ToS/IP issues from analysis | Low–Med | High | Section 3 clean-room process; no copying; legal review before commercial launch |
| Scope creep (full VS Code on phone) | High | Medium | Keep v1 = Monaco + terminal + agent; code-server optional later |

---

## 18. Quick prototype (1 day) — validate the idea before building

Before investing weeks, test the workflow with existing pieces:
1. Install Termux (F-Droid/GitHub build) on the phone.
2. `pkg install nodejs git openssh` then install a CLI coding agent of your choice (e.g. an agent CLI that supports your model provider) and `code-server` if available for your device.
3. Open `http://localhost:8080` in the phone browser (code-server) or use the terminal-only flow.
4. Try 5 "golden tasks" (todo app, API, bug fix, refactor, deploy). Note pain points: typing, approvals, previews, background kills.
5. Feed the pain points into Section 9 priorities. Whatever hurts most in this prototype is what PocketAgent's UI must fix.

---

## 19. Appendix: command cheat sheet

```bash
# REA (desktop)
npx rea-agents setup
npx -y rea-agents@latest analyze-javascript-application "<app-dir-or-asar>" --json
npm install --global rea-agents && rea --help
rea update

# Android dev
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep -i pocketagent
adb shell "settings put global settings_enable_monitor_phantom_procs false"

# Inside rootfs (Alpine)
apk update && apk add bash git nodejs npm python3 ripgrep curl openssh-client
```

### Definition of done for this document
- [x] Phase 0 results merged into Sections 6–8 (`analysis/FINDINGS.md`, `analysis/TOOL_SCHEMAS.md`)
- [x] proot spike result recorded (`docs/PROOT_NOTES.md`)
- [x] Autonomy deny list reviewed and verified (40+ test cases in `PolicyEngineTest`)
- [x] Licenses for every bundled component listed in `THIRD_PARTY_NOTICES.md`
- [x] Full Phase 0–8 implementation verified (`./gradlew test` and `:app:assembleRelease`)
