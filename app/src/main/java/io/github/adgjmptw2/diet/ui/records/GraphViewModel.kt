package io.github.adgjmptw2.diet.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.DayNoteRepository
import io.github.adgjmptw2.diet.data.FastRepository
import io.github.adgjmptw2.diet.data.MealRepository
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.data.settings.TrackingStore
import io.github.adgjmptw2.diet.data.steps.StepsSource
import io.github.adgjmptw2.diet.domain.*
import io.github.adgjmptw2.diet.ui.common.todayDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class GraphUiState(val metric: GraphMetric, val period: GraphPeriod, val from: LocalDate, val to: LocalDate,
    val target: Double, val summary: GraphSummary, val error: String? = null,
    val fasting: FastingGraphSummary? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GraphViewModel @Inject constructor(
    private val notes: DayNoteRepository, meals: MealRepository, steps: StepsSource,
    profiles: ProfileRepository, weights: WeightRepository, settings: TrackingStore, fasts: FastRepository, clock: Clock,
) : ViewModel() {
    private val selection = MutableStateFlow(GraphMetric.WATER to GraphPeriod.WEEK)
    val saveError = MutableStateFlow<String?>(null)
    private val targets = combine(profiles.profile, weights.entries, settings.water, steps.goal, todayDates(clock)) { p, entries, water, stepGoal, today ->
        val current = entries.firstOrNull { it.date <= today }?.weightKg ?: p?.startWeightKg ?: 65.0
        val kcal = p?.let { it.calorieTarget ?: Calories.target(it, current, today,
            Goal.progress(it.startWeightKg, it.targetWeightKg, current).reached).target } ?: 0
        Triple((water.targetMl ?: waterTargetMl(current)).toDouble(), kcal.toDouble(), stepGoal.toDouble())
    }
    val state: StateFlow<GraphUiState?> = combine(selection, todayDates(clock)) { chosen, today -> chosen to today }
        .flatMapLatest { (chosen, today) ->
            val (metric, period) = chosen
            val from = if (metric == GraphMetric.FASTING && period == GraphPeriod.MONTH) today.minusDays(30) else period.from(today)
            val graph: Flow<GraphUiState> = if (metric == GraphMetric.FASTING) {
                val ticks = flow {
                    while (true) {
                        val now = clock.millis()
                        emit(now)
                        delay(60_000 - Math.floorMod(now, 60_000L))
                    }
                }
                combine(fasts.entries, settings.fastingHours, ticks) { rows, hours, _ ->
                    val now = clock.millis()
                    val target = rows.filter { !it.deleted && it.endAt == null }
                        .maxWithOrNull(compareBy<Fast> { it.updatedAt }.thenBy { it.id })?.targetHours ?: hours
                    val summary = fastingGraph(rows, period, target, now, clock.zone)
                    GraphUiState(metric, period, from, today, target.toDouble(),
                        GraphSummary(summary.bars.map { GraphDay(it.date, it.hours) }, summary.averageHours, summary.achievedDays),
                        fasting = summary)
                }
            } else {
                val values: Flow<Map<LocalDate, Double>> = when (metric) {
                    GraphMetric.WATER -> combine(notes.notes(from, today), settings.waterRecordedDays) { days, recorded ->
                        dailyWaterAmounts(days, recorded, from, today)
                    }
                    GraphMetric.CALORIES -> meals.range(from, today).map(::dailyMealCalories)
                    GraphMetric.STEPS -> steps.daily(from, today).map { days -> days.mapValues { it.value.toDouble() } }
                    GraphMetric.FASTING -> error("Fasting uses its own graph")
                }
                combine(values, targets) { daily, goals ->
                    val target = when (metric) {
                        GraphMetric.WATER -> goals.first
                        GraphMetric.CALORIES -> goals.second
                        GraphMetric.STEPS -> goals.third
                        GraphMetric.FASTING -> error("Fasting uses its own target")
                    }
                    GraphUiState(metric, period, from, today, target,
                        trackingGraph(from, today, daily, target, atMostGoal = metric == GraphMetric.CALORIES))
                }
            }
            graph.catch { e ->
                if (e is CancellationException) throw e
                emit(GraphUiState(metric, period, from, today, 0.0, trackingGraph(from, today, emptyMap(), 0.0), "기록을 불러오지 못했어요"))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun select(metric: GraphMetric, period: GraphPeriod) {
        val allowed = if (metric == GraphMetric.FASTING) fastingGraphPeriods else
            listOf(GraphPeriod.WEEK, GraphPeriod.MONTH, GraphPeriod.THREE_MONTHS)
        selection.value = metric to (period.takeIf { it in allowed } ?: GraphPeriod.WEEK)
    }
    fun setWater(date: LocalDate, ml: Int) = viewModelScope.launch {
        try { saveError.value = null; notes.setWater(date, ml) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { saveError.value = "물을 저장하지 못했어요" }
    }
}
