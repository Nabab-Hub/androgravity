# PRoot & Linux Sandbox Architecture Notes (PocketAgent)

## 1. Overview
PocketAgent runs a Termux/UserLAnd-style isolated Linux userspace (Alpine minirootfs) on Android devices without root access.
This document outlines the architecture, execution mechanics, Android version workarounds, and tested argument configurations.

---

## 2. ABI & Binary Packaging

### Native Binaries
On modern Android (API 29+ / Android 10+), executing binaries directly from the application data directory (`/data/data/<pkg>/files/`) is blocked by SELinux (`W^X` / noexec mounts).
To comply with Google Play policies and modern SELinux restrictions:
- `proot` is compiled as a shared library: `libproot.so`
- The custom dynamic loader is packaged as: `libproot-loader.so`
- Packaged in `app/src/main/jniLibs/<abi>/`
- Gradle configuration: `android:extractNativeLibs="true"` (or `useLegacyPackaging = true`) ensures they exist as executable files in `context.applicationInfo.nativeLibraryDir`.

### Supported Architectures
- **arm64-v8a (aarch64)**: Primary target for modern physical Android devices.
- **x86_64**: For Android Studio emulators and Chromebooks.

---

## 3. Verified Proot Invocation Arguments

### Exact Command Line Pattern
```bash
<nativeLibraryDir>/libproot.so \
  --rootfs=<filesDir>/rootfs/alpine \
  --kill-on-exit \
  --link2symlink \
  -b /dev \
  -b /proc \
  -b /sys \
  -b <filesDir>/workspaces/<projectId>:/workspace \
  -b <cacheDir>/proot-tmp:/tmp \
  -w /workspace \
  /bin/sh -l
```

### Argument Details
- `--rootfs=<dir>`: Path to the extracted Alpine Linux root directory.
- `--kill-on-exit`: Automatically terminates all child guest processes when the main shell exits.
- `--link2symlink`: Emulates hard links and symlinks across Android filesystem limitations (FAT/ext4 restrictions on Android app data).
- `-b /dev -b /proc -b /sys`: Bind-mounts host kernel pseudofilesystems for device, process, and system introspection.
- `-b <workspaceDir>:/workspace`: Bind-mounts the active project folder into `/workspace`.
- `-b <tmpDir>:/tmp`: Directs temporary file creation to the app's cache directory.
- `-w /workspace`: Sets the default initial working directory.

### Environment Variables
```ini
PROOT_LOADER=<nativeLibraryDir>/libproot-loader.so
PROOT_TMP_DIR=<cacheDir>/proot-tmp
TERM=xterm-256color
HOME=/root
PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin
LANG=en_US.UTF-8
```

---

## 4. Rootfs Setup & Configuration

1. **Alpine Linux Minirootfs (v3.20.3)**:
   - Compressed download size: ~3.5 MB
   - Extracted footprint: ~12 MB
   - Fast initialization without unnecessary services.
2. **DNS & Networking**:
   - `/etc/resolv.conf` configured with `nameserver 8.8.8.8` and `nameserver 1.1.1.1`.
   - `/etc/hosts` mapped with `127.0.0.1 localhost`.
3. **Core Development Toolchain** (installed via `apk add`):
   ```bash
   apk update && apk add --no-cache \
     bash \
     git \
     nodejs \
     npm \
     python3 \
     ripgrep \
     curl \
     openssh-client
   ```

---

## 5. Android Version Considerations

| Android Version | Behavior & Requirements |
|---|---|
| **Android 10 - 11 (API 29-30)** | Direct execution from `filesDir` blocked. `libproot.so` loaded from `nativeLibraryDir` works seamlessly. |
| **Android 12 - 13 (API 31-33)** | **Phantom Process Killer**: Android kills apps spawning >32 child processes or using heavy CPU in background. Mitigated by running as a Foreground Service with persistent notification. Users can run `adb shell "settings put global settings_enable_monitor_phantom_procs false"`. |
| **Android 14 - 15 (API 34-35)** | System developer option available: "Disable child process restrictions". Strict seccomp filters may require `PROOT_NO_SECCOMP=1` on devices with modified kernels. |

---

## 6. Dual Backend Architecture (`ExecBackend`)
To support development, testing on host JVM, Play Store compliance, and power-user builds:
- **`ProotBackend`**: Strategy A (Sandboxed container via PRoot and Linux rootfs).
- **`DirectBackend`**: Strategy B (Direct execution via `ProcessBuilder` on systems or roots where direct execution is available).
