# Norlu Keyboard (openkeyboard) - Project Instructions

## Project Overview
Norlu Keyboard is a high-performance, privacy-focused Android Input Method Editor (IME) with a core engine written in Rust. It utilizes a Dual-Trie architecture for sub-millisecond fuzzy search and intelligent word prediction.

### Architecture
- **Core Engine (Rust):** Handles linguistic processing, fuzzy search (Levenshtein distance with matrix pruning), and probabilistic scoring.
- **Storage Layer (Rust):** Uses `sled` for persistent user vocabulary, encrypted with `ChaCha20Poly1305` using keys from the Android Keystore.
- **Android Layer (Kotlin):** Implements the `InputMethodService` (`AdvancedKeyboardService.kt`) and a custom `OptimisedKeyboardView.kt`.
- **FFI Bridge:** JNI interface defined in `src/ffi/mod.rs` and consumed by `RustEngineAsync.kt`.

### Key Features
- **Dual-Trie Design:** Combines a static base dictionary with a dynamic, learnable user dictionary.
- **Fuzzy Search:** Optimized Levenshtein search that executes in < 1ms.
- **Dynamic Hitboxes:** Predicts next character probabilities to adjust touch areas (WCAG AAA compliance).
- **Privacy First:** `FLAG_SECURE` prevents screenshots; all processing happens on-device.

---

## Building and Running

### Prerequisites
- Rust (Edition 2024)
- Android NDK
- `cargo-ndk` (`cargo install cargo-ndk`)

### Rust Core
To cross-compile the Rust library for Android:
```bash
./build.sh
```
This script targets `arm64-v8a` and `x86_64`, moving the resulting `.so` files to `android/app/src/main/jniLibs/`.

### Android Application
Open the `android/` directory in Android Studio. Use standard Gradle tasks to build and install:
```bash
cd android
./gradlew assembleDebug
```

### Testing & Benchmarking
- **Rust Unit Tests:** `cargo test`
- **Benchmarks:** `cargo bench --bench fuzzy_search`

---

## Development Conventions

### Coding Style
- **Rust:** Follow standard idiomatic Rust (Edition 2024). Avoid `unsafe` except in JNI FFI boundaries.
- **Kotlin:** Use Coroutines for all native engine interactions to keep the UI thread responsive.

### FFI Guidelines
- JNI functions must be defined in `src/ffi/mod.rs`.
- Follow the naming convention `Java_com_norlu_openkeyboard_RustEngineAsync_<functionName>`.
- Use `Box::into_raw` and `Box::from_raw` for managing the lifecycle of the `KeyboardEngine` pointer across the JNI boundary.

### Storage & Security
- Never store user data in plain text. Use the `storage` module which abstracts encryption.
- The 32-byte secure key must be passed from Android Keystore during engine initialization.

### Documentation
- Architectural details are maintained in the `wiki/` directory. Refer to `01-Arquitetura-Rust.md` for algorithm specifics.
