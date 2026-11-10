# Design

## Context

See `proposal.md` for motivation. The current app is a single-module Compose application:

- `LocalGlassesApp` initialises DAT after the Bluetooth grant.
- `MainActivity` is a single screen: Bluetooth gate -> `GlassesScreen`.
- `SessionViewModel` owns the DAT session, camera, preview, photo capture, and recording.
- `capture/` contains the YUV -> H.264 recorder and MediaStore writer.
- `minSdk 29`, `targetSdk 37`, AGP 9.5.1, Kotlin 2.4.21, no DI, no database, no HTTP client, no tests.

Hardware measured on `CPH2717` (OnePlus Nord CE5 Lite): MediaTek MT6897, 7.65 GB RAM, 7.86 GB compressed swap with ~3 GB used at idle, 2.66 GB available before model load, Android 16, 42 GB free. LiteRT-LM has no MediaTek NPU backend; inference is CPU XNNPACK or Mali GPU.

## Goals / Non-Goals

**Goals:**

- Add the seven capabilities in the proposal without changing the existing DAT camera behaviour.
- Keep the agent fully offline after a one-time model download.
- Make every side effect pass through one visible, editable, approved plan.
- Enforce hard blocks in the runner, not in the prompt.
- Record enough on-device trace data to distil deterministic workflows in v2.
- Keep the app usable on 7.65 GB RAM while other apps run.

**Non-Goals:**

- Voice input, glasses microphone/vision/audio, VoiceInteractionService.
- Spotify, alarms/timers, Telegram, arbitrary app launching.
- Automatic workflow replay or promotion.
- Play Store distribution or policy compliance.
- Multi-module refactor or a DI framework.
- Cloud inference, telemetry, or trace sync.

## Decisions

### D1. Single module, package-by-capability, manual wiring

Keep one `:app` module. Add `com.localglasses.app.agent.*` packages:

```
agent/
├── AgentContainer.kt        manual service locator, owned by Application
├── commands/                AgentViewModel, CommandRouter, NoMatch
├── model/                   ModelRepository, ModelDownloadWorker, EngineHolder, AgentWarmService
├── actions/                 ActionRegistry, individual actions, ContactsResolver, SettingsController
├── automation/              AgentAccessibilityService, Observation, PlanExecutor, ProgressTracker
├── approval/                Plan models, PlanCoordinator, PlanApprovalSheet, ExecutionOverlay
├── safety/                  SafetyPolicy, Redactor, SecureScreen, denylist asset
└── trace/                   TraceDatabase, TraceRecorder, TraceExporter, AgentNotificationListener
```

`LocalGlassesApp` creates `AgentContainer`; `MainActivity` and services read it. No Hilt: one app, a handful of singletons, and Hilt/KSP adds build complexity for no runtime benefit. Alternative considered: multi-module split (deferred until the agent stabilises).

### D2. In-process LiteRT-LM tools, no MCP/A2A

Use `com.google.ai.edge.litertlm:litertlm-android:0.19.1`.

- Create one `Engine` with `EngineConfig(modelPath, backend = Backend.CPU(), cacheDir = context.cacheDir.path)`.
- Create a fresh `Conversation` per command. No conversation history.
- Register router tools with `ConversationConfig(tools = listOf(tool(RouterTools(...))))` and `automaticToolCalling = false`.
- Read `Message.toolCalls`, validate, and build the plan in app code. This is required because the runner must gate on approval; auto-execution inside the runtime cannot.
- Constrained decoding (Gemma 4 FST constraints) keeps tool-call JSON valid. Still validate every argument; a valid shape can carry an invalid value.

Alternative rejected: MCP over stdio does not exist on Android, and a localhost HTTP/SSE MCP server adds latency and service lifecycle for no isolation benefit. MediaPipe LLM Inference is maintenance-only. AICore/Gemini Nano is not available on this MediaTek device.

Router tools use only `@Tool`-supported parameter types (`String`, `Int`, `Boolean`, `Float`, `Double`, list of those):

```kotlin
class RouterTools(private val sink: ToolCallSink) : ToolSet {
    @Tool("Call a contact by name or saved alias") fun callContact(
        @ToolParam("Contact name exactly as the user said it") contact: String,
    ): String = sink.record("call_contact", mapOf("contact" to contact))

    @Tool("Send an SMS to a contact")
    fun sendSms(contact: String, message: String): String =
        sink.record("send_sms", mapOf("contact" to contact, "message" to message))

    @Tool("Send a WhatsApp message to a contact")
    fun sendWhatsapp(contact: String, message: String): String =
        sink.record("send_whatsapp", mapOf("contact" to contact, "message" to message))

    @Tool("Play a YouTube or YouTube Music search")
    fun playMedia(query: String, service: String): String =
        sink.record("play_media", mapOf("query" to query, "service" to service))

    @Tool("Change a curated device setting")
    fun setDeviceSetting(setting: String, value: Int): String =
        sink.record("set_setting", mapOf("setting" to setting, "value" to value))

    @Tool("Open a Settings page")
    fun openSettingsPage(page: String): String =
        sink.record("open_settings_page", mapOf("page" to page))

    @Tool("Plan UI steps when no registry action fits")
    fun planUiTask(goal: String, app: String?): String =
        sink.record("plan_ui_task", mapOf("goal" to goal, "app" to (app ?: "")))
}
```

The UI plan itself needs nested structure, which `@Tool` parameters cannot express. Define a separate `OpenApiTool` named `submit_plan` with a JSON schema for `steps: [{type, target, text, direction, millis}]`. With `automaticToolCalling = false`, its `execute` is not called; the app parses `toolCalls.arguments`. If constrained decoding cannot enforce the nested schema reliably, fall back to a `String` argument containing JSON, validate with kotlinx.serialization, and retry once on failure.

### D3. Model artefact and resumable download

Use the public, non-gated CPU artefact:

| Field | Value |
|---|---|
| URL | `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm` |
| Size | 2,588,147,712 bytes |
| SHA-256 | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` (from HF `x-linked-etag`) |

Keep the URL, size, and hash in `assets/model.json` so an artefact update does not require code changes. The app computes SHA-256 while downloading or after completion and refuses mismatches.

`ModelDownloadWorker : CoroutineWorker`:

- Checks free space (require at least 3 GB).
- Downloads to `filesDir/models/gemma-4-E2B-it.litertlm.part` with OkHttp and `Range: bytes=<existing>-`.
- Shows progress through WorkManager foreground info and a notification.
- Renames to `.litertlm` only after size and hash verification.
- Cancellation keeps the `.part` file for resume.

Alternative rejected: bundling the 2.59 GB file in the APK; it breaks install size and updates. The GPU artefact (`gemma-4-E2B-it-gpu.litertlm`, 2.0 GB) is a benchmark candidate, not the v1 default.

### D4. Engine lifecycle and memory policy

`EngineHolder` is a process singleton with a `Mutex` around load/close.

- `load()` runs on `Dispatchers.IO`; `Engine.initialize()` takes seconds and must never block the main thread.
- First successful load starts `AgentWarmService` with a persistent low-importance notification.
- `LocalGlassesApp.onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` and `onLowMemory()` close the engine.
- Before each command, check `ActivityManager.MemoryInfo.availMem`; below ~1 GB, close the engine and report "model reloading".
- Closing the engine does not stop the service; the notification changes to "Agent idle" and the next command reloads.
- Use `ProcessLifecycleOwner` or service lifecycle to decide when to close idle conversations. One conversation per command is closed in `finally`.

The resident footprint is ~1.5–2 GB. The unload threshold is the protection for launching YouTube/WhatsApp after a command.

### D5. Command pipeline and schemas

```
text
  -> AgentViewModel.submit()
  -> SafetyPolicy.checkCommand()          (hard blocks)
  -> EngineHolder.ensureLoaded()
  -> Conversation(prompt, RouterTools)
  -> toolCalls[0]                          (exactly one)
  -> RegistryActionPlan or UiPlan
  -> PlanCoordinator.pending
  -> approval
  -> PlanExecutor
  -> trace + status + TTS
```

- Build a compact system prompt that names the assistant, states "choose exactly one tool, never answer in prose", and lists the installed target packages (WhatsApp/YouTube/YT Music) so the model does not select unavailable apps.
- No date, no history, no user profile, no prior command.
- If `toolCalls` is empty or more than one distinct call is returned, treat it as no-match.
- No-match suggestions are computed locally from the registry descriptions with a normalised Levenshtein/token overlap, never from the model.
- Map `LiteRtLmJniException`, `IllegalStateException`, validation failures, and `ActivityNotFoundException` to `AgentResult` and a single TTS utterance.

`AgentResult`:

```kotlin
sealed interface AgentResult {
    data class NeedsModel(val state: ModelState) : AgentResult
    data object NeedsPermission : AgentResult
    data class NoMatch(val suggestions: List<String>) : AgentResult
    data class PendingPlan(val plan: Plan) : AgentResult
    data class Blocked(val reason: String) : AgentResult
    data class Success(val message: String) : AgentResult
    data class Failed(val step: Int?, val reason: String) : AgentResult
}
```

### D6. Action registry

`ActionRegistry` returns typed `RegistryAction` objects. Each action owns its parameter validator and a `buildPlan(args)` that resolves identities before approval.

```kotlin
interface RegistryAction {
    val id: String
    val description: String
    fun validate(args: JsonObject): Validation
    suspend fun buildPlan(args: JsonObject): PlanStep
    suspend fun execute(step: PlanStep): StepResult
}
```

**Call.** `ContactsResolver` queries `ContactsContract.CommonDataKinds.Phone` with `READ_CONTACTS`.

Resolution order: exact alias (normalised), exact display name, prefix, contains; deduplicate by contact id + number; sort deterministically. Return `Unique`, `Multiple`, or `None`. Never pass candidate numbers to the model.

Aliases live in the encrypted database (`aliases(alias PRIMARY KEY, contact_id, display_name, phone_e164)`) and are edited in Settings. Storing them encrypted avoids a second sensitive store.

Call execution uses `Intent.ACTION_CALL` with a `tel:` URI and `CALL_PHONE`. If the permission is missing, report it; do not fall back to `ACTION_DIAL` silently. `PlanExecutor` launches from the foreground activity; background activity starts are a known platform limit.

**SMS.** Resolve contact, build a one-step plan containing recipient and text. Execute with `SmsManager.getDefault().sendTextMessage` (or multipart when needed). `SEND_SMS` is required.

**WhatsApp.** Resolve phone number in E.164. Launch `whatsapp://send?phone=<digits>&text=<encoded>` with package `com.whatsapp` (fallback `https://wa.me/<digits>?text=...`). The plan's send step targets `viewId = com.whatsapp:id/send` first, then `contentDescription = "Send"`. If neither resolves, fail closed and report "message not sent". Detect the "not on WhatsApp" dialog and fail. No Telegram.

**YouTube / YouTube Music.** Choose target by command and installed package:

- YouTube: `vnd.youtube://results?search_query=<encoded>` with package `com.google.android.youtube`.
- YouTube Music: `https://music.youtube.com/search?q=<encoded>` with package `com.google.android.apps.youtube.music`.

The play step taps the first clickable result whose text or content description contains the query, then a "Play" control if the result page does not auto-play. If the first result cannot be resolved, fail closed. No Spotify.

**Settings.** `SettingsController` exposes a fixed enum:

| Setting id | Implementation | Validation |
|---|---|---|
| `screen_timeout_seconds` | `Settings.System.SCREEN_OFF_TIMEOUT` | 15..1800 |
| `brightness_percent` | `Settings.System.SCREEN_BRIGHTNESS` (0..255) | 0..100 |
| `media_volume_percent` | `AudioManager.setStreamVolume(STREAM_MUSIC, ...)` | 0..100 |
| `dnd_on` / `dnd_off` | `NotificationManager.setInterruptionFilter` | policy access |
| `rotation_lock` / `rotation_unlock` | `Settings.System.ACCELEROMETER_ROTATION` | none |
| `flashlight_on` / `flashlight_off` | `CameraManager.setTorchMode` | rear camera with flash |
| `open_page` | `Settings.ACTION_*` intents | page id in map |

Check `Settings.System.canWrite` before direct writes; check notification-policy access before DND. A missing access produces a `NeedsPermission` result, not a partial write. Brightness writes set manual mode only when needed and report that auto-brightness was turned off.

### D7. Accessibility observation and execution

`AgentAccessibilityService` config:

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:notificationTimeout="100"
    android:flags="flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews" />
```

Observation model:

```kotlin
data class NodeRef(
    val index: Int,
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val password: Boolean,
)
```

Traversal is breadth-first from `rootInActiveWindow`, visible nodes only, capped at 80 nodes with text truncated to 80 characters. Password nodes are recorded as `password = true` with no text. The prompt receives a compact JSON/line list, not the raw tree. Node refs are valid only for one observation; they are never persisted.

`ScreenSignature` = SHA-256 over sorted `(viewId, text, contentDescription, className, bounds)` plus the window title. Used for repeated-screen detection and post-condition checks.

`PlanExecutor`:

1. `SafetyPolicy.checkStep(step, observation)`.
2. Resolve target node by `viewId`, then `contentDescription`, then exact/contains text. Climb to the nearest clickable ancestor for taps.
3. Perform the action: `ACTION_CLICK`, `ACTION_FOCUS` + `ACTION_SET_TEXT`, `ACTION_SCROLL_FORWARD/BACKWARD`, or `delay`.
4. Wait for the post-condition with a 5 s timeout and 100 ms polling. A timeout counts as a failure.
5. Record the step; update `ProgressTracker`.
6. Stop if the stop flag is set, a hard block appears, the screen repeats, or three consecutive steps fail.

`ProgressTracker` keeps the last screen signatures and consecutive failure count. There is no fixed step ceiling.

`OpenApp` uses `packageManager.getLaunchIntentForPackage`; a missing package fails the step. `RunAction` delegates to the registry action executor, which keeps registry plans as a single approved step.

### D8. Plan model and approval

```kotlin
data class Plan(
    val id: String,
    val command: String,
    val steps: List<PlanStep>,
    val targetPackage: String?,
)

sealed interface PlanStep {
    data class RunAction(val actionId: String, val args: JsonObject) : PlanStep
    data class OpenApp(val packageName: String) : PlanStep
    data class Tap(val target: NodeSelector) : PlanStep
    data class TypeText(val target: NodeSelector, val text: String) : PlanStep
    data class Scroll(val direction: Direction) : PlanStep
    data class Wait(val millis: Long) : PlanStep
}
```

`PlanCoordinator` holds one `MutableStateFlow<PlanState>` (`Idle`, `Pending`, `Executing`, `Done`, `Failed`). A new command while `Pending` or `Executing` is rejected with "approve or cancel the pending plan first".

`PlanApprovalSheet` renders human-readable rows. Edits are allowed for `TypeText.text`, `Tap.target`, `RunAction.args`, plus reorder/delete. Any edit sets `approved = false`; `SafetyPolicy.checkPlan(plan)` runs again before approval. The natural-language correction box sends the original command, the current plan, and the correction to the planner and replaces the pending plan.

Approval starts `PlanExecutor`; the agent activity moves to the background when the target app opens. `ExecutionOverlay` is a small `Service` using classic `WindowManager` views (a `TextView` + `Button`), `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`, top-centre. It does not use Compose, avoiding lifecycle-owner plumbing in a service. If `Settings.canDrawOverlays` is false, a UI task that needs another app cannot start; the plan is still reviewable in-app.

### D9. Safety enforcement

`SafetyPolicy` is a pure Kotlin object with a read-only denylist asset:

- Package denylist: banking, UPI, wallets, password managers, known secure apps.
- Category keywords in package labels and node text: bank, pay, upi, wallet, card number, cvv, pin, password, otp, verify, one-time.
- Node `isPassword` / password input type.
- OTP regex: 4–8 digits near otp/one-time/code/verification.
- Hard-block command intents: install/uninstall, delete files/chats/photos, account-security changes.

Checks run at three points:

1. `checkCommand(text)` before the model call.
2. `checkPlan(plan)` before approval.
3. `checkStep(step, observation)` before every step.

Unknown package plus a payment/credential-looking screen denies by default. A hard-block event produces a trace record with package + reason and no screen text.

Secure-screen handling is best-effort: known secure packages are denylisted; a screenshot attempt that returns `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` aborts the task and stores no image. Android does not expose `FLAG_SECURE` through `AccessibilityWindowInfo`, so this is the reliable signal available without MediaProjection. Document the limitation in the UI.

The observation text is passed through `Redactor` before it enters a model prompt, so OTPs and tokens are not sent to the model even when a screen is not hard-blocked.

### D10. Encrypted trace store

Use `net.zetetic:sqlcipher-android:4.20.0` with a hand-written `SQLiteOpenHelper` instead of Room. One small schema, no KSP/annotation processing, and SQLCipher is required either way. Call `SQLiteDatabase.loadLibs(context)` once in `Application`.

Key management:

- Generate 32 random bytes for the database passphrase.
- Encrypt it with an Android Keystore AES/GCM key (`localglasses_trace_db`), store IV + ciphertext in `SharedPreferences`.
- Open with `SupportOpenHelperFactory(passphrase)`.
- If the Keystore key is invalidated, the database is unreadable; offer purge and start fresh rather than silently losing encryption.

Schema:

```sql
traces(id TEXT PRIMARY KEY, started_at INTEGER, finished_at INTEGER, command TEXT,
       task_type TEXT, target_package TEXT, outcome TEXT, model_prompt TEXT,
       tool_calls_json TEXT, plan_json TEXT, error_reason TEXT)
trace_steps(id INTEGER PRIMARY KEY AUTOINCREMENT, trace_id TEXT, ordinal INTEGER,
            step_json TEXT, screen_text TEXT, post_condition TEXT, outcome TEXT,
            duration_ms INTEGER)
aliases(alias TEXT PRIMARY KEY, contact_id TEXT, display_name TEXT, phone_e164 TEXT)
notifications(id INTEGER PRIMARY KEY AUTOINCREMENT, package TEXT, posted_at INTEGER,
              channel TEXT, group_key TEXT)
```

Screenshots live in `filesDir/traces/shots/`, named by trace id + step. Only failure screenshots; cap 50, oldest deleted first. `TraceRecorder` writes on a single-thread dispatcher, applies `Redactor`, and never blocks the command path. A DB failure is logged and surfaced as a non-fatal warning; it does not undo an action.

`TraceExporter` writes JSONL and Markdown to `MediaStore.Downloads` (`RELATIVE_PATH = Download/LocalGlasses`, `IS_PENDING` then clear). No network. `purge()` deletes rows, screenshots, and runs `VACUUM`; exported files in Downloads are untouched.

`AgentNotificationListener` inserts metadata only; it ignores the app's own notifications and never reads `EXTRA_TITLE`/`EXTRA_TEXT`.

`UsageStatsManager` supplies the foreground package for the "current app" status and for resolving "this app" commands. It does not create a separate history; trace steps already record the target package.

Set `android:allowBackup="false"` so the encrypted DB and 2.5 GB model are not backed up to a different device where the Keystore key does not exist.

### D11. Manifest and permissions

Add:

```xml
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

Services:

```xml
<service android:name=".agent.model.AgentWarmService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="on-device command agent keeps a local language model warm" />
</service>

<service android:name=".agent.automation.AgentAccessibilityService"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data android:name="android.accessibilityservice"
        android:resource="@xml/agent_accessibility_service" />
</service>

<service android:name=".agent.trace.AgentNotificationListener"
    android:exported="true"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>

<service android:name=".agent.approval.ExecutionOverlay"
    android:exported="false" />
```

`MainActivity` gets an `ASSIST` intent filter and `launchMode="singleTop"`; `onNewIntent` focuses the command field. It keeps the existing LAUNCHER and `localglasses://` filters.

Add `<queries>` for `com.whatsapp`, `com.google.android.youtube`, `com.google.android.apps.youtube.music`, plus a launcher-intent query. No `QUERY_ALL_PACKAGES`.

Foreground service start uses `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` on API 34+. WorkManager uses `dataSync` foreground info for the download.

Sideload note: Android 13+ requires "Allow restricted settings" in App info before the Accessibility service can be enabled for a sideloaded app. Surface this in the setup checklist instead of failing silently.

### D12. Navigation and UI

`MainActivity` hosts `AgentScaffold` with bottom tabs:

- **Agent**: text field, submit, status, pending plan sheet, permission checklist (only when something is missing), model download/benchmark state, settings entry.
- **Camera**: the existing `GlassesScreen`, extracted from `MainActivity` into `ui/GlassesScreen.kt`.
- **Settings**: model state, aliases, trace export/purge, special-access shortcuts, safety block list (read-only).

Use existing Compose Material3 and `lifecycle-viewmodel-compose`. Add `lifecycle-runtime-compose` for `collectAsStateWithLifecycle`. Do not add a navigation library for two tabs; a simple `selectedTab` state is enough.

`AgentViewModel` exposes `StateFlow<AgentUiState>`; `PlanCoordinator` is injected from `AgentContainer`. TTS is owned by `AgentContainer`, initialised lazily, `QUEUE_FLUSH` per utterance, and released on app termination.

### D13. Dependencies to pin

| Dependency | Version |
|---|---|
| `com.google.ai.edge.litertlm:litertlm-android` | 0.19.1 |
| `net.zetetic:sqlcipher-android` | 4.20.0 |
| `com.squareup.okhttp3:okhttp` | 5.5.1 |
| `androidx.work:work-runtime-ktx` | 2.12.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 |
| `org.jetbrains.kotlin.plugin.serialization` | 2.4.21 (matches Kotlin) |
| `androidx.lifecycle:lifecycle-service` | 2.11.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 |

Also add JUnit 4.13.2, `kotlinx-coroutines-test`, `androidx.test.ext:junit`, and `androidx.test:runner` for tests. OkHttp `mockwebserver` is useful for download-resume tests.

### D14. Testing strategy

Pure logic gets JVM unit tests:

- command routing: no-match suggestions, multiple tool calls, invalid arguments;
- contact resolution: alias precedence, duplicates, multiple matches;
- redaction: OTP, bearer token, API key, password exclusion;
- safety: every hard-block category, unknown payment screen, plan re-check after edit;
- settings validation ranges;
- loop guards: repeated signature, three failures, stop flag;
- trace export JSONL shape and purge semantics;
- plan editing: approval invalidation.

Instrumentation tests:

- SQLCipher database is unreadable without the key, writable with it;
- notification listener stores metadata only;
- export writes to Downloads without network;
- manifest resolves the accessibility and notification services.

Device-only manual checks (cannot be meaningfully automated without the real apps): model download/checksum, CPU vs GPU benchmark, call/SMS, WhatsApp send, YouTube play, settings writes, overlay over another app, memory unload under pressure.

### D15. Rollout and migration

No prior agent data exists, so there is no data migration. The camera pipeline and gallery files are untouched. `allowBackup` changes from `true` to `false`; this is acceptable for a personal sideload build and prevents an encrypted DB from being restored without its Keystore key.

Implementation order is model runtime -> routing -> one registry action -> approval -> accessibility -> safety -> traces, so each layer is testable before the next. The first generic UI task to prove is **WhatsApp send**; it exercises typed text, contact resolution, plan approval, and the send-button selector. YouTube play follows.

Rollback: uninstall and reinstall. Gallery media persists; the model re-downloads.

## Risks / Trade-offs

- **2B planning quality** -> constrained schema, few-shot examples, local validation, editable plan, fail-closed on ambiguity. The model never executes.
- **MT6897 latency** -> benchmark CPU first, try the GPU artefact only if CPU is unacceptable; keep the engine warm; keep prompts and observations compact.
- **Memory pressure** -> unload at critical memory / <1 GB free; failure-only screenshots; cap screenshots; do not keep conversations.
- **Accessibility selector drift** -> resolve by viewId then contentDescription then text; fail closed; keep selectors in one place per app.
- **Secure-screen detection limits** -> package denylist plus screenshot error; document that `FLAG_SECURE` is not exposed to accessibility.
- **Background activity starts** -> execute while the agent activity is foreground; a blocked launch becomes a reported failure. A full-screen notification fallback is a later change.
- **Trace privacy** -> encryption + redaction + explicit export; unbounded retention remains a residual risk, mitigated by purge.
- **Model download** -> resumable, checksum-verified, free-space check; a 2.59 GB failure is expensive but recoverable.
- **OxygenOS differences** -> verify settings keys, assistant reassignment, and restricted-settings toggle on the device.
- **SQLCipher/Keystore invalidation** -> unreadable DB; offer purge and recreate rather than crash.

## Open Questions

- CPU or GPU backend for the shipping build: benchmark both artefacts on the CPH2717 before choosing. This does not change the architecture.
- Whether the `ASSIST` activity alone is selectable as the default assistant on OxygenOS; if not, add the assistant-role request flow in implementation.
- Exact WhatsApp/YouTube view ids and content descriptions on the installed versions; selectors are verified and adjusted during the first device run.
