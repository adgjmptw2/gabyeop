package io.github.adgjmptw2.diet.ui.records

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.domain.GraphMetric
import io.github.adgjmptw2.diet.ui.RecordSection
import io.github.adgjmptw2.diet.ui.progress.ProgressScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsHub(isActive: Boolean, request: Long, requestedSection: RecordSection, requestedMetric: GraphMetric,
    openTodayRequest: Long) {
    var section by rememberSaveable { mutableStateOf(if (request > 0) requestedSection else RecordSection.CALENDAR) }
    var handled by rememberSaveable { mutableLongStateOf(0) }
    val saved = rememberSaveableStateHolder()
    LaunchedEffect(request) { if (request > handled) { section = requestedSection; handled = request } }
    Column(Modifier.fillMaxSize()) {
        Text("기록", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            RecordSection.entries.forEachIndexed { index, item ->
                SegmentedButton(selected = section == item, onClick = { section = item },
                    shape = SegmentedButtonDefaults.itemShape(index, RecordSection.entries.size)) { Text(item.label) }
            }
        }
        Box(Modifier.weight(1f)) {
            saved.SaveableStateProvider(section.name) {
                when (section) {
                    RecordSection.CALENDAR -> RecordsScreen(isActive = isActive, openTodayRequest = openTodayRequest, showTitle = false)
                    RecordSection.GRAPHS -> GraphScreen(metricRequest = if (requestedSection == RecordSection.GRAPHS) request else 0,
                        requestedMetric = requestedMetric)
                    RecordSection.PROGRESS -> ProgressScreen(showTitle = false)
                }
            }
        }
    }
}
