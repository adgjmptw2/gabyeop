package io.github.adgjmptw2.diet.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers

@Composable
fun WaterControls(ml: Int, glassMl: Int, enabled: Boolean, onSubtract: () -> Unit,
    onAdd: () -> Unit, onEditGlass: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val buttons: @Composable () -> Unit = {
            TextButton(onClick = onSubtract, enabled = enabled && ml > 0, contentPadding = PaddingValues(0.dp)) {
                Text("− 한 잔", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onAdd, enabled = enabled && ml < 10_000, contentPadding = PaddingValues(0.dp)) {
                Text("+ 한 잔", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (compact) Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { buttons() }
        else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
        TextButton(onClick = onEditGlass, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
            Text("$glassMl ml", style = MaterialTheme.typography.labelSmall.tabularNumbers(), maxLines = 1)
            Spacer(Modifier.width(3.dp))
            Icon(painterResource(R.drawable.ic_edit), "한 잔 크기 수정", Modifier.size(12.dp))
        }
    }
}
