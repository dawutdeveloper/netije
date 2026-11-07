package com.gazna.domain.usecase

import com.gazna.domain.model.AppLanguage
import com.gazna.domain.model.DarkModeConfig
import com.gazna.domain.model.FinanceSummary
import com.gazna.domain.model.Person
import com.gazna.domain.model.PersonStatus
import com.gazna.domain.model.PersonWithTransactions
import com.gazna.domain.model.Transaction
import com.gazna.domain.model.UserApp
import com.gazna.domain.model.DayTransactions
import com.gazna.domain.repository.AuthRepository
import com.gazna.domain.repository.BackupRepository
import com.gazna.domain.repository.ContactsRepository
import com.gazna.domain.repository.FinanceRepository
import com.gazna.domain.repository.PersonRepository
import com.gazna.domain.repository.SettingsRepository
import com.gazna.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID
import javax.inject.Inject

class ObservePersonsUseCase @Inject constructor(private val repo: PersonRepository) {
    operator fun invoke(status: PersonStatus? = null, search: String? = null): Flow<List<Person>> =
        repo.observePersons(status, search)
}

class ObservePersonsWithTransactionsUseCase @Inject constructor(private val repo: PersonRepository) {
    operator fun invoke(
        status: PersonStatus? = null,
        search: String? = null
    ): Flow<List<PersonWithTransactions>> =
        repo.observeAllPersonsWithTransactions(status, search)
}

class ObservePersonWithTransactionsUseCase @Inject constructor(private val repo: PersonRepository) {
    operator fun invoke(personId: String): Flow<PersonWithTransactions?> =
        repo.observePersonWithTransactions(personId)
}

class SavePersonUseCase @Inject constructor(private val repo: PersonRepository) {
    suspend operator fun invoke(person: Person): String {
        val id = person.id.ifEmpty { UUID.randomUUID().toString() }
        return repo.upsertPerson(person.copy(id = id, updatedAt = System.currentTimeMillis()))
    }
}

class DeletePersonUseCase @Inject constructor(private val repo: PersonRepository) {
    suspend operator fun invoke(id: String) = repo.deletePerson(id)
}

class SetPersonStatusUseCase @Inject constructor(private val repo: PersonRepository) {
    suspend operator fun invoke(id: String, status: PersonStatus) = repo.setPersonStatus(id, status)
}

class ObserveTransactionsUseCase @Inject constructor(private val repo: TransactionRepository) {
    operator fun invoke(
        personId: String? = null,
        fromDate: Long? = null,
        toDate: Long? = null
    ): Flow<List<Transaction>> = repo.observeTransactions(personId, fromDate, toDate)
}

class ObserveTransactionsByDayUseCase @Inject constructor(private val repo: TransactionRepository) {
    operator fun invoke(fromDate: Long? = null, toDate: Long? = null): Flow<List<DayTransactions>> =
        repo.observeTransactionsGroupedByDay(fromDate, toDate)
}

class SaveTransactionUseCase @Inject constructor(private val repo: TransactionRepository) {
    suspend operator fun invoke(transaction: Transaction): String {
        require(transaction.amount.isFinite() && transaction.amount > 0)
        val id = transaction.id.ifEmpty { UUID.randomUUID().toString() }
        val existing = if (transaction.id.isNotEmpty()) repo.getTransaction(id) else null
        if (existing != null && existing.personId != transaction.personId) {
            require(repo.observeTransactions().firstOrNull().orEmpty().none { it.parentTransactionId == id }) {
                "An original transaction with linked payments cannot change person"
            }
        }
        val parentId = existing?.parentTransactionId ?: transaction.parentTransactionId
        val parent = parentId?.let {
            require(it != id)
            requireNotNull(repo.getTransaction(it)) { "Original transaction no longer exists" }.also { original ->
                require(original.parentTransactionId == null && original.personId == transaction.personId)
            }
        }
        val now = System.currentTimeMillis()
        val changes = existing?.changes.orEmpty().toMutableList()
        if (existing != null && (existing.amount != transaction.amount || existing.type != transaction.type)) {
            changes += com.gazna.domain.model.TransactionChange(
                id = UUID.randomUUID().toString(),
                previousAmount = existing.amount,
                amount = transaction.amount,
                previousType = existing.type,
                type = transaction.type,
                changedAt = now
            )
        }
        return repo.upsertTransaction(transaction.copy(
            id = id,
            currency = "TMT",
            tags = com.gazna.domain.model.normalizeTransactionTags(transaction.tags),
            parentTransactionId = parentId,
            status = existing?.status ?: parent?.status ?: transaction.status,
            createdAt = existing?.createdAt ?: transaction.createdAt,
            updatedAt = now,
            changes = changes
        ))
    }
}

class DeleteTransactionUseCase @Inject constructor(private val repo: TransactionRepository) {
    suspend operator fun invoke(id: String) = repo.deleteTransaction(id)
}

class GetTransactionUseCase @Inject constructor(private val repo: TransactionRepository) {
    suspend operator fun invoke(id: String): Transaction? = repo.getTransaction(id)
}

class GetPersonUseCase @Inject constructor(private val repo: PersonRepository) {
    suspend operator fun invoke(id: String): Person? = repo.getPerson(id)
}

class RefreshDataUseCase @Inject constructor(
    private val personRepo: PersonRepository,
    private val transactionRepo: TransactionRepository,
    private val authRepo: AuthRepository
) {
    suspend operator fun invoke() {
        val user = authRepo.currentUser.firstOrNull()
        user?.id?.let { uid ->
            authRepo.syncLocalDataToServer(uid)
        }
        personRepo.refreshFromRemote()
        transactionRepo.refreshFromRemote()
    }
}

class ObserveFinanceSummaryUseCase @Inject constructor(private val repo: FinanceRepository) {
    operator fun invoke(): Flow<FinanceSummary> = repo.observeSummary()
}

class ObserveMonthSummaryUseCase @Inject constructor(private val repo: FinanceRepository) {
    operator fun invoke(year: Int, month: Int): Flow<FinanceSummary> =
        repo.observeMonthSummary(year, month)
}

class ObserveDaySummaryUseCase @Inject constructor(private val repo: FinanceRepository) {
    operator fun invoke(date: Long): Flow<FinanceSummary> = repo.observeDaySummary(date)
}

class SignInWithGoogleUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke(idToken: String): UserApp? = repo.signInWithGoogle(idToken)
}

class SignOutUseCase @Inject constructor(private val repo: AuthRepository) {
    suspend operator fun invoke() = repo.signOut()
}

class ObserveCurrentUserUseCase @Inject constructor(private val repo: AuthRepository) {
    operator fun invoke(): Flow<UserApp?> = repo.currentUser
}

class ObserveSettingsUseCase @Inject constructor(private val repo: SettingsRepository) {
    operator fun invoke(): Flow<com.gazna.domain.model.AppSettings> = repo.settings
}

class SetCurrencyUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke(code: String) = repo.setCurrency(code)
}

class SetLanguageUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke(language: AppLanguage) = repo.setLanguage(language)
}

class SetDarkModeUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke(mode: DarkModeConfig) = repo.setDarkMode(mode)
}

class SetBackupFrequencyUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke(
        number: Int,
        unit: com.gazna.domain.model.BackupUnit,
        nextRunAt: Long?
    ) = repo.setBackupFrequency(number, unit, nextRunAt)
}

class MarkBackupTakenUseCase @Inject constructor(private val repo: SettingsRepository) {
    suspend operator fun invoke() = repo.markBackupTaken()
}

class ExportBackupUseCase @Inject constructor(private val repo: BackupRepository) {
    suspend operator fun invoke(): String = repo.exportToJson()
}

class ImportBackupUseCase @Inject constructor(private val repo: BackupRepository) {
    suspend operator fun invoke(json: String): Int = repo.importFromJson(json)
}

class PickContactUseCase @Inject constructor(private val repo: ContactsRepository) {
    suspend operator fun invoke(): Person? = repo.pickContact()
}
