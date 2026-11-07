---
type: source
title: "Observation: Workflow set to Grill Me + OpenSpec; cosmetic extensions removed"
tags:
  - openspec
  - grill-me
  - workflow
  - pi-extensions
  - tooling
status: observation
created: 2026-11-07
updated: 2026-11-07
slug: obs-2026-11-07-workflow-set-to-grill-me-openspec-cosmetic-extensions-remove
relevance: high
observed_at: 2026-11-07T15:45:26.574Z
source_context: Setting up Grill Me + OpenSpec workflow and trimming extensions
---

# ⭐ Observation: Workflow set to Grill Me + OpenSpec; cosmetic extensions removed

LocalGlasses workflow tooling settled: Grill Me (phase 1 interview) + OpenSpec (phase 2 spec/tasks). OpenSpec CLI v1.16.1 installed user-local at ~/.local/bin/openspec via the official curl installer (npm global prefix /usr/lib/node_modules is not writable, so npm -g was avoided). `openspec init --tools pi --profile core --no-animation --force` created openspec/config.yaml plus .pi/skills/openspec-{propose,explore,apply,update,sync,archive-change}/SKILL.md and .pi/prompts/opsx-{propose,explore,apply,update,sync,archive}.md. Pi officially supports OpenSpec (docs/supported-tools.md row: Pi -> .pi/skills/openspec-*/SKILL.md + .pi/prompts/opsx-<id>.md), and Pi's configuration.md confirms .pi/skills and .pi/prompts are the project resource dirs, loaded only after project trust. Telemetry disabled via `openspec config set telemetry.enabled false`. Repo policy: openspec/ is committed (spec history), .pi/skills/openspec-* and .pi/prompts/opsx-* are gitignored as generated (regenerate with `openspec update`). Removed three cosmetic Pi extensions: pi-tui-status-beautifier, pi-pigment, pi-markdown-preview. Kept pi-agent-ide (all file/edit/search/terminal tools), pi-web-access, pi-mcp-adapter (search_dat_docs), @zosmaai/pi-llm-wiki, @firstpick/pi-extension-grill-me, pi-caveman (active output style) and pi-cache-optimizer (prompt-cache cost). Note: only 4 skills existed across all packages, two of them shipped with pi-agent-ide, so there were no redundant workflow skills to remove — superpowers was never installed.

*Relevance: high*
*Context: Setting up Grill Me + OpenSpec workflow and trimming extensions*
*Tags: openspec grill-me workflow pi-extensions tooling*

---
*Observed: 2026-11-07T15:45:26.574Z*
