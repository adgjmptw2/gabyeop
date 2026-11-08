package io.github.adgjmptw2.diet.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.WaterSettings
import io.github.adgjmptw2.diet.ui.common.comma
import io.github.adgjmptw2.diet.ui.meals.liters

@Composable
internal fun CalorieTargetDialog(state: RecordSettingsState, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var manual by rememberSaveable { mutableStateOf(state.calorieTarget != null) }
    var value by rememberSaveable { mutableStateOf((state.calorieTarget ?: state.recommendedKcal).toString()) }
    val parsed = value.toIntOrNull()
    val valid = parsed != null && parsed in 800..5000
    AlertDialog(onDismissRequest = onDismiss, title = { Text("하루 목표 칼로리") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioOption("권장량 사용 (${state.recommendedKcal.comma()} kcal)", !manual) { manual = false }
                RadioOption("직접 정하기", manual) { manual = true }
                OutlinedTextField(value, { value = it.filter(Char::isDigit).take(4) }, enabled = manual,
                    suffix = { Text("kcal") }, singleLine = true, label = { Text("목표") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = manual && !valid,
                    supportingText = {
                        when {
                            manual && !valid -> Text("800~5,000 kcal")
                            manual && parsed != null && parsed < state.minimumKcal ->
                                Text("권장 최소량(${state.minimumKcal.comma()} kcal)보다 낮아요")
                        }
                    })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (manual) parsed else null) },
            enabled = !manual || valid) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

@Composable
internal fun WaterSettingsDialog(state: RecordSettingsState, onDismiss: () -> Unit, onSave: (WaterSettings) -> Unit) {
    var glass by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = state.water.glassMl.toString()
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    var manual by rememberSaveable { mutableStateOf(state.water.targetMl != null) }
    var value by rememberSaveable { mutableStateOf((state.water.targetMl ?: state.automaticWaterMl).toString()) }
    val parsed = value.toIntOrNull()
    val valid = parsed != null && parsed in 250..10_000
    AlertDialog(onDismissRequest = onDismiss, title = { Text("물") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(glass, { next ->
                    if (next.text.length <= 4 && next.text.all(Char::isDigit)) glass = next
                }, label = { Text("한 잔 크기") }, suffix = { Text("ml") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !validCup(glass.text), supportingText = { if (!validCup(glass.text)) Text("50~2,000 ml") })
                RadioOption("자동 (${liters(state.automaticWaterMl)} L)", !manual) { manual = false }
                Text("체중 × 30 ml · 250 ml 단위 · 1.5~3.5 L", style = MaterialTheme.typography.labelSmall)
                RadioOption("직접 정하기", manual) { manual = true }
                OutlinedTextField(value, { value = it.filter(Char::isDigit).take(5) }, enabled = manual,
                    label = { Text("하루 목표") }, suffix = { Text("ml") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = manual && !valid,
                    supportingText = { if (manual && !valid) Text("250~10,000 ml") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(WaterSettings(glass.text.toInt(), if (manual) parsed else null)) },
            enabled = validCup(glass.text) && (!manual || valid)) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

private fun validCup(value: String): Boolean = value.toIntOrNull()?.let { it in 50..2000 } == true

@Composable
private fun RadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
