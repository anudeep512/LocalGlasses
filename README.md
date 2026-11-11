# LocalGlasses

Android app built on the **Meta Wearables Device Access Toolkit (DAT)**.

Builds, installs, launches, registers, and streams from Meta glasses. Photo capture and video
recording are wired to the live glasses feed.

---

## Toolchain (already installed on this machine)

| Tool | Location |
|---|---|
| Android Studio 2026.2.1 | `/opt/android-studio` |
| JDK (bundled JBR 25) | `/opt/android-studio/jbr` |
| Android SDK | `~/Android/Sdk` (platform `android-37.0`, build-tools `36.0.0`) |
| Gradle | wrapper → 9.9.0 (auto-downloaded) |
| AVD | `Medium_Phone_API_37.0` |

There is **no system JDK and no system Gradle**. Use the bundled JBR:

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk
```

Android Studio uses its own JBR automatically — no setup needed there.

## Build

```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew assembleDebug      # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # install to connected device/emulator
./gradlew test               # JVM unit tests
./gradlew connectedDebugAndroidTest   # instrumentation tests on the connected phone
./gradlew lint
```

## Open in Android Studio

1. **File → Open** → `/home/anudeep512/GodsNut/projects/LocalGlasses`
2. Wait for Gradle sync. `local.properties` already points at the SDK.
3. **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** should be the bundled JBR (default).
4. Pick `Medium_Phone_API_37.0` in the device dropdown, press **Run**.

## Project layout

```
app/src/main/java/com/localglasses/app/
├── LocalGlassesApp.kt          Application, DAT init, AgentContainer
├── MainActivity.kt             Agent / Camera / Stats / Settings tab shell
├── ui/GlassesScreen.kt         Camera tab: DAT session, preview, capture
├── ui/stats/                   Live metrics blocks, Stats tab, sparklines
├── agent/telemetry/            Device, CPU, thermal, battery, and inference metrics
├── agent/AgentContainer.kt     Manual service locator for the agent layer
├── session/SessionViewModel.kt DeviceSession + Camera lifecycle
├── capture/VideoRecorder.kt    YUV -> H.264 via MediaCodec + MediaMuxer
├── capture/MediaStoreSaver.kt  Saves photos/videos to the gallery

app/src/test/                   JVM unit tests
app/src/androidTest/            Instrumentation tests
```

`SessionViewModel` follows the DAT rules: subscribe to `state` and `errors` **before** `start()`, attach the camera only on `DeviceSessionState.STARTED`, and never reuse a stopped session.

## Local model

The agent uses **Gemma 4 E2B** in LiteRT-LM `.litertlm` format.

- File: `gemma-4-E2B-it.litertlm`, 2,588,147,712 bytes.
- SHA-256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
- Download: one time from the public Hugging Face LiteRT community repository, started from
  the Agent tab.
- Storage: `files/models/` inside the app sandbox, with a `.sha256` verification marker.
- After verification, inference is fully offline; only the download uses the network.
- The download is resumable (`Range`) and keeps a `.part` file on cancellation.

Benchmark procedure: load the model, run a fixed prompt, and record prefill/decode tokens per
second from the runtime. The CPU backend is the current default; the GPU `.litertlm` artefact is
an optional benchmark comparison. Model-card numbers from Snapdragon devices do not transfer to
this MediaTek phone.

## Stats for nerds

The Stats surfaces are gated by a compile-time flag in `app/build.gradle.kts`:

```kotlin
buildConfigField("boolean", "STATS_ENABLED", "true")
```

When `STATS_ENABLED` is false, the Agent-tab stats blocks and the Stats tab are removed from the
build. There is no runtime preference.

On the Agent tab, one **Stats for nerds** block sits below the plan. It is collapsed by default
and auto-expands when a request starts. Its request-metrics section shows the live device sample
while the model is working, then the completed request's inference metrics, and keeps the last
completed request visible until the next command starts. The same block shows the device model,
SoC, ABIs, Android version, memory class, model id, model file size, backend, engine loaded state,
and the explicit `GPU: No public Android API available` line.

The Stats tab refreshes live memory, CPU, thermal, battery, storage, and model state once per
second while it is visible. It also shows:

- summary cards: requests, success rate, p50/p95 latency, average TTFT, average decode tok/s,
  peak app memory, average device CPU, and latest thermal status;
- latency, decode, memory, and CPU sparklines;
- an expandable table of requests in reverse chronological order, with a 24h default range and
  7d/all-time toggles.

Sampling runs only while the Agent or Stats surface is visible and stops when the app is
backgrounded. All readings are best-effort: a restricted `/proc`, sysfs, thermal, or battery
source becomes `Unavailable`; it is never replaced with zero or an estimate. App CPU is top-style
relative to one core and can exceed 100%.

### Per-request metrics

Every trace stores one metrics snapshot in `trace_metrics`, linked by trace id. Inference fields
are null when the model was not invoked, such as a safety block or cancelled request. Device
metrics are still stored when available. The snapshot includes:

- model id, backend, engine loaded state, and engine initialization time;
- prompt/completion token counts, TTFT, prefill/decode tokens per second, and inference latency;
- device/app memory, device/app CPU, per-core frequency, thread count, thermal status, battery
  temperature/level/charging, free storage, and device specification values;
- peak app PSS, peak device CPU, worst thermal status, and command duration.

`TraceRecorder` writes the snapshot at decision and finish time. Existing databases are upgraded
additively: old traces remain readable with no metrics row. The existing export includes the
complete metrics object in JSONL and a readable metrics block in Markdown; the existing purge
deletes metrics with the parent trace. Metrics inherit the trace retention policy and remain
on-device.

## Agent commands and approval

1. Type a command in the Agent tab. One command resolves to one task.
2. The local model selects a registered action, or a UI plan when no action fits.
3. A plan sheet shows the steps. You can edit, reorder, delete, approve, or cancel.
4. Any edit clears approval; approve the edited plan again.
5. Approval is the only confirmation. Registry actions execute after approval; UI task
   execution uses the accessibility executor.

Commands are stateless: a new command starts with no history from the previous one.

## Dictation

The Agent tab has a **Dictate** button beside the command field. Speech recognition runs
entirely on the phone; no audio leaves the device.

1. The first tap asks for the microphone permission, then for the speech model
   (Whisper small.en int8, ~358 MB, one-time download with checksum verification).
2. A tap starts listening. The utterance ends on a second tap, after about 2 seconds of silence,
   or at a 15-second cap.
3. The final transcript replaces the command field. Submission stays an explicit **Run** tap,
   so a misheard phrase never executes anything.
4. A cancelled or failed dictation leaves whatever you typed untouched. Nothing is ever
   transcribed while you type, and the assist gesture does not start the microphone.

**Audio source.** Settings → Dictation chooses the phone microphone (default) or the glasses
microphone. Glasses audio arrives as raw 16 kHz PCM over a DAT camera stream, so dictation
opens a short glasses session and closes it when the utterance ends. That path needs DAT camera
permission and is a **beta** DAT feature (`AudioCodec.PCM` is marked dev-and-beta only); the
phone path works without DAT at all.

**Speech metrics.** A dictated command records its model, audio source, audio duration,
recognition latency, real-time factor, and endpoint reason in **Stats for nerds** and in the
Stats page. Typed commands show no speech section.

**Evaluation harness.** `EvaluationHarness` scores the shipping model against `name.wav` /
`name.txt` pairs in an app-private `eval-corpus` directory and reports word error rate and
real-time factor. Personal recordings live in `.dictation-corpus/`, which is gitignored:
voice data is never committed.

Commands are stateless: a new command starts with no history from the previous one.

## Supported actions

| Action | Required access | Notes |
|---|---|---|
| Call a contact | Contacts + Phone | Cellular call only |
| Send an SMS | Contacts + SMS | Resolved name and number shown in the plan |
| WhatsApp message | Contacts | Prefills the conversation; automatic send needs accessibility |
| YouTube / YouTube Music | none | Opens the search; automatic play needs accessibility |
| Curated settings | Modify system settings (and DND access) | Timeout, brightness, volume, DND, rotation, flashlight |
| Open a Settings page | none | Display, sound, Wi-Fi, Bluetooth, battery, apps, accessibility, notifications, location |

Spotify, alarms/timers, Telegram, and arbitrary app launching are not supported.

## Deterministic action paths

The model never executes an action. It selects exactly one registered tool; `ToolCallParser`
validates the name and arguments; `PlanFactory` creates a one-step plan; the plan waits for
approval; `AgentViewModel.approvePlan()` runs the matching deterministic path.

| User intent | Model tool | Validated action | Execution path |
|---|---|---|---|
| Call a contact | `call_contact` | `call_contact` | Resolve contact/number, then `ACTION_CALL` |
| Send an SMS | `send_sms` | `send_sms` | Resolve contact, then `SmsManager.sendTextMessage` |
| WhatsApp message | `send_whatsapp` | `send_whatsapp` | Resolve contact, open `wa.me` draft; accessibility can send |
| YouTube / YouTube Music | `play_media` | `play_media` | `YouTubePlayer`: open search, tap first video result |
| Change a setting | `set_device_setting` | `set_device_setting` | `SettingsController.apply` |
| Open a Settings page | `open_settings_page` | `open_settings_page` | `SettingsController.openPage` |
| Generic app/UI task | `plan_ui_task` | `ui_plan` | `WhatsAppUiPlanFactory` when possible, otherwise `UiPlanner`; `PlanExecutor` steps |
| Explicit “using the UI” / “on-screen” | none | `ui_plan` | Router bypasses the model and goes straight to planning |

Unknown tools, missing arguments, extra arguments, wrong types, and multiple tool calls are
rejected before a plan exists. No registry action is invented at runtime.

### YouTube path

For `play Kanye West on YouTube`, the model should emit:

```text
play_media(query="Kanye West", service="youtube")
```

`YouTubePlayer` then:

1. checks that YouTube is installed;
2. opens `https://www.youtube.com/results?search_query=...`;
3. adds `sp=CAMSAhAB` when the query says “most viewed”, “most popular”, or “top viewed”
   (YouTube’s view-count sort);
4. waits for the YouTube package to become the foreground app, then waits up to 12 seconds
   and tries the YouTube thumbnail, YouTube Music thumbnail, and “play video” selectors;
5. checks for playback controls and reports “Playing …” only when they are visible;
   otherwise it reports “Selected a YouTube result; playback is not confirmed”.

For “play most viewed song of Kanye West on YouTube”, the model normally passes the raw
phrase as `query`. This is still a **search + first-result tap**, not a YouTube Data API query:
the app cannot read view counts, exclude Shorts/ads/interviews, or guarantee that the opened
video is the intended “most viewed song”. If YouTube changes its view ids or renders the
result page slowly, the tap can fail and the status says so. Exact playback needs a known
video id (`youtube.com/watch?v=...`) and a future `play_video(videoId)` action.

UI plans now execute silently. There is no black “plan executing” overlay: the target app is
never covered by a LocalGlasses window, and no display-over-other-apps permission is requested.

## Trace data

- Tables: `traces`, `trace_steps`, `trace_metrics`, `notifications` in the encrypted database.
- A trace records the command, resolved task, target package, plan, model/tool JSON,
  step outcome, timing, and any screen text touched.
- Each completed or blocked trace may have one `trace_metrics` snapshot. It is included in the
  JSONL/Markdown export and deleted by the same purge action.
- Failure screenshots are the only images; they are capped and excluded for secure and
  hard-blocked screens.
- Retention: kept until manual purge. Export writes JSONL and Markdown to
  `Download/LocalGlasses/`; no cloud sync.
- Notification rows contain package, timestamp, channel, and group key only.

## Trace storage and keys

Agent traces live in a SQLCipher database at `databases/agent_trace.db`. The database
passphrase is 32 random bytes, wrapped by an Android Keystore AES/GCM key; the plaintext
passphrase is never stored. If the Keystore key is invalidated, the database is deleted and
recreated rather than opened unencrypted.

`android:allowBackup="false"` is deliberate: an encrypted database restored to another device
would not have the Keystore key that decrypts it, and the 2.5 GB model should not be backed up.
Purge deletes traces and screenshots inside the app; files already exported to Downloads are
left alone.

## First-run checklist

1. Install the debug APK.
2. Open the Agent tab and download the 2.59 GB model; wait for **Model ready**.
3. Grant notifications and (for UI tasks) Accessibility. For sideloaded builds, enable
   **Allow restricted settings** in App info before enabling Accessibility.
4. Grant contacts / call / SMS and Modify system settings as needed.
5. Optional: grant Usage access and the battery-optimisation exemption.
6. Type `call 7396512369` and confirm a plan appears; cancel it if you only want to test routing.
7. Type `change the screen timeout to 60 seconds`, approve, and check the value.
8. Export traces from Settings and confirm files land in `Download/LocalGlasses/`.

A real uninstall/reinstall was performed with the verified model restored; the first-run
screen showed **Model not downloaded**, then **Model ready** after restore, and
`call 7396512369` produced a plan.

## Test device

| Item | Value |
|---|---|
| Device | OnePlus **CPH2717** (Nord CE5 Lite), codename `OP613BL1`, product `CPH2717IN` |
| SoC | MediaTek **MT6897** (Dimensity 7300), 8 cores, `arm64-v8a` |
| GPU | ARM **Mali-G615 MC6**, OpenGL ES 3.2, Mali Vulkan |
| NPU | MediaTek; **not used** by LiteRT-LM (NPU backends are Qualcomm QNN / Google Tensor) |
| RAM | 7,653,648 kB (~7.65 GB; 8 GB class) |
| Storage | 102 GB `/data`, 39 GB free during testing |
| Android | 16 (SDK 36), build `BP2A.250605.015`, incremental `V.12ce21f_12f810b_12aacdd` |
| Security patch | 2026-09-01 |
| Display | 1080 × 2392, density 480 (override 408) |
| Kernel | `6.1.157-android14-11-o-g56556c5d6047` aarch64 |
| Model | Gemma 4 E2B `.litertlm`, 2,588,147,712 bytes |
| Runtime | LiteRT-LM 0.19.1 |
| Battery during test | 62%, 31.3 °C; GPU benchmark reached ~88 °C SoC |

### Inference backend

**Production uses the GPU, with a CPU fallback.** `AgentContainer` builds a
`FallbackEngine` that tries `Backend.GPU()` first and loads `Backend.CPU()` only if the GPU
backend cannot initialise (an incomplete OpenCL driver, for example). Stats for nerds reports
the backend that actually loaded, so a silent fallback never looks like GPU.

Measured on CPH2717 (Mali-G615 MC6), Gemma 4 E2B:

| Run | Engine init | Time to first token | Decode |
|---|---|---|---|
| First GPU load ever (OpenCL kernel compile) | 103,277 ms | — | — |
| GPU after the cache is warm | 9,155 ms | 1,166–1,419 ms | 10.0–11.1 tok/s |

The one-time ~100 s kernel compile is why the very first command after a fresh install feels
stuck; later loads reuse the cache. GPU was previously avoided because a benchmark run reached
~88 °C and produced one tool-call parse failure on the YouTube prompt — if long sessions get hot,
the fallback is to construct `LiteRtEngine` with `Backend.CPU()` in `AgentContainer`.

### What "Engine init" means

`Engine init` in Stats for nerds is the time `Engine.initialize()` took, reported by LiteRT-LM
as `benchmarkInfo.initTimeInSecond`. It covers:

1. opening the 2.59 GB `.litertlm` file and validating it;
2. building the compute graph for the chosen backend;
3. backend preparation — for the GPU that means creating the OpenCL context, converting weights
   into the layout the GPU wants, and compiling the kernels (this project's GPU cache is
   ~780 MB of weights plus ~13 MB of compiled programs, in `files/gpu-cache`);
4. allocating the runtime's own buffers.

It is **not** per token and not per command: it happens once per engine instance, which is once
per app process unless `MemoryGuard` releases the engine. That is why every request in the same
session shows the same number (for example 8,748 ms) while latency and decode differ.

CPU initialisation is far cheaper — roughly 0.9 s here — because it memory-maps the weights and
computes directly, with no device context, no weight conversion, and no kernel compilation. The
GPU pays that setup once to make prompt processing much faster:

| | CPU | GPU |
|---|---:|---:|
| Engine init | ~0.9 s | ~9 s warm cache, ~103 s first ever |
| Prefill | 157–477 tok/s | 536–827 tok/s |
| Time to first token | 6.7 s | 1.8 s |
| Decode | 2.3–10.3 tok/s | 10.0–11.1 tok/s |

So GPU wins on the part before the first token, which is most of a short command, and pays for it
in initialisation. The app hides that cost by prewarming the engine as soon as the model is
ready, while the user is still typing.

## Verification (2026-11-11)

- `./gradlew test lint assembleDebug` — pass.
- `openspec validate localglasses-on-device-agent --strict` — pass.
- Device: CPH2717 / Android 16, Gemma 4 E2B loaded via LiteRT-LM 0.19.1.
- Real commands verified: `call 7396512369` (in-call UI reached), SMS to
  `7396512369` (status confirmed), WhatsApp message to `7396512369` (message visible
  in the chat), and `play Kanye West on YouTube` (`PlaybackState=PLAYING`).
- Settings writes verified with instrumentation tests for timeout, brightness, volume,
  rotation, DND, and flashlight; screen timeout also checked manually.
- Trace export/purge verified: files land in `Download/LocalGlasses/`, purge leaves exports.

Known limitations: Accessibility is a manual grant on a sideloaded build; real
calls/messages depend on the SIM and WhatsApp availability; secure-screen detection is
best-effort because Android does not expose `FLAG_SECURE` to accessibility. UI plans run
without an overlay, so the user must return to LocalGlasses to see the final status.

### Benchmark (CPH2717, Gemma 4 E2B, 2048 context)

| Backend | Prefill tok/s | Decode tok/s | TTFT |
|---|---:|---:|---:|
| CPU | 156.8 | 2.28 | 13.50 s |
| GPU | 892.6 | 2.77 | 2.66 s |

The GPU backend had one tool-call parse failure on the YouTube prompt and pushed SoC
temperature to ~88 °C during the run. CPU remains the default for reliability; GPU is an
opt-in experiment. Command-matrix benchmark covers call, SMS, WhatsApp, YouTube, screen
timeout, brightness, and a generic UI task.

Soak: 21 commands across call, SMS, WhatsApp, YouTube, settings, and a UI task, with
WhatsApp/YouTube switches and an engine unload/reload. Available RAM stayed ~3.0 GB,
thermal status 1, battery 62%, no crash.

## Enabling automation access

1. **Accessibility** — Settings → Apps → LocalGlasses → **Allow restricted settings**, then
   Settings → Accessibility → LocalGlasses → **On**. Without this, generic UI tasks are refused.
2. **Usage access** (optional) — Settings → Apps → Special access → Usage access. Used only to
   report the foreground package.

Supported UI step types: `open_app`, `tap` (view id, text, content description, or text
contains), `type`, `scroll`, and `wait`. A step that cannot find its target fails closed.

## Agent safety limits

The agent never automates these categories, even when a plan is approved:

- banking, UPI, payment, and wallet apps
- password fields and password managers
- reading or entering OTPs
- account password or security-setting changes
- installing or uninstalling apps
- deleting files, chats, or photos

Checks run before planning, before approval, and before every UI step, against both the
foreground package and the target node. Unknown packages that look like a payment or
credential screen are denied.

Android does not expose `FLAG_SECURE` through `AccessibilityWindowInfo`. Secure-screen
handling therefore uses a package denylist plus the screenshot API's secure-window error;
a secure screen aborts the task and stores no image.

The notification listener records package, timestamp, channel, and grouping id only. It never
stores notification title or body text, so OTP notifications cannot leak into the trace store.

## Versions

- AGP 9.5.1 (built-in Kotlin — do **not** add `org.jetbrains.kotlin.android`)
- Gradle 9.9.0
- Kotlin 2.4.21 (Compose and serialization compiler plugins)
- compileSdk / targetSdk 37, minSdk 29 (Android 10+)
- DAT `com.meta.wearable:*:1.0.1`

---

## Workflow: brainstorm, then spec, then build

Two tools, nothing else.

**1. Understand the idea — Grill Me**

```
/grill-me Add offline sync with conflict resolution
```

Pi reads the project for facts it can answer itself, then asks only what it cannot, in batched
questionnaire rounds with a recommendation per question. Output: decisions, open questions, risks.

**2. Turn it into a spec and tasks — OpenSpec**

```
/opsx-propose add-offline-sync
```

Creates `openspec/changes/add-offline-sync/`:

| File | Contents |
|---|---|
| `proposal.md` | why, and what changes |
| `specs/` | requirements with WHEN/THEN scenarios |
| `design.md` | technical approach |
| `tasks.md` | implementation checklist |

**3. Execute and close out**

```
/opsx-apply      # works tasks.md, 1.1, 1.2, 2.1 …
/opsx-archive    # moves to changes/archive/, updates living specs
```

`openspec/specs/` accumulates the project's real behaviour over time; archived changes are the
history of how it got there.

### Setup notes

- OpenSpec CLI lives at `~/.local/bin/openspec` (v1.16.1). Re-run `openspec update` after
  upgrading to regenerate the Pi integration.
- Telemetry is disabled (`openspec config set telemetry.enabled false`).
- `.pi/skills/openspec-*` and `.pi/prompts/opsx-*` are generated and gitignored; `openspec/` is
  committed because it is the spec history.
- `.pi/` resources load only after project trust is granted.

## Feed source

The only source is **Meta glasses linked to Meta AI on this phone**. There is no mock device
and no phone-camera fallback. If no glasses are connected and eligible, the session will not
start.

Eligibility requires all of:
- registration `REGISTERED`
- glasses linked to the Meta AI app
- Developer Mode enabled for this build
- glasses powered on, hinges open (unfolded), and being worn
- `Permission.CAMERA` granted through the Meta AI app

The feed pipeline is identical for photo capture and recording: `camera.stream.videoStream`
gives decoded YUV frames, which feed both the on-screen preview and the H.264 encoder.

### Frame format

The SDK delivers decoded YUV 4:2:0. `SessionViewModel.nv21ToBitmap` treats it as **NV21** and
converts through `YuvImage` → JPEG → `Bitmap`. `VideoRecorder` converts the same NV21 buffer to
planar I420 for a software H.264 encoder. If colours come out with red/blue swapped on your
glasses model, the buffer is NV12/I420 instead — swap the chroma bytes.

### Recording notes

`VideoRecorder` deliberately avoids two MediaTek traps:
- `COLOR_FormatYUV420SemiPlanar` is accepted by `configure()` but encodes **black** frames.
- `MediaCodec.getInputImage()` segfaults inside its JNI copy.

So it selects a codec supporting `COLOR_FormatYUV420Planar`, preferring the software encoder, and
feeds tightly packed I420 through plain input buffers. All `MediaCodec` calls run on a
single-permit dispatcher, because encode and stop must share one thread.

Captures land in the gallery:
- photos -> `Pictures/LocalGlasses/`
- videos -> `Movies/LocalGlasses/`

## Setting up a real phone

### 1. Phone requirements
- Android 10 (API 29) or newer
- Bluetooth enabled
- **Meta AI app** installed and signed in
- Glasses paired to the Meta AI app

### 2. Enable USB debugging
1. Settings → About phone → tap **Build number** 7×
2. Settings → System → Developer options → enable **USB debugging**
3. Connect USB, accept the RSA fingerprint prompt

Verify:
```bash
adb devices          # should list your phone as "device"
```

### 3. Enable Developer Mode in the Meta AI app
Required for builds using `mwdat_application_id = 0` / `mwdat_client_token = 0`.

1. Phone Settings → **Apps → Meta AI → App info**
2. Tap **App version** 5× → a Developer Mode toggle appears
3. Enable it → **Enable** to confirm
4. Confirm your app shows under **Meta AI → Settings → App connections → Developer mode apps**

Developer Mode is **per linked device** and can reset after app or firmware updates. Re-check it if registration stops working.

### 4. Check glasses firmware
Meta AI → **Devices** tab → your glasses → gear → **General → About → Version**

Must be at or above the minimum in the [version dependencies matrix](https://wearables.developer.meta.com/docs/version-dependencies).

### 5. Install and run
```bash
export JAVA_HOME=/opt/android-studio/jbr
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew installDebug
adb shell am start -n com.localglasses.app/.MainActivity
```

Then in the app: **Register** → the Meta AI app opens → approve → status becomes `REGISTERED` → **Start session**.

### 6. Grant permissions
Camera access is granted through the Meta AI app, not the Android permission dialog. `Wearables.checkPermissionStatus(Permission.CAMERA)` reports the result.

---

## Production

Replace the `"0"` placeholders in `app/build.gradle.kts` with the Application ID and Client Token from the [Wearables Developer Center](https://wearables.developer.meta.com/), and register the `localglasses://` callback scheme for the app.

## Docs

- [DAT developer docs](https://wearables.developer.meta.com/docs/develop/)
- [Android API reference](https://wearables.developer.meta.com/docs/reference/android/dat/latest)
- [Version dependencies](https://wearables.developer.meta.com/docs/version-dependencies)
- Live docs search: `search_dat_docs` via the Wearables MCP server (configured in `.mcp.json`)
