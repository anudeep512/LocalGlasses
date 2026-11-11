# Spec Delta

## ADDED Requirements

### Requirement: Speech recognition metrics presentation

The stats surfaces SHALL always show a Speech Recognition section beside the inference metrics, populated by the most recent dictation, and SHALL render absent values as unavailable rather than hiding the section.

#### Scenario: Dictated request in the live block
- **WHEN** a dictation finishes recognition
- **THEN** the Speech Recognition section immediately shows the speech model, audio source, audio duration, recognition latency, real-time factor, and endpoint reason
- **AND** the values shown are the ones recorded for that utterance
- **AND** they survive the command being submitted

#### Scenario: No dictation yet
- **WHEN** no utterance has been processed since the app started
- **THEN** the Stats for nerds block still shows the Speech Recognition section
- **AND** its values are presented as unavailable

#### Scenario: Historical request
- **WHEN** the user expands a stored request in the Stats page that came from a dictated command
- **THEN** the expanded row shows the same speech metrics that were recorded

#### Scenario: Unavailable speech metric
- **WHEN** a recorded speech metric is absent
- **THEN** it is presented as unavailable rather than as zero or a default
- **AND** the remaining speech metrics are still shown

#### Scenario: Stats disabled at build time
- **WHEN** the app is built with the stats flag disabled
- **THEN** no speech metrics are presented anywhere
- **AND** dictation itself still works
