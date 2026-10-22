# PocketAgent Development Rules

- **Language:** Kotlin; **UI:** Jetpack Compose (Material 3); **Architecture:** Modular clean architecture (`core/*`, `app`).
- Never put business logic in composables; use ViewModels and domain UseCases / Repositories.
- Every new tool needs: schema, policy default, unit test, and documentation entry.
- Never log API keys or workspace file contents.
- Sensitive storage: Always use Android Keystore-backed encrypted storage (`EncryptedSharedPreferences` / Jetpack Security) for API keys.
- Sandbox safety: Every command run or file modification must pass through `PolicyEngine` with a strict hardcoded deny list.
- Follow `docs/antigravity_android_implementation_by_claude.md`; update it when an architectural decision changes.
