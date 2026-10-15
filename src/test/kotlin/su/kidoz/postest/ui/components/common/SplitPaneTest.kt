package su.kidoz.postest.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplitPaneTest {
    @Test
    fun `minimum pane dimensions override fractional limits on small windows`() {
        val bounds = splitBounds(800f, 220f, 480f, 0.15f, 0.4f)
        assertTrue(bounds.start * 800f >= 220f)
        assertTrue((1f - bounds.endInclusive) * 800f >= 480f)
    }

    @Test
    fun `undersized windows keep both panes visible without invalid weights`() {
        val bounds = splitBounds(500f, 220f, 480f, 0.15f, 0.4f)
        assertTrue(bounds.start > 0f && bounds.endInclusive < 1f)
        assertEquals(bounds.start, bounds.endInclusive)
    }

    @Test
    fun `large windows retain the configured fractional limits`() {
        assertEquals(0.15f..0.4f, splitBounds(2000f, 220f, 480f, 0.15f, 0.4f))
    }
}
