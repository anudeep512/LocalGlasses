---
type: source
title: "Observation: ASD-STE100 skill installed to global Pi skills"
tags:
  - pi
  - skills
  - asd-ste100
  - ste100
  - writing
status: observation
created: 2026-11-07
updated: 2026-11-07
slug: obs-2026-11-07-asd-ste100-skill-installed-to-global-pi-skills
relevance: medium
observed_at: 2026-11-07T15:54:58.548Z
source_context: Installing ASD-STE100 skill globally
---

# 🔍 Observation: ASD-STE100 skill installed to global Pi skills

Installed the ASD-STE100 skill globally for Pi at ~/.pi/agent/skills/asd-ste100 (git clone --depth 1 of github.com/danyuchn/asd-ste100-skill, default branch master, MIT, 428K). Pi's user skills dir is <agent-dir>/skills/ per configuration.md, and the skill's frontmatter is valid for Pi: name asd-ste100 matches the directory, description 467 chars (limit 1024). Contents: SKILL.md, references/writing-rules.md, examples/before-after.md, examples/linter-edge-cases.md, scripts/ste-lint.py (2103 lines, stdlib only — argparse/bisect/contextlib/html.entities/io/json/os/re/sys/tempfile, no network, no subprocess, no eval). Verified the linter works: a dense sample produced 10 violations (long-sentence, present-perfect, passive-voice, semicolon, phrasal-verb) and the skill's own SKILL.md lints at 44 violations with --baseline 36. Two modes: strict (procedures, error messages, tool descriptions) and STE-flavored (READMEs, PR descriptions, prose). It does not reproduce ASD's official ~900-word dictionary (redistribution restricted), so dictionary-dependent rules are advisory. Note the direct conflict with pi-caveman: caveman compresses output, STE expands for unambiguity; caveman only affects chat replies while STE applies to text explicitly pointed at it.

*Relevance: medium*
*Context: Installing ASD-STE100 skill globally*
*Tags: pi skills asd-ste100 ste100 writing*

---
*Observed: 2026-11-07T15:54:58.548Z*
