package com.gazna

import com.gazna.data.backup.BackupCodec
import com.gazna.data.backup.BackupDocument
import com.gazna.data.local.db.toDomain
import com.gazna.data.local.db.toEntity
import com.gazna.data.remote.dto.TransactionDto
import com.gazna.domain.model.*
import com.gazna.domain.repository.TransactionRepository
import com.gazna.domain.usecase.SaveTransactionUseCase
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SubTransactionTest {
    private class Repository(original: Transaction) : TransactionRepository {
        val rows = linkedMapOf(original.id to original)
        override suspend fun getTransaction(id: String) = rows[id]
        override suspend fun upsertTransaction(transaction: Transaction): String {
            rows[transaction.id] = transaction
            return transaction.id
        }
        override fun observeTransactions(personId: String?, fromDate: Long?, toDate: Long?) = flowOf(rows.values.toList())
        override fun observeTransactionsGroupedByDay(fromDate: Long?, toDate: Long?): Flow<List<DayTransactions>> = flowOf(emptyList())
        override suspend fun deleteTransaction(id: String) { rows.remove(id) }
        override suspend fun refreshFromRemote() {}
    }

    private val original = Transaction(
        id = "original", personId = "person", type = TransactionType.RECEIVED, amount = 100.0,
        date = 10, time = 10, note = "Initial deposit", attachments = listOf("receipt.pdf")
    )
    private fun payment(id: String = "payment", amount: Double = 50.0) = Transaction(
        id = id, parentTransactionId = original.id, personId = "person",
        type = TransactionType.ISSUED, amount = amount, note = "Took money for groceries", date = 20, time = 20,
        attachments = listOf("withdrawal.pdf")
    )

    @Test fun original100AndWithdrawal50RemainSeparateAndBalanceIs50() = runBlocking {
        val repo = Repository(original)
        SaveTransactionUseCase(repo)(payment())
        assertEquals(original, repo.rows.getValue(original.id))
        val entries = repo.rows.values.toList()
        val group = entries.groupTransactions().single()
        assertEquals(100.0, group.transaction.amount, 0.0)
        assertEquals(TransactionType.RECEIVED, group.transaction.type)
        assertEquals(50.0, group.children.single().amount, 0.0)
        assertEquals(TransactionType.ISSUED, group.children.single().type)
        assertEquals("Took money for groceries", group.children.single().note)
        assertEquals(listOf("receipt.pdf"), group.transaction.attachments)
        assertEquals(listOf("withdrawal.pdf"), group.children.single().attachments)
        val totals = PersonWithTransactions(Person("person", "Name"), entries)
        assertEquals(100.0, totals.totalReceived, 0.0)
        assertEquals(50.0, totals.totalIssued, 0.0)
        assertEquals(50.0, totals.balance, 0.0)
        assertEquals(totals.balance, group.balance, 0.0)
    }

    @Test fun repeatedSaveDoesNotDuplicateThePaymentAndLaterPaymentsAccumulate() = runBlocking {
        val repo = Repository(original)
        val save = SaveTransactionUseCase(repo)
        save(payment())
        save(payment())
        save(payment("second", 25.0))
        assertEquals(3, repo.rows.size)
        assertEquals(25.0, repo.rows.values.toList().groupTransactions().single().balance, 0.0)
    }

    @Test fun editingAPaymentKeepsItsParentAndDoesNotOverwriteTheDeposit() = runBlocking {
        val repo = Repository(original)
        val save = SaveTransactionUseCase(repo)
        save(payment())
        save(payment().copy(amount = 30.0, parentTransactionId = null, note = "Corrected reason"))
        assertEquals(original, repo.rows[original.id])
        assertEquals(original.id, repo.rows.getValue("payment").parentTransactionId)
        assertEquals(70.0, repo.rows.values.toList().groupTransactions().single().balance, 0.0)
        assertEquals(1, repo.rows.getValue("payment").changes.size)
    }

    @Test fun invalidParentsAreRejectedBeforeSaving() = runBlocking {
        val repo = Repository(original)
        val save = SaveTransactionUseCase(repo)
        for (invalid in listOf(
            payment().copy(parentTransactionId = "missing"),
            payment().copy(parentTransactionId = "payment"),
            payment().copy(personId = "different-person"),
            payment().copy(amount = Double.NaN)
        )) {
            assertTrue(runCatching { save(invalid) }.isFailure)
        }
        assertEquals(listOf(original), repo.rows.values.toList())
        save(payment())
        assertTrue(runCatching { save(payment("nested").copy(parentTransactionId = "payment")) }.isFailure)
        assertTrue(runCatching { save(original.copy(personId = "different-person")) }.isFailure)
    }

    @Test fun relationAndReasonSurviveDatabaseApiAndBackupRoundTrips() = runBlocking {
        val repo = Repository(original)
        SaveTransactionUseCase(repo)(payment())
        val saved = repo.rows.getValue("payment")
        assertEquals(saved, saved.toEntity().toDomain())
        assertEquals(saved, TransactionDto.fromDomain(saved).toDomain())
        val document = BackupDocument(persons = listOf(Person("person", "Name")), transactions = repo.rows.values.toList())
        val restored = BackupCodec.decode(Gson().toJson(document)).transactions.groupTransactions().single()
        assertEquals(saved, restored.children.single())
        assertEquals(50.0, restored.balance, 0.0)
    }

    @Test fun filteredReportsKeepAPaymentEvenWhenOriginalIsOutsideTheDateRange() {
        val entry = payment()
        val filtered = listOf(entry).groupTransactions()
        assertEquals(listOf(entry), filtered.map { it.transaction })
        assertTrue(filtered.single().children.isEmpty())
        assertEquals(-50.0, filtered.single().balance, 0.0)
    }

    @Test fun groupShowsOriginalFirstAndPaymentsInDateOrder() {
        val groups = listOf(payment("later").copy(date = 30, time = 30), payment(), original).groupTransactions()
        assertEquals(original.id, groups.single().transaction.id)
        assertEquals(listOf("payment", "later"), groups.single().children.map { it.id })
    }

    @Test fun adding500To300Shows800AndKeepsInitialEntryInHistory() = runBlocking {
        val deposit = original.copy(amount = 300.0)
        val repo = Repository(deposit)
        SaveTransactionUseCase(repo)(payment(amount = 500.0).copy(type = TransactionType.RECEIVED))
        val group = repo.rows.values.toList().groupTransactions().single()
        assertEquals(800.0, group.balance, 0.0)
        assertEquals(300.0, group.transaction.amount, 0.0)
        assertEquals(500.0, group.children.single().amount, 0.0)
        assertEquals(800.0, PersonWithTransactions(Person("person", "Name"), repo.rows.values.toList()).balance, 0.0)
    }

    @Test fun balanceHandlesWithdrawalsZeroAndNegativeWithoutChangingHistory() = runBlocking {
        val repo = Repository(original.copy(amount = 300.0))
        val save = SaveTransactionUseCase(repo)
        save(payment("first", 300.0))
        assertEquals(0.0, repo.rows.values.toList().groupTransactions().single().balance, 0.0)
        save(payment("second", 50.0))
        assertEquals(-50.0, repo.rows.values.toList().groupTransactions().single().balance, 0.0)
        assertEquals(300.0, repo.rows.getValue("original").amount, 0.0)
        assertEquals(3, repo.rows.size)
    }
}
