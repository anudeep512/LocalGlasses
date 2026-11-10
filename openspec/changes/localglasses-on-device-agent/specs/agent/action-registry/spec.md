# Spec Delta

## Purpose

Defines the deterministic device actions the agent can run without planning UI steps, including contact resolution and system settings.

## ADDED Requirements

### Requirement: Call a contact

The system SHALL place a cellular call to a resolved contact after the action plan is approved.

#### Scenario: Unambiguous contact
- **WHEN** the user says "call Amma" and exactly one contact resolves
- **THEN** the plan shows the contact name and number
- **AND** approval places a cellular call to that number

#### Scenario: Contact permission missing
- **WHEN** contact access is not granted
- **THEN** the action reports the missing permission
- **AND** no call is placed

### Requirement: Contact disambiguation

When more than one contact matches, the system SHALL ask the user to choose before building the call or message step.

#### Scenario: Multiple matches
- **WHEN** a contact query matches two or more contacts
- **THEN** the plan lists each candidate with name and number
- **AND** no call or message is executed until one candidate is chosen

### Requirement: Direct phone number

The system SHALL accept a dialable phone number as a call or message target without contact lookup.

#### Scenario: Direct number
- **WHEN** the user says "call 7396512369"
- **THEN** the plan shows that number
- **AND** approval places the call without a contacts permission

#### Scenario: Formatted number
- **WHEN** the user says "call +91 73965-12369"
- **THEN** the number is normalised for the plan
- **AND** the call targets the same subscriber

### Requirement: Model cannot choose identity

Contact candidates SHALL be resolved by the local resolver, never selected by the language model.

#### Scenario: Model output contains a name or number
- **WHEN** the model returns a contact name or a direct number
- **THEN** the resolver maps it to a contact or dialable number
- **AND** the model is not given candidate phone numbers to choose from

### Requirement: Send an SMS

The system SHALL send an SMS to a resolved contact after showing the resolved name, number, and message text in the approved plan.

#### Scenario: SMS approved
- **WHEN** the user approves a plan containing a resolved contact and message text
- **THEN** the message is sent through the device SMS service
- **AND** the status reports the recipient

#### Scenario: SMS permission missing
- **WHEN** SMS sending is not permitted
- **THEN** the action reports the missing permission
- **AND** no message is sent

### Requirement: WhatsApp message

The system SHALL send a WhatsApp message only through WhatsApp, using a prefilled conversation and UI automation for the send control.

#### Scenario: WhatsApp send succeeds
- **WHEN** the approved plan targets a resolved contact with message text
- **THEN** WhatsApp opens the conversation with the text prefilled
- **AND** the send control is activated by the approved automation steps

#### Scenario: Send control not found
- **WHEN** the send control cannot be located
- **THEN** execution stops
- **AND** the status reports that the message was not sent
- **AND** no other messaging app is used

#### Scenario: Other chat apps excluded
- **WHEN** the user asks to message through Telegram or another chat app
- **THEN** the system reports that only WhatsApp is supported
- **AND** nothing is sent

### Requirement: YouTube playback

The system SHALL play media only through YouTube or YouTube Music.

#### Scenario: Play a search query
- **WHEN** the user says "play any song by Kanye West"
- **THEN** the approved plan opens the YouTube or YouTube Music search for that query
- **AND** the first result is started by the approved automation steps

#### Scenario: Spotify excluded
- **WHEN** the user asks to play through Spotify
- **THEN** the system reports that Spotify is not supported
- **AND** no media app is opened

### Requirement: Curated settings actions

The system SHALL expose only a fixed set of settings actions with typed values: screen-off timeout, brightness, volume, do-not-disturb, rotation, flashlight, and opening a settings page.

#### Scenario: Screen timeout
- **WHEN** the user says "change the sleep time to 60 seconds"
- **THEN** the plan contains a screen-off timeout action with 60 seconds
- **AND** approval writes that value

#### Scenario: Unsupported setting

- **WHEN** the user asks for a setting outside the fixed set
- **THEN** the system reports that the setting is not supported
- **AND** no settings value is changed

### Requirement: Settings value validation

A settings action SHALL reject a value outside its allowed range before execution.

#### Scenario: Out-of-range value
- **WHEN** a brightness value outside 0–100 is produced
- **THEN** the plan is rejected with the valid range
- **AND** no setting is written

### Requirement: Settings special access

When a settings action needs write-settings or do-not-disturb access that is not granted, the system SHALL report the missing access and SHALL NOT write a partial value.

#### Scenario: Write-settings missing
- **WHEN** the write-settings access is not granted
- **THEN** the status explains how to grant it
- **AND** the setting is unchanged

### Requirement: Target app missing

When a required app is not installed, the system SHALL report that app as unavailable and SHALL NOT fall back to another app.

#### Scenario: WhatsApp not installed
- **WHEN** a WhatsApp action is selected and WhatsApp is absent
- **THEN** the action reports WhatsApp as unavailable
- **AND** no substitute app is opened
