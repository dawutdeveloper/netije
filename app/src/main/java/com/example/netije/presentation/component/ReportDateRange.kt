package com.example.netije.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.netije.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportDateRange(from: Long, to: Long, onFromClick: () -> Unit, onToClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val formatter = remember(locale) { SimpleDateFormat("dd.MM.yyyy", locale) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(R.string.report_date_from, from, onFromClick),
            Triple(R.string.report_date_to, to, onToClick)
        ).forEach { (label, date, onClick) ->
            AssistChip(
                onClick = onClick,
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text(stringResource(label, formatter.format(Date(date)))) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
