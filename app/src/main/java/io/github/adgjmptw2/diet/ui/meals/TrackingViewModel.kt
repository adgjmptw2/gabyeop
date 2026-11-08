package io.github.adgjmptw2.diet.ui.meals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.DayNoteRepository
import io.github.adgjmptw2.diet.data.FastRepository
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.data.settings.TrackingStore
import io.github.adgjmptw2.diet.domain.Fast
import io.github.adgjmptw2.diet.domain.WaterSettings
import io.github.adgjmptw2.diet.domain.waterTargetMl
import io.github.adgjmptw2.diet.ui.common.todayDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class WaterUiState(val notes: Map<LocalDate, io.github.adgjmptw2.diet.domain.DailyNote> = emptyMap(),
    val settings: WaterSettings = WaterSettings(), val automaticTargetMl: Int = 2000)
data class FastingUiState(val active: Fast? = null, val targetHours: Int = 16)

@HiltViewModel
class TrackingViewModel @Inject constructor(
    private val notes: DayNoteRepository,
    private val fasts: FastRepository,
    private val settings: TrackingStore,
    profiles: ProfileRepository,
    weights: WeightRepository,
    private val clock: Clock,
) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val water = combine(notes.notes(), settings.water, profiles.profile, weights.entries, todayDates(clock)) { days, options, profile, entries, today ->
        val kg = entries.firstOrNull { it.date <= today }?.weightKg ?: profile?.startWeightKg ?: 65.0
        WaterUiState(days, options, waterTargetMl(kg))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WaterUiState())
    val fasting = combine(fasts.entries, settings.fastingHours) { rows, hours ->
        FastingUiState(rows.filter { it.endAt == null }.maxWithOrNull(compareBy<Fast> { it.updatedAt }.thenBy { it.id }), hours)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FastingUiState())
    fun adjustWater(date: LocalDate, delta: Int) = action { notes.adjustWater(date, delta) }
    fun selectTarget(hours: Int) = action { settings.setFastingHours(hours) }
    fun start() = action { fasts.start(fasting.value.targetHours) }
    fun finish() = action { fasts.finish() }
    fun now(): Long = clock.millis()
    fun notificationDenied() { error.value = "알림 권한이 꺼져 있어요" }
    fun changeStart(dayOffset: Long, hour: Int, minute: Int) = action {
        val active = fasting.value.active ?: return@action
        val date = Instant.ofEpochMilli(active.startAt).atZone(clock.zone).toLocalDate().plusDays(dayOffset)
        val selected = date.atTime(hour, minute).atZone(clock.zone).toInstant().toEpochMilli()
        if (selected > clock.millis()) { error.value = "시작 시각은 지금 이전으로 골라 주세요"; return@action }
        if (clock.millis() - selected > io.github.adgjmptw2.diet.domain.Fasting.MAX_DURATION_MS) {
            error.value = "시작 시각은 7일 이내로 골라 주세요"
            return@action
        }
        fasts.changeStart(selected)
    }
    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { error.value = null; block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "저장하지 못했어요" }
        }
    }
}
