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
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val locale = LocalConfiguration.current.locales[0]
    val labels = listOf(stringResource(if (compact) R.string.tx_received else R.string.summary_received),
        stringResource(if (compact) R.string.tx_issued else R.string.summary_issued), stringResource(R.string.summary_balance))
    val numbers = listOf(
        "+${String.format(locale, "%.2f", received)}",
        "-${String.format(locale, "%.2f", issued)}",
        "${if (balance > 0) "+" else ""}${String.format(locale, "%.2f", balance)}"
    )
    val amounts = numbers.map { if (compact) "$it $currency" else "$currency $it" }
    val colors = listOf(PositiveGreen, NegativeRed, when {
        balance > 0 -> PositiveGreen
        balance < 0 -> NegativeRed
        else -> MaterialTheme.colorScheme.onSurface
    })
    val labelStyle = MaterialTheme.typography.bodySmall
    val amountStyle = if (compact) MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        else MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnWidth = with(density) { ((maxWidth - 24.dp) / 3).toPx() }
        val fitsColumns = labels.indices.all { index ->
            measurer.measure(labels[index], labelStyle, softWrap = false).size.width <= columnWidth &&
                measurer.measure(if (compact) numbers[index] else amounts[index], amountStyle, softWrap = false).size.width <= columnWidth
        }
        if (fitsColumns) {
            val currencyOnOwnLine = compact && amounts.any {
                measurer.measure(it, amountStyle, softWrap = false).size.width > columnWidth
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                labels.indices.forEach { index ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(labels[index], style = labelStyle, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (currencyOnOwnLine) numbers[index] else amounts[index], style = amountStyle,
                            textAlign = TextAlign.Center, color = colors[index])
                        if (currencyOnOwnLine) Text(currency, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
