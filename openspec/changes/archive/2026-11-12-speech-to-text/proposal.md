# Proposal

## Why

Every command in LocalGlasses must currently be typed, which is the wrong interaction for an
app whose whole premise is talking to your glasses. Adding on-device dictation removes the
keyboard from the command path without sending audio anywhere: recognition runs locally on the
phone, and the only network access is the one-time model download.

## What Changes

- Add a microphone control beside the existing Command field on the Agent screen. One tap starts
  listening, and the utterance ends on a second tap, on 800 ms of silence, or at a 15-second cap.
- Transcribe with a local Whisper small.en int8 model through the sherpa-onnx ONNX runtime. No
  cloud service, no Meta transcription capability, no audio leaves the device.
- Let the user choose the audio source in Settings: the phone microphone, or the glasses
  microphone delivered as raw 16 kHz mono PCM over an active DAT camera audio stream.
- Replace the Command field contents with the final transcript. Submission stays an explicit
  action, so a misheard phrase never executes anything on its own.
- Download the speech model on first use with progress, cancellation, resume, and checksum
  verification, reusing the download and verification pattern already used for the agent model.
- Record speech recognition timings and the model identity in the metrics snapshot, and show them
  in the existing stats surfaces.
- Add the microphone runtime permission and declare microphone hardware as optional.
- **BREAKING** for the release channel: glasses dictation relies on DAT audio APIs that are
  marked dev-and-beta only, so glasses dictation exists only in dev and beta builds. Phone
  dictation has no such dependency and works on the production channel.
- Out of scope: wake-word detection, live partial transcripts, languages other than English,
  speech output changes, and any use of the DAT Speech capability.

## Capabilities

### New Capabilities

- `agent/dictation`: on-device capture, endpointing, transcription, audio-source selection, model
  acquisition, and transcript insertion for the command screen.

### Modified Capabilities

- `agent/telemetry`: a request snapshot also records the speech recognition metrics of the
  dictation that produced its command.
- `agent/stats-view`: the stats surfaces present those speech recognition metrics, including the
  unavailable case.

## Impact

- `settings.gradle.kts`: add the JitPack repository for the sherpa-onnx Android artifact.
- `app/build.gradle.kts`: add the sherpa-onnx dependency, restrict packaged ABIs to `arm64-v8a`,
  and add the speech model manifest asset.
- `AndroidManifest.xml`: add `RECORD_AUDIO` and an optional microphone feature declaration.
- New `agent/dictation` package: audio source abstraction, recorder, endpointing, recognizer
  wrapper, engine holder, and speech model repository.
- `MainActivity.kt`: microphone control on the Agent screen and the source selector in Settings.
- `AgentViewModel.kt`: dictation state, transcript insertion, and status messages.
- `agent/telemetry`, `agent/trace`, and `ui/stats`: speech metric fields, persistence, and
  presentation.
- `agent/AgentContainer.kt`: wire the dictation engine and repositories.
- Tests: JVM tests for endpointing and manifest parsing; instrumentation tests with bundled WAV
  fixtures for recognition, plus a local-only WER harness.
- No change to command routing, safety policy, plan approval, or the existing agent model.
