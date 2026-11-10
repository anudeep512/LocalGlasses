---
type: source
title: "Observation: LocalGlasses agent scope and safety model decided"
tags:
  - localglasses
  - agent
  - gemma-4
  - litert
  - accessibility
  - grill-me
status: observation
created: 2026-11-10
updated: 2026-11-10
slug: obs-2026-11-10-localglasses-agent-scope-and-safety-model-decided
relevance: high
observed_at: 2026-11-10T18:37:45.171Z
source_context: Extending LocalGlasses from glasses camera app to on-device phone agent
---

# ⭐ Observation: LocalGlasses agent scope and safety model decided

Grill Me completed on 2026-11-11 for the LocalGlasses agent extension. Fixed decisions: phone-only agent; typed input; agent home + camera tab; Gemma 4 E2B via LiteRT-LM (2583 MB, CPU/GPU only); in-process @Tool registry with constrained decoding; one task per utterance; every action gets an editable plan and approval; broad Accessibility allowed for generic tasks with six absolute hard blocks; encrypted trace store kept until manual purge; record-only v1 for later workflow distillation; default-assistant ASSIST activity; in-app model download; warm foreground service with memory-pressure unload. Device is CPH2717/MT6897, 7.65 GB RAM, 2.66 GB free at idle — not Snapdragon. Full log: GRILL-ME.md. Wiki: synthesis/localglasses-on-device-agent-architecture plus two concept pages.

*Relevance: high*
*Context: Extending LocalGlasses from glasses camera app to on-device phone agent*
*Tags: localglasses agent gemma-4 litert accessibility grill-me*

---
*Observed: 2026-11-10T18:37:45.171Z*
