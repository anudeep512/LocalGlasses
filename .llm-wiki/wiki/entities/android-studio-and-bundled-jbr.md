---
type: entity
title: Android Studio (and bundled JBR)
category: tool
created: 2026-11-07
updated: 2026-11-07
---

# Android Studio (and bundled JBR)

This workstation has Android Studio 2026.2.1 at `/opt/android-studio`, and **no system JDK
and no system Gradle**. The IDE's bundled JetBrains Runtime is the only JVM present:

```
/opt/android-studio/jbr/bin/java   # OpenJDK 25.0.3
```

## Consequences

- Android Studio builds work with zero configuration — the IDE picks its own JBR.
- Command-line `./gradlew` **requires** `JAVA_HOME`:
  ```bash
  export JAVA_HOME=/opt/android-studio/jbr
  export ANDROID_HOME=$HOME/Android/Sdk
  ```
- A JDK 25 runtime rules out Gradle 8.x (which caps at Java 23), which independently forces
  Gradle 9.x — and therefore AGP 9.x.

The absence of a system `gradle` also means the Gradle wrapper had to be generated once
using a manually downloaded distribution. See
[LocalGlasses Android setup](/syntheses/localglasses-android-setup.md).
