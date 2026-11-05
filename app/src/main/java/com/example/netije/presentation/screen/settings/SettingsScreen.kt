package com.example.netije.presentation.screen.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    var showLanguageDialog by remember { mutableStateOf(false) }

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
    var backupBusy by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var localBackups by remember { mutableStateOf<List<File>?>(null) }
    localBackups?.let { files ->
        AlertDialog(onDismissRequest = { localBackups = null }, title = { Text(stringResource(R.string.backup_local_files)) },
            text = {
                if (files.isEmpty()) Text(stringResource(R.string.backup_no_files))
                else LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(files.size) { index ->
                        val file = files[index]
                        TextButton(onClick = { localBackups = null; pendingImport = android.net.Uri.fromFile(file) }) {
                            Text(file.name)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { localBackups = null }) { Text(stringResource(R.string.cancel)) } })
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backupBusy = true
            scope.launch {
                try {
                    val json = vm.export()
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                            ?: error("Unable to write backup")
                    }
                    vm.markBackupTakenNow()
                    backupMessage = ctx.getString(R.string.backup_export_success)
                    val missing = com.example.netije.data.backup.BackupCodec.decode(json).missingFiles.size
                    if (missing > 0) backupMessage += "\n" + ctx.getString(R.string.backup_missing_files, missing)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { backupMessage = ctx.getString(R.string.toast_export_error) }
                finally { backupBusy = false }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    if (pendingImport != null) {
        AlertDialog(onDismissRequest = { if (!backupBusy) pendingImport = null },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = { Text(stringResource(R.string.backup_import_explanation)) },
            confirmButton = { TextButton(enabled = !backupBusy, onClick = {
                val uri = pendingImport ?: return@TextButton
                backupBusy = true
                scope.launch {
                    try {
                        val json = withContext(Dispatchers.IO) {
                            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                ?: error("Unable to read backup")
                        }
                        val count = vm.import(json)
                        pendingImport = null
                        backupMessage = ctx.getString(R.string.backup_import_success, count)
                        val document = com.example.netije.data.backup.BackupCodec.decode(json)
                        if (document.missingFiles.isNotEmpty()) backupMessage += "\n" + ctx.getString(R.string.backup_missing_files, document.missingFiles.size)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { pendingImport = null; backupMessage = ctx.getString(R.string.backup_import_failed) }
                    finally { backupBusy = false }
                }
            }) { Text(stringResource(R.string.backup_import)) } },
            dismissButton = { TextButton(enabled = !backupBusy, onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    backupMessage?.let { message ->
        AlertDialog(onDismissRequest = { backupMessage = null }, title = { Text(stringResource(R.string.section_backup)) },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { backupMessage = null }) { Text(stringResource(android.R.string.ok)) } })
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 22.dp, bottom = 100.dp
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
                                        showLanguageDialog = true
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
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_language),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = state.settings.language.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_darkmode),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
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
                                }
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
                    Spacer(Modifier.height(10.dp))
                    BackupActionCard(
                        title = stringResource(R.string.backup_import),
                        description = stringResource(R.string.backup_import_card_hint),
                        icon = Icons.Default.Restore,
                        enabled = !backupBusy,
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
                    )
                    Spacer(Modifier.height(12.dp))
                    BackupActionCard(
                        title = stringResource(R.string.backup_export_file),
                        description = stringResource(R.string.backup_export_card_hint),
                        icon = Icons.Default.SaveAlt,
                        enabled = !backupBusy,
                        onClick = { exportLauncher.launch("netije_backup_${System.currentTimeMillis()}.json") }
                    )
                    Spacer(Modifier.height(12.dp))
                    BackupActionCard(
                        title = stringResource(R.string.backup_local_files),
                        description = stringResource(R.string.backup_local_card_hint),
                        icon = Icons.Default.FolderOpen,
                        enabled = !backupBusy,
                        onClick = {
                            scope.launch {
                                localBackups = withContext(Dispatchers.IO) {
                                    val directories = listOf(
                                        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Netije/backups"),
                                        File(ctx.filesDir, "backups")
                                    )
                                    directories.flatMap { it.listFiles()?.toList().orEmpty() }
                                        .filter { it.isFile && it.extension == "json" }.sortedByDescending { it.lastModified() }
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    if (backupBusy) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                                    .clickableNoRipple { if (!backupBusy) showBackupDialog = true },
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
                                        text = stringResource(R.string.backup_auto_schedule),
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

        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.settings_language)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        AppLanguage.entries.forEach { language ->
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .clickableNoRipple {
                                        vm.onLanguage(language)
                                        showLanguageDialog = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.settings.language == language,
                                    onClick = {
                                        vm.onLanguage(language)
                                        showLanguageDialog = false
                                    }
                                )
                                Text(language.displayName, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showBackupDialog) {
            BackupFrequencyDialog(
                currentNumber = state.settings.backupNumber,
                currentUnit = state.settings.backupUnit,
                onDismiss = { showBackupDialog = false },
                onConfirm = { number, unit ->
                    backupBusy = true
                    scope.launch {
                        try {
                            val millis = backupMillis(number, unit)
                            vm.onBackupFrequency(number, unit, millis?.let { System.currentTimeMillis() + it }).join()
                            val json = vm.export()
                            withContext(Dispatchers.IO) {
                                val directory = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Netije/backups")
                                check(directory.isDirectory || directory.mkdirs())
                                File(directory, "netije_backup_${System.currentTimeMillis()}.json").writeText(json)
                            }
                            vm.markBackupTakenNow()
                            backupMessage = ctx.getString(R.string.backup_schedule_saved)
                            val missing = com.example.netije.data.backup.BackupCodec.decode(json).missingFiles.size
                            if (missing > 0) backupMessage += "\n" + ctx.getString(R.string.backup_missing_files, missing)
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { backupMessage = ctx.getString(R.string.toast_export_error) }
                        finally { backupBusy = false }
                    }
                    showBackupDialog = false
                }
            )
        }
    }
}

@Composable
private fun BackupActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.5f
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                    modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha))
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp))
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BackupPreset.values().forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = customNumber,
                            onValueChange = { v ->
                                customNumber = v.filter { it.isDigit() }.take(4)
                            },
                            label = { Text(stringResource(R.string.backup_custom_amount)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenuBox(
                            expanded = unitExpanded,
                            onExpandedChange = { unitExpanded = it },
                            modifier = Modifier.fillMaxWidth()
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
