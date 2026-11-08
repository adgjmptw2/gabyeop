package io.github.adgjmptw2.diet.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adgjmptw2.diet.domain.MEMO_MAX_LENGTH
import io.github.adgjmptw2.diet.domain.NOTE_MAX_LENGTH
import io.github.adgjmptw2.diet.domain.Nutrition
import io.github.adgjmptw2.diet.domain.WeightRules
import io.github.adgjmptw2.diet.domain.codePointLength
import io.github.adgjmptw2.diet.domain.truncateToCodePoints
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.common.kg
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import kotlin.math.roundToInt

@Composable
fun DayDetailScreen(state: DayDetailState, onBack: () -> Unit, vm: RecordsViewModel) {
    androidx.compose.runtime.key(state.date) {
        DayDetailContent(state, onBack, vm)
    }
}

@Composable
private fun DayDetailContent(state: DayDetailState, onBack: () -> Unit, vm: RecordsViewModel) {
    var weight by rememberSaveable(state.date) {
        mutableStateOf(state.weightKg?.kg().orEmpty())
    }
    var memo by rememberSaveable(state.date) { mutableStateOf(state.memo) }
    var note by rememberSaveable(state.date) { mutableStateOf(state.note) }
    var tried by rememberSaveable(state.date) { mutableStateOf(false) }
    val save by vm.saveState.collectAsStateWithLifecycle()
    val saveForThisDay = save.takeIf { it.date == state.date }
    // 마지막으로 화면에 반영한 값. 실제로 바뀐 칸만 덮어써 입력 중인 다른 칸을 지킨다.
    var loaded by remember(state.date) { mutableStateOf(LoadedFields(state.weightKg, state.memo, state.note)) }
    LaunchedEffect(state.weightKg, state.memo, state.note) {
        if (state.weightKg != loaded.weightKg) {
            weight = state.weightKg?.kg().orEmpty()
            loaded = loaded.copy(weightKg = state.weightKg)
        }
        if (state.memo != loaded.memo) {
            memo = state.memo
            loaded = loaded.copy(memo = state.memo)
        }
        if (state.note != loaded.note) {
            note = state.note
            loaded = loaded.copy(note = state.note)
        }
    }
    val parsed = WeightRules.parse(weight)
    val weightValid = weight.isBlank() || parsed != null
    val saving = saveForThisDay?.saving == true
    val saveError = saveForThisDay?.error
    val saveStatus = when {
        saving -> "저장 중"
        saveError != null -> saveError
        saveForThisDay?.success == true -> "저장했어요"
        else -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text("뒤로") }
                Text("하루 기록", style = MaterialTheme.typography.headlineLarge)
                Text(state.date.format(DayLabel), style = MaterialTheme.typography.bodyMedium.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("체중", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                    label = { Text("체중") }, suffix = { Text("kg") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = tried && !weightValid,
                    supportingText = { if (tried && !weightValid) Text("${WeightRules.MIN_KG.toInt()}~${WeightRules.MAX_KG.toInt()} kg 사이로 입력해 주세요") },
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineSmall.tabularNumbers(),
                )
                Text(
                    when {
                        state.weightId == null -> "체중 기록 없음"
                        else -> "체중을 비우고 저장하면 삭제해요"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = truncateToCodePoints(it.replace('\n', ' '), MEMO_MAX_LENGTH) },
                    label = { Text("한 줄 메모") }, singleLine = true,
                    supportingText = { Text("${codePointLength(memo)}/$MEMO_MAX_LENGTH") },
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = truncateToCodePoints(it, NOTE_MAX_LENGTH) },
                    label = { Text("자세한 메모") }, minLines = 4, maxLines = 10,
                    supportingText = { Text("${codePointLength(note)}/$NOTE_MAX_LENGTH") },
                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        vm.clearNote(state.date)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = !saving && (memo.isNotBlank() || note.isNotBlank() || state.memo.isNotBlank() || state.note.isNotBlank()),
                ) { Text("메모 삭제") }
            }
        }
        if (saveStatus != null) {
            item {
                Text(
                    saveStatus, style = MaterialTheme.typography.bodySmall,
                    color = if (saveError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            TextButton(
                onClick = {
                    tried = true
                    if (!weightValid) return@TextButton
                    vm.saveDay(state.date, parsed, state.weightId, memo, note)
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(if (saving) "저장 중" else "저장") }
        }
        item { Text("그날 식단", style = MaterialTheme.typography.titleMedium) }
        if (state.meals.isEmpty()) {
            item { Text("식단 기록 없음", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.meals, key = { it.id }) { meal ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(meal.emoji.ifBlank { "🍽" }, style = MaterialTheme.typography.titleMedium)
                    Text(meal.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${meal.kcal.roundToInt().comma()} kcal", style = MaterialTheme.typography.bodySmall.tabularNumbers(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "하루 합계 ${Nutrition.total(state.meals).kcal.roundToInt().comma()} kcal",
                    style = MaterialTheme.typography.titleSmall.tabularNumbers(), modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private data class LoadedFields(val weightKg: Double?, val memo: String, val note: String)
