# Third-Party Software Notices and Licenses

PocketAgent incorporates and interacts with the following open-source software libraries and tools. This document provides notice of their respective licenses.

---

### 1. Monaco Editor
- **Project**: [microsoft/monaco-editor](https://github.com/microsoft/monaco-editor)
- **License**: MIT License
- **Copyright**: (c) Microsoft Corporation
```text
The MIT License (MIT)
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

---

### 2. PRoot
- **Project**: [proot-me/PRoot](https://github.com/proot-me/PRoot)
- **License**: GNU General Public License v2.0 (GPLv2)
- **Copyright**: (c) Cedric Vincent and PRoot contributors
- **Notice**: PRoot is used as an unprivileged chroot/exec emulation tool on Linux/Android. In compliance with the GPLv2, source code modifications and build scripts for bundled `libproot.so` are accessible in the repository under `rootfs-scripts/` and upon request.

---

### 3. Alpine Linux (minirootfs)
- **Project**: [Alpine Linux](https://alpinelinux.org/)
- **License**: Various open source licenses (MIT, BSD, GPL, LGPL)
- **Notice**: Alpine Linux base packages downloaded during bootstrap are subject to their respective package copyright licenses available inside the guest filesystem at `/usr/share/licenses/` and package metadata.

---

### 4. Jetpack Compose, AndroidX, and Kotlin Libraries
- **Libraries**:
  - AndroidX Core, Lifecycle, Room, Navigation, Security Crypto, DataStore
  - Jetpack Compose (UI, Material3, Foundation, Animation)
  - Kotlinx Coroutines, Kotlinx Serialization
- **License**: Apache License 2.0
- **Copyright**: (c) The Android Open Source Project, JetBrains s.r.o.
```text
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
```

---

### 5. Ktor Client & OkHttp
- **Libraries**:
  - `io.ktor:ktor-client-*` (Apache License 2.0, (c) JetBrains s.r.o.)
  - `com.squareup.okhttp3:okhttp` (Apache License 2.0, (c) Square, Inc.)

---

### 6. Truth & JUnit
- **Libraries**:
  - `com.google.truth:truth` (Apache License 2.0, (c) Google LLC)
  - `junit:junit` (Eclipse Public License 1.0)
