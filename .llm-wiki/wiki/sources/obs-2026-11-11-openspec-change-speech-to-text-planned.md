---
type: source
title: "Observation: OpenSpec change speech-to-text planned"
tags:
  - openspec
  - planning
  - stt
  - localglasses
status: observation
created: 2026-11-11
updated: 2026-11-11
slug: obs-2026-11-11-openspec-change-speech-to-text-planned
relevance: medium
observed_at: 2026-11-11T18:49:03.453Z
source_context: Planning the speech-to-text change for LocalGlasses
---

# 🔍 Observation: OpenSpec change speech-to-text planned

Created `openspec/changes/speech-to-text` with proposal.md, specs (new `agent/dictation`, deltas for `agent/telemetry` and `agent/stats-view`), design.md, and tasks.md (54 lines, 8 task groups). Validates with `openspec validate --strict`.

Scope: on-device English dictation via sherpa-onnx (JitPack) + Whisper small.en int8, Silero VAD endpointing (800 ms / 15 s cap), phone mic and DAT camera PCM glasses source behind a shared AudioSource, transcript replaces the command field with no auto-submit, speech metrics in the existing stats surfaces.

Planning only — no project code edited.

*Relevance: medium*
*Context: Planning the speech-to-text change for LocalGlasses*
*Tags: openspec planning stt localglasses*

---
*Observed: 2026-11-11T18:49:03.453Z*
