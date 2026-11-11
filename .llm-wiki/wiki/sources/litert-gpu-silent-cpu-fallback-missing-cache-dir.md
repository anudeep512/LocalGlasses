---
type: source
title: "GPU slower: silent CPU fallback from missing cache dir"
status: insight
category: bugfix
created: 2026-11-11
updated: 2026-11-11
slug: litert-gpu-silent-cpu-fallback-missing-cache-dir
---

# GPU slower: silent CPU fallback from missing cache dir

Symptom: after switching LiteRT-LM to `Backend.GPU()`, commands got **slower**, and the metal looked suspicious because the user had just asked for GPU.

Cause: the same change moved the engine cache from `cacheDir` to `filesDir/gpu-cache` **without creating the directory**. LiteRT-LM fails engine creation with `INVALID_ARGUMENT: Cache directory does not exist or is not writable: .../files/gpu-cache`. `FallbackEngine` caught that, loaded CPU, and the app kept working — so nothing looked broken except speed.

Lessons:
1. **A silent fallback hides configuration errors.** Measure the *effective* backend (`FallbackEngine.backendName`), and persist the fallback reason. This project now writes `files/engine.log` on fallback (overwritten, not appended).
2. **Create every directory you hand to a native library.** `LiteRtEngine.load()` now `mkdirs()`s `cacheDir` and fails loudly if it cannot.
3. **This OEM's logcat drops the app's own log tags.** `android.util.Log` from the app never appeared. Writing to an app-private file was the only way to see the error; keep that escape hatch for on-device diagnosis.
4. Numbers on CPH2717 (Mali-G615): GPU ≈ 1.8 s TTFT, 4.6 s total, 10.8 tok/s after a warm cache; CPU fallback ≈ 6.7 s TTFT, 9.6 s total, 10.3 tok/s. GPU's win is prefill/TTFT, not decode.
5. First GPU load on a device compiles OpenCL kernels: ~100 s once, then ~9 s per cold start (cache in `filesDir`, not evictable `cacheDir`). Prewarming when the Agent screen opens hides that behind typing.

Related: [localglasses stt sherpa onnx whisper small](/sources/localglasses-stt-sherpa-onnx-whisper-small.md).

*Category: bugfix*

---
*Captured: 2026-11-11*

## Related

_Add links to related pages._

## Related

- [litert lm gpu backend and engine init](/concepts/litert-lm-gpu-backend-and-engine-init.md)
- [localglasses app](/entities/localglasses-app.md)
