package com.example.netije.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, PersonEntity::class, TransactionEntity::class],
    version = 2,
    exportSchema = true
)
abstract class NetijeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun personDao(): PersonDao
    abstract fun transactionDao(): TransactionDao

    companion object {
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
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
