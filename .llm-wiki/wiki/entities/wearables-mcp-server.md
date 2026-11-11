---
type: entity
title: Wearables MCP server
category: tool
created: 2026-11-07
updated: 2026-11-07
---

# Wearables MCP server

Meta's public documentation-search MCP endpoint at
`https://mcp.developer.meta.com/wearables`. Streamable HTTP, **no authentication**.

## Tools

- `search_dat_docs` — Device Access Toolkit guides, API reference, code examples.
- `search_webapps_docs` — Display Web Apps docs.

Both return up to 5 ranked doc sections by default; `max_results` raises that to 10.

## Why it matters

It keeps the assistant grounded in *currently published* DAT docs while implementing —
complementing the static `AGENTS.md` guidance. The docs recommend calling
`search_dat_docs` before choosing SDK APIs, and asking for Markdown output.

## Gotchas

- The Wearables developer *site* URL is not an MCP endpoint.
- No token is required. If a client reports an auth error, it has a stale
  `Authorization` header — remove it and reconnect.
- Some clients rename the tool with a server prefix (Pi exposes it as
  `mcp_search_dat_docs`).

Registered for this project in `.mcp.json`. Detail in
[SRC-2026-11-07-002](/sources/SRC-2026-11-07-002.md).

## Related

- [meta wearables device access toolkit android sdk](/entities/meta-wearables-device-access-toolkit-android-sdk.md)
- [localglasses app](/entities/localglasses-app.md)
