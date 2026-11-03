package com.example.netije.presentation.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netije.R
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.ui.theme.NegativeRed
import com.example.netije.ui.theme.PositiveGreen

@Composable
fun HomeFinanceSummary(summary: FinanceSummary, currency: String, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val labels = listOf(
        stringResource(R.string.summary_received),
        stringResource(R.string.summary_issued),
        stringResource(R.string.summary_balance)
    )
    val amounts = listOf(
        "$currency +${String.format(locale, "%.2f", summary.totalReceived)}",
        "$currency -${String.format(locale, "%.2f", summary.totalIssued)}",
        "$currency ${if (summary.balance > 0) "+" else ""}${String.format(locale, "%.2f", summary.balance)}"
    )
    val colors = listOf(PositiveGreen, NegativeRed, when {
        summary.balance > 0 -> PositiveGreen
        summary.balance < 0 -> NegativeRed
        else -> MaterialTheme.colorScheme.onSurface
    })
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val amountStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columnWidthPx = with(density) { ((maxWidth - 16.dp) / 3).toPx() }.toInt().coerceAtLeast(1)
        // Fit the widest text, then use that same size for all three columns.
        // Measuring with the current density also accounts for the user's font scale.
        fun fittedStyle(texts: List<String>, style: TextStyle): TextStyle {
            fun fits(size: Float) = texts.all { text ->
                measurer.measure(text, style.copy(fontSize = size.sp), softWrap = false, maxLines = 1)
                    .size.width <= columnWidthPx
            }
            if (fits(style.fontSize.value)) return style
            var low = 0.5f
            var high = style.fontSize.value
            repeat(12) {
                val middle = (low + high) / 2
                if (fits(middle)) low = middle else high = middle
            }
            return style.copy(fontSize = low.sp)
        }
        val fittedLabels = remember(labels, columnWidthPx, labelStyle, density, measurer) {
            fittedStyle(labels, labelStyle)
        }
        val fittedAmounts = remember(amounts, columnWidthPx, amountStyle, density, measurer) {
            fittedStyle(amounts, amountStyle)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.indices.forEach { index ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        labels[index], modifier = Modifier.fillMaxWidth(),
                        style = fittedLabels, color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center, maxLines = 1, softWrap = false
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        amounts[index], modifier = Modifier.fillMaxWidth(),
                        style = fittedAmounts, color = colors[index],
                        textAlign = TextAlign.Center, maxLines = 1, softWrap = false
                    )
                }
            }
        }
    }
}
