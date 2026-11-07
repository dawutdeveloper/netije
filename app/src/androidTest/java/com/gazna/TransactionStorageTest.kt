package com.gazna

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gazna.data.local.db.NetijeDatabase
import com.gazna.data.local.db.toEntity
import com.gazna.data.local.db.toDomain
import com.gazna.domain.model.Transaction
import com.gazna.domain.model.TransactionType
import com.gazna.presentation.util.TransactionAttachments
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TransactionStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun importedPdfSurvivesOriginalRemovalAndDatabaseReopen() = runBlocking {
        val source = File(context.cacheDir, "reports/${UUID.randomUUID()}/receipt.pdf")
        source.parentFile!!.mkdirs()
        source.writeText("%PDF-1.4\nreceipt")
        val sourceUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
        val imported = TransactionAttachments.import(context, sourceUri)
        val databaseName = "attachment-test-${UUID.randomUUID()}"
        try {
            source.delete()
            assertEquals("receipt.pdf", TransactionAttachments.name(context, imported))
            assertEquals("application/pdf", TransactionAttachments.mime(context, imported))
            val firstDb = Room.databaseBuilder(context, NetijeDatabase::class.java, databaseName).build()
            try {
                firstDb.transactionDao().upsert(Transaction(
                    id = "test", personId = "person", type = TransactionType.RECEIVED,
                    amount = 10.0, attachments = listOf(imported), date = 1, time = 1
                ).toEntity())
            } finally {
                firstDb.close()
            }
            val reopenedDb = Room.databaseBuilder(context, NetijeDatabase::class.java, databaseName).build()
            try {
                val path = reopenedDb.transactionDao().getById("test")!!.toDomain().attachments.single()
                val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                assertEquals("content", shareUri.scheme)
                assertEquals("%PDF-1.4\nreceipt", context.contentResolver.openInputStream(shareUri)!!.bufferedReader().use { it.readText() })
            } finally {
                reopenedDb.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
            File(imported).delete()
            File(imported).parentFile!!.delete()
            source.delete()
            source.parentFile!!.delete()
        }
    }

    @Test fun existingContentAndFileUrisResolveToTheSameBytes() {
        val source = File(context.filesDir, "photos/test-${UUID.randomUUID()}.jpg")
        source.parentFile!!.mkdirs()
        source.writeBytes(byteArrayOf(1, 2, 3))
        try {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
            assertEquals(contentUri, TransactionAttachments.uri(contentUri.toString()))
            assertEquals(Uri.fromFile(source), TransactionAttachments.uri(source.absolutePath))
            assertEquals(Uri.fromFile(source), TransactionAttachments.uri(Uri.fromFile(source).toString()))
            assertEquals("image/jpeg", TransactionAttachments.mime(context, contentUri.toString()))
            assertArrayEquals(source.readBytes(), context.contentResolver.openInputStream(contentUri)!!.use { it.readBytes() })
        } finally { source.delete() }
    }

    @Test fun migrationPreservesTransactionsAndInitializesHistory() {
        val name = "migration-test-${UUID.randomUUID()}"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE transactions (id TEXT NOT NULL PRIMARY KEY, amount REAL NOT NULL, currency TEXT NOT NULL)")
                        db.execSQL("INSERT INTO transactions VALUES ('saved', 125.0, 'USD')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()
        )
        try {
            val db = helper.writableDatabase
            NetijeDatabase.MIGRATION_1_2.migrate(db)
            db.query("SELECT id, amount, currency, changes FROM transactions").use {
                assertTrue(it.moveToFirst())
                assertEquals("saved", it.getString(0))
                assertEquals(125.0, it.getDouble(1), 0.0)
                assertEquals("TMT", it.getString(2))
                assertEquals("[]", it.getString(3))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
