package com.example.netije.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.netije.data.remote.api.ReportsApi
import com.example.netije.domain.model.FinanceSummary
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.PersonWithTransactions
import com.example.netije.domain.model.DayTransactions
import com.example.netije.domain.model.Person
import com.example.netije.domain.model.ReportSummaryRow
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import com.example.netije.domain.usecase.DeletePersonUseCase
import com.example.netije.domain.usecase.DeleteTransactionUseCase
import com.example.netije.domain.usecase.ExportBackupUseCase
import com.example.netije.domain.usecase.GetPersonUseCase
import com.example.netije.domain.usecase.GetTransactionUseCase
import com.example.netije.domain.usecase.ImportBackupUseCase
import com.example.netije.domain.usecase.ObserveCurrentUserUseCase
import com.example.netije.domain.usecase.ObserveDaySummaryUseCase
import com.example.netije.domain.usecase.ObserveFinanceSummaryUseCase
import com.example.netije.domain.usecase.ObserveMonthSummaryUseCase
import com.example.netije.domain.usecase.ObservePersonWithTransactionsUseCase
import com.example.netije.domain.usecase.ObservePersonsWithTransactionsUseCase
import com.example.netije.domain.usecase.ObserveSettingsUseCase
import com.example.netije.domain.usecase.ObserveTransactionsByDayUseCase
import com.example.netije.domain.usecase.ObserveTransactionsUseCase
import com.example.netije.domain.usecase.PickContactUseCase
import com.example.netije.domain.usecase.RefreshDataUseCase
import com.example.netije.domain.usecase.SavePersonUseCase
import com.example.netije.domain.usecase.SaveTransactionUseCase
import com.example.netije.domain.usecase.SetBackupFrequencyUseCase
import com.example.netije.domain.usecase.SetCurrencyUseCase
import com.example.netije.domain.usecase.SetDarkModeUseCase
import com.example.netije.domain.usecase.SetLanguageUseCase
import com.example.netije.domain.usecase.SetPersonStatusUseCase
import com.example.netije.domain.usecase.MarkBackupTakenUseCase
import com.example.netije.domain.usecase.SignInWithGoogleUseCase
import com.example.netije.domain.usecase.SignOutUseCase
import com.example.netije.presentation.util.LocalReportGenerator
import com.example.netije.presentation.util.ReportExportHelper
import com.example.netije.presentation.util.ReportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observePersons: ObservePersonsWithTransactionsUseCase,
    observeSummary: ObserveFinanceSummaryUseCase,
    private val refreshData: com.example.netije.domain.usecase.RefreshDataUseCase,
    private val setPersonStatus: SetPersonStatusUseCase,
    private val deletePerson: DeletePersonUseCase
) : ViewModel() {
    val search = MutableStateFlow("")
    val statusFilter = MutableStateFlow<PersonStatus?>(PersonStatus.ACTIVE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { refreshData() }
        }
    }

    fun setPersonStatusById(id: String, status: PersonStatus) = viewModelScope.launch {
        runCatching { setPersonStatus(id, status) }
    }

    fun deletePersonById(id: String) = viewModelScope.launch {
        runCatching { deletePerson(id) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeState> = combine(
        search,
        statusFilter,
        observeSummary()
    ) { q, status, sum -> Triple(q, status, sum) }
        .flatMapLatest { (q, status, sum) ->
            observePersons(status, q.ifBlank { null })
                .combine(flowOf(sum)) { list, s -> HomeState(q, status, s, list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

data class HomeState(
    val query: String = "",
    val status: PersonStatus? = PersonStatus.ACTIVE,
    val summary: FinanceSummary = FinanceSummary(0.0, 0.0),
    val persons: List<PersonWithTransactions> = emptyList()
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    observeMonth: ObserveMonthSummaryUseCase,
    observeByDay: ObserveTransactionsByDayUseCase,
    private val setPersonStatus: SetPersonStatusUseCase,
    private val deletePerson: DeletePersonUseCase
) : ViewModel() {
    private val now = Calendar.getInstance()
    val year = MutableStateFlow(now.get(Calendar.YEAR))
    val month = MutableStateFlow(now.get(Calendar.MONTH))
    val selectedDate = MutableStateFlow(System.currentTimeMillis())

    fun setPersonStatusById(id: String, status: PersonStatus) = viewModelScope.launch {
        runCatching { setPersonStatus(id, status) }
    }

    fun deletePersonById(id: String) = viewModelScope.launch {
        runCatching { deletePerson(id) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val monthSummary: StateFlow<FinanceSummary> = combine(year, month) { y, m -> y to m }
        .flatMapLatest { (y, m) -> observeMonth(y, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceSummary(0.0, 0.0))

    @OptIn(ExperimentalCoroutinesApi::class)
    val dayGroups: StateFlow<List<DayTransactions>> = combine(year, month) { y, m ->
        val c = Calendar.getInstance()
        c.set(y, m, 1, 0, 0, 0); val s = c.timeInMillis
        c.set(y, m + 1, 1, 0, 0, 0); val e = c.timeInMillis - 1
        s to e
    }.flatMapLatest { (s, e) -> observeByDay(s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun nextMonth() {
        val curSelCal = Calendar.getInstance().apply { timeInMillis = selectedDate.value }
        val selDay = curSelCal.get(Calendar.DAY_OF_MONTH)
        val cal = Calendar.getInstance().apply { set(year.value, month.value, 1) }
        cal.add(Calendar.MONTH, 1)
        year.value = cal.get(Calendar.YEAR); month.value = cal.get(Calendar.MONTH)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val newDay = selDay.coerceAtMost(maxDay)
        cal.set(Calendar.DAY_OF_MONTH, newDay)
        selectedDate.value = cal.timeInMillis
    }

    fun prevMonth() {
        val curSelCal = Calendar.getInstance().apply { timeInMillis = selectedDate.value }
        val selDay = curSelCal.get(Calendar.DAY_OF_MONTH)
        val cal = Calendar.getInstance().apply { set(year.value, month.value, 1) }
        cal.add(Calendar.MONTH, -1)
        year.value = cal.get(Calendar.YEAR); month.value = cal.get(Calendar.MONTH)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val newDay = selDay.coerceAtMost(maxDay)
        cal.set(Calendar.DAY_OF_MONTH, newDay)
        selectedDate.value = cal.timeInMillis
    }
}

@HiltViewModel
class NewPersonViewModel @Inject constructor(
    private val savePerson: com.example.netije.domain.usecase.SavePersonUseCase,
    private val getPerson: com.example.netije.domain.usecase.GetPersonUseCase,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    val editingPersonId: String? = savedStateHandle["personId"]
    val name = MutableStateFlow("")
    val phones = MutableStateFlow<List<String>>(listOf(""))
    val emails = MutableStateFlow<List<String>>(listOf(""))
    val city = MutableStateFlow("")
    val address = MutableStateFlow("")
    val isImportant = MutableStateFlow(false)
    val contactUri = MutableStateFlow<String?>(null)
    val avatarFromContact = MutableStateFlow<String?>(null)
    val customAvatarUrl = MutableStateFlow<String?>(null)
    private val existingStatus = MutableStateFlow<PersonStatus>(PersonStatus.ACTIVE)

    init {
        viewModelScope.launch {
            val existing = editingPersonId?.let { getPerson(it) }
            if (existing != null) {
                name.value = existing.name
                val pl = existing.phones.toMutableList()
                phones.value = if (pl.isEmpty()) listOf("") else pl
                val el = existing.emails.toMutableList()
                emails.value = if (el.isEmpty()) listOf("") else el
                city.value = existing.city.orEmpty()
                address.value = existing.address.orEmpty()
                isImportant.value = existing.isImportant
                contactUri.value = existing.contactLookupUri
                avatarFromContact.value = existing.avatarUrl
                existingStatus.value = existing.status
            } else {
                val picked = com.example.netije.di.PickedContactHolder.consume()
                if (picked != null) {
                    name.value = picked.name
                    val pl = picked.phones.toMutableList()
                    phones.value = if (pl.isEmpty()) listOf("") else pl
                    val el = picked.emails.toMutableList()
                    emails.value = if (el.isEmpty()) listOf("") else el
                    contactUri.value = picked.contactLookupUri
                    avatarFromContact.value = picked.avatarUrl
                }
            }
        }
    }

    fun addPhone() { phones.value = phones.value + "" }
    fun addEmail() { emails.value = emails.value + "" }
    fun removePhone(i: Int) {
        val cur = phones.value.toMutableList()
        if (cur.size > 1 && i in cur.indices) {
            cur.removeAt(i)
            phones.value = cur
        }
    }
    fun removeEmail(i: Int) {
        val cur = emails.value.toMutableList()
        if (cur.size > 1 && i in cur.indices) {
            cur.removeAt(i)
            emails.value = cur
        }
    }
    fun updatePhone(i: Int, v: String) {
        phones.value = phones.value.toMutableList().also { if (i in it.indices) it[i] = v }
    }
    fun updateEmail(i: Int, v: String) {
        emails.value = emails.value.toMutableList().also { if (i in it.indices) it[i] = v }
    }

    suspend fun save(): String? {
        val n = name.value.trim()
        if (n.isBlank()) return null
        return savePerson(
            com.example.netije.domain.model.Person(
                id = editingPersonId.orEmpty(),
                name = n,
                phones = phones.value.map { it.trim() }.filter { it.isNotBlank() },
                emails = emails.value.map { it.trim() }.filter { it.isNotBlank() },
                city = city.value.trim().ifBlank { null },
                address = address.value.trim().ifBlank { null },
                avatarUrl = avatarFromContact.value ?: customAvatarUrl.value,
                isImportant = isImportant.value,
                contactLookupUri = contactUri.value,
                status = existingStatus.value
            )
        )
    }
}

@HiltViewModel
class NewTransactionViewModel @Inject constructor(
    private val save: com.example.netije.domain.usecase.SaveTransactionUseCase,
    private val getTransaction: com.example.netije.domain.usecase.GetTransactionUseCase,
    private val getPerson: com.example.netije.domain.usecase.GetPersonUseCase,
    observePersons: ObservePersonsWithTransactionsUseCase,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    companion object {
        private const val KEY_ATTACHMENTS = "nt_attachments"
    }
    val editingTransactionId: String? =
        savedStateHandle.get<String>("transactionId")?.ifBlank { null }
    val type = MutableStateFlow(TransactionType.RECEIVED)
    val date = MutableStateFlow(System.currentTimeMillis())
    val time = MutableStateFlow(System.currentTimeMillis())
    val personId = MutableStateFlow<String?>(null)
    val personName = MutableStateFlow<String?>(null)
    val purpose = MutableStateFlow("")
    val amount = MutableStateFlow("")
    val note = MutableStateFlow("")
    val currency = MutableStateFlow("TMT")
    private val persistedAttachments: MutableStateFlow<List<String>> = MutableStateFlow(
        (savedStateHandle[KEY_ATTACHMENTS] ?: emptyList<String>()).toList()
    )
    val attachments: StateFlow<List<String>> = persistedAttachments.asStateFlow()

    val personsList: StateFlow<List<PersonWithTransactions>> = observePersons(null, null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            persistedAttachments.collect { list ->
                savedStateHandle[KEY_ATTACHMENTS] = ArrayList(list)
            }
        }
        viewModelScope.launch {
            val eid = editingTransactionId
            if (eid != null) {
                val existing = getTransaction(eid)
                if (existing != null) {
                    type.value = existing.type
                    date.value = existing.date
                    time.value = existing.time
                    purpose.value = existing.purpose.orEmpty()
                    amount.value = if (existing.amount == 0.0) "" else
                        java.text.DecimalFormat("#.########").format(existing.amount)
                    note.value = existing.note.orEmpty()
                    currency.value = existing.currency.ifBlank { "TMT" }
                    persistedAttachments.value = existing.attachments
                    if (personId.value.isNullOrBlank()) {
                        val p = getPerson(existing.personId)
                        personId.value = existing.personId
                        personName.value = p?.name
                    }
                }
            }
        }
    }

    val isEditing: Boolean get() = editingTransactionId != null

    fun preselectPerson(id: String?, name: String?) {
        personId.value = id; personName.value = name
    }
    fun presetDate(d: Long) { date.value = d }

    fun setAmount(newVal: String) {
        var filtered = newVal.filter { it.isDigit() || it == '.' || it == ',' }
        if (filtered.firstOrNull() in listOf('.', ',')) filtered = "0$filtered"
        val firstDot = filtered.indexOfFirst { it == '.' || it == ',' }
        amount.value = if (firstDot == -1) filtered else {
            val head = filtered.substring(0, firstDot + 1)
            val tail = filtered.substring(firstDot + 1).filter { it.isDigit() }.take(2)
            head + tail
        }
    }

    fun addAttachment(path: String) {
        persistedAttachments.value = persistedAttachments.value + path
    }
    fun removeAttachment(i: Int) {
        val cur = persistedAttachments.value.toMutableList()
        if (i in cur.indices) {
            cur.removeAt(i)
            persistedAttachments.value = cur
        }
    }

    suspend fun save(): String? {
        val pid = personId.value ?: return null
        val amt = amount.value.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (pid.isBlank() || amt <= 0.0) return null
        return save(
            com.example.netije.domain.model.Transaction(
                id = editingTransactionId.orEmpty(),
                personId = pid,
                type = type.value,
                amount = amt,
                currency = currency.value,
                purpose = purpose.value.trim().ifBlank { null },
                note = note.value.trim().ifBlank { null },
                attachments = attachments.value,
                date = date.value,
                time = time.value
            )
        )
    }
}

@HiltViewModel
class SelectContactViewModel @Inject constructor(
    observePersons: ObservePersonsWithTransactionsUseCase
) : ViewModel() {
    val search = MutableStateFlow("")
    val statusFilter = MutableStateFlow<PersonStatus?>(PersonStatus.ACTIVE)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state = combine(search, statusFilter) { q, s -> q to s }
        .flatMapLatest { (q, s) -> observePersons(s, q.ifBlank { null }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeUser: ObserveCurrentUserUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val signIn: SignInWithGoogleUseCase,
    private val signOut: SignOutUseCase,
    private val setCurrency: SetCurrencyUseCase,
    private val setLanguage: SetLanguageUseCase,
    private val setDarkMode: SetDarkModeUseCase,
    private val setBackupFrequency: SetBackupFrequencyUseCase,
    private val markBackupTaken: MarkBackupTakenUseCase,
    private val exportBackup: ExportBackupUseCase,
    private val importBackup: ImportBackupUseCase
) : ViewModel() {
    val state: StateFlow<SettingsState> = combine(observeUser(), observeSettings()) { user, s ->
        SettingsState(user = user, settings = s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun onSignIn(idToken: String) = viewModelScope.launch { signIn(idToken) }
    fun onSignOut() = viewModelScope.launch { signOut() }
    fun onCurrency(code: String) = viewModelScope.launch { setCurrency(code) }
    fun onLanguage(lang: com.example.netije.domain.model.AppLanguage) =
        viewModelScope.launch { setLanguage(lang) }
    fun onDarkMode(mode: com.example.netije.domain.model.DarkModeConfig) =
        viewModelScope.launch { setDarkMode(mode) }
    fun onBackupFrequency(
        number: Int,
        unit: com.example.netije.domain.model.BackupUnit,
        nextRunAt: Long?
    ) = viewModelScope.launch { setBackupFrequency(number, unit, nextRunAt) }
    fun markBackupTakenNow() = viewModelScope.launch { markBackupTaken() }
    suspend fun export(): String = exportBackup()
    suspend fun import(json: String): Int = importBackup(json)
}

data class SettingsState(
    val user: com.example.netije.domain.model.UserApp? = null,
    val settings: com.example.netije.domain.model.AppSettings = com.example.netije.domain.model.AppSettings()
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    observePersonWithTransactions: ObservePersonWithTransactionsUseCase,
    private val setStatus: SetPersonStatusUseCase,
    private val deletePerson: com.example.netije.domain.usecase.DeletePersonUseCase,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    private val personId: String = savedStateHandle["personId"] ?: ""

    val state: StateFlow<PersonDetailState> =
        observePersonWithTransactions(personId)
            .map { pwt ->
                PersonDetailState(
                    person = pwt?.person,
                    transactions = pwt?.transactions ?: emptyList(),
                    received = pwt?.totalReceived ?: 0.0,
                    issued = pwt?.totalIssued ?: 0.0,
                    balance = pwt?.balance ?: 0.0,
                    isSettled = pwt?.isSettled == true
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailState())

    fun setStatus(status: PersonStatus) = viewModelScope.launch {
        state.value.person?.let { setStatus(it.id, status) }
    }

    fun delete() = viewModelScope.launch {
        state.value.person?.let { deletePerson(it.id) }
    }
}

data class PersonDetailState(
    val person: com.example.netije.domain.model.Person? = null,
    val transactions: List<com.example.netije.domain.model.Transaction> = emptyList(),
    val received: Double = 0.0,
    val issued: Double = 0.0,
    val balance: Double = 0.0,
    val isSettled: Boolean = false
)

// ------- TransactionDetail Screen --------------------------------------------------------------

data class TransactionDetailState(
    val transaction: Transaction? = null,
    val person: Person? = null,
    val loaded: Boolean = false,
    val notFound: Boolean = false
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val getTransaction: com.example.netije.domain.usecase.GetTransactionUseCase,
    private val getPerson: com.example.netije.domain.usecase.GetPersonUseCase,
    private val deleteTransaction: com.example.netije.domain.usecase.DeleteTransactionUseCase,
    private val reportsApi: ReportsApi,
    @ApplicationContext private val ctx: Context,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    private val transactionId: String = savedStateHandle["transactionId"] ?: ""
    private val _state = MutableStateFlow(TransactionDetailState())
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val tx = getTransaction(transactionId)
            if (tx == null) {
                _state.value = TransactionDetailState(loaded = true, notFound = true)
            } else {
                val person = tx.personId.takeIf { it.isNotBlank() }?.let { getPerson(it) }
                _state.value = TransactionDetailState(
                    transaction = tx,
                    person = person,
                    loaded = true,
                    notFound = false
                )
            }
        }
    }

    suspend fun delete(): Boolean {
        val tx = state.value.transaction ?: return false
        return runCatching { deleteTransaction(tx.id) }.isSuccess
    }

    suspend fun export(format: ReportFormat): Pair<File?, ReportFormat?> = withContext(Dispatchers.IO) {
        val tx = state.value.transaction
            ?: return@withContext null to null
        val person = state.value.person
        val baseName = "transaction_${tx.id.take(8)}"
        val file = LocalReportGenerator.createOutputFile(ctx, baseName, format)
        val success: Boolean = when (format) {
            ReportFormat.CSV -> LocalReportGenerator.writeTransactionCsv(file, tx, person)
            ReportFormat.XLSX -> LocalReportGenerator.writeTransactionXlsx(file, tx, person)
            ReportFormat.PDF -> LocalReportGenerator.writeTransactionPdf(file, tx, person, ctx)
        }
        return@withContext if (success && file.exists() && file.length() > 0L) {
            file to format
        } else {
            runCatching { file.delete() }
            null to null
        }
    }
}

@HiltViewModel
class ReportUserViewModel @Inject constructor(
    observePerson: ObservePersonWithTransactionsUseCase,
    observeAllPersons: ObservePersonsWithTransactionsUseCase,
    observeTransactions: ObserveTransactionsUseCase,
    private val reportsApi: ReportsApi,
    @ApplicationContext private val ctx: Context,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    val preselectedPersonId: String? =
        savedStateHandle.get<String>("personId")?.ifBlank { null }
    val personId = MutableStateFlow<String?>(preselectedPersonId)
    val fromDate = MutableStateFlow(startOfMonthMillis())
    val toDate = MutableStateFlow(System.currentTimeMillis())

    @OptIn(ExperimentalCoroutinesApi::class)
    val persons: StateFlow<List<PersonWithTransactions>> =
        observeAllPersons(null, null).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportUserState> = combine(personId, fromDate, toDate) { id, f, t ->
        Triple(id, f, t)
    }.flatMapLatest { (id, f, t) ->
        val pwtFlow: Flow<PersonWithTransactions?> = id?.let { observePerson(it) } ?: flowOf<PersonWithTransactions?>(null)
        val txFlow = id?.let { observeTransactions(it, f, t) } ?: flowOf<List<Transaction>>(emptyList())
        pwtFlow.combine(txFlow) { pwt, txs ->
            val list = if (id != null) txs else emptyList()
            val recv = list.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
            val iss = list.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
            ReportUserState(
                person = pwt?.person,
                transactions = list,
                received = recv,
                issued = iss,
                balance = recv - iss,
                fromDate = f,
                toDate = t
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUserState())

    fun updatePerson(id: String?) { personId.value = id }
    fun updateFrom(m: Long) { fromDate.value = m }
    fun updateTo(m: Long) { toDate.value = m }

    fun export(format: ReportFormat, currencyCode: String = "TMT") {
        val current = state.value
        val person = current.person ?: return
        val list = current.transactions
        val pwt = com.example.netije.domain.model.PersonWithTransactions(person, list)
        val personName = person.name.filter { it.isLetterOrDigit() }.ifBlank { "user" }
        viewModelScope.launch(Dispatchers.IO) {
            val file = LocalReportGenerator.createOutputFile(ctx, "report_${personName}", format)
            val ok = when (format) {
                ReportFormat.CSV -> LocalReportGenerator.writePersonReportCsv(
                    file, pwt, list, currencyCode, current.fromDate, current.toDate
                )
                ReportFormat.XLSX -> LocalReportGenerator.writePersonReportXlsx(
                    file, pwt, list, currencyCode, current.fromDate, current.toDate
                )
                ReportFormat.PDF -> LocalReportGenerator.writePersonReportPdf(
                    file, pwt, list, currencyCode, current.fromDate, current.toDate, ctx
                )
            }
            if (ok && file.exists() && file.length() > 0L) {
                withContext(Dispatchers.Main) { ReportExportHelper.shareFile(ctx, file, format) }
            } else {
                runCatching { file.delete() }
            }
        }
    }

    companion object {
        fun startOfMonthMillis(): Long {
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.clear(Calendar.MINUTE); c.clear(Calendar.SECOND); c.clear(Calendar.MILLISECOND)
            return c.timeInMillis
        }
    }
}

data class ReportUserState(
    val person: com.example.netije.domain.model.Person? = null,
    val transactions: List<com.example.netije.domain.model.Transaction> = emptyList(),
    val received: Double = 0.0,
    val issued: Double = 0.0,
    val balance: Double = 0.0,
    val fromDate: Long = 0L,
    val toDate: Long = 0L
)

@HiltViewModel
class ReportSummaryViewModel @Inject constructor(
    observePersons: ObservePersonsWithTransactionsUseCase,
    observeTransactions: ObserveTransactionsUseCase,
    private val reportsApi: ReportsApi,
    @ApplicationContext private val ctx: Context
) : ViewModel() {
    val fromDate = MutableStateFlow(startOfMonthMillis())
    val toDate = MutableStateFlow(System.currentTimeMillis())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportSummaryState> = combine(fromDate, toDate) { f, t -> f to t }
        .flatMapLatest { (f, t) ->
            observeTransactions(null, f, t).combine(observePersons(null, null)) { txs, pwts ->
                val summaryRows = pwts.map { pwt ->
                    val txsFor = txs.filter { it.personId == pwt.person.id }
                    val recv = txsFor.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
                    val iss = txsFor.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
                    ReportSummaryRow(pwt.person, txsFor.size, recv, iss, recv - iss)
                }
                ReportSummaryState(
                    rows = summaryRows,
                    totalReceived = summaryRows.sumOf { it.received },
                    totalIssued = summaryRows.sumOf { it.issued },
                    fromDate = f,
                    toDate = t
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportSummaryState())

    fun updateFrom(m: Long) { fromDate.value = m }
    fun updateTo(m: Long) { toDate.value = m }

    fun export(format: ReportFormat, currencyCode: String = "TMT") {
        val f = fromDate.value
        val t = toDate.value
        val current = state.value
        val total = FinanceSummary(current.totalReceived, current.totalIssued)
        val rows = current.rows
        viewModelScope.launch(Dispatchers.IO) {
            val file = LocalReportGenerator.createOutputFile(ctx, "summary_report", format)
            val ok = when (format) {
                ReportFormat.CSV -> LocalReportGenerator.writeSummaryCsv(
                    file, rows, total, currencyCode, f, t
                )
                ReportFormat.XLSX -> LocalReportGenerator.writeSummaryXlsx(
                    file, rows, total, currencyCode, f, t
                )
                ReportFormat.PDF -> LocalReportGenerator.writeSummaryPdf(
                    file, rows, total, currencyCode, f, t, ctx
                )
            }
            if (ok && file.exists() && file.length() > 0L) {
                withContext(Dispatchers.Main) { ReportExportHelper.shareFile(ctx, file, format) }
            } else {
                runCatching { file.delete() }
            }
        }
    }

    companion object {
        fun startOfMonthMillis(): Long {
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.clear(Calendar.MINUTE); c.clear(Calendar.SECOND); c.clear(Calendar.MILLISECOND)
            return c.timeInMillis
        }
    }
}

data class ReportSummaryState(
    val rows: List<ReportSummaryRow> = emptyList(),
    val totalReceived: Double = 0.0,
    val totalIssued: Double = 0.0,
    val fromDate: Long = 0L,
    val toDate: Long = 0L
) {
    val totalBalance: Double get() = totalReceived - totalIssued
}

@HiltViewModel
class ReportTransactionsViewModel @Inject constructor(
    observePersons: ObservePersonsWithTransactionsUseCase,
    observeTransactions: ObserveTransactionsUseCase,
    private val reportsApi: ReportsApi,
    @ApplicationContext private val ctx: Context
) : ViewModel() {
    private data class Quad4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    val personFilter = MutableStateFlow<String?>(null)
    val typeFilter = MutableStateFlow<TransactionType?>(null)
    val fromDate = MutableStateFlow(startOfMonthMillis())
    val toDate = MutableStateFlow(System.currentTimeMillis())

    @OptIn(ExperimentalCoroutinesApi::class)
    val persons: StateFlow<List<PersonWithTransactions>> =
        observePersons(null, null).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportTransactionsState> =
        combine(personFilter, typeFilter, fromDate, toDate) { pid, t, f, to ->
            Quad4(pid, t, f, to)
        }.flatMapLatest { (pid, t, f, to) ->
            observeTransactions(pid, f, to).map { txs ->
                val filtered = if (t != null) txs.filter { it.type == t } else txs
                val recv = filtered.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
                val iss = filtered.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
                ReportTransactionsState(filtered, recv, iss, f, to)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportTransactionsState())

    fun setPerson(id: String?) { personFilter.value = id }
    fun setType(t: TransactionType?) { typeFilter.value = t }
    fun updateFrom(m: Long) { fromDate.value = m }
    fun updateTo(m: Long) { toDate.value = m }

    fun export(format: ReportFormat, currencyCode: String = "TMT") {
        val f = fromDate.value
        val t = toDate.value
        val current = state.value
        val txs = current.transactions
        val personsById = persons.value.associate { it.person.id to it.person }
        viewModelScope.launch(Dispatchers.IO) {
            val file = LocalReportGenerator.createOutputFile(ctx, "transactions_report", format)
            val ok = when (format) {
                ReportFormat.CSV -> LocalReportGenerator.writeTransactionsCsv(
                    file, txs, personsById, currencyCode,
                    current.received, current.issued, f, t
                )
                ReportFormat.XLSX -> LocalReportGenerator.writeTransactionsXlsx(
                    file, txs, personsById, currencyCode,
                    current.received, current.issued, f, t
                )
                ReportFormat.PDF -> LocalReportGenerator.writeTransactionsPdf(
                    file, txs, personsById, currencyCode,
                    current.received, current.issued, f, t, ctx
                )
            }
            if (ok && file.exists() && file.length() > 0L) {
                withContext(Dispatchers.Main) { ReportExportHelper.shareFile(ctx, file, format) }
            } else {
                runCatching { file.delete() }
            }
        }
    }

    companion object {
        fun startOfMonthMillis(): Long {
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.clear(Calendar.MINUTE); c.clear(Calendar.SECOND); c.clear(Calendar.MILLISECOND)
            return c.timeInMillis
        }
    }
}

data class ReportTransactionsState(
    val transactions: List<com.example.netije.domain.model.Transaction> = emptyList(),
    val received: Double = 0.0,
    val issued: Double = 0.0,
    val fromDate: Long = 0L,
    val toDate: Long = 0L
) {
    val balance: Double get() = received - issued
}
