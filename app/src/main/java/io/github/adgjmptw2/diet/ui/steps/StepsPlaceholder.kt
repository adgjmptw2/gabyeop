package io.github.adgjmptw2.diet.ui.steps

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.R

// 병합할 때 실제 걸음 화면과 타일을 남기고 이 파일을 지운다.
@Composable
fun StepsScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("걸음", style = MaterialTheme.typography.headlineLarge)
        Text("걸음 기록 준비 중", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StepsTile(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onOpen, modifier = modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("걸음", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(painterResource(R.drawable.ic_steps), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("—", style = MaterialTheme.typography.headlineMedium)
            Text("걸음 기록 준비 중", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
