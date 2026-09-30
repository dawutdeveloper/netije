package com.example.netije.data.repository

import com.example.netije.data.local.db.*
import com.example.netije.data.local.db.PersonDao
import com.example.netije.data.local.db.TransactionDao
import com.example.netije.data.local.db.UserDao
import com.example.netije.data.local.datastore.SettingsDataStore
import com.example.netije.data.remote.api.AuthApi
import com.example.netije.data.remote.api.PersonsApi
import com.example.netije.data.remote.api.ReportsApi
import com.example.netije.data.remote.api.SyncApi
import com.example.netije.data.remote.api.TransactionsApi
import com.example.netije.data.remote.dto.GoogleSignInRequest
import com.example.netije.data.remote.dto.PersonDto
import com.example.netije.data.remote.dto.SyncPayload
import com.example.netije.data.remote.dto.TransactionDto
import com.example.netije.domain.model.AppLanguage
import com.example.netije.domain.model.AppSettings
import com.example.netije.domain.model.DarkModeConfig
import com.example.netije.domain.model.DayTransactions
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import com.example.netije.domain.model.UserApp
import com.example.netije.domain.repository.AuthRepository
import com.example.netije.domain.repository.BackupRepository
import com.example.netije.domain.repository.ContactsRepository
import com.example.netije.domain.repository.FinanceRepository
import com.example.netije.domain.repository.PersonRepository
import com.example.netije.domain.repository.SettingsRepository
import com.example.netije.domain.repository.TransactionRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val ctx: android.content.Context,
    private val userDao: UserDao,
    private val authApi: AuthApi,
    private val syncApi: SyncApi,
    private val personDao: PersonDao,
    private val transactionDao: TransactionDao,
    private val gson: Gson
) : AuthRepository {

    override val currentUser: Flow<UserApp?> = userDao.observeUser().map { it?.toDomain() }

    override suspend fun signInWithGoogle(idToken: String): UserApp? {
        return try {
            val res = authApi.signInWithGoogle(GoogleSignInRequest(idToken))
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                val user = body.user.toDomain(
                    googleIdToken = idToken,
                    accessToken = body.accessToken
                )
                userDao.clear()
                userDao.insertUser(user.toEntity())
                // Mirror access token to SharedPrefs cache for OkHttp interceptor (non-blocking reads)
                com.example.netije.di.saveAccessToken(ctx, user.accessToken)
                user
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun signOut() {
        try { authApi.signOut() } catch (_: Exception) {}
        userDao.clear()
        personDao.clear()
        transactionDao.clear()
        // Clear SharedPrefs access token cache too
        com.example.netije.di.saveAccessToken(ctx, null)
    }

    override suspend fun syncLocalDataToServer(userId: String) {
        try {
            val persons = personDao.observeAll().firstOrNull()?.map { it.toDomain() } ?: emptyList()
            val transactions = transactionDao.observeAll().firstOrNull()?.map { it.toDomain() } ?: emptyList()
            val payload = SyncPayload(
                persons = persons.map { PersonDto.fromDomain(it) },
                transactions = transactions.map { TransactionDto.fromDomain(it) }
            )
            val res = syncApi.sync(payload)
            val body = if (res.isSuccessful) res.body() else null
            if (body != null) {
                personDao.upsertAll(body.persons.map { it.toDomain().toEntity() })
                transactionDao.upsertAll(body.transactions.map { it.toDomain().toEntity() })
            }
        } catch (_: Exception) { }
    }
}

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val personDao: PersonDao,
    private val personsApi: PersonsApi,
    private val transactionDao: TransactionDao
) : PersonRepository {

    override fun observePersons(status: PersonStatus?, search: String?): Flow<List<Person>> {
        val flow = when {
            status != null && !search.isNullOrBlank() ->
                personDao.observeByStatusAndSearch(status.name, search.trim())
            status != null -> personDao.observeByStatus(status.name)
            !search.isNullOrBlank() -> personDao.observeBySearch(search.trim())
            else -> personDao.observeAll()
        }
        return flow.map { list -> list.map { it.toDomain() } }
    }

    override fun observePersonWithTransactions(personId: String): Flow<PersonWithTransactions?> =
        combine(
            personDao.observeById(personId).map { entity -> entity?.toDomain() },
            transactionDao.observeByPerson(personId).map { list -> list.map { it.toDomain() } }
        ) { person, transactions ->
            person?.let { PersonWithTransactions(it, transactions) }
        }

    override fun observeAllPersonsWithTransactions(
        status: PersonStatus?,
        search: String?
    ): Flow<List<PersonWithTransactions>> =
        combine(observePersons(status, search), transactionDao.observeAll().map { it.map { t -> t.toDomain() } }) { persons, transactions ->
            persons.map { p ->
                PersonWithTransactions(
                    person = p,
                    transactions = transactions.filter { it.personId == p.id }
                )
            }
        }

    override suspend fun getPerson(id: String): Person? = personDao.getById(id)?.toDomain()

    override suspend fun upsertPerson(person: Person): String {
        personDao.upsert(person.toEntity())
        try {
            if (personsApi.getById(person.id).isSuccessful) {
                personsApi.update(person.id, PersonDto.fromDomain(person))
            } else {
                personsApi.create(PersonDto.fromDomain(person))
            }
        } catch (_: Exception) { }
        return person.id
    }

    override suspend fun deletePerson(id: String) {
        transactionDao.deleteByPersonId(id)
        personDao.deleteById(id)
        try { personsApi.delete(id) } catch (_: Exception) { }
    }

    override suspend fun setPersonStatus(id: String, status: PersonStatus) {
        personDao.setStatus(id, status.name)
        try {
            val p = personDao.getById(id)?.toDomain() ?: return
            personsApi.update(id, PersonDto.fromDomain(p.copy(status = status)))
        } catch (_: Exception) { }
    }

    override suspend fun refreshFromRemote() {
        try {
            val res = personsApi.getAll()
            val body = if (res.isSuccessful) res.body() else null
            if (body != null) {
                personDao.upsertAll(body.map { it.toDomain().toEntity() })
            }
        } catch (_: Exception) { }
    }
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionsApi: TransactionsApi,
    private val personDao: PersonDao
) : TransactionRepository {

    private suspend fun autoSettleIfNeeded(personId: String) {
        val person = personDao.getById(personId) ?: return
        val current = PersonStatus.fromString(person.status)
        if (current != PersonStatus.ACTIVE) return
        val txList = transactionDao.getByPerson(personId).map { it.toDomain() }
        if (txList.isEmpty()) return
        val received = txList.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
        val issued = txList.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
        val balance = received - issued
        val settled = kotlin.math.abs(balance) < 0.005
        if (settled) {
            personDao.setStatus(personId, PersonStatus.SETTLED.name)
        }
    }

    override fun observeTransactions(
        personId: String?,
        fromDate: Long?,
        toDate: Long?
    ): Flow<List<Transaction>> {
        val flow = when {
            personId != null && fromDate != null && toDate != null ->
                transactionDao.observeByPersonAndDateRange(personId, fromDate, toDate)
            fromDate != null && toDate != null -> transactionDao.observeByDateRange(fromDate, toDate)
            personId != null -> transactionDao.observeByPerson(personId)
            else -> transactionDao.observeAll()
        }
        return flow.map { it.map { e -> e.toDomain() } }
    }

    override fun observeTransactionsGroupedByDay(
        fromDate: Long?,
        toDate: Long?
    ): Flow<List<DayTransactions>> {
        val txFlow = if (fromDate != null && toDate != null) {
            transactionDao.observeByDateRange(fromDate, toDate)
        } else {
            transactionDao.observeAll()
        }
        return combine(txFlow.map { it.map { e -> e.toDomain() } },
            personDao.observeAll().map { it.map { e -> e.toDomain() } }) { transactions, persons ->
            val personMap = persons.associateBy { it.id }
            fun startOfDay(ts: Long): Long {
                val c = Calendar.getInstance()
                c.timeInMillis = ts
                c.set(Calendar.HOUR_OF_DAY, 0)
                c.set(Calendar.MINUTE, 0)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                return c.timeInMillis
            }
            transactions.groupBy { startOfDay(it.date) }
                .map { (date, txs) -> DayTransactions(date, txs.sortedByDescending { it.time }, personMap) }
                .sortedByDescending { it.date }
        }
    }

    override suspend fun getTransaction(id: String): Transaction? =
        transactionDao.getById(id)?.toDomain()

    override suspend fun upsertTransaction(transaction: Transaction): String {
        transactionDao.upsert(transaction.toEntity())
        runCatching { autoSettleIfNeeded(transaction.personId) }
        try {
            if (transactionsApi.getById(transaction.id).isSuccessful) {
                transactionsApi.update(transaction.id, TransactionDto.fromDomain(transaction))
            } else {
                transactionsApi.create(TransactionDto.fromDomain(transaction))
            }
        } catch (_: Exception) { }
        return transaction.id
    }

    override suspend fun deleteTransaction(id: String) {
        val tx = transactionDao.getById(id)
        transactionDao.deleteById(id)
        tx?.let { runCatching { autoSettleIfNeeded(it.personId) } }
        try { transactionsApi.delete(id) } catch (_: Exception) { }
    }

    override suspend fun refreshFromRemote() {
        try {
            val res = transactionsApi.getAll()
            val body = if (res.isSuccessful) res.body() else null
            if (body != null) {
                transactionDao.upsertAll(body.map { it.toDomain().toEntity() })
            }
        } catch (_: Exception) { }
    }
}

@Singleton
class FinanceRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao
) : FinanceRepository {

    private fun calc(list: List<Transaction>): FinanceSummary {
        var received = 0.0
        var issued = 0.0
        list.forEach {
            if (it.type == TransactionType.RECEIVED) received += it.amount
            else issued += it.amount
        }
        return FinanceSummary(received, issued)
    }

    override fun observeSummary(): Flow<FinanceSummary> =
        transactionDao.observeAll().map { list -> calc(list.map { it.toDomain() }) }

    override fun observeMonthSummary(year: Int, month: Int): Flow<FinanceSummary> {
        val cal = java.util.Calendar.getInstance()
        cal.set(year, month, 1, 0, 0, 0)
        val start = cal.timeInMillis
        cal.set(year, month + 1, 1, 0, 0, 0)
        val end = cal.timeInMillis - 1
        return transactionDao.observeByDateRange(start, end)
            .map { list -> calc(list.map { it.toDomain() }) }
    }

    override fun observeDaySummary(date: Long): Flow<FinanceSummary> {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = date }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + 24 * 60 * 60 * 1000 - 1
        return transactionDao.observeByDateRange(start, end)
            .map { list -> calc(list.map { it.toDomain() }) }
    }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataStore.settings
    override suspend fun setCurrency(code: String) = dataStore.setCurrency(code)
    override suspend fun setLanguage(language: AppLanguage) = dataStore.setLanguage(language)
    override suspend fun setDarkMode(mode: DarkModeConfig) = dataStore.setDarkMode(mode)
    override suspend fun markBackupTaken() = dataStore.markBackupTaken()
    override suspend fun setBackupFrequency(
        number: Int,
        unit: com.example.netije.domain.model.BackupUnit,
        nextRunAt: Long?
    ) = dataStore.setBackupFrequency(number, unit, nextRunAt)
}

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val personDao: PersonDao,
    private val transactionDao: TransactionDao,
    private val gson: Gson
) : BackupRepository {

    private data class BackupJson(
        val version: Int = 1,
        val exportedAt: Long = System.currentTimeMillis(),
        val persons: List<Person>,
        val transactions: List<Transaction>
    )

    override suspend fun exportToJson(): String {
        val persons = personDao.observeAll().first().map { it.toDomain() }
        val transactions = transactionDao.observeAll().first().map { it.toDomain() }
        return gson.toJson(BackupJson(persons = persons, transactions = transactions))
    }

    override suspend fun importFromJson(json: String): Int {
        val backup = runCatching { gson.fromJson(json, BackupJson::class.java) }.getOrNull()
            ?: return 0
        personDao.clear()
        transactionDao.clear()
        personDao.upsertAll(backup.persons.map { it.toEntity() })
        transactionDao.upsertAll(backup.transactions.map { it.toEntity() })
        return backup.persons.size + backup.transactions.size
    }

    override suspend fun backupToGoogleDrive(json: String): Boolean {
        return true
    }

    override suspend fun restoreFromGoogleDrive(): String? = null
}

@Singleton
class ContactsRepositoryImpl @Inject constructor(
    @ApplicationContext private val ctx: android.content.Context
) : ContactsRepository {
    override suspend fun pickContact(): Person? = null

    override suspend fun fetchAllContacts(): List<Person> {
        val res = mutableListOf<Person>()
        val cr = ctx.contentResolver ?: return emptyList()
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.READ_CONTACTS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val cursor = cr.query(
            android.provider.ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.Contacts._ID,
                android.provider.ContactsContract.Contacts.DISPLAY_NAME,
                android.provider.ContactsContract.Contacts.LOOKUP_KEY,
                android.provider.ContactsContract.Contacts.PHOTO_URI
            ),
            null,
            null,
            android.provider.ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        ) ?: return emptyList()
        cursor.use { c ->
            val idIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
            val lookupIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts.LOOKUP_KEY)
            val photoIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts.PHOTO_URI)
            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val name = c.getString(nameIdx) ?: continue
                val lookup = if (!c.isNull(lookupIdx)) c.getString(lookupIdx) else null
                val photo = if (!c.isNull(photoIdx)) c.getString(photoIdx) else null
                val phones = mutableListOf<String>()
                val pc = cr.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                pc?.use { p ->
                    val numIdx = p.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (p.moveToNext()) {
                        val n = if (!p.isNull(numIdx)) p.getString(numIdx) else null
                        if (!n.isNullOrBlank()) phones += n.trim()
                    }
                }
                val emails = mutableListOf<String>()
                val ec = cr.query(
                    android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS),
                    android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                ec?.use { e ->
                    val addrIdx = e.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS)
                    while (e.moveToNext()) {
                        val a = if (!e.isNull(addrIdx)) e.getString(addrIdx) else null
                        if (!a.isNullOrBlank()) emails += a.trim()
                    }
                }
                res += com.example.netije.domain.model.Person(
                    id = "",
                    name = name,
                    phones = phones,
                    emails = emails,
                    avatarUrl = photo,
                    contactLookupUri = lookup?.let {
                        android.provider.ContactsContract.Contacts.getLookupUri(
                            id.toLongOrNull() ?: 0L,
                            it
                        ).toString()
                    },
                    status = com.example.netije.domain.model.PersonStatus.ACTIVE
                )
            }
        }
        return res
    }

    override suspend fun syncPersonFromContact(personId: String): Person? = null
}
