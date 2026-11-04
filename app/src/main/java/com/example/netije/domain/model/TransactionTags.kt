package com.example.netije.domain.model

import java.text.Normalizer
import java.util.Locale

private val tagWord = Regex("[\\p{L}\\p{N}_][\\p{L}\\p{M}\\p{N}_-]*")
private val hashtag = Regex("#([\\p{L}\\p{N}_][\\p{L}\\p{M}\\p{N}_-]*)")

fun parseTransactionTags(input: String): List<String> =
    tagWord.findAll(Normalizer.normalize(input, Normalizer.Form.NFC))
        .map { "#${it.value.lowercase(Locale.ROOT)}" }.distinct().toList()

fun normalizeTransactionTags(tags: List<String>?): List<String> = parseTransactionTags(tags.orEmpty().joinToString(" "))

/** Explicit hashtags match whole tags; ordinary words also search notes, purposes and names. */
fun Transaction.matchesSearch(query: String, personName: String = ""): Boolean {
    val normalized = Normalizer.normalize(query, Normalizer.Form.NFC).lowercase(Locale.ROOT).trim()
    if (normalized.isEmpty()) return true
    val tags = normalizeTransactionTags(this.tags)
    val required = hashtag.findAll(normalized).map { it.value }.toList()
    if (!required.all { it in tags }) return false
    if ('#' in normalized && required.isEmpty() && tags.isEmpty()) return false
    val words = tagWord.findAll(normalized.replace(hashtag, " ")).map { it.value }.toList()
    val text = Normalizer.normalize(listOf(personName, purpose.orEmpty(), note.orEmpty(), tags.joinToString(" ")).joinToString(" "), Normalizer.Form.NFC).lowercase(Locale.ROOT)
    return words.all { it in text }
}

/** Keep complete matching groups so searching never changes a group's displayed balance. */
fun PersonWithTransactions.matchingTransactionGroups(query: String): PersonWithTransactions? {
    if (query.isBlank()) return this
    if ('#' !in query && person.name.contains(query.trim(), ignoreCase = true)) return this
    val matching = transactions.groupTransactions().filter { group ->
        (listOf(group.transaction) + group.children).any { it.matchesSearch(query, person.name) }
    }.flatMap { listOf(it.transaction) + it.children }
    return if (matching.isEmpty()) null else copy(transactions = matching)
}
