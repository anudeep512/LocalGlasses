# agent/trace-store Specification

## Purpose
Defines the encrypted on-device record of agent activity that v2 will use to distil deterministic workflows.

## Requirements

### Requirement: Record every interaction

The store SHALL record the command, resolved task, app target, plan steps and selectors, outcome, timings, model prompt, and tool-call JSON for every executed or blocked task.

#### Scenario: Completed task
- **WHEN** a task completes
- **THEN** one trace record contains the command, plan, steps, outcome, and timings
- **AND** it contains the model prompt and tool-call JSON

#### Scenario: Blocked task
- **WHEN** a task is blocked by safety policy
- **THEN** a trace record contains the block reason and target package
- **AND** it contains no blocked screen content

### Requirement: Screen text capture

The store SHALL record the accessibility text of each screen observed during a UI task.

#### Scenario: UI task screen
- **WHEN** a UI task observes a screen
- **THEN** the normalised screen text is recorded with the step
- **AND** password fields and OTP values are absent

### Requirement: Redaction before write

Redaction SHALL run before any content is written, removing password fields, OTP-like values, bearer and API-key patterns, and content from hard-blocked packages.

#### Scenario: OTP in screen text
- **WHEN** an observed screen contains a six-digit OTP
- **THEN** the stored text contains a masked value
- **AND** the original code is not present in the database or its journal

#### Scenario: Blocked package content
- **WHEN** a hard-blocked package is observed
- **THEN** its screen text is not written

### Requirement: Encryption at rest

Trace data SHALL be stored in a database encrypted with a key held by the Android Keystore.

#### Scenario: Database file inspected
- **WHEN** the database file is read outside the app without its key
- **THEN** trace content is not readable

#### Scenario: App data cleared
- **WHEN** the user clears app storage or uninstalls the app
- **THEN** the trace database is removed with the app data

### Requirement: Failure screenshots only

The store SHALL capture a screenshot only when a task fails, and SHALL retain no more than 50 failure screenshots, deleting the oldest first.

#### Scenario: Successful task
- **WHEN** a task completes successfully
- **THEN** no screenshot is stored

#### Scenario: Screenshot cap
- **WHEN** a fifty-first failure screenshot is stored
- **THEN** the oldest screenshot is deleted
- **AND** the total remains fifty

### Requirement: Screenshot exclusions

The store SHALL NOT capture a screenshot of a secure screen or a hard-blocked package.

#### Scenario: Secure failure
- **WHEN** a task fails on a secure screen
- **THEN** the failure is recorded without a screenshot

### Requirement: Retention until purge

Trace records SHALL remain until the user purges them, and the app SHALL NOT delete them automatically.

#### Scenario: Old trace
- **WHEN** a trace is more than thirty days old and the user has not purged
- **THEN** it is still present and readable in the app

### Requirement: Manual purge

The app SHALL provide a purge action that deletes all trace records and screenshots after an explicit confirmation.

#### Scenario: Purge confirmed
- **WHEN** the user confirms purge
- **THEN** the trace database and stored screenshots are deleted
- **AND** previously exported files outside the app are not deleted

#### Scenario: Purge cancelled
- **WHEN** the user dismisses the purge confirmation
- **THEN** all traces remain

### Requirement: Manual export

The app SHALL export traces on explicit request as JSONL and Markdown into the device Downloads collection.

#### Scenario: Export
- **WHEN** the user requests an export
- **THEN** a JSONL file and a Markdown file are written to Downloads
- **AND** no network request is made

### Requirement: Notification metadata only

The store SHALL record notification package, timestamp, channel, and grouping identifier, and SHALL NOT record notification title or body.

#### Scenario: Message notification
- **WHEN** a messaging notification arrives
- **THEN** only its metadata is recorded
- **AND** the message text is absent

### Requirement: No automatic transmission

Trace data SHALL NOT be uploaded, synced, or shared automatically.

#### Scenario: Network available
- **WHEN** the device has network access and a trace is written
- **THEN** no request carries trace content
- **AND** only an explicit user export writes trace data outside the app

### Requirement: Metrics snapshot retention

A per-request metrics snapshot SHALL remain available exactly as long as its parent trace remains.

#### Scenario: Old trace with metrics
- **WHEN** a trace with metrics is older than thirty days and the user has not purged
- **THEN** its metrics snapshot is still present and readable

#### Scenario: Trace without metrics
- **WHEN** a trace was created before metrics existed
- **THEN** the trace remains readable without a metrics snapshot

### Requirement: Metrics in export

The existing JSONL and Markdown export SHALL include each trace's metrics snapshot, with unavailable fields represented explicitly.

#### Scenario: JSONL export
- **WHEN** the user exports traces
- **THEN** each JSONL record contains a metrics object for traces that have one
- **AND** absent metrics fields are represented as null rather than omitted or defaulted

#### Scenario: Markdown export
- **WHEN** the user exports traces
- **THEN** each trace section includes its latency, token-throughput, memory, CPU, and thermal summary where available

#### Scenario: Export without metrics
- **WHEN** a trace has no metrics snapshot
- **THEN** its export record or section states that metrics are unavailable
- **AND** the export still succeeds

### Requirement: Metrics in purge

The existing purge action SHALL delete metrics snapshots together with their parent traces.

#### Scenario: Purge confirmed
- **WHEN** the user confirms purge
- **THEN** every trace and its metrics snapshot are deleted from the encrypted database

#### Scenario: Purge cancelled
- **WHEN** the user dismisses the purge confirmation
- **THEN** all traces and metrics snapshots remain
