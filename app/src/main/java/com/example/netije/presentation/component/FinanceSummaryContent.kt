package com.example.netije.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.netije.R
import com.example.netije.ui.theme.NegativeRed
import com.example.netije.ui.theme.PositiveGreen

/** Stack totals when columns would require smaller text, including at larger font scales. */
@Composable
fun FinanceSummaryContent(
    received: Double,
    issued: Double,
    balance: Double,
    currency: String,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    val labels = listOf(stringResource(R.string.summary_received),
        stringResource(R.string.summary_issued), stringResource(R.string.summary_balance))
    val amounts = listOf(
        "$currency +${String.format(locale, "%.2f", received)}",
        "$currency -${String.format(locale, "%.2f", issued)}",
        "$currency ${if (balance > 0) "+" else ""}${String.format(locale, "%.2f", balance)}"
    )
    val colors = listOf(PositiveGreen, NegativeRed, when {
        balance > 0 -> PositiveGreen
        balance < 0 -> NegativeRed
        else -> MaterialTheme.colorScheme.onSurface
    })
    val labelStyle = MaterialTheme.typography.bodySmall
    val amountStyle = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnWidth = with(density) { ((maxWidth - 24.dp) / 3).toPx() }
        val fitsColumns = labels.indices.all { index ->
            measurer.measure(labels[index], labelStyle, softWrap = false).size.width <= columnWidth &&
                measurer.measure(amounts[index], amountStyle, softWrap = false).size.width <= columnWidth
        }
        if (fitsColumns) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                labels.indices.forEach { index ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(labels[index], style = labelStyle, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(amounts[index], style = amountStyle, color = colors[index])
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                labels.indices.forEach { index ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(labels[index], style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(amounts[index], style = amountStyle, color = colors[index])
                    }
                }
            }
        }
    }
}
