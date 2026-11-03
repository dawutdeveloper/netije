package com.example.netije.data.backup

import com.example.netije.domain.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class BackupAsset(val name: String, val data: String)
data class BackupDocument(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val persons: List<Person>,
    val transactions: List<Transaction>,
    val assets: Map<String, BackupAsset> = emptyMap(),
    val missingFiles: List<String> = emptyList()
)

/** Validate the entire document before touching any existing records. Supports legacy JSON. */
object BackupCodec {
    fun decode(json: String, gson: Gson = Gson()): BackupDocument {
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.get("version")?.asInt ?: 1
        require(version in 1..2) { "Unsupported backup version" }
        require(root.get("persons")?.isJsonArray == true && root.get("transactions")?.isJsonArray == true)
        fun JsonObject.defaults(key: String, value: String) {
            if (!has(key) || get(key).isJsonNull) add(key, JsonParser.parseString(value))
        }
        val people = root.getAsJsonArray("persons").map { element ->
            val p = element.asJsonObject
            require(p.get("id")?.asString?.isNotBlank() == true && p.get("name")?.asString?.isNotBlank() == true)
            p.defaults("phones", "[]"); p.defaults("emails", "[]")
            p.defaults("status", "\"ACTIVE\"")
            require(p.get("status").asString in PersonStatus.entries.map { it.name })
            gson.fromJson(p, Person::class.java).also {
                require(it.phones.all { phone -> phone != null } && it.emails.all { email -> email != null })
                require(it.createdAt >= 0 && it.updatedAt >= 0)
            }
        }
        require(people.map { it.id }.distinct().size == people.size)
        val peopleById = people.associateBy { it.id }
        val transactions = root.getAsJsonArray("transactions").map { element ->
            val t = element.asJsonObject
            require(listOf("id", "personId", "type", "amount", "date", "time").all { t.has(it) && !t.get(it).isJsonNull })
            require(t.get("id").asString.isNotBlank() && t.get("personId").asString in peopleById)
            require(t.get("type").asString in TransactionType.entries.map { it.name })
            t.defaults("attachments", "[]"); t.defaults("changes", "[]")
            // Old backups attached status to the person. Carry that grouping onto each transaction.
            t.defaults("status", "\"${peopleById.getValue(t.get("personId").asString).status.name}\"")
            require(t.get("status").asString in TransactionStatus.entries.map { it.name })
            gson.fromJson(t, Transaction::class.java).also {
                require(it.amount.isFinite() && it.amount > 0 && it.date >= 0 && it.time >= 0)
                require(it.updatedAt >= 0 && it.createdAt >= 0 && it.attachments.all { path -> !path.isNullOrBlank() })
                require(it.changes.all { change -> change != null && change.id.isNotBlank() && change.amount.isFinite() && change.previousAmount.isFinite() })
            }
        }
        require(transactions.map { it.id }.distinct().size == transactions.size)
        val transactionsById = transactions.associateBy { it.id }
        transactions.forEach { child ->
            child.parentTransactionId?.let { parentId ->
                val parent = requireNotNull(transactionsById[parentId]) { "Missing original transaction" }
                require(parent.id != child.id && parent.parentTransactionId == null && parent.personId == child.personId)
            }
        }
        val assets = root.get("assets")?.takeUnless { it.isJsonNull }?.asJsonObject?.entrySet()?.associate { (key, value) ->
            require(key.startsWith("backup:"))
            key to gson.fromJson(value, BackupAsset::class.java).also {
                require(!it.name.isNullOrBlank() && it.data != null)
                require(it.data.length % 4 == 0 && Regex("[A-Za-z0-9+/]*={0,2}").matches(it.data))
            }
        }.orEmpty()
        val references = people.mapNotNull { it.avatarUrl } + transactions.flatMap { it.attachments }
        require(references.filter { it.startsWith("backup:") }.all { it in assets })
        val missingFiles = root.get("missingFiles")?.takeUnless { it.isJsonNull }?.asJsonArray?.map { it.asString }.orEmpty()
        return BackupDocument(version, root.get("exportedAt")?.asLong ?: 0, people, transactions, assets, missingFiles)
    }
}
