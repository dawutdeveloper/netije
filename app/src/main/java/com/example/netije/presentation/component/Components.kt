package com.example.netije.presentation.component

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.netije.R
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import com.example.netije.ui.theme.NegativeRed
import com.example.netije.ui.theme.PositiveGreen
import com.example.netije.ui.theme.PrimaryBlue
import com.example.netije.ui.theme.PrimaryBlueContainer

private val avatarColors = listOf(
    Color(0xFF2563EB), Color(0xFF0891B2), Color(0xFF059669),
    Color(0xFFD97706), Color(0xFFDC2626), Color(0xFF7C3AED),
    Color(0xFFDB2777), Color(0xFF475569), Color(0xFF0D9488)
)

private fun avatarColorFor(text: String): Color =
    avatarColors[text.fold(0) { acc, ch -> acc + ch.code } % avatarColors.size]

@Composable
fun NetijeAvatar(
    initials: String,
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    photoUrl: String? = null
) {
    val bg = avatarColorFor(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNullOrBlank()) {
            Text(
                text = initials.take(2),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.42f).sp,
                maxLines = 1
            )
        }
        if (photoUrl != null && photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(bg)
            )
        }
    }
}

@Composable
fun NetijeSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null
) {
    val resolvedPlaceholder = placeholder ?: stringResource(id = R.string.search_hint)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(text = resolvedPlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun NetijeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, fontWeight = FontWeight.SemiBold) },
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrimaryBlue,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = if (selected) null else FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun AmountText(
    amount: Double,
    type: TransactionType,
    currency: String = "TMT",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    fontWeight: FontWeight? = null
) {
    val isPositive = type == TransactionType.RECEIVED
    val sign = if (isPositive) "+" else "-"
    Text(
        text = "$currency $sign${"%.2f".format(amount)}",
        color = if (isPositive) PositiveGreen else NegativeRed,
        style = style,
        fontWeight = fontWeight ?: FontWeight.SemiBold
    )
}

@Composable
fun BalanceAmountText(
    amount: Double,
    currency: String = "TMT",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    fontWeight: FontWeight? = null
) {
    val sign = if (amount > 0) "+" else ""
    val color = when {
        amount > 0 -> PositiveGreen
        amount < 0 -> NegativeRed
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = "$currency $sign${"%.2f".format(amount)}",
        color = color,
        style = style,
        fontWeight = fontWeight ?: FontWeight.SemiBold
    )
}

@Composable
fun TransactionRow(
    t: Transaction,
    currency: String,
    purpose: String?,
    onEditClick: (() -> Unit)? = null,
    onRowClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onRowClick != null) Modifier.clickable(onClick = onRowClick) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val divider = androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(1.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.tx_price) + ":",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            AmountText(
                amount = t.amount,
                type = t.type,
                currency = currency,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(Modifier.width(12.dp))
        divider
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.tx_purpose) + ":",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = purpose.takeIf { !it.isNullOrBlank() } ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onEditClick != null) {
            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpandablePersonCard(
    pwt: PersonWithTransactions,
    currency: String,
    onEditTransaction: (Transaction) -> Unit,
    onTransactionClick: ((Transaction) -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
    onCardLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember(pwt.person.id) { mutableStateOf(false) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onCardClick != null || onCardLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onCardClick ?: {},
                        onLongClick = onCardLongClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                NetijeAvatar(
                    initials = pwt.person.initials,
                    name = pwt.person.name,
                    size = 44.dp,
                    photoUrl = pwt.person.avatarUrl
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pwt.person.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    BalanceAmountText(
                        amount = pwt.balance,
                        currency = currency,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(visible = expanded && pwt.transactions.isNotEmpty()) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    pwt.transactions.forEachIndexed { idx, t ->
                        TransactionRow(
                            t = t,
                            currency = currency,
                            purpose = t.purpose,
                            onEditClick = { onEditTransaction(t) },
                            onRowClick = { onTransactionClick?.invoke(t) }
                        )
                        if (idx < pwt.transactions.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(72.dp),
        containerColor = PrimaryBlue,
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    ) {
        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun PlusFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(72.dp),
        containerColor = PrimaryBlue,
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(34.dp))
    }
}

@Composable
fun SegmentedTypeControl(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    val receivedLabel = stringResource(id = R.string.tx_received_full)
    val issuedLabel = stringResource(id = R.string.tx_issued_full)
    val selectedBg = if (selected == TransactionType.RECEIVED) PositiveGreen else NegativeRed
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        SegmentedOption(
            label = receivedLabel,
            selected = selected == TransactionType.RECEIVED,
            selectedBg = PositiveGreen,
            onClick = { onSelect(TransactionType.RECEIVED) },
            modifier = Modifier.weight(1f)
        )
        SegmentedOption(
            label = issuedLabel,
            selected = selected == TransactionType.ISSUED,
            selectedBg = NegativeRed,
            onClick = { onSelect(TransactionType.ISSUED) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentedOption(
    label: String,
    selected: Boolean,
    selectedBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .padding(4.dp)
            .clip(shape)
            .clickable(
                onClick = onClick,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ),
        shape = shape,
        color = if (selected) selectedBg else Color.Transparent
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp)
        )
    }
}

@Composable
fun SettingsRowCard(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}
