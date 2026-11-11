---
type: source
title: Raw exception in status; early return stuck isSubmitting
status: insight
category: bugfix
created: 2026-11-11
updated: 2026-11-11
slug: raw-exception-status-and-stuck-submitting-flag
---

# Raw exception in status; early return stuck isSubmitting

A failed command showed two defects at once; both are general Android/LLM-app lessons.

**1. Never put `Throwable.message` in user-facing status.** LiteRT's tool-call parser threw:

```
Failed to call nativeSendMessage: INVALID_ARGUMENT: Failed to parse tool calls from code block:
... FallThrough(ParseCancelledError(InputMismatchError { offending_token: GenericToken {...},
states_stack: [42, 49, 43, ...] }))
```

That filled the whole command screen. Fix: `StatusText.shorten()` — collapse whitespace, cap at 180 chars with an ellipsis — plus a human message ("Could not build a plan. Try rephrasing") while the raw detail goes to the trace. The trace is for debugging; the status bar is for the user.

**2. An early `return@launch` skipped the flag reset.** `submit()` set `isSubmitting = true`, then a blocked-accessibility path did `return@launch` before the trailing `_uiState.update { isSubmitting = false }`, leaving Run and Dictate permanently disabled until restart. Wrap the whole body in `try/finally` — with coroutines, every early return is a missed cleanup unless it is in `finally`.

**Root cause of the parse failure:** the model emits literal `<"|>` where `"` belongs. One corrective retry that restates "exactly one `submit_plan` tool call, plain JSON, no prose" recovers it; a second failure gets a plain message.

**Test-harness trap:** five pre-existing tests in `AgentViewModelStateTest` fail because they `advanceUntilIdle()` while `CommandRouter` does real work on `Dispatchers.Default`. Virtual time does not advance for real-thread resumption. Wait on state instead: `withTimeout(15_000) { uiState.first { !it.isSubmitting } }`.

Related: [[concepts/localglasses-dictation]]

*Category: bugfix*

---
*Captured: 2026-11-11*

## Related

_Add links to related pages._

## Related

- [localglasses app](/entities/localglasses-app.md)
- [localglasses stats sections](/concepts/localglasses-stats-sections.md)
