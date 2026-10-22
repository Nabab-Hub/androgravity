# Antigravity-Inspired Android AI IDE --- Complete Implementation Specification

**Filename:** `antigravity_android_implementation.md`\
**Status:** Implementation specification; REA findings must be verified
on an authorized local installation before any claim about Antigravity
internals is treated as fact.\
**Product concept:** An Android-first AI agent development environment
inspired by agentic IDE workflows, with multi-provider AI (Gemini,
Claude, GPT), remote isolated execution, and optional local Termux
execution.

> This is an independent implementation plan. Do not copy Antigravity
> proprietary code, branding, assets, private APIs, authentication
> tokens, or undocumented protocols. Use only official provider APIs and
> integrations permitted by their terms.

------------------------------------------------------------------------

# 1. Product vision

Build an Android application where a user can describe an outcome and
the AI agent can plan, create and edit files, run approved commands,
test code, diagnose errors, preview supported applications, and export
the project.

Example tasks: - "Create a Next.js portfolio website with a responsive
design." - "Build a FastAPI backend with Firebase authentication." -
"Inspect this repository and fix the failing tests." - "Create a Python
script, run it, and explain the output." - "Create an Android app and
build an APK remotely." - "Add a dashboard to this existing project."

The app should combine: 1. An Android-native interface. 2. A
provider-neutral AI agent engine. 3. An approval-controlled tool system.
4. Remote isolated code execution on a VPS. 5. Optional local execution
through a user-configured Termux installation. 6. A multi-provider
router for Gemini, Claude, and GPT.

"Create anything" is the long-term goal, not a guarantee that every
framework can run on every phone or server.

# 2. Core design principles

-   **Official integrations only:** use official APIs and documented
    authentication. Do not reuse Antigravity OAuth tokens or private
    endpoints.
-   **Model-provider independence:** do not couple agent logic to one
    provider's SDK.
-   **Review before action:** plans, diffs, commands, network access,
    deployments, and destructive actions must be visible and
    appropriately approved.
-   **Secure execution:** generated code runs in a sandboxed runner,
    never inside the API server process.
-   **Remote-first, local-optional:** the VPS is the reliable default
    for builds; Termux is an opt-in local target.
-   **Mobile-first UX:** design for small screens and touch, not a
    desktop IDE squeezed onto a phone.
-   **Secrets stay secret:** provider API keys must never be embedded in
    the APK, committed to source control, or sent to the Android client
    when the server is using them.
-   **Cost awareness:** show provider, model, estimated/actual usage
    where available, and limits.
-   **Evidence-based REA findings:** distinguish observation, inference,
    and unknowns.

# 3. REA investigation of Antigravity

## 3.1 Purpose

Use REA to investigate the authorized local Antigravity desktop
installation and map its user-visible workflows. Do not assume the app
is Electron until its package format has been identified.

Public references: - REA repository: https://github.com/morluto/rea -
REA documentation: https://rea.tools/ - Antigravity overview:
https://www.antigravity.google/docs/overview/ - Antigravity IDE
overview: https://www.antigravity.google/docs/ide/overview/ -
Antigravity agent concepts: https://www.antigravity.google/docs/agent/

**Disclosure:** REA has not been run against the user's local
Antigravity installation as part of creating this file. No internal
implementation detail is claimed as verified.

## 3.2 Setup and checks

Read the current official README first; requirements and CLI commands
can change. The REA README has documented Node.js/npm-based setup and a
setup command similar to:

``` bash
node --version
npm --version
npx rea-agents@latest setup
npx -y rea-agents@latest --help
npx -y rea-agents@latest doctor
```

Select the agent integration that matches the actual environment. Review
configuration changes before approving them. Use current help and
official docs for exact syntax.

If the target is an inspectable JavaScript/Electron application and the
current REA version supports it, the command shape documented by the
project may resemble:

``` bash
npx -y rea-agents@latest analyze-javascript-application "/absolute/path/to/app" --json
```

Replace the placeholder with the real authorized application directory.
Do not use this command for an unverified/native package format. Choose
the target-specific REA workflow from current documentation.

## 3.3 Investigation tasks

Ask the REA-enabled agent to: 1. Identify application format, version,
install path, and supported analysis workflow. 2. Map user-visible task
creation, workspace selection, planning, file editing, terminal
approval, verification, preview, and history. 3. Separate direct
observations from inference and unknowns. 4. If supported, map
high-level modules involved in those workflows without extracting
credentials or proprietary assets. 5. Verify whether any documented
public SDK/API is available for third-party integration; do not infer
support from a private endpoint. 6. Produce a feature matrix containing
behavior, evidence, confidence, product implication, and open questions.
7. Suggest an independent implementation of the visible workflow.

Do not extract passwords, session cookies, tokens, private user data, or
proprietary assets. Do not bypass access controls or terms.

## 3.4 Findings ledger

Fill this table only after the investigation:

  -------------------------------------------------------------------------------------------
  Area                  Finding        Evidence                 Confidence     Product
                                                                               implication
  --------------------- -------------- ------------------------ -------------- --------------
  Application/package   Pending        REA report reference     Unknown        Choose
  format                                                                       matching
                                                                               target
                                                                               workflow

  Task creation UX      Pending        Screenshot/observation   Unknown        Native task
                                       ID                                      composer

  Plan/review workflow  Pending        Observation ID           Unknown        Plan approval
                                                                               screen

  File edits/diffs      Pending        Diff observation         Unknown        File tree/diff
                                                                               viewer

  Terminal approvals    Pending        Runtime observation      Unknown        Explicit
                                                                               command
                                                                               approval

  Test/verification     Pending        Test/log observation     Unknown        Job state and
  loop                                                                         verification
                                                                               UI

  Public third-party    Verify         Official documentation   Unknown        Use supported
  API/SDK               official docs                                          integration
                                                                               only

  Multi-agent behavior  Pending        Evidence/limitations     Unknown        Defer until
                                                                               single-agent
                                                                               loop works
  -------------------------------------------------------------------------------------------

## 3.5 Prompt for REA

> Investigate the authorized local Antigravity installation using the
> currently supported REA workflow. Identify the application format
> before choosing a target workflow; do not assume Electron. Map
> user-visible workflows and separate direct evidence from inference and
> unknowns. Do not extract credentials, tokens, private user data, or
> proprietary assets. Do not bypass authentication or use
> undocumented/private endpoints. Produce
> `docs/rea-investigation-report.md` and fill the findings ledger in
> `antigravity_android_implementation.md`. Stop after reporting
> findings; do not begin implementation.

# 4. Feature scope

## 4.1 MVP

-   Google Sign-in for app identity.
-   User profile and device/session management.
-   Provider settings for Gemini, Claude, and GPT.
-   Model selector with provider/model capability metadata.
-   API key configuration or server-managed credentials.
-   Agent chat with streaming responses.
-   Structured plan creation and approval.
-   Project/workspace creation and ZIP import/export.
-   File tree, file search, code viewer, basic editor.
-   Multi-file patch generation and diff review.
-   Explicit tool approval and command profiles.
-   Remote isolated execution on a VPS.
-   Live logs, exit codes, cancellation, timeout, job history.
-   Build/test summaries and bounded repair loop.
-   Web preview for supported project types.
-   Optional Termux handoff as an experimental/advanced feature.
-   Usage tracking and spending/compute limits.

## 4.2 Later releases

-   GitHub import/export, branch and commit workflows.
-   Parallel specialist agents and isolated branches/worktrees.
-   Design/image input and screenshot-based UI iteration.
-   Remote Android SDK/Gradle builds and APK download.
-   Local Termux execution.
-   Voice input and speech-to-text.
-   Collaboration and shared projects.
-   Browser automation with a strict allowlist and visible audit trail.

## 4.3 Non-goals

-   Shipping a modified Antigravity binary.
-   Using Google Antigravity's internal OAuth tokens or undocumented
    endpoints.
-   Treating Google Sign-in as automatic Gemini API entitlement.
-   Treating a Claude web subscription as automatic Anthropic API
    credit.
-   Treating a ChatGPT subscription as automatic OpenAI API credit.
-   Unrestricted shell access to the Android app or API host.
-   Silent phone control or privilege escalation.

# 5. Architecture overview

``` text
┌────────────────────────────────────────────────────────────┐
│ Android app — Kotlin + Jetpack Compose                      │
│ Sign-in · Projects · Chat · Plan · Files · Diff · Jobs      │
│ Provider settings · Logs · Preview · Termux handoff         │
└──────────────────────────┬─────────────────────────────────┘
                           │ HTTPS REST + WebSocket/SSE
                           ▼
┌────────────────────────────────────────────────────────────┐
│ FastAPI backend / Agent Orchestrator                        │
│ Auth · Ownership checks · Agent loop · Tool policy          │
│ Provider router · Approvals · Usage accounting · Audit      │
└───────────┬──────────────────┬────────────────┬────────────┘
            │                  │                │
            ▼                  ▼                ▼
      PostgreSQL          Model APIs       Redis/queue
      users/projects      Gemini/Claude/GPT   job events
            │                                   │
            └──────────────────┬────────────────┘
                               ▼
┌────────────────────────────────────────────────────────────┐
│ Isolated workspace runner on VPS                            │
│ Per-job containers · Builds · Tests · Logs · Artifacts      │
│ Resource limits · Network policy · Short-lived preview      │
└────────────────────────────────────────────────────────────┘

Optional local route:
Android app ──explicit user action──> installed Termux
```

# 6. Recommended technology stack

## Android

-   Kotlin.
-   Jetpack Compose + Material 3.
-   MVVM, repositories, Kotlin Coroutines/Flow.
-   Navigation Compose.
-   Retrofit/OkHttp for REST.
-   WebSocket or SSE for live events/logs.
-   Room for cached project metadata and conversation history.
-   WorkManager for resumable uploads and background synchronization.
-   Android Keystore-backed storage for refresh/session credentials.
-   Storage Access Framework for user-controlled ZIP export/import.
-   Code editor: evaluate a mobile-optimized editor or embedded Monaco;
    Monaco can be resource-heavy on phones, so benchmark before
    committing.

## Backend

-   Python + FastAPI.
-   PostgreSQL + migrations.
-   Redis + a job queue/worker.
-   Docker for isolated command execution.
-   Object storage for snapshots, exports, and build artifacts.
-   TLS reverse proxy, request limits, rate limiting, and monitoring.

## Runner

-   Docker containers with a non-root user.
-   Pinned build images and command profiles.
-   CPU, memory, disk, process, and wall-clock limits.
-   Network egress disabled by default.
-   No Docker socket mounted in untrusted containers.
-   No API/server secrets mounted into build containers.
-   Separate temporary workspace per job or isolated project.
-   Clean up containers and temporary files on cancellation or expiry.

# 7. Multi-provider AI engine

## 7.1 Important billing and authentication distinction

Google Sign-in authenticates the user to **this Android application**.
It does not automatically grant Gemini API access or transfer a Google
AI Pro/Ultra subscription quota to this app.

Likewise: - Claude website subscription and Anthropic API billing are
separate unless Anthropic explicitly documents a compatible
entitlement. - ChatGPT subscription and OpenAI API billing are
separate. - API keys grant access according to the key's provider
account, API billing, permissions, and quotas---not simply the app
user's Google subscription.

Use each provider's current official API documentation and terms. Do not
reuse Antigravity OAuth credentials, private tokens, browser cookies, or
undocumented subscription endpoints.

## 7.2 Supported providers

Create three provider adapters:

-   `GeminiProvider`
-   `ClaudeProvider`
-   `OpenAIProvider`

The UI should display only models actually available through the
configured provider account. Model IDs, context limits, tool support,
streaming support, and pricing must be loaded from maintained
configuration and official provider documentation rather than hardcoded
assumptions wherever practical.

Official documentation: - Gemini API:
https://ai.google.dev/gemini-api/docs - Anthropic API:
https://docs.anthropic.com/ - OpenAI API:
https://platform.openai.com/docs - Google Identity:
https://developers.google.com/identity - Android Credential Manager:
https://developer.android.com/identity

## 7.3 Provider connection modes

Support two distinct modes:

### Mode A --- User-provided API key

The user configures a key for a provider.

Recommended implementation: 1. Explain that API usage may incur charges
on that provider's account. 2. Send the key over HTTPS only to the
backend if requests are server-routed. 3. Encrypt stored keys using a
dedicated secret-management system or envelope encryption with a
KMS-managed key. 4. Mask the key in UI; never return the full stored key
after saving. 5. Provide test connection, rotate, and delete controls.
6. Never write keys to logs, analytics, crash reports, source control,
or generated project files. 7. Do not store provider keys in plaintext
in Android SharedPreferences or Room. 8. If a direct-device API mode is
offered, explain that the device may be rooted/compromised and ensure
keys are stored using platform-backed protection; still prefer backend
proxying for a shared/public app.

### Mode B --- App owner/server-managed key

The operator configures keys in server environment/secret manager.
Requests are sent through the backend. Users consume the app's allocated
quota, and the operator pays the provider.

This mode requires: - Per-user quotas and rate limits. - A clear usage
policy. - Budget alerts and a global kill switch. - Abuse detection and
account suspension. - Cost/usage reporting. - No client-side access to
the operator's key.

Do not mix the two modes silently. Clearly label whose provider account
is being billed.

## 7.4 Provider adapter interface

Define a normalized interface and keep provider-specific SDK code behind
it:

``` python
from typing import Protocol, AsyncIterator, Any

class ModelProvider(Protocol):
    async def list_models(self) -> list[dict[str, Any]]:
        ...

    async def generate_plan(
        self, messages: list[dict[str, Any]], context: dict[str, Any]
    ) -> dict[str, Any]:
        ...

    async def stream_agent_response(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        context: dict[str, Any],
    ) -> AsyncIterator[dict[str, Any]]:
        ...

    async def summarize_execution(
        self, logs: str, exit_code: int | None
    ) -> dict[str, Any]:
        ...
```

Normalize: - text and streamed deltas, - tool calls, - finish reasons, -
usage/token counts when returned, - provider errors and retryability, -
rate limits, - context-window limits, - cancellation and timeout
behavior.

Do not assume all three providers use the same tool schema or have
identical feature support.

## 7.5 Model selector UX

Provide: - Provider selector. - Model selector filtered by account
availability. - Capability tags such as "tool calling", "vision", or
"long context" only when verified. - Provider connection status. -
Quota/rate-limit state where available. - Approximate cost/usage notice
where available. - Fallback preference, e.g. "ask before switching" or
"switch only to an explicitly approved provider".

Do not silently send private repository content to another provider.
Before cross-provider fallback, disclose the destination provider and
request consent unless the user has explicitly configured a clear
persistent policy.

## 7.6 Model router

Use explicit routing policies: - **Manual:** user chooses provider/model
for each task. - **Preferred provider:** use a configured default. -
**Task-based:** route by task type only if the user enables it. -
**Fallback:** switch only to an approved provider/model and only under
the user's configured policy.

The router should consider: - available model and account entitlement, -
capability requirements, - context size, - rate limits, - estimated
cost, - user's privacy preferences, - task budget, - whether tool
calling is supported.

Do not claim one provider is always best at a given task; benchmark
representative workloads and let the user choose.

## 7.7 Usage and cost tracking

Track, when provider responses expose it: - provider and model, -
input/output token counts, - cached token counts where applicable, -
request duration, - estimated API cost based on a versioned pricing
table, - rate-limit errors, - task and user IDs.

Pricing changes. Store pricing configuration with an `effective_at` date
and clearly label estimates. Do not infer a provider's exact bill solely
from token counts when its billing has other components.

# 8. Agent engine and tool permissions

The model should not have unrestricted shell access. Expose typed tools:

-   `list_files(project_id, directory)`
-   `read_file(project_id, path)`
-   `search_files(project_id, query)`
-   `write_file(project_id, path, content, expected_version)`
-   `propose_patch(project_id, patch)`
-   `request_command(project_id, command_profile, reason)`
-   `get_job_status(job_id)`
-   `get_job_logs(job_id)`
-   `create_preview(job_id)`
-   `request_network_access(hosts, reason)`

A model can **request** an action; the backend policy engine decides
whether it is allowed and whether approval is needed.

## 8.1 Agent loop

1.  Gather task description and relevant workspace context.
2.  Generate a structured plan.
3.  Validate the plan and show it to the user.
4.  Wait for approval.
5.  Execute bounded tool actions one at a time.
6.  Validate each tool input and ownership scope.
7.  Save outputs and update task state.
8.  Show file diffs before accepting broad changes.
9.  Request approval before commands, network access, deletion,
    deployment, publication, or external-system changes.
10. Run verification and summarize tests, limitations, and next steps.
11. Limit tool calls, repair attempts, tokens, duration, and compute
    spend.

## 8.2 Command profiles

Prefer safe profiles such as: - `npm_test` - `npm_build` - `pytest` -
`python_script` - `gradle_test` - `gradle_assemble_debug`

Each profile must have: - a fixed execution image, - allowed arguments
or validated argument schema, - working-directory constraints, - network
policy, - resource/time limits, - expected output/artifact paths.

Custom shell commands, if offered, must run only in the isolated
container and require explicit user approval. Never execute generated
commands directly on the VPS host.

# 9. Remote execution and VPS

## 9.1 Responsibilities

-   Queue jobs and assign a workspace.
-   Create isolated job containers.
-   Apply command profiles and policy.
-   Stream stdout/stderr and state changes.
-   Collect exit code, duration, and artifacts.
-   Cancel processes and terminate containers.
-   Enforce per-user concurrent-job and compute limits.
-   Clean up expired containers, logs, and previews.

## 9.2 Security baseline

-   Run as non-root.
-   No Docker socket inside the build container.
-   Do not mount host home directories or server credentials.
-   Disable network by default; allow specific hosts only when required.
-   Pin images by digest for production.
-   Apply CPU, memory, disk, PID, and time limits.
-   Use separate low-privilege runner infrastructure.
-   Keep the host and container runtime patched.
-   Treat repository content and dependency scripts as untrusted.
-   Do not rely on a container alone as a perfect security boundary for
    hostile workloads; consider stronger isolation for a public service.

# 10. Optional local Termux execution

## 10.1 Strategy

Make remote execution the default. Termux is optional and must be
installed/configured by the user from a currently trusted source. Verify
current Termux integration documentation and Android-version limitations
before implementation.

Possible workflow: 1. User opens Settings → Local execution. 2. App
explains the permissions and limitations. 3. User explicitly connects or
opens Termux. 4. App hands off a project or command through a documented
integration that is supported by the installed setup. 5. User confirms
local execution when appropriate. 6. App receives status/output only
through a documented mechanism. 7. User can disconnect at any time.

## 10.2 Constraints

-   Android app sandboxing prevents an ordinary app from silently
    accessing Termux's private files or unrestricted shell.
-   Do not bypass scoped storage or app sandboxing.
-   Do not expose an unauthenticated local HTTP shell server.
-   Do not run background shell commands without user awareness.
-   Do not put API keys in scripts passed to Termux.
-   Do not assume all Termux installations support the same plugins,
    intents, or package versions.
-   Local Android builds can be slow and storage-heavy; remote Gradle
    builds should be the fallback.

Official starting point: https://github.com/termux/termux-app

# 11. Android UI and screen map

## Global navigation

-   Home
-   Projects
-   Jobs
-   Settings

## Project workspace

-   **Chat:** conversation, context, plan, progress.
-   **Plan:** editable steps and approval.
-   **Files:** tree, search, code viewer/editor.
-   **Changes:** per-file diff and accept/reject.
-   **Run:** command proposals, approval, terminal output.
-   **Preview:** short-lived web preview for supported apps.
-   **History:** previous tasks, jobs, artifacts.

## Provider settings

-   Gemini: connection status, model selection, key/configuration
    management.
-   Claude: connection status, model selection, key/configuration
    management.
-   GPT: connection status, model selection, key/configuration
    management.
-   Default provider/model.
-   Approved fallback policy.
-   Privacy/cross-provider data policy.
-   Usage and budget.
-   Revoke/remove provider credentials.

## UX requirements

-   Touch-friendly controls and font scaling.
-   Never hide command approval in a tiny icon.
-   Show task status clearly: Draft → Planning → Awaiting approval →
    Editing → Awaiting command approval → Running → Verifying →
    Complete/Failed/Cancelled.
-   Make logs searchable and copyable.
-   Keep destructive actions separate and require confirmation.
-   Show which provider is handling the current task.

# 12. API design

Base path: `/api/v1`. Authenticate all private endpoints and check
resource ownership on every request.

## Auth and providers

-   `POST /auth/google` --- verify Google ID token server-side and
    create app session.
-   `POST /auth/refresh` --- rotate refresh token.
-   `POST /auth/logout` --- revoke session.
-   `GET /providers` --- list configured provider connections for
    current user.
-   `POST /providers/{provider}/credentials` --- securely configure
    user-provided credentials.
-   `POST /providers/{provider}/test` --- test connection.
-   `DELETE /providers/{provider}/credentials` --- revoke/delete user
    credential.
-   `GET /providers/{provider}/models` --- list allowed models.
-   `GET /usage` --- usage and quota summary.

Never trust a user ID supplied by the client; derive identity from the
validated session.

## Projects and files

-   `POST /projects`
-   `GET /projects`
-   `GET /projects/{project_id}`
-   `POST /projects/{project_id}/import`
-   `GET /projects/{project_id}/files`
-   `GET /projects/{project_id}/files/content?path=...`
-   `PUT /projects/{project_id}/files/content`
-   `POST /projects/{project_id}/export`

## Agent tasks

-   `POST /projects/{project_id}/tasks`
-   `GET /tasks/{task_id}`
-   `POST /tasks/{task_id}/approve-plan`
-   `POST /tasks/{task_id}/reject-plan`
-   `POST /tasks/{task_id}/cancel`
-   `GET /tasks/{task_id}/events`
-   `GET /tasks/{task_id}/diff`
-   `POST /tasks/{task_id}/approve-tool`

## Execution

-   `POST /projects/{project_id}/jobs`
-   `GET /jobs/{job_id}`
-   `POST /jobs/{job_id}/cancel`
-   `GET /jobs/{job_id}/logs`
-   `GET /jobs/{job_id}/artifacts`
-   `GET /jobs/{job_id}/preview`

## API safety

-   Request IDs and idempotency keys for job/task creation.
-   Strict request, response, and upload limits.
-   Canonicalize paths and reject traversal, absolute paths, escaping
    symlinks, and special files.
-   Secure archive extraction against ZIP Slip.
-   Never accept arbitrary host paths or Docker images from a mobile
    client.
-   Use typed schemas and API versioning.
-   Redact secrets before storing logs/events.

# 13. Data model

Minimum PostgreSQL tables:

-   `users`: `id`, `email`, `display_name`, `created_at`, `status`.
-   `sessions`: `id`, `user_id`, `refresh_token_hash`, `expires_at`,
    `revoked_at`.
-   `devices`: `id`, `user_id`, `public_key_fingerprint`, `created_at`,
    `revoked_at`.
-   `provider_connections`: `id`, `user_id`, `provider`,
    `credential_reference`, `mode`, `status`, `created_at`,
    `updated_at`.
-   `provider_models`: `provider`, `model_id`, `capabilities_json`,
    `enabled`, `updated_at`.
-   `projects`: `id`, `user_id`, `name`, `project_type`,
    `default_branch`, `created_at`, `updated_at`.
-   `project_snapshots`: `id`, `project_id`, `storage_key`, `sha256`,
    `created_at`.
-   `conversations`: `id`, `project_id`, `user_id`, `title`,
    `created_at`.
-   `messages`: `id`, `conversation_id`, `role`, `content_json`,
    `created_at`.
-   `tasks`: `id`, `project_id`, `conversation_id`, `provider`,
    `model_id`, `status`, `plan_json`, `created_at`, `updated_at`.
-   `tool_actions`: `id`, `task_id`, `tool_name`, `input_redacted_json`,
    `approval_status`, `approved_by`, `created_at`.
-   `jobs`: `id`, `project_id`, `task_id`, `status`, `command_profile`,
    `exit_code`, `started_at`, `finished_at`.
-   `job_events`: `id`, `job_id`, `event_type`, `payload_redacted_json`,
    `created_at`.
-   `artifacts`: `id`, `job_id`, `kind`, `storage_key`, `sha256`,
    `expires_at`.
-   `usage_records`: `id`, `user_id`, `provider`, `model_id`,
    `input_tokens`, `output_tokens`, `estimated_cost`,
    `compute_seconds`, `created_at`.

Use migrations, ownership indexes, and retention policies. Store actual
credentials in a secret manager or encrypted secret store;
`credential_reference` should point to that store rather than contain
plaintext.

# 14. Suggested repository structure

``` text
pocket-agent-studio/
├── android/
│   ├── app/
│   ├── core/
│   │   ├── model/
│   │   ├── network/
│   │   ├── database/
│   │   └── design-system/
│   └── feature/
│       ├── auth/
│       ├── home/
│       ├── project/
│       ├── agent/
│       ├── providers/
│       ├── files/
│       ├── diff/
│       ├── terminal/
│       ├── preview/
│       ├── jobs/
│       └── settings/
├── backend/
│   ├── app/
│   │   ├── api/
│   │   ├── auth/
│   │   ├── agent/
│   │   ├── providers/
│   │   │   ├── base.py
│   │   │   ├── gemini.py
│   │   │   ├── claude.py
│   │   │   └── openai.py
│   │   ├── router/
│   │   ├── policies/
│   │   ├── projects/
│   │   ├── jobs/
│   │   ├── usage/
│   │   └── storage/
│   ├── migrations/
│   └── tests/
├── runner/
│   ├── images/
│   ├── profiles/
│   └── worker/
├── infra/
│   ├── compose.yaml
│   ├── reverse-proxy/
│   └── monitoring/
├── docs/
│   ├── threat-model.md
│   ├── api.md
│   └── rea-investigation-report.md
└── README.md
```

# 15. Development phases

## Phase 0 --- Validate requirements and investigate REA

-   Set up REA using the current official guide.
-   Inspect authorized Antigravity installation or document why
    inspection is unsupported.
-   Fill the findings ledger.
-   Verify official model APIs, terms, billing, and current model IDs.
-   Finalize threat model and cost model.

**Exit criteria:** no undocumented Antigravity integration is required;
assumptions are clearly labeled.

## Phase 1 --- Android shell and authentication

-   Create Compose project and navigation.
-   Implement Google Sign-in for this app using official Google
    Identity/Credential Manager guidance.
-   Verify identity tokens on the backend.
-   Add sessions, revocation, project list, settings, and error states.

**Exit criteria:** users can sign in and only access their own data.

## Phase 2 --- Provider connections

-   Implement provider-neutral adapter interface.
-   Add Gemini, Claude, and OpenAI adapters.
-   Add server-managed and/or user-provided credential mode.
-   Add model list, connection test, error normalization, and quota
    state.
-   Add usage tracking and budget limits.

**Exit criteria:** each provider can complete a small test request
through an official API, keys never appear in client logs, and provider
costs are clearly attributed.

## Phase 3 --- Workspace and files

-   Create project APIs and storage.
-   Add ZIP import/export and safe archive extraction.
-   Add file tree, search, viewer, diff renderer, and snapshots.

**Exit criteria:** file/path security tests pass and users can
inspect/export projects without an AI request.

## Phase 4 --- Single-agent planning and edits

-   Add structured plans and approval UI.
-   Add restricted file tools.
-   Add patch application and per-file review.
-   Persist task events and conversation history.

**Exit criteria:** agent can make a multi-file change in a sample
project; user can reject the plan or patch.

## Phase 5 --- Remote runner

-   Add queue/worker and Docker job isolation.
-   Add command profiles, resource limits, network policy, streaming
    logs, cancellation, and artifact collection.

**Exit criteria:** test/build jobs work; runaway jobs terminate;
containers cannot access unrelated projects or API secrets.

## Phase 6 --- Verification and preview

-   Add bounded repair loop and test summaries.
-   Add short-lived preview URLs and artifact downloads.
-   Add history and retry.

**Exit criteria:** failure output is sanitized and actionable; repair
loops and spend are bounded.

## Phase 7 --- Beta hardening

-   Add quotas, monitoring, backups, retention/deletion controls, and
    privacy policy.
-   Test on low-memory and mid-range Android devices.
-   Test connection loss, duplicate requests, cancellation, server
    restart, and account revocation.
-   Review app store privacy/data safety requirements.

## Phase 8 --- Termux and advanced features

-   Prototype explicit Termux handoff.
-   Test supported Android versions and package sources.
-   Add Git integration, parallel agents, and Android builds after core
    stability.

**Exit criteria:** local mode is optional, visible, and can be
disconnected; remote mode remains usable without Termux.

# 16. Testing plan

## Android

-   ViewModel state-transition tests.
-   Compose UI tests for provider selection, plan approval, diff
    rejection, command approval, and cancellation.
-   Session expiry and refresh tests.
-   Offline/reconnect and duplicate submission tests.
-   Accessibility and font-scaling checks.
-   Small-screen and tablet tests.

## Backend

-   Authentication and project ownership tests.
-   Provider adapter contract tests using mocks.
-   Rate-limit, timeout, malformed response, and retry tests.
-   Secret redaction tests.
-   Quota and cost-accounting tests.
-   File traversal and ZIP extraction tests.
-   Idempotency and job cancellation tests.

## Runner

-   CPU/RAM/disk/PID/time limit tests.
-   Network-egress tests.
-   Process-tree termination.
-   Cross-user workspace isolation.
-   Artifact expiry and cleanup.
-   Container security configuration review.

## End-to-end acceptance

1.  Sign in with Google.
2.  Configure a provider key or use the server-managed provider.
3.  Test connection and select an available model.
4.  Create a project.
5.  Ask for a small feature.
6.  Review and approve the plan.
7.  Review the file diff.
8.  Approve a test command.
9.  Watch live logs and see exit code.
10. Open preview.
11. Export ZIP.
12. Verify task history and usage accounting.
13. Revoke the provider credential and confirm further calls fail
    safely.

# 17. Initial implementation backlog

Build in this order: 1. Monorepo, README, environment example files. 2.
FastAPI health/readiness endpoints. 3. PostgreSQL schema and migrations.
4. Google Sign-in verification and session handling. 5. Provider adapter
contract and mock tests. 6. Gemini adapter and connection test. 7.
Claude adapter and connection test. 8. OpenAI adapter and connection
test. 9. Provider settings, model selector, and usage UI. 10. Android
Compose shell and API client. 11. Projects and workspace file APIs. 12.
Safe ZIP import/export. 13. Plan schema and approval UI. 14. File tools
and diff UI. 15. Job queue and Docker runner. 16. Streaming logs,
cancellation, and artifacts. 17. Preview support. 18. Budget limits,
audit trail, and hardening. 19. Termux handoff prototype. 20. Git
integration and advanced agent features.

Do not start with multi-agent orchestration, all-language local
compilation, or a full desktop-like IDE. First prove the complete loop
for one supported project type.

# 18. Implementation prompt for Antigravity

Paste this prompt into your development workspace after adding this file
to the repository:

> Read `antigravity_android_implementation.md` completely. First inspect
> the current repository and report its state; do not assume it is empty
> or overwrite existing work. Start with Phase 0 and Phase 1 only, then
> stop for approval. Keep REA observations separate from assumptions.
> Use official Google Identity authentication for this app and official
> Gemini, Anthropic, and OpenAI APIs only. Do not reuse Antigravity
> OAuth tokens, private endpoints, browser cookies, undocumented APIs,
> proprietary code, assets, or branding. Build the Android client with
> Kotlin and Jetpack Compose and the backend with FastAPI. Create a
> provider-neutral adapter interface and implement one provider at a
> time with tests. Never embed provider keys in the APK or commit
> secrets. Do not run arbitrary model-generated commands on the VPS
> host; execution must be isolated and approval-controlled. Before
> system-modifying commands, dependency installation, or infrastructure
> changes, show the exact command and its effect. After the approved
> phase, run tests and report changed-file diffs, test results,
> unresolved choices, security concerns, and the next proposed phase. Do
> not continue to the next phase without approval.

## 18.1 Prompt for provider module only

> Implement the Multi-Provider AI Engine described in
> `antigravity_android_implementation.md`. Inspect the repository first.
> Add a common provider interface, Gemini/Claude/OpenAI adapters using
> their official SDKs/APIs, connection-test endpoints, normalized error
> handling, streaming support where supported, model availability
> configuration, usage recording, and tests with mocked provider
> responses. Do not assume a provider's website subscription grants API
> access. Never log keys or return stored secrets. Support explicit
> user-provided-key and server-managed-key modes only if both can be
> secured and billed transparently. Do not build the code execution
> runner in this task. Report files changed and tests run.

# 19. Security and privacy checklist

-   [ ] Google identity token verified server-side.
-   [ ] Short-lived access token and rotating refresh token.
-   [ ] Device/session revocation works.
-   [ ] Provider keys never embedded in APK.
-   [ ] Provider keys never logged or returned after saving.
-   [ ] Stored credentials encrypted or held in a secret manager.
-   [ ] Per-user quotas, rate limits, and budget caps enabled.
-   [ ] Cross-provider fallback requires explicit policy/consent.
-   [ ] User knows which provider receives project context.
-   [ ] Project/file/job ownership checked server-side.
-   [ ] Generated code executes only in isolated runner.
-   [ ] No Docker socket or host secrets in untrusted containers.
-   [ ] Network access denied by default in runner.
-   [ ] ZIP extraction and file path traversal tests pass.
-   [ ] Logs redact tokens, keys, and common secret patterns.
-   [ ] Project deletion and artifact retention controls exist.
-   [ ] Temporary previews expire.
-   [ ] Termux integration is optional and transparent.
-   [ ] Privacy policy and app-store declarations are ready.
-   [ ] App branding does not imply official Google affiliation.

# 20. Release checklist

-   [ ] REA investigation report has evidence and limitations.
-   [ ] Official API terms, current models, pricing, and quotas
    reviewed.
-   [ ] Gemini, Claude, and GPT connection tests pass.
-   [ ] Model selector shows only currently available models.
-   [ ] User can review plans, file diffs, and commands.
-   [ ] Remote build/test jobs are isolated and bounded.
-   [ ] Cancellation and failure recovery work.
-   [ ] Cost/usage display is labeled as estimate when appropriate.
-   [ ] No critical cross-user data leak or secret exposure remains.
-   [ ] App tested on supported Android devices.
-   [ ] Data deletion, retention, and privacy controls are documented.
-   [ ] Termux mode is clearly optional.
-   [ ] Store listing avoids misleading affiliation claims.

# 21. Source links

-   REA repository: https://github.com/morluto/rea
-   REA documentation: https://rea.tools/
-   Antigravity overview: https://www.antigravity.google/docs/overview/
-   Antigravity IDE overview:
    https://www.antigravity.google/docs/ide/overview/
-   Gemini API documentation: https://ai.google.dev/gemini-api/docs
-   Anthropic API documentation: https://docs.anthropic.com/
-   OpenAI API documentation: https://platform.openai.com/docs
-   Google Identity: https://developers.google.com/identity
-   Android Credential Manager: https://developer.android.com/identity
-   Jetpack Compose: https://developer.android.com/compose
-   Android Storage Access Framework:
    https://developer.android.com/guide/topics/providers/document-provider
-   Termux: https://github.com/termux/termux-app
-   FastAPI: https://fastapi.tiangolo.com/
-   Docker security: https://docs.docker.com/engine/security/

------------------------------------------------------------------------

## Final note

This file defines an independent product architecture. The REA
investigation remains a required validation step for any claims about
the installed Antigravity application's internals. The three API keys
enable official API integration, but they do not automatically transfer
website subscriptions or Antigravity-specific quotas to this app.
