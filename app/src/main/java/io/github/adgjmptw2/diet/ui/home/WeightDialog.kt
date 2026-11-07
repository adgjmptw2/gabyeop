package io.github.adgjmptw2.diet.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.WeightRules
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.common.kg
import java.time.LocalDate

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightDialog(
    today: LocalDate,
    initialDate: LocalDate,
    initialKg: Double?,
    dateLocked: Boolean,
    recordedDays: Set<LocalDate>,
    onSave: (LocalDate, Double) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialKg?.kg() ?: "") }
    var epochDay by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(epochDay)
    val parsed = WeightRules.parse(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialKg == null) "체중 기록" else "기록 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { picking = true }, enabled = !dateLocked) {
                    Text(date.format(DayLabel))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                    label = { Text("체중") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = tried && parsed == null,
                    supportingText = {
                        when {
                            tried && parsed == null -> Text("${WeightRules.MIN_KG.toInt()}~${WeightRules.MAX_KG.toInt()} kg 사이로 적어 주세요")
                            !dateLocked && date in recordedDays -> Text("이 날짜 기록을 바꿔요")
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tried = true
                parsed?.let { onSave(date, it) }
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )

    if (picking) {
        val todayMs = today.toEpochDay() * DAY_MS
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = epochDay * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { epochDay = it / DAY_MS }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) {
            DatePicker(picker)
        }
    }
}
