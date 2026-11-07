package com.gazna

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gazna.data.local.db.*
import com.gazna.data.repository.BackupRepositoryImpl
import com.gazna.domain.model.*
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupStorageTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun statusMigrationKeepsEveryRecordAndCopiesLegacyGrouping() {
        val name = "status-migration-${UUID.randomUUID()}"
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(app).name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE persons (id TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE transactions (id TEXT NOT NULL PRIMARY KEY, personId TEXT NOT NULL, amount REAL NOT NULL)")
                        db.execSQL("INSERT INTO persons VALUES ('person', 'ARCHIVED')")
                        db.execSQL("INSERT INTO transactions VALUES ('one', 'person', 15.5), ('two', 'missing', 25.0)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()
        )
        try {
            val db = helper.writableDatabase
            NetijeDatabase.MIGRATION_3_4.migrate(db)
            db.query("SELECT status, statusPending, amount FROM transactions ORDER BY id").use {
                assertTrue(it.moveToFirst())
                assertEquals("ARCHIVED", it.getString(0))
                assertEquals(1, it.getInt(1))
                assertEquals(15.5, it.getDouble(2), 0.0)
                assertTrue(it.moveToNext())
                assertEquals("ACTIVE", it.getString(0))
                assertEquals(25.0, it.getDouble(2), 0.0)
            }
        } finally { helper.close(); app.deleteDatabase(name) }
    }

    @Test fun transferRestoresFilesAndMergesWithoutErasingNewerRecords() = runBlocking {
        val directory = File(app.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = directory }
        val old = Room.inMemoryDatabaseBuilder(app, NetijeDatabase::class.java).build()
        val current = Room.inMemoryDatabaseBuilder(app, NetijeDatabase::class.java).build()
        fun repository(db: NetijeDatabase) = BackupRepositoryImpl(db.personDao(), db.transactionDao(), Gson(), db, context)
        fun transaction(id: String, updated: Long, amount: Double = 10.0) = Transaction(
            id = id, personId = "person", type = TransactionType.RECEIVED, amount = amount,
            date = 1, time = 1, updatedAt = updated
        )
        try {
            val source = File(directory, "receipt.pdf").apply { writeText("%PDF-test") }
            old.personDao().upsert(Person("person", "Old name", updatedAt = 10).toEntity())
            old.transactionDao().upsert(transaction("updated", 300, 20.0).copy(attachments = listOf(source.absolutePath), status = TransactionStatus.ARCHIVED).toEntity())
            old.transactionDao().upsert(transaction("keep-newer", 100).toEntity())
            old.transactionDao().upsert(transaction("new", 100).toEntity())
            val json = repository(old).exportToJson()
            source.delete()
            current.personDao().upsert(Person("person", "Current name", updatedAt = 200).toEntity())
            current.transactionDao().upsert(transaction("updated", 100).toEntity())
            current.transactionDao().upsert(transaction("keep-newer", 300, 90.0).toEntity())
            current.transactionDao().upsert(transaction("only-on-current-phone", 500).toEntity())
            assertEquals(2, repository(current).importFromJson(json))
            assertEquals("Current name", current.personDao().getById("person")!!.name)
            assertEquals(4, current.transactionDao().observeAll().first().size)
            assertEquals(90.0, current.transactionDao().getById("keep-newer")!!.amount, 0.0)
            val transferred = current.transactionDao().getById("updated")!!.toDomain()
            assertEquals(20.0, transferred.amount, 0.0)
            assertEquals(TransactionStatus.ARCHIVED, transferred.status)
            assertEquals("%PDF-test", File(transferred.attachments.single()).readText())
            assertEquals(0, repository(current).importFromJson(json))
            assertEquals(4, current.transactionDao().observeAll().first().size)
            assertTrue(File(directory, "backups").listFiles()!!.isNotEmpty())
        } finally {
            old.close(); current.close(); directory.deleteRecursively()
        }
    }

    @Test fun malformedImportLeavesDatabaseIntact() = runBlocking {
        val directory = File(app.cacheDir, "invalid-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = directory }
        val db = Room.inMemoryDatabaseBuilder(app, NetijeDatabase::class.java).build()
        try {
            db.personDao().upsert(Person("keep", "Keep me").toEntity())
            val repo = BackupRepositoryImpl(db.personDao(), db.transactionDao(), Gson(), db, context)
            assertTrue(runCatching { repo.importFromJson("""{"persons":[],"transactions":[{"id":"bad"}]}""") }.isFailure)
            assertEquals("Keep me", db.personDao().getById("keep")!!.name)
            assertEquals(0, repo.importFromJson("""{"version":1,"persons":[],"transactions":[]}"""))
            assertEquals("Keep me", db.personDao().getById("keep")!!.name)
        } finally { db.close(); directory.deleteRecursively() }
    }

    @Test fun unavailableLegacyFilesDoNotPreventBackingUpOrImportingRecords() = runBlocking {
        val directory = File(app.cacheDir, "legacy-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getFilesDir() = directory }
        val db = Room.inMemoryDatabaseBuilder(app, NetijeDatabase::class.java).build()
        try {
            db.personDao().upsert(Person("person", "Name", avatarUrl = "/missing/avatar.jpg").toEntity())
            db.transactionDao().upsert(Transaction("transaction", "person", TransactionType.RECEIVED, 10.0,
                attachments = listOf("/missing/old-phone.pdf"), date = 1, time = 1).toEntity())
            val repo = BackupRepositoryImpl(db.personDao(), db.transactionDao(), Gson(), db, context)
            val json = repo.exportToJson()
            val document = com.gazna.data.backup.BackupCodec.decode(json)
            assertEquals(2, document.missingFiles.size)
            assertEquals(1, document.transactions.size)
            assertEquals(0, repo.importFromJson(json))
            assertEquals(10.0, db.transactionDao().getById("transaction")!!.amount, 0.0)
        } finally { db.close(); directory.deleteRecursively() }
    }
}
