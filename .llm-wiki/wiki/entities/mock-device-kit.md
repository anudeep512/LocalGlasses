---
type: entity
title: Mock Device Kit
category: tool
created: 2026-11-07
updated: 2026-11-07
---

# Mock Device Kit

`mwdat-mockdevice` — DAT's simulated-glasses environment. It fakes the *entire* stack,
including registration and permission requests, so app code is identical against mock and
real hardware.

## Verified behaviour (LocalGlasses)

- `enable()` alone flips `registrationState` to `REGISTERED`. That is the first proof the
  DAT wiring works without the Meta AI app.
- `pairGlasses(GlassesModel.RAYBAN_META)` is **not sufficient** to start a session.
  Session start fails with *"No eligible device available"* until the mock is driven to a
  wearable state: `powerOn()` → `unfold()` → `don()`.
- With those three calls, `DeviceSessionState` reaches `STARTED`.
- Stream then reports `CRITICAL_STREAM_ERROR` and stays `STOPPED` until a camera feed is
  assigned — not a bug, just no media.

## Media

`glasses.services.camera.setCameraFeed(uri)` / `setCapturedImage(uri)`. Android does **not**
transcode, so mock video must be h.265/HEVC. Image formats: JPEG, PNG.

`setCameraFeed(CameraFacing)` uses the *phone* camera and therefore needs
`android.permission.CAMERA` — a host grant distinct from DAT's own `Permission.CAMERA`.
`setCameraFeed(uri)` has no such requirement.

See also [DAT session lifecycle](/concepts/dat-session-lifecycle.md).
