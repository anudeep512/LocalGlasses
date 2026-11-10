# Tasks

## 1. Foundations

- [x] 1.1 Add LiteRT-LM, SQLCipher, OkHttp, WorkManager, kotlinx.serialization, lifecycle-service, and test dependencies to the version catalog and `app/build.gradle.kts`; add the serialization plugin. Verify `./gradlew :app:assembleDebug` succeeds.
- [x] 1.2 Create the `com.localglasses.app.agent` package skeleton and `AgentContainer`, and wire it into `LocalGlassesApp` without changing DAT initialisation. Verify the app launches and the container is non-null in an instrumentation smoke test.
- [x] 1.3 Update `AndroidManifest.xml`: agent permissions, launcher `<queries>`, `ASSIST` intent filter on `MainActivity`, `launchMode="singleTop"`, and `allowBackup="false"`. Verify the merged manifest with `./gradlew :app:processDebugMainManifest` and `adb shell dumpsys package com.localglasses.app` shows the ASSIST filter.
- [ ] 1.4 Extract `GlassesScreen` from `MainActivity` into its own file and add a bottom-tab shell with Agent, Camera, and Settings placeholders. Verify on device that the Camera tab still reaches `Session: STARTED` and renders a preview.
- [x] 1.5 Add JUnit 4, `kotlinx-coroutines-test`, AndroidX test runner, and a first smoke test. Verify `./gradlew test` passes and `./gradlew :app:connectedDebugAndroidTest` runs on the CPH2717.
- [x] 1.6 Document the new module layout, build, test, and device-run commands in `README.md`. Verify every documented command runs as written.

## 2. Model Runtime

- [x] 2.1 Add `assets/model.json` with the E2B URL, size, and SHA-256, plus a `ModelManifest` parser. Verify a unit test parses the asset and rejects a missing or malformed field.
- [x] 2.2 Implement `ModelRepository` and `ModelDownloadWorker` with free-space check, HTTP `Range` resume, progress, cancellation, and checksum verification. Verify MockWebServer tests cover resume after interruption, checksum rejection, and cancellation retaining the partial file.
- [x] 2.3 Define an `InferenceEngine` interface and a LiteRT-LM implementation, and implement `EngineHolder` with a mutex, IO-dispatcher load, close, and reload. Verify unit tests with a fake engine cover load/close/reload transitions and that a loading call does not block the caller thread.
- [x] 2.4 Add `AgentWarmService` with a persistent notification and `specialUse` foreground-service declaration. Verify on device with `adb shell dumpsys activity services com.localglasses.app` and a visible notification after the first load.
- [x] 2.5 Implement `MemoryGuard`: check `ActivityManager.MemoryInfo.availMem` before load and close the engine on `onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)` or `onLowMemory`. Verify unit tests with a fake memory source and a device check with `adb shell am send-trim-memory com.localglasses.app RUNNING_CRITICAL` followed by reload on the next command.
- [x] 2.6 Implement `RouterTools` and `ToolCallParser` with exactly-one-tool enforcement, argument validation, and schema rejection. Verify unit tests for zero, one, and multiple tool calls, and a manual command that selects `call_contact`.
- [ ] 2.7 Add the battery-optimisation exemption and notification-permission request flows. Verify that denying the exemption still allows a command and that the warm service survives with the permission granted.
- [ ] 2.8 Verify offline inference and benchmark CPU versus the GPU artefact on the CPH2717: record prefill and decode tokens/s for both, then select the default backend. Verify a command succeeds in airplane mode and record the measurements in `README.md`.
- [x] 2.9 Surface model states (absent, downloading, verifying, loading, ready, reloading) in the Agent UI and keep the screen interactive during load. Verify with a manual slow-load run and an instrumentation test of the state flow.
- [x] 2.10 Document model download, verification, storage location, and the benchmark procedure in `README.md`. Verify the documented steps reproduce a verified model.

## 3. Safety

- [x] 3.1 Implement `Redactor` for OTP-like values, bearer tokens, API keys, long hex strings, and card-like numbers. Verify unit tests cover each pattern and leave ordinary text unchanged.
- [x] 3.2 Add the read-only safety denylist asset and implement `SafetyPolicy` checks for the six hard-block categories, password nodes, OTP patterns, ambiguity denial, and command-level blocks. Verify unit tests cover every category and a benign command.
- [ ] 3.3 Implement `SecureScreen` detection using the package denylist and `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW`, and map it to a block result. Verify unit tests for both signals and a device check on a secure app.
- [ ] 3.4 Wire `checkCommand` into command submission, `checkPlan` into approval, and `checkStep` into the executor seam, and ensure a block records only package and reason. Verify unit tests and a manual blocked command.
- [x] 3.5 Document the hard-block list, the secure-screen limitation, and the notification content limit in `README.md`. Verify the documented list matches `SafetyPolicy`.

## 4. Commands and Approval

- [x] 4.1 Implement `AgentUiState` and `AgentViewModel` with a text field, submit action, status line, and blank-input rejection. Verify an instrumentation test covers submit and blank input, and a manual command shows a status.
- [x] 4.2 Implement `CommandRouter` with one-task resolution, registry-versus-plan selection, local no-match suggestions, and stateless prompt construction. Verify unit tests for a registry command, a generic command, no match, and a second command with no first-command context.
- [x] 4.3 Implement `Plan`, `PlanStep`, validation, and `PlanCoordinator` with a single pending plan. Verify unit tests for a second command while pending and for discarding task state after completion.
- [x] 4.4 Build `PlanApprovalSheet` with approve, edit, reorder, delete, and cancel, and invalidate approval on every edit. Verify a Compose instrumentation test for edit invalidation and a manual approval of a two-step plan.
- [x] 4.5 Implement natural-language correction: send the command, current plan, and correction to the planner, replace the pending plan, and require new approval. Verify a unit test with a fake planner.
- [ ] 4.6 Add `ExecutionOverlay` with progress text and a stop button, guarded by `Settings.canDrawOverlays`. Verify on device that the overlay appears over WhatsApp and that stop prevents the next step.
- [ ] 4.7 Wire phone TTS for approval requests, completions, and failures, with one utterance per event. Verify manually that no token stream or duplicate status is spoken.
- [ ] 4.8 Wire the real router to `AgentViewModel` behind a fake engine in tests, covering needs-model, needs-permission, no-match, pending, blocked, success, and failure results. Verify instrumentation tests for each state.
- [x] 4.9 Document the command-to-approval flow and the "approval is confirmation" rule in `README.md`. Verify the described flow matches the UI.

## 5. Encrypted Store and Aliases

- [x] 5.1 Add SQLCipher and implement `TraceDatabase` with a Keystore-wrapped passphrase, the trace/step/alias/notification schema, and a purge-and-recreate path for an invalidated key. Verify an instrumentation test opens the database with the key and fails without it.
- [x] 5.2 Implement `AliasStore` CRUD in the encrypted database. Verify instrumentation tests for add, edit, delete, and lookup.
- [x] 5.3 Implement the database failure path: log, surface a non-fatal warning, and offer purge/recreate. Verify an instrumentation test with a corrupted key reaches the recovery path without crashing.
- [x] 5.4 Document key management and the `allowBackup="false"` consequence in `README.md`. Verify the documented behaviour matches the manifest.

## 6. Action Registry

- [ ] 6.1 Implement `ActionRegistry`, parameter validators, and plan builders for the seven router actions. Verify unit tests for valid and invalid arguments for each action.
- [x] 6.2 Implement `ContactsResolver` with alias precedence, normalised exact/prefix/contains matching, deterministic ordering, and multiple-match disambiguation. Verify unit tests with fake contacts for unique, multiple, alias, and none cases.
- [ ] 6.3 Implement the call action with `CALL_PHONE` checks and an `ACTION_CALL` step. Verify on device that an approved call reaches the dialer and that a missing permission produces `NeedsPermission`.
- [ ] 6.4 Implement the SMS action with the resolved recipient and message in the plan and `SmsManager` execution. Verify on device that an approved SMS is sent and that a missing permission does not send.
- [ ] 6.5 Implement the WhatsApp action with a prefilled conversation and a send step resolved by view id then content description, failing closed when not found. Verify on device that an approved message sends and that a removed selector produces "not sent".
- [ ] 6.6 Implement the YouTube and YouTube Music actions with package-specific search URIs and first-result play steps. Verify on device for both packages and with one package uninstalled.
- [ ] 6.7 Implement `SettingsController` for screen timeout, brightness, media volume, DND, rotation, flashlight, and open-page, with range validation and access checks. Verify unit tests for ranges and device checks for each write.
- [ ] 6.8 Build the Settings and alias-editing screens: model state, aliases, trace actions, and special-access shortcuts. Verify manual add/edit/delete of an alias and that the next call uses it.
- [x] 6.9 Document supported actions, required permissions, and excluded apps in `README.md`. Verify the list matches the registry.

## 7. Accessibility Automation

- [x] 7.1 Add `AgentAccessibilityService`, its XML config, manifest declaration, and the "Allow restricted settings" setup guidance. Verify the service can be enabled on the CPH2717 and reports its state.
- [x] 7.2 Implement observation with bounded breadth-first traversal, stable node refs, password-text exclusion, text truncation, and screen signatures. Verify unit tests with a fake node tree for node cap, password exclusion, and signature stability.
- [x] 7.3 Implement `PlanExecutor` for open-app, tap, type, scroll, and wait with node resolution, clickable-ancestor climbing, post-condition polling, and a 5 s timeout. Verify unit tests with a fake accessibility gateway for success and timeout.
- [x] 7.4 Implement `ProgressTracker` for repeated-screen detection, three consecutive failures, hard-block stops, immediate user stop, and no step ceiling. Verify unit tests for each guard and a 60-step fake plan that keeps progressing.
- [ ] 7.5 Implement generic plan generation through the `submit_plan` schema and connect it to approval and execution. Verify the first generic task end to end on device: approve and send a WhatsApp message with a resolved contact.
- [x] 7.6 Document how to enable Accessibility, the restricted-settings step, and the supported step types in `README.md`. Verify the steps work from a clean install.

## 8. Trace Store

- [x] 8.1 Implement `TraceRecorder` for trace, step, model prompt, tool-call JSON, screen text, outcome, and timing records, including block events without sensitive content. Verify an instrumentation test inspects a completed trace and a blocked trace.
- [ ] 8.2 Implement failure-only screenshots, the 50-image cap, and exclusion of secure and hard-blocked screens. Verify an instrumentation test for the cap and a manual secure-screen failure with no image.
- [x] 8.3 Implement `TraceExporter` (JSONL and Markdown to Downloads) and the confirmed purge action. Verify an instrumentation test reads the exported files and confirms purge deletes records but not exports.
- [ ] 8.4 Add `AgentNotificationListener`, its manifest declaration, and metadata-only recording. Verify an instrumentation test that posts a notification with a code and asserts the code is absent from storage.
- [ ] 8.5 Implement the usage-access helper for the foreground package. Verify on device that the current package is reported and that denying usage access degrades without failing commands.
- [x] 8.6 Build the trace settings screen with record count, export, and purge. Verify manual export, purge, and count refresh.
- [x] 8.7 Document the trace schema, retention, export format, and purge behaviour in `README.md`. Verify the documented export can be opened and parsed.

## 9. Integration and Device Validation

- [ ] 9.1 Run the call and SMS flows end to end with an alias such as "Amma", including multiple-match disambiguation. Verify the call/SMS log matches the approved plan.
- [ ] 9.2 Run the WhatsApp send and YouTube play flows end to end from typed command through approval. Verify the target app performs the approved action and the status matches.
- [ ] 9.3 Run all seven settings actions on the device, including a missing write-settings and DND access case. Verify each value before and after and that no partial write occurs.
- [ ] 9.4 Verify the default-assistant entry, the camera tab, TTS output, and the overlay stop control in one session. Verify the agent opens from the assist gesture and the camera still streams.
- [ ] 9.5 Exercise every permission-denied path: contacts, call, SMS, accessibility, overlay, write settings, DND, notification listener, usage access, and battery exemption. Verify each reports a clear status and no partial action.
- [ ] 9.6 Exercise the hard blocks on a real banking app, a password field, and an OTP screen. Verify none is automated and no sensitive content appears in the trace export.
- [ ] 9.7 Run a 20-command soak with the model warm and apps switching, then check memory, thermal state, and battery with `adb shell dumpsys meminfo`, `dumpsys thermalservice`, and `dumpsys battery`. Verify unload/reload occurs without a crash and record the results.
- [ ] 9.8 Run `./gradlew test lint assembleDebug` and `openspec validate localglasses-on-device-agent --strict`. Verify all pass and record the results in `README.md`.
- [ ] 9.9 Update `README.md` with the verified device results, known limitations, and the first-run setup checklist. Verify a fresh install can follow the checklist to a working command.

## Workflow follow-up

- Archive the change after review and device validation are complete.
- Verify the archived specs under `openspec/specs/` contain the seven new capabilities.
