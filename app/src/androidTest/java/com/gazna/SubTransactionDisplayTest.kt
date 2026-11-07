package com.gazna

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gazna.domain.model.Transaction
import com.gazna.domain.model.TransactionType
import com.gazna.presentation.component.TransactionRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SubTransactionDisplayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun depositAndWithdrawalAreVisibleTogetherWithReasonAndBalance() {
        val original = Transaction("original", "person", TransactionType.RECEIVED, 100.0, date = 1, time = 1)
        val child = Transaction("payment", "person", TransactionType.ISSUED, 50.0,
            parentTransactionId = original.id, note = "Groceries", date = 2, time = 2)
        compose.setContent {
            MaterialTheme {
                TransactionRow(t = original, currency = "TMT", purpose = null, subTransactions = listOf(child))
            }
        }
        compose.onNodeWithText("TMT +${String.format(Locale.getDefault(), "%.2f", 100.0)}").assertIsDisplayed()
        compose.onNodeWithText("TMT -${String.format(Locale.getDefault(), "%.2f", 50.0)}").assertIsDisplayed()
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithText("TMT +${String.format(Locale.getDefault(), "%.2f", 50.0)}").assertIsDisplayed()
    }

    @Test fun currentBalanceShows800While300And500RemainInHistory() {
        val original = Transaction("original", "person", TransactionType.RECEIVED, 300.0, date = 1, time = 1)
        val child = Transaction("payment", "person", TransactionType.RECEIVED, 500.0,
            parentTransactionId = original.id, note = "Additional payment", date = 2, time = 2)
        compose.setContent {
            MaterialTheme {
                TransactionRow(t = original, currency = "TMT", purpose = null, subTransactions = listOf(child))
            }
        }
        for (amount in listOf(800.0, 300.0, 500.0)) {
            val label = "TMT +${String.format(Locale.getDefault(), "%.2f", amount)}"
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onAllNodesWithText(label).assertCountEquals(1)
        }
        compose.onNodeWithText("Additional payment").assertIsDisplayed()
    }
}
