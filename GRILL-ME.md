# Grill Me Results

Generated: 2026-11-10T18:36:29.023Z

## Plan

I want to extend this project where my vision is to extend metaglasses to be able to be like my one stop for all the things on my phone. For example I should come to this app and then on typing "call Amma" it should be to call the contact with the name Amma, and for non andriod packaged apps like for example if we consider spotify in such a case if I type "Play any song by kanye west" in that case my agent should be able to do that for me with a predefined workflow everything should be predefined nothign should be thought on the flow by the agent and the decision is taken in place. And another thing I want to achieve is the LLM should be running locally on my phone which is already connected to the PC I want to use google gemma 4 for this task and I leave it upto for choosing the specific model of gemma 4. So the idea is simple this app should be the one stop app for all the things that I can do on my andriod device hand the interactions should be predefined and the ai should just decide what is the action that needs to be performed and the global runner executes that. This is what we want to achieve now ask me all the questions that you need help with.
the following is my previous discussion with another agent now I want to discuss with you more on this take this as a reference about the idea im trying to ship:

Here is a comprehensive, highly detailed summary of our entire discussion, structured in logical sections. You can feed this directly into another AI to instantly bring it up to speed on your project architecture, hardware constraints, and technical decisions.

---

### **Project Overview & Hardware Context**

**Goal:** Build a fully offline, on-device AI agent for Android that can navigate the OS, send messages, play media, and interact with app UIs.
**Target Device:** OnePlus Nord CE5 Lite (Snapdragon 7 Gen 2, 8GB RAM).

### **1. Model Selection & Performance**

* **Chosen Model:** **Gemma 4 E2B (2 Billion Parameters)**. This requires ~6GB of RAM, leaving enough headroom for the Android OS on an 8GB device. The E4B model was rejected as it would push the memory to the absolute limit and cause OS crashes.
* **Storage Footprint:** Using 4-bit quantization (Q4), the E2B model is ~3GB. Using Google's newer 2-bit mobile quantization (QAT), it can be reduced to under 1GB.
* **Expected Inference:** 12 to 20 tokens per second (fast enough for real-time mobile tasks).
* **Capabilities:** Multimodal (text/image/audio out of the box).
* **Bottlenecks:** Context length must be kept small (a few thousand tokens) to avoid out-of-memory errors. Sustained use will cause thermal throttling.

### **2. Original Architectural Approach: Google A2A Protocol**

The initial idea was to use Google's Agent-to-Agent (A2A) protocol to spawn separate agents for different apps (Phone, Messages, Spotify).

* **Memory Constraint Resolved:** A phone cannot run multiple LLMs. To solve this, the architecture was split into an **Orchestrator** and **Remote Servers**.
* **The Orchestrator (A2A Client):** A single app running the Gemma 4 LLM. It interprets user intent and routes JSON commands.
* **The Remote Agents (A2A Servers):** Deterministic, LLM-free endpoints embedded in target apps (or running locally). They host an HTTP server (e.g., Ktor or NanoHTTPD) on `localhost` (e.g., ports 8081, 8082).
* **A2A Mechanics:**
* **Agent Card:** Servers host a `/.well-known/agent.json` file exposing their skills.
* **Communication:** Orchestrator sends a standard **JSON-RPC 2.0** payload (specifying `method: tasks/create`, `skill`, and `inputs`) via HTTP POST.
* **Persistence:** Servers run as Android **Foreground Services** to prevent Doze mode from killing them.



### **3. Execution Mechanisms (How the Agent controls the phone)**

The discussion broke down how different intents are executed natively on Android without relying on cloud APIs:

* **Background Services (SMS):** The Orchestrator sends a JSON payload with `recipient_number` and `message_text`. The SMS server parses it and triggers Android's native `SmsManager`.
* **Deep Linking / Intents (Spotify, YouTube Search):** To play media (e.g., "Play Believer on Spotify") without complex SDKs or authentication, the agent fires Android's `ACTION_MEDIA_PLAY_FROM_SEARCH` intent targeted at `com.spotify.music`. For general searches, it targets `Intent.ACTION_SEARCH`.
* **Complex UI Control (Clicking, Scrolling):** Standard intents cannot press buttons inside an app. To achieve actions like "Like this video" or "Scroll down," the agent must use an **Android Accessibility Service**.
* It reads the active View tree via `AccessibilityNodeInfo`.
* It finds target elements using `findAccessibilityNodeInfosByText()`.
* It executes clicks (`ACTION_CLICK`), node-based scrolling (`ACTION_SCROLL_FORWARD`), or raw gesture injection (`dispatchGesture()`).
* *Limitations:* Cannot read secure screens (apps with `FLAG_SECURE` like banking apps or Netflix), and requires manual user permission in settings.



### **4. System Optimization & Latency Mitigation**

To make a 2B parameter model fast and accurate enough for UI navigation:

* **Single-App Deployment:** Instead of separate standalone apps, all A2A HTTP servers and the Orchestrator should run inside a single Monolithic APK. This centralizes permissions and battery management.
* **Constrained Generation:** The Gemma 4 model must be forced to output *only* valid JSON using Grammar Constraints (GBNF). No conversational filler (e.g., "Sure, I'll do that").
* **Stream Execution:** Do not wait for the LLM to finish generating the full JSON payload. Parse the token stream in real-time. As soon as the `skill` and required `inputs` are generated, fire the Android Intent instantly.

### **5. Final Conclusion: A2A vs. MCP**

The discussion concluded by evaluating A2A against the **Model Context Protocol (MCP)**.

* **Verdict:** **MCP is superior for this specific use case.**
* **Reasoning:** A2A relies on HTTP layers and Server-Sent Events (SSE), which introduces network-stack overhead and latency, even on `localhost`.
* **Final Proposal:** Build a **Spec-Driven Local MCP Pipeline**. Instead of spinning up internal web servers, define tools (SMS, Accessibility, Media Intents) as JSON schemas in a local registry. Inject these directly into the Gemma 4 system prompt. The LLM acts as the MCP client, generating tool calls that are directly bound to native Android execution loops via standard input/output (stdio). This eliminates HTTP latency, creating a high-throughput, near-instant execution pipeline.

and capture all this in the LLM wiki as required

## Shared Understanding

LocalGlasses becomes a phone-only, fully on-device agent. Typed (or assist-invoked) command -> Gemma 4 E2B either selects one deterministic registry action or plans an Accessibility UI task -> the plan is shown as an editable step list -> you approve -> the runner executes with overlay progress and a stop button. Every interaction is recorded on-device so v2 can distil deterministic workflows. Glasses stay camera-only in v1.

## Questions and Answers

### 1. Which Gemma 4 variant should the app target on this phone?

**Recommended answer:** Gemma 4 E2B — only mobile size that leaves OS headroom on 7.65 GB

**User answer:** Gemma 4 E2B. User explicitly delegated the size choice; E2B chosen because it is the only Gemma 4 mobile variant that fits 7.65 GB RAM with OS headroom.

**Status:** resolved

**Notes:** Model card: 2583 MB .litertlm, 0.8 GB 2/4/8-bit weights + 1.12 GB mmapped embeddings, 32k context. E4B rejected.

### 2. Does this phone expose an NPU backend for LiteRT-LM?

**Recommended answer:** No — CPU/GPU only on MediaTek MT6897

**User answer:** No. Device is CPH2717 / MT6897 (Dimensity 7300), 7.65 GB RAM, Android 16. LiteRT-LM NPU backends are Qualcomm QNN and Google Tensor only, so the app runs CPU XNNPACK or Mali GPU.

**Status:** resolved

**Notes:** Benchmarks must be run on-device; S26 Ultra numbers in the model card do not transfer.

### 3. Does the repo already contain agent, LLM, or tool-registry code?

**Recommended answer:** No — greenfield feature inside the existing app

**User answer:** No. Current app is camera-only: DAT init, session lifecycle, preview, photo capture, video recording. No text input, no tool registry, no LLM dependency, no accessibility service.

**Status:** resolved

**Notes:** Agent layer is new code under com.localglasses.app.agent (or similar), plus a new home screen.

### 4. What must the glasses hardware do in v1, beyond the camera capture the app already has? Recommended: Phone-only agent, glasses stay camera-only

**Recommended answer:** Phone-only agent; glasses stay camera-only

**User answer:** Phone-only agent; glasses stay camera-only

**Status:** resolved

**Notes:** No DAT Speech, no vision context, no audio routing in v1.

### 5. How do you trigger a command in v1? Recommended: Typed text in the app

**Recommended answer:** Typed text field in the app

**User answer:** Typed text field in the app

**Status:** resolved

**Notes:** No speech-to-text in v1; deterministic input for testing.

### 6. Should the agent become the app's home screen? Recommended: Agent screen is home; glasses camera becomes a tab

**Recommended answer:** Agent screen is home; glasses camera becomes a tab

**User answer:** Agent screen is home; glasses camera becomes a tab

**Status:** resolved

**Notes:** Navigation: agent home + camera tab. Existing camera code stays intact.

### 7. Which command families must work in v1? Recommended: Call contact, Send SMS, Spotify play/search, Set alarm/timer

**Recommended answer:** Call contact, Send SMS, Spotify play/search, Set alarm/timer

**User answer:** Call a contact by name, Send an SMS by contact name, WhatsApp / Telegram message, YouTube or YouTube Music playback. Other: I should be access all the settings from my phone for example if I say "change the sleep time to 60s" my agent should be able to do that

**Status:** resolved

**Notes:** Five families: call, SMS, chat apps, YouTube/YT Music, system settings. Spotify and alarm/timer not selected. Settings access needs its own scope decision.

### 8. You said 'predefined workflow, nothing thought on the fly'. Which definition do you mean? Recommended: One tool call per utterance; no chaining

**Recommended answer:** One tool call per utterance; no chaining

**User answer:** One tool call per utterance; no chaining

**Status:** resolved

**Notes:** Model selects one registered action and fills typed slots; runner validates and executes.

### 9. How should tools be exposed to the model? Recommended: In-process Kotlin registry using LiteRT-LM @Tool/ToolSet with constrained decoding

**Recommended answer:** In-process LiteRT-LM @Tool registry + constrained decoding

**User answer:** In-process LiteRT-LM @Tool registry + constrained decoding

**Status:** resolved

**Notes:** No MCP/HTTP/SSE layer. LiteRT-LM generates schemas and constrains output to valid tool JSON.

### 10. When must the runner stop and ask before acting? Recommended: Confirm calls, messages and anything irreversible; auto-run media and system actions

**Recommended answer:** Confirm calls/messages/irreversible; auto-run media and system

**User answer:** Confirm calls/messages/irreversible; auto-run media and system

**Status:** resolved

**Notes:** Confirmation is a deterministic runner rule keyed on action risk class, not a model decision.

### 11. How should results and confirmations come back? Recommended: On-screen status plus phone TTS for confirmations

**Recommended answer:** On-screen status + spoken confirmation via phone TTS

**User answer:** On-screen status + spoken confirmation via phone TTS

**Status:** resolved

**Notes:** Google TTS is installed on the device; DAT offers no direct audio route to glasses.

### 12. Which languages must v1 understand? Recommended: English only

**Recommended answer:** English only

**User answer:** English only

**Status:** resolved

**Notes:** Proper-noun aliases like 'Amma' still resolve; no translation.

### 13. Is this personal sideload only, or must it eventually ship on Play Store? Recommended: Personal sideload only

**Recommended answer:** Personal sideload only

**User answer:** Personal sideload only

**Status:** resolved

**Notes:** Unlocks SEND_SMS, READ_CONTACTS, WRITE_SETTINGS, and an Accessibility Service without Play policy constraints.

### 14. How does the 2.58 GB model get onto the phone? Recommended: One-time in-app download from Hugging Face with checksum verification

**Recommended answer:** In-app one-time download + checksum

**User answer:** In-app one-time download + checksum

**Status:** resolved

**Notes:** 42 GB free on device. Download once, then fully offline.

### 15. Should the model stay loaded between commands? Recommended: Load on first use, unload after ~10 minutes in background

**Recommended answer:** Load on first use, unload after ~10 min background

**User answer:** Keep warm in a foreground service

**Status:** resolved

**Notes:** Conflicts with 7.65 GB RAM: ~1.5-2 GB resident model competes with Spotify/YouTube/WhatsApp that the agent launches. Eviction policy asked in round 2.

### 16. When no registered action matches, what should happen? Recommended: Show 'not understood' plus the 3 nearest registered commands

**Recommended answer:** Show nearest registered commands as suggestions

**User answer:** Show nearest registered commands as suggestions

**Status:** resolved

**Notes:** Never invent an action; vocabulary feedback only.

### 17. Should the agent remember earlier commands? Recommended: Stateless per command

**Recommended answer:** Stateless: each command is independent

**User answer:** Stateless: each command is independent

**Status:** resolved

**Notes:** No follow-up pronouns in v1. Confirmation is a pending-action UI state, not conversational memory.

### 18. How far should "access all the settings" go in v1? Recommended: Curated action set with direct writes plus open-any-Settings-page

**Recommended answer:** Curated actions with direct writes + open any Settings page

**User answer:** Curated actions with direct writes + open any Settings page

**Status:** resolved

**Notes:** Fixed typed actions (screen timeout, brightness, volume, DND, rotation, flashlight, open page). Needs WRITE_SETTINGS granted once; DND needs notification-policy access.

### 19. Do you want an Accessibility Service in v1 for the steps intents cannot do? Recommended: Narrow whitelisted auto-taps

**Recommended answer:** Narrow whitelisted auto-taps with prefill fallback

**User answer:** Generic UI tree navigation for any app

**Status:** resolved

**Notes:** Contradicts the earlier single-tool / predefined-only decision: broad UI navigation needs runtime tap decisions. Reconciliation asked in round 3.

### 20. Which chat app must work in v1? Recommended: WhatsApp only

**Recommended answer:** WhatsApp only in v1

**User answer:** WhatsApp only in v1

**Status:** resolved

**Notes:** Telegram deferred; different deep-link scheme and selectors.

### 21. Add Spotify alongside YouTube/YT Music? Recommended: Add Spotify as a sixth family

**Recommended answer:** Add Spotify alongside YouTube/YT Music

**User answer:** YouTube/YT Music only in v1

**Status:** resolved

**Notes:** The original 'play any song by Kanye West' example now maps to YouTube. Spotify remains installed but out of v1 scope.

### 22. How should 'call Amma' resolve a nickname to a real contact? Recommended: READ_CONTACTS + local fuzzy match + editable alias table + disambiguation sheet

**Recommended answer:** Local match + editable alias table + disambiguation sheet

**User answer:** Local match + editable alias table + disambiguation sheet

**Status:** resolved

**Notes:** No model involvement in identity resolution. Alias table persisted locally.

### 23. When should the warm model be unloaded? Recommended: Warm 10 min idle, then unload; unload on memory pressure

**Recommended answer:** Warm 10 min idle, then unload; unload on memory pressure

**User answer:** my phone has the support for extending the ram upto 16gb using storage and this has been set to on so it fine that the model is loaded onto ram and stays there and take care of the andriod battery otpimizer task

**Status:** resolved

**Notes:** Measured: 7.65 GB physical, 7.86 GB swap with ~3 GB already used at idle, 2.66 GB available before model load. RAM expansion is compressed zram, not real RAM, and quantized weights compress poorly. Correction and final policy asked in round 3.

### 24. How should the app survive Android battery optimization while staying warm?

**Recommended answer:** Foreground service + request battery-optimization exemption (sideload, so Play policy does not apply)

**User answer:** Take care of the Android battery optimizer task so the warm model service is not killed.

**Status:** resolved

**Notes:** Requires FOREGROUND_SERVICE + typed foreground service permission on Android 14+, a persistent notification, and REQUEST_IGNORE_BATTERY_OPTIMIZATIONS granted by the user.

### 25. Broad Accessibility Service and 'one tool call, nothing thought on the fly' cannot both be literally true. Which rule wins?

**Recommended answer:** Broad service + declared step lists per action; model emits one action

**User answer:** keep it generic only but show me the workflow that the agent wants to follow for achieving this task so I can validate and correct it if required. This is how want to handle generic support

**Status:** resolved

**Notes:** User accepts runtime planning for generic tasks, gated by a visible plan preview and correction. This supersedes single-tool for generic UI tasks; registry actions stay single-call.

### 26. If the model stays resident (~1.5-2 GB), what is the fallback? Recommended: stay resident, unload on critical memory callback or free RAM under ~1 GB

**Recommended answer:** Resident by default; unload on critical memory/low free RAM

**User answer:** Resident by default; unload on critical memory/low free RAM

**Status:** resolved

**Notes:** Model stays warm in a foreground service; Android TRIM_MEMORY_RUNNING_CRITICAL and a free-RAM threshold trigger unload, reload on next command.

### 27. When you want a new app supported after v1, who writes its action? Recommended: Developer-authored action definitions in the repo

**Recommended answer:** Developer-authored action definitions in the repo

**User answer:** as the first cut we want to store all the interactions the agent has done in my daily workflows and store it in the phone in the future we can take those and make a deterministic workflow out of those and restrict the agent accordingly. So in the first cut we want the agent to have all the access required and we give it full access but all these results should be stored in the phone for future reference in creating the deterministic workflows

**Status:** resolved

**Notes:** Strategy: full-access agent first, record every interaction on-device, later distill traces into deterministic workflows and restrict the agent. Requires a trace store with redaction from day one.

### 28. When must the agent show its workflow before acting? Recommended: First time only, then silent replay

**Recommended answer:** First time only, then silent replay; re-show on mismatch/failure

**User answer:** Every generic UI task, every time

**Status:** resolved

**Notes:** No silent replay in v1. Every generic task pays plan generation + approval latency; deterministic registry actions are unaffected.

### 29. How do you correct a plan you disagree with? Recommended: Approve / edit steps / reorder / cancel, plus natural-language re-plan

**Recommended answer:** Approve / edit steps / reorder / cancel, plus natural-language re-plan

**User answer:** Approve / edit steps / reorder / cancel, plus natural-language re-plan

**Status:** resolved

**Notes:** Structured step editor plus NL correction. Editing a plan to add a risky step re-arms confirmation.

### 30. What gets written to the on-device trace store? Recommended: Command, task, step selectors, outcome, timings, model/tool JSON, no screenshots

**Recommended answer:** Command, task, step selectors, outcome, timings, and model/tool JSON; no screenshots by default

**User answer:** Command, task, step selectors, outcome, timings, Model prompt and tool-call JSON (redacted), Accessibility text of every screen touched, Screenshot on failure only

**Status:** resolved

**Notes:** Screen text and failure screenshots are stored. Redaction and package denylist become mandatory before write.

### 31. Which categories must the agent never automate, even if you approve the plan? Recommended: banking/payments, passwords, OTP, account security

**Recommended answer:** Banking/UPI/payments/wallets, password entry and managers, OTP read/entry, account-security changes

**User answer:** Banking, UPI, payments, wallet apps, Password entry and password managers, Reading or entering OTPs, Changing account passwords or security settings, Installing or uninstalling apps, Deleting files, chats, or photos

**Status:** resolved

**Notes:** Six hard blocks, enforced before planning and again at each step, on both the foreground package and the target node.

### 32. Which special Android accesses should the app request? Recommended: Accessibility, overlay, WRITE_SETTINGS, battery exemption

**Recommended answer:** Accessibility, display over other apps, WRITE_SETTINGS, battery-optimization exemption

**User answer:** Accessibility Service, Display over other apps (plan preview / stop button), Modify system settings (WRITE_SETTINGS), Battery-optimization exemption, Notification listener, Usage access (foreground app history)

**Status:** resolved

**Notes:** All six granted. Notification listener can see OTP notifications, so its content policy is asked in round 5.

### 33. Once a plan succeeds, when does it become the deterministic workflow? Recommended: Record all; manual promotion

**Recommended answer:** Record all; you manually promote a candidate to fixed steps

**User answer:** for now just record all the messages and responses in future for v2 of this app I will come up with the determinsitic promotion later and give the feature of replay of the prompts so it is easier to debug

**Status:** resolved

**Notes:** v1 = record only. No automatic replay or promotion. v2 adds deterministic promotion and prompt replay for debugging.

### 34. State across separate commands? Recommended: Task-scoped state only; new command = clean slate

**Recommended answer:** Task-scoped state only; new command = clean slate

**User answer:** Task-scoped state only; new command = clean slate

**Status:** resolved

**Notes:** Within-task observation loop keeps state; no cross-command memory.

### 35. How long should interaction traces stay on the phone? Recommended: 30-day rolling with purge and no cloud

**Recommended answer:** 30-day rolling, with purge and no cloud

**User answer:** Keep everything until manually deleted

**Status:** resolved

**Notes:** Unbounded retention, on-device only. Needs a purge control and a storage-size guard, especially with failure screenshots.

### 36. Should LocalGlasses become the default assistant (long-press home), or stay a normal app icon? Recommended: Normal app icon for v1

**Recommended answer:** Normal app icon for v1

**User answer:** Make it the default assistant (long-press home)

**Status:** resolved

**Notes:** Replaces Google Assistant as the assist role; 'Hey Google' hotword likely stops. Round 5 confirms implementation depth.

### 37. How should traces be protected at rest? Recommended: Encrypted DB + redaction before write + package denylist

**Recommended answer:** Encrypted DB + redact before write + package denylist

**User answer:** Encrypted DB + redact before write + package denylist

**Status:** resolved

**Notes:** Keystore-backed encrypted DB; drop password nodes, mask OTP/token patterns, skip hard-block packages, cap screenshots.

### 38. What may the notification listener store? Recommended: Metadata only

**Recommended answer:** Metadata only; never notification text

**User answer:** Metadata only; never notification text

**Status:** resolved

**Notes:** Package, timestamp, channel, grouping hash. No title/body, so OTP notifications cannot leak.

### 39. Do deterministic actions need the editable plan preview, or only generic UI tasks? Recommended: Compact confirm for risky registry actions; plan preview only for generic tasks

**Recommended answer:** Compact confirm for risky registry actions; plan preview only for generic tasks

**User answer:** every action is editable task

**Status:** resolved

**Notes:** Every action, including call/SMS/media/settings, opens an editable task plan. Conflicts with the earlier auto-run media/system policy; execution gate asked in round 6.

### 40. How do traces leave the app? Recommended: JSONL/Markdown export to Downloads + purge button

**Recommended answer:** JSONL/Markdown export to Downloads + purge button

**User answer:** JSONL/Markdown export to Downloads + purge button

**Status:** resolved

**Notes:** Export is manual and local; no cloud sync.

### 41. How many UI steps may one plan run? Recommended: 8 steps

**Recommended answer:** 8 steps

**User answer:** No step limit

**Status:** resolved

**Notes:** Conflicts with runaway-tap safety and the 2B context budget. Non-step loop guards asked in round 6.

### 42. How deep should the default-assistant integration go? Recommended: Assist activity only

**Recommended answer:** Assist activity only (text-first); replaces Google Assistant

**User answer:** Assist activity only (text-first); replaces Google Assistant

**Status:** resolved

**Notes:** ASSIST intent activity set as default assistant; no VoiceInteractionService in v1; 'Hey Google' hotword likely stops.

### 43. If every action is an editable task, when does execution start? Recommended: every action waits for your approval, including media and system

**Recommended answer:** Every action waits for approval, including media/system

**User answer:** Every action waits for approval, including media/system

**Status:** resolved

**Notes:** Supersedes the earlier risky-only auto-run policy. One approval gate per command; editing a plan re-arms approval.

### 44. Without a step cap, which non-step guards should stop the agent? Recommended: repeated screen, 3 consecutive failed steps, hard-block target, or user stop

**Recommended answer:** Stop on repeated screen, 3 failed steps, hard-block target, or user stop

**User answer:** Stop on repeated screen, 3 failed steps, hard-block target, or user stop

**Status:** resolved

**Notes:** No step ceiling. Progress guards only; the overlay stop button is always available.

## Agreed Decisions

- Model: Gemma 4 E2B (.litertlm, ~2.58 GB) via LiteRT-LM; E4B rejected for 7.65 GB RAM.
- Device reality: CPH2717 / MediaTek MT6897 (Dimensity 7300), 7.65 GB RAM, Android 16, 42 GB free. No Qualcomm/Tensor NPU, so CPU XNNPACK or Mali GPU only.
- Glasses role: none in v1 beyond existing camera capture. No DAT Speech, no vision context, no glasses audio.
- Input: typed text only, in-app. No STT or wake word in v1.
- App identity: agent screen is home; glasses camera becomes a tab.
- Launch surface: ASSIST-intent activity set as default assistant (long-press home). No VoiceInteractionService. 'Hey Google' likely stops working.
- v1 flows: call a contact, send SMS, WhatsApp message, YouTube/YouTube Music playback, curated system settings + open-any-Settings-page. Spotify, alarms/timers, Telegram deferred.
- Settings: fixed typed actions with direct writes (screen timeout, brightness, volume, DND, rotation, flashlight) plus open-Settings-page actions. WRITE_SETTINGS granted once.
- Tool layer: in-process Kotlin registry using LiteRT-LM @Tool/ToolSet and Gemma 4 constrained decoding. No MCP, no localhost HTTP/SSE.
- Task semantics: one task per utterance. Registry actions are a single tool call; generic tasks are a multi-step Accessibility plan. No cross-command memory; state is task-scoped.
- Execution: every action, including media and system, opens an editable plan and waits for approval. This supersedes the earlier risky-only auto-run.
- Plan UX: approve / edit step / reorder / delete / cancel, plus natural-language re-plan. Every action is editable.
- Plan preview: every generic UI task, every time. No silent replay in v1.
- Accessibility: broad generic UI navigation is allowed, but only through plan approval. Absolute hard blocks: banking/UPI/payments/wallets, password entry and password managers, OTP read/entry, account-security changes, app install/uninstall, deleting files/chats/photos.
- Loop guards: no step count ceiling; stop on repeated screen signature, three consecutive failed steps, any hard-block target, or the overlay stop button.
- Contacts: READ_CONTACTS + local fuzzy match + editable alias table (Amma -> contact) + disambiguation sheet. The model never picks an identity.
- Media: YouTube/YT Music only. WhatsApp only. Spotify out of v1.
- Model delivery: one-time in-app download from Hugging Face with checksum; then fully offline.
- Model lifecycle: warm in a foreground service, resident by default; unload on Android critical-memory callback or free RAM under ~1 GB; reload on next command. Battery-optimisation exemption granted.
- Trace store: command, task, step selectors, outcome, timings, model prompt/tool JSON, accessibility text of screens touched, failure screenshots only. Encrypted DB (Keystore-backed), redaction before write, package denylist, screenshot cap.
- Trace retention: keep until manually deleted, on-device only. JSONL/Markdown export to Downloads plus in-app purge.
- Notification listener: metadata only (package, timestamp, channel, grouping hash). Never notification text. Usage access for foreground-app history.
- v1 records only: no workflow promotion, no automatic replay. v2 adds deterministic promotion and prompt replay for debugging.
- No-match: show the three nearest registered commands; never invent an action.
- Output: on-screen status plus phone TTS for confirmations.
- Distribution: personal sideload only. Play Store policy is out of scope.

## Open Risks

- E2B decode speed on MT6897 is unmeasured. The model-card benchmarks are Snapdragon/Tensor devices and do not transfer; first task is an on-device benchmark.
- A 2B model planning Accessibility steps is the least proven part. Plan quality, selector grounding, and retry behaviour are unknown on OxygenOS.
- No step cap plus small context (2048 default, 32k max) means observation encoding must stay compact; progress guards mitigate but do not bound token growth.
- Approve-every-action adds a tap and a plan-generation wait to every command, including 'set volume 50%'. This is the chosen UX but may feel heavy in daily use.
- Accessibility + full screen text + forever retention is a high-value local target. Encryption and redaction are best-effort; a stolen unlocked phone can decrypt the app DB.
- Failure screenshots and screen text can contain private messages even with banking/password/OTP blocks. Screenshot cap and redaction rules must be enforced before write, not after.
- Making the app default assistant replaces Google Assistant and likely disables 'Hey Google'; OxygenOS may also restrict assistant reassignment.
- ACTION_CALL / activity launches from an overlay or background may hit Android background-activity-start limits; the runner may need the agent activity in front or a full-screen notification.
- Settings direct writes depend on OEM keys; some OxygenOS keys differ, and DND needs notification-policy access in addition to WRITE_SETTINGS.
- Trace storage grows without bound because retention is forever; a size guard and purge nudge are still needed.
- Plan approval cannot be shown over FLAG_SECURE screens; if a plan reaches one, it must abort rather than guess.
- The prior A2A/MCP localhost design is abandoned. Android has no MCP stdio transport, and LiteRT-LM already provides tool schemas and constrained decoding.

## Next Decision Needed

Which single generic UI task to prove first on the phone: WhatsApp send (validates text entry + send-button selector) or YouTube play (validates search + first-result selector). Pick one before building the trace distiller.
