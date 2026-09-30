package com.example.netije.data.remote.dto

import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import com.example.netije.domain.model.UserApp
import java.util.UUID

data class UserDto(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(
        googleIdToken: String? = null,
        accessToken: String? = null
    ): UserApp {
        return UserApp(
            id = id,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl,
            googleIdToken = googleIdToken,
            accessToken = accessToken,
            createdAt = createdAt
        )
    }
}

data class GoogleSignInRequest(val idToken: String)
data class AuthResponse(val user: UserDto, val accessToken: String)

data class PersonDto(
    val id: String? = null,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val city: String? = null,
    val address: String? = null,
    val avatarUrl: String? = null,
    val contactLookupUri: String? = null,
    val isImportant: Boolean = false,
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Person = Person(
        id = id ?: UUID.randomUUID().toString(),
        name = name,
        phones = phones,
        emails = emails,
        city = city,
        address = address,
        avatarUrl = avatarUrl,
        contactLookupUri = contactLookupUri,
        isImportant = isImportant,
        status = PersonStatus.fromString(status),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(person: Person) = PersonDto(
            id = person.id,
            name = person.name,
            phones = person.phones,
            emails = person.emails,
            city = person.city,
            address = person.address,
            avatarUrl = person.avatarUrl,
            contactLookupUri = person.contactLookupUri,
            isImportant = person.isImportant,
            status = person.status.name,
            createdAt = person.createdAt,
            updatedAt = person.updatedAt
        )
    }
}

data class TransactionDto(
    val id: String? = null,
    val personId: String,
    val type: String,
    val amount: Double,
    val currency: String = "TMT",
    val purpose: String? = null,
    val note: String? = null,
    val attachments: List<String> = emptyList(),
    val date: Long,
    val time: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Transaction = Transaction(
        id = id ?: UUID.randomUUID().toString(),
        personId = personId,
        type = TransactionType.fromString(type),
        amount = amount,
        currency = currency,
        purpose = purpose,
        note = note,
        attachments = attachments,
        date = date,
        time = time,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(t: Transaction) = TransactionDto(
            id = t.id,
            personId = t.personId,
            type = t.type.name,
            amount = t.amount,
            currency = t.currency,
            purpose = t.purpose,
            note = t.note,
            attachments = t.attachments,
            date = t.date,
            time = t.time,
            createdAt = t.createdAt,
            updatedAt = t.updatedAt
        )
    }
}

data class SyncPayload(
    val persons: List<PersonDto>,
    val transactions: List<TransactionDto>
)

data class SyncResponse(
    val persons: List<PersonDto>,
    val transactions: List<TransactionDto>
)
