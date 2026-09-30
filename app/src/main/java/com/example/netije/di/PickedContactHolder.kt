package com.example.netije.di

import com.example.netije.domain.model.Person
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Singleton

@Singleton
object PickedContactHolder {
    private val ref = AtomicReference<Person?>(null)

    fun set(p: Person?) { ref.set(p) }

    fun consume(): Person? = ref.getAndSet(null)

    @Deprecated("Use consume() or set() instead", ReplaceWith("consume()"))
    var person: Person?
        get() = consume()
        set(value) = set(value)
}

/**
 * Handshake holder for: NewTransactionScreen → SelectContactScreen → back to NewTransactionScreen.
 * Passes the (personId, personName) selection across NavBackStack entries because two different
 * Hilt ViewModel instances can't share state via a simple preselectPerson() call when they're
 * scoped to separate composable entries in NavHost.
 */
@Singleton
object PickedPersonForTxHolder {
    private val ref = AtomicReference<Pair<String, String>?>(null)

    fun set(id: String, name: String) { ref.set(id to name) }

    /** Consume the stored value (returns and clears atomically). */
    fun consume(): Pair<String, String>? = ref.getAndSet(null)
}
