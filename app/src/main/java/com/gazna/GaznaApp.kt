package com.gazna

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.gazna.data.local.datastore.SettingsDataStore
import com.gazna.data.backup.BackupScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltAndroidApp
class GaznaApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            SettingsDataStore(this@GaznaApp).settings
                .map { it.backupNumber to it.backupUnit }.distinctUntilChanged()
                .collect { (number, unit) -> BackupScheduler.schedule(this@GaznaApp, number, unit) }
        }
    }
}
