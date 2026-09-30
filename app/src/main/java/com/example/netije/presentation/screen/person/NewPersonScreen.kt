package com.example.netije.presentation.screen.person

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.netije.R
import com.example.netije.presentation.util.rememberCameraCaptureLauncher
import com.example.netije.presentation.viewmodel.NewPersonViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPersonScreen(nav: NavController, vm: NewPersonViewModel = hiltViewModel()) {
    val name by vm.name.collectAsState()
    val phones by vm.phones.collectAsState()
    val emails by vm.emails.collectAsState()
    val city by vm.city.collectAsState()
    val address by vm.address.collectAsState()
    val important by vm.isImportant.collectAsState()
    val avatarFromContact by vm.avatarFromContact.collectAsState()
    val customAvatarUrl by vm.customAvatarUrl.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    // ══════════════════════════════════════════════════════════════════════
    //  Gallery: pick from device
    // ══════════════════════════════════════════════════════════════════════
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            takePersistableReadPermission(ctx, it)
            vm.customAvatarUrl.value = it.toString()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Camera: use the helper that:
    //    - requests CAMERA permission first
    //    - uses TakePicture(fileUri) via FileProvider (reliable on Xiaomi)
    //    - falls back to TakePicturePreview if file-backed launch fails
    // ══════════════════════════════════════════════════════════════════════
    val (cameraLauncher, _) = rememberCameraCaptureLauncher(
        onImageSaved = { uri -> vm.customAvatarUrl.value = uri.toString() },
        onFailed = { /* user cancelled or no camera app — silent */ }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.new_person_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, null,
                            tint = MaterialTheme.colorScheme.onBackground,
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)
            ) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showBottomSheet = true }
                            )
                        ) {
                            val displayAvatar = customAvatarUrl ?: avatarFromContact
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (displayAvatar != null) {
                                        AsyncImage(
                                            model = displayAvatar,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(160.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Add,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(90.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 2.dp, y = 2.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PhotoCamera, null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }
                item { FormField(label = stringResource(R.string.person_name)) }
                item {
                    NetijeTextField(
                        value = name,
                        placeholder = stringResource(R.string.person_name_hint),
                        onValueChange = { vm.name.value = it }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item { FormField(label = stringResource(R.string.person_phone)) }
                itemsIndexed(phones) { i, phone ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            NetijeTextField(
                                value = phone,
                                placeholder = stringResource(R.string.person_phone_hint),
                                onValueChange = { vm.updatePhone(i, it) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                )
                            )
                        }
                        if (phones.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { vm.removePhone(i) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        val interaction = remember { MutableInteractionSource() }
                        Text(
                            text = stringResource(R.string.person_add_phone),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .clickable(
                                    onClick = { vm.addPhone() },
                                    interactionSource = interaction,
                                    indication = null
                                )
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                item { FormField(label = stringResource(R.string.person_email)) }
                itemsIndexed(emails) { i, email ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            NetijeTextField(
                                value = email,
                                placeholder = stringResource(R.string.person_email_hint),
                                onValueChange = { vm.updateEmail(i, it) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                )
                            )
                        }
                        if (emails.size > 1) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(onClick = { vm.removeEmail(i) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.remove),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        val interaction = remember { MutableInteractionSource() }
                        Text(
                            text = stringResource(R.string.person_add_email),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .clickable(
                                    onClick = { vm.addEmail() },
                                    interactionSource = interaction,
                                    indication = null
                                )
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                item { FormField(label = stringResource(R.string.person_city)) }
                item {
                    NetijeTextField(
                        value = city,
                        placeholder = stringResource(R.string.person_city_hint),
                        onValueChange = { vm.city.value = it }
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item { FormField(label = stringResource(R.string.person_address)) }
                item {
                    NetijeTextField(
                        value = address,
                        placeholder = stringResource(R.string.person_address_hint),
                        minLines = 3,
                        onValueChange = { vm.address.value = it }
                    )
                    Spacer(Modifier.height(18.dp))
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = important,
                            onCheckedChange = { vm.isImportant.value = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.person_important),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                }
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

            // ══════════════════════════════════════════════════════════════
            //  Photo source BottomSheet
            // ══════════════════════════════════════════════════════════════
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 36.dp)
                    ) {
                        Text(
                            stringResource(R.string.photo_action_choose),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        if (!sheetState.isVisible) {
                                            showBottomSheet = false
                                            galleryLauncher.launch("image/*")
                                        }
                                    }
                                }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.photo_gallery),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        if (!sheetState.isVisible) {
                                            showBottomSheet = false
                                            // Use the FileProvider + permission-aware launcher
                                            cameraLauncher("avatar")
                                        }
                                    }
                                }
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.photo_take),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun takePersistableReadPermission(ctx: Context, uri: Uri) {
    try {
        ctx.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: Exception) {
    }
}

@Composable
private fun FormField(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun NetijeTextField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
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
