---
type: concept
title: Accessibility-driven Android agent safety model
created: 2026-11-10
updated: 2026-11-10
---

# Accessibility-driven Android agent safety model

The LocalGlasses agent plans and executes UI steps through a broad `AccessibilityService`, records what it sees, and keeps that record on the phone indefinitely. That combination is powerful and dangerous. This page is the rule set that makes it defensible.

## Threat model

An Accessibility Service can read every visible node in every app. With screen-text tracing and failure screenshots, the app accumulates OTPs, private messages, contact details, and anything else rendered on screen. The realistic failures are:

- a wrong tap that sends a message, places a call, or changes a setting;
- a plan that wanders into a banking or password screen;
- a trace store that becomes a plaintext history of the user's life;
- a notification listener that quietly captures OTPs;
- an exported trace file that leaves the sandbox.

The model is 2B parameters. It is not a safety boundary. The runner and the platform are.

## Absolute hard blocks

Enforced twice: before planning (against the foreground package and the requested task) and before each step (against the target node and the foreground package). There is no approval path around them.

| Category | Examples |
|---|---|
| Banking / payments | banking apps, UPI, wallets, payment sheets |
| Credentials | password fields, password managers, autofill prompts |
| OTP | reading or entering one-time codes, SMS OTP notifications |
| Account security | changing passwords, 2FA, security settings |
| App lifecycle | installing or uninstalling packages |
| Destructive data | deleting files, chats, photos |

Implementation notes:

- Match on package name **and** node attributes (`isPassword`, input type, known OTP regex).
- Deny on ambiguity: if the package is unknown and the screen looks like a payment or credential screen, abort.
- `FLAG_SECURE` screens cannot be screenshotted; the overlay also cannot be shown over them. Abort rather than guess.
- A hard block is a failure with a user-visible reason, not a silent skip.

## Approval gate

Every action opens an editable plan and waits for approval — including media and system actions. This is deliberate friction.

- The plan is an ordered step list: `tap`, `type`, `scroll`, `wait`, `open app`, plus the resolved arguments.
- The user can approve, edit a step, reorder, delete, cancel, or send a natural-language correction that triggers a re-plan.
- Editing a plan to introduce a risky step re-arms approval.
- Approval is the confirmation. There is no second dialog for calls or messages.
- The plan preview and the stop button are drawn in a `SYSTEM_ALERT_WINDOW` overlay, because the target app is in front.

## Loop guards

No step-count ceiling. Progress-based stops instead:

1. **Repeated screen signature.** Hash the normalised view-tree summary. The same signature twice with no state change stops the task.
2. **Three consecutive failed steps.** A step that cannot find its target, or whose post-condition does not hold, counts as failed.
3. **Hard-block target.** Immediate stop.
4. **User stop.** The overlay button is always available.

Each step also has a post-condition check; "tap succeeded" is not "state changed".

## Trace store

What is recorded:

- command text, resolved task, app/package, step selectors, outcome, timings;
- model prompt and tool-call JSON, redacted;
- accessibility text of screens touched;
- a screenshot **on failure only**, capped.

What is not recorded:

- notification title/body (metadata only: package, timestamp, channel, grouping hash);
- password-field content;
- hard-blocked packages;
- anything from a `FLAG_SECURE` screen.

Protection:

- Keystore-backed encrypted database.
- Redaction **before write**: password nodes dropped, OTP-like and token-like strings masked (`\b\d{4,8}\b` near OTP keywords, `Bearer …`, API-key shapes), package denylist applied.
- Retention: keep until manual purge (user choice). Export is manual JSONL/Markdown to Downloads. No cloud sync.
- A size guard and purge nudge are still required because retention is unbounded.

Encryption is not redaction. A stolen unlocked phone can decrypt the app's own database; redaction is what limits what is there to decrypt.

## Permissions in play

- `BIND_ACCESSIBILITY_SERVICE` (user enables in Settings)
- `SYSTEM_ALERT_WINDOW`
- `WRITE_SETTINGS`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `READ_CONTACTS`, `CALL_PHONE`, `SEND_SMS`
- `BIND_NOTIFICATION_LISTENER_SERVICE` (metadata only)
- `PACKAGE_USAGE_STATS`
- foreground service + persistent notification

Sideload only. Play policy would reject most of this, which is why distribution is a personal build.

## Platform limits that shape the design

- **Background activity starts.** `ACTION_CALL`, `ACTION_VIEW`, and other activity launches from an overlay or service can be blocked on Android 10+. Keep the agent activity in the foreground, or use a full-screen notification.
- **Assistant role.** An `ASSIST` activity set as default assistant replaces Google Assistant; "Hey Google" likely stops. There is no `VoiceInteractionService` in v1.
- **OEM drift.** OxygenOS Settings keys and assistant reassignment differ from AOSP; verify on the device, not in docs.
- **Secure screens.** `FLAG_SECURE` blocks both screenshots and the overlay.

## Related

- [LocalGlasses on-device agent architecture](/syntheses/localglasses-on-device-agent-architecture.md)
- [Gemma 4 E2B on LiteRT-LM for Android](/concepts/gemma-4-e2b-on-litert-lm-for-android.md)
- [DAT real-hardware gotchas](/concepts/dat-real-hardware-gotchas.md)
