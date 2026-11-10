---
type: source
title: LiteRT conversation BenchmarkInfo can be null
status: insight
category: android
created: 2026-11-10
updated: 2026-11-10
slug: litert-conversation-benchmarkinfo-null
---

# LiteRT conversation BenchmarkInfo can be null

In the LocalGlasses real-device run, `Conversation.getBenchmarkInfo()` returned null after `sendMessage`, so prompt/completion token counts, TTFT, prefill/decode tok/s, and engine init remained `Unavailable`. Total inference latency and device metrics were still recorded. The UI must not fabricate token metrics; the mapper keeps them null. If exact token telemetry is required, investigate LiteRT-LM benchmark or streaming APIs rather than estimating from text. See [[sources/obs-2026-11-10-localglasses-stats-for-nerds-design-settled]].

## Fix (verified 2026-11-11)

`Conversation.getBenchmarkInfo()` is null unless the native benchmark collector is enabled. Set
`ExperimentalFlags.enableBenchmark = true` before constructing the LiteRT `Engine`:

```kotlin
@OptIn(ExperimentalApi::class)
override fun load() {
    ExperimentalFlags.enableBenchmark = true
    engine = Engine(EngineConfig(...)).also { it.initialize() }
}
```

With that flag, the CPH2717 run returned a non-null `BenchmarkInfo`: 884 prompt tokens, 16
completion tokens, 5631 ms TTFT, 159.4 tok/s prefill, 11.6 tok/s decode, and 1954 ms engine
init. The Stats UI then showed real values instead of `Unavailable`.

*Category: android*

---
*Captured: 2026-11-10*

## Related

_Add links to related pages._
