package com.example.netije.domain.repository

import com.example.netije.domain.model.AppSettings
import com.example.netije.domain.model.AppLanguage
import com.example.netije.domain.model.DarkModeConfig
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.UserApp
import com.example.netije.domain.model.DayTransactions
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<UserApp?>
    suspend fun signInWithGoogle(idToken: String): UserApp?
    suspend fun signOut()
    suspend fun syncLocalDataToServer(userId: String)
}

interface PersonRepository {
    fun observePersons(
        status: PersonStatus? = null,
        search: String? = null
    ): Flow<List<Person>>

    fun observePersonWithTransactions(personId: String): Flow<PersonWithTransactions?>
    fun observeAllPersonsWithTransactions(
        status: PersonStatus? = null,
        search: String? = null
    ): Flow<List<PersonWithTransactions>>

    suspend fun getPerson(id: String): Person?
    suspend fun upsertPerson(person: Person): String
    suspend fun deletePerson(id: String)
    suspend fun setPersonStatus(id: String, status: PersonStatus)
    suspend fun refreshFromRemote()
}

interface TransactionRepository {
    fun observeTransactions(
        personId: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null
    ): Flow<List<Transaction>>

    fun observeTransactionsGroupedByDay(
        fromDate: Long? = null,
        toDate: Long? = null
    ): Flow<List<DayTransactions>>

    suspend fun getTransaction(id: String): Transaction?
    suspend fun upsertTransaction(transaction: Transaction): String
    suspend fun deleteTransaction(id: String)
    suspend fun refreshFromRemote()
}

interface FinanceRepository {
    fun observeSummary(): Flow<FinanceSummary>
    fun observeMonthSummary(year: Int, month: Int): Flow<FinanceSummary>
    fun observeDaySummary(date: Long): Flow<FinanceSummary>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setCurrency(code: String)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun setDarkMode(mode: DarkModeConfig)
    suspend fun markBackupTaken()
    suspend fun setBackupFrequency(
        number: Int,
        unit: com.example.netije.domain.model.BackupUnit,
        nextRunAt: Long?
    )
}

interface BackupRepository {
    suspend fun exportToJson(): String
    suspend fun importFromJson(json: String): Int
    suspend fun backupToGoogleDrive(json: String): Boolean
    suspend fun restoreFromGoogleDrive(): String?
}

interface ContactsRepository {
    suspend fun pickContact(): Person?
    suspend fun fetchAllContacts(): List<Person>
    suspend fun syncPersonFromContact(personId: String): Person?
}
