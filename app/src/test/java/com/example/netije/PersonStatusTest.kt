package com.example.netije

import com.example.netije.data.local.db.PersonDao
import com.example.netije.data.local.db.PersonEntity
import com.example.netije.data.local.db.TransactionDao
import com.example.netije.data.local.db.TransactionEntity
import com.example.netije.data.remote.api.PersonsApi
import com.example.netije.data.remote.dto.PersonDto
import com.example.netije.data.repository.PersonRepositoryImpl
import com.example.netije.domain.model.PersonStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy

class PersonStatusTest {
    private class MemoryDao : PersonDao {
        val value = MutableStateFlow<PersonEntity?>(PersonEntity(id = "person", name = "Name"))
        override suspend fun upsert(person: PersonEntity) { value.value = person }
        override suspend fun upsertAll(persons: List<PersonEntity>) { persons.forEach { upsert(it) } }
        override suspend fun update(person: PersonEntity) = upsert(person)
        override suspend fun getById(id: String) = value.value?.takeIf { it.id == id }
        override fun observeById(id: String) = value.map { it?.takeIf { p -> p.id == id } }
        override fun observeAll() = value.map { listOfNotNull(it) }
        override fun observeByStatus(status: String) = observeAll().map { list -> list.filter { it.status == status } }
        override fun observeBySearch(search: String) = observeAll().map { list -> list.filter { it.name.contains(search) } }
        override fun observeByStatusAndSearch(status: String, search: String) = observeByStatus(status).map { list -> list.filter { it.name.contains(search) } }
        override suspend fun deleteById(id: String) { if (value.value?.id == id) value.value = null }
        override suspend fun setStatus(id: String, status: String, now: Long) {
            getById(id)?.let { upsert(it.copy(status = status, updatedAt = now, statusPending = true)) }
        }
        override suspend fun getPendingStatuses() = listOfNotNull(value.value?.takeIf { it.statusPending })
        override suspend fun acknowledgeStatus(id: String, status: String, updatedAt: Long) {
            getById(id)?.takeIf { it.status == status && it.updatedAt == updatedAt }
                ?.let { upsert(it.copy(statusPending = false)) }
        }
        override suspend fun clear() { value.value = null }
    }

    private class Api : PersonsApi {
        var remote = PersonDto(id = "person", name = "Name")
        var offline = false
        var rejected = false
        var missing = false
        var creates = 0
        var beforeUpdate: suspend (PersonDto) -> Unit = {}
        override suspend fun getAll() = Response.success(listOf(remote))
        override suspend fun getById(id: String) = Response.success(remote)
        override suspend fun update(id: String, person: PersonDto): Response<PersonDto> {
            beforeUpdate(person)
            if (offline) throw IOException("Offline")
            if (rejected) return Response.error(500, "Unavailable".toResponseBody())
            if (missing) return Response.error(404, "Not found".toResponseBody())
            remote = person
            return Response.success(person)
        }
        override suspend fun create(person: PersonDto): Response<PersonDto> {
            creates++
            missing = false
            remote = person
            return Response.success(person)
        }
        override suspend fun delete(id: String) = Response.success(Unit)
    }

    private val transaction = TransactionEntity(
        id = "transaction", personId = "person", type = "RECEIVED", amount = 125.0, date = 1, time = 1
    )
    private val transactions = Proxy.newProxyInstance(
        TransactionDao::class.java.classLoader, arrayOf(TransactionDao::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "observeAll", "observeByPerson" -> flowOf(listOf(transaction))
            else -> error("Unexpected transaction mutation: ${method.name}")
        }
    } as TransactionDao

    @Test fun everyStatusCanMoveToEveryOtherStatusWithoutChangingTransactions() = runBlocking {
        val dao = MemoryDao()
        val repo = PersonRepositoryImpl(dao, Api(), transactions)
        for (from in PersonStatus.entries) {
            for (to in PersonStatus.entries) {
                repo.setPersonStatus("person", from)
                repo.setPersonStatus("person", to)
                assertEquals(to, repo.getPerson("person")!!.status)
                for (filter in PersonStatus.entries) {
                    assertEquals(if (filter == to) 1 else 0, repo.observePersons(filter, null).first().size)
                }
                val detail = repo.observePersonWithTransactions("person").first()!!
                assertEquals(1, detail.transactions.size)
                assertEquals(125.0, detail.balance, 0.0)
            }
        }
    }

    @Test fun offlineSelectionSurvivesRefreshAndRepositoryRecreation() = runBlocking {
        val dao = MemoryDao()
        val api = Api().apply { offline = true }
        PersonRepositoryImpl(dao, api, transactions).setPersonStatus("person", PersonStatus.ARCHIVED)
        val reopened = PersonRepositoryImpl(dao, api, transactions)
        reopened.refreshFromRemote()
        assertEquals(PersonStatus.ARCHIVED, reopened.getPerson("person")!!.status)
        assertTrue(dao.value.value!!.statusPending)
        api.offline = false
        reopened.refreshFromRemote()
        assertEquals("ARCHIVED", api.remote.status)
        assertFalse(dao.value.value!!.statusPending)
    }

    @Test fun serverFailureDoesNotResetLocalStatus() = runBlocking {
        val dao = MemoryDao()
        val api = Api().apply { rejected = true }
        val repo = PersonRepositoryImpl(dao, api, transactions)
        repo.setPersonStatus("person", PersonStatus.SETTLED)
        repo.refreshFromRemote()
        assertEquals(PersonStatus.SETTLED, repo.getPerson("person")!!.status)
        assertTrue(dao.value.value!!.statusPending)
    }

    @Test fun missingRemotePersonIsCreatedWithTheSelectedStatus() = runBlocking {
        val dao = MemoryDao()
        val api = Api().apply { missing = true }
        PersonRepositoryImpl(dao, api, transactions).setPersonStatus("person", PersonStatus.ARCHIVED)
        assertEquals(1, api.creates)
        assertEquals("ARCHIVED", api.remote.status)
        assertFalse(dao.value.value!!.statusPending)
    }

    @Test fun anOldUploadCannotAcknowledgeANewerSelection() = runBlocking {
        val dao = MemoryDao()
        val api = Api()
        val firstRequest = CompletableDeferred<Unit>()
        val continueFirst = CompletableDeferred<Unit>()
        api.beforeUpdate = { person ->
            if (person.status == "ARCHIVED") {
                firstRequest.complete(Unit)
                continueFirst.await()
            }
        }
        val repo = PersonRepositoryImpl(dao, api, transactions)
        val first = launch(start = CoroutineStart.UNDISPATCHED) { repo.setPersonStatus("person", PersonStatus.ARCHIVED) }
        firstRequest.await()
        val second = launch(start = CoroutineStart.UNDISPATCHED) { repo.setPersonStatus("person", PersonStatus.SETTLED) }
        assertEquals("SETTLED", dao.value.value!!.status)
        assertTrue(dao.value.value!!.statusPending)
        continueFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals("SETTLED", api.remote.status)
        assertEquals("SETTLED", dao.value.value!!.status)
        assertFalse(dao.value.value!!.statusPending)
    }

    @Test fun syncingRemotePeoplePreservesUnsentLocalStatus() = runBlocking {
        val dao = MemoryDao()
        dao.setStatus("person", "SETTLED", 10)
        dao.upsertRemoteAll(listOf(PersonEntity(id = "person", name = "Updated remotely", status = "ACTIVE", updatedAt = 20)))
        assertEquals("Updated remotely", dao.value.value!!.name)
        assertEquals("SETTLED", dao.value.value!!.status)
        assertEquals(10L, dao.value.value!!.updatedAt)
        assertTrue(dao.value.value!!.statusPending)
    }
}
