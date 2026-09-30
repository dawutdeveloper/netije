package com.example.netije.presentation.screen.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.netije.R
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.TransactionType
import com.example.netije.presentation.component.AmountText
import com.example.netije.presentation.component.BalanceAmountText
import com.example.netije.presentation.component.NetijeAvatar
import com.example.netije.presentation.component.PlusFAB
import com.example.netije.presentation.component.TransactionRow
import com.example.netije.presentation.navigation.Routes
import com.example.netije.presentation.viewmodel.CalendarViewModel
import com.example.netije.presentation.viewmodel.SettingsViewModel
import com.example.netije.ui.theme.NegativeRed
import com.example.netije.ui.theme.PositiveGreen
import com.example.netije.ui.theme.PrimaryBlue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun startOfDay(date: Long): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

private fun formatWeekdayShort(dayOfWeek: Int, locale: Locale): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
    val fmt = SimpleDateFormat("EEE", locale)
    return fmt.format(cal.time).replaceFirstChar { if (it.isLowerCase()) it.uppercase(locale) else it.toString() }
}

private fun formatMonthStandalone(year: Int, month: Int, locale: Locale): String {
    val cal = Calendar.getInstance().apply { set(year, month, 1) }
    val fmt = SimpleDateFormat("LLLL yyyy", locale)
    return fmt.format(cal.time).replaceFirstChar { if (it.isLowerCase()) it.uppercase(locale) else it.toString() }
}

private fun formatDayFull(selectedDate: Long, locale: Locale): String {
    val fmt = SimpleDateFormat("d MMM yyyy", locale)
    return fmt.format(Date(selectedDate))
}

private fun formatWeekdayFull(selectedDate: Long, locale: Locale): String {
    val fmt = SimpleDateFormat("EEEE", locale)
    return fmt.format(Date(selectedDate)).replaceFirstChar { if (it.isLowerCase()) it.uppercase(locale) else it.toString() }
}

@Composable
fun CalendarScreen(
    nav: NavController,
    vm: CalendarViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val summary by vm.monthSummary.collectAsState()
    val groups by vm.dayGroups.collectAsState()
    val year by vm.year.collectAsState()
    val month by vm.month.collectAsState()
    val selected by vm.selectedDate.collectAsState()
    val settingsState by settingsVm.state.collectAsState()
    val currency = settingsState.settings.currencyCode
    var calendarExpanded by remember { mutableStateOf(true) }
    var longPressPerson: Person? by remember { mutableStateOf(null) }
    var confirmDeletePerson: Person? by remember { mutableStateOf(null) }

    val dayMap: Map<Long, Pair<Double, Double>> = groups.associate { d ->
        d.date to (d.totalReceived to d.totalIssued)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { vm.prevMonth() }) {
                        Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = formatMonthStandalone(year, month, locale),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { vm.nextMonth() }) {
                        Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.summary_received),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        AmountText(
                            summary.totalReceived, TransactionType.RECEIVED,
                            currency = currency,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.summary_issued),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        AmountText(
                            summary.totalIssued, TransactionType.ISSUED,
                            currency = currency,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.summary_balance),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        BalanceAmountText(summary.balance, currency = currency, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            AnimatedVisibility(visible = calendarExpanded) {
                CalendarGrid(
                    year = year,
                    month = month,
                    selectedDate = selected,
                    dayTotals = dayMap,
                    locale = locale,
                    onDateSelected = { vm.selectedDate.value = it }
                )
            }

            DayDetailsPanel(
                selectedDate = selected,
                groups = groups,
                locale = locale,
                currency = currency,
                calendarExpanded = calendarExpanded,
                onToggleCalendar = { calendarExpanded = !calendarExpanded },
                onAdd = { nav.navigate(Routes.newTransaction(date = selected)) },
                onEditTransaction = { t ->
                    nav.navigate(Routes.newTransaction(transactionId = t.id))
                },
                onTransactionClick = { t ->
                    nav.navigate(Routes.transactionDetail(t.id))
                },
                onPersonClick = { personId ->
                    nav.navigate(Routes.personDetail(personId))
                },
                onPersonLongClick = { person ->
                    longPressPerson = person
                }
            )
            Spacer(Modifier.height(120.dp))
        }

        PlusFAB(
            onClick = { nav.navigate(Routes.newTransaction(date = selected)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 24.dp)
        )
    }

    val lp = longPressPerson
    if (lp != null) {
        AlertDialog(
            onDismissRequest = { longPressPerson = null },
            title = { Text(lp.name) },
            text = {
                Column {
                    val statusRes = when (lp.status) {
                        PersonStatus.ACTIVE -> R.string.filter_active
                        PersonStatus.ARCHIVED -> R.string.filter_archived
                        PersonStatus.SETTLED -> R.string.settled_filter
                    }
                    Text(stringResource(statusRes))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            vm.setPersonStatusById(lp.id, PersonStatus.ACTIVE)
                            longPressPerson = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.filter_active), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            vm.setPersonStatusById(lp.id, PersonStatus.ARCHIVED)
                            longPressPerson = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.filter_archived), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            vm.setPersonStatusById(lp.id, PersonStatus.SETTLED)
                            longPressPerson = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.settled_filter), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            confirmDeletePerson = lp
                            longPressPerson = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.delete_person_btn),
                            color = NegativeRed
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { longPressPerson = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val dp = confirmDeletePerson
    if (dp != null) {
        AlertDialog(
            onDismissRequest = { confirmDeletePerson = null },
            title = { Text(stringResource(R.string.delete_person_title)) },
            text = { Text(stringResource(R.string.delete_person_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deletePersonById(dp.id)
                        confirmDeletePerson = null
                    }
                ) {
                    Text(stringResource(R.string.delete_confirm_btn), color = NegativeRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePerson = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    selectedDate: Long,
    dayTotals: Map<Long, Pair<Double, Double>>,
    locale: Locale,
    onDateSelected: (Long) -> Unit
) {
    val cal = Calendar.getInstance()
    cal.set(year, month, 1)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            for (dow in Calendar.SUNDAY..Calendar.SATURDAY) {
                Text(
                    text = formatWeekdayShort(dow, locale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val cells = mutableListOf<Int?>()
        repeat(firstDayOfWeek) { cells.add(null) }
        for (d in 1..daysInMonth) cells.add(d)
        while (cells.size % 7 != 0) cells.add(null)

        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                week.forEach { dayOfMonth ->
                    if (dayOfMonth == null) {
                        Spacer(modifier = Modifier.weight(1f).height(82.dp))
                    } else {
                        val c = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val dayKey = c.timeInMillis
                        val totals = dayTotals[dayKey]
                        val net = (totals?.first ?: 0.0) - (totals?.second ?: 0.0)
                        val isSel = startOfDay(selectedDate) == dayKey
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(82.dp)
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (isSel) Modifier
                                        .border(2.dp, PrimaryBlue, RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                    else Modifier
                                )
                                .clickable(
                                    onClick = { onDateSelected(dayKey) },
                                    interactionSource = interaction,
                                    indication = null
                                )
                                .padding(6.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayOfMonth.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(3.dp))
                                if (totals != null && (totals.first > 0 || totals.second > 0)) {
                                    val netColor = when {
                                        net > 0 -> PositiveGreen
                                        net < 0 -> NegativeRed
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    val sign = if (net > 0) "+" else if (net < 0) "-" else ""
                                    Text(
                                        text = "$sign${String.format("%.2f", kotlin.math.abs(net))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = netColor,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayDetailsPanel(
    selectedDate: Long,
    groups: List<com.example.netije.domain.model.DayTransactions>,
    locale: Locale,
    currency: String,
    calendarExpanded: Boolean,
    onToggleCalendar: () -> Unit,
    onAdd: () -> Unit,
    onEditTransaction: (com.example.netije.domain.model.Transaction) -> Unit,
    onTransactionClick: (com.example.netije.domain.model.Transaction) -> Unit,
    onPersonClick: (String) -> Unit,
    onPersonLongClick: (Person) -> Unit
) {
    val groupForDay = groups.firstOrNull { it.date == startOfDay(selectedDate) }
    val dayTotal = (groupForDay?.totalReceived ?: 0.0) - (groupForDay?.totalIssued ?: 0.0)
    val personExpanded = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = onToggleCalendar) {
                Icon(
                    imageVector = if (calendarExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            formatDayFull(selectedDate, locale),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            formatWeekdayFull(selectedDate, locale),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    BalanceAmountText(dayTotal, currency = currency, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(10.dp))
            }
            if (groupForDay == null || groupForDay.transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_data_period),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val personMap = groupForDay.personMap
                val groupedByPerson = groupForDay.transactions.groupBy { it.personId }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedByPerson.forEach { (pid, txs) ->
                        val person = personMap[pid]
                        val expanded = personExpanded.getOrPut(pid) { true }
                        androidx.compose.material3.Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (person != null) {
                                                Modifier.combinedClickable(
                                                    onClick = { onPersonClick(pid) },
                                                    onLongClick = { onPersonLongClick(person) }
                                                )
                                            } else Modifier
                                        )
                                ) {
                                    if (person != null) {
                                        NetijeAvatar(
                                            initials = person.initials,
                                            name = person.name,
                                            size = 44.dp,
                                            photoUrl = person.avatarUrl
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = person?.name
                                                ?: stringResource(R.string.contact_person_hint),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        val pRec = txs.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
                                        val pIss = txs.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
                                        BalanceAmountText(pRec - pIss, currency = currency)
                                    }
                                    androidx.compose.material3.IconButton(onClick = {
                                        personExpanded[pid] = !expanded
                                    }) {
                                        androidx.compose.material3.Icon(
                                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                                    Column {
                                        Spacer(Modifier.height(6.dp))
                                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                        txs.forEachIndexed { idx, t ->
                                            TransactionRow(
                                                t = t,
                                                currency = currency,
                                                purpose = t.purpose,
                                                onEditClick = { onEditTransaction(t) },
                                                onRowClick = { onTransactionClick(t) }
                                            )
                                            if (idx < txs.lastIndex) {
                                                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
