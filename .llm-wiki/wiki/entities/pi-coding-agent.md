---
type: entity
title: Pi coding agent
category: tool
created: 2026-11-07
updated: 2026-11-07
---

# Pi coding agent

The agent harness these notes are written from. Its relevant traits for DAT work:

- Reads `AGENTS.md` from the project root as persistent instructions — so the DAT repo's
  universal `agents` install works, with no plugin system needed.
- Reads project `.mcp.json` for MCP servers, so the
  [Wearables MCP server](/entities/wearables-mcp-server.md) is picked up per-project.
- Exposes MCP tools with a server prefix: `search_dat_docs` appears as
  `mcp_search_dat_docs`.
- Terminal is a PTY-backed plugin, which is how a `node-pty` packaging gap can take out all
  shell access — see [Gradle and JDK bootstrap](/concepts/gradle-and-jdk-bootstrap-without-a-system-toolchain.md).

Because Pi is not one of the plugin clients (Claude Code, Codex, Muse Code), the DAT setup
path is: repo `AGENTS.md` for guidance + `.mcp.json` for docs search.
