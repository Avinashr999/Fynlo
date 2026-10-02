package app.fynlo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerPaletteTest {
    private fun assertReadable(foreground: Color, background: Color, label: String) {
        val a = foreground.luminance()
        val b = background.luminance()
        val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue("$label contrast: $contrast", contrast >= 4.5f)
    }

    @Test fun lightTextAndControlsAreReadable() = verify(LightColorScheme)
    @Test fun darkTextAndControlsAreReadable() = verify(DarkColorScheme)

    private fun verify(colors: androidx.compose.material3.ColorScheme) {
        assertReadable(colors.onSurface, colors.background, "body")
        assertReadable(colors.onSurfaceVariant, colors.background, "secondary")
        assertReadable(colors.onSurfaceVariant, colors.surface, "dialog secondary")
        assertReadable(colors.primary, colors.background, "links")
        assertReadable(colors.onPrimary, colors.primary, "primary button")
        assertReadable(colors.onPrimaryContainer, colors.primaryContainer, "selected control")
        assertReadable(colors.onError, colors.error, "destructive button")
    }
}
