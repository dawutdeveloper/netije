package com.example.netije.presentation.screen.transaction

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.netije.R
import com.example.netije.di.PickedPersonForTxHolder
import com.example.netije.domain.model.TransactionType
import com.example.netije.presentation.component.SegmentedTypeControl
import com.example.netije.presentation.navigation.Routes
import com.example.netije.presentation.util.rememberCameraCaptureLauncher
import com.example.netije.presentation.viewmodel.NewTransactionViewModel

import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTransactionScreen(
    nav: NavController,
    preselectedPersonId: String? = null,
    presetDate: Long? = null,
    vm: NewTransactionViewModel = hiltViewModel()
) {
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val scope = rememberCoroutineScope()
    val type by vm.type.collectAsState()
    val date by vm.date.collectAsState()
    val time by vm.time.collectAsState()
    val pid by vm.personId.collectAsState()
    val pName by vm.personName.collectAsState()
    val purpose by vm.purpose.collectAsState()
    val amount by vm.amount.collectAsState()
    val note by vm.note.collectAsState()
    val attachments by vm.attachments.collectAsState()
    val personsListState = vm.personsList.collectAsState()

    // Preselections from navigation arguments (applied once on first composition)
    LaunchedEffect(Unit) {
        if (preselectedPersonId != null && pid.isNullOrBlank()) {
            val list = personsListState.value
            val match = list.firstOrNull { it.person.id == preselectedPersonId }
            vm.preselectPerson(preselectedPersonId, match?.person?.name)
        }
        if (presetDate != null && presetDate > 0) {
            vm.presetDate(presetDate)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Consume picker result from SelectContactScreen after user picks a
    //  person and presses back (NavController popBackStack triggers recomposition).
    // ══════════════════════════════════════════════════════════════════════
    LaunchedEffect(pid) {
        PickedPersonForTxHolder.consume()?.let { (id, nm) ->
            if (id.isNotBlank()) {
                vm.preselectPerson(id, nm)
            }
        }
    }

    val dateFmt = remember(locale) { SimpleDateFormat("d MMM yyyy (EEE)", locale) }
    val timeFmt = remember { SimpleDateFormat("HH.mm", Locale.ROOT) }

    var showAttachSheet by remember { mutableStateOf(false) }
    val attachSheetState = rememberModalBottomSheetState()

    // ─── Attachment launchers ──────────────────────────────────────────
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.addAttachment(it.toString()) }
    }
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.addAttachment(it.toString())
        }
    }
    // Use the reliable FileProvider-backed camera helper (works on Xiaomi)
    val (attachmentCameraLauncher, _) = rememberCameraCaptureLauncher(
        onImageSaved = { uri -> vm.addAttachment(uri.toString()) },
        onFailed = { /* silent — user cancelled or no camera available */ }
    )

    // ─── Transaction presets (Назначение) ──────────────────────────────
    val customOption = stringResource(R.string.tx_write_purpose)
    val purposePresets = when (type) {
        TransactionType.RECEIVED -> listOf(
            stringResource(R.string.purpose_received_1),
            stringResource(R.string.purpose_received_2),
            stringResource(R.string.purpose_received_3),
            customOption
        )
        TransactionType.ISSUED -> listOf(
            stringResource(R.string.purpose_issued_1),
            stringResource(R.string.purpose_issued_2),
            stringResource(R.string.purpose_issued_3),
            customOption
        )
    }
    val selectedPreset = purposePresets.firstOrNull { it == purpose && it != customOption }
    val showCustomField = purpose.isBlank() || purpose == customOption || selectedPreset == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.new_tx_title),
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
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(4.dp))
                SegmentedTypeControl(
                    selected = type,
                    onSelect = { vm.type.value = it }
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.tx_date),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.tx_time),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ClickableField(
                        value = dateFmt.format(Date(date)),
                        leadingIcon = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                null, tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = date }
                            DatePickerDialog(
                                ctx,
                                { _, y, m, d ->
                                    val c = Calendar.getInstance().apply {
                                        set(y, m, d)
                                        val t = Calendar.getInstance().apply { timeInMillis = time }
                                        set(Calendar.HOUR_OF_DAY, t.get(Calendar.HOUR_OF_DAY))
                                        set(Calendar.MINUTE, t.get(Calendar.MINUTE))
                                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                    }
                                    vm.date.value = c.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ClickableField(
                        value = timeFmt.format(Date(time)),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Schedule,
                                null, tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = time }
                            TimePickerDialog(
                                ctx,
                                { _, h, min ->
                                    val c = Calendar.getInstance().apply { timeInMillis = date }
                                    c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min)
                                    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                                    vm.time.value = c.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY),
                                cal.get(Calendar.MINUTE),
                                true
                            ).show()
                        },
                        modifier = Modifier.weight(0.7f)
                    )
                }

                // ══════════════════════════════════════════════════════════════
                //  "Контактное лицо" field:
                //  - Hidden when coming from PersonDetail screen (preselectedPersonId != null)
                //  - Shown when coming from Calendar screen (date preset, person = null)
                //  - When user selects → person's name appears here INSTEAD of placeholder
                // ══════════════════════════════════════════════════════════════
                if (preselectedPersonId == null) {
                    Spacer(Modifier.height(20.dp))
                    val contactHint = stringResource(R.string.contact_person_hint)
                    LabeledRow(stringResource(R.string.contact_person_required)) {
                        val isSelected = !pid.isNullOrBlank()
                        val displayText = if (isSelected && !pName.isNullOrBlank()) {
                            pName!!
                        } else {
                            contactHint
                        }
                        ClickableField(
                            value = displayText,
                            placeholderHint = contactHint,
                            leadingIcon = if (isSelected) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pName.orEmpty().take(1).ifBlank { "?" }.uppercase(),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else null,
                            trailing = {
                                Icon(
                                    Icons.Default.KeyboardArrowRight, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            placeholderIfEmpty = !isSelected,
                            onClick = { nav.navigate(Routes.SELECT_CONTACT) }
                        )
                    }
                }

                // ══════════════════════════════════════════════════════════════
                //  Назначение: 4 presets + custom text field via "Напиши свое"
                // ══════════════════════════════════════════════════════════════
                Spacer(Modifier.height(20.dp))
                LabeledRow(stringResource(R.string.tx_purpose)) {
                    Column {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            purposePresets.forEach { preset ->
                                val isSelected = when {
                                    preset == customOption -> showCustomField
                                    else -> purpose == preset
                                }
                                val chipColors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (preset != customOption) {
                                            vm.purpose.value = preset
                                        } else {
                                            vm.purpose.value = customOption
                                        }
                                    },
                                    label = {
                                        Text(
                                            preset,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    colors = chipColors
                                )
                            }
                        }
                        if (showCustomField) {
                            Spacer(Modifier.height(10.dp))
                            NetijeTextField(
                                value = if (purpose == customOption) "" else purpose,
                                placeholder = stringResource(R.string.tx_purpose_hint),
                                onValueChange = { vm.purpose.value = it }
                            )
                        }
                    }
                }

                // ══════════════════════════════════════════════════════════════
                //  Сумма: only number keyboard
                // ══════════════════════════════════════════════════════════════
                Spacer(Modifier.height(20.dp))
                LabeledRow(stringResource(R.string.tx_amount)) {
                    NetijeTextField(
                        value = amount,
                        placeholder = "0.00",
                        onValueChange = { s -> vm.setAmount(s) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(0xFFCE1126)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(androidx.compose.ui.graphics.Color(0xFF008000))
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.currency_symbol),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                        }
                    )
                }
                Spacer(Modifier.height(20.dp))
                LabeledRow(stringResource(R.string.tx_note)) {
                    NetijeTextField(
                        value = note,
                        placeholder = stringResource(R.string.tx_note_hint),
                        minLines = 4,
                        onValueChange = { vm.note.value = it }
                    )
                }

                // ══════════════════════════════════════════════════════════════
                //  Вложения: clickable surface opens BottomSheet (3 options)
                // ══════════════════════════════════════════════════════════════
                Spacer(Modifier.height(20.dp))
                LabeledRow(stringResource(R.string.tx_attachments)) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    scope.launch {
                                        showAttachSheet = true
                                        attachSheetState.show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (attachments.isEmpty())
                                        stringResource(R.string.attach_files)
                                    else
                                        stringResource(R.string.attach_count, attachments.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (attachments.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                attachments.forEachIndexed { idx, uriStr ->
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val u = runCatching { Uri.parse(uriStr) }.getOrNull()
                                            if (u != null) {
                                                AsyncImage(
                                                    model = u,
                                                    contentDescription = null,
                                                    error = rememberVectorPainter(Icons.Default.BrokenImage),
                                                    fallback = rememberVectorPainter(Icons.Default.AttachFile),
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.InsertDriveFile,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { vm.removeAttachment(idx) },
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.TopEnd)
                                                .background(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = CircleShape
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.remove),
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(120.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val id = vm.save()
                            if (id != null) nav.popBackStack()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        stringResource(R.string.save),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Attachment BottomSheet: 3 options (photo / file / take picture)
    //  Camera uses rememberCameraCaptureLauncher which requests CAMERA
    //  permission first, then fires TakePicture(uri) via FileProvider,
    //  with TakePicturePreview as fallback — guaranteed to work on Xiaomi.
    // ══════════════════════════════════════════════════════════════════════
    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = attachSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    stringResource(R.string.attach_files),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.attach_photo),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch { attachSheetState.hide() }
                        showAttachSheet = false
                        pickImageLauncher.launch("image/*")
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.attach_file),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch { attachSheetState.hide() }
                        showAttachSheet = false
                        pickFileLauncher.launch(arrayOf("*/*"))
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.photo_take),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        scope.launch { attachSheetState.hide() }
                        showAttachSheet = false
                        attachmentCameraLauncher("attach")
                    }
                )
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(10.dp))
    content()
}

@Composable
private fun ClickableField(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    placeholderIfEmpty: Boolean = false,
    placeholderHint: String = ""
) {
    val interaction = remember { MutableInteractionSource() }
    val isPlaceholder = placeholderIfEmpty && (value.isBlank() || (placeholderHint.isNotBlank() && value == placeholderHint))
    val textColor = if (isPlaceholder)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                onClick = onClick,
                interactionSource = interaction,
                indication = null
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (trailing != null) trailing()
    }
}

@Composable
private fun NetijeTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = trailing,
        minLines = minLines,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions
    )
}
