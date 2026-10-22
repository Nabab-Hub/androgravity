<p align="center">
  <img src="docs/images/logo.png" width="160" height="160" alt="Maza Agent Logo" />
</p>

<h1 align="center">Maza Agent</h1>

<p align="center">
  <strong>Autonomous Mobile AI Coding Assistant for Android</strong><br>
  <em>Build, edit, run, and ship full-stack software directly from your phone or tablet.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg" alt="Language: Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-blue.svg" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Editor-Monaco%20Bundled-orange.svg" alt="Monaco Editor" />
  <img src="https://img.shields.io/badge/Sandbox-Alpine%20Linux%20(PRoot)-red.svg" alt="Alpine Linux PRoot" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg" alt="License: Apache 2.0" />
</p>

---

## 🌟 What is Maza Agent?

**Maza Agent** is a full-featured, local-first autonomous coding environment for Android. It mirrors the developer experience of Google Antigravity and modern desktop AI IDEs, optimized specifically for mobile form factors, touch keyboards, and battery survival.

Describe what you want to build $\to$ Maza Agent outlines the architectural plan $\to$ writes the code $\to$ runs builds in an embedded Linux terminal $\to$ tests in a real in-app browser $\to$ and reports back with rich visual artifacts.

---

## ✨ Key Features

- 🤖 **Autonomous Agent Loop**: Multi-turn planning, code generation, shell execution, visual browser verification, and artifact reporting.
- 🐧 **Embedded Alpine Linux (PRoot)**: Unprivileged Linux environment on Android. Run `apk add nodejs npm python3 git ripgrep` without root.
- 📝 **Monaco Code Editor**: Bundled VS Code editor core loaded offline with syntax highlighting, search, and Git diff comparisons.
- 🌐 **In-App Preview Browser**: Live localhost browser with responsive device frames (`:3000`, `:5173`, `:8080`) and live console log streaming.
- 🛡️ **Strict Policy Engine & Deny List**: Protects your device with autonomy modes (`Ask Always`, `Agent Decides`, `Always Proceed`) and hardcoded safety boundaries.
- ⚡ **Background Survival**: Android Foreground Service with CPU `WakeLock` keeps builds alive when the screen is off; approve tool actions directly from lock screen notifications.
- 📱 **Adaptive Two-Pane Layout**: Seamlessly transitions between a portrait phone bottom-bar UI and a desktop-class side-by-side view on foldable devices and tablets ($\ge 720\text{ dp}$).
- 🔌 **Model Context Protocol (MCP)**: Native client support for external HTTP/SSE and stdio MCP tool servers.
- ☁️ **Remote Sandbox Mode**: Connect to your PC, laptop, or cloud server via the companion `pocket-api` daemon to run heavy builds at workstation speeds.
- 🔒 **Zero Telemetry & Hardware Keystore**: Local-first privacy. API keys are hardware-encrypted. Diagnostic crash logs are strictly opt-in and automatically scrubbed of credentials.

---

## 📐 Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│ UI Layer (Jetpack Compose Material 3)                        │
│  Chat │ Agent Manager │ Monaco Editor │ Terminal │ Preview   │
└──────────────────────────────▲───────────────────────────────┘
                               │ StateFlows / Flows
┌──────────────────────────────┴───────────────────────────────┐
│ Background Agent Foreground Service (WakeLock Protected)     │
│  AgentLoop ──► ToolRouter ──► PolicyEngine (Strict Deny List)│
│  ArtifactManager │ SessionRecoveryManager │ Checkpoints      │
└──────────────┬───────────────────┬───────────────────┬───────┘
               │                   │                   │
┌──────────────┴──────┐   ┌────────┴────────┐   ┌──────┴───────────────┐
│ Model Providers     │   │ Tool Ecosystem  │   │ Sandbox Execution    │
│  • Google Gemini    │   │  • fs.*, search │   │  • Alpine PRoot      │
│  • Anthropic Claude │   │  • terminal.run │   │  • Direct Process    │
│  • OpenAI-compat    │   │  • browser.*    │   │  • Remote Daemon     │
└─────────────────────┘   │  • mcp.*        │   └──────────────────────┘
                          └─────────────────┘
```

---

## 🚀 Quick Start

### 1. Build the APK

Prerequisites: JDK 21+ and Android SDK 35.

```bash
# Clone the repository
git clone https://github.com/skahi/maza-agent.git
cd maza-agent

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease
```

The APK will be generated at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

### 2. Install on Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Setup on Phone

1. Open **Maza Agent**.
2. Go to **Settings** $\to$ Enter your Gemini, Claude, or OpenAI API key $\to$ Tap **Save Settings**.
3. Open **Terminal** $\to$ Initialize Alpine Linux $\to$ Run `apk add nodejs git python3`.
4. Switch to **Chat** and tell Maza Agent what to build!

---

## 📖 Documentation

- 📘 **[User Manual](docs/USER_MANUAL.md)**: Comprehensive guide on all screens, shortcuts, and workflows.
- 🌐 **[Remote Mode Setup](docs/REMOTE_MODE_SETUP.md)**: How to connect to a desktop PC or cloud VM.
- 📦 **[Release & Signing Guide](docs/RELEASE_GUIDE.md)**: Play Store and F-Droid compilation details.
- 📜 **[Third-Party Licenses](THIRD_PARTY_NOTICES.md)**: Open-source attributions and notices.

---

## 🛡️ Security & Privacy

- **Hardware Keystore**: Keys are stored via Android's `EncryptedSharedPreferences` backed by AES-256 GCM.
- **Strict Sandbox**: The agent cannot access files outside the workspace directory or touch private app keystores.
- **Privacy Scrubber**: If diagnostic logs are enabled, API keys and absolute file paths are automatically redacted with `[REDACTED]`.

---

## 📄 License

Maza Agent is licensed under the [Apache License 2.0](LICENSE). Bundled subcomponents are subject to their respective licenses detailed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
