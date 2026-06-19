# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**TG WS Proxy Android** — Android MTProto proxy app that routes Telegram traffic through CloudFlare WebSocket connections or directly to Telegram DCs.

**Tech Stack:**
- **Rust** (native proxy engine) → `src/` → compiled to `.so` via `cargo-ndk`
- **Kotlin** (Android UI) → `app/src/main/java/` → Jetpack Compose + Material 3
- **Build:** Gradle 9.1, AGP 9.0.1, Kotlin 2.1.20

## Build Commands

### Build Native Library (.so)
```bash
# One-click (Windows)
build_so.bat

# Manual (all architectures)
cargo ndk -t arm64-v8a --platform 24 -o app/src/main/jniLibs build --release
cargo ndk -t armeabi-v7a --platform 21 -o app/src/main/jniLibs build --release
```

### Build APK
```bash
# One-click (Windows) - requires .so files first
build_apk.bat

# Manual
./gradlew assembleRelease         # Release APKs (3 flavors)
./gradlew assembleDebug           # Debug APK
./gradlew assembleRelease --no-daemon  # CI mode
```

### APK Output (after build_apk.bat)
```
app/release/
├── v{version}-android-universal.apk     # arm64 + arm32
├── v{version}-android-v8a-minsdk24.apk  # arm64 only
└── v{version}-android-v7a-minsdk21.apk  # arm32 only
```

## Architecture

### Rust Engine (`src/`)
```
lib.rs       → C-ABI exports (StartProxy, StopProxy, SetPoolSize, etc.)
config.rs    → Constants, global state (ATOMICS, Lazy), DC defaults, stats
proxy.rs     → MTProto proxy core, connection handling
ws.rs        → WebSocket connections to Telegram DCs
cfproxy.rs   → CloudFlare proxy routing, domain caching
crypto.rs    → AES-256-CTR encryption for MTProto
balancer.rs  → DC load balancing, failover logic
```

**Key exports (C-ABI):**
- `StartProxy(host, port, dcIps, secret, verbose)` → starts proxy, returns 0 on success
- `StopProxy()` → graceful shutdown
- `SetPoolSize(n)` → adjust WS pool size (2-16)
- `SetCfProxyConfig(enabled, priority, userDomain)` → toggle CF routing
- `SetCfProxyCacheDir(path)` → cache directory for CF domains
- `SetSecret(secret)` → 32-char hex MTProto secret
- `GetStats()` → connection stats summary
- `GetSecretWithPrefix()` → full secret with `dd` prefix

### Kotlin Android (`app/src/main/java/com/amurcanov/tgwsproxy/`)
```
NativeProxy.kt          → JNA bridge to Rust .so
ProxyController.kt      → Service launcher, secret generation, DC IP builder
ProxyService.kt         → Foreground service hosting the proxy
MainActivity.kt         → Compose UI entry point
SettingsStore.kt        → DataStore preferences wrapper
LogEntry.kt             → Log data class for UI
BootReceiver.kt         → Boot-complete receiver (auto-start)
ProxyTilePreferencesActivity.kt → Quick settings tile prefs
AppUpdate.kt            → In-app update checker
ui/                     → Compose components (Theme, LogsTab, Cards, Dialogs)
```

### Build Configuration

**Gradle flavors** (`app/build.gradle.kts:24-50`):
| Flavor | ABI | minSdk |
|--------|-----|--------|
| `universal` | arm64-v8a + armeabi-v7a | 21 |
| `arm64` | arm64-v8a | 24 |
| `arm32` | armeabi-v7a | 21 |

**Rust release profile** (`Cargo.toml:37-41`):
```toml
opt-level = "z"    # Optimize for size
lto = true         # Link-time optimization
codegen-units = 1  # Single compilation unit
panic = "abort"    # No unwind tables
strip = true       # Strip symbols
```

## Key Patterns

### JNA Bridge
Kotlin loads `.so` via `Native.load("tgwsproxy", ...)`. Strings passed to Rust must be valid; returned `Pointer` must be freed with `FreeString()`.

### DC IP Format
Rust expects: `"1:149.154.175.50,2:149.154.167.51"` (dcId:ip pairs, comma-separated).

### MTProto Secret
32-character lowercase hex string. UI shows with `dd` prefix (e.g., `dd0123456789abcdef...`).

### Foreground Service
`ProxyService` runs as foreground with notification to prevent Android from killing the proxy.

## CI/CD

GitHub Actions (`.github/workflows/build.yml`):
- Builds on push/PR to `main`
- Ubuntu runner, JDK 17, NDK 27
- Outputs debug APKs as artifacts

## Files to Know

| File | Purpose |
|------|---------|
| `build_so.bat` | Windows script: compile Rust → `.so` |
| `build_apk.bat` | Windows script: `.so` → APK + rename |
| `BUILD.md` | Detailed build guide (Chinese) |
| `local.properties` | SDK/NDK paths, signing config (gitignored) |
| `gradle.properties` | JVM args, parallel builds, R8 settings |
