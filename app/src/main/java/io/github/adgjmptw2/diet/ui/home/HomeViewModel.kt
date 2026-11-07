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
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class RecentRow(val entry: WeightEntry, val diffKg: Double?)

data class HomeUiState(
    val today: LocalDate,
    val profile: Profile,
    val progress: GoalProgress,
    val calories: CalorieTarget,
    val todayEntry: WeightEntry?,
    val recent: List<RecentRow>,
    val recordedDays: Set<LocalDate>,
    val todayKcal: Double = 0.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    profiles: ProfileRepository,
    private val weights: WeightRepository,
    private val clock: Clock,
    meals: MealRepository,
) : ViewModel() {

    private val todayMeals = flow {
        while (true) {
            emit(LocalDate.now(clock))
            delay(30_000)
        }
    }.distinctUntilChanged().flatMapLatest { date ->
        meals.day(date).map { entries -> date to entries.sumOf { it.kcal } }
    }

    val state: StateFlow<HomeUiState?> =
        combine(profiles.profile.filterNotNull(), weights.entries, todayMeals) { profile, entries, day ->
            build(profile, entries, day.first, day.second)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(date: LocalDate, kg: Double) {
        viewModelScope.launch { weights.save(date, kg) }
    }

    fun delete(id: String) {
        viewModelScope.launch { weights.delete(id) }
    }

    companion object {
        const val RECENT_COUNT = 10

        /** entries는 최신 날짜가 앞 */
        fun build(profile: Profile, entries: List<WeightEntry>, today: LocalDate, todayKcal: Double = 0.0): HomeUiState {
            val current = entries.firstOrNull()?.weightKg ?: profile.startWeightKg
            val progress = Goal.progress(profile.startWeightKg, profile.targetWeightKg, current)
            val recent = entries.take(RECENT_COUNT).mapIndexed { i, e ->
                RecentRow(e, entries.getOrNull(i + 1)?.let { e.weightKg - it.weightKg })
            }
            return HomeUiState(
                today = today,
                profile = profile,
                progress = progress,
                calories = Calories.target(profile, current, today, progress.reached),
                todayEntry = entries.firstOrNull { it.date == today },
                recent = recent,
                recordedDays = entries.mapTo(HashSet()) { it.date },
                todayKcal = todayKcal,
            )
        }
    }
}
