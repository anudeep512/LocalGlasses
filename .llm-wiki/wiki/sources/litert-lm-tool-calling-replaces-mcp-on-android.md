---
type: source
title: LiteRT-LM tool calling replaces MCP on Android
status: insight
category: architecture
created: 2026-11-10
updated: 2026-11-10
slug: litert-lm-tool-calling-replaces-mcp-on-android
---

# LiteRT-LM tool calling replaces MCP on Android

For an on-device Android agent, do not build an MCP client/server or A2A HTTP localhost layer. Android has no MCP stdio transport, and a localhost server adds network stack, ports, and foreground-service lifecycles for no benefit.

LiteRT-LM already supplies the missing layer:
- `ToolSet` + `@Tool` / `@ToolParam` generates an OpenAPI-style schema.
- `OpenApiTool` accepts a hand-written schema.
- `ConversationConfig(tools = ...)` plus `automaticToolCalling = false` gives manual tool execution.
- Gemma 4 constrained decoding (FST `GemmaModelConstraintProvider`) forces valid tool-call JSON.

This was verified while planning [[syntheses/localglasses-on-device-agent-architecture]]. The prior plan for localhost A2A servers and a "stdio MCP pipeline" was abandoned on these grounds.

Related: [[concepts/gemma-4-e2b-on-litert-lm-for-android]].

Device correction found in the same review: the target phone (CPH2717, OnePlus Nord CE5 Lite) is MediaTek MT6897 with 7.65 GB RAM, not Snapdragon 8 GB. LiteRT-LM NPU backends are Qualcomm QNN and Google Tensor only, so this phone runs CPU XNNPACK or Mali GPU. Model-card benchmarks from Snapdragon devices do not transfer; benchmark on-device before promising latency.


*Category: architecture*

---
*Captured: 2026-11-10*

## Related

_Add links to related pages._
