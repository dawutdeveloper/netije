package com.example.netije.data.backup

import android.content.Context
import androidx.work.*
import com.example.netije.domain.model.BackupUnit
import com.example.netije.domain.model.backupMillis
import com.example.netije.domain.repository.BackupRepository
import com.example.netije.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint {
    fun backups(): BackupRepository
    fun settings(): SettingsRepository
}

object BackupScheduler {
    fun schedule(context: Context, number: Int?, unit: BackupUnit) {
        val manager = WorkManager.getInstance(context)
        val interval = backupMillis(number, unit)
        if (interval == null) {
            manager.cancelUniqueWork("scheduled-backup")
            return
        }
        val work = PeriodicWorkRequestBuilder<BackupWorker>(interval, TimeUnit.MILLISECONDS)
            .setInitialDelay(interval, TimeUnit.MILLISECONDS).build()
        manager.enqueueUniquePeriodicWork("scheduled-backup", ExistingPeriodicWorkPolicy.UPDATE, work)
    }
}

class BackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val services = EntryPointAccessors.fromApplication(applicationContext, BackupEntryPoint::class.java)
        return try {
            val json = services.backups().exportToJson()
            val directory = File(applicationContext.getExternalFilesDir(null) ?: applicationContext.filesDir, "Netije/backups")
            check(directory.isDirectory || directory.mkdirs())
            val pending = File(directory, "backup-${id}.tmp")
            pending.writeText(json)
            check(pending.renameTo(File(directory, "netije_backup_${System.currentTimeMillis()}.json")))
            services.settings().markBackupTaken()
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { Result.retry() }
    }
}
