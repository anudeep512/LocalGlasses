---
type: concept
title: YUV to H.264 recording on MediaTek
category: android-media
created: 2026-11-07
updated: 2026-11-07
---

# YUV to H.264 recording on MediaTek

Recording DAT camera frames to MP4 means feeding decoded YUV into `MediaCodec` + `MediaMuxer`.
On a MediaTek device (OnePlus CPH2717, Android 16) the obvious approaches fail in three distinct
ways.

## `COLOR_FormatYUV420SemiPlanar` encodes black frames

`configure()` accepts it, `start()` succeeds, buffers are consumed, and the muxed file plays — but
every frame is black. Measured on the output: mean luma ≈ 4.6 across all 665 frames while the
source preview was correctly exposed.

Vendor encoders misread the raw semi-planar buffer layout. `configure()` does not validate it.

## `MediaCodec.getInputImage()` segfaults

The stride-aware path is the documented portable answer, but here it dies natively:

```
F DEBUG: #06 pc … (com.localglasses.app.capture.VideoRecorder.copyPlane+352)
F DEBUG: #12 pc … VideoRecorder.copyNv21IntoImage+0
F DEBUG: #16 pc … VideoRecorder.encode+0
```

A hard SIGSEGV inside the JNI copy, not a Java exception. Not recoverable with `runCatching`.

## `MediaCodec` is not thread-safe

Draining and stopping from different dispatchers throws:

```
W VideoRecorder: Encoder stop failed
java.lang.IllegalStateException: Invalid to call while another dequeue output request is pending
```

`encode()` ran on multi-threaded `Dispatchers.Default` while `stop()` ran on another coroutine.

## What works

1. **Select a codec that supports `COLOR_FormatYUV420Planar`, preferring the software encoder.**
   Tightly packed I420 through plain input buffers is the one path vendor encoders handle
   predictably.

```kotlin
val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
val chosen = list.codecInfos
    .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME, true) } }
    .filter { info ->
        runCatching {
            info.getCapabilitiesForType(MIME).colorFormats
                .contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)
        }.getOrDefault(false)
    }
    .minByOrNull { if (it.isHardwareAccelerated) 1 else 0 }   // software first
    ?: error("No H.264 encoder supports planar YUV")

MediaCodec.createByCodecName(chosen.name)
```

2. **Serialise every `MediaCodec` call onto one thread.**

```kotlin
private val mediaDispatcher = Dispatchers.Default.limitedParallelism(1)
```

Both the per-frame `encode()` and the terminal `stop()` must run on it.

3. **Deinterleave planar source into I420 planes** in the same pass that fills the input buffer,
   and queue exactly `w*h*3/2` bytes.

4. **Prefer `MediaMuxer` start on `INFO_OUTPUT_FORMAT_CHANGED`**, and drop samples flagged
   `BUFFER_FLAG_CODEC_CONFIG`.

## Debugging technique that paid off

`ffprobe`/`ffmpeg` on the pulled MP4 beats guessing: `nb_frames` plus per-timestamp mean luma
proved the file was structurally valid but visually black, which ruled out muxing and pointed at
the colour format.

## Related

- [DAT real-hardware gotchas](/concepts/dat-real-hardware-gotchas.md)
- [LocalGlasses app](/entities/localglasses-app.md)
