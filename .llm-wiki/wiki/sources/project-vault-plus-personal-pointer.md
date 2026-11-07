---
type: source
title: Project vault with a personal pointer page
status: insight
category: architecture
created: 2026-11-07
updated: 2026-11-07
slug: project-vault-plus-personal-pointer
---

# Project vault with a personal pointer page

Two-vault pattern that keeps project detail out of the global wiki without losing recall.

**Project vault** (`.llm-wiki/` in the repo) holds the deep, project-specific material: source packets, syntheses, session-lifecycle notes, build-chain reasoning. It travels with the repo and is scoped to that codebase.

**Personal vault** (`~/.llm-wiki/`) holds one pointer page per project plus the genuinely portable facts — the ones you would want recalled while working on a *different* repo. For LocalGlasses that meant: "no system JDK, use `/opt/android-studio/jbr`", "AGP 9 forbids `org.jetbrains.kotlin.android`", "MockDeviceKit needs powerOn→unfold→don".

**Why not one vault:** project pages would flood global recall with context that is irrelevant outside that repo, and the project vault would lose its tight scope.

**Link caveat:** cross-vault relative links do not resolve. Either duplicate the portable page into the personal vault (short version, pointing back at the project vault path) or use plain code paths. Duplicating the *portable* subset is worth it — it is what makes global recall useful.

**Verification step worth keeping:** after writing pages, grep every `](/path.md)` target and check the file exists. `wiki_ensure_page` slugifies titles, so a hand-written link like `/entities/dat-android-sdk.md` silently breaks when the page landed at `/entities/meta-wearables-device-access-toolkit-android-sdk.md`. Four links broke this way in one session.

Related: [[syntheses/localglasses-android-setup]]

*Category: architecture*

---
*Captured: 2026-11-07*

## Related

_Add links to related pages._
