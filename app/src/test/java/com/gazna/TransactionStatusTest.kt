package com.gazna

import com.gazna.data.local.db.*
import com.gazna.data.remote.api.TransactionsApi
import com.gazna.data.remote.dto.TransactionDto
import com.gazna.data.repository.TransactionRepositoryImpl
import com.gazna.domain.model.TransactionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy

class TransactionStatusTest {
    @Test fun movingOneTransactionNeverChangesPeopleOrOtherTransactionsAndSurvivesOfflineRefresh() = runBlocking {
        val original = TransactionEntity("one", "person", "RECEIVED", 20.0, date = 1, time = 1)
        val rows = linkedMapOf("one" to original, "two" to original.copy(id = "two"))
        val flow = MutableStateFlow(rows.values.toList())
        var offline = true
        var remote = TransactionDto("one", "person", "RECEIVED", 10.0, date = 1, time = 1, status = "ACTIVE")
        val dao = Proxy.newProxyInstance(TransactionDao::class.java.classLoader, arrayOf(TransactionDao::class.java)) { _, method, args ->
            when (method.name) {
                "getById" -> rows[args[0]]
                "setStatus" -> { val id = args[0] as String; rows[id] = rows.getValue(id).copy(status = args[1] as String, updatedAt = args[2] as Long, statusPending = true); flow.value = rows.values.toList(); Unit }
                "pendingStatuses" -> rows.values.filter { it.statusPending }
                "acknowledgeStatus" -> { val id = args[0] as String; rows[id]?.takeIf { it.status == args[1] && it.updatedAt == args[2] }?.let { rows[id] = it.copy(statusPending = false) }; Unit }
                "upsertAll", "upsertRemoteAll" -> { (args[0] as List<TransactionEntity>).forEach { if (rows[it.id]?.statusPending != true) rows[it.id] = it }; flow.value = rows.values.toList(); Unit }
                "observeAll" -> flow
                else -> error("Unexpected mutation: ${method.name}")
            }
        } as TransactionDao
        val people = Proxy.newProxyInstance(PersonDao::class.java.classLoader, arrayOf(PersonDao::class.java)) { _, method, _ -> error("Person must not change: ${method.name}") } as PersonDao
        val api = Proxy.newProxyInstance(TransactionsApi::class.java.classLoader, arrayOf(TransactionsApi::class.java)) { _, method, args ->
            when (method.name) {
                "update" -> if (offline) Response.error<TransactionDto>(503, "offline".toResponseBody()) else { remote = args[1] as TransactionDto; Response.success(remote) }
                "getAll" -> Response.success(listOf(remote))
                else -> error("Unexpected API call: ${method.name}")
            }
        } as TransactionsApi
        var repository = TransactionRepositoryImpl(dao, api, people)
        for (status in TransactionStatus.entries) {
            repository.setTransactionStatus("one", status)
            repository = TransactionRepositoryImpl(dao, api, people)
            repository.refreshFromRemote()
            assertEquals(status.name, rows.getValue("one").status)
            assertEquals("ACTIVE", rows.getValue("two").status)
            assertEquals(20.0, rows.getValue("one").amount, 0.0)
            assertTrue(rows.getValue("one").statusPending)
        }
        offline = false
        repository.refreshFromRemote()
        assertEquals("SETTLED", remote.status)
        assertEquals(20.0, remote.amount, 0.0)
        assertFalse(rows.getValue("one").statusPending)
    }
}
