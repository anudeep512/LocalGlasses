# Grill Me Results

Generated: 2026-11-10T21:14:34.118Z

## Plan

Now I want to introduce a view for all the metrics of the phone which should be a flippable implementation using a flag and for me keep it as true that is I should always see the metrics that you are going to present. Now the idea is a as follows show me the ram usage and all the stats that makes sense when running inference on the app so that I can use it for seeing whats happening add all such meaning metrics that are used in inference engineering and I want to see all those to make a good sense of the metrics. Show stats like model used device specs and keep it in a collapsable block and user can see if he wants to see that. And then create me a stats page for seeing all this data in curated form. The per request data should be visible for me live below the run in a collapsable and all the historical data should be present in that page and this info should be persisted in the trace log that we already have an example for live data is ram usage and other meaning full metrics that is generally looked in inference engineering.and when showing the specs of the users phone use system apis to get all that data for displaying and name this collapsable as Stats for nerds.

## Shared Understanding

LocalGlasses gains a build-flagged Stats feature. `BuildConfig.STATS_ENABLED = true` keeps it always visible for this device: a live \"Stats for nerds\" section under the Run control on the Agent tab, plus a fourth Stats tab. One foreground sampler refreshes memory, CPU, thermal/battery, storage, model and device data every second while Agent or Stats is visible, and pauses in the background. Each command writes a per-request metrics snapshot linked to its existing trace: model id/backend/load state, prompt and completion token counts, TTFT, prefill/decode tok/s, total latency, peak memory, CPU and thermal state during the run. The Agent tab auto-expands request metrics and keeps device/model specs collapsed. The Stats tab shows a live snapshot, summary cards, Compose-Canvas sparklines and an expandable trace table over a 24h/7d/all-time range, read-only. Existing JSONL/Markdown trace export includes the metrics, and the existing purge deletes them. No GPU utilization (no public Android API), no benchmark runner, no continuous time-series database.

## Questions and Answers

### 1. How should the Stats feature be toggled?

**Recommended answer:** Compile-time BuildConfig flag, default true

**User answer:** Compile-time BuildConfig flag, default true

**Status:** resolved

**Notes:** One switch hides both live block and Stats tab; no runtime preference surface.

### 2. Where should the historical Stats page live?

**Recommended answer:** New fourth bottom-nav tab: Stats

**User answer:** New fourth bottom-nav tab: Stats

**Status:** resolved

**Notes:** Navigation becomes Agent / Camera / Stats / Settings.

### 3. Which metric groups should v1 collect and show?

**Recommended answer:** All listed groups: memory, CPU, thermal/battery, model/inference, storage, device specs

**User answer:** Memory: device total/available/used, app PSS/Java/native heap, low-memory flag; CPU: device CPU %, app-process CPU %, per-core frequency, thread count; Thermal/battery: thermal status, battery temp, level, charging state; Model/inference: model id, backend, loaded state, load time, prompt/completion tokens, TTFT, prefill/decode tok/s, total latency; Storage: model file size, free internal storage; Device specs: SoC, ABI list, Android version, RAM class, device model

**Status:** resolved

**Notes:** All six metric groups selected.

### 4. What should the persisted history contain?

**Recommended answer:** Per-request snapshots only

**User answer:** Per-request snapshots only

**Status:** resolved

**Notes:** No continuous device time series; each trace stores its own inference metrics plus peak memory/thermal snapshot.

### 5. How often should the live metrics refresh?

**Recommended answer:** 1 second while visible, pause in background

**User answer:** 1 second while visible, pause in background

**Status:** resolved

**Notes:** Collector runs only while Agent or Stats screen is visible.

### 6. How should the two collapsible blocks default?

**Recommended answer:** Request metrics expanded, device/model specs collapsed

**User answer:** Request metrics expanded, device/model specs collapsed

**Status:** resolved

**Notes:** Per-request block auto-expands once a run starts or completes; 'Stats for nerds' specs block stays collapsed by default.

### 7. Which outcomes should persist metrics?

**Recommended answer:** All traces; nullable inference fields when model not invoked

**User answer:** All traces; nullable inference fields when model not invoked

**Status:** resolved

**Notes:** Success, failure, blocked, no-match and cancelled all store metrics where available.

### 8. How should the Stats page present history?

**Recommended answer:** Summary cards + sparklines + expandable trace table

**User answer:** Summary cards + sparklines + expandable trace table

**Status:** resolved

**Notes:** Sparklines use Compose Canvas; no new chart dependency.

### 9. What time range should the Stats page show by default?

**Recommended answer:** Last 24h default; 7d and all-time toggles

**User answer:** Last 24h default; 7d and all-time toggles

**Status:** resolved

**Notes:** Older traces remain reachable via all-time.

### 10. What CPU numbers should be shown?

**Recommended answer:** Device CPU % + app-process CPU % + per-core frequency

**User answer:** Device CPU % + app-process CPU % + per-core frequency

**Status:** resolved

**Notes:** Separates phone load from agent cost and exposes thermal throttling.

### 11. Should v1 attempt GPU metrics?

**Recommended answer:** Skip GPU utilization; display backend only

**User answer:** Skip GPU metrics and say "No API available" or another relevant message

**Status:** resolved

**Notes:** Show an explicit 'GPU: no public API available' line rather than a fake number.

### 12. Should the existing trace export include metrics?

**Recommended answer:** Include metrics in existing JSONL/Markdown export; Stats page read-only

**User answer:** Include metrics in existing JSONL/Markdown export; Stats page read-only

**Status:** resolved

**Notes:** One export and purge path; Stats tab has no destructive controls.

### 13. Should the Stats page also run benchmarks?

**Recommended answer:** Passive telemetry only; no benchmark runner

**User answer:** Passive telemetry only; no benchmark runner

**Status:** resolved

**Notes:** ModelBenchmarker remains an instrumentation test; Stats shows recorded runs only.

## Agreed Decisions

- Feature flag: compile-time `BuildConfig.STATS_ENABLED = true`; when false, hide both the live Stats for nerds block and the Stats tab. No runtime preference.
- Stats page placement: new fourth bottom-navigation tab, order Agent / Camera / Stats / Settings.
- Metric groups: memory (device total/available/used, app PSS/Java/native heap, low-memory flag), CPU (device %, app-process %, per-core frequency, thread count), thermal/battery (thermal status, battery temp/level/charging), model/inference (model id, backend, loaded state, load time, prompt/completion tokens, TTFT, prefill/decode tok/s, total latency), storage (model file size, free internal storage), device specs (SoC, ABIs, Android version, RAM class, device model).
- History granularity: one per-request snapshot only. Each trace stores its own inference metrics plus peak memory/thermal/CPU observed during that request. No continuous device time series.
- Live refresh: every 1 second while the Agent or Stats screen is visible; paused when the app is backgrounded.
- Collapsible defaults: per-request metrics block expands automatically once a run starts or completes; device/model 'Stats for nerds' block is collapsed by default.
- Trace coverage: every trace records metrics where available, including success, failure, blocked, no-match and cancelled. Inference-only fields are null when the model was not invoked.
- History presentation: summary cards plus small Compose-Canvas sparklines plus an expandable per-trace table. No new chart dependency.
- History range: last 24 hours by default, with 7-day and all-time toggles. All historical traces remain reachable.
- CPU scope: whole-device CPU % from /proc/stat, app-process CPU %, and per-core current frequency.
- GPU: skipped. Display an explicit 'GPU: no public API available' line; show the active backend instead.
- Export: metrics fields are added to the existing JSONL and Markdown trace export. The Stats tab is read-only; Settings keeps export and purge.
- Benchmark runner: passive telemetry only. The existing ModelBenchmarker stays an instrumentation test; no benchmark UI in v1.

## Open Risks

- OEM/Android 16 restrictions may block /proc/stat, per-core cpufreq sysfs, thermal status or battery reads. Treat every source as best-effort and render 'unavailable' instead of a wrong value.
- CPU % needs two samples; the first reading has no delta baseline and must be suppressed until a second sample exists.
- PSS/native-heap reads cost a few milliseconds and allocate; 1s sampling must stay off the main thread and must stop when the screen is not visible.
- LiteRT BenchmarkInfo is only available after sendMessage returns. During inference the live block can show RAM/CPU/thermal but not token metrics; true streaming token metrics would need a streaming API and are out of scope.
- A 1s global sampler may miss short memory peaks. 'Peak memory during request' needs a tighter sampler around the inference call or must be documented as an approximate sample.
- Adding a trace_metrics table requires a schema migration. The existing SQLCipher database must not be dropped or recreated on upgrade.
- Hiding the Stats tab when BuildConfig.STATS_ENABLED is false shifts tab indices; navigation must use stable route ids, not raw integers.
- Metrics reveal usage patterns (request timing, app load, failure rates). Keep them on-device and inside the existing encrypted trace DB; do not add cloud sync.
- GPU load is unavailable through public Android APIs on this MediaTek device; vendor sysfs is unreliable across OxygenOS updates.
- Trace export size grows because every trace gains metrics fields; the existing forever-retention policy still applies.

## Next Decision Needed

None for design. Ready for `/opsx-propose stats-for-nerds`; build order should be telemetry core, live block, per-request persistence, then Stats tab and export fields.
