# Grill Me Results

Generated: 2026-11-11T18:46:15.848Z

## Plan

we want to add speech-to-text feature we want to use explore all opensource speech to text models which should run on my device locally which is then presented on the text box and example setup can be to use sherpa-onnx or whisper.cpp backend with whisper models we want to evaluate all such options and come up with best model for local speech-to-text

## Shared Understanding

Add local speech-to-text dictation to the Agent Command box. A mic button beside the existing OutlinedTextField (MainActivity.kt:184) records 16 kHz mono PCM, endpoints on Silero VAD silence, transcribes with Whisper small.en int8 through a pinned sherpa-onnx (ONNX Runtime) AAR, and replaces the Command field contents. The user reviews the transcript and taps Run — never auto-submitted. Audio source is selectable: phone mic via AudioRecord, or Meta glasses mic via DAT camera AudioCodec.PCM (dev/beta-only APIs, beta channel accepted) with the Agent tab owning a short-lived glasses session. No DAT Speech, no whisper.cpp, no NDK, no live partials, English only.

## Questions and Answers

### 1. Which microphone feeds the recognizer?

**Recommended answer:** Phone microphone via AudioRecord

**User answer:** Both, user-selectable in Settings

**Status:** resolved

**Notes:** Glasses audio scope refined in rounds 2 and 3.

### 2. How does the user start and stop dictation?

**Recommended answer:** Tap to start, auto-stop on silence (VAD endpointing)

**User answer:** Tap to start, tap again to stop

**Status:** resolved

**Notes:** Stop semantics reconciled in round 2: tap, 800 ms silence, or 15 s cap, whichever comes first.

### 3. What happens to the transcript in the Command box?

**Recommended answer:** Replace field contents, user taps Run

**User answer:** Replace field contents, user taps Run

**Status:** resolved

### 4. Which on-device inference backend?

**Recommended answer:** sherpa-onnx only

**User answer:** sherpa-onnx only

**Status:** resolved

**Notes:** Superseded in round 3: whisper.cpp only, everywhere. Sherpa-onnx and its JitPack dependency are out of scope.

### 5. Is the model comparison a deliverable of this change?

**Recommended answer:** Same change, phased: benchmark harness then wiring

**User answer:** user whisper small for now that shold be sufficient

**Status:** resolved

**Notes:** No cross-family bake-off; a WER harness survives per round 2 corpus answer.

### 6. Language coverage for v1?

**Recommended answer:** English only

**User answer:** English only

**Status:** resolved

### 7. Which models go into the comparison?

**Recommended answer:** Whisper tiny.en, Whisper base.en, Zipformer English, Moonshine base, SenseVoice small

**User answer:** Whisper small.en (~500 MB)

**Status:** resolved

**Notes:** whisper.cpp GGML quants change the real sizes; quantified in round 4 Q2.

### 8. How does the STT model reach the device?

**Recommended answer:** Download on first use, checksum verified

**User answer:** Download on first use, checksum verified

**Status:** resolved

### 9. Show live partial text while the user is speaking?

**Recommended answer:** Final transcript only

**User answer:** Live partial text in the box

**Status:** resolved

**Notes:** Resolved in round 2 to final-only after the Whisper streaming limitation was explained.

### 10. Utterance cap and silence endpoint?

**Recommended answer:** 15 s cap / 800 ms silence

**User answer:** 15 s cap / 800 ms silence

**Status:** resolved

### 11. STT engine lifetime?

**Recommended answer:** Load once, keep resident, release under memory pressure

**User answer:** Load once, keep resident, release under memory pressure

**Status:** resolved

### 12. Record STT metrics in the existing Stats-for-Nerds surface?

**Recommended answer:** Yes, add STT fields to the metrics surface

**User answer:** Yes, add STT fields to the metrics surface

**Status:** resolved

### 13. Does the assist-gesture rule change?

**Recommended answer:** Keep: assist never starts capture, mic is a deliberate tap

**User answer:** Keep: assist never starts capture, mic is a deliberate tap

**Status:** resolved

### 14. How is the native dependency taken?

**Recommended answer:** JitPack, pinned version

**User answer:** JitPack, pinned version

**Status:** resolved

**Notes:** Superseded by the whisper.cpp switch: JitPack sherpa-onnx artifact is no longer needed. Round 4 Q1 re-opens native integration.

### 15. What is the evaluation corpus?

**Recommended answer:** Bundled fixtures + personal recorded command set

**User answer:** Bundled fixtures + personal recorded command set

**Status:** resolved

**Notes:** Round 2 confirmed purpose; round 3 confirmed personal audio stays out of git.

### 16. How do we resolve live partials versus a non-streaming Whisper?

**Recommended answer:** Final transcript only, show indicator + level meter

**User answer:** Final transcript only, show indicator + level meter

**Status:** resolved

### 17. Which model is evicted when both STT and Gemma cannot fit in RAM?

**Recommended answer:** Evict Gemma while dictating, reload after

**User answer:** Keep both resident, accept OOM / process kill risk

**Status:** resolved

**Notes:** Premise corrected: MemoryGuard.kt documents Gemma at ~1.5-2 GB on a 7.65 GB phone, so both fit; the 6 GB figure came from an older summary. Keep the 1 GiB canLoad threshold for STT and evict on TRIM_MEMORY_RUNNING_CRITICAL.

### 18. Is glasses microphone audio in scope for this change?

**Recommended answer:** Phone mic now behind an AudioSource seam, glasses deferred

**User answer:** Implement glasses mic in this change too (needs approval + new artifacts)

**Status:** resolved

**Notes:** Route settled in round 3: no DAT Speech; glasses PCM into whisper.cpp.

### 19. Which condition ends an utterance?

**Recommended answer:** Tap OR 800 ms silence OR 15 s cap ends it

**User answer:** Tap OR 800 ms silence OR 15 s cap ends it

**Status:** resolved

### 20. What is the evaluation corpus for?

**Recommended answer:** Fixtures for regression tests + personal set for a WER harness

**User answer:** Fixtures for regression tests + personal set for a WER harness

**Status:** resolved

### 21. What happens to existing Command box text if capture is cancelled or recognition fails?

**Recommended answer:** Restore the pre-capture text

**User answer:** Leave whatever partial transcript arrived

**Status:** resolved

**Notes:** With partials disabled there is no partial text, so this resolves to: cancel or failure leaves the pre-existing field text untouched and never clears a typed command.

### 22. Which glasses-microphone path: DAT Speech or camera PCM into a local model?

**Recommended answer:** DAT Speech on-glasses recognizer

**User answer:** use whisper.cpp only everywhere for speech to text, for both phone microphone and Meta glasses microphone; do not use Meta inbuilt speech to text

**Status:** resolved

**Notes:** Forces Camera AudioCodec.PCM RATE_16000 mono into whisper.cpp, and drops mwdat-speech from scope.

### 23. How should the dev-and-beta-only DAT audio APIs be handled?

**Recommended answer:** Gate glasses audio behind a build flag / debug menu

**User answer:** Accept dev/beta-only APIs; ship the app on the beta channel

**Status:** resolved

**Notes:** AudioCodec.PCM is dev-and-beta-only per DAT v1.0 docs; production channel would drop glasses dictation.

### 24. What happens on mic tap when the selected audio source is unavailable?

**Recommended answer:** Fall back to phone mic, show which source is active

**User answer:** Refuse and tell the user to connect glasses

**Status:** resolved

**Notes:** Selected-source failure is a hard error with a message; no silent fallback.

### 25. Should personal voice recordings be committed to the repo?

**Recommended answer:** Gitignore personal recordings, commit fixtures only

**User answer:** Gitignore personal recordings, commit fixtures only

**Status:** resolved

### 26. How is whisper.cpp integrated into this build?

**Recommended answer:** Git submodule + CMake module, pinned NDK

**User answer:** Third-party JitPack whisper.cpp AAR

**Status:** resolved

**Notes:** PROVISIONAL/VOID: the follow-up message said ONNX, so this whisper.cpp answer is superseded. If whisper.cpp ever returns, flag that third-party wrappers are unmaintained and unpinned.

### 27. Which whisper.cpp model file is the default?

**Recommended answer:** small.en q8_0, ~252 MB

**User answer:** small.en q8_0, ~252 MB

**Status:** resolved

**Notes:** PROVISIONAL/VOID: GGML quantisation applies only to whisper.cpp, not ONNX. Re-asked in ONNX terms.

### 28. Which whisper.cpp variants does the WER harness compare?

**Recommended answer:** small.en q5_1, q8_0 and f16 plus base.en q8_0

**User answer:** small.en q8_0 (~252 MB)

**Status:** resolved

**Notes:** PROVISIONAL/VOID: re-asked in ONNX terms.

### 29. Which lifecycle brings up glasses audio streaming?

**Recommended answer:** Agent tab starts its own short-lived session per dictation

**User answer:** Agent tab starts its own short session per dictation

**Status:** resolved

**Notes:** Backend-independent; stands whichever runtime wins. AudioCodec.PCM RATE_16000 mono over an active camera stream, camera permission included, 2-4 s connect delay surfaced in status.

### 30. Which backend is final: sherpa-onnx (ONNX) or whisper.cpp (ggml)?

**Recommended answer:** sherpa-onnx with Whisper models

**User answer:** sherpa-onnx (ONNX) + Whisper models only

**Status:** resolved

**Notes:** Settles the round-3 conflict. ONNX Runtime, prebuilt AAR, no NDK; non-Whisper families excluded (Zipformer/SenseVoice/Moonshine out).

### 31. How is sherpa-onnx taken as a dependency?

**Recommended answer:** JitPack pinned AAR

**User answer:** JitPack pinned AAR

**Status:** resolved

**Notes:** Requires a jitpack.io repository in settings.gradle.kts; current config is google() + mavenCentral() with FAIL_ON_PROJECT_REPOS. arm64-v8a only.

### 32. Which Whisper artefact is the default?

**Recommended answer:** small.en int8 ONNX

**User answer:** small.en int8 ONNX

**Status:** resolved

**Notes:** Encoder+decoder+tokenizer, roughly 400-500 MB unverified; exact size and sha256 pinned in the model manifest.

### 33. What does the WER harness compare?

**Recommended answer:** tiny.en int8, base.en int8, small.en int8

**User answer:** small.en int8

**Status:** resolved

**Notes:** No cross-model comparison. Harness degrades to a single-variant WER/RTF baseline and regression check.

### 34. How is the 800 ms silence endpoint detected?

**Recommended answer:** Silero VAD from sherpa-onnx (~2 MB), endpointing only

**User answer:** Silero VAD from sherpa-onnx (~2 MB), endpointing only

**Status:** resolved

**Notes:** Endpointing only, not a decode gate. No extra dependency: Vad ships in sherpa-onnx.

## Agreed Decisions

- Entry point: mic button next to the Command OutlinedTextField (MainActivity.kt:184). Transcript replaces AgentUiState.command via AgentViewModel.onCommandChange; user taps Run; no auto-submit, so a misheard phrase never executes an action.
- Backend: sherpa-onnx (ONNX Runtime) pinned from JitPack as com.github.k2-fsa.sherpa-onnx:sherpa-onnx. Add maven("https://jitpack.io") to settings.gradle.kts (currently google()+mavenCentral() with FAIL_ON_PROJECT_REPOS). arm64-v8a only. No NDK, no CMake.
- Models: Whisper family only. Default artifact is Whisper small.en int8 ONNX (encoder + decoder + tokenizer), downloaded on first use with a pinned ModelManifest and sha256 verification, reusing ModelRepository/ModelDownloadWorker/ModelDownloadState. Exact byte size and checksum are captured when the manifest is written.
- Recognition API: sherpa-onnx OfflineRecognizer (Whisper has no streaming path). Final transcript only — no live partial text. UI shows a recording indicator plus an input level meter instead.
- Endpointing: Silero VAD (ships inside sherpa-onnx, ~2 MB) detects the 800 ms silence endpoint. Utterance ends on tap, on 800 ms silence, or at the 15 s cap, whichever comes first. Audio is 16 kHz mono PCM. VAD is used for endpointing only, not as a decode gate.
- Audio sources: phone microphone via AudioRecord, and Meta glasses microphone via DAT camera streaming with StreamConfiguration(audioCodec = AudioCodec.PCM(AudioSampleRate.RATE_16000, 1)) and stream.audioStream. User selects the source in Settings.
- Source failure: no silent fallback. If the selected source is unavailable (glasses disconnected, permission denied), the mic tap fails with a message telling the user to connect glasses.
- Glasses lifecycle: the Agent tab owns its own short-lived DeviceSession — wait for STARTED, addCamera with the audio codec configured, stream.start(), stop the session when the utterance ends, and show a connecting state for the 2-4 s bring-up. Not coupled to the Camera tab session.
- Beta-channel decision: DAT marks AudioCodec.PCM and the audio sample rates as dev-and-beta only. The app ships on the beta channel; the phone-mic path stays independent of DAT so it works on the production channel too.
- Engine lifetime: lazy-load on first dictation, keep resident, respect MemoryGuard's 1 GiB canLoad threshold, and release on TRIM_MEMORY_RUNNING_CRITICAL or TRIM_MEMORY_COMPLETE. Gemma (~1.5-2 GB per MemoryGuard.kt on the 7.65 GB device) and Whisper small.en int8 can coexist.
- Metrics: add STT fields to the existing Stats-for-Nerds surface — model id, audio duration, decode time, real-time factor, peak RSS, active source, and endpoint reason.
- Language: English only, .en model, no custom text post-processing beyond whitespace trim. Whisper supplies casing and punctuation.
- Assist rule unchanged: the assist gesture opens the command screen without starting capture; the mic is always a deliberate tap (openspec/specs/agent/commands/spec.md requirement on default-assistant entry).
- Cancellation: cancel or recognition failure leaves whatever text was already in the Command box untouched; a failed dictation never clears a typed command. With partials disabled no speculative text is ever inserted.
- Evaluation corpus: committed synthetic WAV fixtures drive deterministic instrumentation tests; personal voice recordings live in a gitignored directory and produce a WER/RTF baseline report for the chosen model. No cross-family bake-off — the harness compares only the single shipping variant against its own history.

## Open Risks

- JitPack is the only distribution channel for the sherpa-onnx Android AAR, and the repo currently resolves only google() + mavenCentral(). Pin the version and verify the resolved artifact; keep a vendored-AAR fallback if JitPack proves flaky.
- AudioCodec.PCM is dev-and-beta only, so glasses dictation is unsupported on the production release channel. Phone-mic dictation is unaffected.
- Glasses dictation requires an active camera stream, so video frames also flow: extra battery and thermal cost, and it needs DAT camera permission. Concurrent sessions with the Camera tab are untested and may conflict.
- Whisper small.en int8 ONNX is roughly 400-500 MB (exact bytes unverified; the Hugging Face API was unreachable here) and sits alongside a 2.59 GB agent model. Download UX and disk budget need a story.
- Whisper hallucinates on silence or music. Silero endpointing reduces but does not remove it; the review-before-Run step is the real guard.
- The .en model cannot handle Hindi or mixed Hindi-English commands, which is plausible for this user's command style. Revisit if commands are misrecognised.
- Third-party JitPack whisper.cpp AAR was once chosen in a provisional round; it is void and must not leak into the implementation.
- The AAR ships every ABI (~47 MB); confirm abiFilters actually strips unused ABIs from the APK.
- Personal voice recordings are sensitive: keep the personal corpus directory gitignored (repo hygiene rule applied to voice data).

## Next Decision Needed

None outstanding. Proceed to /opsx-propose with a change name such as command-dictation.
