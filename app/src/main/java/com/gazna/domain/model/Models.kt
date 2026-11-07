package com.gazna.domain.model

enum class TransactionType {
    RECEIVED,
    ISSUED;

    val isPositive: Boolean get() = this == RECEIVED

    companion object {
        fun fromString(value: String?) = entries.firstOrNull { it.name == value } ?: RECEIVED
    }
}

enum class TransactionStatus {
    ACTIVE, ARCHIVED, SETTLED;

    companion object {
        fun fromString(value: String?) = entries.firstOrNull { it.name == value } ?: ACTIVE
    }
}

// Retained for compatibility with older backups and servers. Status UI belongs to transactions.
enum class PersonStatus {
    ACTIVE,
    ARCHIVED,
    SETTLED;

    companion object {
        fun fromString(value: String) = entries.firstOrNull { it.name == value } ?: ACTIVE
    }
}

enum class AppLanguage(val code: String, val displayName: String) {
    RU("ru", "Русский"),
    TM("tk", "Türkmençe"),
    EN("en", "English"),
    TR("tr", "Türkçe");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: RU
    }
}

enum class DarkModeConfig {
    ON, OFF, SYSTEM;

    companion object {
        fun fromString(value: String?) = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class UserApp(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val googleIdToken: String? = null,
    val accessToken: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Person(
    val id: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val city: String? = null,
    val address: String? = null,
    val avatarUrl: String? = null,
    val contactLookupUri: String? = null,
    val isImportant: Boolean = false,
    val status: PersonStatus = PersonStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val initials: String
        get() {
            val parts = name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2)
            if (parts.isNotEmpty()) return parts.joinToString("")
            return name.firstOrNull()?.uppercase()?.toString() ?: "?"
        }

    val firstPhone: String? get() = phones.firstOrNull()
    val firstEmail: String? get() = emails.firstOrNull()
}

data class TransactionChange(
    val id: String,
    val previousAmount: Double,
    val amount: Double,
    val previousType: TransactionType,
    val type: TransactionType,
    val changedAt: Long
) {
    val difference: Double get() = amount - previousAmount
}

data class Transaction(
    val id: String,
    val personId: String,
    val type: TransactionType,
    val amount: Double,
    val currency: String = "TMT",
    val purpose: String? = null,
    val note: String? = null,
    val attachments: List<String> = emptyList(),
    val date: Long,
    val time: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val changes: List<TransactionChange> = emptyList(),
    val status: TransactionStatus = TransactionStatus.ACTIVE,
    val parentTransactionId: String? = null,
    val tags: List<String> = emptyList()
)

data class TransactionGroup(val transaction: Transaction, val children: List<Transaction>) {
    val balance: Double get() = (listOf(transaction) + children).sumOf {
        if (it.type == TransactionType.RECEIVED) it.amount else -it.amount
    }
}

/** Keep each payment beneath its original; filtered-out parents never hide payments. */
fun List<Transaction>.groupTransactions(): List<TransactionGroup> {
    val ids = map { it.id }.toSet()
    val children = filter { it.parentTransactionId in ids }.groupBy { it.parentTransactionId }
    return filter { it.parentTransactionId !in ids }.map {
        TransactionGroup(it, children[it.id].orEmpty().sortedWith(compareBy<Transaction> { t -> t.date }.thenBy { t -> t.time }))
    }
}

data class PersonWithTransactions(
    val person: Person,
    val transactions: List<Transaction> = emptyList()
) {
    val totalReceived: Double
        get() = transactions.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }

    val totalIssued: Double
        get() = transactions.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }

    val balance: Double get() = totalReceived - totalIssued

    val isSettled: Boolean get() = kotlin.math.abs(balance) < 0.005 && transactions.isNotEmpty()
}

data class FinanceSummary(
    val totalReceived: Double,
    val totalIssued: Double
) {
    val balance: Double get() = totalReceived - totalIssued
}

data class ReportSummaryRow(
    val person: Person,
    val count: Int,
    val received: Double,
    val issued: Double,
    val balance: Double,
    val transactions: List<Transaction> = emptyList()
)

data class DayTransactions(
    val date: Long,
    val transactions: List<Transaction>,
    val personMap: Map<String, Person>
) {
    val totalReceived: Double
        get() = transactions.filter { it.type == TransactionType.RECEIVED }.sumOf { it.amount }
    val totalIssued: Double
        get() = transactions.filter { it.type == TransactionType.ISSUED }.sumOf { it.amount }
    val balance: Double get() = totalReceived - totalIssued
}

data class AppSettings(
    val currencyCode: String = "TMT",
    val language: AppLanguage = AppLanguage.RU,
    val darkMode: DarkModeConfig = DarkModeConfig.SYSTEM,
    val lastBackupAt: Long? = null,
    val backupNumber: Int? = null,
    val backupUnit: BackupUnit = BackupUnit.HOUR,
    val backupNextRunAt: Long? = null
)

enum class BackupUnit(val millisPerUnit: Long) {
    HOUR(60L * 60L * 1000L),
    DAY(24L * 60L * 60L * 1000L),
    WEEK(7L * 24L * 60L * 60L * 1000L),
    MONTH(30L * 24L * 60L * 60L * 1000L);

    companion object {
        fun fromName(name: String?): BackupUnit =
            values().firstOrNull { it.name == name } ?: HOUR
    }
}

fun backupMillis(number: Int?, unit: BackupUnit): Long? {
    val n = number ?: return null
    if (n <= 0) return null
    return n.toLong() * unit.millisPerUnit
}
