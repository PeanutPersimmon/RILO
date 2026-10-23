package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Surface(Modifier.widthIn(max = 360.dp).testTag("compact_snackbar"),
            shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
            Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(data.visuals.message, Modifier.weight(1f, fill = false), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                data.visuals.actionLabel?.let { label ->
                    Box(Modifier.heightIn(min = 40.dp).clickable(onClick = data::performAction), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
