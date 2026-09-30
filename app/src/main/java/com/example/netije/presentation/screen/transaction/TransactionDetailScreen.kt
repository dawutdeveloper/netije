package com.example.netije.presentation.screen.transaction

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import com.example.netije.presentation.navigation.Routes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.netije.R
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import com.example.netije.presentation.component.AmountText
import com.example.netije.presentation.util.ReportExportHelper
import com.example.netije.presentation.util.ReportFormat
import com.example.netije.presentation.viewmodel.SettingsViewModel
import com.example.netije.presentation.viewmodel.TransactionDetailViewModel
import com.example.netije.ui.theme.NegativeRed
import com.example.netije.ui.theme.PositiveGreen
import com.example.netije.ui.theme.PrimaryBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    nav: NavController,
    vm: TransactionDetailViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val settings by settingsVm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currency = remember(state) {
        state.transaction?.currency
            ?.takeIf { it.isNotBlank() }
            ?: settings.settings.currencyCode.takeIf { it.isNotBlank() }
            ?: "TMT"
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        ?: Locale.getDefault()
    val dfDate = remember(locale) { SimpleDateFormat("dd MMM yyyy", locale) }
    val dfTime = remember(locale) { SimpleDateFormat("HH:mm", locale) }

    var exportMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val tx: Transaction? = state.transaction

    val exportSuccessMsg = stringResource(R.string.transaction_detail_export_success)
    val exportFailMsg = stringResource(R.string.transaction_detail_export_fail)
    val deletedMsg = stringResource(R.string.transaction_detail_deleted)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.transaction_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (state.loaded && !state.notFound && tx != null) {
                        IconButton(onClick = {
                            tx.id?.let { id ->
                                nav.navigate(Routes.newTransaction(transactionId = id))
                            }
                        }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.transaction_detail_edit_btn),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box {
                            IconButton(onClick = { exportMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = stringResource(R.string.transaction_detail_export_btn),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = exportMenuExpanded,
                                onDismissRequest = { exportMenuExpanded = false }
                            ) {
                                ReportFormat.values().forEach { fmt ->
                                    DropdownMenuItem(
                                        text = { Text(fmt.display) },
                                        onClick = {
                                            exportMenuExpanded = false
                                            scope.launch(Dispatchers.IO) {
                                                val (file, frmt) = vm.export(fmt)
                                                withContext(Dispatchers.Main) {
                                                    if (file != null && frmt != null) {
                                                        Toast.makeText(
                                                            context,
                                                            exportSuccessMsg,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        ReportExportHelper.shareFile(context, file, frmt)
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            exportFailMsg,
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_confirm_btn),
                                tint = NegativeRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { pad ->
        when {
            !state.loaded -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = PrimaryBlue) }

            state.notFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.transaction_detail_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            tx != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ===== Amount + Person summary card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Amount
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.transaction_detail_amount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AmountText(
                                amount = tx.amount,
                                type = tx.type,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                currency = currency
                            )
                        }
                        // Person
                        val person = state.person
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.transaction_detail_person),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (person != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryBlue),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = person.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = person.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (person.status == PersonStatus.SETTLED) {
                                        Spacer(Modifier.width(6.dp))
                                        AssistChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    stringResource(R.string.settled_filter),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = PositiveGreen,
                                                labelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Type chip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.transaction_detail_type),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val chipContainer = when (tx.type) {
                                TransactionType.RECEIVED -> PositiveGreen
                                TransactionType.ISSUED -> NegativeRed
                            }
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = stringResource(
                                            when (tx.type) {
                                                TransactionType.RECEIVED -> R.string.filter_received
                                                TransactionType.ISSUED -> R.string.filter_issued
                                            }
                                        ),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = chipContainer,
                                    labelColor = Color.White
                                )
                            )
                        }
                    }
                }

                // ===== Date / Time / Currency info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InfoRow(
                            label = stringResource(R.string.transaction_detail_date),
                            value = dfDate.format(java.util.Date(tx.date))
                        )
                        InfoRow(
                            label = stringResource(R.string.transaction_detail_time),
                            value = dfTime.format(java.util.Date(tx.time))
                        )
                        InfoRow(
                            label = stringResource(R.string.transaction_detail_currency),
                            value = currency
                        )
                    }
                }

                // ===== Purpose
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.transaction_detail_purpose),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tx.purpose?.ifBlank { null }
                                ?: stringResource(R.string.transaction_detail_no_purpose),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (tx.purpose.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ===== Note
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.transaction_detail_note),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = tx.note?.ifBlank { null }
                                ?: stringResource(R.string.transaction_detail_no_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (tx.note.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // ===== Attachments
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.transaction_detail_attachments),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (tx.attachments.isEmpty()) {
                            Text(
                                text = stringResource(R.string.transaction_detail_no_attachments),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(tx.attachments) { attPath ->
                                    AttachmentThumb(
                                        attPath = attPath,
                                        onClick = {
                                            openFile(context, attPath)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteDialog && tx != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.transaction_delete_title)) },
            text = { Text(stringResource(R.string.transaction_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch(Dispatchers.IO) {
                        val ok = vm.delete()
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    deletedMsg,
                                    Toast.LENGTH_SHORT
                                ).show()
                                nav.popBackStack()
                            }
                        }
                    }
                }) {
                    Text(
                        stringResource(R.string.delete_confirm_btn),
                        color = NegativeRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        stringResource(R.string.cancel_action),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AttachmentThumb(attPath: String, onClick: () -> Unit) {
    val file = File(attPath)
    val isImage = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp").any { ext ->
        attPath.substringAfterLast('.', "").lowercase() == ext
    }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isImage) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isImage && file.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = attPath.substringAfterLast('/').take(10) +
                            (if (attPath.length > 10) "…" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

private fun openFile(ctx: Context, attPath: String) {
    val file = File(attPath)
    if (!file.exists()) {
        Toast.makeText(ctx, "Not found: $attPath", Toast.LENGTH_LONG).show()
        return
    }
    val uri: Uri = runCatching {
        androidx.core.content.FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file
        )
    }.getOrElse { Uri.fromFile(file) }
    val mime = ctx.contentResolver.getType(uri)
        ?: guessMime(attPath.substringAfterLast('.', "").lowercase())
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = android.content.Intent.createChooser(intent, null)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(chooser) }
        .onFailure { Toast.makeText(ctx, it.message ?: "Open failed", Toast.LENGTH_LONG).show() }
}

private fun guessMime(ext: String): String = when (ext) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "txt" -> "text/plain"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls", "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    else -> "*/*"
}
