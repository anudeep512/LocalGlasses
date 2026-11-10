---
type: entity
title: LocalGlasses app
category: project
created: 2026-11-07
updated: 2026-11-07
---

# LocalGlasses app

Android app in `/home/anudeep512/GodsNut/projects/LocalGlasses`, built on the
[Meta Wearables DAT](/entities/meta-wearables-device-access-toolkit-android-sdk.md).

Repository: **https://github.com/anudeep512/LocalGlasses** (private) — pushed 2026-11-07,
commit `345072e`, 64 files. A fresh clone builds with `./gradlew :app:assembleDebug` given
`JAVA_HOME=/opt/android-studio/jbr` and `ANDROID_HOME=$HOME/Android/Sdk`.

Status: **working against real Meta glasses.** Registration, session, live camera feed, photo
capture, and video recording all run on hardware. Verified on a OnePlus CPH2717 (Android 16)
with Ray-Ban Meta glasses (`RB Meta 0HD0`): `Session: STARTED`, `Stream: STREAMING`,
`504x896` frames rendering in the preview, and a `1080x1440` photo saved to
`Pictures/LocalGlasses/`.

## Feed source policy

**Real glasses only.** Mock Device Kit and the phone-camera feed were both removed on request —
the `mwdat-mockdevice` dependency, `mock/MockDevice.kt`, the mock UI, and the host
`android.permission.CAMERA` are all gone. If no eligible glasses are present the app refuses to
fabricate a feed and reports *"No eligible device available"*.

## Layout

```
app/src/main/java/com/localglasses/app/
├── LocalGlassesApp.kt          holds datReady; initializeDat() after BT permission
├── MainActivity.kt             permission gate -> GlassesScreen
├── session/SessionViewModel.kt DeviceSession + Camera + capture + recording
├── capture/VideoRecorder.kt    I420 -> H.264 via MediaCodec + MediaMuxer
└── capture/MediaStoreSaver.kt  gallery writes
```

## Runtime sequence

1. `RootScreen` checks `BLUETOOTH_CONNECT`, then calls `app.initializeDat()`.
2. `GlassesScreen` renders only once `datReady` is true; then starts the devices observer.
3. **Register** → Meta AI approval → `REGISTERED`.
4. **Start session** → `STARTED`; camera attached on that transition.
5. `checkPermissionStatus(Permission.CAMERA)` gates `stream.start()`; a
   `Wearables.RequestPermissionContract()` button grants it via Meta AI.
6. Frames: `videoStream` → planar I420 → NV21 → preview; and → I420 → H.264 → MP4.

## Invariants it encodes

- capabilities attach only on `DeviceSessionState.STARTED`
- `state` and `errors` are collected before `start()`
- a stopped session is discarded, never reused
- `Wearables.initialize()` runs *after* the runtime Bluetooth grant
- all `MediaCodec` calls share one single-permit dispatcher
- captures are written with `IS_PENDING` then cleared, so the gallery never shows partial files

## Agent direction (2026-11-11)

The app is being extended from a glasses camera app into a **phone-only, fully on-device
command agent**. Typed command -> local **Gemma 4 E2B** via LiteRT-LM -> either one
deterministic registry action (call, SMS, WhatsApp, YouTube, curated settings) or a multi-step
Accessibility plan -> editable plan preview -> approval -> runner -> encrypted on-device trace.
The glasses remain camera-only in v1. The agent screen becomes home; the camera moves to a tab.
The app becomes the default assistant via an `ASSIST` activity.

Security is part of the architecture: absolute hard blocks (banking/payments, passwords, OTP,
account security, install/uninstall, deletion), approval for every action, progress-based loop
guards, Keystore-backed encrypted traces, redaction before write, and notification metadata only.

This supersedes no existing camera behaviour; the DAT session, capture, and recording code stay.

- [LocalGlasses on-device agent architecture](/syntheses/localglasses-on-device-agent-architecture.md)
- [Gemma 4 E2B on LiteRT-LM for Android](/concepts/gemma-4-e2b-on-litert-lm-for-android.md)
- [Accessibility-driven Android agent safety model](/concepts/accessibility-driven-android-agent-safety-model.md)

## Known open items

- Video recording is wired but **not yet confirmed on real glasses** — the last recording test ran
  on the phone-camera mock before mock was deleted. The colour-space fix (I420) landed after the
  last recording run, so first real recording should be re-checked.
- `Last error` can hold a stale "Camera permission needed" string after the permission resolves
  successfully; cosmetic only.

## Related

- [DAT real-hardware gotchas](/concepts/dat-real-hardware-gotchas.md) — the four faults that
  blocked real hardware
- [YUV to H.264 recording on MediaTek](/concepts/yuv-to-h264-recording-on-mediatek.md)
- [DAT session lifecycle](/concepts/dat-session-lifecycle.md)
- [LocalGlasses Android setup](/syntheses/localglasses-android-setup.md)
