---
domain: engineering
type: concept
title: LocalGlasses stats sections
category: architecture
created: 2026-11-11
updated: 2026-11-11
---

# LocalGlasses stats sections

The **Stats for nerds** block groups numbers by what produced them, so an app figure can never be
misread as a model figure. Added during the `speech-to-text` change.

## Sections, in order

| Section | Contents |
|---|---|
| LLM Inference Metrics | model, backend, engine loaded, engine init, latency, TTFT, prompt/completion tokens, prefill, decode |
| Speech Recognition | speech model, audio source, audio ms, recognition ms, real-time factor, endpoint, words, peak PSS |
| App | app PSS, app CPU, peak app memory, threads, Java heap, native heap |
| Device Live | memory used/available, device CPU, thermal, battery, free storage |
| Device and Model | device model, SoC, ABIs, Android, memory class, model id, model file, backend, engine loaded |

## Rules

- **Speech Recognition is always rendered**, like the inference section. A missing value reads as
  `Unavailable`; the section never disappears (decided after the first version hid it for typed
  commands).
- Speech numbers appear as soon as a dictation finishes recognition, **before** the command is
  submitted: `AgentViewModel.lastDictationMetrics` publishes them, and the block prefers the
  in-flight dictation while a request runs.
- The stats flag (`BuildConfig.STATS_ENABLED`) hides every stats surface, dictation included
  remaining functional.

## Where it lives

- `ui/stats/RequestMetricsBlock.kt` — `RequestMetricsContent` with `MetricsSection`, plus
  `SpeechMetricsContent` reused by the Stats page rows.
- `ui/stats/StatsForNerdsBlock.kt` — collapsible card; owns the **Device and Model** section.
- `ui/stats/StatsAggregator.kt` — summary also aggregates speech: `dictations`,
  `averageRecognitionMs`, `averageRealTimeFactor`, `averageWords`, decoded from each row's
  `snapshotJson` because speech is not denormalised into columns.
- `agent/telemetry/MetricsModels.kt` — `SpeechMetrics` nested in `RequestMetrics`.

## Tests

JVM: `StatsAggregatorTest` (5, incl. speech aggregates), `PendingSpeechMetricsTest`,
`SpeechMetricsJsonTest`.
On device: `StatsSectionsTest`, `StatsBlocksTest`, `SpeechMetricsPresentationTest`,
`LiveSpeechMetricsTest`, `SpeechMetricsDisabledTest`.

Related: [localglasses dictation](/concepts/localglasses-dictation.md), [localglasses app](/entities/localglasses-app.md)
