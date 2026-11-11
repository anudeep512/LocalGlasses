---
type: concept
title: LocalGlasses dictation
category: architecture
created: 2026-11-11
updated: 2026-11-11
---

# LocalGlasses dictation

Push-to-dictate for the agent command box. Speech is recognised **on the phone**; no audio
leaves the device. Added in the `speech-to-text` OpenSpec change.

## Flow

`DictationViewModel.onMicTap()` → `DictationController.run()` → `AudioSource.open()` →
`Endpointer` (Silero VAD) → `SherpaWhisperRecognizer` → `DictationOutcome.Transcript` →
`AgentViewModel.onDictationFinished(text, metrics)` replaces the command field. Submission stays
an explicit **Run** tap.

## Rules

- Utterance ends on: second tap, **2 s** of silence, or a 15 s cap (whichever first).
- A capture with no detected speech is discarded as `NoSpeech`; the field keeps its text.
- Live partial text is never shown; a level meter stands in for it.
- `RECORD_AUDIO` is requested on the first Dictate tap, not at launch.
- The assist gesture never starts capture.

## Audio sources

- `PhoneAudioSource` — `AudioRecord`, `VOICE_RECOGNITION`, 16 kHz mono 16-bit.
- `GlassesAudioSource` — DAT camera stream with `AudioCodec.PCM(AudioSampleRate.RATE_16000, 1)`
  and `stream.audioStream`; the Agent tab owns a **short-lived** `DeviceSession` per dictation
  and stops it when the utterance ends. `AudioCodec.PCM` is dev-and-beta only.
- The user picks the source in Settings (`DictationSourcePreference`, phone default). A selected
  source that is unavailable is an error telling the user to connect glasses — no silent
  fallback.

## Model

- sherpa-onnx **1.13.8** (JitPack, `com.github.k2-fsa.sherpa-onnx:sherpa-onnx`), `arm64-v8a` only.
- Whisper **small.en int8** ONNX: encoder 112,442,483 B, decoder 262,223,042 B, tokens 835,554 B,
  from `huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en` (see
  [[sources/huggingface-401-means-wrong-repo-not-blocked-network]]).
- Downloaded on first use, checksum-verified per file, resumable; `SpeechModelRepository` over the
  shared `VerifiedFileRepository`.
- Silero VAD is a **bundled asset** (`assets/silero_vad.onnx`, 629 KB), so endpointing works on a
  fresh install with no download.

## Metrics

Per dictation: model id, source, audio ms, recognition ms, real-time factor, endpoint reason,
word count, peak app PSS. Attached to the next request only while the field still holds the
transcript; the block is always visible in Stats — see
[[concepts/localglasses-stats-sections]].

## Tests

JVM: `PcmCodecTest`, `PhoneAudioSourceTest`, `GlassesAudioSourceTest`, `EndpointerTest`,
`DictationControllerTest` (13), `DictationViewModelTest`, `SpeechMetricsFactoryTest`,
`SpeechModelRepositoryTest`, `SpeechModelManifestTest`, `WordErrorRateTest`,
`EvaluationHarnessTest`.
On device: `DictationRecognitionTest` (real engine, bundled WAV), `DictationEndToEndTest`
(fixture → VAD → Whisper → command field), `DictationControlsTest`,
`DictationSourceSelectorTest`.

Related: [[entities/localglasses-app]], [[concepts/litert-lm-gpu-backend-and-engine-init]]
