package com.example.netije

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.example.netije.data.local.datastore.SettingsDataStore
import com.example.netije.data.backup.BackupScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltAndroidApp
class NetijeApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            SettingsDataStore(this@NetijeApp).settings
                .map { it.backupNumber to it.backupUnit }.distinctUntilChanged()
                .collect { (number, unit) -> BackupScheduler.schedule(this@NetijeApp, number, unit) }
        }
    }
}
