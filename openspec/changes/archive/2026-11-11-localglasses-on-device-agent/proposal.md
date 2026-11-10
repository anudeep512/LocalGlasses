# Proposal

## Why

LocalGlasses is a working glasses camera app, but every phone task still requires opening an app and operating it by hand. This change turns the app into a fully offline, on-device command surface: a typed command is resolved by a local Gemma 4 E2B model into a predefined action or a visible UI plan, approved by the user, and executed by a deterministic runner. Nothing leaves the phone.

## What Changes

- The agent screen becomes the app's home. The glasses camera moves to a tab. **BREAKING** for the current launch and navigation behaviour.
- The app registers an `ASSIST` activity and becomes the default assistant (long-press home). No `VoiceInteractionService` in this change; "Hey Google" is expected to stop working.
- Typed commands are interpreted by **Gemma 4 E2B** through **LiteRT-LM** with in-process `@Tool` schemas and constrained decoding. The model selects one task per command; it does not free-form chain tasks.
- Two execution paths: a deterministic action registry (call, SMS, WhatsApp, YouTube/YouTube Music, curated system settings) and a generic Accessibility plan for everything else.
- Every action, including media and system actions, produces an editable step plan and waits for explicit approval. Approval is the confirmation gate.
- Broad Accessibility automation is allowed, bounded by six absolute hard blocks, per-step checks, secure-screen aborts, and progress-based loop guards (no fixed step count).
- The model is downloaded once in-app (~2.59 GB, public Hugging Face artefact, checksum-verified), then runs offline. A foreground service keeps it warm and unloads it on critical memory or when free RAM drops below ~1 GB.
- Every interaction is recorded to an encrypted on-device trace store for later (v2) distillation into deterministic workflows. Traces are redacted before write, exported only on request, and kept until manual purge.
- Notification listener records metadata only; notification text is never stored. Usage access supplies foreground-app history.
- Out of scope for this change: glasses microphone, vision context, glasses audio, voice input, Spotify, alarms/timers, Telegram, cross-command memory, automatic workflow replay/promotion, and Play Store distribution.

## Capabilities

### New Capabilities

- `agent/commands`: command entry surfaces (agent home, camera tab, default-assistant activity), typed input, one-task-per-command routing, no-match suggestions, task-scoped state, and on-screen/TTS status.
- `agent/model-runtime`: Gemma 4 E2B acquisition and checksum verification, LiteRT-LM engine lifecycle, warm foreground service, memory-pressure unload, and constrained tool invocation.
- `agent/action-registry`: deterministic device actions for calls, SMS, WhatsApp, YouTube/YT Music, curated system settings, and direct-number or device-contact resolution.
- `agent/ui-automation`: AccessibilityService observation, plan generation for generic tasks, step execution, and progress-based loop guards.
- `agent/plan-approval`: editable plan preview for every action, approval/rejection, structured edits, natural-language re-plan, and the over-app execution overlay with a stop control.
- `agent/safety-policy`: absolute hard-block categories, secure-screen aborts, and notification-content limits, enforced before planning and at every step.
- `agent/trace-store`: encrypted on-device recording of commands, plans, model I/O, screen text, outcomes, and failure screenshots; redaction before write; retention, export, and purge.

### Modified Capabilities

None. `openspec/specs/` is currently empty, so every capability above is new.

## Impact

- `app/src/main/java/com/localglasses/app/`: new `agent/` packages (commands, model, actions, automation, approval, safety, trace), a navigation shell, and Application-level initialisation. Existing DAT session, capture, and recording code stays in place and keeps working.
- `app/src/main/AndroidManifest.xml`: Accessibility service, notification listener, special-use foreground service, overlay, `ASSIST` intent filter, usage access, contacts/call/SMS/settings permissions, launcher-app `<queries>`.
- `app/build.gradle.kts` and `gradle/libs.versions.toml`: `com.google.ai.edge.litertlm:litertlm-android` (pin 0.19.1), SQLCipher for Android (encrypted trace DB), kotlinx.serialization, OkHttp for the resumable model download, WorkManager, coroutines, and lifecycle-service dependencies.
- New runtime surfaces: model download manager, encrypted trace database, accessibility service, overlay UI, settings actions, contact resolver, TTS output.
- Device envelope: CPH2717 / MediaTek MT6897, 7.65 GB RAM, no NPU backend. Inference runs on CPU XNNPACK or Mali GPU; latency must be measured on-device.
- Distribution remains personal sideload, so Play policy restrictions do not apply.
