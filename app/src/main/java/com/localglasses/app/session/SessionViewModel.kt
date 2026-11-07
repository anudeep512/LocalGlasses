package com.localglasses.app.session

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.exifinterface.media.ExifInterface as AndroidXExif
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localglasses.app.capture.MediaStoreSaver
import com.localglasses.app.capture.VideoRecorder
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.*
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the DAT [DeviceSession], its attached [Camera], photo capture, and video recording.
 *
 * Rules from the DAT guidance:
 *  - `session.start()` is fire-and-forget; subscribe to `state` and `errors` first.
 *  - Capabilities may only be attached once the session reports [DeviceSessionState.STARTED].
 *  - `capturePhoto()` only succeeds while the stream is actively streaming.
 *  - Only one capture may be in flight at a time.
 *  - A stopped session is terminal; create a new one instead of reusing it.
 */
class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val _sessionState = MutableStateFlow<DeviceSessionState?>(null)
    val sessionState: StateFlow<DeviceSessionState?> = _sessionState.asStateFlow()

    private val _streamState = MutableStateFlow<StreamState?>(null)
    val streamState: StateFlow<StreamState?> = _streamState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val _frameStats = MutableStateFlow(FrameStats())
    val frameStats: StateFlow<FrameStats> = _frameStats.asStateFlow()

    private val _lastSaved = MutableStateFlow<String?>(null)
    val lastSaved: StateFlow<String?> = _lastSaved.asStateFlow()

    private val _capturing = MutableStateFlow(false)
    val capturing: StateFlow<Boolean> = _capturing.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    /** Glasses linked to Meta AI. Mock devices never appear here. */
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    /** DAT camera permission state, granted through the Meta AI app. */
    private val _cameraPermission = MutableStateFlow<PermissionStatus?>(null)
    val cameraPermission: StateFlow<PermissionStatus?> = _cameraPermission.asStateFlow()

    /** Where the current stream comes from. */
    private val _source = MutableStateFlow("No source")
    val source: StateFlow<String> = _source.asStateFlow()

    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private var collectors: Job? = null

    private var recorder: VideoRecorder? = null
    private var recordingFile: java.io.File? = null

    /**
     * MediaCodec is not thread safe: encode and stop must run on the same thread.
     * A single-permit dispatcher serialises every recorder and preview operation.
     */
    private val mediaDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** Guards the preview converter so slow frames are dropped instead of queued. */
    private var converting = false

    private var devicesJob: Job? = null

    /** Start after Wearables.initialize() succeeded; reading flows earlier throws. */
    fun startObservingDevices() {
        if (devicesJob != null) return
        devicesJob = viewModelScope.launch {
            Wearables.devices
                .flatMapLatest { ids: Set<DeviceIdentifier> ->
                    val flows = ids.map { id -> Wearables.devicesMetadata[id] ?: flowOf(null) }
                    if (flows.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(flows) { snapshots -> snapshots.filterNotNull() }
                    }
                }
                .collect { list -> _devices.value = list }
        }
    }

    private fun markSourceStarted() {
        val connected = _devices.value.firstOrNull { it.linkState == LinkState.CONNECTED }
        _source.value = connected?.let { "Meta glasses: " + it.name } ?: "Meta glasses"
    }

    fun startSession() {
        if (session != null) return

        Wearables.createSession(AutoDeviceSelector()).fold(
            onSuccess = { created ->
                session = created
                _lastError.value = null

                collectors = viewModelScope.launch {
                    launch {
                        created.errors.collect { error ->
                            _lastError.value = error.description
                        }
                    }
                    launch {
                        created.state.collect { state ->
                            _sessionState.value = state
                            when (state) {
                                DeviceSessionState.STARTED -> {
                                    markSourceStarted()
                                    attachCamera(created)
                                }
                                DeviceSessionState.STOPPED -> clearSession(created)
                                else -> Unit
                            }
                        }
                    }
                }

                // Fire-and-forget: failures surface through session.errors.
                created.start()
            },
            onFailure = { error, _ ->
                _lastError.value = error.description
            },
        )
    }

    private fun attachCamera(activeSession: DeviceSession) {
        if (camera != null) return

        activeSession.addCamera(
            StreamConfiguration(
                videoQuality = VideoQuality.MEDIUM,
                frameRate = 24,
                compressVideo = false,
            ),
        ).fold(
            onSuccess = { added ->
                camera = added

                viewModelScope.launch {
                    added.stream.state.collect { _streamState.value = it }
                }
                viewModelScope.launch {
                    added.stream.errorStream.collect { error ->
                        _lastError.value = error.description
                    }
                }
                viewModelScope.launch {
                    added.stream.videoStream.collect { frame ->
                        withContext(mediaDispatcher) { handleFrame(frame) }
                    }
                }

                startStreamWhenPermitted(added)
            },
            onFailure = { error, _ ->
                _lastError.value = error.description
            },
        )
    }

    private fun handleFrame(frame: VideoFrame) {
        if (frame.isCompressed || frame.isCodecConfig) return

        val nv21 = frame.toNv21Bytes()

        // Recording gets every frame; preview may skip when a conversion is still running.
        recorder?.let { rec ->
            runCatching { rec.encode(nv21, frame.presentationTimeUs) }
                .onFailure { _lastError.value = "Encode failed: ${it.message}" }
        }

        if (converting) return
        converting = true
        try {
            val bitmap = nv21ToBitmap(nv21, frame.width, frame.height) ?: return
            _preview.value = bitmap
            _frameStats.update {
                it.copy(frames = it.frames + 1, width = frame.width, height = frame.height)
            }
        } finally {
            converting = false
        }
    }

    // ---------------------------------------------------------------- photo capture

    fun capturePhoto() {
        val stream = camera?.stream
        if (stream == null) {
            _lastError.value = "No camera. Start a session first."
            return
        }
        if (_capturing.value) return

        _capturing.value = true
        viewModelScope.launch {
            try {
                stream.capturePhoto()
                    .onSuccess { photoData ->
                        val bitmap = withContext(Dispatchers.Default) { decodePhoto(photoData) }
                        if (bitmap == null) {
                            _lastError.value = "Could not decode the captured photo"
                        } else {
                            val name = timestamp("IMG")
                            MediaStoreSaver.savePhoto(getApplication(), bitmap, name).fold(
                                onSuccess = { uri -> _lastSaved.value = "Photo saved: $uri" },
                                onFailure = { e ->
                                    _lastError.value = "Save failed: ${e.message}"
                                },
                            )
                        }
                    }
                    .onFailure { error, _ ->
                        _lastError.value = error.description
                    }
            } finally {
                _capturing.value = false
            }
        }
    }

    private fun decodePhoto(photo: PhotoData): Bitmap? = when (photo) {
        is PhotoData.Bitmap -> photo.bitmap
        is PhotoData.HEIC -> decodeHeic(photo.data)
    }

    /**
     * The glasses record orientation in an EXIF tag that BitmapFactory does not apply to HEIC,
     * so rotate manually or the saved image is sideways.
     */
    private fun decodeHeic(data: ByteBuffer): Bitmap? = runCatching {
        val buffer = data.duplicate().apply { rewind() }
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val orientation = runCatching {
            ByteArrayInputStream(bytes).use { input ->
                AndroidXExif(input).getAttributeInt(
                    AndroidXExif.TAG_ORIENTATION,
                    AndroidXExif.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(AndroidXExif.ORIENTATION_NORMAL)

        val matrix = Matrix().apply {
            when (orientation) {
                AndroidXExif.ORIENTATION_ROTATE_90 -> postRotate(90f)
                AndroidXExif.ORIENTATION_ROTATE_180 -> postRotate(180f)
                AndroidXExif.ORIENTATION_ROTATE_270 -> postRotate(270f)
                AndroidXExif.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                AndroidXExif.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
        if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { bitmap.recycle() }
        }
    }.getOrNull()

    // ---------------------------------------------------------------- video recording

    fun startRecording() {
        if (_recording.value) return
        val stats = _frameStats.value
        if (stats.width <= 0 || stats.height <= 0) {
            _lastError.value = "No frames yet — wait for the stream to start"
            return
        }

        val file = MediaStoreSaver.scratchFile(getApplication(), timestamp("VID"))
        val rec = VideoRecorder(
            width = stats.width,
            height = stats.height,
            outputFile = file,
        )

        runCatching {
            kotlinx.coroutines.runBlocking(mediaDispatcher) { rec.start() }
        }.onFailure { e ->
            _lastError.value = "Recorder failed to start: ${e.message}"
            return
        }

        recorder = rec
        recordingFile = file
        _recording.value = true
        _lastSaved.value = null
    }

    fun stopRecording() {
        val rec = recorder ?: return
        val file = recordingFile
        recorder = null
        recordingFile = null
        _recording.value = false

        viewModelScope.launch {
            val frames = withContext(mediaDispatcher) {
                rec.stop()
                rec.frames
            }

            if (file == null || !file.exists() || file.length() == 0L) {
                _lastError.value = "Recording produced no data ($frames frames)"
                file?.delete()
                return@launch
            }

            MediaStoreSaver.saveVideo(getApplication(), file, timestamp("VID")).fold(
                onSuccess = { uri ->
                    _lastSaved.value = "Video saved ($frames frames): $uri"
                },
                onFailure = { e ->
                    _lastError.value = "Save failed: ${e.message}"
                },
            )
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * The SDK delivers **planar** YUV 4:2:0 (I420): a full `Y` plane, then a `U` plane, then a
     * `V` plane. `YuvImage` only accepts packed NV21 (`VU` interleaved), and the I420 encoder
     * path expects planar, so convert once here into NV21 and keep consumers on NV21.
     *
     * Assuming a semi-planar layout instead produces a green/magenta cast while luma stays
     * correct, which is exactly what the wrong versions of this function looked like.
     */
    private fun VideoFrame.toNv21Bytes(): ByteArray {
        val dup = buffer.duplicate()
        val src = ByteArray(dup.remaining())
        dup.get(src)

        val ySize = width * height
        val chromaSize = ySize / 4
        if (src.size < ySize + 2 * chromaSize) return src

        val out = ByteArray(ySize + 2 * chromaSize)
        System.arraycopy(src, 0, out, 0, ySize)

        for (i in 0 until chromaSize) {
            val u = src[ySize + i]
            val v = src[ySize + chromaSize + i]
            out[ySize + 2 * i] = v
            out[ySize + 2 * i + 1] = u
        }
        return out
    }

    private fun nv21ToBitmap(bytes: ByteArray, width: Int, height: Int): Bitmap? = runCatching {
        val yuv = YuvImage(bytes, ImageFormat.NV21, width, height, null)
        val jpeg = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, width, height), 70, jpeg)
        val data = jpeg.toByteArray()
        BitmapFactory.decodeByteArray(data, 0, data.size)
    }.getOrNull()

    private fun timestamp(prefix: String): String =
        "$prefix-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    /**
     * DAT requires its own camera permission, granted through the Meta AI app. Starting the
     * stream without it fails with ERROR_CODE_PERMISSION_DENIED.
     */
    fun startStreamWhenPermitted(target: Camera? = null) {
        val cam = target ?: camera ?: return
        viewModelScope.launch {
            Wearables.checkPermissionStatus(Permission.CAMERA).fold(
                onSuccess = { status ->
                    _cameraPermission.value = status
                    if (status == PermissionStatus.Granted) {
                        cam.stream.start().onFailure { error, _ ->
                            _lastError.value = error.description
                        }
                    } else {
                        _lastError.value =
                            "Camera permission needed — grant it in the Meta AI app"
                    }
                },
                onFailure = { error, _ -> _lastError.value = error.description },
            )
        }
    }

    /** Called after the RequestPermissionContract result comes back. */
    fun refreshAfterPermissionRequest() {
        startStreamWhenPermitted()
    }

    fun clearError() {
        _lastError.value = null
    }

    fun stopSession() {
        if (_recording.value) stopRecording()

        camera?.stop()
        session?.stop()
        camera = null
        session = null
        collectors?.cancel()
        collectors = null
        _sessionState.value = null
        _streamState.value = null
        _preview.value = null
        _frameStats.value = FrameStats()
        _source.value = "No source"
    }

    private fun clearSession(stopped: DeviceSession) {
        if (session !== stopped) return
        camera = null
        session = null
        _streamState.value = null
    }

    override fun onCleared() {
        recorder?.stop()
        recorder = null
        stopSession()
    }

    data class FrameStats(
        val frames: Int = 0,
        val width: Int = 0,
        val height: Int = 0,
    )
}
