# Tasks

## 1. Build and dependency setup

- [x] 1.1 Add `maven("https://jitpack.io")` to `dependencyResolutionManagement` in `settings.gradle.kts` and pin the sherpa-onnx version in `gradle/libs.versions.toml`; verify `./gradlew help` resolves the repository list without error.
- [x] 1.2 Add the sherpa-onnx dependency to `app/build.gradle.kts` and restrict packaged ABIs to `arm64-v8a`; verify `./gradlew assembleDebug` succeeds and `unzip -l app/build/outputs/apk/debug/app-debug.apk` lists only `arm64-v8a` native libraries.
- [x] 1.3 Declare `RECORD_AUDIO` and `android.hardware.microphone` (`required="false"`) in `AndroidManifest.xml`; verify the merged manifest with `./gradlew processDebugMainManifest` and inspect the generated merged file for both entries.

## 2. Speech model artefact and storage

- [x] 2.1 Extract the download, checksum, partial-file, and verified-marker behaviour of `ModelRepository` into a reusable `VerifiedFileRepository` parameterised by a manifest, leaving agent-model paths and WorkManager behaviour unchanged; verify the existing `ModelDownloaderTest` and `ModelManifestTest` still pass and `./gradlew test` is green.
- [x] 2.2 Author `assets/speech-model.json` with the encoder, decoder, and tokenizer URLs, byte sizes, and sha256 values captured from the distribution host, and add `SpeechModelManifestParser` with rejection tests for missing files, non-https URLs, and malformed checksums; verify with new JVM tests plus `./gradlew test`.
- [x] 2.3 Add a speech-model repository instance to `AgentContainer` with a `ModelState`-shaped flow and first-use download entry point; verify a JVM test covers absent, downloading, verifying, ready, failed, and cancelled states.

## 3. Audio capture and endpointing

- [x] 3.1 Add the `AudioSource` interface and `PhoneAudioSource` using `AudioRecord` at 16 kHz mono 16-bit with `VOICE_RECOGNITION`, including the runtime permission gate; verify a JVM test covers the byte-to-sample conversion and a denied-permission path returns a typed failure.
- [x] 3.2 Implement the Silero VAD endpointer with the 800 ms silence rule and the 15 s cap, and the no-speech discard rule; verify JVM tests drive synthetic frames through silence-only, speech-then-silence, tap-stop, and cap-stop cases.
- [ ] 3.3 Implement `GlassesAudioSource` on a short-lived `DeviceSession` using `AudioCodec.PCM(RATE_16000, 1)` and `stream.audioStream`, with a typed unavailable result when the SDK reports no audio support; verify a JVM test covers PCM frame conversion and an instrumentation check on hardware confirms capture starts and the session stops when the utterance ends.

## 4. Recognition engine

- [x] 4.1 Add the recogniser wrapper around the sherpa-onnx offline recogniser plus an engine holder that lazily loads, reports loaded state, and refuses to load when `MemoryGuard.canLoad()` is false and releases on critical trim; verify JVM tests with a fake recogniser cover lazy load, refusal, and release.
- [x] 4.2 Add an instrumentation test that feeds a bundled WAV fixture through the real engine and asserts the expected phrase and a non-zero real-time factor; verify `./gradlew connectedDebugAndroidTest` passes with the model present.
- [x] 4.3 Record audio duration, recognition latency, real-time factor, model identifier, source, endpoint reason, and peak app PSS for every dictation, including failed and no-speech runs; verify a JVM test asserts the measured block is produced on success and on failure with no transcript.

## 5. Dictation UI and command integration

- [x] 5.1 Implement `DictationController` and `DictationViewModel` as the state machine over idle, connecting, capturing, transcribing, and error states, resolving the selected source and model state before capture; verify JVM tests cover every transition, a source-unavailable error, and a model-absent prompt.
- [x] 5.2 Add the microphone control, recording indicator, and input level meter to the Agent screen, disabled during submission and pending approval, with no partial transcript rendered; verify Compose UI tests assert the control's enabled state per condition and that the field is unchanged while capturing.
- [x] 5.3 Forward only the final transcript to `AgentViewModel.onCommandChange` so the field is replaced and never submitted, leaving a typed command untouched on cancel, interruption, empty transcript, or recognition failure; verify Compose UI tests cover replacement, no-auto-submit, cancellation, and the assist-entry case where no capture starts.
- [x] 5.4 Add the dictation source selector to the Settings screen with phone as the default and persisted choice, showing glasses as unavailable when the build lacks audio support; verify a Compose UI test asserts the default, a changed and restored selection, and the unavailable rendering.
- [x] 5.5 Document dictation in `README.md`: the microphone control, the permission prompt, source selection, the first-use model download, and the beta-only glasses limitation; verify the documented steps match the shipped screens.

## 6. Metrics surface

- [x] 6.1 Add the speech metrics block to the request metrics model, persist and decode it tolerantly, and attach it to the next request only while the field still holds the dictated text; verify tests cover attachment, clearing on manual edit, clearing after submission, and decoding a snapshot written before the field existed.
- [x] 6.2 Present the speech metrics in the Stats for nerds block and in expanded Stats rows, hide the section for typed commands, and show absent values as unavailable; verify Compose tests cover dictated, typed, missing-value, and stats-disabled cases.

## 7. Local evaluation harness

- [x] 7.1 Add a debug-only harness that reads WAV files and reference transcripts from an app-private, gitignored directory and writes a word-error-rate and real-time-factor report for the current model; verify a JVM test scores a bundled fixture pair and produces the expected report lines.
- [x] 7.2 Add the personal corpus directory to `.gitignore` and confirm the harness output stays outside traces and exports; verify `git status --porcelain` stays clean after running the harness.

## 8. Integration verification

- [x] 8.1 Run end-to-end phone dictation on the target device: grant the permission, download the model, dictate a contact-name command, confirm the transcript in the field, and confirm submission requires an explicit tap; verify by manual run-through with the field and stats screenshots recorded in the change notes.
- [ ] 8.2 Run end-to-end glasses dictation on the target device in a beta build: select the glasses source, confirm the connecting state, dictate, and confirm the session stops afterwards; verify by manual run-through plus the DAT error path when glasses are disconnected.
- [x] 8.3 Run `./gradlew lint test` and the connected instrumented tests, and confirm no warning is introduced in the files this change touches; verify by the clean report.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied.
- Verify the archived specs and the updated capability list after archiving.

## Archive note

Archived on 2026-11-12 with 2 of 24 tasks open, both blocked on hardware rather than on code:

- **3.3** — the glasses microphone path is implemented (`GlassesAudioSource`,
  `DatGlassesAudio`, 6 unit tests), but the on-hardware check needs glasses paired with
  Developer Mode.
- **8.2** — the glasses end-to-end run needs the same hardware.

Everything else is verified: 223 JVM tests, the instrumented suites, real Whisper recognition on
the CPH2717, and the phone-mic path confirmed by the user ("call Amma").
