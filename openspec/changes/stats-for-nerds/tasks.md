# Tasks

## 1. Feature Flag and Navigation

- [x] 1.1 Add `buildConfigField("boolean", "STATS_ENABLED", "true")` to the app's `defaultConfig` and verify `./gradlew assembleDebug` generates a `BuildConfig` with `STATS_ENABLED = true`.
- [x] 1.2 Replace the `TAB_AGENT`/`TAB_CAMERA`/`TAB_SETTINGS` integer constants with an `AppTab` enum and a pure `visibleTabs(statsEnabled)` helper; verify a unit test returns Agent, Camera, Stats, Settings when enabled and excludes Stats when disabled.
- [x] 1.3 Wire the filtered tab list into `MainActivity`, keep the selected tab stable when Stats is hidden, and add a placeholder Stats destination; verify a Compose test selects each visible tab and `onNewIntent` still selects Agent.

## 2. Metric Models and Collection

- [x] 2.1 Add `DeviceSnapshot`, `InferenceMetrics`, and `RequestMetrics` immutable models with nullable fields and a formatting helper that returns `"Unavailable"` for null; verify unit tests cover null, zero, bytes, percent, milliseconds, and tokens-per-second formatting.
- [x] 2.2 Add a device-spec provider using `Build`, `ActivityManager.memoryClass`, `StatFs`, the model manifest, and the engine state; verify a unit test with fake values covers the API 31 `SOC_MODEL` path and the pre-31 `HARDWARE` fallback.
- [x] 2.3 Add Android memory, thermal, battery, and storage reads with per-field failure isolation; verify an instrumentation test on the CPH2717 records readable values and leaves restricted fields null without throwing.
- [x] 2.4 Add a pure `/proc/stat` parser and a CPU sampler using two deltas, `Process.getElapsedCpuTime()`, per-core sysfs frequency, and `/proc/self/status` thread count; verify unit tests cover the first sample returning null percentages, a known delta, a missing core, and a missing thread count.
- [x] 2.5 Implement `MetricsCollector.snapshot()` and a cold `stream()` that emits immediately then every second; verify a coroutine test with a fake source observes one-second emissions and confirms cancellation stops further reads.
- [x] 2.6 Add `modelId` and `backendName` to `InferenceEngine`, pass the manifest id and CPU backend from `AgentContainer` into `LiteRtEngine`, and expose loaded state; verify the existing `EngineHolderTest` and model-runtime tests compile and pass with the widened interface.

## 3. Inference Metrics and Request Session

- [x] 3.1 Add a `BenchmarkInfo` mapper producing `InferenceMetrics`; verify unit tests convert seconds to milliseconds, copy prefill/decode token counts and tok/s, and produce only model/backend/latency when `BenchmarkInfo` is null.
- [x] 3.2 Add optional `metrics` to every `RoutingDecision` variant plus a `withMetrics` helper; verify unit tests attach metrics to Action, UiPlan, NoMatch, and Failed without changing existing parser assertions.
- [x] 3.3 Measure model-call latency in `CommandRouter`, attach metrics to the returned decision, and keep the latency on inference failure; verify router tests with a fake engine assert latency is non-negative and metrics are null for pre-model blocks.
- [x] 3.4 Implement `RequestMetricsSession` with `begin`, `observe`, and `finish`; verify unit tests produce null peaks with no observation, maximum memory/CPU and worst thermal with observations, and a completed snapshot with inference metrics and command duration.
- [x] 3.5 Start the request session on submit, feed it live samples, persist metrics at decision and finish, and expose live plus last-request state from `AgentViewModel`; verify `AgentViewModelStateTest` covers a completed run, a blocked run with null inference, and a cancelled run.

## 4. Trace Persistence, Export, and Purge

- [x] 4.1 Add the `trace_metrics` table, a metrics upsert, a per-trace read, a range query joined to traces, and metrics deletion in `purge()`; verify an instrumentation test writes one snapshot per trace, rejects a duplicate as an update, and reads it back after reopening the database.
- [x] 4.2 Verify the additive migration by opening a database created with the pre-metrics schema and asserting existing traces and steps survive with no metrics row.
- [x] 4.3 Add metrics persistence to `TraceRecorder` at decision and finish time; verify an instrumentation test inspects a successful trace with inference metrics and a blocked trace with device metrics but null inference fields.
- [x] 4.4 Extend `TraceExporter` JSONL and Markdown with the metrics snapshot and an explicit unavailable state; verify `TraceExporterTest` reads the exported files and asserts latency, token, memory, CPU, and thermal fields plus the no-metrics case.
- [x] 4.5 Extend `TraceDatabase.purge()` and its test to assert metrics rows are deleted while exported files outside the app remain untouched.

## 5. Agent-Tab Stats UI

- [x] 5.1 Build the collapsed `StatsForNerdsBlock` below the plan with the request-metrics section, device specs, model id, backend, loaded state, and the GPU no-API message; verify a Compose test asserts it starts collapsed, auto-expands on request start, and shows the spec values and message after expansion.
- [x] 5.2 Render the request-metrics section inside `StatsForNerdsBlock`, showing live device metrics then final inference metrics; verify a Compose test covers the initial empty state, running state, completed state, and manual collapse.
- [x] 5.3 Collect `MetricsCollector.stream()` on the Agent screen only while the lifecycle is resumed and forward samples to the view model; verify a Compose test with a fake flow observes collection start on resume and no emission after the lifecycle stops.
- [x] 5.4 Render unavailable metrics as `"Unavailable"` in both Agent-tab blocks and keep the last completed request visible until the next submit; verify Compose tests for a null-metric snapshot and for a second request replacing the first.

## 6. Stats Tab

- [x] 6.1 Implement `StatsViewModel` with range selection (24h default, 7d, all), database loading off the main thread, and aggregate calculation; verify unit tests for empty data, success rate, median, p95, average TTFT, average decode tok/s, peak memory, average CPU, and exclusion of null values.
- [x] 6.2 Build the Stats-tab live snapshot card for memory, CPU, thermal, battery, storage, and model state with a last-updated label; verify a Compose test renders readable values and the unavailable label for null fields.
- [x] 6.3 Build summary cards and their empty state; verify a Compose test shows no fabricated zeros when the range has no traces.
- [x] 6.4 Implement a Compose-Canvas sparkline with downsampling to 120 points and gaps for null values; verify unit tests for downsampling and gap detection plus a Compose render test for one-point, multi-point, and all-null series.
- [x] 6.5 Build the reverse-chronological trace table with collapsed summary and expanded full snapshot; verify a Compose test expands a row and asserts unavailable fields are labelled.
- [x] 6.6 Wire the range selector so cards, sparklines, and table reload for 24h, 7d, and all time; verify a Compose test switches all three ranges and observes the matching row count.
- [x] 6.7 Verify the Stats tab exposes no export or purge control and that Settings still exposes both; verify a Compose test asserts the absence and presence of those controls.

## 7. Integration, Device Validation, and Documentation

- [x] 7.1 Run `./gradlew test lint assembleDebug` and fix every failure; verify the command exits zero.
- [x] 7.2 On the CPH2717, run a model-backed command and a blocked command, then verify the Agent-tab block, the persisted Stats row, and the row after an app restart.
- [x] 7.3 Export traces and verify the JSONL contains a metrics object and the Markdown contains the latency, token, memory, CPU, and thermal summary; purge and verify no metrics remain.
- [x] 7.4 Build once with `STATS_ENABLED = false` and verify no Agent-tab stats block and no Stats tab appear, then restore `true` and verify both return.
- [x] 7.5 Verify a restricted or unavailable metric source on device (for example an unreadable core frequency or thermal value) renders `"Unavailable"` and does not fail the command.
- [x] 7.6 Document the flag, metric definitions, unavailable behavior, per-request persistence, export fields, retention, and GPU limitation in `README.md`; verify the documented build and export steps match the implementation.

## Workflow follow-up

- Archive the change after review and device validation are complete.
- Verify the archived `agent/telemetry` and `agent/stats-view` specs exist and the `agent/trace-store` spec contains the metrics export and purge requirements.
