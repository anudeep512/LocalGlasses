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
├── LocalGlassesApp.kt          Wearables.initialize(this)
├── MainActivity.kt             Compose status screen + controls
├── session/SessionViewModel.kt DeviceSession + Camera lifecycle
├── capture/VideoRecorder.kt    YUV -> H.264 via MediaCodec + MediaMuxer
├── capture/MediaStoreSaver.kt  Saves photos/videos to the gallery
```

`SessionViewModel` follows the DAT rules: subscribe to `state` and `errors` **before** `start()`, attach the camera only on `DeviceSessionState.STARTED`, and never reuse a stopped session.

## Versions

- AGP 9.5.1 (built-in Kotlin — do **not** add `org.jetbrains.kotlin.android`)
- Gradle 9.9.0
- Kotlin 2.4.21 (Compose compiler plugin only)
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
