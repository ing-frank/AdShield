package com.adshield.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adshield.domain.model.StatsTotals
import com.adshield.domain.repository.TrafficRepository
import com.adshield.domain.usecase.ChartBar
import com.adshield.domain.usecase.StatsAggregator
import com.adshield.domain.usecase.StatsRange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

data class StatisticsUiState(
    val range: StatsRange = StatsRange.DAY,
    val totals: StatsTotals = StatsTotals(),
    val bars: List<ChartBar> = emptyList()
)

class StatisticsViewModel(private val trafficRepository: TrafficRepository) : ViewModel() {

    private val range = MutableStateFlow(StatsRange.DAY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatisticsUiState> = range.flatMapLatest { selected ->
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val from = StatsAggregator.rangeStart(selected, now, zone)
        trafficRepository.observeStatsSince(from).map { buckets ->
            StatisticsUiState(
                range = selected,
                totals = StatsTotals.of(buckets),
                bars = StatsAggregator.buildBars(selected, now, zone, buckets)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun selectRange(selected: StatsRange) {
        range.value = selected
    }
}
