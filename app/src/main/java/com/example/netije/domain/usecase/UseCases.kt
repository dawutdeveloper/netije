package com.example.netije.domain.usecase

import com.example.netije.domain.model.AppLanguage
import com.example.netije.domain.model.DarkModeConfig
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.UserApp
import com.example.netije.domain.model.DayTransactions
import com.example.netije.domain.repository.AuthRepository
import com.example.netije.domain.repository.BackupRepository
import com.example.netije.domain.repository.ContactsRepository
import com.example.netije.domain.repository.FinanceRepository
import com.example.netije.domain.repository.PersonRepository
import com.example.netije.domain.repository.SettingsRepository
import com.example.netije.domain.repository.TransactionRepository
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
        val id = transaction.id.ifEmpty { UUID.randomUUID().toString() }
        return repo.upsertTransaction(transaction.copy(id = id, updatedAt = System.currentTimeMillis()))
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
    operator fun invoke(): Flow<com.example.netije.domain.model.AppSettings> = repo.settings
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
        unit: com.example.netije.domain.model.BackupUnit,
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
