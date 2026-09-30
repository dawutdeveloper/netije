package com.example.netije.presentation.screen.reports

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.netije.R
import com.example.netije.domain.model.TransactionType
import com.example.netije.presentation.component.AmountText
import com.example.netije.presentation.component.BalanceAmountText
import com.example.netije.presentation.component.NetijeAvatar
import com.example.netije.presentation.component.TransactionRow
import com.example.netije.presentation.util.ReportFormat
import com.example.netije.presentation.viewmodel.ReportUserViewModel
import com.example.netije.ui.theme.NegativeRed
import com.example.netije.ui.theme.PositiveGreen
import com.example.netije.ui.theme.PrimaryBlue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportUserScreen(
    nav: NavController,
    vm: ReportUserViewModel = hiltViewModel(),
    settingsVm: com.example.netije.presentation.viewmodel.SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val persons by vm.persons.collectAsState()
    val settings by settingsVm.state.collectAsState()
    val ctx = LocalContext.current
    val cfg = LocalConfiguration.current
    val locale = cfg.locales[0] ?: Locale.getDefault()
    val df = remember(locale) { SimpleDateFormat("dd MMM yyyy", locale) }
    val currency = settings.settings.currencyCode
    var personExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(persons, vm.preselectedPersonId) {
        val cur = vm.personId.value
        if (cur == null && persons.isNotEmpty()) {
            vm.updatePerson(persons.first().person.id)
        }
    }

    fun showDatePicker(isFrom: Boolean) {
        val cal = Calendar.getInstance()
        val current = if (isFrom) state.fromDate else state.toDate
        cal.timeInMillis = current
        val dp = android.app.DatePickerDialog(
            ctx,
            { _, y, m, d ->
                cal.set(y, m, d, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (isFrom) vm.updateFrom(cal.timeInMillis) else vm.updateTo(cal.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        dp.show()
    }

    val userLabel = state.person?.name ?: if (persons.isEmpty()) {
        stringResource(R.string.no_persons_yet)
    } else {
        stringResource(R.string.no_selected_person)
    }
    val canExport = state.person != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.report_user_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            var showExport by remember { mutableStateOf(false) }
            Box {
                ExtendedFloatingActionButton(
                    onClick = { if (canExport) showExport = true },
                    containerColor = if (canExport) PrimaryBlue else MaterialTheme.colorScheme.outlineVariant,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    icon = {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    text = {
                        Text(
                            stringResource(R.string.export),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.report_user_label),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.width(8.dp))
                            ExposedDropdownMenuBox(
                                expanded = personExpanded,
                                onExpandedChange = { if (persons.isNotEmpty()) personExpanded = it },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = userLabel,
                                    onValueChange = { },
                                    readOnly = true,
                                    enabled = persons.isNotEmpty(),
                                    trailingIcon = {
                                        if (persons.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = personExpanded
                                        )
                                    },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = personExpanded,
                                    onDismissRequest = { personExpanded = false }
                                ) {
                                    persons.forEach { pwt ->
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(pwt.person.name) },
                                            onClick = {
                                                vm.updatePerson(pwt.person.id)
                                                personExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(
                                onClick = { showDatePicker(true) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(R.string.from_date) + " " + df.format(
                                            Date(state.fromDate)
                                        )
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = { showDatePicker(false) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        stringResource(R.string.to_date) + " " + df.format(
                                            Date(state.toDate)
                                        )
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.tx_received),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    com.example.netije.presentation.component.AmountText(
                                        amount = state.received,
                                        type = TransactionType.RECEIVED,
                                        currency = currency,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.tx_issued),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    com.example.netije.presentation.component.AmountText(
                                        amount = state.issued,
                                        type = TransactionType.ISSUED,
                                        currency = currency,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.summary_balance),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    BalanceAmountText(
                                        state.balance,
                                        currency = currency,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (state.transactions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.empty_transactions_period),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(state.transactions) { tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                state.person?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        NetijeAvatar(
                                            it.initials,
                                            it.name,
                                            size = 28.dp,
                                            photoUrl = it.avatarUrl
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            it.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    df.format(Date(tx.date)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TransactionRow(t = tx, currency = currency, purpose = tx.purpose)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
}
