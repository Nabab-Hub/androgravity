# PocketAgent Release & Distribution Guide

This document outlines the build process, signing steps, ProGuard rules, and store compliance checklists for deploying PocketAgent.

---

## 1. Build Flavors & TargetSDK Strategy

Per Section 16 of the architecture specification, PocketAgent is designed for two distribution paths:

| Feature | `play` Flavor | `full` Flavor (GitHub / F-Droid) |
|---|---|---|
| **Target SDK** | SDK 34 / 35 (Latest Google Play requirement) | SDK 28 or current |
| **Exec Engine** | PRoot loaded via `libproot.so` from `nativeLibraryDir` + Remote mode | PRoot / Direct Exec + Remote mode |
| **Storage Model** | Scoped Storage / SAF + App-private rootfs | SAF + optional broader storage access |
| **Telemetry** | Strictly Zero / Local-first opt-in scrubbed crash logs | Strictly Zero / Local-first |

---

## 2. Release Compilation

To build an optimized, shrunk release APK:

```bash
./gradlew :app:assembleRelease
```

The output APK will be placed at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 3. Signing the Release APK

Generate an Android release keystore:
```bash
keytool -genkey -v -keystore pocketagent-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pocketagent
```

Sign the APK using `apksigner`:
```bash
zipalign -v -p 4 app/build/outputs/apk/release/app-release-unsigned.apk app-release-aligned.apk
apksigner sign --ks pocketagent-release.jks --out app-release-signed.apk app-release-aligned.apk
```

---

## 4. Google Play Store Submission Checklist

Before submitting to Google Play Console:
1. **Developer Policy Pre-review**:
   - Provide clear disclosure that PRoot runs an unprivileged, sandboxed userland solely to interpret scripts and compile local code projects created by the user.
   - Clarify that no executable code is downloaded to writable executable memory (complies with Android 10+ W^X constraints).
2. **Foreground Service Declarations**:
   - `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`: Required for background agent loops executing long-running code generation and test tasks.
   - Use subtype description: `dev.pocketagent.agent_task_execution`.
3. **Data Safety Section**:
   - PocketAgent collects **no personal data**.
   - API keys are stored exclusively in the hardware-backed Android Keystore.
   - Zero remote telemetry or third-party ad SDKs.
4. **Third-Party Notices**:
   - Ensure `THIRD_PARTY_NOTICES.md` is bundled or linked in the app settings.
