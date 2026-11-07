package com.gazna.presentation.screen.reports

import androidx.compose.foundation.background
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazna.R
import com.gazna.domain.model.groupTransactions
import com.gazna.presentation.component.ReportDateRange
import com.gazna.domain.model.TransactionType
import com.gazna.presentation.component.AmountText
import com.gazna.presentation.component.BalanceAmountText
import com.gazna.presentation.component.NetijeAvatar
import com.gazna.presentation.component.NetijeFilterChip
import com.gazna.presentation.util.ReportFormat
import com.gazna.presentation.viewmodel.ReportTransactionsViewModel
import com.gazna.ui.theme.NegativeRed
import com.gazna.ui.theme.PositiveGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportTransactionsScreen(
    nav: NavController,
    vm: ReportTransactionsViewModel = hiltViewModel(),
    settingsVm: com.gazna.presentation.viewmodel.SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val search by vm.search.collectAsState()
    val allDates by vm.allDates.collectAsState()
    val typeFilter by vm.typeFilter.collectAsState()
    val persons by vm.persons.collectAsState()
    val personFilter by vm.personFilter.collectAsState()
    val statusFilter by vm.statusFilter.collectAsState()
    val settings by settingsVm.state.collectAsState()
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val df = remember(locale) { SimpleDateFormat("dd MMM yyyy", locale) }
    val currency = settings.settings.currencyCode
    var personExpanded by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.report_transactions_title), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge) },
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
                        com.gazna.presentation.component.NetijeSearchBar(
                            value = search, onValueChange = vm::setSearch,
                            placeholder = stringResource(R.string.search_transactions_tags)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.contact_person_hint) + ":", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            ExposedDropdownMenuBox(
                                expanded = personExpanded,
                                onExpandedChange = { personExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = persons.firstOrNull { it.person.id == personFilter }?.person?.name ?: stringResource(R.string.filter_all),
                                    onValueChange = { },
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = personExpanded,
                                    onDismissRequest = { personExpanded = false }
                                ) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.filter_all)) },
                                        onClick = { vm.setPerson(null); personExpanded = false }
                                    )
                                    persons.forEach { pwt ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(pwt.person.name) },
                                            onClick = { vm.setPerson(pwt.person.id); personExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NetijeFilterChip(stringResource(R.string.search_all_dates), allDates, { vm.allDates.value = !allDates })
                            if (allDates) {
                                androidx.compose.material3.TextButton(onClick = { vm.allDates.value = false }) {
                                    Text(stringResource(R.string.search_choose_dates))
                                }
                            }
                        }
                        if (!allDates) ReportDateRange(
                            from = state.fromDate, to = state.toDate,
                            onFromClick = { showDatePicker(true) }, onToClick = { showDatePicker(false) }
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NetijeFilterChip(stringResource(R.string.filter_all), typeFilter == null, { vm.setType(null) })
                            NetijeFilterChip(stringResource(R.string.tx_received), typeFilter == TransactionType.RECEIVED, { vm.setType(TransactionType.RECEIVED) })
                            NetijeFilterChip(stringResource(R.string.tx_issued), typeFilter == TransactionType.ISSUED, { vm.setType(TransactionType.ISSUED) })
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                        com.gazna.presentation.component.FinanceSummaryContent(
                            state.received, state.issued, state.balance, currency
                        )
                    }
                }
            }
            item { com.gazna.presentation.component.TransactionStatusFilter(statusFilter, { vm.statusFilter.value = it }) }
            if (state.transactions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (search.isBlank()) R.string.empty_transactions else R.string.search_no_transactions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(state.transactions.groupTransactions(), key = { it.transaction.id }) { group ->
                    val tx = group.transaction
                    val person = persons.firstOrNull { it.person.id == tx.personId }?.person
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                person?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        NetijeAvatar(it.initials, it.name, size = 28.dp, photoUrl = it.avatarUrl)
                                        Spacer(Modifier.width(8.dp))
                                        Text(it.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Text(df.format(Date(tx.date)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            com.gazna.presentation.component.TransactionRow(
                                t = tx, currency = currency, purpose = tx.purpose,
                                onRowClick = { nav.navigate(com.gazna.presentation.navigation.Routes.transactionDetail(tx.id)) },
                                subTransactions = group.children,
                                onSubTransactionClick = { nav.navigate(com.gazna.presentation.navigation.Routes.transactionDetail(it.id)) }, balanceLabelRes = R.string.period_balance
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}
