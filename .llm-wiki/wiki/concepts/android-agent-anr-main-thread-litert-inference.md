---
type: concept
title: "Android agent ANR: main-thread LiteRT inference"
created: 2026-11-10
updated: 2026-11-10
---

# Android agent ANR: main-thread LiteRT inference

## Symptom

On the CPH2717, sending a second message appeared to crash the LocalGlasses app. Android Dropbox
recorded repeated `data_app_anr` events (`Input dispatching timed out ... MainActivity is not
responding`) rather than a Java crash. The main thread waited 5+ seconds for a touch event while
LiteRT inference was running.

## Cause

`CommandRouter.route()` and `UiPlanner.plan()` called the blocking native
`Conversation.sendMessage()` from the `viewModelScope` main dispatcher. On this phone a single
command took roughly 8–13 seconds, so the main thread exceeded the 5-second ANR window. A second
tap arrived while the first inference still owned the main thread, producing the perceived crash.
The dropbox stack showed native `liblitertlm_jni` work on an execution thread while the UI thread
was blocked.

## Fix

Run every blocking model call off the main dispatcher:

```kotlin
withContext(inferenceDispatcher) {
    engine.conversation(...).use { it.send(command) }
}
```

`CommandRouter` and `UiPlanner` now take an injectable `inferenceDispatcher` defaulting to
`Dispatchers.IO`. `AgentViewModel` already launches work in `viewModelScope`; the router switches
only the blocking native call, so UI state updates still return to the main dispatcher.

## Verification

After the fix, during a second model-backed command the Stats tab opened and live metrics updated
while inference was still running. No `data_app_anr` entry appeared after 03:29 on the test run.
Token metrics also became available after enabling `ExperimentalFlags.enableBenchmark`.

## Related

- [[sources/litert-conversation-benchmarkinfo-null]]
- [[sources/android-proc-stat-selinux-inference-metrics]]
- [[sources/obs-2026-11-10-localglasses-stats-for-nerds-design-settled]]
