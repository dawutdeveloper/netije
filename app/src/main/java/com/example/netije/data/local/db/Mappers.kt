package com.example.netije.data.local.db

import com.example.netije.domain.model.Person
import com.example.netije.domain.model.PersonStatus
import com.example.netije.domain.model.Transaction
import com.example.netije.domain.model.TransactionType
import com.example.netije.domain.model.UserApp

private const val SEP = "\u0001"

fun String.splitList(): List<String> =
    split(SEP).filter { it.isNotBlank() }.map { it.trim() }

fun List<String>.joinForDb(): String = joinToString(separator = SEP)

fun UserEntity.toDomain(): UserApp = UserApp(
    id = id,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl,
    googleIdToken = googleIdToken,
    accessToken = accessToken,
    createdAt = createdAt
)

fun UserApp.toEntity(): UserEntity = UserEntity(
    id = id,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl,
    googleIdToken = googleIdToken,
    accessToken = accessToken,
    createdAt = createdAt
)

fun PersonEntity.toDomain(): Person = Person(
    id = id,
    name = name,
    phones = phones.splitList(),
    emails = emails.splitList(),
    city = city,
    address = address,
    avatarUrl = avatarUrl,
    contactLookupUri = contactLookupUri,
    isImportant = isImportant,
    status = PersonStatus.fromString(status),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Person.toEntity(): PersonEntity = PersonEntity(
    id = id,
    name = name,
    phones = phones.joinForDb(),
    emails = emails.joinForDb(),
    city = city,
    address = address,
    avatarUrl = avatarUrl,
    contactLookupUri = contactLookupUri,
    isImportant = isImportant,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    personId = personId,
    type = TransactionType.fromString(type),
    amount = amount,
    currency = currency,
    purpose = purpose,
    note = note,
    attachments = attachments.splitList(),
    date = date,
    time = time,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    personId = personId,
    type = type.name,
    amount = amount,
    currency = currency,
    purpose = purpose,
    note = note,
    attachments = attachments.joinForDb(),
    date = date,
    time = time,
    createdAt = createdAt,
    updatedAt = updatedAt
)
