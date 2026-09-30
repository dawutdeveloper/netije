package com.example.netije.presentation.screen.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.netije.R
import com.example.netije.di.PickedContactHolder
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.TransactionType
import com.example.netije.presentation.component.AmountText
import com.example.netije.presentation.component.BalanceAmountText
import com.example.netije.presentation.component.ExpandablePersonCard
import com.example.netije.presentation.component.NetijeAvatar
import com.example.netije.presentation.component.NetijeFilterChip
import com.example.netije.presentation.component.NetijeSearchBar
import com.example.netije.presentation.component.PersonFAB
import com.example.netije.presentation.navigation.Routes
import com.example.netije.presentation.viewmodel.HomeViewModel
import com.example.netije.presentation.viewmodel.SettingsViewModel

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val searchQuery by vm.search.collectAsState()
    val settingsVm: SettingsViewModel = hiltViewModel()
    val settingsState by settingsVm.state.collectAsState()
    val filterExpanded = remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    var longPressPerson: PersonWithTransactions? by remember { mutableStateOf(null) }
    var confirmDeletePerson: Person? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val pickIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val uri: Uri? = data?.data
            if (uri != null) {
                val picked = loadContactFromUri(ctx, uri)
                if (picked != null) {
                    PickedContactHolder.set(picked)
                    nav.navigate(Routes.newPerson())
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pickIntentLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
        }
    }

    fun addFromContacts() {
        val perm = Manifest.permission.READ_CONTACTS
        val hasPerm = ContextCompat.checkSelfPermission(ctx, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false }
        if (hasPerm) {
            pickIntentLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
        } else {
            permissionLauncher.launch(perm)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { nav.navigate(Routes.SETTINGS) }
                    )
                ) {
                    val user = settingsState.user
                    if (user != null) {
                        NetijeAvatar(
                            initials = user.displayName.firstOrNull()?.uppercase().toString(),
                            name = user.displayName,
                            size = 40.dp,
                            photoUrl = user.photoUrl
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "A",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = user?.displayName ?: stringResource(R.string.signin_hint),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(18.dp))
                val currency = settingsState.settings.currencyCode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text(stringResource(R.string.summary_received), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        AmountText(
                            amount = state.summary.totalReceived,
                            type = TransactionType.RECEIVED,
                            style = MaterialTheme.typography.titleLarge,
                            currency = currency
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.summary_issued), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        AmountText(
                            amount = state.summary.totalIssued,
                            type = TransactionType.ISSUED,
                            style = MaterialTheme.typography.titleLarge,
                            currency = currency
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.summary_balance), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        BalanceAmountText(
                            amount = state.summary.balance,
                            style = MaterialTheme.typography.titleLarge,
                            currency = currency
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        NetijeFilterChip(
                            label = when (state.status) {
                                PersonStatus.ACTIVE -> stringResource(R.string.filter_active)
                                PersonStatus.ARCHIVED -> stringResource(R.string.filter_archived)
                                PersonStatus.SETTLED -> stringResource(R.string.settled_filter)
                                null -> stringResource(R.string.filter_all)
                            },
                            selected = true,
                            onClick = { filterExpanded.value = true }
                        )
                        DropdownMenu(
                            expanded = filterExpanded.value,
                            onDismissRequest = { filterExpanded.value = false }
                        ) {
                            listOf(PersonStatus.ACTIVE, PersonStatus.ARCHIVED, PersonStatus.SETTLED, null).forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (opt) {
                                                PersonStatus.ACTIVE -> stringResource(R.string.filter_active)
                                                PersonStatus.ARCHIVED -> stringResource(R.string.filter_archived)
                                                PersonStatus.SETTLED -> stringResource(R.string.settled_filter)
                                                else -> stringResource(R.string.filter_all)
                                            }
                                        )
                                    },
                                    onClick = {
                                        vm.statusFilter.value = opt
                                        filterExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    NetijeSearchBar(
                        value = searchQuery,
                        onValueChange = { vm.search.value = it }
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp, bottom = 120.dp
                )
            ) {
                if (state.persons.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.home_empty),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(state.persons, key = { it.person.id }) { pwt ->
                    ExpandablePersonCard(
                        pwt = pwt,
                        currency = settingsState.settings.currencyCode,
                        onEditTransaction = { tx ->
                            nav.navigate(Routes.newTransaction(transactionId = tx.id))
                        },
                        onTransactionClick = { t ->
                            nav.navigate(Routes.transactionDetail(t.id))
                        },
                        onCardClick = {
                            nav.navigate(Routes.personDetail(pwt.person.id))
                        },
                        onCardLongClick = { longPressPerson = pwt }
                    )
                }
            }
        }

        PersonFAB(
            onClick = { showSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 24.dp)
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        PickedContactHolder.set(null)
                        scope.launch { sheetState.hide() }.invokeOnCompletion { showSheet = false }
                        nav.navigate(Routes.newPerson())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_new_person), color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedButton(
                    onClick = { addFromContacts() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_from_contacts), color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    val lp = longPressPerson
    if (lp != null) {
        AlertDialog(
            onDismissRequest = { longPressPerson = null },
            title = { Text(lp.person.name) },
            text = {
                Column {
                    val statusRes = when (lp.person.status) {
                        PersonStatus.ACTIVE -> R.string.filter_active
                        PersonStatus.ARCHIVED -> R.string.filter_archived
                        PersonStatus.SETTLED -> R.string.settled_filter
                    }
                    Text(stringResource(statusRes))
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            vm.setPersonStatusById(lp.person.id, PersonStatus.ACTIVE)
                            longPressPerson = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.filter_active), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            vm.setPersonStatusById(lp.person.id, PersonStatus.ARCHIVED)
                            longPressPerson = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.filter_archived), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            vm.setPersonStatusById(lp.person.id, PersonStatus.SETTLED)
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
                            confirmDeletePerson = lp.person
                            longPressPerson = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.delete_person_btn),
                            color = com.example.netije.ui.theme.NegativeRed
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
                    Text(
                        stringResource(R.string.delete_confirm_btn),
                        color = com.example.netije.ui.theme.NegativeRed
                    )
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

private fun loadContactFromUri(ctx: android.content.Context, uri: Uri): Person? {
    val cr = ctx.contentResolver ?: return null
    val cursor = cr.query(uri, null, null, null, null) ?: return null
    var result: Person? = null
    cursor.use { c ->
        if (c.moveToFirst()) {
            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val lookupIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            val photoIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val id = if (!c.isNull(idIdx)) c.getString(idIdx) else null
            val name = if (!c.isNull(nameIdx)) c.getString(nameIdx) ?: "" else ""
            if (id == null || name.isBlank()) return null
            val lookup = if (!c.isNull(lookupIdx)) c.getString(lookupIdx) else null
            val photo = if (!c.isNull(photoIdx)) c.getString(photoIdx) else null
            val phones = mutableListOf<String>()
            val pCur = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                arrayOf(id),
                null
            )
            pCur?.use { p ->
                val numIdx = p.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (p.moveToNext()) {
                    val n = if (!p.isNull(numIdx)) p.getString(numIdx) else null
                    if (!n.isNullOrBlank()) phones.add(n.trim())
                }
            }
            val emails = mutableListOf<String>()
            val eCur = cr.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                arrayOf(id),
                null
            )
            eCur?.use { e ->
                val addrIdx = e.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (e.moveToNext()) {
                    val a = if (!e.isNull(addrIdx)) e.getString(addrIdx) else null
                    if (!a.isNullOrBlank()) emails.add(a.trim())
                }
            }
            val lookupUri = lookup?.let {
                ContactsContract.Contacts.getLookupUri(id.toLongOrNull() ?: 0L, it).toString()
            }
            result = Person(
                id = "",
                name = name,
                phones = phones,
                emails = emails,
                avatarUrl = photo,
                contactLookupUri = lookupUri,
                status = PersonStatus.ACTIVE
            )
        }
    }
    return result
}
