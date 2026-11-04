package com.example.netije.presentation.screen.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.netije.R
import com.example.netije.presentation.component.ReportDateRange
import com.example.netije.presentation.component.AmountText
import com.example.netije.presentation.component.BalanceAmountText
import com.example.netije.presentation.component.NetijeAvatar
import com.example.netije.presentation.util.ReportFormat
import com.example.netije.presentation.viewmodel.ReportSummaryViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSummaryScreen(
    nav: NavController,
    vm: ReportSummaryViewModel = hiltViewModel(),
    settingsVm: com.example.netije.presentation.viewmodel.SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val settings by settingsVm.state.collectAsState()
    val ctx = LocalContext.current
    val currency = settings.settings.currencyCode

    fun showDatePicker(isFrom: Boolean) {
        val cal = Calendar.getInstance()
        val current = if (isFrom) state.fromDate else state.toDate
        cal.timeInMillis = current
        val dp = android.app.DatePickerDialog(
            ctx,
            { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
                if (isFrom) vm.updateFrom(cal.timeInMillis) else vm.updateTo(cal.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        dp.show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_summary_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.nav_back), tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            var showExport by remember { mutableStateOf(false) }
            Box {
                ExtendedFloatingActionButton(
                    onClick = { showExport = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    icon = { Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    text = { Text(stringResource(R.string.export), fontWeight = FontWeight.SemiBold) }
                )
                DropdownMenu(
                    expanded = showExport,
                    onDismissRequest = { showExport = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_pdf)) },
                        onClick = { showExport = false; vm.export(ReportFormat.PDF, currency) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_xlsx)) },
                        onClick = { showExport = false; vm.export(ReportFormat.XLSX, currency) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_csv)) },
                        onClick = { showExport = false; vm.export(ReportFormat.CSV, currency) }
                    )
                }
            }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        ReportDateRange(
                            from = state.fromDate,
                            to = state.toDate,
                            onFromClick = { showDatePicker(true) },
                            onToClick = { showDatePicker(false) }
                        )
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                        com.example.netije.presentation.component.FinanceSummaryContent(
                            state.totalReceived, state.totalIssued, state.totalBalance, currency
                        )
                    }
                }
            }
            if (state.rows.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.empty_data_period), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(state.rows) { row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NetijeAvatar(row.person.initials, row.person.name, size = 44.dp, photoUrl = row.person.avatarUrl)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                BalanceAmountText(row.balance, currency = currency, style = MaterialTheme.typography.titleMedium)
                                Text("${row.count} " + stringResource(R.string.report_operations_count), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                                Column {
                                    AmountText(row.received, com.example.netije.domain.model.TransactionType.RECEIVED, currency = currency, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(4.dp))
                                    AmountText(row.issued, com.example.netije.domain.model.TransactionType.ISSUED, currency = currency, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                        }
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}
