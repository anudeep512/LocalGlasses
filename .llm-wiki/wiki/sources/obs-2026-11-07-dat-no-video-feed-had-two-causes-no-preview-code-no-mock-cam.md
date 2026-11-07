---
type: source
title: 'Observation: DAT "no video feed" had two causes: no preview code + no mock camera source'
tags:
  - dat
  - android
  - preview
  - yuv
  - mockdevice
  - oneplox
  - coloros
status: observation
created: 2026-11-07
updated: 2026-11-07
slug: obs-2026-11-07-dat-no-video-feed-had-two-causes-no-preview-code-no-mock-cam
relevance: high
observed_at: 2026-11-07T12:58:27.430Z
source_context: Debugging missing video preview and mock camera source
---

# ⭐ Observation: DAT "no video feed" had two causes: no preview code + no mock camera source

Why "Start session does nothing / no video feed" in LocalGlasses: two independent causes, both now fixed. (1) The app had NO preview rendering code at all — MainActivity only printed state text, so even a healthy stream showed nothing. Added SessionViewModel.videoStream collection + NV21 YuvImage->JPEG->Bitmap conversion on Dispatchers.Default with frame dropping, rendered in a 9:16 Compose Box. (2) Mock mode had no camera source: log said "Streaming error occurred: code=2, msg:No camera source configured. Call MockCameraKit.setCameraFeed() with a video URI or CameraFacing before starting a stream." Fixed by adding MockDevice.setFeedFromPhoneCamera (CameraFacing.BACK) and setFeedFromUri. Verified on OnePlus CPH2717 (V4IBZTLVLNQSOFVO): Registration REGISTERED, Session STARTED, Stream STREAMING, 641 frames @ 480x640 rendering live. Mock API paths: com.meta.wearable.dat.mockdevice.api.GlassesModel, ...api.camera.CameraFacing, ...api.MockDeviceKitConfig, device.services.camera.setCameraFeed(uri|CameraFacing). Two ColorOS/OnePlus quirks: adb install returns INSTALL_FAILED_VERIFICATION_FAILURE (fix: install from Android Studio or disable Developer options > Verify apps over USB) and adb shell pm grant CAMERA throws SecurityException GRANT_RUNTIME_PERMISSIONS (must grant via in-app dialog).

*Relevance: high*
*Context: Debugging missing video preview and mock camera source*
*Tags: dat android preview yuv mockdevice oneplox coloros*

---
*Observed: 2026-11-07T12:58:27.430Z*
