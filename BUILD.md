# 编译 Android APK 指南

适用于 `tg-ws-proxy-android`（Rust 引擎 + Kotlin UI）。

---

## 目录

1. [前提条件](#1-前提条件)
2. [环境配置](#2-环境配置)
3. [编译 Rust 原生库](#3-编译-rust-原生库)
4. [编译 Android APK](#4-编译-android-apk)
5. [完整一键编译](#5-完整一键编译)
6. [输出文件说明](#6-输出文件说明)
7. [APK 签名说明](#7-apk-签名说明)
8. [常见问题](#8-常见问题)

---

## 1. 前提条件

| 工具 | 最低版本 | 安装确认 |
|------|---------|---------|
| Rust 工具链 | 1.70+ | `rustc --version` |
| Android SDK | API 35 | `sdkmanager --list` |
| Android NDK | 任意版本 | SDK Manager 中安装 |
| JDK | 17 | `java -version` |
| Gradle | 9.1（使用项目 wrapper） | — |

### Rust 额外组件

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk
```

---

## 2. 环境配置

### 2.1 Android SDK

通过 Android Studio 的 SDK Manager 安装：

- **Android SDK Platform 35**（compileSdk）
- **Android NDK**（任意版本）

### 2.2 local.properties

在项目根目录创建 `local.properties`，将 `sdk.dir` 指向你的 Android SDK 路径：

```properties
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk

# 以下为发布签名配置（可选的，见第 7 节）
KEYSTORE_FILE=../release.keystore
KEYSTORE_PASSWORD=your_pass
KEY_ALIAS=your_alias
KEY_PASSWORD=your_pass
```

> Windows 路径中反斜杠需转义为 `\\` 或使用正斜杠 `/`。

### 2.3 项目结构

编译涉及两部分代码：

```
项目根目录
├── src/                          ← Rust 引擎（7 个源文件）
│   ├── lib.rs                    C-ABI 导出入口
│   ├── config.rs                 配置与常量
│   ├── proxy.rs                  MTProto 代理核心
│   ├── ws.rs                     WebSocket 连接
│   ├── cfproxy.rs                Cloudflare 代理
│   ├── crypto.rs                 加密（AES-256-CTR）
│   └── balancer.rs               负载均衡
├── app/
│   └── src/main/
│       ├── java/...tgwsproxy/    ← Kotlin UI（JNA 桥接）
│       └── jniLibs/              编译输出的 .so 文件
├── build_so.bat                  一键编译 Rust → .so
└── build_apk.bat                 一键编译 .so → APK
```

---

## 3. 编译 Rust 原生库

将 Rust 源码交叉编译为 Android 可加载的 `.so` 文件。

### 3.1 手动执行

```bash
REM arm64-v8a（现代 64 位设备，API 24+）
cargo ndk ^
  -t arm64-v8a ^
  --platform 24 ^
  -o app/src/main/jniLibs ^
  build --release

REM armeabi-v7a（旧 32 位设备，API 21+）
cargo ndk ^
  -t armeabi-v7a ^
  --platform 21 ^
  -o app/src/main/jniLibs ^
  build --release
```

各参数含义：

| 参数 | 说明 |
|------|------|
| `-t <target>` | Rust 三元组目标（`arm64-v8a` / `armeabi-v7a`） |
| `--platform <N>` | Android API 级别，决定 NDK 链接的 libc 版本 |
| `-o <dir>` | 输出目录，Gradle 的 `jniLibs.srcDirs` 指向此处 |
| `build --release` | 以 release profile 编译 |

### 3.2 使用脚本

```bash
build_so.bat
```

脚本自动完成：

1. 检测 NDK 路径（从 `local.properties` 的 `sdk.dir` 推导）
2. 安装 `cargo-ndk`（如缺失）
3. 添加 Rust Android targets
4. 编译 arm64-v8a 和 armeabi-v7a

### 3.3 编译产出

```
app/src/main/jniLibs/
├── arm64-v8a/
│   └── libtgwsproxy.so    ← 64 位 ARM
└── armeabi-v7a/
    └── libtgwsproxy.so    ← 32 位 ARM
```

> **磁盘占用**：Release 编译的结果约 1–3 MB（已 strip 符号、LTO 优化、panic=abort）。

### 3.4 Rust 编译配置说明

`Cargo.toml` 中 release profile 的优化设置：

```toml
[profile.release]
opt-level = "z"      # 优化体积（最小化）
lto = true           # 链接时优化
codegen-units = 1    # 单编译单元（最大化内联）
panic = "abort"      # panic 直接终止（移除展开代码）
strip = true         # 剥离符号表
```

---

## 4. 编译 Android APK

### 4.1 前提：.so 文件已就位

运行 `build_apk.bat` 前，确认以下文件存在：

- `app/src/main/jniLibs/arm64-v8a/libtgwsproxy.so`
- `app/src/main/jniLibs/armeabi-v7a/libtgwsproxy.so`

### 4.2 手动执行

```bash
gradlew assembleRelease
```

### 4.3 构建产物（Gradle 原始输出）

```
app/build/outputs/apk/
├── universal/release/app-universal-release.apk   ← 双 ABI（arm64 + arm32）
├── arm64/release/app-arm64-release.apk           ← 仅 64 位
└── arm32/release/app-arm32-release.apk           ← 仅 32 位
```

### 4.4 Gradle product flavors

项目定义了三个 flavor（`app/build.gradle.kts:24-50`）：

| Flavor | ABI | minSdk | 推荐场景 |
|--------|-----|--------|---------|
| `universal` | arm64-v8a + armeabi-v7a | 21 | 通用发布（文件较大） |
| `arm64` | arm64-v8a | 24 | 现代设备（推荐） |
| `arm32` | armeabi-v7a | 21 | 旧设备 |

### 4.5 Gradle 版本及插件

| 组件 | 版本 |
|------|------|
| Gradle | 9.1 |
| Android Gradle Plugin | 9.0.1 |
| Kotlin | 2.1.20（Compose 编译器插件） |
| Compose BOM | 2024.12.01 |
| JNA | 5.14.0@aar |

---

## 5. 完整一键编译

项目提供了两个批处理脚本串联两阶段编译：

```bash
REM 第 1 步：编译 Rust → .so
build_so.bat

REM 第 2 步：编译 .so → APK + 重命名输出
build_apk.bat
```

`build_apk.bat` 额外完成：

1. 从 `app/build.gradle.kts` 读取 `versionName`
2. 校验 `.so` 文件是否存在
3. 运行 `gradlew assembleRelease --no-daemon`
4. 将 APK 复制到 `app/release/` 并重命名：

```
app/release/
├── v1.2.3-android-universal.apk     ← 双 ABI
├── v1.2.3-android-v8a-minsdk24.apk  ← 仅 64 位
└── v1.2.3-android-v7a-minsdk21.apk  ← 仅 32 位
```

---

## 6. 输出文件说明

### 6.1 APK 大小构成

APK 包含的主要体积来源：

| 组件 | 内容 | 说明 |
|------|------|------|
| `libtgwsproxy.so` (×2) | Rust 引擎 | 1–3 MB 每个架构 |
| Kotlin 字节码 | Compose UI + JNA 桥 | ~2–5 MB（经 R8 缩小） |
| 资源 | Material3、图标、字体 | ~1–2 MB（经 shrinkResources） |

### 6.2 APK 安装兼容性

| APK 文件 | Android 版本 | 架构 |
|----------|-------------|------|
| `*-universal.apk` | 5.0+ (API 21) | arm64 + arm32 |
| `*-v8a-minsdk24.apk` | 7.0+ (API 24) | 仅 arm64 |
| `*-v7a-minsdk21.apk` | 5.0+ (API 21) | 仅 arm32 |

---

## 7. APK 签名说明

### 7.1 签名流程

Gradle 在 `buildTypes.release` 中的逻辑（`app/build.gradle.kts:84-106`）：

1. 读取 `local.properties` 的 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`
2. 如果 keystore 文件存在 → 使用 release 签名
3. 如果 keystore 不存在或用 debug → 使用 Android SDK 的 debug keystore

三种签名方式均启用：V1 + V2 + V3。

### 7.2 无 keystore 的情况

```
⚠️ WARNING: Keystore not found, using debug signing
```

debug 签名的 APK 可以正常安装和测试，**但无法发布**到任何商店。

### 7.3 发布准备

如需发布，在项目根目录的父目录放置 `release.keystore`，并在 `local.properties` 填写：

```properties
KEYSTORE_FILE=../release.keystore
KEYSTORE_PASSWORD=你的密钥库密码
KEY_ALIAS=你的别名
KEY_PASSWORD=你的别名密码
```

---

## 8. 常见问题

### 编译阶段

| 问题 | 原因 | 解决 |
|------|------|------|
| `Error: arm64-v8a .so not found!` | 未先编译 Rust | 先运行 `build_so.bat` |
| `error: linker `aarch64-linux-android` not found` | 缺少 Android Rust target | `rustup target add aarch64-linux-android` |
| `error: failed to run custom build command for openssl-sys` | 项目不用 OpenSSL（用 rustls） | 确认 `Cargo.toml` 未误加 openssl 依赖 |
| `Could not find com.android.application:9.0.1` | Gradle 插件未缓存 | 检查网络，或用 `gradlew --offline` |
| `BUILD FAILED` (Gradle) | 缺少 SDK 组件 | 用 SDK Manager 安装 API 35 |
| `AAPT2 error` | 资源文件问题 | 检查 `res/` 下 XML 是否有中文未转义 |

### 运行时

| 问题 | 原因 | 解决 |
|------|------|------|
| APK 安装后打开闪退 | ABI 不匹配 | 安装 `universal` 版本 |
| 安装提示 `INSTALL_FAILED_NO_MATCHING_ABIS` | APK 不含当前设备架构 | 装 `universal` 版本 |
| 启动代理后无反应 | `.so` 加载失败 | 检查 `logcat -s TgWsProxy` 中的 JNA 错误 |
| 代理启动但 Telegram 连不上 | 网络/路由问题 | 在应用设置中切换 Cloudflare 开关对比 |

---

> 本文档仅描述编译 APK 的流程。应用使用说明请参阅 [README.md](README.md)。
