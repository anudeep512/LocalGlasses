---
type: concept
title: DAT Developer Mode
domain: engineering
created: 2026-11-07
updated: 2026-11-07
---

# DAT Developer Mode

Developer Mode in the Meta AI app is what allows an *unpublished* app to register and talk to
glasses. It is the top cause of "everything looks right but registration does nothing".

## Enabling

1. Phone Settings → **Apps → Meta AI → App info**
2. Tap **App version** five times → Developer Mode toggle appears
3. Enable → confirm
4. Verify: Meta AI → Settings → App connections → **Developer mode apps** lists the app

## Why this project needs it

`app/build.gradle.kts` sets both manifest placeholders to `"0"`:

```kotlin
manifestPlaceholders["mwdat_application_id"] = "0"
manifestPlaceholders["mwdat_client_token"] = "0"
```

Attestation is bypassed in Developer Mode, so `0`/`0` is valid for local builds. Production
requires the real Application ID and Client Token from the Wearables Developer Center.

## Failure symptoms when it is off

- Registration appears to complete but the device never becomes eligible
- Permission requests silently fail for dev builds
- `Wearables.createSession(...)` fails with no eligible device

## It resets

Developer Mode is configured **per linked device** and can turn itself off after app or
firmware updates. Re-check it before blaming code.

Related: [DAT session lifecycle](/concepts/dat-session-lifecycle.md),
[SRC-2026-11-07-001](/sources/SRC-2026-11-07-001.md).
