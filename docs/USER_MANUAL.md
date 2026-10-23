# Androgravity — User Manual & Operating Guide

<p align="center">
  <img src="images/logo.png" width="160" height="160" alt="Androgravity Logo" />
</p>

Welcome to **Androgravity**, the autonomous mobile AI coding assistant for Android. Androgravity brings the full power of an autonomous software engineering agent — planning, writing code, executing terminal commands, testing in a real browser, and managing complex projects — directly onto your phone or tablet.

---

## Table of Contents
1. [Getting Started](#1-getting-started)
2. [Interface Overview](#2-interface-overview)
3. [Chat & Autonomous Problem Solving](#3-chat--autonomous-problem-solving)
4. [Agent Manager ("Mission Control")](#4-agent-manager-mission-control)
5. [Monaco Code Editor & File Explorer](#5-monaco-code-editor--file-explorer)
6. [Embedded Alpine Linux Terminal](#6-embedded-alpine-linux-terminal)
7. [In-App Preview Browser & Testing](#7-in-app-preview-browser--testing)
8. [Autonomy Modes & Safety Policy](#8-autonomy-modes--safety-policy)
9. [Model Context Protocol (MCP) Integration](#9-model-context-protocol-mcp-integration)
10. [Background Survival & Battery Setup](#10-background-survival--battery-setup)
11. [Remote Workspace Mode](#11-remote-workspace-mode)
12. [Project Templates](#12-project-templates)
13. [Troubleshooting & FAQ](#13-troubleshooting--faq)

---

## 1. Getting Started

### 1.1 First Launch Configuration
1. Tap the **Settings** icon (gear icon) in the top-right corner or navigation bar.
2. Select your AI Model Provider:
   - **Google Gemini**: Enter your Google AI Studio API key (`AIzaSy...`). Default recommended model: `gemini-2.5-flash`.
   - **Anthropic Claude**: Enter your Anthropic key (`sk-ant-...`).
   - **OpenAI**: Enter your OpenAI-compatible API key (`sk-...`).
3. Tap **Save Settings**. Your API keys are encrypted immediately with hardware-backed AES-256 keys via the Android Keystore.

### 1.2 Initializing the Linux Userland
1. Switch to the **Terminal** tab.
2. If this is a fresh install, tap **Initialize Alpine Linux**. Androgravity will download and unpack the minirootfs into private storage.
3. Install standard packages by running:
   ```bash
   apk update && apk add git nodejs npm python3 ripgrep
   ```

---

## 2. Interface Overview

Androgravity dynamically adapts its layout depending on your device form factor:

- **Phone (Portrait)**: Clean bottom navigation bar with 5 primary screens:
  - 💬 **Chat**: Prompt the agent and monitor live reasoning.
  - 📋 **Manager**: Multi-agent sessions, plans, tasks, and walkthroughs.
  - 📝 **Editor**: Full Monaco code editor with file tree and git diffs.
  - 💻 **Terminal**: Real VT100 terminal tabs with extra-keys row.
  - 🌐 **Preview**: Interactive localhost browser with device frame toggle.
- **Tablet / Foldable / Landscape**: Responsive two-pane layout:
  - **Left Pane (45%)**: Chat, Agent Manager, and Settings.
  - **Right Pane (55%)**: Code Editor, Linux Terminal, and In-App Preview side-by-side.

---

## 3. Chat & Autonomous Problem Solving

### Starting a Task
Simply type what you want to build in natural language into the chat bar:
> *"Create a responsive Todo web application with localStorage persistence and dark mode styling. Start the dev server and verify it in the browser."*

### What Happens Behind the Scenes:
1. **Context Building**: Androgravity scans the active workspace, reads `.agent/skills` and `AGENTS.md` rules.
2. **Planning**: The agent produces an implementation plan (`plan.md`).
3. **Execution**:
   - Creates HTML, CSS, JavaScript, or backend files.
   - Executes shell commands (e.g. `npm install`, `python main.py`).
   - Captures terminal outputs and strips ANSI escape codes.
4. **Verification**: Launches the in-app preview browser, takes screenshots to inspect UI layout, and fixes any visual bugs autonomously.
5. **Reporting**: Generates a comprehensive walkthrough artifact (`walkthrough.md`).

---

## 4. Agent Manager ("Mission Control")

The **Manager** screen lets you track multiple agent sessions running in parallel on isolated Git worktrees.

Tapping any session opens the **Session Detail View** with 6 tabs:
1. **Chat**: Full conversation history and streaming output.
2. **Plan**: Rendered `plan.md` artifact showing the agent's architectural roadmap.
3. **Tasks**: Interactive task checklist with real-time status badges.
4. **Walkthrough**: Summary report explaining what was created and how to test it.
5. **Changes**: Unified Git diff viewer showing all modified, added, and deleted files.
6. **Log**: Raw audit log of every command executed, tool invoked, and timestamp.

> **Pro-Tip**: You can select any line in an artifact or diff and tap **Add Comment** to guide the agent without typing a whole prompt!

---

## 5. Monaco Code Editor & File Explorer

Androgravity embeds the Microsoft Monaco Editor (the editor core powering VS Code):
- **Full Syntax Highlighting**: JavaScript, TypeScript, Python, HTML, CSS, JSON, Kotlin, Rust, Go, Markdown, and more.
- **File Explorer**: Browse folders, create files, rename, delete, and search across thousands of files instantly.
- **Diff Editor**: Side-by-side or inline view comparing working directory changes against git `HEAD`.
- **Zip Import / Export**: Import your existing repositories from Android Storage Access Framework (SAF) or export your finished project as a `.zip`.

---

## 6. Embedded Alpine Linux Terminal

Androgravity includes a complete, unprivileged Linux userland powered by PRoot:
- **Real Package Manager**: Run `apk add <pkg>` to install compilers, runtimes, and tools.
- **PTY Terminal Emulator**: Full color VT100 / xterm support with ANSI formatting.
- **Extra-Keys Row**: Quick touch access to `ESC`, `TAB`, `CTRL`, `ALT`, `PIPE (|)`, `~`, `/`, `-`, and arrow keys.
- **Multiple Tabs**: Keep dev servers running in one tab while executing Git commands in another.

---

## 7. In-App Preview Browser & Testing

Inspect and interact with your web apps without leaving Androgravity:
- **Port Selector**: Fast one-tap switching between `:3000`, `:5173`, `:8080`, or custom ports.
- **Device Frame Toggle**: Test how your web app looks on phone screen vs tablet viewport.
- **Collapsible Console**: Live JavaScript `console.log`, warnings, and runtime errors stream into a collapsible bottom tray.
- **Autonomous Agent Inspection**: The agent uses `browser_screenshot`, `browser_click`, and `browser_eval` to verify its own code.

---

## 8. Autonomy Modes & Safety Policy

You maintain complete control over what the agent is allowed to do. Select an Autonomy Mode in **Settings**:

| Autonomy Mode | Description |
|---|---|
| **Ask Always** | Requires your confirmation before running any terminal command or writing files. |
| **Agent Decides** *(Default)* | Agent automatically runs non-destructive commands (read files, `git status`, test runs). Destructive commands prompt for approval. |
| **Always Proceed** | Full autonomy for trusted autonomous builds. |

### Strict Hardcoded Deny List
Regardless of autonomy settings, Androgravity's `PolicyEngine` strictly forbids high-risk operations:
- ❌ Recursive root deletions (`rm -rf /`, `rm -rf ~`)
- ❌ Raw disk or device writes (`dd if=... of=/dev/...`, `mkfs`)
- ❌ Accessing parent escapes outside workspace (`../../`)
- ❌ Accessing private app keystore or internal databases

---

## 9. Model Context Protocol (MCP) Integration

Androgravity supports the open Model Context Protocol (MCP) specification:
- Connect to HTTP/SSE and stdio tool servers.
- Configure servers under **Settings $\to$ MCP Tool Servers**.
- Toggle per-server autonomy policies (`Auto-approve`, `Ask`, `Disabled`).

---

## 10. Background Survival & Battery Setup

Modern Android OS versions aggressively terminate background apps. Androgravity implements multiple safeguards to keep your builds running:

1. **Foreground Service & WakeLock**:
   - Keeps CPU awake while the agent is running a task.
   - Ongoing notification displays current progress (e.g., `Step 4/20: Installing npm dependencies`).
   - Notification action buttons allow you to **Approve** or **Deny** tool requests directly from your lock screen!
2. **Android 12+ 32-Child-Process Limit**:
   - Go to **Settings $\to$ Background Limits & Phantom Process Killer Guide**.
   - If running heavy builds, copy the one-tap ADB commands to remove background process limits:
     ```bash
     adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"
     adb shell "setprop sys.fflag.override.settings_enable_monitor_phantom_procs false"
     ```

---

## 11. Connecting with Termux (Local Termux Bridge) & Remote PC

Androgravity allows you to harness the full power of **Termux** on your Android device (or a remote desktop PC / Cloud VM). This enables running standard Linux tools (`git`, `node`, `npm`, `python3`, `clang`, `rust`, etc.) directly with full native package support.

### 11.1 How It Works
Androgravity communicates with a lightweight, zero-dependency background daemon (`pocket-api.py`) running in Termux on `127.0.0.1:8787`. All shell commands, filesystem operations, and builds requested by the AI agent are dispatched directly to Termux.

---

### 11.2 Step-by-Step Termux Setup Guide

#### Step 1: Install & Prepare Termux
1. Open **Termux** (install from F-Droid or GitHub for the latest releases).
2. Update packages and install Python:
   ```bash
   pkg update -y && pkg install -y python git openssl
   ```

#### Step 2: Generate an Auth Token
An authentication token protects your Termux environment from unauthorized local access. You can generate a secure random 32-character hexadecimal token using `openssl` or `python`:

- **Option A (using OpenSSL)**:
  ```bash
  openssl rand -hex 16
  ```
- **Option B (using Python)**:
  ```bash
  python3 -c "import secrets; print(secrets.token_hex(16))"
  ```
- *Example generated token*: `9a3f8c2e1b4d5e709a12c45f89e2b10a`  
*(Copy this token; you will paste it into Androgravity settings).*

#### Step 3: Download and Run the Daemon (`pocket-api.py`)
In Termux, you can fetch `pocket-api.py` directly from your repository or copy it:

```bash
# Download the bridge daemon script
curl -sSL https://raw.githubusercontent.com/Nabab-Hub/androgravity/main/tools/pocket-api/pocket-api.py -o pocket-api.py

# Or if you already cloned the repository in Termux:
# cd androgravity/tools/pocket-api
```

Now start the daemon, replacing `<YOUR_TOKEN>` with the token generated in Step 2:
```bash
python3 pocket-api.py --port 8787 --token <YOUR_TOKEN> --workspace ~/projects
```

You will see:
```text
[pocket-api] Listening on 0.0.0.0:8787
[pocket-api] Workspace root: /data/data/com.termux/files/home/projects
[pocket-api] Auth token required: Yes (Bearer ***)
```

> **Tip for Background Execution**:  
> To keep the daemon running even when you switch apps, acquire a Termux WakeLock:
> ```bash
> termux-wake-lock
> python3 pocket-api.py --port 8787 --token <YOUR_TOKEN> &
> ```

---

### 11.3 Configure Androgravity App

1. In Androgravity, tap the **Settings** icon.
2. Scroll to **Execution Sandbox Mode** and select **Termux Bridge / Remote PC**.
3. In the **Remote Host Configuration** section:
   - **Host / IP**: `127.0.0.1` (or your PC's LAN IP if running on a desktop)
   - **Port**: `8787`
   - **Auth Bearer Token**: Paste the token you generated in Step 2
   - **Use HTTPS / WSS**: Leave **OFF** for local Termux (`127.0.0.1`)
4. Tap **Test Remote Daemon Connection**.
   - You should see a green checkmark: `Online: Linux (v1.0.0), Latency: < 10 ms`.
5. Tap **Save Settings** at the bottom of the Settings screen.

Now, whenever you ask Androgravity to plan, code, build, or run tasks, it will seamlessly execute commands in your Termux environment!

---

## 12. Project Templates

Jumpstart new projects with built-in templates:
- **Static Web**: HTML5 boilerplate, modern CSS, and JavaScript.
- **React + Vite**: Fast, modern frontend development with hot-reload.
- **Node.js Express**: REST API backend with JSON endpoints.
- **Python FastAPI**: Modern async Python API server.
- **Empty Project**: Clean canvas for custom workflows.

---

## 13. Troubleshooting & FAQ

**Q: Why did my npm install stop when I locked the screen?**  
A: Ensure Androgravity is exempted from Android Battery Optimization (Doze Mode). Tap **Request Exemption** in the Background Guide screen.

**Q: Are my API keys safe?**  
A: Yes. All API keys and auth tokens are stored using Android's hardware-backed `EncryptedSharedPreferences`. They are never logged, exported in crash dumps, or written to workspace files.

**Q: Can Androgravity work offline?**  
A: The Monaco editor, Alpine Linux terminal, and file manager work 100% offline. The AI agent loop requires network connectivity to communicate with your chosen model provider (or a local Ollama server in Remote Mode).
