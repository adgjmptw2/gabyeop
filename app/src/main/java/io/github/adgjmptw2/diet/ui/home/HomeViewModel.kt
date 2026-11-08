package io.github.adgjmptw2.diet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.MealRepository
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.domain.CalorieTarget
import io.github.adgjmptw2.diet.domain.Calories
import io.github.adgjmptw2.diet.domain.Goal
import io.github.adgjmptw2.diet.domain.GoalProgress
import io.github.adgjmptw2.diet.domain.Profile
import io.github.adgjmptw2.diet.domain.WeightEntry
import io.github.adgjmptw2.diet.domain.MealEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val today: LocalDate,
    val profile: Profile,
    val progress: GoalProgress,
    val calories: CalorieTarget,
    val todayEntry: WeightEntry?,
    val todayKcal: Double = 0.0,
    val meals: List<MealEntry> = emptyList(),
    val weekChangeKg: Double? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    profiles: ProfileRepository,
    weights: WeightRepository,
    private val clock: Clock,
    meals: MealRepository,
) : ViewModel() {

    private val todayMeals = flow {
        while (true) {
            emit(LocalDate.now(clock))
            delay(30_000)
        }
    }.distinctUntilChanged().flatMapLatest { date ->
        meals.day(date).map { entries -> date to entries }
    }

    val state: StateFlow<HomeUiState?> =
        combine(profiles.profile.filterNotNull(), weights.entries, todayMeals) { profile, entries, day ->
            build(profile, entries, day.first, day.second.sumOf { it.kcal }).copy(meals = day.second)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        /** entries는 최신 날짜가 앞 */
        fun build(profile: Profile, entries: List<WeightEntry>, today: LocalDate, todayKcal: Double = 0.0): HomeUiState {
            val current = entries.firstOrNull { !it.date.isAfter(today) }?.weightKg ?: profile.startWeightKg
            val progress = Goal.progress(profile.startWeightKg, profile.targetWeightKg, current)
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val recorded = entries.filter { it.date <= today }.sortedByDescending { it.date }
            val baseline = recorded.firstOrNull { it.date < monday } ?: recorded.lastOrNull { it.date >= monday }
            val weekChange = if (recorded.any { it.date >= monday } && baseline != null &&
                recorded.firstOrNull()?.date != baseline.date) current - baseline.weightKg else null
            return HomeUiState(
                today = today,
                profile = profile,
                progress = progress,
                calories = Calories.target(profile, current, today, progress.reached),
                todayEntry = entries.firstOrNull { it.date == today },
                todayKcal = todayKcal,
                weekChangeKg = weekChange,
            )
        }
    }
}
