package su.kidoz.postest.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VerticalSplitPane(
    modifier: Modifier = Modifier,
    splitFraction: Float = 0.5f,
    minTopFraction: Float = 0.2f,
    maxTopFraction: Float = 0.8f,
    dividerThickness: Dp = 4.dp,
    topContent: @Composable BoxScope.() -> Unit,
    bottomContent: @Composable BoxScope.() -> Unit,
) {
    var fraction by remember { mutableStateOf(splitFraction) }
    var totalHeight by remember { mutableStateOf(0) }

    Column(modifier = modifier.onSizeChanged { totalHeight = it.height }) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(fraction),
        ) {
            topContent()
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(dividerThickness)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (totalHeight > 0) {
                                val delta = dragAmount.y / totalHeight
                                fraction = (fraction + delta).coerceIn(minTopFraction, maxTopFraction)
                            }
                        }
                    },
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f - fraction),
        ) {
            bottomContent()
        }
    }
}

@Composable
fun HorizontalSplitPane(
    modifier: Modifier = Modifier,
    splitFraction: Float = 0.25f,
    minLeftFraction: Float = 0.15f,
    maxLeftFraction: Float = 0.5f,
    dividerThickness: Dp = 4.dp,
    leftContent: @Composable BoxScope.() -> Unit,
    rightContent: @Composable BoxScope.() -> Unit,
) {
    var fraction by remember { mutableStateOf(splitFraction) }
    var totalWidth by remember { mutableStateOf(0) }

    Row(modifier = modifier.onSizeChanged { totalWidth = it.width }) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(fraction),
        ) {
            leftContent()
        }

        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(dividerThickness)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (totalWidth > 0) {
                                val delta = dragAmount.x / totalWidth
                                fraction = (fraction + delta).coerceIn(minLeftFraction, maxLeftFraction)
                            }
                        }
                    },
        )

        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(1f - fraction),
        ) {
            rightContent()
        }
    }
}
