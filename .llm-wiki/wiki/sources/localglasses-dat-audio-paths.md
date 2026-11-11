---
type: source
title: "DAT glasses audio: Speech vs camera PCM"
status: insight
category: api
created: 2026-11-11
updated: 2026-11-11
slug: localglasses-dat-audio-paths
---

# DAT glasses audio: Speech vs camera PCM

Two ways to get glasses audio in Meta Wearables DAT v1.0, both flagged dev-and-beta only:

- **Speech capability** (`com.meta.wearable:mwdat-speech`, 1.0.1 on Maven Central): on-glasses recogniser; `start()`/`transcriptions` gives partial plus final `TranscriptionResult` with confidence. Recognition happens on the glasses, so it is *not* your model. Requires `Permission.MICROPHONE`.
- **Camera PCM**: `StreamConfiguration(audioCodec = AudioCodec.PCM(AudioSampleRate.RATE_16000, 1))`, then collect `stream.audioStream` (`frame.buffer`, `presentationTimeUs`). Raw 16 kHz mono PCM you can feed to whisper.cpp or sherpa-onnx. Requires an active camera stream, so video frames flow too, plus DAT `Permission.CAMERA`.

LocalGlasses uses the PCM path so one local Whisper serves both phone and glasses audio. That also means the Agent tab owns a short-lived `DeviceSession` per dictation and surfaces the 2–4 s bring-up delay.

*Category: api*

---
*Captured: 2026-11-11*

## Related

_Add links to related pages._

## Related

- [localglasses dictation](/concepts/localglasses-dictation.md)
- [dat session lifecycle](/concepts/dat-session-lifecycle.md)
