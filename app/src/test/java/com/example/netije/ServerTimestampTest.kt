package com.example.netije

import com.example.netije.data.remote.dto.TransactionDto
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ServerTimestampTest {
    @Test fun serverDatesAndEpochDatesBothLoadAndStatusSurvivesRoundTrip() {
        val gson = Gson()
        val json = """{"id":"t","personId":"p","type":"RECEIVED","amount":10,"date":"1","time":"1","status":"ARCHIVED","createdAt":"2026-11-02T00:00:00.000Z","updatedAt":"2026-11-02T01:00:00.000Z"}"""
        val transaction = gson.fromJson(json, TransactionDto::class.java)
        assertEquals(3_600_000, transaction.updatedAt - transaction.createdAt)
        assertEquals("ARCHIVED", transaction.status)
        assertEquals(transaction, gson.fromJson(gson.toJson(transaction), TransactionDto::class.java))
    }
}
