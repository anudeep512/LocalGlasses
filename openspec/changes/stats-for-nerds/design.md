# Design

## Context

See `proposal.md` for motivation and `specs/` for required behavior.

Current constraints that shape the design:

- `MainActivity` uses hardcoded integer tab indices (`TAB_AGENT`, `TAB_CAMERA`, `TAB_SETTINGS`) and has no Stats surface.
- `CommandRouter.route()` already calls the model and receives `InferenceReply.benchmarkInfo`, but discards it. LiteRT-LM 0.19.1 `BenchmarkInfo` exposes `initTimeInSecond`, `timeToFirstTokenInSecond`, `lastPrefillTokenCount`, `lastDecodeTokenCount`, `lastPrefillTokensPerSecond`, and `lastDecodeTokensPerSecond`.
- `TraceDatabase` creates `traces`, `trace_steps`, and `notifications` with `CREATE TABLE IF NOT EXISTS`. There is no schema-version migration framework, and the encrypted database must not be recreated.
- `TraceExporter` writes JSONL and Markdown to Downloads. `TraceDatabase.purge()` deletes tables and vacuums.
- `MemoryGuard` reads only `ActivityManager.MemoryInfo.availMem`. `AgentContainer` hardcodes `Backend.CPU()`.
- Compose Material3 and `lifecycle-runtime-compose` are already dependencies. There is no chart library.
- `minSdk` is 29, so thermal status is available; `Build.SOC_MODEL` requires API 31 and needs a fallback.

## Goals / Non-Goals

**Goals:**

- One metric model shared by collection, persistence, export, and both UI surfaces.
- System reads isolated behind small interfaces so unavailable or OEM-restricted values degrade to null instead of failing a command.
- Per-request metrics link to an existing trace without changing trace identity or retention.
- Stats UI works from a compile-time flag and adds no new dependency.
- Existing traces survive the database change.

**Non-Goals:**

- Continuous time-series storage, background sampling, or sampling while the app is not visible.
- GPU utilization, NPU metrics, or vendor-specific sysfs probing.
- Benchmark prompt suites or a benchmark runner UI.
- Cloud sync, telemetry upload, or a runtime stats preference.
- Changes to DAT camera behavior.

## Decisions

### 1. Separate live, inference, and request metric models

Use three immutable models in `agent/telemetry`:

- `DeviceSnapshot`: current device, app, thermal, battery, storage, model, and spec values. Every field nullable.
- `InferenceMetrics`: model id, backend, engine loaded, engine init time, prompt/completion tokens, TTFT, prefill/decode tok/s, inference latency. Every inference-specific field nullable.
- `RequestMetrics`: the request's device snapshot, inference metrics, sampled peaks, and command duration. This is the persisted and exported shape.

**Why:** the live UI needs a cheap snapshot, the router needs a small inference payload, and history needs the combined request shape. One all-purpose blob would force the live path to carry inference fields it cannot know and would make null semantics ambiguous.

**Alternative considered:** persist the raw `BenchmarkInfo` and reconstruct later. Rejected because `BenchmarkInfo` is a runtime object, not a stable storage contract, and it does not contain the model id, backend, or total latency.

### 2. Cold flow sampling owned by the visible surface

`MetricsCollector.stream()` returns a cold `Flow<DeviceSnapshot>` that emits immediately and then every second. The Agent and Stats screens collect it inside `repeatOnLifecycle(RESUMED)`. Leaving the screen or backgrounding the app cancels the collection and stops sampling.

**Why:** a cold flow has no global lifecycle to leak, needs no reference counting between two screens, and naturally satisfies the foreground-only requirement. A foreground service would duplicate the existing warm-model service and would keep sampling when no one is looking.

**Trade-off:** a request that continues while the app is backgrounded has no live samples during that period, so peak fields may be null. The persisted snapshot still records the last known values and the final inference metrics. The Agent-tab "Stats for nerds" block auto-expands when a request starts so the request metrics are visible without a tap, while its specification section stays available on the same surface.

### 3. `trace_metrics` table with typed aggregate columns plus full JSON

Add:

```text
trace_metrics(
  trace_id TEXT PRIMARY KEY,
  captured_at INTEGER NOT NULL,
  inference_latency_ms INTEGER,
  ttft_ms INTEGER,
  prompt_tokens INTEGER,
  completion_tokens INTEGER,
  prefill_tps REAL,
  decode_tps REAL,
  peak_app_pss_bytes INTEGER,
  peak_device_cpu_percent REAL,
  worst_thermal_status TEXT,
  snapshot_json TEXT NOT NULL
)
```

Typed columns cover values used by summary cards, sparklines, and range queries. `snapshot_json` holds the complete `RequestMetrics` for the expandable row and export, including per-core frequencies and device specs.

**Why:** adding columns to `traces` would widen the hot trace table and make every trace query carry metric data. A single JSON column alone would force parsing every row for aggregates. The split keeps queries cheap and the full snapshot lossless.

**Migration:** `CREATE TABLE IF NOT EXISTS` in the existing `createSchema()` is additive. No existing table is altered or dropped, so old traces remain readable with no metrics row. `purge()` deletes `trace_metrics` before `traces`.

**Alternative considered:** a schema-version framework. Rejected for one additive table; it adds more moving parts than the change needs.

### 4. Metrics travel with `RoutingDecision`

Add an optional `metrics: InferenceMetrics?` to the `RoutingDecision` sealed interface, defaulting to null on every variant. `ToolCallParser` keeps returning decisions with null metrics; `CommandRouter` measures latency around the model call and returns `decision.withMetrics(...)`.

**Why:** `AgentViewModel` already receives exactly one `RoutingDecision` per command. Adding an optional field preserves every existing constructor and test while giving the view model the metrics at the same moment as the decision. A separate `RouteResult` wrapper would touch `PlanReplanner`, `PlanFactory`, and several tests for no behavioral gain.

`CommandRouter` also starts a monotonic timer before `engineHolder.ensureLoaded()`. The total latency therefore includes model load when the engine was cold, which is the number the user needs for a first-command comparison.

### 5. Request peaks are observed, not invented

`MetricsCollector.beginRequest()` captures a baseline snapshot. Each live sample during the request updates `RequestMetricsSession` peaks. `finish(inference, duration)` builds `RequestMetrics`. If no live sample occurs, peak fields stay null while the baseline snapshot and inference metrics are still stored.

**Why:** it keeps the spec's "peak" honest. A background or very short request must not pretend its starting value was a peak.

### 6. `BenchmarkInfo` mapping lives at the inference boundary

Extend `InferenceEngine` with `modelId` and `backendName`. `LiteRtEngine` receives the model id from the manifest and returns `CPU` or `GPU` as the backend label. A mapper converts `BenchmarkInfo` seconds to milliseconds and token counts to `InferenceMetrics`. When `BenchmarkInfo` is null, only model id, backend, loaded state, and measured latency are populated.

**Why:** the router should not know LiteRT-LM field names, and the Stats UI should not depend on the inference dependency. The mapper is the only place that translates runtime units.

### 7. Stats navigation uses a route enum, not integers

Replace `TAB_*` integers with `enum class AppTab { AGENT, CAMERA, STATS, SETTINGS }`. The navigation list is `AppTab.entries.filter { it != STATS || BuildConfig.STATS_ENABLED }`. The activity keeps the selected tab as an `AppTab`; `onNewIntent` selects `AGENT`.

**Why:** hiding Stats with integer indices would silently shift Camera and Settings. The enum makes the flag a filter, not an index arithmetic problem.

### 8. BuildConfig flag, default true

Add `buildConfigField("boolean", "STATS_ENABLED", "true")` in `defaultConfig`. Compose reads `BuildConfig.STATS_ENABLED` once. There is no settings toggle and no persistence.

**Why:** the user wants a switch that keeps the feature on for this build while allowing it to be removed wholesale. A runtime preference would add storage and a second source of truth for no requested benefit.

### 9. Compose Canvas sparklines, downsampled

Implement a small `Sparkline` composable with `Canvas` and `Path`. Plot at most 120 points by even downsampling; break the path at null values rather than plotting zero. Do not add a chart dependency.

**Why:** the history page needs four small trends, not axes, zoom, or touch exploration. A dependency would be larger than the feature. Per-request history is small enough that a simple Canvas is sufficient.

### 10. Stats tab is read-only; export and purge stay in Settings

`StatsScreen` reads `TraceMetricRow` data and renders it. It has no export, purge, or delete action. `TraceExporter` gains a metrics section in the existing JSONL and Markdown output; `SettingsPlaceholder` keeps the existing export and purge buttons.

**Why:** one export path and one destructive path are easier to test and less likely to split retention behavior. The Stats page stays a viewer.

### 11. System reads are best-effort and isolated

`SystemMetricsSource` groups Android API reads; `ProcMetricsSource` groups `/proc` and sysfs reads. Every read is wrapped so one failure produces a null field. Device specs are built once and cached; free storage, model state, thermal, and battery are read per sample.

Concrete sources:

- Memory: `ActivityManager.MemoryInfo`, `Debug.MemoryInfo`, `Debug.getNativeHeapAllocatedSize()`, `Runtime`.
- CPU: `/proc/stat` deltas for device percentage; `Process.getElapsedCpuTime()` deltas for app percentage; `/sys/devices/system/cpu/cpuN/cpufreq/scaling_cur_freq` for cores; `/proc/self/status` for thread count.
- Thermal: `PowerManager.currentThermalStatus` API 29+.
- Battery: sticky `ACTION_BATTERY_CHANGED` broadcast.
- Storage: `StatFs(filesDir)`.
- Specs: `Build.MODEL`, `Build.SOC_MODEL` API 31+ or `Build.HARDWARE`, `Build.SUPPORTED_ABIS`, `Build.VERSION.RELEASE`, `ActivityManager.memoryClass`.
- Model: `ModelRepository.manifest`, `EngineHolder.isLoaded`, `InferenceEngine.backendName`.

All reads run on `Dispatchers.IO`. App CPU percentage is reported top-style relative to one core and may exceed 100%; the UI labels it as app CPU, not a fraction of total capacity.

### 12. Live rendering has explicit unavailable states

Formatting helpers return `"Unavailable"` for null, never `0`. GPU displays `"No public Android API available"`. The live snapshot shows a last-updated timestamp; the Stats table and expanded row label missing fields.

**Why:** silent zeros are the main failure mode for device telemetry. The spec requires the distinction, and the UI is where it becomes visible.

## Risks / Trade-offs

- **OEM/Android 16 restrictions on `/proc`, sysfs, thermal, or battery reads** → every read is nullable, failures are isolated, and the UI shows unavailable. A device validation task checks the actual CPH2717 values.
- **App CPU percentage can exceed 100%** → label it as app CPU and document top-style semantics in the UI help text or field label.
- **First CPU sample has no delta** → percentages are null until two samples exist; the UI shows unavailable rather than zero.
- **Sampling cost and main-thread jank** → all reads on `Dispatchers.IO`; 1s cadence only while a stats surface is visible.
- **`BenchmarkInfo` arrives only after the reply** → token metrics appear when the request finishes, not during generation. True streaming metrics would need a streaming inference API, which is out of scope.
- **Short or backgrounded requests may have null peaks** → documented as sampled peaks; no fabricated maximum.
- **Additive table may still need migration care** → `CREATE TABLE IF NOT EXISTS` only; an instrumentation test opens a pre-metrics database and asserts old traces survive.
- **JSON snapshot size with forever retention** → snapshots are small and per request; no continuous samples. Existing purge remains the escape hatch.
- **Sparkline cost with many rows** → downsample to 120 points; no per-pixel work.
- **Metrics reveal usage patterns** → data stays inside the existing Keystore-encrypted trace DB; no network path is added.

## Migration Plan

1. Add the empty `trace_metrics` table during normal database open. Existing traces and steps are untouched.
2. Ship the collector and persistence behind `STATS_ENABLED`; when the flag is false, no table writes occur and no stats UI is shown.
3. Add export and purge handling before enabling the Stats tab by default.
4. Rollback is a build with `STATS_ENABLED = false`; existing metrics rows remain readable but hidden. No destructive downgrade is required.

## Open Questions

None. The Grill Me decisions resolve the scope; remaining unknowns are device-read availability and are handled by the unavailable-value contract rather than by an unresolved design choice.
