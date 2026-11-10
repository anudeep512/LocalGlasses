# agent/plan-approval Specification

## Purpose
Defines the editable plan and approval gate that stands between every command and every side effect.

## Requirements

### Requirement: Plan for every action

Every command that resolves to an action or a UI task SHALL produce a plan, and no side effect SHALL occur before that plan is approved.

#### Scenario: Registry action plan
- **WHEN** a call, message, media, or settings action is selected
- **THEN** a plan is shown before execution
- **AND** the action runs only after approval

#### Scenario: UI task plan
- **WHEN** a generic UI task is selected
- **THEN** a plan is shown before execution
- **AND** no step runs until approval

### Requirement: Plan contents

A plan SHALL show its ordered steps, resolved arguments, and target app or contact.

#### Scenario: Resolved arguments
- **WHEN** a plan targets a contact or an app
- **THEN** the plan shows the resolved contact name and number or the target package
- **AND** it does not show unresolved placeholders

### Requirement: Explicit approval

Approval SHALL require a positive user action, and a plan SHALL NOT execute on a timeout or by default.

#### Scenario: No approval
- **WHEN** the user leaves the plan untouched
- **THEN** no step executes
- **AND** the plan remains pending

### Requirement: Rejection

The user SHALL be able to cancel a plan, and cancellation SHALL prevent every step.

#### Scenario: Cancelled plan
- **WHEN** the user cancels a plan
- **THEN** no side effect occurs
- **AND** the status reports cancellation

### Requirement: Structured editing

The user SHALL be able to edit a step's value, reorder steps, delete a step, or add a step within the allowed step types before approval.

#### Scenario: Edit before approval
- **WHEN** the user changes a message's text in the plan
- **THEN** the edited text is what executes after approval

#### Scenario: Delete a step
- **WHEN** the user deletes a step
- **THEN** that step does not execute
- **AND** the remaining steps keep their order

### Requirement: Edit invalidates approval

Any edit to an approved or pending plan SHALL discard the previous approval and require a new approval.

#### Scenario: Edit after approval
- **WHEN** the user edits a plan that was already approved but not yet started
- **THEN** execution does not start
- **AND** the plan returns to pending approval

### Requirement: Natural-language correction

The user SHALL be able to submit a correction in natural language, and the system SHALL replace the pending plan with a new plan.

#### Scenario: Re-plan
- **WHEN** the user says "use the search bar instead"
- **THEN** a new plan is generated from the same command and the correction
- **AND** the old plan is discarded
- **AND** the new plan waits for approval

### Requirement: Safety re-check after edit

Every edited plan SHALL be checked against the hard-block policy again before approval is accepted.

#### Scenario: Blocked edit
- **WHEN** an edit introduces a hard-blocked target
- **THEN** approval is refused
- **AND** the status explains the block
- **AND** no step executes

### Requirement: Silent execution

While a UI task runs in another app, the system SHALL NOT display an overlay or request display-over-other-apps permission.

#### Scenario: UI task starts
- **WHEN** an approved UI task starts in WhatsApp or YouTube
- **THEN** no progress overlay is shown
- **AND** the target app is not covered by a LocalGlasses window
- **AND** progress status remains available when the user returns to LocalGlasses

### Requirement: One pending plan

At most one plan SHALL be pending approval at a time.

#### Scenario: Second command while pending
- **WHEN** a plan is pending and the user submits another command
- **THEN** the new command is not executed
- **AND** the status asks the user to approve or cancel the pending plan first

### Requirement: Approval is the confirmation

Approving a plan SHALL be the only confirmation needed for the actions it contains, including calls and messages.

#### Scenario: Approved call
- **WHEN** the user approves a plan whose only step is a call
- **THEN** the call is placed without a second confirmation dialog
