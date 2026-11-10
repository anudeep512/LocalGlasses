---
type: source
title: Hard blocks before trace capture
status: insight
category: security
created: 2026-11-10
updated: 2026-11-10
slug: accessibility-agent-hard-blocks-before-trace
---

# Hard blocks before trace capture

When an Android agent gets a broad Accessibility Service plus screen-text tracing, decide the hard blocks before writing the recorder. Retrofitting redaction after traces exist means the sensitive data is already on disk.

Defaults that worked for [[syntheses/localglasses-on-device-agent-architecture]]:
- Never automate banking/UPI/wallet apps, password fields or managers, OTP read/entry, account-security changes, app install/uninstall, or deletion. Enforce before planning and again per step, against both foreground package and target node.
- Approve every action through an editable plan; the plan is the confirmation. The model is 2B and is not a safety boundary.
- Notification listener stores metadata only (package, time, channel), never title/body, or it silently captures OTPs.
- Trace: Keystore-backed encrypted DB, redaction before write, package denylist, failure-only screenshots, manual purge and local export.
- Loop guards are progress-based: repeated screen signature, three failed steps, hard-block target, user stop. A step ceiling is optional; no-progress detection is not.

Full rule set: [[concepts/accessibility-driven-android-agent-safety-model]].


*Category: security*

---
*Captured: 2026-11-10*

## Related

_Add links to related pages._
