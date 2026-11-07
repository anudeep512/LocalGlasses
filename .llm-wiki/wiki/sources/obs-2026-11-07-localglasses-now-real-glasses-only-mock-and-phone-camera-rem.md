---
type: source
title: "Observation: LocalGlasses now real-glasses-only; mock and phone camera removed"
tags:
  - dat
  - android
  - real-glasses
  - video-recorder
  - mediacodec
  - i420
status: observation
created: 2026-11-07
updated: 2026-11-07
slug: obs-2026-11-07-localglasses-now-real-glasses-only-mock-and-phone-camera-rem
relevance: high
observed_at: 2026-11-07T13:15:46.509Z
source_context: Removing mock source and wiring real Meta glasses feed with photo/video capture
---

# ⭐ Observation: LocalGlasses now real-glasses-only; mock and phone camera removed

LocalGlasses app switched to real-Meta-glasses-only feed. Removed: app/src/main/java/com/localglasses/app/mock/MockDevice.kt, the mwdat-mockdevice dependency from libs.versions.toml and app/build.gradle.kts, all mock UI in MainActivity, and the host CAMERA permission plus camera uses-feature from AndroidManifest.xml (only BLUETOOTH, BLUETOOTH_CONNECT, INTERNET remain). MainActivity is now GlassesScreen: status card (Source/Registration/Session/Stream/Frames/Last error), linked-glasses list from viewModel.devices showing name, LinkState, batteryLevel and DeviceCompatibility with a firmware-update button when incompatible, Register/Unregister, Start session/Stop, live 9:16 preview, Take photo and Record video. SessionViewModel exposes devices by flatMapLatest over Wearables.devices (StateFlow<Set<DeviceIdentifier>>) combining Wearables.devicesMetadata[id] (Map<DeviceIdentifier, StateFlow<Device>>). Device has name, linkState, deviceType, firmwareInfo, compatibility, thermalLevel, batteryLevel, chargingState, donState, hingeState. With no glasses connected, Start session correctly reports 'No eligible device available'. VideoRecorder rewrite uses COLOR_FormatYUV420Planar on a software encoder plus a single-permit dispatcher for encode/stop, because SemiPlanar produced black frames and getInputImage segfaulted; this fix is still unverified on real hardware since no glasses were available.

*Relevance: high*
*Context: Removing mock source and wiring real Meta glasses feed with photo/video capture*
*Tags: dat android real-glasses video-recorder mediacodec i420*

---
*Observed: 2026-11-07T13:15:46.509Z*
