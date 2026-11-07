---
type: concept
title: DAT AI-assisted setup
domain: engineering
created: 2026-11-07
updated: 2026-11-07
---

# DAT AI-assisted setup

There are three independent layers of AI assistance for DAT, and they are worth keeping
distinct because each fails differently:

| Layer | Delivery | What it gives | Failure mode |
|---|---|---|---|
| Coding guidance | GitHub repo plugin **or** `AGENTS.md` | patterns, setup flow, MDK test recipes | plugin invisible until session restart |
| Docs search | Wearables MCP server | current published guides | stale `Authorization` header, wrong URL |
| API reference | `llms.txt` | exact signatures | only useful once you know the symbol |

## Installing guidance

`install-skills.sh` in the DAT Android repo takes a client argument:

```bash
./install-skills.sh muse|claude|codex|copilot|cursor|agents|all
```

- `muse`, `claude`, `codex` → registers plugin marketplace + installs `mwdat-android`
- `copilot` → copies `.github/`
- `cursor` → copies `.cursor/rules/*.mdc`
- `agents` → copies `AGENTS.md` to repo root

Anything not on that list falls back to `agents`. That is the only workable option for clients
without a plugin system — Pi reads `AGENTS.md` at the project root, so `agents` is correct.

## A live-session caveat

A plugin installed *during* a running agent session generally will not load until restart. The
docs' own advice is to read the repo `AGENTS.md` instead of reinstalling into the live session.

## MCP

Claude Code / Codex / Muse Code wire the MCP server automatically. Everything else adds
`https://mcp.developer.meta.com/wearables` manually — see
[Wearables MCP server](/entities/wearables-mcp-server.md) and
[SRC-2026-11-07-002](/sources/SRC-2026-11-07-002.md).
