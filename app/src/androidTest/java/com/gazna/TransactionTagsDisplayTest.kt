package com.gazna

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gazna.domain.model.parseTransactionTags
import com.gazna.presentation.component.TransactionTags
import com.gazna.presentation.component.TransactionTagsField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionTagsDisplayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tagsCanBeEnteredAndTappedForSearch() {
        val input = mutableStateOf("")
        var selectedTag: String? = null
        compose.setContent {
            MaterialTheme {
                Column {
                    TransactionTagsField(input.value, { input.value = it })
                    TransactionTags(parseTransactionTags(input.value), onTagClick = { selectedTag = it })
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("#Work, rent")
        compose.onNodeWithText("#work").assertIsDisplayed()
        compose.onNodeWithText("#rent").performClick()
        compose.runOnIdle { assertEquals("#rent", selectedTag) }
    }
}
