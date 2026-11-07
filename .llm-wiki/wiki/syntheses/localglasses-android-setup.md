---
type: synthesis
title: LocalGlasses Android setup
created: 2026-11-07
updated: 2026-11-07
confidence: high
---

# LocalGlasses Android setup — synthesis

How the [LocalGlasses app](/entities/localglasses-app.md) was taken from an empty directory to a
verified, runnable Android build on the [Meta Wearables DAT](/entities/meta-wearables-device-access-toolkit-android-sdk.md).

> **Thesis:** every constraint here is transitive. Platform choice is forced by the host OS,
> the JDK is forced by what the IDE ships, the Gradle version is forced by the JDK, and the
> AGP version is forced by both Gradle and the DAT SDK's Kotlin requirement. Read the chain
> before changing any single version — see
> [Gradle and JDK bootstrap](/concepts/gradle-and-jdk-bootstrap-without-a-system-toolchain.md).

## 1. Platform: Android, not iOS

Linux host, no Xcode. Android was not a preference, it was the only option. Android SDK was
already present (`android-37.0`, build-tools `36.0.0`, `adb`, emulator, one AVD).

## 2. AI tooling

Pi is not a supported DAT plugin client, so:

- **Guidance** → the DAT Android repo's `AGENTS.md` at project root (the `agents` install
  target). See [DAT AI-assisted setup](/concepts/dat-ai-assisted-setup.md).
- **Docs search** → `https://mcp.developer.meta.com/wearables` in `.mcp.json`, surfacing
  `mcp_search_dat_docs`. See [Wearables MCP server](/entities/wearables-mcp-server.md).

Both were verified live: `search_dat_docs` returned the DAT v1.0 first-app guidance.

## 3. Toolchain, forced by the environment

| Constraint | Consequence |
|---|---|
| only JVM is AS's JBR 25 | Gradle 9.x, not 8.x |
| only SDK platform is API 37 | AGP 9.x (8.13 caps at 36.1) |
| DAT needs Kotlin 2.2+ | built-in Kotlin, not the KGP |
| no system gradle | wrapper bootstrapped from a downloaded dist |

Final: **AGP 9.5.1 · Gradle 9.9.0 · Kotlin 2.4.21 · compileSdk/targetSdk 37 · minSdk 29 · DAT 1.0.1**.

`compileSdk` uses the AGP 9 DSL, because platform 37 ships as the minor-versioned `android-37.0`:

```kotlin
compileSdk {
    version = release(37)
}
```

No `org.jetbrains.kotlin.android` plugin anywhere — see
[AGP 9 built-in Kotlin](/concepts/agp-9-built-in-kotlin.md). The Compose compiler plugin is
retained.

## 4. Code

`SessionViewModel` encodes [DAT session lifecycle](/concepts/dat-session-lifecycle.md)
directly, and `MockDevice` encodes the mock eligibility rule from
[Mock Device Kit](/entities/mock-device-kit.md) (`powerOn → unfold → don`).

## 5. Verification actually performed

| Check | Result |
|---|---|
| `assembleDebug` | APK 61 MB |
| `lint` | 0 issues |
| install + launch | no crash, ~0.9 s first frame |
| mock `enable()` | `registrationState` → `REGISTERED` |
| mock pair | Ray-Ban Meta paired |
| `startSession()` | `DeviceSessionState.STARTED` |
| stream | `STOPPED` + `CRITICAL_STREAM_ERROR` — no feed assigned |

## 6. Open items

- **No camera feed** — mock video must be h.265; until then the stream cannot reach `STREAMING`.
- **No physical device tested** — the real path needs the [Meta AI app](/entities/meta-ai-app.md),
  [Developer Mode](/concepts/dat-developer-mode.md), and glasses firmware on the supported matrix.
- **Placeholders are still `0`/`0`** — production needs real Application ID and Client Token plus
  a registered `localglasses://` callback scheme.
- **`mwdat-display` not wired** — only needed for Meta Ray-Ban Display targets.

## Outcome (2026-11-07, later the same day)

The gaps above are closed. The app now runs on real hardware: OnePlus CPH2717 (Android 16) with
Ray-Ban Meta `RB Meta 0HD0` reported `Session: STARTED`, `Stream: STREAMING`, `504x896` frames in
the preview, and a `1080x1440` photo written to `Pictures/LocalGlasses/`.

Mock Device Kit and the phone-camera feed were **removed by request**; the feed source is real
glasses only. Anyone reading the earlier mock sections should treat them as history.

Four faults stood between "mock works" and "glasses work":

1. `BLUETOOTH_CONNECT` granted *after* `Wearables.initialize()` → SDK bonded-device scan throws,
   never retries → *"No eligible device available"* forever.
2. Numeric `0` manifest placeholders → integer-typed meta-data → `Bundle.getString()` null →
   `Attestation failed: TOKEN_NOT_CONFIGURED`.
3. DAT's own `Permission.CAMERA` never requested → stream fails with
   `ERROR_CODE_PERMISSION_DENIED … CAPABILITY_STREAM` while the session still reads `STARTED`.
4. `VideoFrame.buffer` treated as NV12/NV21 when it is **planar I420** → correct luma, green/magenta
   cast.

Details: [DAT real-hardware gotchas](/concepts/dat-real-hardware-gotchas.md) and
[YUV to H.264 recording on MediaTek](/concepts/yuv-to-h264-recording-on-mediatek.md).

Still open: video recording has not been re-confirmed on real glasses since the I420 fix.

## Sources

- [SRC-2026-11-07-001](/sources/SRC-2026-11-07-001.md) — DAT requirements, AI setup, hardware
- [SRC-2026-11-07-002](/sources/SRC-2026-11-07-002.md) — MCP endpoint, client setup
- [SRC-2026-11-07-003](/sources/SRC-2026-11-07-003.md) — AGP 9 built-in Kotlin migration
