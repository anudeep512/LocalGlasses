# agent/telemetry Specification

## Purpose
Collects phone, app, and on-device inference metrics and binds one metric snapshot to each agent trace for live display, history, and export.

## Requirements

### Requirement: Foreground metric sampling

The app SHALL sample metrics at most once per second while the Agent or Stats surface is visible, and SHALL suspend sampling when the app is backgrounded.

#### Scenario: Visible surface
- **WHEN** the Agent or Stats surface is visible
- **THEN** a new metric sample is produced at approximately one-second intervals
- **AND** the sample includes the currently available device, app, and model metrics

#### Scenario: App backgrounded
- **WHEN** the app moves to the background
- **THEN** metric sampling stops
- **AND** no metric read runs until a stats surface becomes visible again

#### Scenario: Stats disabled at build time
- **WHEN** the stats feature is disabled at build time
- **THEN** no metric sampling runs

### Requirement: Memory metrics

The app SHALL report device total, available, and used memory, app proportional set size, Java heap used and maximum, native heap size, and the system low-memory flag.

#### Scenario: Memory read succeeds
- **WHEN** the Android memory APIs return values
- **THEN** the sample contains device total, available, and used bytes
- **AND** it contains app proportional set size, Java heap used and maximum, and native heap size

#### Scenario: Memory read fails
- **WHEN** a memory source throws or is restricted
- **THEN** that field is marked unavailable
- **AND** the remaining metrics are still collected

### Requirement: CPU metrics

The app SHALL report whole-device CPU percentage, app-process CPU percentage, per-core current frequency, and app thread count.

#### Scenario: Delta sample available
- **WHEN** a previous CPU sample exists
- **THEN** device and app CPU percentages are computed from the elapsed CPU time between samples
- **AND** per-core current frequencies and thread count are included

#### Scenario: First CPU sample
- **WHEN** no previous CPU sample exists
- **THEN** device and app CPU percentages are marked unavailable rather than reported as zero

#### Scenario: Core frequency unavailable
- **WHEN** a core frequency cannot be read
- **THEN** that core is marked unavailable
- **AND** readable cores and the remaining metrics are still reported

### Requirement: Thermal and battery metrics

The app SHALL report thermal status, battery temperature, battery level, and charging state.

#### Scenario: Thermal and battery read succeeds
- **WHEN** the Android thermal and battery APIs return values
- **THEN** the sample contains thermal status, battery temperature in degrees Celsius, battery percentage, and charging state

#### Scenario: Thermal or battery read fails
- **WHEN** a thermal or battery source is unavailable
- **THEN** only the affected fields are marked unavailable

### Requirement: Device, storage, and model context

The app SHALL report device model, system-on-chip name, supported ABIs, Android version, memory class, active inference backend, model identifier, model file size, free internal storage, and engine loaded state.

#### Scenario: Context snapshot
- **WHEN** a metrics sample is produced
- **THEN** it contains the device and Android specification values
- **AND** it contains the model identifier, model file size, free internal storage, active backend, and engine loaded state

#### Scenario: Model absent
- **WHEN** no verified model file is present
- **THEN** model-specific fields are marked unavailable
- **AND** device and storage fields are still reported

#### Scenario: GPU request
- **WHEN** the user views GPU information
- **THEN** the app states that no public Android GPU-utilization API is available
- **AND** it does not display a fabricated GPU percentage

### Requirement: Inference metrics per request

For each command that invokes the model, the app SHALL capture the model identifier, backend, engine loaded state, engine initialization time, prompt token count, completion token count, time to first token, prefill tokens per second, decode tokens per second, and total request latency.

#### Scenario: Model invoked
- **WHEN** a command completes a model inference call
- **THEN** its metric snapshot contains the inference metrics reported by the inference runtime
- **AND** it contains the measured total latency from request start to reply

#### Scenario: Model not invoked
- **WHEN** a command is blocked, has no match, is cancelled, or otherwise does not invoke the model
- **THEN** inference-only fields are null
- **AND** device metrics collected during the request are still recorded

### Requirement: Peak observation during a request

The app SHALL record the maximum app memory and device CPU percentage and the worst thermal status observed between request start and request finish.

#### Scenario: Request spans samples
- **WHEN** one or more metric samples occur during a request
- **THEN** the snapshot contains the maximum observed app memory and device CPU percentage
- **AND** it contains the worst observed thermal status

#### Scenario: Request has no sample
- **WHEN** no metric sample occurs during a request
- **THEN** peak fields are null
- **AND** the snapshot is still written

### Requirement: Per-request persistence

The app SHALL persist exactly one metrics snapshot per trace, linked to that trace, in the encrypted trace database.

#### Scenario: Completed request
- **WHEN** a trace is finished
- **THEN** exactly one metrics snapshot is associated with that trace
- **AND** the snapshot survives an app restart

#### Scenario: Existing database upgraded
- **WHEN** the app starts with a trace database created before metrics existed
- **THEN** existing traces are preserved
- **AND** they have no metrics snapshot until a new request is recorded

### Requirement: Metric failure isolation

A failed metric source SHALL NOT fail the command, the trace write, or the other metrics.

#### Scenario: Restricted source
- **WHEN** one metric source throws or is denied by the platform
- **THEN** the command still completes or fails according to its own outcome
- **AND** the trace and metrics snapshot are still written with an unavailable field

### Requirement: No fabricated metrics

The app SHALL NOT substitute zero, a default, or an estimate for a metric that could not be measured.

#### Scenario: Unmeasurable metric
- **WHEN** a metric cannot be measured
- **THEN** it is stored as absent
- **AND** it is presented as unavailable rather than as a numeric value
