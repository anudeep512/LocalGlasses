---
type: source
title: "Observation: Conditional silence endpoint contradicted the spec"
tags:
  - dictation
  - vad
  - bug
  - testing
status: observation
created: 2026-11-11
updated: 2026-11-11
slug: obs-2026-11-11-conditional-silence-endpoint-contradicted-the-spec
relevance: medium
observed_at: 2026-11-11T19:34:03.350Z
source_context: Implementing speech-to-text dictation in LocalGlasses
---

# 🔍 Observation: Conditional silence endpoint contradicted the spec

Manual UI testing on the CPH2717 caught a bug that 13 green controller tests missed: the silence endpointer only ended an utterance *after* speech had been detected, so an accidental Dictate tap held the microphone open for the full 15 s cap. `openspec/specs/agent/dictation` says capture ends after ~800 ms of detected silence unconditionally and a silent capture is discarded — the code had added a `hasSpeech &&` guard.

Fix: `Endpointer` returns `SilenceEnd` whenever trailing silence reaches the threshold; the controller's existing `shouldDiscard()` still turns a speechless capture into a `NoSpeech` outcome with no transcript. Re-verified on device: tap → "No speech heard", command field untouched, mic released.

Lesson: a state machine can satisfy every unit test and still contradict the spec sentence it implements — read the requirement at the same time as the wiring, and drive the real UI once.

*Relevance: medium*
*Context: Implementing speech-to-text dictation in LocalGlasses*
*Tags: dictation vad bug testing*

---
*Observed: 2026-11-11T19:34:03.350Z*

## Related

- [localglasses dictation](/concepts/localglasses-dictation.md)
- [localglasses app](/entities/localglasses-app.md)
