package io.github.adgjmptw2.diet.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.MealRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.domain.MealEntry
import io.github.adgjmptw2.diet.domain.WeekSummary
import io.github.adgjmptw2.diet.domain.weeks
import io.github.adgjmptw2.diet.ui.common.todayDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class RecordDay(val date: LocalDate, val weightKg: Double?, val meals: List<MealEntry>)

data class RecordsUiState(
    val today: LocalDate,
    val weeks: List<WeekSummary>,
    val days: Map<LocalDate, RecordDay>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    weights: WeightRepository,
    meals: MealRepository,
    clock: Clock,
) : ViewModel() {
    private val datedMeals = todayDates(clock).flatMapLatest { today ->
        meals.range(LocalDate.MIN, today).map { today to it }
    }

    val state: StateFlow<RecordsUiState?> = combine(weights.entries, datedMeals) { entries, (today, meals) ->
        val dailyWeights = entries.filter { it.date <= today }.distinctBy { it.date }.associateBy { it.date }
        val dailyMeals = meals.groupBy { it.date }
        val days = (dailyWeights.keys + dailyMeals.keys).associateWith { date ->
            RecordDay(date, dailyWeights[date]?.weightKg, dailyMeals[date].orEmpty())
        }
        RecordsUiState(today, weeks(entries, meals, today), days)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
