package io.github.adgjmptw2.diet.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.*
import io.github.adgjmptw2.diet.data.settings.TrackingStore
import io.github.adgjmptw2.diet.domain.*
import io.github.adgjmptw2.diet.ui.common.todayDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.Instant
import javax.inject.Inject

data class RecordDay(val date: LocalDate, val weightKg: Double?, val meals: List<MealEntry>)

data class DayDetailState(
    val date: LocalDate, val weightKg: Double?, val weightId: String?,
    val memo: String, val note: String, val meals: List<MealEntry>,
    val bodyFatPct: Double? = null, val profile: Profile? = null,
    val waterMl: Int = 0, val glassMl: Int = 250, val finishedFasts: List<Fast> = emptyList(),
    val macroGoals: MacroGoal = MacroGoal(), val targetKcal: Int = 0, val today: LocalDate = date,
)

data class DaySaveState(val date: LocalDate? = null, val saving: Boolean = false,
    val success: Boolean = false, val error: String? = null)

/** Null follows stored data; even an empty string is an unsaved user edit. */
data class DayDraft(
    val weight: String? = null, val bodyFat: String? = null,
    val memo: String? = null, val note: String? = null,
)

data class RecordsUiState(
    val today: LocalDate, val weeks: List<WeekSummary>, val days: Map<LocalDate, RecordDay>,
    val weekCells: List<DayCell> = emptyList(), val notes: Map<LocalDate, DailyNote> = emptyMap(),
    val thisWeek: WeekSummary? = null, val weights: List<WeightEntry> = emptyList(),
    val profile: Profile? = null, val fasts: List<Fast> = emptyList(),
    val targetKcal: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordsViewModel @Inject constructor(
    private val weights: WeightRepository, private val meals: MealRepository,
    private val dayNotes: DayNoteRepository, profiles: ProfileRepository,
    private val tracking: TrackingStore, fasts: FastRepository,
    private val db: DietDatabase, private val clock: Clock,
) : ViewModel() {
    private val _saveState = MutableStateFlow(DaySaveState())
    val saveState = _saveState.asStateFlow()
    private val _drafts = MutableStateFlow<Map<LocalDate, DayDraft>>(emptyMap())
    val drafts = _drafts.asStateFlow()

    init {
        viewModelScope.launch {
            // DeviceDataCleaner removes the profile along with the saved records.
            profiles.profile.collect { if (it == null) _drafts.value = emptyMap() }
        }
    }

    fun updateDraft(date: LocalDate, change: (DayDraft) -> DayDraft) {
        if (_saveState.value.date == date && _saveState.value.saving) return
        _drafts.update { it + (date to change(it[date] ?: DayDraft())) }
    }

    private fun clearDraftFields(date: LocalDate, change: (DayDraft) -> DayDraft) {
        _drafts.update { drafts ->
            val cleared = drafts[date]?.let(change)
            if (cleared == null || cleared == DayDraft()) drafts - date else drafts + (date to cleared)
        }
    }
    private val datedMeals = todayDates(clock).flatMapLatest { today ->
        meals.range(LocalDate.of(1900, 1, 1), today).map { today to it }
    }
    val state: StateFlow<RecordsUiState?> = combine(
        weights.entries, datedMeals, dayNotes.notes(), profiles.profile, fasts.entries,
    ) { entries, dated, notes, profile, fastRows ->
        val (today, mealsForDays) = dated
        val dailyWeights = entries.filter { it.date <= today }.distinctBy { it.date }.associateBy { it.date }
        val dailyMeals = mealsForDays.groupBy { it.date }
        val days = (dailyWeights.keys + dailyMeals.keys + notes.keys).associateWith { date ->
            RecordDay(date, dailyWeights[date]?.weightKg, dailyMeals[date].orEmpty())
        }
        val summaries = weeks(entries, mealsForDays, today, notes.filterValues { it.hasTextNotes }.keys)
        val current = entries.firstOrNull { it.date <= today }?.weightKg ?: profile?.startWeightKg ?: 65.0
        val targetKcal = profile?.let { it.calorieTarget ?: Calories.target(it, current, today,
            Goal.progress(it.startWeightKg, it.targetWeightKg, current).reached).target } ?: 0
        RecordsUiState(today, summaries, days, weekCells(today, notes.values.toList()), notes,
            summaries.firstOrNull { today in it.start..it.end }, entries, profile, fastRows, targetKcal)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 달력에서 보고 있는 월·주 범위만 조회한다. 식단 없는 날은 키가 없다. */
    fun calendarCalories(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Double>> =
        meals.range(from, to).map(::dailyMealCalories)

    fun saveDay(date: LocalDate, weightKg: Double?, memo: String, note: String,
        bodyFatPct: Double? = null) {
        if (_saveState.value.saving || date > LocalDate.now(clock)) return
        _saveState.value = DaySaveState(date, saving = true)
        viewModelScope.launch {
            try {
                require(weightKg != null || bodyFatPct == null)
                db.withTransaction {
                    if (weightKg != null) weights.save(date, weightKg, bodyFatPct)
                    dayNotes.save(date, memo, note)
                }
                _drafts.update { it - date }
                _saveState.value = DaySaveState(date, success = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _saveState.value = DaySaveState(date, error = "저장하지 못했어요") }
        }
    }

    fun deleteWeight(date: LocalDate, id: String) {
        if (_saveState.value.saving || date > LocalDate.now(clock)) return
        _saveState.value = DaySaveState(date, saving = true)
        viewModelScope.launch {
            try {
                weights.delete(id)
                clearDraftFields(date) { it.copy(weight = null, bodyFat = null) }
                _saveState.value = DaySaveState(date, success = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _saveState.value = DaySaveState(date, error = "체중을 지우지 못했어요") }
        }
    }

    fun clearNote(date: LocalDate) {
        if (_saveState.value.saving) return
        _saveState.value = DaySaveState(date, saving = true)
        viewModelScope.launch {
            try {
                dayNotes.clear(date)
                clearDraftFields(date) { it.copy(memo = null, note = null) }
                _saveState.value = DaySaveState(date, success = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _saveState.value = DaySaveState(date, error = "메모를 지우지 못했어요") }
        }
    }

    fun adjustWater(date: LocalDate, delta: Int) = viewModelScope.launch {
        try { dayNotes.adjustWater(date, delta) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { _saveState.value = DaySaveState(date, error = "물을 저장하지 못했어요") }
    }
    fun setWater(date: LocalDate, ml: Int) = viewModelScope.launch {
        try { dayNotes.setWater(date, ml) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { _saveState.value = DaySaveState(date, error = "물을 저장하지 못했어요") }
    }

    fun setGlassMl(date: LocalDate, ml: Int) = viewModelScope.launch {
        try { tracking.setGlassMl(ml) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { _saveState.value = DaySaveState(date, error = "한 잔 크기를 저장하지 못했어요") }
    }

    fun setMacroGoals(date: LocalDate, goal: MacroGoal) = viewModelScope.launch {
        try { tracking.setMacroGoals(goal) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { _saveState.value = DaySaveState(date, error = "영양정보 목표를 저장하지 못했어요") }
    }

    fun dayDetail(date: LocalDate): Flow<DayDetailState> = combine(state.filterNotNull(), tracking.water, tracking.macroGoals) { s, water, goals ->
        val weight = s.weights.firstOrNull { it.date == date }
        val note = s.notes[date]
        val p = s.profile
        val current = s.weights.firstOrNull { it.date <= s.today }?.weightKg ?: p?.startWeightKg ?: 65.0
        val targetKcal = p?.let { it.calorieTarget ?: Calories.target(it, current, s.today,
            Goal.progress(it.startWeightKg, it.targetWeightKg, current).reached).target } ?: 0
        DayDetailState(date, weight?.weightKg, weight?.id, note?.memo.orEmpty(), note?.note.orEmpty(),
            s.days[date]?.meals.orEmpty(), weight?.bodyFatPct, s.profile, note?.waterMl ?: 0, water.glassMl,
            s.fasts.filter { it.endAt?.let { end -> Instant.ofEpochMilli(end).atZone(clock.zone).toLocalDate() == date } == true },
            goals, targetKcal, s.today)
    }
}
