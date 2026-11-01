package com.example.netije.presentation.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object TransactionAttachments {
    fun uri(path: String): Uri {
        val parsed = Uri.parse(path)
        return if (parsed.scheme == null) Uri.fromFile(File(path)) else parsed
    }

    fun name(context: Context, path: String): String {
        val source = uri(path)
        if (source.scheme == "content") {
            runCatching {
                context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()?.let { return it }
        }
        return source.lastPathSegment ?: "attachment"
    }

    fun mime(context: Context, path: String): String {
        val source = uri(path)
        val provided = runCatching { context.contentResolver.getType(source) }.getOrNull()
        return provided ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name(context, path).substringAfterLast('.', "").lowercase())
            ?: "*/*"
    }

    // Own the bytes so picker grants and cache eviction cannot break saved attachments.
    fun import(context: Context, source: Uri): String {
        val directory = File(context.filesDir, "attachments/${UUID.randomUUID()}")
        check(directory.mkdirs())
        val filename = name(context, source.toString()).substringAfterLast('/').substringAfterLast('\\')
            .takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "attachment"
        val file = File(directory, filename)
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: error("Unable to read attachment")
            input.use { stream -> file.outputStream().use { stream.copyTo(it) } }
            return file.absolutePath
        } catch (e: Exception) {
            file.delete()
            directory.delete()
            throw e
        }
    }

    fun open(context: Context, path: String) {
        val source = uri(path)
        val shared = if (source.scheme == "file") {
            val file = File(requireNotNull(source.path))
            check(file.isFile)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else source
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shared, mime(context, path))
            clipData = ClipData.newRawUri("attachment", shared)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}
