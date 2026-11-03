package com.example.netije.presentation.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.presentation.component.FinanceSummaryContent

@Composable
fun HomeFinanceSummary(summary: FinanceSummary, currency: String, modifier: Modifier = Modifier) {
    FinanceSummaryContent(summary.totalReceived, summary.totalIssued, summary.balance, currency, modifier)
}
