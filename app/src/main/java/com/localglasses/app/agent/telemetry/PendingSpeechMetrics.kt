package com.localglasses.app.agent.telemetry

/**
 * Holds the speech metrics of the last dictation until the command they produced is submitted.
 *
 * The metrics only describe the request when the command field still holds the dictated text,
 * so any manual edit drops them. That keeps a dictation followed by hand-editing from being
 * reported as a recognised request.
 */
class PendingSpeechMetrics {

    private var transcript: String? = null
    private var metrics: SpeechMetrics? = null

    val isPending: Boolean
        get() = metrics != null

    fun record(transcript: String, metrics: SpeechMetrics?) {
        this.transcript = transcript
        this.metrics = metrics
    }

    /** Called for every text change, including the one that carried the transcript. */
    fun onTextEdited(current: String) {
        if (transcript == null || current != transcript) clear()
    }

    /**
     * Returns the metrics for a request whose command is [command], and clears them.
     *
     * Returns null when the field no longer holds the dictated text.
     */
    fun consumeForRequest(command: String): SpeechMetrics? {
        val attached = if (transcript != null && command == transcript) metrics else null
        clear()
        return attached
    }

    fun clear() {
        transcript = null
        metrics = null
    }
}
