package io.github.adgjmptw2.diet.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WaterDayLabel = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

@Composable
fun WaterAmountDialog(date: LocalDate, today: LocalDate, ml: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    key(date) {
        WaterMlDialog(if (date == today) "오늘 마신 물" else "${date.format(WaterDayLabel)} 마신 물",
            "마신 양", ml, 0..10_000, "0~10,000 ml", onDismiss, onSave)
    }
}

@Composable
fun WaterGlassSizeDialog(ml: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    WaterMlDialog("한 잔 크기", "한 잔 크기", ml, 50..2000, "50~2,000 ml", onDismiss, onSave)
}

@Composable
private fun WaterMlDialog(title: String, label: String, ml: Int, range: IntRange, hint: String,
    onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(ml.toString(), selection = TextRange(0, ml.toString().length)))
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
        keyboard?.show()
    }
    val parsed = value.text.toIntOrNull()
    val valid = parsed != null && parsed in range
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, { next ->
                if (next.text.length <= range.last.toString().length && next.text.all(Char::isDigit)) value = next
            }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text(label) }, suffix = { Text("ml") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !valid,
                supportingText = { if (!valid) Text(hint) })
        } },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = valid) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
