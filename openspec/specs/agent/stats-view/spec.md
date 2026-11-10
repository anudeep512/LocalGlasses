# agent/stats-view Specification

## Purpose
Presents live and historical on-device inference metrics in a build-flagged, collapsible interface on the Agent screen and a dedicated Stats page.

## Requirements

### Requirement: Build-time stats flag

The app SHALL expose a compile-time stats flag that defaults to enabled, and SHALL hide every stats surface when the flag is disabled.

#### Scenario: Flag enabled
- **WHEN** the app is built with the stats flag enabled
- **THEN** the Agent-tab stats blocks and the Stats navigation item are available

#### Scenario: Flag disabled
- **WHEN** the app is built with the stats flag disabled
- **THEN** no stats block appears on the Agent tab
- **AND** no Stats navigation item appears
- **AND** the app offers no runtime control that re-enables the feature

### Requirement: Agent-tab Stats for nerds block

The Agent tab SHALL show one collapsible "Stats for nerds" block below the plan, containing both per-request metrics and device/model specifications.

#### Scenario: Block placement
- **WHEN** the Agent tab is opened
- **THEN** the "Stats for nerds" block appears below the plan area
- **AND** it is collapsed by default

#### Scenario: Request starts
- **WHEN** a command starts
- **THEN** the "Stats for nerds" block expands automatically
- **AND** its request-metrics section shows live device metrics while the request is running

#### Scenario: Request finishes
- **WHEN** a command finishes
- **THEN** the request-metrics section shows the persisted inference metrics for that request if the model ran
- **AND** it keeps showing the latest completed request until the next request starts

#### Scenario: Expanded content
- **WHEN** the user expands "Stats for nerds"
- **THEN** the block shows the request-metrics section followed by device model, system-on-chip, ABIs, Android version, memory class, model identifier, model file size, active backend, and engine loaded state
- **AND** it states that GPU utilization is unavailable because Android exposes no public API

#### Scenario: User collapses the block
- **WHEN** the user collapses "Stats for nerds"
- **THEN** the request metrics and specifications remain recorded and visible again when the block is expanded

### Requirement: Stats tab live snapshot

The Stats tab SHALL show a live snapshot of memory, CPU, thermal, battery, storage, and model state.

#### Scenario: Stats tab visible
- **WHEN** the Stats tab is visible
- **THEN** the live snapshot refreshes approximately once per second
- **AND** it shows the time of the last successful refresh

#### Scenario: Stats tab not visible
- **WHEN** the user leaves the Stats tab or backgrounds the app
- **THEN** live refresh stops
- **AND** returning to the tab refreshes immediately

### Requirement: Historical summary

The Stats tab SHALL show summary cards for the selected time range.

#### Scenario: Data present
- **WHEN** traces with metrics exist in the selected range
- **THEN** the summary shows total requests, success rate, median and 95th-percentile latency, average time to first token, average decode tokens per second, peak app memory, average device CPU percentage, and latest thermal status

#### Scenario: No data
- **WHEN** no traces exist in the selected range
- **THEN** the summary shows an empty state
- **AND** it shows no fabricated zero values

### Requirement: Metric sparklines

The Stats tab SHALL show sparklines for request latency, decode tokens per second, app memory, and device CPU percentage over the selected range.

#### Scenario: Sufficient points
- **WHEN** two or more traces with the relevant metric exist in the selected range
- **THEN** each sparkline plots those values in time order

#### Scenario: Missing points
- **WHEN** a trace lacks the plotted metric
- **THEN** that point is omitted or shown as a gap
- **AND** it is not plotted as zero

### Requirement: Expandable trace table

The Stats tab SHALL list recorded requests in reverse chronological order and allow each row to expand.

#### Scenario: Collapsed row
- **WHEN** a request row is collapsed
- **THEN** it shows time, command, outcome, total latency, time to first token, decode tokens per second, and peak app memory where available

#### Scenario: Expanded row
- **WHEN** the user expands a request row
- **THEN** it shows the full metric snapshot for that request
- **AND** unavailable values are labelled unavailable

### Requirement: History range filter

The Stats tab SHALL filter history to the last 24 hours, the last 7 days, or all retained traces.

#### Scenario: Default range
- **WHEN** the Stats tab is opened
- **THEN** the selected range is the last 24 hours

#### Scenario: Range changed
- **WHEN** the user selects the 7-day or all-time range
- **THEN** the summary, sparklines, and trace table use traces from that range

#### Scenario: All-time range
- **WHEN** the user selects all time
- **THEN** every retained trace is eligible regardless of age

### Requirement: Read-only history

The Stats tab SHALL NOT provide export, purge, or delete controls.

#### Scenario: Stats page controls
- **WHEN** the user views the Stats tab
- **THEN** no export, purge, or delete control is shown
- **AND** export and purge remain available from the existing Settings surface

### Requirement: Unavailable metric presentation

The app SHALL label every metric it could not measure as unavailable.

#### Scenario: Missing metric in live view
- **WHEN** a live metric cannot be read
- **THEN** its field shows an unavailable label rather than a blank or zero value

#### Scenario: Missing metric in history
- **WHEN** a historical snapshot lacks a metric
- **THEN** the table and expanded row show an unavailable label
- **AND** aggregates exclude the missing value
