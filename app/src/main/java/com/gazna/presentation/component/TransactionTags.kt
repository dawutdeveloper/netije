package com.gazna.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gazna.R
import com.gazna.domain.model.normalizeTransactionTags

@Composable
fun TransactionTagsField(value: String, onValueChange: (String) -> Unit, enabled: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.transaction_tags)) },
        placeholder = { Text(stringResource(R.string.transaction_tags_placeholder)) },
        supportingText = { Text(stringResource(R.string.transaction_tags_hint)) },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun TransactionTags(tags: List<String>, modifier: Modifier = Modifier, onTagClick: ((String) -> Unit)? = null) {
    val normalized = normalizeTransactionTags(tags)
    if (normalized.isEmpty()) return
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        normalized.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = if (onTagClick != null) Modifier.clickable { onTagClick(tag) } else Modifier
            ) {
                Text(tag, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}
