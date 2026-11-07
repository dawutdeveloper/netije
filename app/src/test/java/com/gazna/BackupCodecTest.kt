package com.gazna

import com.gazna.data.backup.BackupCodec
import com.gazna.domain.model.TransactionStatus
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val legacy = """{"version":1,"persons":[{"id":"p","name":"Name","status":"ARCHIVED"}],"transactions":[{"id":"t","personId":"p","type":"RECEIVED","amount":12.5,"date":1,"time":1}]}"""

    @Test fun legacyBackupMovesPersonGroupingOntoTransaction() {
        val backup = BackupCodec.decode(legacy)
        assertEquals(TransactionStatus.ARCHIVED, backup.transactions.single().status)
        assertTrue(backup.transactions.single().attachments.isEmpty())
        assertTrue(backup.transactions.single().changes.isEmpty())
    }
    @Test fun invalidDocumentsAreRejectedBeforeImport() {
        for (json in listOf("{}", legacy.replace("\"version\":1", "\"version\":999"),
            legacy.replace("\"amount\":12.5", "\"amount\":-2"),
            legacy.replace("\"personId\":\"p\"", "\"personId\":\"missing\""),
            legacy.replace("\"name\":\"Name\"", "\"name\":null"),
            legacy.replace("\"time\":1", "\"time\":1,\"attachments\":[\"backup:missing\"]"))) {
            assertTrue(json, runCatching { BackupCodec.decode(json) }.isFailure)
        }
    }
}
