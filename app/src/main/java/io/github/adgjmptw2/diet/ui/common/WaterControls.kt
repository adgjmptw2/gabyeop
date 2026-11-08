package io.github.adgjmptw2.diet.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.ui.theme.tabularNumbers

/** − 한 잔 · + 한 잔. compact면 한 잔 크기 버튼은 빼고(카드 머리에 [WaterGlassButton]을 둔다) */
@Composable
fun WaterControls(ml: Int, glassMl: Int, enabled: Boolean, onSubtract: () -> Unit,
    onAdd: () -> Unit, onEditGlass: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedIconButton(onClick = onSubtract, enabled = enabled && ml > 0,
            modifier = Modifier.size(40.dp).semantics { contentDescription = "물 한 잔 빼기" }) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        FilledTonalButton(onClick = onAdd, enabled = enabled && ml < 10_000, modifier = Modifier.weight(1f).height(40.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("+ 한 잔", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        if (!compact) WaterGlassButton(glassMl, enabled, onEditGlass)
    }
}

@Composable
fun WaterGlassButton(glassMl: Int, enabled: Boolean, onEditGlass: () -> Unit) {
    TextButton(onClick = onEditGlass, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.heightIn(min = 32.dp)) {
        Text("$glassMl ml", style = MaterialTheme.typography.labelMedium.tabularNumbers(), maxLines = 1)
        Spacer(Modifier.width(3.dp))
        Icon(painterResource(R.drawable.ic_edit), "한 잔 크기 수정", Modifier.size(12.dp))
    }
}
