package com.localglasses.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localglasses.app.session.SessionViewModel
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

/** Runtime Bluetooth permissions DAT needs before it can see bonded glasses. */
private val REQUIRED_BLUETOOTH_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RootScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun RootScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as LocalGlassesApp
    val datReady by app.datReady.collectAsState()
    val datError by app.datError.collectAsState()

    var permissionDenied by remember { mutableStateOf(false) }

    fun hasBluetoothPermissions(): Boolean = REQUIRED_BLUETOOTH_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results.values.all { it } && hasBluetoothPermissions()
        permissionDenied = !granted
        if (granted) app.initializeDat()
    }

    // Permission must be granted BEFORE Wearables.initialize(), otherwise the SDK's
    // bonded-device scan throws and no glasses ever become eligible.
    LaunchedEffect(Unit) {
        if (hasBluetoothPermissions()) {
            app.initializeDat()
        } else {
            permissionLauncher.launch(REQUIRED_BLUETOOTH_PERMISSIONS)
        }
    }

    if (!datReady) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("LocalGlasses", style = MaterialTheme.typography.headlineSmall)

            if (permissionDenied) {
                Text(
                    "Bluetooth permission is required. The Device Access Toolkit cannot list " +
                        "your glasses without it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Enable Nearby devices / Bluetooth in Settings → Apps → LocalGlasses → " +
                        "Permissions, then reopen the app.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { permissionLauncher.launch(REQUIRED_BLUETOOTH_PERMISSIONS) },
                ) { Text("Request again") }
            } else {
                Text("Waiting for Bluetooth permission…")
            }

            datError?.let {
                Text("DAT init failed: $it", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { app.initializeDat() }) { Text("Retry init") }
            }
        }
        return
    }

    GlassesScreen(
        modifier = modifier,
        onRegister = {
            (context as? ComponentActivity)?.let { Wearables.startRegistration(it) }
        },
        onUnregister = {
            (context as? ComponentActivity)?.let { Wearables.startUnregistration(it) }
        },
        onFirmwareUpdate = {
            (context as? ComponentActivity)?.let { Wearables.openFirmwareUpdate(it) }
        },
    )
}

/**
 * Single source of truth: the camera feed comes from Meta glasses linked to Meta AI on this
 * phone. There is no mock or phone-camera fallback.
 */
@Composable
private fun GlassesScreen(
    modifier: Modifier = Modifier,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onFirmwareUpdate: () -> Unit,
    viewModel: SessionViewModel = viewModel(),
) {
    val registrationState by Wearables.registrationState.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val streamState by viewModel.streamState.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val frameStats by viewModel.frameStats.collectAsState()
    val capturing by viewModel.capturing.collectAsState()
    val recording by viewModel.recording.collectAsState()
    val lastSaved by viewModel.lastSaved.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val source by viewModel.source.collectAsState()
    val cameraPermission by viewModel.cameraPermission.collectAsState()

    // DAT camera permission is granted through the Meta AI app, not the Android dialog.
    val datPermissionLauncher = rememberLauncherForActivityResult(
        Wearables.RequestPermissionContract(),
    ) { result ->
        result.onSuccess { viewModel.refreshAfterPermissionRequest() }
    }

    LaunchedEffect(Unit) { viewModel.startObservingDevices() }

    val streaming = streamState == StreamState.STREAMING

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("LocalGlasses", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Meta Wearables Device Access Toolkit",
            style = MaterialTheme.typography.bodySmall,
        )

        StatusCard(
            rows = listOf(
                "Source" to source,
                "Registration" to registrationState.toString(),
                "Session" to (sessionState?.toString() ?: "—"),
                "Stream" to (streamState?.toString() ?: "—"),
                "Frames" to if (frameStats.frames == 0) {
                    "0"
                } else {
                    "${frameStats.frames} @ ${frameStats.width}x${frameStats.height}"
                },
                "Last error" to (lastError ?: "none"),
            ),
        )

        // ------------------------------------------------ linked glasses
        Text("Glasses", style = MaterialTheme.typography.titleSmall)

        if (devices.isEmpty()) {
            Text(
                "No glasses linked. Pair them in the Meta AI app, enable Developer Mode, " +
                    "then tap Register.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            devices.forEach { device ->
                val link = when (device.linkState) {
                    LinkState.CONNECTED -> "connected"
                    LinkState.CONNECTING -> "connecting"
                    LinkState.DISCONNECTED -> "disconnected"
                }
                val extras = buildList {
                    if (device.batteryLevel != 0) add("${device.batteryLevel}%")
                    if (device.compatibility != DeviceCompatibility.COMPATIBLE) {
                        add(device.compatibility.toString())
                    }
                }
                Text(
                    "• ${device.name} — $link" +
                        if (extras.isEmpty()) "" else " (${extras.joinToString()})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (devices.any { it.compatibility != DeviceCompatibility.COMPATIBLE }) {
            OutlinedButton(onClick = onFirmwareUpdate) { Text("Update glasses firmware") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRegister) { Text("Register") }
            OutlinedButton(onClick = onUnregister) { Text("Unregister") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.startSession() }) { Text("Start session") }
            OutlinedButton(onClick = { viewModel.stopSession() }) { Text("Stop") }
        }

        if (cameraPermission != PermissionStatus.Granted) {
            Button(onClick = { datPermissionLauncher.launch(Permission.CAMERA) }) {
                Text("Grant camera permission") }
            Text(
                "Camera access is approved inside the Meta AI app.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ------------------------------------------------ live feed from glasses
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Glasses camera feed",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: Text(
                if (streaming) "Waiting for frames" else "No frames",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ------------------------------------------------ capture
        Text("Capture", style = MaterialTheme.typography.titleSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { viewModel.capturePhoto() },
                enabled = !capturing && streaming,
            ) { Text(if (capturing) "Capturing…" else "Take photo") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recording) {
                Button(onClick = { viewModel.stopRecording() }) { Text("Stop recording") }
            } else {
                Button(
                    onClick = { viewModel.startRecording() },
                    enabled = frameStats.frames > 0,
                ) { Text("Record video") }
            }
        }

        lastSaved?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun StatusCard(rows: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.forEach { (label, value) ->
                Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
