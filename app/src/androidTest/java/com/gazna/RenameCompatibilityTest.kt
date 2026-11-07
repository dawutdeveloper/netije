package com.gazna

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenameCompatibilityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun existingLauncherComponentOpensRenamedActivity() {
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        val legacyName = ComponentName(context.packageName, "com.example.netije.MainActivity")
        assertEquals(legacyName, launcher.component)
        val info = context.packageManager.getActivityInfo(legacyName, 0)
        assertEquals(MainActivity::class.java.name, info.targetActivity)
        assertEquals("Gazna", info.loadLabel(context.packageManager).toString())
    }

    @Test fun persistedBackupWorkerClassStillHasWorkManagerConstructor() {
        val legacyWorker = Class.forName("com.example.netije.data.backup.BackupWorker")
        assertTrue(com.gazna.data.backup.BackupWorker::class.java.isAssignableFrom(legacyWorker))
        legacyWorker.getConstructor(android.content.Context::class.java, androidx.work.WorkerParameters::class.java)
    }
}
