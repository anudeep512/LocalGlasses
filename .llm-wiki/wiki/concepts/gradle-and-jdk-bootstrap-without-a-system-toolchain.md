---
type: concept
title: Gradle and JDK bootstrap without a system toolchain
domain: engineering
created: 2026-11-07
updated: 2026-11-07
---

# Gradle and JDK bootstrap without a system toolchain

Scaffolding [LocalGlasses](/entities/localglasses-app.md) on a machine with
[Android Studio but no system JDK or Gradle](/entities/android-studio-and-bundled-jbr.md)
surfaced a bootstrap problem worth remembering, because each step constrains the next.

## The chain

1. **No JDK** → the only JVM is Android Studio's JBR 25.
2. **JVM 25** → Gradle 8.x is out (caps at Java 23) → Gradle 9.x.
3. **No system `gradle`** → the wrapper jar cannot be generated the usual way.
4. **Wrapper jar is binary** → cannot be produced by text file edits.
5. **SDK only had `android-37.0`** → AGP 8.13 tops out at API 36.1 → AGP 9.x anyway.
6. **AGP 9 + DAT's Kotlin 2.2 floor** → built-in Kotlin, no `kotlin-android` plugin.

## The workaround

Download a Gradle distribution once, then use it to write the wrapper:

```bash
curl -sL -o gradle-9.9.0-bin.zip https://services.gradle.org/distributions/gradle-9.9.0-bin.zip
unzip -q gradle-9.9.0-bin.zip -d ~/.local/

export JAVA_HOME=/opt/android-studio/jbr
~/.local/gradle-9.9.0/bin/gradle wrapper --gradle-version 9.9.0
```

After that `./gradlew` is self-sufficient.

## Wrapper download timeout

The generated `gradle-wrapper.properties` defaults to `networkTimeout=10000`, and the wrapper
then failed to fetch its distribution:

```
Downloading ... gradle-9.9.0-bin.zip failed: timeout (10000ms)
Caused by: java.net.SocketTimeoutException: Connect timed out
```

Raise it to `120000`. This is a one-line fix that looks like a network outage.

## Emulator lifecycle

A background emulator started with `nohup ... &` dies when its launching shell exits. Use
`setsid` so it survives:

```bash
setsid nohup emulator -avd Medium_Phone_API_37.0 -no-window -no-audio -no-snapshot \
  -gpu swiftshader_indirect < /dev/null > /tmp/emu.log 2>&1 &
```
