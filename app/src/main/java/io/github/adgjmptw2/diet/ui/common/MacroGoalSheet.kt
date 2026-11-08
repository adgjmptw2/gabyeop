package io.github.adgjmptw2.diet.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.MacroGoal
import io.github.adgjmptw2.diet.domain.MacroPreset
import io.github.adgjmptw2.diet.domain.macroGoal
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroGoalSheet(goals: MacroGoal, targetKcal: Int, onDismiss: () -> Unit, onSave: (MacroGoal) -> Unit) {
    val current = goals.resolved(targetKcal)
    val recommended = macroGoal(targetKcal)
    var carb by rememberSaveable { mutableStateOf(current.carbGrams.toString()) }
    var fat by rememberSaveable { mutableStateOf(current.fatGrams.toString()) }
    var protein by rememberSaveable { mutableStateOf(current.proteinGrams.toString()) }
    val values = listOf(carb, fat, protein)
    val suggestions = listOf(recommended.carbGrams, recommended.fatGrams, recommended.proteinGrams)
    val valid = values.all { it.toIntOrNull()?.let { grams -> grams in 0..1000 } == true }
    val edited = MacroGoal(carb.toIntOrNull(), protein.toIntOrNull(), fat.toIntOrNull())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("영양정보 목표", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MacroPreset.entries.forEach { preset ->
                    SuggestionChip(onClick = {
                        val filled = macroGoal(targetKcal, preset)
                        carb = filled.carbGrams.toString(); fat = filled.fatGrams.toString(); protein = filled.proteinGrams.toString()
                    }, label = { Text(preset.label) })
                }
            }
            listOf("탄수화물", "지방", "단백질").forEachIndexed { i, label ->
                Column {
                    OutlinedTextField(values[i], { raw ->
                        val clean = raw.filter(Char::isDigit).take(4)
                        when (i) { 0 -> carb = clean; 1 -> fat = clean; else -> protein = clean }
                    }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, suffix = { Text("g") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = values[i].isNotEmpty() && values[i].toIntOrNull()?.let { it in 0..1000 } != true,
                        supportingText = {
                            if (values[i].isNotEmpty() && values[i].toIntOrNull()?.let { it in 0..1000 } != true) Text("0~1,000 g")
                        })
                    TextButton(onClick = {
                        val suggested = suggestions[i].toString()
                        when (i) { 0 -> carb = suggested; 1 -> fat = suggested; else -> protein = suggested }
                    }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("추천 ${suggestions[i]} g", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("합계 ${edited.energyKcal.comma()} kcal · 하루 목표 ${targetKcal.comma()} kcal",
                style = MaterialTheme.typography.bodySmall.tabularNumbers())
            TextButton(onClick = { onSave(MacroGoal()) }) { Text("추천값으로 되돌리기") }
            Button(onClick = { onSave(edited) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("저장") }
        }
    }
}
