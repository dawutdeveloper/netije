package com.example.netije.presentation.screen.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.netije.R
import com.example.netije.domain.model.AppLanguage
import com.example.netije.domain.model.BackupUnit
import com.example.netije.domain.model.DarkModeConfig
import com.example.netije.domain.model.backupMillis
import com.example.netije.presentation.component.NetijeAvatar
import com.example.netije.presentation.component.SectionHeader
import com.example.netije.presentation.viewmodel.SettingsViewModel

import com.example.netije.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BackupPreset(val labelKey: Int, val number: Int, val unit: BackupUnit) {
    H1(R.string.backup_freq_1h, 1, BackupUnit.HOUR),
    W1(R.string.backup_freq_1w, 1, BackupUnit.WEEK),
    M1(R.string.backup_freq_1m, 1, BackupUnit.MONTH),
    CUSTOM(R.string.backup_freq_custom, 0, BackupUnit.HOUR);
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    scope.launch { vm.onSignIn(idToken) }
                }
            } catch (_: ApiException) { }
        }
    }

    fun onSignInClick() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        val client = GoogleSignIn.getClient(ctx, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    var showBackupDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 0.dp, bottom = 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                }
                item {
                    SectionHeader(stringResource(R.string.section_account))
                    Spacer(Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val user = state.user
                                if (user != null) {
                                    NetijeAvatar(
                                        initials = user.displayName.firstOrNull()?.uppercase().toString(),
                                        name = user.displayName,
                                        size = 48.dp,
                                        photoUrl = user.photoUrl
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user?.displayName
                                            ?: stringResource(R.string.account_placeholder),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = user?.email
                                            ?: stringResource(R.string.signin_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val interaction = remember { MutableInteractionSource() }
                                Text(
                                    text = if (user == null) {
                                        stringResource(R.string.sign_in_label)
                                    } else stringResource(R.string.sign_out_label),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable(
                                        onClick = {
                                            scope.launch {
                                                if (user == null) {
                                                    onSignInClick()
                                                } else {
                                                    runCatching {
                                                        val gso = GoogleSignInOptions.Builder(
                                                            GoogleSignInOptions.DEFAULT_SIGN_IN
                                                        ).build()
                                                        GoogleSignIn.getClient(ctx, gso).signOut()
                                                    }
                                                    vm.onSignOut()
                                                }
                                            }
                                        },
                                        interactionSource = interaction,
                                        indication = null
                                    )
                                )
                            }
                            HorizontalDivider()
                            var currencyExpanded by remember { mutableStateOf(false) }
                            val currencyOptions = listOf(
                                "TMT" to stringResource(R.string.currency_symbol),
                                "USD" to stringResource(R.string.currency_usd),
                                "EUR" to stringResource(R.string.currency_eur)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Payments,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        text = stringResource(R.string.settings_currency),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 62.dp)
                                ) {
                                    ExposedDropdownMenuBox(
                                        expanded = currencyExpanded,
                                        onExpandedChange = { currencyExpanded = it },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = currencyOptions.firstOrNull { it.first == state.settings.currencyCode }?.second
                                                ?: state.settings.currencyCode,
                                            onValueChange = {},
                                            readOnly = true,
                                            singleLine = true,
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded)
                                            },
                                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = currencyExpanded,
                                            onDismissRequest = { currencyExpanded = false },
                                            modifier = Modifier.exposedDropdownSize()
                                        ) {
                                            currencyOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt.second) },
                                                    onClick = {
                                                        vm.onCurrency(opt.first)
                                                        currencyExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    SectionHeader(stringResource(R.string.section_preferences))
                    Spacer(Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                                    .clickableNoRipple {
                                        val cur = state.settings.language
                                        val next = when (cur) {
                                            AppLanguage.RU -> AppLanguage.TM
                                            AppLanguage.TM -> AppLanguage.EN
                                            AppLanguage.EN -> AppLanguage.RU
                                            else -> AppLanguage.RU
                                        }
                                        vm.onLanguage(next)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = stringResource(R.string.settings_language),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = state.settings.language.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                                    .clickableNoRipple {
                                        val cur = state.settings.darkMode
                                        val next = when (cur) {
                                            DarkModeConfig.ON -> DarkModeConfig.OFF
                                            DarkModeConfig.OFF -> DarkModeConfig.SYSTEM
                                            DarkModeConfig.SYSTEM -> DarkModeConfig.ON
                                            else -> DarkModeConfig.SYSTEM
                                        }
                                        vm.onDarkMode(next)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.DarkMode,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = stringResource(R.string.settings_darkmode),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = when (state.settings.darkMode) {
                                        DarkModeConfig.ON -> stringResource(R.string.darkmode_short_on)
                                        DarkModeConfig.OFF -> stringResource(R.string.darkmode_short_off)
                                        DarkModeConfig.SYSTEM -> stringResource(R.string.darkmode_short_system)
                                        else -> "—"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item {
                    SectionHeader(stringResource(R.string.section_backup))
                    Spacer(Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            val freqNumber = state.settings.backupNumber
                            val freqUnit = state.settings.backupUnit
                            val freqSummary = if (freqNumber != null) {
                                val unitStr = when (freqUnit) {
                                    BackupUnit.HOUR -> stringResource(R.string.backup_unit_hour)
                                    BackupUnit.DAY -> stringResource(R.string.backup_unit_day)
                                    BackupUnit.WEEK -> stringResource(R.string.backup_unit_week)
                                    BackupUnit.MONTH -> stringResource(R.string.backup_unit_month)
                                }
                                "$freqNumber $unitStr"
                            } else stringResource(R.string.backup_freq_never)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                                    .clickableNoRipple { showBackupDialog = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_backup_export),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.backup_freq_current) + " " + freqSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showBackupDialog) {
            BackupFrequencyDialog(
                currentNumber = state.settings.backupNumber,
                currentUnit = state.settings.backupUnit,
                onDismiss = { showBackupDialog = false },
                onConfirm = { number, unit ->
                    scope.launch {
                        val millis = backupMillis(number, unit)
                        val next = if (millis != null) System.currentTimeMillis() + millis else null
                        vm.onBackupFrequency(number, unit, next)
                        val json = runCatching { vm.export() }.getOrElse { "" }
                        val savedPath = if (json.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val baseDir = File(
                                        ctx.getExternalFilesDir(null),
                                        "Netije${File.separator}backups"
                                    )
                                    baseDir.mkdirs()
                                    val stamp = SimpleDateFormat(
                                        "yyyyMMdd_HHmmss",
                                        Locale.US
                                    ).format(Date())
                                    val file = File(baseDir, "netije_backup_$stamp.json")
                                    file.writeText(json)
                                    vm.markBackupTakenNow()
                                    file.absolutePath
                                }.getOrNull()
                            }
                        } else null
                        val msg = if (savedPath != null) {
                            String.format(
                                ctx.getString(R.string.backup_saved_to),
                                savedPath
                            )
                        } else ctx.getString(R.string.toast_export_error)
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                    showBackupDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupFrequencyDialog(
    currentNumber: Int?,
    currentUnit: BackupUnit,
    onDismiss: () -> Unit,
    onConfirm: (Int, BackupUnit) -> Unit
) {
    val presetInitial = when {
        currentNumber == null -> BackupPreset.H1
        currentNumber == 1 && currentUnit == BackupUnit.HOUR -> BackupPreset.H1
        currentNumber == 1 && currentUnit == BackupUnit.WEEK -> BackupPreset.W1
        currentNumber == 1 && currentUnit == BackupUnit.MONTH -> BackupPreset.M1
        else -> BackupPreset.CUSTOM
    }
    var selectedPreset by remember { mutableStateOf(presetInitial) }
    var customNumber by remember { mutableStateOf((currentNumber ?: 2).toString()) }
    var customUnit by remember { mutableStateOf(currentUnit) }
    var unitExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.backup_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BackupPreset.values().forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickableNoRipple { selectedPreset = preset },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPreset == preset,
                            onClick = { selectedPreset = preset }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(preset.labelKey),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (selectedPreset == BackupPreset.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customNumber,
                            onValueChange = { v ->
                                customNumber = v.filter { it.isDigit() }.take(4)
                            },
                            label = { Text(stringResource(R.string.backup_custom_amount)) },
                            modifier = Modifier.width(130.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenuBox(
                            expanded = unitExpanded,
                            onExpandedChange = { unitExpanded = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = when (customUnit) {
                                    BackupUnit.HOUR -> stringResource(R.string.backup_unit_hour)
                                    BackupUnit.DAY -> stringResource(R.string.backup_unit_day)
                                    BackupUnit.WEEK -> stringResource(R.string.backup_unit_week)
                                    BackupUnit.MONTH -> stringResource(R.string.backup_unit_month)
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.backup_custom_unit)) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = unitExpanded
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = unitExpanded,
                                onDismissRequest = { unitExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.backup_unit_hour)) },
                                    onClick = {
                                        customUnit = BackupUnit.HOUR
                                        unitExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.backup_unit_day)) },
                                    onClick = {
                                        customUnit = BackupUnit.DAY
                                        unitExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.backup_unit_week)) },
                                    onClick = {
                                        customUnit = BackupUnit.WEEK
                                        unitExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.backup_unit_month)) },
                                    onClick = {
                                        customUnit = BackupUnit.MONTH
                                        unitExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val (n, u) = when (selectedPreset) {
                    BackupPreset.H1 -> 1 to BackupUnit.HOUR
                    BackupPreset.W1 -> 1 to BackupUnit.WEEK
                    BackupPreset.M1 -> 1 to BackupUnit.MONTH
                    BackupPreset.CUSTOM -> {
                        val nInt = customNumber.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        nInt to customUnit
                    }
                }
                onConfirm(n, u)
            }) {
                Text(
                    text = stringResource(R.string.save),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 18.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        onClick = onClick,
        interactionSource = MutableInteractionSource(),
        indication = null
    )
