---
type: entity
title: Meta AI app
category: product
created: 2026-11-07
updated: 2026-11-07
---

# Meta AI app

Meta's phone app and the required bridge for [DAT](/concepts/dat-session-lifecycle.md)
development. It is not optional plumbing — it *is* the registration and permission broker.

## Role in DAT

- Registration: `Wearables.startRegistration(activity)` hands off to the Meta AI app for approval.
- Permissions: camera access is granted **through the Meta AI app**, not Android's dialog.
  `Wearables.checkPermissionStatus(Permission.CAMERA)` reports the result.
- Firmware: glasses updates and version checks happen here.
- [Developer Mode](/concepts/dat-developer-mode.md): the toggle that lets unpublished apps work.

## Practical checks

- Glasses firmware: Devices → gear → General → About → Version, compared against Meta's
  version-dependency matrix.
- Dev-mode apps: Settings → App connections → Developer mode apps.
- The app must be installed and signed in **on the same phone** running the debug build.

On an emulator without the Meta AI app, `registrationState` reports `UNAVAILABLE` — the
expected signal that the device cannot register, and the reason
[Mock Device Kit](/entities/mock-device-kit.md) exists.
