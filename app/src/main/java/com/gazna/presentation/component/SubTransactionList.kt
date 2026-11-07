package com.gazna.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gazna.R
import com.gazna.domain.model.Transaction
import com.gazna.domain.model.TransactionType
import com.gazna.ui.theme.NegativeRed
import com.gazna.ui.theme.PositiveGreen
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun SubTransactionList(children: List<Transaction>, original: Transaction, onClick: ((Transaction) -> Unit)? = null) {
    if (children.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("dd.MM.yyyy", locale) }
    val timeFormatter = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    val entries = listOf(original) + children
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(color = lineColor)
        Text(stringResource(R.string.sub_transactions), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Column {
            entries.forEachIndexed { index, entry ->
                val isOriginal = entry.id == original.id
                val canOpen = onClick != null && !isOriginal
                val received = entry.type == TransactionType.RECEIVED
                val amountColor = if (received) PositiveGreen else NegativeRed
                Row(
                    modifier = Modifier.fillMaxWidth().drawBehind {
                        if (index < entries.lastIndex) {
                            drawLine(lineColor, Offset(12.dp.toPx(), 12.dp.toPx()),
                                Offset(12.dp.toPx(), size.height), strokeWidth = 1.dp.toPx())
                        }
                    },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isOriginal) Icons.Default.ReceiptLong else if (received) Icons.Default.Add else Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (isOriginal) MaterialTheme.colorScheme.onSurface else amountColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).padding(bottom = if (index < entries.lastIndex) 12.dp else 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isOriginal) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, lineColor)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().then(
                                if (canOpen) Modifier.clickable(role = Role.Button,
                                    onClickLabel = stringResource(R.string.transaction_detail_title)) { onClick?.invoke(entry) }
                                else Modifier
                            ).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(if (isOriginal) R.string.initial_entry else if (received) R.string.tx_received_full else R.string.tx_issued_full),
                                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (canOpen) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                Text("${dateFormatter.format(Date(entry.date))} · ${timeFormatter.format(Date(entry.time))}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AmountText(entry.amount, entry.type, currency = "TMT", style = MaterialTheme.typography.titleMedium)
                            }
                            entry.purpose?.takeIf { it.isNotBlank() }?.let {
                                HistoryField(stringResource(R.string.tx_purpose), it)
                            }
                            entry.note?.takeIf { it.isNotBlank() }?.let {
                                HistoryField(stringResource(R.string.tx_note), it)
                            }
                            TransactionTags(entry.tags)
                            if (entry.attachments.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.sub_transaction_files, entry.attachments.size),
                                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
