package com.gazna.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, PersonEntity::class, TransactionEntity::class],
    version = 6,
    exportSchema = true
)
abstract class NetijeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun personDao(): PersonDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN parentTransactionId TEXT")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN statusPending INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE transactions SET status = COALESCE((SELECT status FROM persons WHERE persons.id = transactions.personId), 'ACTIVE'), statusPending = 1")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN statusPending INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN changes TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("UPDATE transactions SET currency = 'TMT'")
            }
        }
        @Volatile
        private var INSTANCE: NetijeDatabase? = null

        fun getInstance(context: Context): NetijeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetijeDatabase::class.java,
                    "netije_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { INSTANCE = it }
            }
    }
}
