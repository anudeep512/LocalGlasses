# Spec Delta

## Purpose

Defines the non-overridable limits on what the agent may read or automate, regardless of user approval.

## ADDED Requirements

### Requirement: Hard-block categories

The agent SHALL NOT automate banking, UPI, payment, or wallet apps; password entry or password managers; reading or entering OTPs; account password or security-setting changes; app installation or uninstallation; or deletion of files, chats, or photos.

#### Scenario: Payment app
- **WHEN** a plan targets a package on the banking or payment denylist
- **THEN** the plan is blocked
- **AND** no step executes
- **AND** approval cannot override the block

#### Scenario: Deletion request
- **WHEN** a command would delete files, chats, or photos
- **THEN** the command is refused
- **AND** nothing is deleted

#### Scenario: Account security change
- **WHEN** the user asks to change an account password or security setting
- **THEN** the command is blocked
- **AND** no account or security screen is changed

### Requirement: Enforcement before planning

Every command SHALL be checked against the hard-block policy before a plan is generated.

#### Scenario: Blocked command
- **WHEN** the command asks to install an app
- **THEN** no plan is generated
- **AND** the status explains that installation is blocked

### Requirement: Enforcement at every step

Every UI step SHALL be checked against the hard-block policy using both the foreground package and the target node before it executes.

#### Scenario: Blocked target mid-plan
- **WHEN** a plan reaches a screen whose package or target node is hard-blocked
- **THEN** execution stops before the step
- **AND** the status reports the blocked target

### Requirement: Password field protection

The agent SHALL NOT read, store, or type into a field marked as a password field.

#### Scenario: Password screen encountered
- **WHEN** the active screen contains a password input
- **THEN** its content is excluded from observation and traces
- **AND** no typing step targets it

### Requirement: OTP protection

The agent SHALL NOT read, store, or enter one-time codes.

#### Scenario: OTP prompt
- **WHEN** a screen or notification contains an OTP
- **THEN** the code is not recorded or entered
- **AND** the task is blocked if it requires the code

### Requirement: Secure screen abort

When the active window is marked secure, the agent SHALL abort the task and SHALL NOT capture a screenshot.

#### Scenario: Secure window
- **WHEN** the active window reports a secure flag
- **THEN** the task stops
- **AND** no screenshot is taken
- **AND** the status reports the secure screen

### Requirement: Notification content limit

The notification listener SHALL record package, timestamp, channel, and a grouping identifier only, and SHALL NOT record notification title or body text.

#### Scenario: OTP notification
- **WHEN** an OTP notification arrives
- **THEN** only its package and timing metadata are recorded
- **AND** the code is absent from storage

### Requirement: Ambiguity denial

When the target package is unknown and the screen matches a payment, credential, or OTP pattern, the agent SHALL deny the action.

#### Scenario: Unknown payment screen
- **WHEN** an unknown package shows a payment form
- **THEN** the task is blocked
- **AND** the status explains the ambiguity

### Requirement: No sensitive block traces

A hard-block event SHALL be recorded as a block reason without storing the sensitive content that triggered it.

#### Scenario: Blocked banking screen
- **WHEN** a banking screen is blocked
- **THEN** the trace records the block and package
- **AND** it does not record account numbers or balances

