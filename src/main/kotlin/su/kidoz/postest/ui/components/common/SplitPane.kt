package su.kidoz.postest.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

@Composable
fun VerticalSplitPane(
    modifier: Modifier = Modifier,
    splitFraction: Float = 0.5f,
    minTopFraction: Float = 0.2f,
    maxTopFraction: Float = 0.8f,
    dividerThickness: Dp = 10.dp,
    topContent: @Composable BoxScope.() -> Unit,
    bottomContent: @Composable BoxScope.() -> Unit,
) {
    var fraction by remember { mutableStateOf(splitFraction) }
    var totalHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val available = with(density) { totalHeight.toDp().value - dividerThickness.value }
    val bounds = splitBounds(available, 180f, 120f, minTopFraction, maxTopFraction)
    val actual = fraction.coerceIn(bounds)
    Column(modifier.onSizeChanged { totalHeight = it.height }) {
        Box(Modifier.fillMaxWidth().weight(actual), content = topContent)
        SplitHandle(
            vertical = false,
            label = "Resize request and response",
            fraction = actual,
            bounds = bounds,
            onChange = { fraction = it.coerceIn(bounds) },
            onDrag = { delta -> if (available > 0) fraction = (actual + delta / density.density / available).coerceIn(bounds) },
            modifier = Modifier.fillMaxWidth().height(dividerThickness),
        )
        Box(Modifier.fillMaxWidth().weight(1f - actual), content = bottomContent)
    }
}

@Composable
fun HorizontalSplitPane(
    modifier: Modifier = Modifier,
    splitFraction: Float = 0.25f,
    minLeftFraction: Float = 0.15f,
    maxLeftFraction: Float = 0.5f,
    dividerThickness: Dp = 10.dp,
    leftContent: @Composable BoxScope.() -> Unit,
    rightContent: @Composable BoxScope.() -> Unit,
) {
    var fraction by remember { mutableStateOf(splitFraction) }
    var totalWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val available = with(density) { totalWidth.toDp().value - dividerThickness.value }
    val bounds = splitBounds(available, 220f, 480f, minLeftFraction, maxLeftFraction)
    val actual = fraction.coerceIn(bounds)
    Row(modifier.onSizeChanged { totalWidth = it.width }) {
        Box(Modifier.fillMaxHeight().weight(actual), content = leftContent)
        SplitHandle(
            vertical = true,
            label = "Resize sidebar",
            fraction = actual,
            bounds = bounds,
            onChange = { fraction = it.coerceIn(bounds) },
            onDrag = { delta -> if (available > 0) fraction = (actual + delta / density.density / available).coerceIn(bounds) },
            modifier = Modifier.fillMaxHeight().width(dividerThickness),
        )
        Box(Modifier.fillMaxHeight().weight(1f - actual), content = rightContent)
    }
}

/** Prefer usable pane dimensions over fractional limits when the window is narrow. */
internal fun splitBounds(
    available: Float,
    minFirst: Float,
    minSecond: Float,
    minFraction: Float,
    maxFraction: Float,
): ClosedFloatingPointRange<Float> {
    if (available < minFirst + minSecond) {
        val proportion = minFirst / (minFirst + minSecond)
        return proportion..proportion
    }
    val lower = maxOf(minFraction, minFirst / available)
    val upper = minOf(maxFraction, 1f - minSecond / available)
    return if (lower <= upper) lower..upper else (minFirst / available)..(1f - minSecond / available)
}

@Composable
private fun SplitHandle(
    vertical: Boolean,
    label: String,
    fraction: Float,
    bounds: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val drag by rememberUpdatedState(onDrag)
    val color = if (hovered || focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier =
            modifier
                .background(if (hovered || focused) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.background)
                .pointerHoverIcon(PointerIcon(Cursor(if (vertical) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR)))
                .hoverable(interaction)
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, bounds)
                    setProgress {
                        onChange(it)
                        true
                    }
                }.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val step = if (event.isShiftPressed) 0.1f else 0.02f
                    when {
                        event.key == Key.MoveHome -> onChange(bounds.start)
                        event.key == Key.MoveEnd -> onChange(bounds.endInclusive)
                        event.key == (if (vertical) Key.DirectionLeft else Key.DirectionUp) -> onChange(fraction - step)
                        event.key == (if (vertical) Key.DirectionRight else Key.DirectionDown) -> onChange(fraction + step)
                        else -> return@onKeyEvent false
                    }
                    true
                }.focusable()
                .pointerInput(vertical) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        drag(if (vertical) amount.x else amount.y)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            (if (vertical) Modifier.fillMaxHeight().width(1.dp) else Modifier.fillMaxWidth().height(1.dp)).background(color),
        )
        Box((if (vertical) Modifier.width(3.dp).height(28.dp) else Modifier.height(3.dp).width(28.dp)).background(color))
    }
}
