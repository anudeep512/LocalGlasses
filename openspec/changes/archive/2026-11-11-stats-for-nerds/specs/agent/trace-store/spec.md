# Spec Delta

## ADDED Requirements

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
