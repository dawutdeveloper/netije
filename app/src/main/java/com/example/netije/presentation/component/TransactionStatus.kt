package com.example.netije.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.netije.R
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionStatus
import com.example.netije.presentation.viewmodel.TransactionActionsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun transactionStatusLabel(status: TransactionStatus): String = stringResource(when (status) {
    TransactionStatus.ACTIVE -> R.string.filter_active
    TransactionStatus.ARCHIVED -> R.string.filter_archived
    TransactionStatus.SETTLED -> R.string.filter_settled
})

@Composable
fun TransactionStatusFilter(selected: TransactionStatus?, onSelect: (TransactionStatus?) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<TransactionStatus?>(null) + TransactionStatus.entries).forEach { status ->
            FilterChip(selected = status == selected, onClick = { onSelect(status) }, label = {
                Text(status?.let { transactionStatusLabel(it) } ?: stringResource(R.string.filter_all))
            })
        }
    }
}

@Composable
fun TransactionStatusDialog(transaction: Transaction, onDismiss: () -> Unit, vm: TransactionActionsViewModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.transaction_status_change)) },
        text = {
            Column {
                Text(stringResource(R.string.transaction_status_hint))
                TransactionStatus.entries.forEach { status ->
                    OutlinedButton(enabled = !busy && status != transaction.status, modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true
                        scope.launch {
                            try {
                                vm.setStatus(transaction.id, status)
                                onDismiss()
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { failed = true }
                            finally { busy = false }
                        }
                    }) { Text(transactionStatusLabel(status)) }
                }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
