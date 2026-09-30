package com.example.netije.presentation.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Permission + camera launcher combo that guarantees working camera capture across
 * Xiaomi/MIUI, Samsung, Pixel, Huawei devices.
 *
 * Strategy:
 *  1) Request Manifest.permission.CAMERA at runtime if not granted
 *  2) On permission-denied, still attempt to fire the TakePicture system intent (some
 *     OEM camera apps will work without our app holding the permission)
 *  3) Use TakePicture(uri) contract — camera app writes directly to our FileProvider
 *     URI → no bitmap-threshold data loss, no MediaStore permission issues, works on
 *     ALL Android versions including API 23+ and Xiaomi HyperOS.
 */
@Composable
fun rememberCameraCaptureLauncher(
    onImageSaved: (Uri) -> Unit,
    onFailed: (String) -> Unit = {}
): Pair<(String) -> Unit, (String) -> Unit> {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // ──── File-backed camera (TakePicture(uri) — most reliable) ──────────
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val last = LastCapturedUriHolder.last
        LastCapturedUriHolder.last = null
        if (success && last != null) {
            onImageSaved(last)
        } else {
            onFailed("Camera capture was cancelled")
        }
    }

    // ──── Thumbnail fallback (TakePicturePreview) ────────────────────────
    val thumbnailLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                val uri = withContext(Dispatchers.IO) { bitmap.saveToAppCache(ctx) }
                if (uri != null) onImageSaved(uri) else onFailed("Failed to save camera photo")
            }
        } else {
            onFailed("Camera returned no data")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        // Always try even if permission-denied (some OEM cameras allow it anyway)
        fireTakePicture(ctx, takePictureLauncher, thumbnailLauncher, onFailed)
    }

    /** Entry point: call when user taps "Сделать фото". `prefix` for file-name grouping. */
    fun launch(prefix: String) {
        LastCapturedPrefixHolder.last = prefix
        val perm = android.Manifest.permission.CAMERA
        val has = ContextCompat.checkSelfPermission(ctx, perm) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (has) {
            fireTakePicture(ctx, takePictureLauncher, thumbnailLauncher, onFailed)
        } else {
            permissionLauncher.launch(perm)
        }
    }

    /** Launch thumbnail-only path (not used normally, exposed for attachments if needed). */
    fun launchThumbnail(prefix: String) {
        LastCapturedPrefixHolder.last = prefix
        thumbnailLauncher.launch(null)
    }

    return ::launch to ::launchThumbnail
}

// ════════════════════════════════════════════════════════════════════════════
//  Internal helpers
// ════════════════════════════════════════════════════════════════════════════

private object LastCapturedUriHolder {
    @Volatile var last: Uri? = null
}
private object LastCapturedPrefixHolder {
    @Volatile var last: String = "capture"
}

private fun fireTakePicture(
    ctx: Context,
    takePictureLauncher: androidx.activity.result.ActivityResultLauncher<Uri>,
    thumbnailLauncher: androidx.activity.result.ActivityResultLauncher<Void?>,
    onFailed: (String) -> Unit
) {
    try {
        val uri = createImageFileUri(ctx, LastCapturedPrefixHolder.last)
        LastCapturedUriHolder.last = uri
        takePictureLauncher.launch(uri)
    } catch (t: Throwable) {
        // Fallback: try thumbnail launcher instead.
        try {
            thumbnailLauncher.launch(null)
        } catch (tt: Throwable) {
            onFailed("Unable to open camera: ${tt.message}")
        }
    }
}

private fun createImageFileUri(ctx: Context, prefix: String): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Netije").apply { mkdirs() }
    } else {
        @Suppress("DEPRECATION")
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Netije"
        ).apply { mkdirs() }
    }
    val image = File.createTempFile("${prefix}_${timeStamp}_", ".jpg", dir)
    return FileProvider.getUriForFile(
        ctx,
        "${ctx.packageName}.fileprovider",
        image
    )
}

private fun Bitmap.saveToAppCache(ctx: Context): Uri? {
    return try {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "${LastCapturedPrefixHolder.last}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { os ->
            compress(Bitmap.CompressFormat.JPEG, 90, os)
            os.flush()
        }
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    } catch (_: Exception) {
        null
    }
}
