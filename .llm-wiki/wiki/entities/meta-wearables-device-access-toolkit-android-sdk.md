---
type: entity
title: Meta Wearables Device Access Toolkit (Android SDK)
category: tool
created: 2026-11-07
updated: 2026-11-07
---

# Meta Wearables Device Access Toolkit (Android SDK)

Kotlin Android SDK that lets a third-party phone app reach Meta AI glasses. Published to
Maven Central under the `com.meta.wearable` group, so only `mavenCentral()` is needed — no
access token, no private repo. It is the platform this project is built on.

## Modules

| Artifact | Purpose |
|---|---|
| `mwdat-core` | registration, permissions, devices, session creation |
| `mwdat-camera` | camera capability, video frames, photo capture |
| `mwdat-display` | Display glasses UI: flexbox, text, buttons, icons, video |
| `mwdat-mockdevice` | MockDeviceKit for hardware-free testing |

LocalGlasses uses core + camera + mockdevice; display is deliberately omitted until a
Display target exists. Versions and wiring are in
[LocalGlasses Android setup](/syntheses/localglasses-android-setup.md).

## Hard constraints

- **Kotlin 2.2+** — the toolkit is compiled with Kotlin 2.2. See
  [SRC-2026-11-07-001](/sources/SRC-2026-11-07-001.md).
- Android 10+ (API 29), iOS 17.2+.
- Registration must complete before a session can start.

## See also

- [DAT session lifecycle](/concepts/dat-session-lifecycle.md)
- [Mock Device Kit](/entities/mock-device-kit.md)
- [Wearables MCP server](/entities/wearables-mcp-server.md)
