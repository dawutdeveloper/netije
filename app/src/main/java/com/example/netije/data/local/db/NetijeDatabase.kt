package com.example.netije.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.netije.BuildConfig

@Database(
    entities = [UserEntity::class, PersonEntity::class, TransactionEntity::class],
    version = 1,
    exportSchema = true
)
abstract class NetijeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun personDao(): PersonDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: NetijeDatabase? = null

        fun getInstance(context: Context): NetijeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NetijeDatabase::class.java,
                    "netije_db"
                ).apply {
                    if (BuildConfig.DEBUG) {
                        fallbackToDestructiveMigration()
                    }
                }.build().also { INSTANCE = it }
            }
    }
}
