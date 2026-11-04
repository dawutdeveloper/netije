package com.example.netije.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_account")
data class UserEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val googleIdToken: String? = null,
    val accessToken: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phones: String = "",
    val emails: String = "",
    val city: String? = null,
    val address: String? = null,
    val avatarUrl: String? = null,
    val contactLookupUri: String? = null,
    val isImportant: Boolean = false,
    val status: String = "ACTIVE",
    @androidx.room.ColumnInfo(defaultValue = "0") val statusPending: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val personId: String,
    val type: String,
    val amount: Double,
    val currency: String = "TMT",
    val purpose: String? = null,
    val note: String? = null,
    val attachments: String = "",
    val parentTransactionId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "'[]'") val tags: String = "[]",
    @androidx.room.ColumnInfo(defaultValue = "'[]'") val changes: String = "[]",
    @androidx.room.ColumnInfo(defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    @androidx.room.ColumnInfo(defaultValue = "0") val statusPending: Boolean = false,
    val date: Long,
    val time: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
