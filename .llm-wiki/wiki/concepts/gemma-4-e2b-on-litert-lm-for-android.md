---
type: concept
title: Gemma 4 E2B on LiteRT-LM for Android
created: 2026-11-10
updated: 2026-11-10
---

# Gemma 4 E2B on LiteRT-LM for Android

Verified against the Gemma 4 launch material and LiteRT-LM docs on 2026-11-11. Gemma 4 shipped 2026-04-02 (Apache 2.0) in E2B, E4B, 12B, 26B A4B, and 31B sizes, with MTP variants on 2026-04-16.

## Model

| Property | Value |
|---|---|
| Chosen variant | **gemma-4-E2B-it** |
| Format | `.litertlm` (LiteRT-LM) |
| File size | **2583 MB** |
| Weight quantisation | mixed **2-bit / 4-bit / 8-bit** (QAT) |
| Text weight footprint | as low as **0.8 GB**, plus ~1.12 GB of memory-mapped embeddings |
| Context | up to **32k**; benchmarks at 2048 |
| Modality | text, image, audio (vision/audio loaded on demand) |
| License | Apache 2.0 |
| Hub | `litert-community/gemma-4-E2B-it-litert-lm` |

E4B is roughly 4.5 GB and was rejected for a 7.65 GB phone. E2B is the only mobile Gemma 4 size that leaves OS headroom.

## Runtime

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
```

```kotlin
val config = EngineConfig(
    modelPath = modelFile.absolutePath,
    backend = Backend.CPU(),            // or Backend.GPU()
    cacheDir = context.cacheDir.path,   // improves second load
)
Engine(config).use { engine ->
    engine.initialize()                 // seconds; never on the main thread
    engine.createConversation(
        ConversationConfig(systemInstruction = Contents.of(prompt)),
    ).use { conversation ->
        conversation.sendMessageAsync(command).collect { chunk -> /* ... */ }
    }
}
```

GPU on Android needs these in `<application>`:

```xml
<uses-native-library android:name="libvndksupport.so" android:required="false"/>
<uses-native-library android:name="libOpenCL.so" android:required="false"/>
```

Other runtime facts:

- `ExperimentalFlags.enableSpeculativeDecoding = true` enables MTP, recommended on GPU.
- `ThinkingConfig(enableThinking, thinkingTokenBudget)` separates reasoning tokens into a `thought` channel.
- `sendMessage`, `sendMessageAsync(callback)`, and `sendMessageAsync(): Flow<Message>` all exist.
- Errors surface as `LiteRtLmJniException` or `IllegalStateException`.

## Tool calling and constrained decoding

Two ways to define tools:

1. Kotlin: `class X : ToolSet` with `@Tool(description = ...)` methods and `@ToolParam` parameters. Supported parameter types: `String`, `Int`, `Boolean`, `Float`, `Double`, or lists of those. The runtime generates an OpenAPI-style schema.
2. `OpenApiTool`: supply the JSON schema yourself and implement `execute(paramsJsonString): String`.

Register with `ConversationConfig(tools = listOf(tool(myToolSet)))`.

- `automaticToolCalling = false` returns `Message.toolCalls` and lets the app execute manually, then send `Message.tool(Contents.of(toolResponses))`.
- Constrained decoding (`GemmaModelConstraintProvider`, FST-based) forces syntactically valid Gemma 4 tool-call JSON. This is the replacement for hand-written GBNF grammars.
- This is also why the app does not need MCP or a localhost server: the schema and the grammar come from the same runtime.

## Device reality (measured)

| Fact | Value |
|---|---|
| Phone | CPH2717, OnePlus Nord CE5 Lite |
| SoC | MediaTek **MT6897** (Dimensity 7300), 8 cores |
| GPU | Mali-class, OpenCL support unverified for LiteRT-LM |
| NPU | **Unsupported** — LiteRT-LM NPU backends are Qualcomm QNN and Google Tensor |
| RAM | 7.65 GB physical, 2.66 GB available at idle |
| Swap | 7.86 GB compressed, ~3 GB used at idle |

The model card benchmarks are Snapdragon 8 / Apple / desktop parts. They do not predict MT6897 performance. The first engineering task is an on-device benchmark of CPU (4 threads) versus GPU, measuring time-to-first-token and decode tokens/s at 2048 context.

Android AICore / Gemini Nano is the recommended production path on supported devices, but it is not available on this phone and would hand control of the model to the system. It is not an option here.

## Pitfalls

- **MediaPipe LLM Inference is maintenance-only.** New work belongs in LiteRT-LM.
- `.litertlm` files are 2.5 GB; download must be resumable and checksum-verified, and the app must handle the user deleting the model.
- A resident engine is ~1.5–2 GB. The phone already uses ~3 GB of compressed swap at idle, so warm residency needs a memory-pressure unload path.
- Context is the real budget. A full accessibility view tree will not fit; summarise it before prompting.
- Quantised weights are high-entropy and compress poorly, so swap does not rescue a model that no longer fits.
- Gemma 4 E2B tool calling quality on a multi-step UI plan is unproven at 2B; expect to constrain the plan schema hard.

## Related

- [LocalGlasses on-device agent architecture](/syntheses/localglasses-on-device-agent-architecture.md)
- [Accessibility-driven Android agent safety model](/concepts/accessibility-driven-android-agent-safety-model.md)
- [AGP 9 built-in Kotlin](/concepts/agp-9-built-in-kotlin.md)
