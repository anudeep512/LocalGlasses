# agent/model-runtime Specification

## Purpose
Defines how the local Gemma 4 E2B model is obtained, kept warm, constrained to registered tools, and released under memory pressure.

## Requirements

### Requirement: One-time model download

On first use, the app SHALL offer a user-initiated download of the local model from its public distribution URL and SHALL verify the file checksum before enabling inference.

#### Scenario: First command without a model
- **WHEN** the user submits a command and no verified model is present
- **THEN** the command screen offers the model download with its size
- **AND** no inference is attempted
- **AND** the command is not executed

#### Scenario: Verified model present
- **WHEN** the model file exists and its checksum matches
- **THEN** inference is enabled without another download

### Requirement: Resumable download

The model download SHALL show progress, support cancellation, and resume from the partial file after interruption.

#### Scenario: Interrupted download
- **WHEN** a download is interrupted and the user retries
- **THEN** the download resumes from the bytes already stored
- **AND** progress continues from the resumed point

#### Scenario: Cancelled download
- **WHEN** the user cancels a download
- **THEN** no inference is enabled
- **AND** the partial file is retained for a later resume

### Requirement: Integrity rejection

A model file whose checksum does not match SHALL NOT be used for inference.

#### Scenario: Corrupt model
- **WHEN** verification fails
- **THEN** the app reports a checksum failure
- **AND** inference stays disabled
- **AND** the rejected file is not loaded

### Requirement: Offline inference

After the model is verified, command interpretation SHALL work with no network connection.

#### Scenario: Airplane mode
- **WHEN** the device has no network and a verified model is present
- **THEN** commands are interpreted and executed locally
- **AND** no command text, plan, screen text, or trace is uploaded

### Requirement: Single mobile model

The app SHALL use Gemma 4 E2B as its only inference model and SHALL NOT offer E4B in this change.

#### Scenario: Model selection
- **WHEN** the user opens model settings
- **THEN** E2B is the only selectable model
- **AND** no E4B download option is shown

### Requirement: Warm foreground service

After the first successful model load, the app SHALL keep the engine warm in a foreground service with a persistent notification while the service is running.

#### Scenario: Warm service running
- **WHEN** the engine is loaded and the app is not on screen
- **THEN** a persistent notification identifies the agent service
- **AND** the next command does not reload the model from disk

### Requirement: Memory-pressure release

The app SHALL release the loaded engine when Android signals critical memory pressure or when available RAM falls below approximately 1 GB.

#### Scenario: Critical memory
- **WHEN** the system reports critical memory pressure while the engine is loaded
- **THEN** the engine is closed
- **AND** the foreground service remains available to reload it

#### Scenario: Low available RAM
- **WHEN** available RAM is below approximately 1 GB before a command
- **THEN** the engine is not loaded or is released
- **AND** the command reports that the model is reloading

### Requirement: Reload on demand

If the engine was released, the next command SHALL reload it without requiring the app to be restarted.

#### Scenario: Reload after release
- **WHEN** a command arrives after a memory-pressure release
- **THEN** the engine is loaded again
- **AND** the command proceeds once loading completes

### Requirement: Constrained output

Model output SHALL be restricted to the registered action and plan schemas, and any output that fails schema validation SHALL NOT be executed.

#### Scenario: Invalid model output
- **WHEN** the model emits an unknown action, an unknown argument, or malformed JSON
- **THEN** the system rejects the output
- **AND** nothing is executed
- **AND** the user sees a failure status

### Requirement: Responsive loading

Model loading SHALL NOT block the command screen's input or navigation.

#### Scenario: Slow load
- **WHEN** the engine is loading
- **THEN** the command screen remains interactive
- **AND** a loading status is visible until the model is ready

### Requirement: Battery-optimisation exemption

The app SHALL request exemption from battery optimisation for the warm service and SHALL keep working if the user denies it.

#### Scenario: Exemption denied
- **WHEN** the user denies the battery-optimisation exemption
- **THEN** commands still work
- **AND** the engine may be released by the system and reload on the next command
