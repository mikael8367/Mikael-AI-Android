# Four Model Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an honest four-model catalog with separate MKAI and GGUF runtimes, safe downloads/imports, and a debug APK artifact.

**Architecture:** Preserve the custom MKAI JNI engine for Mikael AI. Add a pinned CPU-only llama.cpp JNI runtime for GGUF models. Put model metadata, validation and download lifecycle in small Kotlin classes; keep Compose as presentation.

**Tech Stack:** Kotlin, Jetpack Compose, Android DownloadManager/HTTP, C++17, Android NDK/CMake, llama.cpp, JUnit, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-10-four-model-catalog-design.md`

## Global Constraints
- Work only on `feature/github-native-connector`; do not merge or modify `main`.
- Preserve the custom MKAI engine and own-training pipeline.
- GGUF models must run only through llama.cpp; MKAI files only through the Mikael engine.
- Target ABI: `arm64-v8a`; CPU-only portable build for Android.
- Keep all inference local; no remote chat/API or WebView.
- Do not claim Mikael AI is trained before its own corpus and weights exist.
- Publish only a debug APK artifact after checks.

## Review Focus
- Partial/corrupt download or wrong file header: must never appear installed.
- Disk-space shortage or interrupted download: preserve previous model and clean temporary files.
- Switching model/engine while generating: cancel and unload before switching.
- Invalid/oversized context and memory pressure: reject before native allocation.
- App restart after model selection: restore correct engine and validate model file before load.

---

### Task 1: Model catalog domain

**Files:**
- Create: `app/src/main/java/com/mikael/ai/ModelCatalog.kt`
- Create: `app/src/test/java/com/mikael/ai/ModelCatalogTest.kt`

**Interfaces:**
- Produce `enum class ModelEngine { MKAI, LLAMA_CPP }`
- Produce `data class ModelDefinition(id, name, engine, fileName, downloadUrl, expectedBytes, sha256, license, description)`
- Produce immutable four-entry catalog and file-format validation helpers.

- [ ] Write tests for exactly four unique IDs, correct engine mapping, HTTPS URLs, allowed extensions, and no GGUF-to-MKAI routing.
- [ ] Run `gradle :app:testDebugUnitTest` and confirm the new tests fail for missing definitions.
- [ ] Implement catalog and validators.
- [ ] Run unit tests and confirm pass.

### Task 2: Native GGUF runtime

**Files:**
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/llama_engine_jni.cpp`
- Create: `app/src/main/java/com/mikael/ai/LlamaCppEngine.kt`
- Modify: `.github/workflows/android-apk.yml`

**Interfaces:**
- Kotlin: `load(modelPath: String, contextTokens: Int): Result<Unit>`
- Kotlin: `generate(prompt: String, maxTokens: Int): Result<String>`
- Kotlin: `cancel()`, `unload()`, `isReady()`
- Native calls use the pinned llama.cpp C API; no local HTTP server.

- [ ] Add tests for engine routing and context boundaries before wiring UI.
- [ ] Pin llama.cpp to a fixed release/commit and disable tools, examples, server, tests, OpenMP and GPU backends.
- [ ] Compile static llama/ggml libraries for arm64-v8a and link a JNI bridge.
- [ ] Implement synchronized model/context lifetime, tokenization, prompt evaluation, greedy sampling, stop conditions, cancellation and cleanup.
- [ ] Run native build, unit tests and lint in CI; inspect linker output and APK native libraries.

### Task 3: Safe model installation

**Files:**
- Create: `app/src/main/java/com/mikael/ai/ModelInstaller.kt`
- Create: `app/src/test/java/com/mikael/ai/ModelInstallerTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Download/import states: `Idle`, `Queued`, `Downloading(progress)`, `Verifying`, `Installed`, `Failed(message)`.
- Installer accepts a `ModelDefinition`, writes to a temporary file, validates size/header/checksum, then atomically promotes it.

- [ ] Test file-size boundaries, wrong magic, truncation and checksum mismatch.
- [ ] Implement resumable/progress-aware downloads with HTTPS and cancellation.
- [ ] Verify file integrity before exposing an installed model.
- [ ] Clean up partial files on failure and avoid deleting existing valid files.

### Task 4: Compose catalog and engine switching

**Files:**
- Modify: `app/src/main/java/com/mikael/ai/MainActivity.kt`
- Modify: `app/src/main/java/com/mikael/ai/AppPreferences.kt`
- Modify: `app/src/main/java/com/mikael/ai/MemoryBudget.kt`
- Create/update focused tests.

- [ ] Add catalog cards for Mikael AI, Qwen, SmolLM2 and TinyLlama with source/license, approximate file size, install progress, select/load/unload/delete states.
- [ ] Keep Mikael AI visibly marked as awaiting its own corpus/weights until a valid MKAI file exists.
- [ ] Persist selected model ID and engine; migrate any existing MKAI path without changing its meaning.
- [ ] Route generation only to the selected engine; cancel and unload the previous engine before switching.
- [ ] Guard buttons and errors for loading, generating, missing file, download, and low memory.

### Task 5: Deep QA and APK

**Files:**
- Modify docs/README as needed.
- Modify: `.github/workflows/android-apk.yml`

- [ ] Run all unit tests, native compile, lint and debug assemble from a fresh CI run.
- [ ] Inspect workflow job logs, artifact existence, APK ABI contents and APK size.
- [ ] Review all changed files for races, cancellation, overflow, path traversal, partial files, invalid GGUF/MKAI routing, license attribution and stale UI state.
- [ ] Report device-specific tests as not performed unless a physical device run is evidenced.
- [ ] Provide the debug APK artifact link only after successful CI; do not merge `main`.
