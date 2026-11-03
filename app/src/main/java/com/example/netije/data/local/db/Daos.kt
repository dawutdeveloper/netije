package com.example.netije.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.netije.domain.model.PersonStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM user_account LIMIT 1")
    fun observeUser(): Flow<UserEntity?>

    @Query("DELETE FROM user_account")
    suspend fun clear()
}

@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(persons: List<PersonEntity>)

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM persons WHERE id = :id")
    fun observeById(id: String): Flow<PersonEntity?>

    @Query("SELECT * FROM persons ORDER BY name ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE status = :status ORDER BY name ASC")
    fun observeByStatus(status: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE name LIKE '%' || :search || '%' ORDER BY name ASC")
    fun observeBySearch(search: String): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons WHERE status = :status AND name LIKE '%' || :search || '%' ORDER BY name ASC")
    fun observeByStatusAndSearch(status: String, search: String): Flow<List<PersonEntity>>

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE persons SET status = :status, statusPending = 1, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM persons WHERE statusPending = 1")
    suspend fun getPendingStatuses(): List<PersonEntity>

    @Query("UPDATE persons SET statusPending = 0 WHERE id = :id AND status = :status AND updatedAt = :updatedAt")
    suspend fun acknowledgeStatus(id: String, status: String, updatedAt: Long)

    @androidx.room.Transaction
    suspend fun upsertRemoteAll(persons: List<PersonEntity>) {
        persons.forEach { remote ->
            val local = getById(remote.id)
            upsert(if (local?.statusPending == true) local else remote)
        }
    }

    @Query("DELETE FROM persons")
    suspend fun clear()
}

@Dao
interface TransactionDao {
    @Query("UPDATE transactions SET status = :status, statusPending = 1, updatedAt = :now WHERE id = :id OR parentTransactionId = :id")
    suspend fun setStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions WHERE statusPending = 1 ORDER BY CASE WHEN parentTransactionId IS NULL THEN 0 ELSE 1 END")
    suspend fun pendingStatuses(): List<TransactionEntity>

    @Query("UPDATE transactions SET statusPending = 0 WHERE id = :id AND status = :status AND updatedAt = :updatedAt")
    suspend fun acknowledgeStatus(id: String, status: String, updatedAt: Long)

    @androidx.room.Transaction
    suspend fun upsertRemoteAll(transactions: List<TransactionEntity>) {
        transactions.forEach { remote ->
            val local = getById(remote.id)
            val history = (local?.toDomain()?.changes.orEmpty() + remote.toDomain().changes)
                .distinctBy { it.id }.sortedBy { it.changedAt }
            val selected = if (local?.statusPending == true) local else remote
            upsert(selected.copy(changes = com.google.gson.Gson().toJson(history), parentTransactionId = selected.parentTransactionId ?: local?.parentTransactionId))
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC, time DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE personId = :personId ORDER BY date DESC, time DESC")
    fun observeByPerson(personId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE personId = :personId ORDER BY date DESC, time DESC")
    suspend fun getByPerson(personId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC, time DESC")
    fun observeByDateRange(from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE personId = :personId AND date BETWEEN :from AND :to ORDER BY date DESC, time DESC")
    fun observeByPersonAndDateRange(personId: String, from: Long, to: Long): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE id = :id OR parentTransactionId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: String)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
