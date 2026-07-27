# Telegram WS Proxy — Android

A local **MTProto proxy** for Telegram that runs entirely on your Android
device. It stands up a loopback proxy that Telegram connects to, then forwards
that traffic to Telegram's data centers either **directly** or **tunneled over
WebSocket behind Cloudflare** — useful on networks where Telegram is throttled,
filtered, or gets stuck while routing.

No account, no remote server, no configuration server. The proxy is a native
core written in **Rust**; the app around it is **Kotlin + Jetpack Compose**.

---

## How it works

```
Telegram app  →  local MTProto (default 127.0.0.1:1443)  →  this app
              →  WSS  (via Cloudflare  or  direct)         →  Telegram DC
```

1. The Rust core starts a local MTProto endpoint and generates a connection
   secret.
2. Telegram is pointed at that local endpoint (`tg://proxy`).
3. For each session the core reads the target `DC ID`, opens a TLS WebSocket to
   the right data center, and — when enabled — routes it through Cloudflare.
4. A warm connection pool, keepalives, DoH name resolution, and route
   fallbacks keep it usable on flaky mobile networks.

---

## Features

- **Two routing modes** — direct to Telegram DCs, or tunneled through
  Cloudflare WebSocket domains. Custom Cloudflare domains are supported.
- **Auto route selection** — probe the direct route on start and fall back to
  Cloudflare automatically when it isn't reachable.
- **Anti-DPI TLS fragmentation** — optionally split the TLS ClientHello across
  several small TCP segments so SNI-based filtering can't match the hostname in
  a single packet. Off / Light / Medium / Aggressive presets.
- **Self-healing watchdog** — if a route stalls (active sessions but no traffic
  for a while) the proxy restarts itself, with backoff, instead of hanging.
- **Reachability test** — measure direct latency to Telegram data centers from
  inside the app.
- **Stay-alive helpers** — foreground service, wake-lock refresh, boot
  autostart, and a battery-optimization exemption prompt.
- **Quick controls** — a quick-settings tile and a live event-log viewer.
- **Modern UI** — Material 3, dynamic colors on Android 12+, built-in palettes,
  and localization in English, Russian, and Persian (with RTL support).
- **In-app update check** against the repository's GitHub releases.

---

## Quick start

1. Install the latest APK from this repository's **Releases** page, or build it
   yourself (below).
2. Open the app and skim the in-app **Info** help.
3. Tap **Start proxy** — a foreground notification confirms it's running.
4. Tap **Apply in Telegram** — a compatible client opens with the proxy
   pre-filled; confirm the connection there.

If a connection takes a long time, the watchdog will try to recover the route
on its own; you can also flip the Cloudflare toggle and compare.

---

## Build it yourself

The APK is produced by the `Build APK` GitHub Actions workflow
(`.github/workflows/build-apk.yml`) on every push: it compiles the Rust native
libraries with `cargo-ndk` for `arm64-v8a` and `armeabi-v7a`, then assembles
the release APKs and uploads them as build artifacts.

Locally you'll need the Android SDK/NDK, a Rust toolchain, and `cargo-ndk`:

```bash
# 1. native core → app/src/main/jniLibs/<abi>/libtgwsproxy.so
cargo ndk -t arm64-v8a   --platform 24 -o app/src/main/jniLibs build --release
cargo ndk -t armeabi-v7a --platform 21 -o app/src/main/jniLibs build --release

# 2. the APKs
./gradlew assembleRelease
```

Three flavors are built: `universal`, `arm64` (v8a), and `arm32` (v7a).

---

## Configuration notes

- **Cloudflare vs direct** — Cloudflare can be steadier on some carriers but
  depends on routing and DNS; direct is simpler when it isn't blocked. Auto mode
  picks for you.
- **WS pool** — number of pre-warmed WebSocket connections; 2–4 is enough for
  most cases.
- **TLS fragmentation** — start at Light and increase only if a network keeps
  blocking the handshake; stronger presets add tiny per-connection overhead.
- **Secret key** — a 16-byte MTProto key; only rotate it if your old
  connection link leaked.
- **Manual DCs** — normally unnecessary; mainly for diagnostics and unusual
  routes.

---

## Reporting problems

Use the in-app **Build report** button and attach its output to any issue —
it captures Android version, ABI, active settings, and recent errors. Minor
log warnings while the proxy is otherwise working can be ignored.

---

## License

Released under the **GNU GPLv3**. The underlying `tg-ws-proxy` idea is
originally distributed under the MIT license.

---

_This project is a community fork of the original **tg-ws-proxy** project, adapted and extended for Android._
