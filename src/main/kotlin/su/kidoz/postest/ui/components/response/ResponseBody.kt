package su.kidoz.postest.ui.components.response

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import su.kidoz.postest.ui.components.common.CodeEditor
import su.kidoz.postest.ui.components.common.CodeLanguage
import su.kidoz.postest.ui.components.common.JsonViewer
import su.kidoz.postest.util.HtmlFormatter
import su.kidoz.postest.util.JsonFormatter
import su.kidoz.postest.util.XmlFormatter
import kotlin.math.min

enum class BodyViewMode(
    val displayName: String,
) {
    PRETTY("Pretty"),
    RAW("Raw"),
    TREE("Tree"),
}

@Composable
fun ResponseBody(
    body: String,
    contentType: String?,
    modifier: Modifier = Modifier,
) {
    val bodyBytes = remember(body) { body.toByteArray(Charsets.UTF_8).size }
    val isLargeBody = bodyBytes > 2 * 1024 * 1024 // 2MB guard to avoid UI stalls
    var allowHeavyProcessing by remember(body) { mutableStateOf(!isLargeBody) }
    var viewMode by remember(body) {
        mutableStateOf(if (isLargeBody) BodyViewMode.RAW else BodyViewMode.PRETTY)
    }
    var showFullRaw by remember(body) { mutableStateOf(!isLargeBody) }
    var formatState by remember(body) { mutableStateOf<FormatState>(FormatState.Idle) }

    val isJson =
        contentType?.contains("json", ignoreCase = true) == true ||
            body.trimStart().startsWith("{") ||
            body.trimStart().startsWith("[")

    val isXml =
        contentType?.contains("xml", ignoreCase = true) == true ||
            body.trimStart().startsWith("<?xml") ||
            body.trimStart().startsWith("<")

    val isHtml =
        contentType?.contains("html", ignoreCase = true) == true ||
            body.trimStart().lowercase().startsWith("<!doctype html") ||
            body.trimStart().lowercase().startsWith("<html")

    val codeLanguage =
        when {
            isJson -> CodeLanguage.JSON
            isHtml -> CodeLanguage.HTML
            isXml -> CodeLanguage.XML
            else -> CodeLanguage.PLAIN
        }

    LaunchedEffect(viewMode, body, allowHeavyProcessing) {
        if (!allowHeavyProcessing || viewMode == BodyViewMode.RAW || !isJson) {
            formatState = FormatState.Idle
            return@LaunchedEffect
        }

        formatState = FormatState.Formatting
        val result =
            withContext(Dispatchers.Default) {
                withTimeoutOrNull(2_000L) {
                    runCatching { JsonFormatter.format(body) }.getOrNull()
                }
            }

        formatState =
            when {
                result == null -> FormatState.Timeout
                else -> FormatState.Success(result)
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // View mode selector
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BodyViewMode.entries.forEach { mode ->
                FilterChip(
                    selected = viewMode == mode,
                    onClick = { viewMode = mode },
                    label = { Text(mode.displayName) },
                    enabled =
                        when (mode) {
                            BodyViewMode.TREE -> isJson && allowHeavyProcessing && !isLargeBody
                            BodyViewMode.PRETTY -> allowHeavyProcessing
                            BodyViewMode.RAW -> true
                        },
                )
            }
        }

        if (isLargeBody) {
            AssistChip(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                onClick = {
                    allowHeavyProcessing = true
                    viewMode = BodyViewMode.PRETTY
                },
                label = {
                    Text("Large response (~${bodyBytes / 1024} KB). Pretty view is opt-in.")
                },
                enabled = !allowHeavyProcessing,
            )
        }

        HorizontalDivider()

        // Body content
        Box(modifier = Modifier.weight(1f)) {
            // Capture delegated property to enable smart casting
            val currentFormatState = formatState

            when (viewMode) {
                BodyViewMode.PRETTY -> {
                    when (currentFormatState) {
                        FormatState.Idle -> {
                            val prettyBody =
                                when {
                                    isJson -> JsonFormatter.format(body)
                                    isHtml -> HtmlFormatter.format(body)
                                    isXml -> XmlFormatter.format(body)
                                    else -> body
                                }

                            CodeEditor(
                                value = prettyBody,
                                onValueChange = {},
                                language = codeLanguage,
                                readOnly = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        FormatState.Formatting -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is FormatState.Success -> {
                            CodeEditor(
                                value = currentFormatState.formatted,
                                onValueChange = {},
                                language = codeLanguage,
                                readOnly = true,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        FormatState.Timeout -> {
                            LargeBodyFallback(
                                body = body,
                                codeLanguage = codeLanguage,
                                isLargeBody = isLargeBody,
                                showFullRaw = showFullRaw,
                                onShowFullRaw = { showFullRaw = true },
                                message = "Pretty-print timed out. Showing raw instead.",
                            )
                        }
                    }
                }

                BodyViewMode.RAW -> {
                    LargeBodyFallback(
                        body = body,
                        codeLanguage = CodeLanguage.PLAIN,
                        isLargeBody = isLargeBody,
                        showFullRaw = showFullRaw,
                        onShowFullRaw = { showFullRaw = true },
                    )
                }

                BodyViewMode.TREE -> {
                    when (currentFormatState) {
                        is FormatState.Success -> {
                            JsonViewer(
                                jsonString = currentFormatState.formatted,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        FormatState.Formatting -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        else -> {
                            Text(
                                text = "Tree view needs formatting. Enable pretty view first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val RAW_PREVIEW_BYTES = 200 * 1024 // 200KB preview to avoid huge allocations

private sealed interface FormatState {
    data object Idle : FormatState

    data object Formatting : FormatState

    data class Success(
        val formatted: String,
    ) : FormatState

    data object Timeout : FormatState
}

@Composable
private fun LargeBodyFallback(
    body: String,
    codeLanguage: CodeLanguage,
    isLargeBody: Boolean,
    showFullRaw: Boolean,
    onShowFullRaw: () -> Unit,
    message: String? = null,
) {
    val displayText =
        if (isLargeBody && !showFullRaw) {
            val preview = body.take(min(body.length, RAW_PREVIEW_BYTES))
            preview
        } else {
            body
        }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLargeBody) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Large body. Showing ${if (showFullRaw) "full content" else "first 200KB"} to avoid freezing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!showFullRaw) {
                    TextButton(onClick = onShowFullRaw) {
                        Text("Load full")
                    }
                }
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        CodeEditor(
            value = displayText,
            onValueChange = {},
            language = codeLanguage,
            readOnly = true,
            showLineNumbers = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
