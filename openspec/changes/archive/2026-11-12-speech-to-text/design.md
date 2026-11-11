# Design

See `proposal.md` for motivation and `specs/agent/dictation/spec.md` for the behaviour contract.

## Context

- The command surface is one `OutlinedTextField` in `MainActivity.kt` bound to
  `AgentViewModel.onCommandChange`, submitting through `AgentViewModel.submit()`. `AgentUiState`
  holds `command`, `status`, `isSubmitting`, and `suggestions`.
- There is no microphone use anywhere: no `RECORD_AUDIO` permission, no `AudioRecord`, no
  capture code. Output speech is the platform `TextToSpeech` in `TtsSpeaker`.
- The agent model is a single `.litertlm` artefact described by `assets/model.json`, downloaded
  by `ModelDownloader` (resumable, sha256, free-space check), managed by `ModelRepository`
  (verified marker file, `ModelState` flow, WorkManager retry), and loaded through `EngineHolder`
  guarded by `MemoryGuard` (1 GiB availability threshold, unload on
  `TRIM_MEMORY_RUNNING_CRITICAL` / `TRIM_MEMORY_COMPLETE`).
- `MemoryGuard.kt` documents a resident Gemma 4 E2B at roughly 1.5–2 GB on this 7.65 GB device,
  which leaves room for a second, smaller model.
- DAT 1.0.1 exposes camera streaming. `AudioCodec.PCM(AudioSampleRate.RATE_16000, 1)` plus
  `stream.audioStream` yields raw 16 kHz mono PCM, but requires an active camera stream and is
  marked dev-and-beta only. `mwdat-speech` exists but performs recognition on the glasses, which
  the change explicitly does not use.
- The build has no NDK, no CMake, no `jniLibs`, and no CI workflow. `settings.gradle.kts` allows
  only `google()` and `mavenCentral()` with `FAIL_ON_PROJECT_REPOS`.
- Metrics are modelled in `MetricsModels.kt` (`DeviceSnapshot`, `InferenceMetrics`,
  `RequestMetrics`), collected per request by `MetricsCollector.beginRequest()`, persisted with
  the trace, and rendered by `ui/stats`.

## Goals / Non-Goals

**Goals:**

- Add on-device English dictation with no cloud dependency and no audio retention.
- Keep one recognition model for both audio sources, so phone and glasses behave the same.
- Keep the existing command, routing, safety, and approval behaviour untouched.
- Make the speech runtime swappable behind a small interface, so a future model or a
  Whisper.cpp comparison does not touch the screen or the ViewModel.

**Non-Goals:**

- Streaming or partial transcription, wake-word detection, non-English models.
- Using the DAT Speech capability, or any recogniser that runs off the app process.
- Replacing or resizing the Gemma agent model, or sharing memory budget code beyond `MemoryGuard`.
- A committed model bake-off: the harness scores one shipping model for regression purposes.

## Decisions

### Recogniser runtime: sherpa-onnx

Use the sherpa-onnx Android AAR (`com.github.k2-fsa.sherpa.onnx:sherpa-onnx`, pinned) from
JitPack, with the Kotlin `OfflineRecognizer` and `Vad` APIs.

- Alternatives: whisper.cpp (ggml) needs NDK + CMake in a build that has neither, supports only
  Whisper, and has no official Android artefact. Building it was rejected as unnecessary
  toolchain risk for a single model family.
- Cost: JitPack is the only distribution channel. Pin the version and keep a vendored AAR as the
  documented fallback if resolution or the artifact itself proves unstable.
- `settings.gradle.kts` gains `maven("https://jitpack.io")` inside
  `dependencyResolutionManagement`; this is the first non-Google/Maven-Central repository in the
  project, so the change is explicit and reviewable.
- Restrict packaging to `arm64-v8a`; the AAR carries every ABI (~47 MB).

### Model: Whisper small.en int8 ONNX

`small.en` is the smallest artefact that reliably handles accented English and contact names at
command length. int8 is chosen over float for decode speed on CPU; the accuracy loss at small
size is smaller than the accuracy lost by dropping to `base.en`.

- The model consists of encoder, decoder, and tokenizer files. All three are pinned in a speech
  model manifest and verified individually, because a partial set produces unusable output
  rather than a clean failure.
- Exact byte sizes and sha256 values are recorded when the manifest is authored; they are not
  asserted in this document because the artefact was not downloadable from the build host.

### Batch recognition, no partials

Whisper has no streaming decoder in sherpa-onnx. Partials would mean re-decoding the whole
buffer on a timer, which on this CPU class costs more than it communicates. The UI therefore
shows a level meter and a transcribing state, and inserts text once.

- Alternative considered: add a small streaming Zipformer purely for partials. Rejected: two
  recognisers, two accuracy profiles, two failure modes for cosmetic feedback.

### Endpointing with Silero VAD

`Vad` ships in sherpa-onnx and needs no extra dependency, but the Silero model itself is a
629 KB file. It is bundled as an app asset (`assets/silero_vad.onnx`) rather than downloaded, so
endpointing works on a fresh install with no network. Far more robust than an RMS threshold in
the street noise and off-axis glasses-mic conditions this app is used in.

- The utterance end condition is: user tap, 2 s of VAD silence, or a 15 s cap. Two
seconds rather than under one, because a pause mid-command is normal and a short window cuts
sentences in half.
- VAD is only an endpoint detector; audio is not pre-trimmed before decoding, so a mis-tuned
  threshold degrades latency, not content.

### Audio capture abstraction

A single `AudioSource` interface exposes `Flow<ShortArray>` at 16 kHz mono plus a state flow.
Two implementations:

- `PhoneAudioSource`: `AudioRecord` with `VOICE_RECOGNITION` source, 16 kHz, mono, 16-bit.
- `GlassesAudioSource`: owns a short-lived `DeviceSession`, waits for `STARTED`, calls
  `addCamera(StreamConfiguration(audioCodec = AudioCodec.PCM(RATE_16000, 1)))`, collects
  `stream.audioStream`, and stops the session when capture ends. Video frames are ignored.

Glasses session ownership stays with dictation, not the Camera tab. Sharing the Camera tab's
session would couple two screens and leave a camera stream running with no visible reason.

### Dictation as its own controller and ViewModel

`DictationController` (in `agent/dictation`) owns capture, endpointing, recognition, and the
engine. `DictationViewModel` exposes `DictationState` to Compose. The screen forwards only the
final transcript to `AgentViewModel.onCommandChange`, so the command field keeps exactly one
owner and `submit()` keeps its existing guarantees.

- Alternative: push dictation state into `AgentViewModel`. Rejected: it already carries routing,
  plan, trace, and metrics concerns, and every new field would widen its test surface.

### Model storage: generic repository, separate manifest

Extract the download/verify/marker behaviour of `ModelRepository` into a reusable
`VerifiedFileRepository` parameterised by a manifest, and have both the agent model and the
speech model use it. `ModelManifestParser` stays agent-specific; a new
`SpeechModelManifestParser` validates the three-file speech set.

- Alternative: a second copy of the repository code. Rejected: two divergent checksum/retry
  paths is exactly the kind of duplication that later breaks resume.
- The agent model's on-disk paths, marker semantics, and WorkManager behaviour stay identical, so
  no migration is needed.

### Engine lifetime

Lazy load on the first dictation, keep resident afterwards, and release on critical memory trim
or when `MemoryGuard.canLoad()` is false before loading. Both models coexist under normal use.

- Alternative: load per utterance. Rejected: 0.5–1 s of model load per command, for a model that
  is smaller than the agent model it sits beside.

### Metrics attachment

Add `SpeechMetrics` to `RequestMetrics` (model id, source, audio duration, recognition latency,
real-time factor, endpoint reason, peak app PSS during recognition, transcript-derived word
count — no transcript text). `DictationController` produces the block; `AgentViewModel` attaches
it to the next request only when the command field still holds the dictated text unchanged, and
clears it on manual edit, on a new dictation, and after submission.

- Alternative: store the transcript in metrics. Rejected: it duplicates trace content and widens
  the data-retention surface for no analytical gain.
- Rendering: the Speech Recognition section is always present, like the inference section, and
  is fed by the latest dictation as soon as recognition finishes — before the command is
  submitted. A missing value reads as unavailable instead of hiding the row.

### Beta-channel audio

Glasses audio stays behind the DAT beta APIs, as the user accepted. The source selector shows the
glasses option as unavailable when the SDK reports the capability missing, and phone dictation is
independent of DAT entirely.

## Risks / Trade-offs

- [JitPack resolution or artifact changes] → pin the version, keep `libs/sherpa-onnx.aar` vendoring
  documented as the fallback, and fail the build loudly rather than silently resolving another
  version.
- [~400–500 MB speech model beside a 2.59 GB agent model] → free-space precheck already exists in
  `ModelDownloader`; the download prompt states the size; the file is deletable from Settings.
- [Glasses dictation needs an active camera stream] → short-lived session, `VideoQuality.LOW`,
  stop on utterance end, and a visible connecting state so the cost is attributable.
- [Glasses session collides with the Camera tab's session] → dictation stops its own session
  before returning, and a failed start reports the DAT error instead of retrying silently.
- [Whisper hallucination on silence or music] → VAD endpointing plus an empty-transcript guard
  that leaves the field untouched.
- [Accent and name accuracy] → the local WER harness scores the shipping model against recorded
  commands, and the review-before-submit step is the user-facing guard.
- [English-only model cannot handle Hindi or code-mixed commands] → documented limitation;
  changing it is a model swap behind `AudioSource`/recogniser interfaces.
- [Native memory is invisible to the Java heap] → measure app PSS around recognition, and release
  the engine on trim instead of trusting GC.
- [Audio privacy] → buffers are released after transcription; no audio file, trace attachment, or
  export ever contains speech.

## Migration Plan

- Additive only: no database schema break, no changed command semantics, no model migration.
- First launch after upgrade: microphone permission is requested on the first dictation tap, and
  the speech model download is offered there. Nothing happens in the background.
- Rollback: remove the microphone control and the dependency; stored traces keep their speech
  metrics block as unknown fields, which the tolerant `MetricsJson` decoder already ignores.

## Open Questions

- Resolved: the model lives at `huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en`
  (not `k2-fsa`, which returns 401 for a repo that does not exist). Pinned sizes and checksums
  are 112,442,483 / `8bdac288…462f` (encoder int8), 262,223,042 / `710ccf89…77fb` (decoder
  int8), and 835,554 / `306cd27f…d930` (tokens). All three were verified by an on-device
  download, and the encoder and decoder hashes match Hugging Face's own LFS metadata.
- Whether `audioStream` delivers PCM continuously enough for Silero VAD during the 2–4 s glasses
  bring-up; validate on hardware and fall back to a minimum-capture guard if it does not.
- Whether JitPack resolves reproducibly enough for a future CI pipeline; if not, switch to the
  vendored AAR without changing any other decision.
