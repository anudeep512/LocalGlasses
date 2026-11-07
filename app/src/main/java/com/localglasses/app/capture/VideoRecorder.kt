package com.localglasses.app.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

/**
 * Encodes the SDK's decoded YUV 4:2:0 frames to H.264 and muxes them into an MP4.
 *
 * Two hardware-encoder traps on MediaTek devices:
 *  - `COLOR_FormatYUV420SemiPlanar` is accepted by `configure` but then yields black frames,
 *    because the vendor codec misreads the raw buffer layout.
 *  - `getInputImage()` segfaults inside its JNI copy.
 *
 * So this picks a codec that supports `COLOR_FormatYUV420Planar`, preferring the software
 * encoder, and feeds tightly packed I420 through plain input buffers.
 *
 * The SDK delivers NV21 (`Y` plane, then interleaved `VU`). Not thread safe: call [encode] from
 * one thread, as the frame collector does.
 */
class VideoRecorder(
    private val width: Int,
    private val height: Int,
    private val outputFile: File,
    private val frameRate: Int = 24,
    private val bitRate: Int = 4_000_000,
) {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameCount = 0L
    private var loggedCodec = false

    private val frameSize = width * height * 3 / 2
    private val ySize = width * height

    val frames: Long get() = frameCount

    fun start() {
        require(width > 0 && height > 0) { "Invalid frame size ${width}x$height" }

        val codecName = selectPlanarCodec()
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        codec = MediaCodec.createByCodecName(codecName).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /** Feeds one decoded frame. [nv21] must be width*height*3/2 bytes. */
    fun encode(nv21: ByteArray, presentationTimeUs: Long) {
        val encoder = codec ?: return
        if (nv21.size < frameSize) {
            Log.w(TAG, "Short frame ${nv21.size}B < $frameSize; dropping")
            return
        }

        try {
            val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex < 0) return

            val input = encoder.getInputBuffer(inIndex)
            if (input == null || input.capacity() < frameSize) {
                Log.w(TAG, "Input buffer too small: ${input?.capacity()} < $frameSize")
                encoder.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, 0)
                drain()
                return
            }

            input.clear()
            input.put(nv21, 0, ySize)
            // Deinterleave NV21's VU pairs into the planar U then V planes of I420.
            for (i in ySize until frameSize step 2) {
                input.put(nv21[i + 1]) // U
            }
            for (i in ySize until frameSize step 2) {
                input.put(nv21[i]) // V
            }

            if (!loggedCodec) {
                loggedCodec = true
                Log.i(TAG, "Encoding $width x $height via ${encoder.name}")
            }

            encoder.queueInputBuffer(inIndex, 0, frameSize, presentationTimeUs, 0)
            frameCount++
            drain()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Encode failed", e)
        }
    }

    /** Finishes the stream and releases the encoder and muxer. Safe to call twice. */
    fun stop() {
        val encoder = codec ?: return
        try {
            val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                encoder.queueInputBuffer(
                    inIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
            }
            drain()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Encoder stop failed", e)
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            codec = null

            muxer?.let { mux ->
                if (muxerStarted) runCatching { mux.stop() }
                runCatching { mux.release() }
            }
            muxer = null
            muxerStarted = false
        }
    }

    private fun drain() {
        val encoder = codec ?: return
        val info = MediaCodec.BufferInfo()

        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val mux = muxer ?: return
                    if (!muxerStarted) {
                        trackIndex = mux.addTrack(encoder.outputFormat)
                        mux.start()
                        muxerStarted = true
                    }
                }

                outIndex >= 0 -> {
                    val buffer = encoder.getOutputBuffer(outIndex)
                    if (buffer != null &&
                        info.size > 0 &&
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                        muxerStarted
                    ) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer?.writeSampleData(trackIndex, buffer, info)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)

                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** Prefers a software encoder that can take planar YUV; falls back to any planar codec. */
    private fun selectPlanarCodec(): String {
        val planar = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        val candidates = mutableListOf<Pair<MediaCodecInfo, Boolean>>()

        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            if (info.supportedTypes.none { it.equals(MIME, ignoreCase = true) }) continue
            val supportsPlanar = runCatching {
                info.getCapabilitiesForType(MIME).colorFormats.contains(planar)
            }.getOrDefault(false)
            if (supportsPlanar) candidates += info to info.isHardwareAccelerated
        }

        val chosen = candidates.firstOrNull { !it.second }?.first
            ?: candidates.firstOrNull()?.first
            ?: error("No H.264 encoder supports COLOR_FormatYUV420Planar on this device")

        return chosen.name
    }

    private companion object {
        const val TAG = "VideoRecorder"
        const val MIME = "video/avc"
        const val TIMEOUT_US = 10_000L
    }
}
