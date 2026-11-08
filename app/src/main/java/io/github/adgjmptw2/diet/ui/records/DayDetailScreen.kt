package io.github.adgjmptw2.diet.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.domain.*
import io.github.adgjmptw2.diet.ui.common.MacroSummary
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.common.kg
import io.github.adgjmptw2.diet.ui.meals.liters
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import kotlin.math.roundToInt

@Composable
fun DayDetailScreen(state: DayDetailState, vm: RecordsViewModel,
    focusWeight: Boolean = false, onFocused: () -> Unit = {}) {
    key(state.date) { DayDetailContent(state, vm, focusWeight, onFocused) }
}

@Composable
private fun DayDetailContent(state: DayDetailState, vm: RecordsViewModel, focusWeight: Boolean, onFocused: () -> Unit) {
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val draft = drafts[state.date] ?: DayDraft()
    val weight = draft.weight ?: state.weightKg?.kg().orEmpty()
    val bodyFat = draft.bodyFat ?: state.bodyFatPct?.kg().orEmpty()
    val memo = draft.memo ?: state.memo
    val note = draft.note ?: state.note
    var tried by rememberSaveable(state.date) { mutableStateOf(false) }
    val save by vm.saveState.collectAsStateWithLifecycle()
    val status = save.takeIf { it.date == state.date }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusWeight) {
        if (focusWeight) {
            withFrameNanos { }
            requester.requestFocus()
            keyboard?.show()
            onFocused()
        }
    }
    val parsed = WeightRules.parse(weight)
    val fat = BodyMetrics.parseBodyFat(bodyFat)
    val validWeight = weight.isBlank() || parsed != null
    val validFat = bodyFat.isBlank() || (fat != null && parsed != null)
    val saving = status?.saving == true
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(weight, onValueChange = {
                    val value = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6)
                    vm.updateDraft(state.date) { edited ->
                        edited.copy(weight = value, bodyFat = if (value.isBlank()) "" else edited.bodyFat)
                    }
                }, label = { Text("체중") }, suffix = { Text("kg") }, singleLine = true, enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = tried && !validWeight,
                    supportingText = { if (tried && !validWeight) Text("30~300 kg 사이로 입력해 주세요") },
                    modifier = Modifier.fillMaxWidth().focusRequester(requester))
                if (state.weightId != null) TextButton(onClick = {
                    vm.deleteWeight(state.date, state.weightId)
                }, enabled = !saving) { Text("체중 삭제") }
                val profile = state.profile
                if (parsed != null && profile != null) {
                    val bmi = BodyMetrics.bmi(parsed, profile.heightCm)
                    Text("BMI ${bmi.kg()} · ${BodyMetrics.category(bmi)}", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                OutlinedTextField(bodyFat, onValueChange = {
                    val value = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)
                    vm.updateDraft(state.date) { edited -> edited.copy(bodyFat = value) }
                }, label = { Text("체지방률(선택)") }, suffix = { Text("%") }, singleLine = true,
                    enabled = !saving && parsed != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = tried && !validFat,
                    supportingText = {
                        Text(if (tried && !validFat) "체중과 함께 3~70%로 입력해 주세요" else "체지방계로 잰 값")
                    }, modifier = Modifier.fillMaxWidth())
                if (bodyFat.isBlank() && parsed != null && profile != null) {
                    val estimate = BodyMetrics.estimateBodyFat(BodyMetrics.bmi(parsed, profile.heightCm),
                        state.date.year - profile.birthYear, profile.sex)
                    Text("추정 약 ${estimate.roundToInt()}% (BMI·나이·성별로 계산, 실제와 다를 수 있어요)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(memo, {
                    val value = truncateToCodePoints(it.replace('\n', ' '), MEMO_MAX_LENGTH)
                    vm.updateDraft(state.date) { edited -> edited.copy(memo = value) }
                },
                    label = { Text("한 줄 메모") }, singleLine = true, enabled = !saving,
                    supportingText = { Text("${codePointLength(memo)}/$MEMO_MAX_LENGTH") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, {
                    val value = truncateToCodePoints(it, NOTE_MAX_LENGTH)
                    vm.updateDraft(state.date) { edited -> edited.copy(note = value) }
                },
                    label = { Text("자세한 메모") }, minLines = 3, maxLines = 8, enabled = !saving,
                    supportingText = { Text("${codePointLength(note)}/$NOTE_MAX_LENGTH") },
                    modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.clearNote(state.date) },
                    enabled = !saving && (memo.isNotBlank() || note.isNotBlank())) { Text("메모 삭제") }
                Button(onClick = {
                    tried = true
                    if (validWeight && validFat) vm.saveDay(state.date, parsed, memo, note, fat)
                }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(if (saving) "저장 중" else "체중·메모 저장") }
                val message = status?.error ?: if (status?.success == true) "저장했어요" else null
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (status?.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("물 ${liters(state.waterMl)} L", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.adjustWater(state.date, -state.glassMl) }, enabled = state.waterMl > 0) { Text("−") }
                TextButton(onClick = { vm.adjustWater(state.date, state.glassMl) }, enabled = state.waterMl < 10_000) { Text("+") }
            }
        }
        items(state.finishedFasts, key = { "fast-${it.id}" }) { fast ->
            Text("단식 ${Fasting.durationText(Fasting.elapsedMillis(fast.startAt, fast.endAt!!))}",
                style = MaterialTheme.typography.bodyMedium)
        }
        item { Text("그날 식단", style = MaterialTheme.typography.titleMedium) }
        if (state.meals.isEmpty()) item {
            Text("식단 기록 없음", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.meals, key = { it.id }) { meal ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(meal.emoji.ifBlank { "🍽" })
                Text(meal.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("${meal.kcal.roundToInt().comma()} kcal", style = MaterialTheme.typography.bodySmall.tabularNumbers())
            }
        }
        item {
            Text("하루 합계 ${Nutrition.total(state.meals).kcal.roundToInt().comma()} kcal",
                style = MaterialTheme.typography.titleSmall.tabularNumbers())
            MacroSummary(state.meals)
        }
    }
}
