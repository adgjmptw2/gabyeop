package io.github.adgjmptw2.diet.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.domain.Field
import io.github.adgjmptw2.diet.domain.Profile
import io.github.adgjmptw2.diet.domain.ProfileInput
import io.github.adgjmptw2.diet.domain.ProfileValidator
import io.github.adgjmptw2.diet.ui.common.kg
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    var input by mutableStateOf(ProfileInput())
        private set
    var errors by mutableStateOf<Map<Field, String>>(emptyMap())
        private set
    var saving by mutableStateOf(false)
        private set

    /** 화면에 들어올 때마다 저장된 값으로 채움(수정하다 취소한 값이 남지 않게) */
    fun load() {
        viewModelScope.launch {
            val p = profiles.profile.first()
            input = p?.let {
                ProfileInput(
                    sex = it.sex,
                    birthYear = it.birthYear.toString(),
                    heightCm = it.heightCm.kg().removeSuffix(".0"),
                    startWeightKg = it.startWeightKg.kg(),
                    targetWeightKg = it.targetWeightKg.kg(),
                    activity = it.activity,
                )
            } ?: ProfileInput()
            errors = emptyMap()
        }
    }

    fun update(next: ProfileInput) {
        input = next
        if (errors.isNotEmpty()) errors = ProfileValidator.validate(next, LocalDate.now(clock)).errors
    }

    fun submit(onDone: () -> Unit) {
        if (saving) return
        val today = LocalDate.now(clock)
        val result = ProfileValidator.validate(input, today)
        errors = result.errors
        val v = result.value ?: return
        saving = true
        viewModelScope.launch {
            try {
                val prev = profiles.profile.first()
                val profile = Profile(
                    sex = v.sex,
                    birthYear = v.birthYear,
                    heightCm = v.heightCm,
                    startWeightKg = v.startWeightKg,
                    targetWeightKg = v.targetWeightKg,
                    activity = v.activity,
                    startDate = prev?.startDate ?: today,
                )
                if (prev == null) profiles.createWithFirstWeight(profile) else profiles.save(profile)
                onDone()
            } finally {
                saving = false
            }
        }
    }
}
