# Spec Delta

## ADDED Requirements

### Requirement: Speech recognition metrics per request

When a request's command was dictated, its metrics snapshot SHALL also record the audio duration, recognition latency, real-time factor, speech model identifier, audio source, endpoint reason, and the peak app memory observed during recognition.

#### Scenario: Dictated command
- **WHEN** a command is submitted after a successful dictation
- **THEN** the request snapshot contains the speech recognition metrics of that dictation
- **AND** it identifies the audio source that produced the audio

#### Scenario: Typed command
- **WHEN** a command is submitted without dictation
- **THEN** every speech recognition field is absent
- **AND** the snapshot is otherwise unchanged

#### Scenario: Recognition failed
- **WHEN** the dictation that preceded the command produced no transcript
- **THEN** the snapshot still records the measured speech metrics
- **AND** no substitute transcript value is recorded

### Requirement: Speech metric failure isolation

A speech recognition metric that cannot be measured SHALL be stored as absent and SHALL NOT affect the other metrics or the request.

#### Scenario: Timing unavailable
- **WHEN** the recognition runtime reports no timing information
- **THEN** only the affected speech fields are absent
- **AND** the request and its trace are still recorded
