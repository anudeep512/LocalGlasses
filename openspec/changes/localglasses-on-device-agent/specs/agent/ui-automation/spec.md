# Spec Delta

## Purpose

Defines how the agent observes other apps, turns a generic command into UI steps, executes those steps, and stops safely when progress fails.

## ADDED Requirements

### Requirement: Accessibility availability

Generic UI tasks SHALL run only while the Accessibility service is enabled.

#### Scenario: Service disabled
- **WHEN** the user submits a generic UI command and the Accessibility service is off
- **THEN** the command reports that automation access is required
- **AND** no plan is generated
- **AND** registered actions remain available

### Requirement: Screen observation

Before planning and after every executed step, the system SHALL observe the active window and produce a compact description of actionable nodes with stable references.

#### Scenario: Observation after a step
- **WHEN** a step completes
- **THEN** the active window is observed again
- **AND** the next step is resolved against that new observation

#### Scenario: Password node
- **WHEN** the active window contains a password field
- **THEN** its content is excluded from the observation and from the prompt

### Requirement: Allowed step types

A UI plan SHALL contain only these step types: open app, tap a resolved node, type text, scroll, and wait.

#### Scenario: Unknown step type
- **WHEN** a plan contains a step outside the allowed types
- **THEN** the plan is rejected
- **AND** no step is executed

### Requirement: Node-resolved taps

A tap step SHALL resolve its target from the current observation, and SHALL fail if the target node is not present.

#### Scenario: Target missing
- **WHEN** a tap step names a node that is not in the current screen
- **THEN** the step fails
- **AND** the runner does not tap an approximate or hardcoded position

### Requirement: Sequential execution

Plan steps SHALL execute in order, and a step SHALL NOT start until the previous step has completed or failed.

#### Scenario: Ordered execution
- **WHEN** a plan contains three steps
- **THEN** step two starts only after step one succeeds
- **AND** step three does not start if step two fails

### Requirement: Step timeout

Each step SHALL have a bounded wait, and a step that does not meet its post-condition within that wait SHALL count as failed.

#### Scenario: Step timeout
- **WHEN** a step's expected screen change does not occur within its timeout
- **THEN** the step is marked failed
- **AND** the failure count for loop guards increases

### Requirement: Task-scoped state

UI automation state SHALL persist only for the current task and SHALL be discarded when the task ends.

#### Scenario: Task end
- **WHEN** a UI task completes, fails, or is stopped
- **THEN** its observations, step history, and any pending node references are discarded

### Requirement: Loop guards

A UI task SHALL stop when the same screen signature repeats without progress, when three consecutive steps fail, when a hard-block target is encountered, or when the user presses stop.

#### Scenario: Repeated screen
- **WHEN** the same normalised screen signature is observed twice with no state change
- **THEN** the task stops
- **AND** the status reports no progress

#### Scenario: Consecutive failures
- **WHEN** three consecutive steps fail
- **THEN** the task stops
- **AND** the failing step and reason are reported

### Requirement: No step ceiling

A UI task SHALL NOT be limited by a fixed step count and SHALL run until completion or a loop guard.

#### Scenario: Long task
- **WHEN** a task requires more than fifty steps and keeps making progress
- **THEN** execution continues
- **AND** only the loop guards can stop it

### Requirement: Immediate stop

Pressing the stop control SHALL prevent any further step from starting.

#### Scenario: User stop
- **WHEN** the user presses stop during execution
- **THEN** no further step is dispatched
- **AND** the task ends with a stopped status

### Requirement: App launch step

An open-app step SHALL launch the named package, and SHALL fail if that package is not installed.

#### Scenario: App absent
- **WHEN** an open-app step names an uninstalled package
- **THEN** the step fails
- **AND** no substitute app is opened

### Requirement: Failure reporting

A failed UI task SHALL report the failing step, its reason, and whether any earlier steps completed.

#### Scenario: Mid-plan failure
- **WHEN** step three of five fails
- **THEN** the status names step three and its reason
- **AND** the task does not continue to step four
