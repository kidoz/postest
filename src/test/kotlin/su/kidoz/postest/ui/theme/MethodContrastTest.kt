package su.kidoz.postest.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class MethodContrastTest {
    @Test
    fun `method labels remain readable on normal and selected surfaces in both themes`() {
        listOf(
            LightExtendedColors to listOf(AppColors.LightSurface, AppColors.LightSurfaceVariant, AppColors.LightPrimaryContainer),
            DarkExtendedColors to listOf(AppColors.DarkSurface, AppColors.DarkSurfaceVariant, AppColors.DarkPrimaryContainer),
        ).forEach { (colors, surfaces) ->
            val methods =
                listOf(
                    colors.methodGet,
                    colors.methodPost,
                    colors.methodPut,
                    colors.methodPatch,
                    colors.methodDelete,
                    colors.methodHead,
                    colors.methodOptions,
                )
            methods.forEachIndexed { index, color ->
                surfaces.forEach { surface ->
                    assertTrue(contrast(color, surface) >= 4.5f, "Method $index must remain readable on $surface")
                }
            }
        }
    }

    private fun contrast(
        foreground: Color,
        background: Color,
    ): Float {
        val first = foreground.luminance()
        val second = background.luminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }
}
