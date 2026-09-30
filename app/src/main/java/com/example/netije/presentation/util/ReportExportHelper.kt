package com.example.netije.presentation.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.example.netije.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File

enum class ReportFormat(val ext: String, val mimeType: String, val display: String) {
    PDF("pdf", "application/pdf", "PDF"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "XLSX"),
    CSV("csv", "text/csv", "CSV")
}

object ReportExportHelper {

    suspend fun runExport(
        ctx: Context,
        baseName: String,
        format: ReportFormat,
        block: suspend () -> Response<ResponseBody>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val res = block()
            if (!res.isSuccessful) throw IllegalStateException("HTTP ${res.code()}")
            val body = res.body() ?: throw IllegalStateException("empty body")
            val dir = File(ctx.filesDir, "reports").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val file = File(dir, "${baseName}_${timestamp}.${format.ext}")
            file.outputStream().use { out ->
                body.byteStream().use { it.copyTo(out) }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.toast_export_success) + " · ${format.display}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            file
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.toast_export_error) + ": ${t.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            null
        }
    }

    fun shareFile(ctx: Context, file: File, format: ReportFormat) {
        val uri: Uri = try {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        } catch (_: Throwable) {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            clipData = ClipData.newUri(ctx.contentResolver, "netije_${format.ext}", uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(
                Intent.EXTRA_SUBJECT,
                ctx.getString(R.string.toast_share_subject) + " · ${format.display}"
            )
        }
        val resolveInfoList = ctx.packageManager.queryIntentActivities(intent, 0)
        for (ri in resolveInfoList) {
            val packageName = ri.activityInfo.packageName
            runCatching {
                ctx.grantUriPermission(
                    packageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        val chooser = Intent.createChooser(
            intent,
            ctx.getString(R.string.toast_share_title)
        )
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { ctx.startActivity(chooser) }
    }

    fun lastBackupAt(ctx: Context): Long? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val v = prefs.getLong("last_backup_at", -1L)
        return if (v > 0) v else null
    }

    fun markBackupAt(ctx: Context, millis: Long = System.currentTimeMillis()) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit {
            putLong("last_backup_at", millis)
        }
    }
}
