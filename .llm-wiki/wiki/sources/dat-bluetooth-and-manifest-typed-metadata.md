---
type: source
title: "DAT silent registration killers: BT permission order and typed meta-data"
status: insight
category: dat
created: 2026-11-07
updated: 2026-11-07
slug: dat-bluetooth-and-manifest-typed-metadata
---

# DAT silent registration killers: BT permission order and typed meta-data

Two silent registration killers in Meta Wearables DAT Android, both of which leave the UI looking healthy.

**1. `BLUETOOTH_CONNECT` must be granted before `Wearables.initialize()`.**
Initializing from `Application.onCreate()` throws inside the SDK's bonded-device scan:

```
E DAT:CORE:BluetoothDeviceDetection: Security exception while checking bonded devices
java.lang.SecurityException: Need android.permission.BLUETOOTH_CONNECT permission
  for …: AdapterService getBondedDevices
    at BluetoothDeviceDetectionProvider.checkForTargetDevices
    at Wearables.initialize
```

DAT only re-checks on registration changes, so it never recovers — the app reports **"No eligible device available"** with perfectly paired glasses. Fix: request the runtime grant in the UI, then call an idempotent `initializeDat()`, and gate every DAT flow read on that success.

**2. DAT manifest meta-data must be STRING typed.**
`manifestPlaceholders["mwdat_client_token"] = "0"` is stored by AAPT as `INT_DEC`. `Bundle.getString()` returns `null`, so registration dies with `Attestation failed: TOKEN_NOT_CONFIGURED` ("App client token is not configured") and never opens the Meta AI app. Point the meta-data at `<string>` resources and verify with `aapt2 dump xmltree` that the value resolves to `@0x7f…` rather than `=0`.

**Diagnostic pattern:** keep `capturePhoto()` wired while debugging the stream. Its bitmap comes from the glasses' own encoder, so a good photo plus a bad preview proves the fault is in your YUV handling, not the hardware.

See [[concepts/dat-real-hardware-gotchas]] and [[entities/localglasses-app]].


*Category: dat*

---
*Captured: 2026-11-07*

## Related

_Add links to related pages._
