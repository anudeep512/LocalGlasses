# Proposal

## Why

The agent currently gives no visibility into what inference costs on the phone. `CommandRouter` discards the LiteRT `BenchmarkInfo` returned with every reply, `MemoryGuard` reads only available RAM, and traces record no CPU, memory, thermal, or token-throughput data. Without those numbers, a slow or failing command cannot be diagnosed, and the on-device model cannot be tuned against this device's 7.65 GB RAM and MediaTek CPU.

## What Changes

- Add a foreground-only telemetry collector that samples device memory, app memory, device and app CPU, per-core frequency, thermal status, battery state, storage, and model state once per second.
- Surface LiteRT inference metrics per request: model id, backend, engine load state, init/load time, prompt and completion token counts, time to first token, prefill and decode tokens per second, and total latency.
- Persist one metrics snapshot per trace in the encrypted trace database, including peak memory, CPU, and thermal observations taken during the request.
- Add a compile-time `BuildConfig.STATS_ENABLED` flag, default `true`. When `false`, hide all stats UI.
- Add one collapsible "Stats for nerds" block below the plan on the Agent tab: it contains the per-request metrics and the collapsed device/model specs.
- Add a fourth Stats bottom-navigation tab with a live snapshot, summary cards, Compose-Canvas sparklines, and an expandable per-trace table filtered to 24 hours, 7 days, or all time.
- Include metrics in the existing JSONL/Markdown trace export and delete them with the existing trace purge.
- Explicitly show `GPU: no public API available`; do not fabricate GPU utilization.
- No benchmark runner, no continuous device time-series database, and no runtime preference for the flag.

## Capabilities

### New Capabilities

- `agent/telemetry`: foreground metric sampling, the metric value model, inference metric capture from LiteRT, per-request peak tracking, and persistence of one metrics snapshot per trace.
- `agent/stats-view`: the feature flag, the collapsible Agent-tab blocks, the Stats tab, live refresh behaviour, summary cards, sparklines, history filters, and unavailable-metric presentation.

### Modified Capabilities

- `agent/trace-store`: export and purge requirements now include the per-request metrics snapshot, and retention applies to metrics with the parent trace.

## Impact

- Agent runtime: `AgentContainer`, `AgentViewModel`, `CommandRouter`, `InferenceEngine`/`LiteRtEngine`, `EngineHolder`, `ModelBenchmarker` mapping.
- Persistence: `TraceDatabase` schema migration, `TraceRecorder`, `TraceExporter`, existing purge path.
- UI: `MainActivity` navigation and Agent tab, new stats UI package, `GlassesScreen` untouched.
- Build: an explicit `STATS_ENABLED` BuildConfig field.
- Tests: unit tests for metric parsing and formatting, instrumentation tests for migration, persistence, export, and Compose visibility.
- Out of scope: GPU utilization, benchmark runner, continuous time-series sampling, cloud sync, changes to the DAT camera flow.
