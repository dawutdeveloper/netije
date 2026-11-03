package com.example.netije

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.netije.domain.model.TransactionType
import com.example.netije.presentation.component.FinanceSummaryContent
import com.example.netije.presentation.component.SegmentedTypeControl
import com.example.netije.ui.theme.NetijeTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ResponsiveLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun SemanticsNodeInteraction.assertTextFits() {
        assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        checkNotNull(action).invoke(layouts)
        assertTrue("Text layout must be available", layouts.isNotEmpty())
        layouts.forEach { assertFalse("Text must not be clipped or ellipsized", it.hasVisualOverflow) }
    }

    @Test fun largeTotalsFitNarrowScreenAtDoubleFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(density.density, fontScale = 2f)) {
                NetijeTheme {
                    Box(Modifier.width(280.dp)) {
                        FinanceSummaryContent(1234567.89, 234567.89, 1000000.0, "TMT")
                    }
                }
            }
        }
        listOf("TMT +1234567.89", "TMT -234567.89", "TMT +1000000.00").forEach {
            compose.onNodeWithText(it).assertTextFits()
        }
        val first = compose.onNodeWithText("TMT +1234567.89").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("TMT -234567.89").fetchSemanticsNode().boundsInRoot
        assertTrue("Totals must stack without overlapping", second.top >= first.bottom)
    }

    @Test fun smallTotalsKeepCompactColumnsWhenTheyFit() {
        compose.setContent {
            val density = LocalDensity.current
            val config = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
            CompositionLocalProvider(LocalConfiguration provides config,
                LocalDensity provides Density(density.density, fontScale = 1f)) {
                NetijeTheme {
                    Box(Modifier.width(360.dp)) {
                        FinanceSummaryContent(10.0, 5.0, 5.0, "TMT")
                    }
                }
            }
        }
        listOf("TMT +10.00", "TMT -5.00", "TMT +5.00").forEach {
            compose.onNodeWithText(it).assertTextFits()
        }
        val first = compose.onNodeWithText("TMT +10.00").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("TMT -5.00").fetchSemanticsNode().boundsInRoot
        assertTrue("Compact totals should share a row", first.top == second.top && second.left >= first.right)
    }

    @Test fun transactionTypeLabelsFitAtDoubleFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                NetijeTheme {
                    Box(Modifier.width(280.dp)) {
                        SegmentedTypeControl(TransactionType.RECEIVED, {})
                    }
                }
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        listOf(R.string.tx_received_full, R.string.tx_issued_full).forEach {
            compose.onNodeWithText(context.getString(it)).assertTextFits()
        }
    }
}
