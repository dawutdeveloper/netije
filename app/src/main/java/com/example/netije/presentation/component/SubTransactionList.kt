package com.example.netije.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.netije.R
import com.example.netije.domain.model.Transaction
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun SubTransactionList(children: List<Transaction>, original: Transaction, onClick: ((Transaction) -> Unit)? = null) {
    if (children.isEmpty()) return
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { SimpleDateFormat("dd.MM.yyyy", locale) }
    val timeFormatter = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.sub_transactions), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (listOf(original) + children).forEach { child ->
            Column(Modifier.fillMaxWidth().then(if (onClick != null && child.id != original.id) Modifier.clickable { onClick(child) } else Modifier)) {
                if (child.id == original.id) Text(
                    stringResource(R.string.initial_entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${dateFormatter.format(Date(child.date))} ${timeFormatter.format(Date(child.time))}", modifier = Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountText(child.amount, child.type, currency = "TMT", style = MaterialTheme.typography.titleMedium)
                }
                child.purpose?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                child.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (child.attachments.isNotEmpty()) Text(stringResource(R.string.sub_transaction_files, child.attachments.size), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
