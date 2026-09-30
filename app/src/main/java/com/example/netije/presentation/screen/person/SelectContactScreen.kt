package com.example.netije.presentation.screen.person

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.netije.R
import com.example.netije.domain.model.PersonStatus
import com.example.netije.presentation.component.BalanceAmountText
import com.example.netije.presentation.component.NetijeAvatar
import com.example.netije.presentation.component.NetijeFilterChip
import com.example.netije.presentation.component.NetijeSearchBar
import com.example.netije.presentation.component.PersonFAB
import com.example.netije.presentation.navigation.Routes
import com.example.netije.presentation.viewmodel.SelectContactViewModel
import com.example.netije.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectContactScreen(
    nav: NavController,
    onPersonSelected: (String, String) -> Unit,
    vm: SelectContactViewModel = hiltViewModel()
) {
    val persons by vm.state.collectAsState()
    val search by vm.search.collectAsState()
    val status by vm.statusFilter.collectAsState()
    var filterExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.contact_select_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            PersonFAB(
                onClick = { nav.navigate(Routes.NEW_PERSON) }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    NetijeFilterChip(
                        label = when (status) {
                            PersonStatus.ACTIVE -> stringResource(R.string.filter_active)
                            PersonStatus.ARCHIVED -> stringResource(R.string.filter_archived)
                            PersonStatus.SETTLED -> stringResource(R.string.settled_filter)
                            null -> stringResource(R.string.filter_all)
                        },
                        selected = true,
                        onClick = { filterExpanded = true }
                    )
                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false }
                    ) {
                        listOf(PersonStatus.ACTIVE, PersonStatus.ARCHIVED, null).forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (opt) {
                                            PersonStatus.ACTIVE -> stringResource(R.string.filter_active)
                                            PersonStatus.ARCHIVED -> stringResource(R.string.filter_archived)
                                            else -> stringResource(R.string.filter_all)
                                        }
                                    )
                                },
                                onClick = {
                                    vm.statusFilter.value = opt
                                    filterExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                NetijeSearchBar(
                    value = search,
                    onValueChange = { vm.search.value = it },
                    placeholder = stringResource(R.string.search_hint)
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 4.dp, bottom = 100.dp
                )
            ) {
                if (persons.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_persons_yet),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(persons, key = { it.person.id }) { pwt ->
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = {
                                    onPersonSelected(pwt.person.id, pwt.person.name)
                                    nav.popBackStack()
                                },
                                interactionSource = interaction,
                                indication = null
                            )
                            .padding(vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NetijeAvatar(
                                initials = pwt.person.initials,
                                name = pwt.person.name,
                                size = 48.dp,
                                photoUrl = pwt.person.avatarUrl
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pwt.person.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(4.dp))
                                BalanceAmountText(amount = pwt.balance)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
    }
}
