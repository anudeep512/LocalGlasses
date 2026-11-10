package com.localglasses.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localglasses.app.agent.PermissionsCoordinator
import com.localglasses.app.agent.approval.PlanApprovalSheet
import com.localglasses.app.agent.approval.PlanState
import com.localglasses.app.agent.commands.AgentViewModel
import com.localglasses.app.agent.model.ModelState
import com.localglasses.app.agent.trace.ContactAlias
import com.localglasses.app.ui.GlassesScreen
import com.meta.wearable.dat.core.Wearables
import kotlinx.coroutines.launch

/** Runtime Bluetooth permissions DAT needs before it can see bonded glasses. */
private val REQUIRED_BLUETOOTH_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
    }

private const val TAB_AGENT = 0
private const val TAB_CAMERA = 1
private const val TAB_SETTINGS = 2

class MainActivity : ComponentActivity() {

    private val selectedTab = mutableIntStateOf(TAB_AGENT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(
                        selectedTab = selectedTab.intValue,
                        onSelectTab = { selectedTab.intValue = it },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_ASSIST) {
            selectedTab.intValue = TAB_AGENT
        }
    }
}

@Composable
private fun AppRoot(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                TAB_AGENT -> AgentPlaceholder()
                TAB_CAMERA -> CameraTab()
                else -> SettingsPlaceholder()
            }
        }
        NavigationBar {
            NavigationBarItem(
                selected = selectedTab == TAB_AGENT,
                onClick = { onSelectTab(TAB_AGENT) },
                icon = { Text("A") },
                label = { Text("Agent") },
            )
            NavigationBarItem(
                selected = selectedTab == TAB_CAMERA,
                onClick = { onSelectTab(TAB_CAMERA) },
                icon = { Text("C") },
                label = { Text("Camera") },
            )
            NavigationBarItem(
                selected = selectedTab == TAB_SETTINGS,
                onClick = { onSelectTab(TAB_SETTINGS) },
                icon = { Text("S") },
                label = { Text("Settings") },
            )
        }
    }
}

@Composable
private fun AgentPlaceholder(viewModel: AgentViewModel = viewModel()) {
    val state by viewModel.modelState.collectAsState()
    val ui by viewModel.uiState.collectAsState()
    val planState by viewModel.planState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Agent", style = MaterialTheme.typography.headlineSmall)
        Text(
            AgentViewModel.statusText(state),
            style = MaterialTheme.typography.bodyMedium,
        )

        when (val current = state) {
            ModelState.Absent -> {
                Text(
                    "Download the local model once. After that, commands run offline.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = viewModel::downloadModel) {
                    Text("Download model (2.59 GB)")
                }
            }
            is ModelState.Downloading -> {
                val progress = if (current.total > 0) {
                    current.bytes.toFloat() / current.total
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ModelState.Verifying -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            ModelState.Ready -> Unit
            is ModelState.Failed -> {
                Text(current.message, style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::retryModel) { Text("Retry download") }
            }
        }

        OutlinedTextField(
            value = ui.command,
            onValueChange = viewModel::onCommandChange,
            enabled = !ui.isSubmitting,
            label = { Text("Command") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::submit,
                enabled = ui.command.isNotBlank() && !ui.isSubmitting,
            ) { Text("Run") }
        }

        if (ui.status.isNotBlank()) {
            Text(ui.status, style = MaterialTheme.typography.bodyMedium)
        }
        if (ui.suggestions.isNotEmpty()) {
            Text(
                ui.suggestions.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val pending = (planState as? PlanState.Pending)?.plan
        if (pending != null) {
            PlanApprovalSheet(
                plan = pending,
                onApprove = viewModel::approvePlan,
                onCancel = viewModel::cancelPlan,
                onUpdate = viewModel::updatePlan,
            )
        }

        Text(
            "Camera stays available on its own tab. Settings holds the model, aliases, " +
                "traces, and special access.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SettingsPlaceholder() {
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(PermissionsCoordinator.notificationsGranted(context))
    }
    var batteryExempt by remember {
        mutableStateOf(PermissionsCoordinator.batteryExempt(context))
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Model state, contact aliases, trace export/purge, and special access will appear here.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("Access", style = MaterialTheme.typography.titleSmall)
        Text(
            "Notifications: ${if (notificationsGranted) "granted" else "not granted"}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!notificationsGranted) {
            Button(
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ) { Text("Allow notifications") }
        }

        Text(
            "Battery optimisation: ${if (batteryExempt) "exempt" else "not exempt"}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!batteryExempt) {
            OutlinedButton(
                onClick = {
                    PermissionsCoordinator.requestBatteryExemption(context)
                    batteryExempt = PermissionsCoordinator.batteryExempt(context)
                },
            ) { Text("Request exemption") }
        }

        var writeSettings by remember {
            mutableStateOf(PermissionsCoordinator.writeSettingsGranted(context))
        }
        Text(
            "Modify system settings: ${if (writeSettings) "granted" else "not granted"}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!writeSettings) {
            OutlinedButton(
                onClick = {
                    PermissionsCoordinator.requestWriteSettings(context)
                    writeSettings = PermissionsCoordinator.writeSettingsGranted(context)
                },
            ) { Text("Grant modify system settings") }
        }

        val devicePermissions = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
        )
        var deviceGranted by remember {
            mutableStateOf(
                devicePermissions.all {
                    ContextCompat.checkSelfPermission(context, it) ==
                        PackageManager.PERMISSION_GRANTED
                },
            )
        }
        val deviceLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            deviceGranted = results.values.all { it }
        }
        if (!deviceGranted) {
            OutlinedButton(
                onClick = { deviceLauncher.launch(devicePermissions.toTypedArray()) },
            ) { Text("Grant contacts / call / SMS") }
        }

        val app = context.applicationContext as LocalGlassesApp
        val aliasStore = app.agentContainer.aliasStore
        var aliases by remember { mutableStateOf(aliasStore.list()) }
        var aliasText by remember { mutableStateOf("") }
        var nameText by remember { mutableStateOf("") }
        var phoneText by remember { mutableStateOf("") }

        Text("Contact aliases", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = aliasText,
            onValueChange = { aliasText = it },
            label = { Text("Alias (for example Amma)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            label = { Text("Contact name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = phoneText,
            onValueChange = { phoneText = it },
            label = { Text("Phone number") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = aliasText.isNotBlank() && nameText.isNotBlank() && phoneText.isNotBlank(),
            onClick = {
                aliasStore.put(
                    ContactAlias(
                        alias = aliasText.trim(),
                        contactId = aliasText.trim(),
                        displayName = nameText.trim(),
                        phone = phoneText.trim(),
                    ),
                )
                aliases = aliasStore.list()
                aliasText = ""
                nameText = ""
                phoneText = ""
            },
        ) { Text("Save alias") }

        aliases.forEach { alias ->
            Row {
                Text(
                    "${alias.alias} → ${alias.displayName} ${alias.phone}",
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        aliasStore.delete(alias.alias)
                        aliases = aliasStore.list()
                    },
                ) { Text("Delete") }
            }
        }

        val traceDatabase = app.agentContainer.traceDatabase
        val traceExporter = app.agentContainer.traceExporter
        var traceCount by remember { mutableStateOf(traceDatabase.countTraces()) }
        val scope = rememberCoroutineScope()
        Text("Traces: $traceCount", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        traceExporter.export()
                        traceCount = traceDatabase.countTraces()
                    }
                },
            ) { Text("Export traces") }
            OutlinedButton(
                onClick = {
                    traceDatabase.purge()
                    traceCount = traceDatabase.countTraces()
                },
            ) { Text("Purge traces") }
        }
    }
}

@Composable
private fun CameraTab(modifier: Modifier = Modifier) {
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
            Text("Glasses", style = MaterialTheme.typography.headlineSmall)

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
