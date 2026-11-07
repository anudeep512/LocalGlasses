---
type: source
title: "YUV to H.264 on MediaTek: black frames, JNI segfault, thread safety"
status: insight
category: android-media
created: 2026-11-07
updated: 2026-11-07
slug: yuv-h264-mediacodec-mediatek-traps
---

# YUV to H.264 on MediaTek: black frames, JNI segfault, thread safety

Three facts about encoding DAT camera frames to H.264 on a MediaTek device (OnePlus CPH2717, Android 16):

**1. `COLOR_FormatYUV420SemiPlanar` encodes black frames.** `configure()` accepts it, `start()` succeeds, buffers are consumed, the MP4 plays — but every frame is black. Measured mean luma ≈ 4.6 across 665 frames while the preview was correctly exposed. `configure()` does not validate the layout.

**2. `MediaCodec.getInputImage()` segfaults.** The stride-aware path is the documented portable answer but dies natively: `F DEBUG: VideoRecorder.copyPlane+352` → `copyNv21IntoImage` → `encode`. A hard SIGSEGV inside the JNI copy, so `runCatching` cannot save it.

**3. `MediaCodec` is not thread safe.** `IllegalStateException: Invalid to call while another dequeue output request is pending` when `encode()` runs on multi-threaded `Dispatchers.Default` and `stop()` runs elsewhere.

**What works:** select a codec supporting `COLOR_FormatYUV420Planar`, preferring the software encoder (`minByOrNull { if (it.isHardwareAccelerated) 1 else 0 }`), feed tightly packed I420 through plain input buffers, and serialise every `MediaCodec` call onto `Dispatchers.Default.limitedParallelism(1)`.

**Also:** the SDK's `VideoFrame.buffer` is planar I420, not NV12/NV21. Assuming semi-planar keeps luma correct and only shifts hue — a green/magenta cast that survives a chroma byte swap and makes the preview look "almost right".

**Debugging tip:** pull the MP4 and run `ffprobe`/`ffmpeg`. `nb_frames` plus per-timestamp mean luma proved the file was structurally valid but visually black, which ruled out muxing and pointed straight at the colour format.

See [[concepts/yuv-to-h264-recording-on-mediatek]] and [[concepts/dat-real-hardware-gotchas]].


*Category: android-media*

---
*Captured: 2026-11-07*

## Related

_Add links to related pages._
