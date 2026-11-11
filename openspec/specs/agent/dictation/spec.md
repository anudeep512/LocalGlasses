# agent/dictation Specification

## Purpose
Turns speech captured from the phone or the glasses into English command text, entirely on the device, and places that text in the agent command field for explicit submission.

## Requirements

### Requirement: Dictation entry point

The Agent screen SHALL show a microphone control beside the command field whenever no command is being submitted and no plan is awaiting approval.

#### Scenario: Control visible
- **WHEN** the Agent screen is shown with no submission in flight
- **THEN** a microphone control appears beside the command field
- **AND** it is enabled

#### Scenario: Control unavailable
- **WHEN** a command is being submitted or a plan is pending approval
- **THEN** the microphone control is disabled
- **AND** tapping it starts no capture

#### Scenario: Assist gesture does not dictate
- **WHEN** the app is opened through the assist entry point
- **THEN** no microphone capture starts
- **AND** the microphone control is the only way to begin dictation

### Requirement: Utterance capture lifecycle

One dictation SHALL capture exactly one utterance, and capture SHALL end on a second tap, on detected silence, or at the length cap, whichever occurs first.

#### Scenario: Tap starts capture
- **WHEN** the user taps the microphone control while idle
- **THEN** capture starts
- **AND** the control shows an active recording state

#### Scenario: Tap ends capture
- **WHEN** the user taps the microphone control while capturing
- **THEN** capture stops immediately
- **AND** the utterance is transcribed

#### Scenario: Only one utterance per dictation
- **WHEN** an utterance ends
- **THEN** no further audio is captured until the user taps the control again

### Requirement: Silence endpointing

Capture SHALL end after approximately 2 seconds of detected silence, and a capture containing no speech SHALL be discarded without transcription.

#### Scenario: Speech followed by silence
- **WHEN** the user stops speaking for approximately 2 seconds
- **THEN** capture ends
- **AND** the captured speech is transcribed

#### Scenario: No speech detected
- **WHEN** a capture ends without any detected speech
- **THEN** no transcription is attempted
- **AND** the app reports that no speech was heard
- **AND** the command field is unchanged

### Requirement: Utterance length cap

Capture SHALL stop at 15 seconds regardless of detected speech, and the audio captured up to that point SHALL be transcribed.

#### Scenario: Cap reached
- **WHEN** an utterance reaches 15 seconds
- **THEN** capture stops
- **AND** the audio captured so far is transcribed
- **AND** the app reports that the cap ended the utterance

### Requirement: On-device English recognition

Recognition SHALL run inside the app on the device for English speech, and captured audio SHALL NOT be transmitted to any service.

#### Scenario: Recognition without network
- **WHEN** a dictation runs with no network connection and a ready model
- **THEN** the utterance is transcribed locally
- **AND** no audio leaves the device

#### Scenario: Non-English speech
- **WHEN** the utterance is not English
- **THEN** the app returns the best English transcription it can
- **AND** it makes no language-selection claim

### Requirement: Transcript insertion

The final transcript SHALL replace the command field contents, SHALL NOT be submitted automatically, and SHALL preserve an empty field when recognition produces no text.

#### Scenario: Recognised speech
- **WHEN** recognition returns non-empty text
- **THEN** the command field contains that text
- **AND** the text is trimmed of leading and trailing whitespace
- **AND** no command is submitted

#### Scenario: Previous text replaced
- **WHEN** the command field already contains text and a dictation succeeds
- **THEN** the field contains the new transcript only

#### Scenario: Empty transcript
- **WHEN** recognition returns empty or whitespace-only text
- **THEN** the field keeps its previous contents
- **AND** the app reports that nothing was recognised

### Requirement: Speech model availability

Dictation SHALL be unavailable until a verified speech model is present, and the app SHALL offer its download from the microphone control.

#### Scenario: Model absent
- **WHEN** the user taps the microphone control with no verified speech model
- **THEN** the app offers the speech model download with its size
- **AND** no capture starts
- **AND** no recognition is attempted

#### Scenario: Model ready
- **WHEN** a verified speech model is present
- **THEN** tapping the microphone control starts capture without a download prompt

### Requirement: Model download and integrity

The speech model download SHALL show progress, support cancellation, resume after interruption, and SHALL verify a checksum before recognition is enabled.

#### Scenario: Interrupted download
- **WHEN** a download is interrupted and the user retries
- **THEN** it resumes from the bytes already stored
- **AND** progress continues from the resumed point

#### Scenario: Cancelled download
- **WHEN** the user cancels the download
- **THEN** recognition stays disabled
- **AND** the partial file is kept for a later attempt

#### Scenario: Checksum mismatch
- **WHEN** the downloaded file fails verification
- **THEN** recognition stays disabled
- **AND** the app reports a checksum failure
- **AND** the rejected file is not loaded

### Requirement: Audio source selection

The app SHALL offer a dictation audio-source setting with phone and glasses options, SHALL default to the phone microphone, and SHALL persist the choice across restarts.

#### Scenario: Default source
- **WHEN** the app runs with no stored preference
- **THEN** dictation uses the phone microphone

#### Scenario: Source changed
- **WHEN** the user selects the glasses microphone in Settings
- **THEN** the next dictation uses the glasses microphone
- **AND** the choice survives an app restart

### Requirement: Phone microphone source

With the phone microphone selected, dictation SHALL use the device microphone and SHALL request the microphone permission on first use.

#### Scenario: Permission granted
- **WHEN** the microphone permission is granted and the phone source is selected
- **THEN** capture starts on tap

#### Scenario: Permission denied
- **WHEN** the microphone permission is denied
- **THEN** capture does not start
- **AND** the app explains that the microphone permission is required
- **AND** the command field is unchanged

### Requirement: Glasses microphone source

With the glasses microphone selected, dictation SHALL capture raw audio from the connected glasses during a glasses session that the dictation itself starts and stops.

#### Scenario: Glasses ready
- **WHEN** glasses are connected and their audio permission is granted
- **THEN** the app shows a connecting state while the glasses session starts
- **AND** capture begins when the session is ready
- **AND** the session stops when the utterance ends

#### Scenario: Selected source unavailable
- **WHEN** the glasses microphone is selected but the glasses are disconnected or the audio permission is denied
- **THEN** capture does not start
- **AND** the app tells the user to connect glasses or grant access
- **AND** it does not silently fall back to the phone microphone

#### Scenario: Glasses audio unavailable in this build
- **WHEN** the installed build does not include glasses audio support
- **THEN** the glasses option is shown as unavailable
- **AND** phone dictation still works

### Requirement: Capture interruption

An interruption during capture SHALL stop the utterance without transcribing partial audio, and SHALL leave the command field unchanged.

#### Scenario: App backgrounded mid-capture
- **WHEN** the app leaves the foreground while capturing
- **THEN** capture stops
- **AND** no transcript is inserted
- **AND** the command field keeps its previous contents

#### Scenario: Audio focus lost
- **WHEN** another app takes audio focus during capture
- **THEN** capture stops
- **AND** the app reports why dictation ended

#### Scenario: User cancels
- **WHEN** the user cancels a capture or a glasses connect in progress
- **THEN** no transcript is inserted
- **AND** the command field keeps its previous contents

### Requirement: Capture feedback

While capturing, the app SHALL show a recording indicator and an input level meter, and SHALL NOT display a partial transcript.

#### Scenario: Capturing
- **WHEN** capture is active
- **THEN** a recording indicator and a level meter are visible
- **AND** the command field is not updated during capture

#### Scenario: Recognition running
- **WHEN** capture has ended and recognition is running
- **THEN** the app shows a transcribing status
- **AND** the microphone control is disabled until a result arrives

### Requirement: Recognition measurement

Each dictation SHALL measure its audio duration, recognition latency, real-time factor, audio source, model identifier, and the condition that ended the utterance.

#### Scenario: Successful dictation
- **WHEN** an utterance is transcribed
- **THEN** the measured values are available to the metrics surface

#### Scenario: Failed dictation
- **WHEN** recognition fails or no speech is detected
- **THEN** the measured values are still available
- **AND** the dictation result records no transcript rather than empty text

### Requirement: No retained audio

Captured audio SHALL be discarded once the utterance ends, except when the user explicitly runs the local evaluation harness.

#### Scenario: Normal dictation
- **WHEN** a dictation finishes, succeeds, or fails
- **THEN** the captured audio buffer is released
- **AND** no audio file is written to storage or included in a trace

#### Scenario: Evaluation harness
- **WHEN** the user explicitly runs the local evaluation harness
- **THEN** the harness may write audio and transcripts to an app-private location for scoring
- **AND** that output is excluded from traces and exports
