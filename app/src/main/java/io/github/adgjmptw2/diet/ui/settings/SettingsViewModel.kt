package io.github.adgjmptw2.diet.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.BuildConfig
import io.github.adgjmptw2.diet.data.auth.AuthRepository
import io.github.adgjmptw2.diet.data.auth.AuthState
import io.github.adgjmptw2.diet.data.auth.ReminderSettings
import io.github.adgjmptw2.diet.data.auth.SignInResult
import io.github.adgjmptw2.diet.data.auth.TokenStore
import io.github.adgjmptw2.diet.data.export.CsvExporter
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.data.settings.SupplementSettings
import io.github.adgjmptw2.diet.data.WeightRepository
import io.github.adgjmptw2.diet.data.settings.TrackingStore
import io.github.adgjmptw2.diet.domain.Calories
import io.github.adgjmptw2.diet.domain.Goal
import io.github.adgjmptw2.diet.domain.WaterSettings
import io.github.adgjmptw2.diet.domain.MacroGoal
import io.github.adgjmptw2.diet.domain.waterTargetMl
import io.github.adgjmptw2.diet.ui.common.todayDates
import java.time.Clock
import io.github.adgjmptw2.diet.data.reminder.ReminderScheduler
import io.github.adgjmptw2.diet.data.sync.LoginRequiredException
import io.github.adgjmptw2.diet.data.sync.SyncRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val account: AuthState = AuthState(),
    val reminder: ReminderSettings = ReminderSettings(),
    val busy: Boolean = false,
    val message: String? = null,
    val records: RecordSettingsState = RecordSettingsState(),
    val splitSupplementsByTime: Boolean = true,
)

data class RecordSettingsState(
    val recommendedKcal: Int = 0, val calorieTarget: Int? = null, val minimumKcal: Int = 1200,
    val water: WaterSettings = WaterSettings(), val automaticWaterMl: Int = 2000,
    val macroGoals: MacroGoal = MacroGoal(),
)

sealed interface SettingsEvent {
    data class Share(val intent: Intent) : SettingsEvent
    data object Close : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val tokens: TokenStore,
    private val reminders: ReminderScheduler,
    private val csv: CsvExporter,
    private val profiles: ProfileRepository,
    weights: WeightRepository,
    private val tracking: TrackingStore,
    private val supplementSettings: SupplementSettings,
    sync: SyncRepository,
    clock: Clock,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val displayMessage = combine(message, sync.message, auth.state) { local, server, account ->
        server.takeIf { account.canSync } ?: local
    }
    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val googleAvailable = auth.googleSignInAvailable
    private val recordSettings = combine(profiles.profile, weights.entries, tracking.water, todayDates(clock), tracking.macroGoals) { p, entries, water, today, goals ->
        val current = entries.firstOrNull { it.date <= today }?.weightKg ?: p?.startWeightKg ?: 65.0
        val recommended = p?.let { Calories.target(it, current, today,
            Goal.progress(it.startWeightKg, it.targetWeightKg, current).reached).target } ?: 0
        RecordSettingsState(recommended, p?.calorieTarget, p?.let { Calories.minimum(it.sex) } ?: 1200,
            water, waterTargetMl(current), goals)
    }
    private val supplementView = combine(recordSettings, supplementSettings.splitByTime) { records, split ->
        records to split
    }
    val state = combine(supplementView, busy, auth.state, tokens.reminder, displayMessage) {
            view, working, account, reminder, error ->
        SettingsState(account, reminder, working, error, view.first, view.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun signIn(context: Context) = action {
        when (val result = auth.signInWithGoogle(context)) {
            is SignInResult.Failed -> message.value = result.message
            SignInResult.Cancelled -> Unit
            is SignInResult.Success -> if (!result.syncCompleted) message.value = "로그인했어요. 동기화는 연결 후 이어져요"
        }
    }

    fun signOut(keepLocal: Boolean) = action {
        auth.signOut(keepLocal)
        eventChannel.send(SettingsEvent.Close)
    }

    fun deleteAccount() = action {
        auth.deleteAccount()
        eventChannel.send(SettingsEvent.Close)
    }

    fun clearDeviceData() = action {
        auth.clearDeviceData()
        eventChannel.send(SettingsEvent.Close)
    }

    fun setReminderEnabled(enabled: Boolean) = action {
        reminders.update(tokens.reminder.first().copy(enabled = enabled))
    }

    fun setReminderTime(hour: Int, minute: Int) = action {
        reminders.update(tokens.reminder.first().copy(hour = hour, minute = minute))
    }

    fun notificationDenied() { message.value = "알림 권한이 꺼져 있어요" }
    fun shareFailed() { message.value = "공유할 앱을 열지 못했어요" }

    fun export() = action { eventChannel.send(SettingsEvent.Share(csv.shareIntent(csv.export()))) }
    fun setCalorieTarget(target: Int?) = action { profiles.setCalorieTarget(target) }
    fun setWater(settings: WaterSettings) = action { tracking.setWater(settings) }
    fun setMacroGoals(goal: MacroGoal) = action { tracking.setMacroGoals(goal) }
    fun setSplitSupplementsByTime(split: Boolean) = action { supplementSettings.setSplitByTime(split) }

    private fun action(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        message.value = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: LoginRequiredException) { message.value = "다시 로그인이 필요해요" }
            catch (_: Exception) { message.value = "완료하지 못했어요. 다시 시도해 주세요" }
            finally { busy.value = false }
        }
    }

    companion object {
        val privacyUrl: String get() = BuildConfig.API_BASE_URL.trim().trimEnd('/').let {
            if (it.isEmpty()) "" else "$it/privacy"
        }
    }
}
