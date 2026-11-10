---
type: synthesis
title: LocalGlasses on-device agent architecture
created: 2026-11-10
updated: 2026-11-10
---

# LocalGlasses on-device agent architecture

Decided 2026-11-10 in a Grill Me interview. Full turn log: `GRILL-ME.md` in the repo root.

## Goal

Make LocalGlasses the phone's one-stop command surface. You type (or invoke the assistant and type) a command such as `call Amma` or `play any song by Kanye West`. A local Gemma 4 E2B model decides *which* predefined action to run. A deterministic runner executes it. Nothing leaves the phone.

The glasses remain camera-only in v1. This is a phone agent that happens to ship inside the glasses app.

## Hardware envelope (measured with adb, not assumed)

| Fact | Value |
|---|---|
| Model | CPH2717 (OnePlus Nord CE5 Lite) |
| SoC | MediaTek **MT6897** / Dimensity 7300, 8 cores |
| RAM | **7.65 GB** physical |
| Swap / "extended RAM" | 7.86 GB, **~3 GB already used at idle** |
| Available before model load | **2.66 GB** |
| Android | 16 (SDK 36) |
| Free storage | 42 GB |
| NPU path | **None** — LiteRT-LM NPU backends are Qualcomm QNN and Google Tensor only |

The earlier plan assumed Snapdragon 7 Gen 2 with 8 GB. That was wrong on both counts. The practical consequence: CPU XNNPACK or Mali GPU inference, and a resident model competes with the apps the agent launches. Swap is compressed zram, and quantised weights compress badly, so swapping the model out produces very slow decode, not extra headroom.

## Model and runtime

- **Gemma 4 E2B** in `.litertlm` format, **2583 MB** on disk, 32k context supported, benchmarked at 2048.
- **LiteRT-LM** Kotlin API (`com.google.ai.edge.litertlm:litertlm-android`): `Engine`, `EngineConfig`, `Conversation`, `ConversationConfig`, `Backend.CPU()` / `Backend.GPU()`.
- Tool calling is built in: `ToolSet` + `@Tool` / `@ToolParam`, or `OpenApiTool` with a raw JSON schema. `automaticToolCalling = false` gives manual control.
- **Constrained decoding** (FST-backed `GemmaModelConstraintProvider`) keeps tool-call output valid JSON.
- MediaPipe LLM Inference is maintenance-only; do not start there.
- Android AICore / Gemini Nano is the recommended production path on *supported* devices, but not on this MediaTek phone, and it would not be app-controlled.

Details: [Gemma 4 E2B on LiteRT-LM for Android](/concepts/gemma-4-e2b-on-litert-lm-for-android.md).

## Command pipeline

```
text command
  -> LiteRT-LM conversation with the tool registry injected
  -> one of:
       a) registry action  (single @Tool call, typed slots)
       b) generic UI task  (multi-step Accessibility plan)
  -> plan preview (editable step list)  [always, for every action]
  -> approval
  -> TaskRunner executes steps
  -> trace written to encrypted on-device store
  -> screen status + phone TTS
```

### Path A — registry action

Fixed Kotlin executors with typed slots. The model only picks the action and fills arguments; it never invents behaviour.

- **Call**: direct phone number, or `READ_CONTACTS` + fuzzy match + disambiguation sheet, then `ACTION_CALL` with `CALL_PHONE`. The model never chooses an identity. There is no alias table.
- **SMS**: `SmsManager` + `SEND_SMS`; draft shown with the resolved contact and number.
- **WhatsApp**: deep link / prefill plus Accessibility steps for the send button. WhatsApp only; Telegram deferred.
- **YouTube / YouTube Music**: `ACTION_SEARCH` / media intents plus Accessibility for the first result.
- **Settings**: curated direct writes via `WRITE_SETTINGS` (screen-off timeout, brightness, volume, DND, rotation, flashlight) plus open-any-Settings-page actions. `change the sleep time to 60s` maps to `Settings.System.SCREEN_OFF_TIMEOUT`.
- **No match**: show the three nearest registered commands. Never fall back to an invented action.

Spotify, alarms/timers, and Telegram are **out of v1**.

### Path B — generic UI task

Broad `AccessibilityService` navigation with a plan the user sees and can edit. This is an explicit exception to the original "nothing thought on the fly" rule, gated by approval.

- Observation: compact accessibility-tree summary, not a raw dump.
- Plan: ordered steps (`tap node by text/id`, `type`, `scroll`, `wait`, `open app`).
- Editing: approve / edit step / reorder / delete / cancel, plus natural-language re-plan.
- Guards: no step ceiling. Stop on a repeated screen signature, three consecutive failed steps, a hard-block target, or the overlay stop button.
- Execution progress and stop control live in a `SYSTEM_ALERT_WINDOW` overlay, because the target app is in front.

## Determinism boundary

- One task per utterance. Registry path = one tool call. Generic path = one approved plan.
- No cross-command memory. State exists only inside the current task.
- Stateless prompt, constrained tool JSON, deterministic argument validation.
- The model may not emit arbitrary settings keys, arbitrary packages, or arbitrary taps without a visible plan.

## Approval and safety

Every action, including media and system, opens an editable plan and waits for approval. This supersedes an earlier risky-only auto-run decision.

Absolute hard blocks, enforced before planning and again at each step against both the foreground package and the target node:

- banking, UPI, payments, wallet apps
- password entry and password managers
- reading or entering OTPs
- account-password or security-setting changes
- installing / uninstalling apps
- deleting files, chats, or photos

Trace store: encrypted DB (Keystore-backed), redaction before write, package denylist, screenshot cap. Records command, task, step selectors, outcome, timings, model prompt/tool JSON, accessibility text, and failure screenshots only. Retention is forever until manual purge; export is JSONL/Markdown to Downloads. On-device only, no cloud sync.

Notification listener stores **metadata only** (package, timestamp, channel, grouping hash), never notification text, so OTP notifications cannot leak. Usage access supplies foreground-app history.

Details: [Accessibility-driven Android agent safety model](/concepts/accessibility-driven-android-agent-safety-model.md).

## Memory and service lifecycle

- Foreground service holds the engine warm. Battery-optimisation exemption requested; persistent notification.
- Resident by default, unload on Android's critical-memory callback or free RAM under ~1 GB; reload on the next command.
- `Engine.initialize()` is slow (~seconds); call it off the main thread.
- The resident model is ~1.5–2 GB. With 2.66 GB available at idle, launching a heavy media app can still evict something; the unload threshold exists for that reason.

## Model delivery

One-time in-app download from Hugging Face with checksum verification, then fully offline. Not bundled in the APK. 42 GB free makes this safe.

## Trace store and v2

v1 **records only**: no automatic replay, no workflow promotion. v2 will distil recorded traces into deterministic step lists and add prompt replay for debugging. That is the whole reason the trace schema exists now.

## Why the previous A2A / MCP plan was dropped

The prior design proposed A2A HTTP servers on localhost, then a "spec-driven local MCP pipeline" using stdio.

- **Android has no MCP stdio transport.** MCP over stdio is a desktop process convention; an Android app cannot spawn and pipe to MCP servers that way.
- **LiteRT-LM already provides the layer**: tool schemas from Kotlin annotations, constrained decoding, automatic or manual tool execution. A localhost HTTP/SSE layer only adds latency, ports, and foreground-service lifecycle problems.
- A2A agent cards and JSON-RPC buy nothing when the orchestrator and tools are in one process and one APK.

What survives from that plan: the single-monolith decision, constrained JSON output, and deterministic tool executors. What dies: HTTP servers, agent cards, and stdio MCP.

## v1 scope summary

In: call, SMS, WhatsApp, YouTube/YT Music, curated settings, generic Accessibility tasks with approval, trace recording, TTS, default-assistant launch.

Out: glasses mic/vision/audio, voice input, Spotify, alarms/timers, Telegram, cross-command memory, workflow replay, Play Store distribution.

## Open risks

- E2B decode speed on MT6897 is unmeasured; model-card benchmarks do not transfer.
- A 2B model planning Accessibility steps is the least proven part.
- No step cap plus small context means observation encoding must stay compact.
- Approve-every-action may feel heavy in daily use.
- Accessibility + full screen text + forever retention is a high-value local target; encryption and redaction are best-effort.
- `ACTION_CALL` and activity launches from an overlay may hit background-activity-start limits.
- OxygenOS Settings keys and assistant reassignment are unverified.
- Plan approval cannot be shown over `FLAG_SECURE` screens; the runner must abort there.

## Related

- [LocalGlasses app](/entities/localglasses-app.md)
- [Gemma 4 E2B on LiteRT-LM for Android](/concepts/gemma-4-e2b-on-litert-lm-for-android.md)
- [Accessibility-driven Android agent safety model](/concepts/accessibility-driven-android-agent-safety-model.md)
- [DAT real-hardware gotchas](/concepts/dat-real-hardware-gotchas.md)
