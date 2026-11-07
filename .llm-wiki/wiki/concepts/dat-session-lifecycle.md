---
type: concept
title: DAT session lifecycle
domain: engineering
created: 2026-11-07
updated: 2026-11-07
---

# DAT session lifecycle

The single most common source of DAT bugs: **session state and capability state are two
different machines**, and capabilities can only attach in one window.

## Session states

```
IDLE → STARTING → STARTED → (PAUSED) → STOPPING → STOPPED
```

`STOPPED` is **terminal**. A stopped session cannot restart; call
`Wearables.createSession(...)` again.

## The rules that bite

1. `session.start()` is fire-and-forget — returns `Unit`, reports failure through
   `session.errors`. Subscribe to `state` and `errors` **before** calling `start()` or the
   first transition is lost.
2. `session.addCamera(...)` / `addDisplay()` before `STARTED` fails with
   `DeviceSessionError.SESSION_IDLE`. After `STOPPED`, `SESSION_ALREADY_STOPPED`.
3. Attach capabilities *from inside* the state collector, guarded so it runs once.
4. `Camera.stop()` and `Stream.stop()` invalidate the instance permanently. To reattach, call
   `session.removeCamera()` first.
5. A session implies neither streaming nor display access — capabilities are separate.

## Stream states

```
STOPPED → STARTING → STARTED → STREAMING → STOPPING → STOPPED   (CLOSED = terminal)
```

`PAUSED` is *not* an error — the device pauses streaming on a single cap-touch tap and can
resume by itself. Watch `camera.stream.errorStream` alongside state:
`STREAM_ERROR` is informational, `CRITICAL_STREAM_ERROR` means tear down.

## Transitions the SDK initiates

Another experience taking over the device, glasses folded, Bluetooth drop, app unregistered
or permission revoked.

Implemented in `SessionViewModel` for
[LocalGlasses](/syntheses/localglasses-android-setup.md); hardware-free reproduction via
[Mock Device Kit](/entities/mock-device-kit.md).
