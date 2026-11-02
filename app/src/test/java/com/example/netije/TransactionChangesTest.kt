package com.example.netije

import com.example.netije.data.local.db.toDomain
import com.example.netije.data.local.db.toEntity
import com.example.netije.data.remote.dto.TransactionDto
import com.example.netije.domain.model.DayTransactions
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import com.example.netije.domain.repository.TransactionRepository
import com.example.netije.domain.usecase.SaveTransactionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TransactionChangesTest {
    private class Repository(var transaction: Transaction? = null) : TransactionRepository {
        override suspend fun getTransaction(id: String) = transaction?.takeIf { it.id == id }
        override suspend fun upsertTransaction(transaction: Transaction): String {
            this.transaction = transaction
            return transaction.id
        }
        override fun observeTransactions(personId: String?, fromDate: Long?, toDate: Long?) =
            flowOf(listOfNotNull(transaction))
        override fun observeTransactionsGroupedByDay(fromDate: Long?, toDate: Long?): Flow<List<DayTransactions>> = flowOf(emptyList())
        override suspend fun deleteTransaction(id: String) { transaction = null }
        override suspend fun refreshFromRemote() {}
    }

    private fun original() = Transaction(
        id = "transaction", personId = "person", type = TransactionType.RECEIVED,
        amount = 100.0, date = 10, time = 10, createdAt = 1,
        attachments = listOf("content://documents/receipt.pdf")
    )

    @Test fun increasesAndDecreasesKeepHistoryWithoutDoubleCounting() = runBlocking {
        val repo = Repository(original())
        val save = SaveTransactionUseCase(repo)
        save(original().copy(amount = 140.0))
        save(repo.transaction!!.copy(amount = 80.0))
        val result = repo.transaction!!
        assertEquals(listOf(40.0, -60.0), result.changes.map { it.difference })
        assertEquals(140.0, result.changes.last().previousAmount, 0.0)
        assertEquals(80.0, result.changes.last().amount, 0.0)
        assertEquals(2, result.changes.map { it.id }.distinct().size)
        assertEquals(1L, result.createdAt)
        assertEquals(original().attachments, result.attachments)
        assertEquals(80.0, PersonWithTransactions(Person("person", "Name"), listOf(result)).balance, 0.0)
    }

    @Test fun noteOnlyEditDoesNotAddAmountChange() = runBlocking {
        val repo = Repository(original())
        SaveTransactionUseCase(repo)(original().copy(note = "Updated"))
        assertTrue(repo.transaction!!.changes.isEmpty())
        assertEquals("Updated", repo.transaction!!.note)
    }

    @Test fun editingAnArchivedTransactionPreservesItsStatus() = runBlocking {
        val repo = Repository(original().copy(status = com.example.netije.domain.model.TransactionStatus.ARCHIVED))
        SaveTransactionUseCase(repo)(original().copy(amount = 120.0))
        assertEquals(com.example.netije.domain.model.TransactionStatus.ARCHIVED, repo.transaction!!.status)
        assertEquals(120.0, repo.transaction!!.amount, 0.0)
    }

    @Test fun newTransactionHasNoEditHistoryAndUsesTmt() = runBlocking {
        val repo = Repository()
        SaveTransactionUseCase(repo)(original().copy(id = "", currency = "USD"))
        assertTrue(repo.transaction!!.id.isNotBlank())
        assertTrue(repo.transaction!!.changes.isEmpty())
        assertEquals("TMT", repo.transaction!!.currency)
    }

    @Test fun typeChangeIsRecorded() = runBlocking {
        val repo = Repository(original())
        SaveTransactionUseCase(repo)(original().copy(type = TransactionType.ISSUED))
        val change = repo.transaction!!.changes.single()
        assertEquals(TransactionType.RECEIVED, change.previousType)
        assertEquals(TransactionType.ISSUED, change.type)
        assertEquals(0.0, change.difference, 0.0)
    }

    @Test fun historySurvivesDatabaseAndApiRoundTrips() = runBlocking {
        val repo = Repository(original())
        SaveTransactionUseCase(repo)(original().copy(amount = 130.0))
        val saved = repo.transaction!!
        assertEquals(saved, saved.toEntity().toDomain())
        assertEquals(saved, TransactionDto.fromDomain(saved).toDomain())
        assertTrue(TransactionDto.fromDomain(saved).copy(changes = null).toDomain().changes.isEmpty())
    }
}
