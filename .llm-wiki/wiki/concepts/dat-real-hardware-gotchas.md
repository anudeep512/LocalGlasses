---
type: concept
title: DAT real-hardware gotchas (Android)
category: dat
created: 2026-11-07
updated: 2026-11-07
---

# DAT real-hardware gotchas (Android)

Four separate faults made a working-looking DAT integration fail on real Meta glasses while a
mock passed. Each one hides behind a plausible-looking UI state, so they are worth recognising by
symptom.

## 1. `BLUETOOTH_CONNECT` must be granted *before* `Wearables.initialize()`

Symptom: glasses are paired in Android Bluetooth, the app is `REGISTERED`, yet
`Wearables.createSession(AutoDeviceSelector())` fails with **"No eligible device available"** —
forever.

```
E DAT:CORE:BluetoothDeviceDetection: Security exception while checking bonded devices
java.lang.SecurityException: Need android.permission.BLUETOOTH_CONNECT permission
  for AttributionSource@…: AdapterService getBondedDevices
    at BluetoothDeviceDetectionProvider.checkForTargetDevices
    at RegistrationManagerImpl.prepareWithContext
    at Wearables.initialize
    at LocalGlassesApp.onCreate
```

`Wearables.initialize()` scans bonded devices at construction time. On Android 12+ that needs the
runtime `BLUETOOTH_CONNECT` grant. Initializing from `Application.onCreate()` guarantees it is
missing. DAT only re-checks on registration changes, so it never recovers.

Fix: initialize from the UI **after** the permission result arrives, and keep an idempotent
`initializeDat()` on the `Application`.

```kotlin
val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { r ->
    if (r.values.all { it }) app.initializeDat()
}
LaunchedEffect(Unit) {
    if (hasBluetoothPermissions()) app.initializeDat() else launcher.launch(arrayOf(BLUETOOTH_CONNECT))
}
```

Every DAT flow read (`registrationState`, `devices`, `devicesMetadata`) must be gated on that
successful init, or it throws `NOT_INITIALIZED`.

## 2. Manifest meta-data must be STRING typed

Symptom: registration never launches the Meta AI app; log shows

```
E DAT:CORE:RegistrationManager: Attestation failed: TOKEN_NOT_CONFIGURED
```

with the SDK string *"App client token is not configured"*.

`manifestPlaceholders["mwdat_client_token"] = "0"` is numeric, so AAPT stores it as
`INT_DEC`. `Bundle.getString()` then returns `null`, and DAT treats the token as absent. The docs
say the placeholders can be `0` in Developer Mode, but the *type* matters.

Fix: point the meta-data at string resources.

```xml
<meta-data android:name="com.meta.wearable.mwdat.APPLICATION_ID"
    android:value="@string/mwdat_application_id" />
<meta-data android:name="com.meta.wearable.mwdat.CLIENT_TOKEN"
    android:value="@string/mwdat_client_token" />
```

```xml
<string name="mwdat_application_id">0</string>
<string name="mwdat_client_token">0</string>
```

Verify with `aapt2 dump xmltree`: the value must resolve to `@0x7f…`, not `=0`.

## 3. DAT camera permission is its own grant, through the Meta AI app

Symptom: session `STARTED`, stream `STOPPED`, error *"Permissions denied"*.

```
E DAT:STREAM:StreamImpl: DWA stream control error: DWA stream request failed:
  ERROR_CODE_PERMISSION_DENIED - Permission 'android.permission.CAMERA' denied for capability CAPABILITY_STREAM
```

This is **not** the Android `CAMERA` permission. Remove the host permission; request DAT's:

```kotlin
val launcher = rememberLauncherForActivityResult(Wearables.RequestPermissionContract()) { /* … */ }
launcher.launch(Permission.CAMERA)
```

Gate `stream.start()` on `Wearables.checkPermissionStatus(Permission.CAMERA) == Granted`, and
retry after the contract returns. Approval itself happens inside the Meta AI app.

## 4. `VideoFrame.buffer` is **planar I420**, not NV12 or NV21

Symptom: luma is right, colours are wrong — a green/magenta cast that survives a chroma byte
swap. `capturePhoto()` returns a perfect image, which proves the glasses and permissions are fine
and isolates the fault to the YUV path.

The SDK hands over a full `Y` plane, then `U`, then `V` (`w*h` followed by two `w*h/4` planes).
`YuvImage` only accepts packed NV21 (`VU` interleaved), so convert once:

```kotlin
val ySize = width * height
val chroma = ySize / 4
val out = ByteArray(ySize + 2 * chroma)
System.arraycopy(src, 0, out, 0, ySize)
for (i in 0 until chroma) {
    out[ySize + 2 * i] = src[ySize + chroma + i]      // V
    out[ySize + 2 * i + 1] = src[ySize + i]           // U
}
```

Two wrong hypotheses wasted cycles first: treating the buffer as NV21, then swapping chroma pairs
for NV12. Both produce a cast, not garbage, so the preview still looks "almost right".

## Verifying the pipeline

Keep `capturePhoto()` wired while debugging the stream. Its `PhotoData.Bitmap` / `PhotoData.HEIC`
comes from the glasses' own encoder, so a good photo plus a bad preview localises the fault to
YUV handling rather than hardware, registration, or permissions.

## Related

- [LocalGlasses app](/entities/localglasses-app.md)
- [DAT session lifecycle](/concepts/dat-session-lifecycle.md)
- [YUV to H.264 recording on MediaTek](/concepts/yuv-to-h264-recording-on-mediatek.md)
