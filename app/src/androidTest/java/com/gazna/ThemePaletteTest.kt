package com.gazna

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gazna.domain.model.DarkModeConfig
import com.gazna.ui.theme.NetijeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePaletteTest {
    @get:Rule val compose = createComposeRule()

    private fun verify(mode: DarkModeConfig, background: Color, foreground: Color) {
        lateinit var colors: ColorScheme
        compose.setContent { NetijeTheme(mode) { colors = MaterialTheme.colorScheme } }
        compose.runOnIdle {
            assertEquals(background, colors.background)
            assertEquals(foreground, colors.onBackground)
            assertNotEquals(colors.primary, colors.onPrimary)
            listOf(colors.primary, colors.primaryContainer, colors.background, colors.surface,
                colors.surfaceVariant, colors.surfaceDim, colors.surfaceBright,
                colors.surfaceContainerLowest, colors.surfaceContainerLow, colors.surfaceContainer,
                colors.surfaceContainerHigh, colors.surfaceContainerHighest, colors.outline,
                colors.onSurface, colors.onSurfaceVariant).forEach { color ->
                assertEquals("Theme chrome must use neutral grays", color.red, color.green, 0f)
                assertEquals("Theme chrome must not have a blue tint", color.green, color.blue, 0f)
            }
        }
    }

    @Test fun darkModeUsesBlackWithWhiteText() = verify(DarkModeConfig.ON, Color.Black, Color.White)
    @Test fun lightModeUsesWhiteWithBlackText() = verify(DarkModeConfig.OFF, Color.White, Color.Black)
}
