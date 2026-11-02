package com.example.netije.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, PersonEntity::class, TransactionEntity::class],
    version = 3,
    exportSchema = true
)
abstract class NetijeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun personDao(): PersonDao
    abstract fun transactionDao(): TransactionDao

    companion object {
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
