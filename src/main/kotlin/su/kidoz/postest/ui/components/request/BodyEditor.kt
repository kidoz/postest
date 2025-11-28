package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.ui.components.common.CodeEditor
import su.kidoz.postest.ui.components.common.CodeLanguage
import su.kidoz.postest.ui.components.common.KeyValueEditor
import su.kidoz.postest.util.JsonFormatter
import su.kidoz.postest.util.XmlFormatter

enum class BodyType(
    val displayName: String,
) {
    NONE("None"),
    JSON("JSON"),
    XML("XML"),
    FORM_URL_ENCODED("x-www-form-urlencoded"),
    RAW("Raw"),
}

@Composable
fun BodyEditor(
    body: RequestBody?,
    onBodyChange: (RequestBody?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current

    var selectedType by remember(body) {
        mutableStateOf(
            when (body) {
                is RequestBody.Json -> BodyType.JSON
                is RequestBody.Xml -> BodyType.XML
                is RequestBody.FormUrlEncoded -> BodyType.FORM_URL_ENCODED
                is RequestBody.Raw -> BodyType.RAW
                else -> BodyType.NONE
            },
        )
    }

    // Get current content for copy/format operations
    val currentContent =
        when (body) {
            is RequestBody.Json -> body.content
            is RequestBody.Xml -> body.content
            is RequestBody.Raw -> body.content
            else -> ""
        }

    Column(modifier = modifier.fillMaxSize()) {
        // Body type selector with action buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BodyType.entries.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = {
                        if (selectedType != type) {
                            selectedType = type
                            // Try to preserve content when switching types
                            val contentToPreserve =
                                when (body) {
                                    is RequestBody.Json -> body.content
                                    is RequestBody.Xml -> body.content
                                    is RequestBody.Raw -> body.content
                                    else -> ""
                                }
                            when (type) {
                                BodyType.NONE -> onBodyChange(RequestBody.None)
                                BodyType.JSON -> onBodyChange(RequestBody.Json(contentToPreserve))
                                BodyType.XML -> onBodyChange(RequestBody.Xml(contentToPreserve))
                                BodyType.FORM_URL_ENCODED -> onBodyChange(RequestBody.FormUrlEncoded(emptyList()))
                                BodyType.RAW -> onBodyChange(RequestBody.Raw(contentToPreserve, "text/plain"))
                            }
                        }
                    },
                    label = { Text(type.displayName) },
                )
            }

            Spacer(Modifier.weight(1f))

            // Format button (only for JSON/XML with content)
            if ((selectedType == BodyType.JSON || selectedType == BodyType.XML) && currentContent.isNotBlank()) {
                IconButton(
                    onClick = {
                        val formatted =
                            when (selectedType) {
                                BodyType.JSON -> JsonFormatter.format(currentContent)
                                BodyType.XML -> XmlFormatter.format(currentContent)
                                else -> currentContent
                            }
                        when (selectedType) {
                            BodyType.JSON -> onBodyChange(RequestBody.Json(formatted))
                            BodyType.XML -> onBodyChange(RequestBody.Xml(formatted))
                            else -> {}
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Format",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // Copy button (for any body with content)
            if (selectedType != BodyType.NONE && currentContent.isNotBlank()) {
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(currentContent))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        HorizontalDivider()

        // Body content
        Box(modifier = Modifier.weight(1f)) {
            when (selectedType) {
                BodyType.NONE -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    ) {
                        Text(
                            text = "This request does not have a body",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                BodyType.JSON -> {
                    val content = (body as? RequestBody.Json)?.content ?: ""
                    CodeEditor(
                        value = content,
                        onValueChange = { onBodyChange(RequestBody.Json(it)) },
                        language = CodeLanguage.JSON,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                BodyType.XML -> {
                    val content = (body as? RequestBody.Xml)?.content ?: ""
                    CodeEditor(
                        value = content,
                        onValueChange = { onBodyChange(RequestBody.Xml(it)) },
                        language = CodeLanguage.XML,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                BodyType.FORM_URL_ENCODED -> {
                    val fields = (body as? RequestBody.FormUrlEncoded)?.fields ?: emptyList()
                    KeyValueEditor(
                        items = fields,
                        onItemsChange = { onBodyChange(RequestBody.FormUrlEncoded(it)) },
                        keyPlaceholder = "Field name",
                        valuePlaceholder = "Field value",
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                BodyType.RAW -> {
                    val rawBody = body as? RequestBody.Raw
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = rawBody?.contentType ?: "text/plain",
                            onValueChange = { contentType ->
                                onBodyChange(RequestBody.Raw(rawBody?.content ?: "", contentType))
                            },
                            label = { Text("Content-Type") },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            singleLine = true,
                        )

                        CodeEditor(
                            value = rawBody?.content ?: "",
                            onValueChange = { content ->
                                onBodyChange(RequestBody.Raw(content, rawBody?.contentType ?: "text/plain"))
                            },
                            language = CodeLanguage.PLAIN,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
