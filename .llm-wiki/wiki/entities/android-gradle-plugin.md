---
type: entity
title: Android Gradle Plugin
category: tool
created: 2026-11-07
updated: 2026-11-07
---

# Android Gradle Plugin

Google's Gradle plugin (`com.android.application`) that builds Android apps. Version
compatibility is the usual source of build breakage, and the matrix moves fast:

| AGP | Min Gradle | Max API |
|---|---|---|
| 8.13 | 8.13 | 36.1 |
| 9.1 | 9.3.1 | 37.0 |
| 9.5 | 9.6.0 | 37 |

Version 9.x is the breaking line: it enables
[built-in Kotlin](/concepts/agp-9-built-in-kotlin.md), which makes applying
`org.jetbrains.kotlin.android` an error rather than a redundancy.

LocalGlasses landed on AGP **9.5.1** with Gradle **9.9.0**, because API 37 was the only
platform installed and the DAT Kotlin 2.2 floor ruled out the older AGP 8 line. Reasoning
in [LocalGlasses Android setup](/syntheses/localglasses-android-setup.md).

See [SRC-2026-11-07-003](/sources/SRC-2026-11-07-003.md).
