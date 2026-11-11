---
domain: ai
type: concept
title: LiteRT-LM GPU backend and engine init
category: performance
created: 2026-11-11
updated: 2026-11-11
---

# LiteRT-LM GPU backend and engine init

The agent runs Gemma 4 E2B through LiteRT-LM 0.19.1. Production builds a `FallbackEngine`
(`agent/model/FallbackEngine.kt`) that tries `Backend.GPU()` and loads `Backend.CPU()` only if the
GPU cannot initialise.

## Numbers on CPH2717 (Mali-G615 MC6, 7.65 GB)

| | CPU | GPU |
|---|---:|---:|
| Engine init | ~0.9 s | ~9 s warm cache, **~103 s first ever** |
| Prefill | 157–477 tok/s | 536–827 tok/s |
| Time to first token | 6.7 s | **1.8 s** |
| Decode | 2.3–10.3 tok/s | 10.0–11.1 tok/s |
| Total, short command | 9.6 s | **4.6 s** |

GPU wins on prefill/TTFT, which dominates a short command; decode is a wash.

## What "Engine init" measures

`benchmarkInfo.initTimeInSecond`: wall time of `Engine.initialize()` — open and validate the
2.59 GB `.litertlm`, build the compute graph, backend preparation, allocate buffers. It is **per
engine instance**, not per token or per command, so every request in one process reports the same
value.

GPU pays three extra costs at init: weights are converted and uploaded (~782 MB weight cache),
OpenCL kernels are compiled for the driver (~13 MB program cache), and an OpenCL context/queue is
created. CPU memory-maps the weights and has none of that.

## Gotchas that cost hours

- **`EngineConfig.cacheDir` must already exist.** LiteRT fails with `INVALID_ARGUMENT: Cache
  directory does not exist or is not writable` — see
  [litert gpu silent cpu fallback missing cache dir](/sources/litert-gpu-silent-cpu-fallback-missing-cache-dir.md). `LiteRtEngine.load()` now
  `mkdirs()`s it and fails loudly.
- **A silent fallback hides configuration errors.** `FallbackEngine.backendName` reports what
  actually loaded; the app writes `files/engine.log` on fallback. Never trust "GPU is configured"
  as "GPU is running".
- **Keep the caches out of `cacheDir`.** Android may evict it; a rebuild costs a fresh ~100 s
  compile. They live in `files/gpu-cache`.
- **Prewarm.** `AgentViewModel` loads the engine as soon as the model is Ready, so the ~9 s init
  overlaps with typing instead of blocking Run.
- **Thermals:** an earlier benchmark hit ~88 °C and produced one tool-call parse failure, which is
  why CPU was once the deliberate default. Re-check on long sessions.

Related: [localglasses dictation](/concepts/localglasses-dictation.md), [localglasses app](/entities/localglasses-app.md)
