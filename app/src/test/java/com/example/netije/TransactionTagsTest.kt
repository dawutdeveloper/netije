package com.example.netije

import com.example.netije.data.backup.BackupCodec
import com.example.netije.data.backup.BackupDocument
import com.example.netije.data.local.db.toDomain
import com.example.netije.data.local.db.toEntity
import com.example.netije.data.remote.dto.TransactionDto
import com.example.netije.domain.model.*
import com.example.netije.domain.repository.TransactionRepository
import com.example.netije.domain.usecase.SaveTransactionUseCase
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TransactionTagsTest {
    private val transaction = Transaction(
        "transaction", "person", TransactionType.RECEIVED, 300.0,
        note = "Monthly rent", attachments = listOf("receipt.pdf"), date = 1, time = 1,
        tags = listOf("#rent", "#work")
    )

    @Test fun tagsAcceptHashesSpacesCommasUnicodeAndDeduplicateCase() {
        assertEquals(listOf("#work", "#rent", "#ýol", "#работа"), parseTransactionTags("#Work, rent; ##work #Ýol #Работа"))
        assertEquals(emptyList<String>(), parseTransactionTags(" # , ; "))
        assertEquals(listOf("#café"), parseTransactionTags("#cafe\u0301"))
    }

    @Test fun hashtagSearchMatchesWholeTagsAndAllRequestedTags() {
        assertTrue(transaction.matchesSearch("#RENT"))
        assertTrue(transaction.matchesSearch("#work, #rent"))
        assertFalse(transaction.matchesSearch("#rent #missing"))
        assertFalse(transaction.copy(tags = listOf("#rental")).matchesSearch("#rent"))
        assertFalse(transaction.copy(tags = emptyList()).matchesSearch("#rent"))
    }

    @Test fun searchAlsoSupportsNamesNotesAndTagWordsTogether() {
        assertTrue(transaction.matchesSearch("Alice #rent", "Alice Smith"))
        assertTrue(transaction.matchesSearch("monthly #work"))
        assertTrue(transaction.matchesSearch("rent"))
        assertFalse(transaction.matchesSearch("Bob #rent", "Alice Smith"))
        assertTrue(transaction.matchesSearch("#"))
        assertFalse(transaction.copy(tags = emptyList()).matchesSearch("#"))
    }

    @Test fun normalizationDoesNotDependOnPhoneLanguage() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals(listOf("#invoice"), parseTransactionTags("#INVOICE"))
            assertTrue(transaction.copy(tags = listOf("#invoice")).matchesSearch("#INVOICE"))
        } finally { Locale.setDefault(original) }
    }

    @Test fun tagsSurviveDatabaseApiAndBackupRoundTrips() {
        assertEquals(transaction, transaction.toEntity().toDomain())
        assertEquals(transaction, TransactionDto.fromDomain(transaction).toDomain())
        val json = Gson().toJson(BackupDocument(persons = listOf(Person("person", "Alice")), transactions = listOf(transaction)))
        assertEquals(transaction, BackupCodec.decode(json).transactions.single())
    }

    @Test fun legacyRecordsWithoutTagsRemainReadableAndInvalidTagsAreRejected() {
        val json = """{"persons":[{"id":"person","name":"Alice"}],"transactions":[{"id":"transaction","personId":"person","type":"RECEIVED","amount":300,"date":1,"time":1}]}"""
        assertTrue(BackupCodec.decode(json).transactions.single().tags.isEmpty())
        assertTrue(TransactionDto.fromDomain(transaction).copy(tags = null).toDomain().tags.isEmpty())
        assertTrue(runCatching { BackupCodec.decode(json.replace("\"amount\":300", "\"tags\":[12],\"amount\":300")) }.isFailure)
    }

    @Test fun homeSearchKeepsMatchingPaymentWithItsGroupAndUnchangedBalance() {
        val child = transaction.copy(id = "payment", parentTransactionId = transaction.id,
            type = TransactionType.ISSUED, amount = 50.0, tags = listOf("#food"))
        val person = PersonWithTransactions(Person("person", "Alice"), listOf(transaction, child))
        val result = person.matchingTransactionGroups("#food")!!
        assertEquals(250.0, result.balance, 0.0)
        assertEquals(listOf(transaction, child), result.transactions)
        assertNull(person.matchingTransactionGroups("#missing"))
        assertEquals(person, person.matchingTransactionGroups("Alice"))
    }

    @Test fun editingOnlyTagsPreservesAmountsNotesAttachmentsAndPaymentLinks() = runBlocking {
        val child = transaction.copy(id = "payment", parentTransactionId = "parent")
        val rows = linkedMapOf("parent" to transaction.copy(id = "parent"), child.id to child)
        val repo = object : TransactionRepository {
            override suspend fun getTransaction(id: String) = rows[id]
            override suspend fun upsertTransaction(transaction: Transaction): String { rows[transaction.id] = transaction; return transaction.id }
            override fun observeTransactions(personId: String?, fromDate: Long?, toDate: Long?) = flowOf(rows.values.toList())
            override fun observeTransactionsGroupedByDay(fromDate: Long?, toDate: Long?): Flow<List<DayTransactions>> = flowOf(emptyList())
            override suspend fun deleteTransaction(id: String) {}
            override suspend fun refreshFromRemote() {}
        }
        SaveTransactionUseCase(repo)(child.copy(tags = listOf("New", "#NEW")))
        val saved = rows.getValue(child.id)
        assertEquals(listOf("#new"), saved.tags)
        assertEquals(child.amount, saved.amount, 0.0)
        assertEquals(child.note, saved.note)
        assertEquals(child.attachments, saved.attachments)
        assertEquals(child.parentTransactionId, saved.parentTransactionId)
        assertTrue(saved.changes.isEmpty())
        SaveTransactionUseCase(repo)(saved.copy(tags = emptyList()))
        assertTrue(rows.getValue(child.id).tags.isEmpty())
    }
}
