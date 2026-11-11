# agent/commands Specification

## Purpose
Defines how a command enters LocalGlasses, how one command maps to exactly one task, and how the result is reported.

## Requirements

### Requirement: Agent home entry

The app SHALL open on the agent command screen when launched normally.

#### Scenario: Normal launch
- **WHEN** the user taps the LocalGlasses launcher icon
- **THEN** the agent command screen is shown with the text field available
- **AND** no glasses session is started automatically

#### Scenario: Camera remains reachable
- **WHEN** the user selects the camera tab
- **THEN** the existing glasses camera screen is shown
- **AND** registration, session, capture, and recording controls behave as before

### Requirement: Default assistant entry

The app SHALL expose an assist entry point that opens the command screen with the text field focused.

#### Scenario: Assist gesture
- **WHEN** LocalGlasses is the default assistant and the user performs the system assist gesture
- **THEN** the agent command screen opens with the text field focused
- **AND** the app does not start microphone capture

### Requirement: One task per command

One submitted command SHALL resolve to at most one task.

#### Scenario: Single resolution
- **WHEN** the user submits a command
- **THEN** the system produces exactly one action or one UI plan
- **AND** it does not chain a second task from the same command

### Requirement: Two execution paths

A resolved command SHALL be either one registered action or one UI-automation plan.

#### Scenario: Registry selection
- **WHEN** the command matches a registered action
- **THEN** that action is selected with its typed arguments
- **AND** no UI plan is generated

#### Scenario: Generic task selection
- **WHEN** the command does not match a registered action but can be expressed as UI steps
- **THEN** a UI plan is generated
- **AND** no registry action is executed

### Requirement: No-match feedback

When a command resolves to neither an action nor a UI plan, the system SHALL show the three nearest registered commands.

#### Scenario: Unsupported command
- **WHEN** the user submits a command that no registered action or plan can satisfy
- **THEN** the command screen lists three suggested registered commands
- **AND** nothing is executed
- **AND** no trace is written for an invented action

### Requirement: Status output

Every submitted command SHALL produce a visible status on the command screen. The app SHALL NOT produce speech output.

#### Scenario: Completion
- **WHEN** a task completes
- **THEN** the command screen shows the outcome
- **AND** no audio is played

#### Scenario: Failure
- **WHEN** a task cannot complete
- **THEN** the command screen shows the failing step and reason
- **AND** no audio is played
- **AND** the model is not asked to explain the failure

### Requirement: Stateless commands

Each command SHALL be interpreted without conversation history from earlier commands.

#### Scenario: New command after a completed task
- **WHEN** the user submits a second command after a task completes
- **THEN** the prompt for the second command contains no content from the first command or its result
- **AND** task state from the first command is discarded

### Requirement: Blank input

Blank or whitespace-only input SHALL be ignored without model invocation.

#### Scenario: Empty submission
- **WHEN** the user submits an empty or whitespace-only command
- **THEN** no model call is made
- **AND** no status or trace entry is produced
