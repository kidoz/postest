package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.FormField
import su.kidoz.postest.domain.model.FormFieldType
import su.kidoz.postest.domain.model.RequestBody
import su.kidoz.postest.ui.components.common.CodeEditor
import su.kidoz.postest.ui.components.common.CodeLanguage
import su.kidoz.postest.ui.components.common.KeyValueEditor
import su.kidoz.postest.util.JsonFormatter
import su.kidoz.postest.util.XmlFormatter
import java.io.File
import javax.swing.JFileChooser

enum class BodyType(
    val displayName: String,
) {
    NONE("None"),
    JSON("JSON"),
    XML("XML"),
    FORM_URL_ENCODED("x-www-form-urlencoded"),
    FORM_DATA("form-data"),
    BINARY("Binary"),
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
                is RequestBody.FormData -> BodyType.FORM_DATA
                is RequestBody.Binary -> BodyType.BINARY
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
                                BodyType.FORM_DATA -> onBodyChange(RequestBody.FormData(emptyList()))
                                BodyType.BINARY -> onBodyChange(RequestBody.Binary(""))
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

                BodyType.FORM_DATA -> {
                    val fields = (body as? RequestBody.FormData)?.fields ?: emptyList()
                    FormDataEditor(
                        fields = fields,
                        onFieldsChange = { onBodyChange(RequestBody.FormData(it)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                BodyType.BINARY -> {
                    val filePath = (body as? RequestBody.Binary)?.filePath ?: ""
                    BinaryFileEditor(
                        filePath = filePath,
                        onFilePathChange = { onBodyChange(RequestBody.Binary(it)) },
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

/**
 * Editor for multipart/form-data fields with support for text and file types.
 */
@Composable
private fun FormDataEditor(
    fields: List<FormField>,
    onFieldsChange: (List<FormField>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Add field button
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    onFieldsChange(fields + FormField(key = "", value = "", type = FormFieldType.TEXT))
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Add Field")
            }
        }

        if (fields.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No form fields. Click 'Add Field' to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(8.dp),
            ) {
                itemsIndexed(fields) { index, field ->
                    FormDataFieldRow(
                        field = field,
                        onFieldChange = { updatedField ->
                            onFieldsChange(fields.toMutableList().apply { set(index, updatedField) })
                        },
                        onRemove = {
                            onFieldsChange(fields.toMutableList().apply { removeAt(index) })
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FormDataFieldRow(
    field: FormField,
    onFieldChange: (FormField) -> Unit,
    onRemove: () -> Unit,
) {
    var showTypeMenu by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Enabled checkbox
        Checkbox(
            checked = field.enabled,
            onCheckedChange = { onFieldChange(field.copy(enabled = it)) },
            modifier = Modifier.size(24.dp),
        )

        // Key field
        OutlinedTextField(
            value = field.key,
            onValueChange = { onFieldChange(field.copy(key = it)) },
            placeholder = { Text("Field name") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )

        // Type selector
        Box {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showTypeMenu = true }
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (field.type == FormFieldType.FILE) Icons.Default.AttachFile else Icons.Default.TextFields,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (field.type == FormFieldType.FILE) "File" else "Text",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DropdownMenu(
                expanded = showTypeMenu,
                onDismissRequest = { showTypeMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Text") },
                    onClick = {
                        onFieldChange(field.copy(type = FormFieldType.TEXT, value = ""))
                        showTypeMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.TextFields, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("File") },
                    onClick = {
                        onFieldChange(field.copy(type = FormFieldType.FILE, value = ""))
                        showTypeMenu = false
                    },
                    leadingIcon = {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                    },
                )
            }
        }

        // Value field (text input or file picker)
        if (field.type == FormFieldType.TEXT) {
            OutlinedTextField(
                value = field.value,
                onValueChange = { onFieldChange(field.copy(value = it)) },
                placeholder = { Text("Value") },
                modifier = Modifier.weight(1.5f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        } else {
            // File picker
            FilePickerField(
                filePath = field.value,
                onFileSelected = { onFieldChange(field.copy(value = it)) },
                modifier = Modifier.weight(1.5f),
            )
        }

        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove field",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * File picker field component used in FormData and Binary editors.
 */
@Composable
private fun FilePickerField(
    filePath: String,
    onFileSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fileName = if (filePath.isNotBlank()) File(filePath).name else ""

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .clickable { selectFile(onFileSelected) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (filePath.isBlank()) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (filePath.isBlank()) "Select file..." else fileName,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (filePath.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (filePath.isNotBlank()) {
            IconButton(
                onClick = { onFileSelected("") },
                modifier = Modifier.size(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear file",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Editor for binary file body type.
 */
@Composable
private fun BinaryFileEditor(
    filePath: String,
    onFilePathChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (filePath.isBlank()) {
            // No file selected
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "No file selected",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Select a file to send as the request body",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { selectFile(onFilePathChange) },
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Select File")
            }
        } else {
            // File selected
            val file = File(filePath)
            val fileSize = if (file.exists()) formatFileSize(file.length()) else "File not found"

            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = fileSize,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = filePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { selectFile(onFilePathChange) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Change File")
                }

                Button(
                    onClick = { onFilePathChange("") },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Remove")
                }
            }
        }
    }
}

/**
 * Opens a file chooser dialog and returns the selected file path.
 */
private fun selectFile(onFileSelected: (String) -> Unit) {
    val fileChooser =
        JFileChooser().apply {
            dialogTitle = "Select File"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = true
        }

    val result = fileChooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        onFileSelected(fileChooser.selectedFile.absolutePath)
    }
}

private fun formatFileSize(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
