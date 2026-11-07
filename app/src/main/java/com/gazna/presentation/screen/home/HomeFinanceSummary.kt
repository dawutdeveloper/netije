package com.gazna.presentation.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gazna.domain.model.FinanceSummary
import com.gazna.presentation.component.FinanceSummaryContent

@Composable
fun HomeFinanceSummary(summary: FinanceSummary, currency: String, modifier: Modifier = Modifier) {
    FinanceSummaryContent(summary.totalReceived, summary.totalIssued, summary.balance, currency, modifier, compact = true)
}
