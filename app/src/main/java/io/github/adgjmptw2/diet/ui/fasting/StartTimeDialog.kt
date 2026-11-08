package io.github.adgjmptw2.diet.ui.fasting

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.Fasting
import io.github.adgjmptw2.diet.ui.common.DayLabel
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 단식 시작 시각 고르기: 날짜(오늘부터 7일 전까지) + 시·분. 회전해도 고른 값이 남는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartTimeDialog(initial: Long, now: Long, zone: ZoneId, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val start = Instant.ofEpochMilli(initial).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    var dateEpoch by rememberSaveable { mutableLongStateOf(start.toLocalDate().toEpochDay()) }
    val date = LocalDate.ofEpochDay(dateEpoch)
    val time = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    val picked = date.atTime(time.hour, time.minute).atZone(zone).toInstant().toEpochMilli()
    val valid = Fasting.validStart(picked, now)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("단식 시작 시각") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { dateEpoch-- }, enabled = date > today.minusDays(7)) { Text("◀ 전날") }
                Text(if (date == today) "오늘" else date.format(DayLabel),
                    style = MaterialTheme.typography.titleSmall.tabularNumbers())
                TextButton(onClick = { dateEpoch++ }, enabled = date < today) { Text("다음날 ▶") }
            }
            TimeInput(time)
            Text(if (valid) "단식 ${Fasting.durationText(now - picked)} 지난 것으로 기록해요"
                else if (picked > now) "지금보다 늦은 시각은 고를 수 없어요" else "7일 안으로 골라 주세요",
                style = MaterialTheme.typography.bodySmall,
                color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onSave(picked) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
