package com.example.netije.presentation.screen.person

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.netije.R
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.TransactionType
import com.example.netije.presentation.component.AmountText
import com.example.netije.presentation.component.BalanceAmountText
import com.example.netije.presentation.component.NetijeAvatar
import com.example.netije.presentation.component.SectionHeader
import com.example.netije.presentation.component.TransactionRow
import com.example.netije.presentation.navigation.Routes
import com.example.netije.presentation.viewmodel.PersonDetailViewModel
import com.example.netije.presentation.viewmodel.SettingsViewModel
import com.example.netije.ui.theme.NegativeRed
import com.example.netije.ui.theme.PositiveGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    nav: NavController,
    vm: PersonDetailViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val settingsState by settingsVm.state.collectAsState()
    val cfg = LocalConfiguration.current
    val locale = cfg.locales[0] ?: Locale.getDefault()
    val df = remember(locale) { SimpleDateFormat("dd MMM yyyy", locale) }
    val currency = settingsState.settings.currencyCode
    var deleteDialog by remember { mutableStateOf(false) }
    val person = state.person

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.delete_person_title)) },
            text = { Text(stringResource(R.string.delete_person_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialog = false
                        vm.delete()
                        nav.popBackStack()
                    }
                ) { Text(stringResource(R.string.delete_confirm_btn), color = NegativeRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = person?.name ?: stringResource(R.string.person_title_placeholder),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    if (person?.firstPhone?.isNotBlank() == true) {
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${person.firstPhone}")
                            }
                            try {
                                nav.context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = stringResource(R.string.person_phone),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { deleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_person_btn),
                            tint = NegativeRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            if (person != null) {
                FloatingActionButton(
                    onClick = { nav.navigate(Routes.newTransaction(person.id)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp))
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
                            person?.let { p ->
                                NetijeAvatar(
                                    initials = p.initials,
                                    name = p.name,
                                    size = 60.dp,
                                    photoUrl = p.avatarUrl
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = p.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    p.firstPhone?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                                    p.firstEmail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(0.3f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.tx_received),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                AmountText(
                                    amount = state.received,
                                    type = TransactionType.RECEIVED,
                                    currency = currency,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Column(
                                modifier = Modifier.weight(0.3f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.tx_issued),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                AmountText(
                                    amount = state.issued,
                                    type = TransactionType.ISSUED,
                                    currency = currency,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Column(
                                modifier = Modifier.weight(0.4f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.summary_balance),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                BalanceAmountText(
                                    state.balance,
                                    currency = currency,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { vm.setStatus(PersonStatus.ACTIVE) },
                                label = { Text(stringResource(R.string.filter_active)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (person?.status == PersonStatus.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    labelColor = if (person?.status == PersonStatus.ACTIVE) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            AssistChip(
                                onClick = { vm.setStatus(PersonStatus.ARCHIVED) },
                                label = { Text(stringResource(R.string.filter_archived)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (person?.status == PersonStatus.ARCHIVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    labelColor = if (person?.status == PersonStatus.ARCHIVED) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            AssistChip(
                                onClick = { vm.setStatus(PersonStatus.SETTLED) },
                                label = { Text(stringResource(R.string.settled_filter)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (person?.status == PersonStatus.SETTLED) PositiveGreen else MaterialTheme.colorScheme.surface,
                                    labelColor = if (person?.status == PersonStatus.SETTLED) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                        if (person?.city?.isNotBlank() == true || person?.address?.isNotBlank() == true) {
                            Spacer(Modifier.height(10.dp))
                            person.city?.let {
                                Text("${stringResource(R.string.person_city_label)} $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            person.address?.let {
                                Text("${stringResource(R.string.person_address_label)} $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { SectionHeader(text = "${stringResource(R.string.transactions_header)} (${state.transactions.size})") }
            if (state.transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_transactions_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    df.format(Date(tx.date)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.weight(1f))
                                AmountText(
                                    tx.amount, tx.type,
                                    currency = currency,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            TransactionRow(
                                t = tx,
                                currency = currency,
                                purpose = tx.purpose,
                                onRowClick = { nav.navigate(com.example.netije.presentation.navigation.Routes.transactionDetail(tx.id)) }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
