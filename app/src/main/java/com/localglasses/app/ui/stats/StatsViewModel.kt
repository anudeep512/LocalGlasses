package com.localglasses.app.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.localglasses.app.LocalGlassesApp
import com.localglasses.app.agent.telemetry.DeviceSnapshot
import com.localglasses.app.agent.trace.TraceDatabase
import com.localglasses.app.agent.trace.TraceMetricRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsState(
    val range: StatsRange = StatsRange.DAY,
    val rows: List<TraceMetricRow> = emptyList(),
    val summary: StatsSummary = StatsSummary(),
    val loading: Boolean = false,
)

class StatsViewModel @JvmOverloads constructor(
    app: Application,
    private val databaseOverride: TraceDatabase? = null,
) : AndroidViewModel(app) {

    private val container = (app as LocalGlassesApp).agentContainer
    private val database = databaseOverride ?: container.traceDatabase
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    val liveMetrics: Flow<DeviceSnapshot> = container.metricsCollector.stream()

    init {
        refresh()
    }

    fun setRange(range: StatsRange) {
        if (_state.value.range == range) return
        _state.update { it.copy(range = range, loading = true) }
        refresh()
    }

    fun refresh() {
        val range = _state.value.range
        viewModelScope.launch(Dispatchers.IO) {
            val since = if (range == StatsRange.ALL) 0L else {
                System.currentTimeMillis() - range.durationMs
            }
            val rows = database.metricsSince(since)
            _state.update {
                it.copy(
                    rows = rows,
                    summary = StatsAggregator.summarize(rows),
                    loading = false,
                )
            }
        }
    }
}
