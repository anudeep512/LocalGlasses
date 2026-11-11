---
type: source
title: "LocalGlasses STT: sherpa-onnx + Whisper small.en int8"
status: insight
category: architecture
created: 2026-11-11
updated: 2026-11-11
slug: localglasses-stt-sherpa-onnx-whisper-small
---

# LocalGlasses STT: sherpa-onnx + Whisper small.en int8

Dictation into the Agent Command box (`MainActivity.kt:184`) chosen as sherpa-onnx ONNX Runtime + Whisper small.en int8, pinned from JitPack (jCP only distribution; settings needs `maven("https://jitpack.io")`). No NDK, no whisper.cpp.

Key constraints that drove it:
- Whisper has no streaming path in sherpa-onnx (`OfflineRecognizer` only) → live partials dropped, final transcript only.
- Endpointing uses Silero VAD bundled in sherpa-onnx (~2 MB): utterance ends on tap, 800 ms silence, or 15 s cap.
- `MemoryGuard.kt` documents resident Gemma 4 E2B at ~1.5–2 GB on a 7.65 GB phone (an older 6 GB figure was wrong), so both models fit; the 1 GiB `canLoad` threshold is reused for STT.
- Glasses mic uses DAT camera PCM (`AudioCodec.PCM(RATE_16000, 1)` → `stream.audioStream`), not DAT Speech, because the requirement is one local Whisper everywhere. See [localglasses dat audio paths](/sources/localglasses-dat-audio-paths.md).

Open risk: JitPack supply chain, and ~500 MB model download beside a 2.59 GB agent model.

*Category: architecture*

---
*Captured: 2026-11-11*

## Related

_Add links to related pages._

## Related

- [localglasses dictation](/concepts/localglasses-dictation.md)
- [litert lm gpu backend and engine init](/concepts/litert-lm-gpu-backend-and-engine-init.md)
