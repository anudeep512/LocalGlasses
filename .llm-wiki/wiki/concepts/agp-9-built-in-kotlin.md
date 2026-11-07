---
type: concept
title: AGP 9 built-in Kotlin
domain: engineering
created: 2026-11-07
updated: 2026-11-07
---

# AGP 9 built-in Kotlin

From AGP 9.0, `com.android.application` and `com.android.library` compile Kotlin themselves.
Applying `org.jetbrains.kotlin.android` is now an **error**, not merely redundant.

## Symptoms when the plugin is still applied

```
Cannot add extension with name 'kotlin', as there is an extension already registered with that name.
```
or
```
The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
```

## The four mechanical changes

1. Remove `org.jetbrains.kotlin.android` from module build files, the root build file, and the
   version-catalog `[plugins]` block.
2. `kotlin-kapt` → KSP, or `com.android.legacy-kapt` at the same version as AGP.
3. `android.kotlinOptions {}` → top-level `kotlin.compilerOptions {}`.
4. Kotlin source dirs: `android.sourceSets { kotlin.directories += ... }`, not `kotlin.sourceSets`.

## What is *not* replaced

The Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` is still required and still
versioned independently. Built-in Kotlin replaces only `kotlin-android`.

## `jvmTarget` is now redundant

`kotlin.compilerOptions.jvmTarget` defaults to `android.compileOptions.targetCompatibility`.
Set the Java target once and leave Kotlin alone.

## Opting out is temporary

`android.builtInKotlin=false` + `android.newDsl=false` disables it, but the flag disappears in
AGP 10.0. Treat it as a migration window, not a setting.

Source: [SRC-2026-11-07-003](/sources/SRC-2026-11-07-003.md). Applied in
[LocalGlasses Android setup](/syntheses/localglasses-android-setup.md).
