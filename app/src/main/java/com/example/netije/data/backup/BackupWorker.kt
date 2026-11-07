package com.example.netije.data.backup

import android.content.Context
import androidx.work.WorkerParameters

/** Keeps persisted WorkManager requests from before the Gazna rename runnable. */
class BackupWorker(context: Context, parameters: WorkerParameters) :
    com.gazna.data.backup.BackupWorker(context, parameters)
