package com.gazna

import com.gazna.data.local.db.PersonDao
import com.gazna.data.local.db.TransactionDao
import com.gazna.data.local.db.TransactionEntity
import com.gazna.data.local.db.toDomain
import com.gazna.data.local.db.toEntity
import com.gazna.data.remote.api.TransactionsApi
import com.gazna.data.remote.dto.TransactionDto
import com.gazna.data.repository.TransactionRepositoryImpl
import com.gazna.domain.model.Transaction
import com.gazna.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class TransactionTagsSyncTest {
    @Test fun legacyServerCannotEraseTagsAndExplicitRemoteChangesAreApplied() = runBlocking {
        var local = Transaction("saved", "person", TransactionType.RECEIVED, 300.0,
            tags = listOf("#rent"), date = 1, time = 1).toEntity()
        var remote = TransactionDto.fromDomain(local.toDomain()).copy(tags = null)
        val dao = Proxy.newProxyInstance(TransactionDao::class.java.classLoader, arrayOf(TransactionDao::class.java)) { _, method, args ->
            when (method.name) {
                "getById" -> local
                "pendingStatuses" -> if (local.statusPending) listOf(local) else emptyList()
                "upsert" -> { local = args[0] as TransactionEntity; Unit }
                "upsertRemoteAll" -> { local = (args[0] as List<TransactionEntity>).single(); Unit }
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as TransactionDao
        val people = Proxy.newProxyInstance(PersonDao::class.java.classLoader, arrayOf(PersonDao::class.java)) { _, method, _ ->
            error("Tags must not alter people: ${method.name}")
        } as PersonDao
        val api = Proxy.newProxyInstance(TransactionsApi::class.java.classLoader, arrayOf(TransactionsApi::class.java)) { _, method, _ ->
            when (method.name) {
                "getAll" -> Response.success(listOf(remote))
                "update" -> Response.error<TransactionDto>(503, "offline".toResponseBody())
                else -> error("Unexpected API call: ${method.name}")
            }
        } as TransactionsApi
        val repository = TransactionRepositoryImpl(dao, api, people)
        repository.refreshFromRemote()
        assertEquals(listOf("#rent"), local.toDomain().tags)
        remote = remote.copy(tags = listOf("#work"))
        repository.refreshFromRemote()
        assertEquals(listOf("#work"), local.toDomain().tags)
        remote = remote.copy(tags = emptyList())
        repository.refreshFromRemote()
        assertTrue(local.toDomain().tags.isEmpty())
        repository.upsertTransaction(local.toDomain().copy(tags = listOf("#pending")))
        assertTrue(local.statusPending)
        repository.refreshFromRemote()
        assertEquals(listOf("#pending"), local.toDomain().tags)
        assertEquals(300.0, local.amount, 0.0)
    }
}
