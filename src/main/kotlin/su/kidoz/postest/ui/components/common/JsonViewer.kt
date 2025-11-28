package su.kidoz.postest.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.*
import su.kidoz.postest.ui.theme.AppTheme
import su.kidoz.postest.ui.theme.CodeTextStyle

@Composable
fun JsonViewer(
    jsonString: String,
    modifier: Modifier = Modifier,
) {
    val jsonElement =
        remember(jsonString) {
            try {
                Json.parseToJsonElement(jsonString)
            } catch (e: Exception) {
                null
            }
        }

    if (jsonElement != null) {
        LazyColumn(
            modifier = modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                JsonNode(
                    key = null,
                    element = jsonElement,
                    depth = 0,
                )
            }
        }
    } else {
        Text(
            text = jsonString,
            style = CodeTextStyle,
            modifier = modifier.padding(8.dp),
        )
    }
}

@Composable
private fun JsonNode(
    key: String?,
    element: JsonElement,
    depth: Int,
) {
    val extendedColors = AppTheme.extendedColors

    when (element) {
        is JsonObject -> {
            var expanded by remember { mutableStateOf(true) }

            Column {
                Row(
                    modifier =
                        Modifier
                            .clickable { expanded = !expanded }
                            .padding(start = (depth * 16).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )

                    if (key != null) {
                        Text(
                            text =
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = extendedColors.syntaxProperty)) {
                                        append("\"$key\"")
                                    }
                                    append(": ")
                                },
                            style = CodeTextStyle,
                        )
                    }

                    Text(
                        text = if (expanded) "{" else "{ ... } (${element.size} items)",
                        style = CodeTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (expanded) {
                    element.entries.forEach { (k, v) ->
                        JsonNode(key = k, element = v, depth = depth + 1)
                    }
                    Text(
                        text = "}",
                        style = CodeTextStyle,
                        modifier = Modifier.padding(start = (depth * 16).dp),
                    )
                }
            }
        }

        is JsonArray -> {
            var expanded by remember { mutableStateOf(true) }

            Column {
                Row(
                    modifier =
                        Modifier
                            .clickable { expanded = !expanded }
                            .padding(start = (depth * 16).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )

                    if (key != null) {
                        Text(
                            text =
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = extendedColors.syntaxProperty)) {
                                        append("\"$key\"")
                                    }
                                    append(": ")
                                },
                            style = CodeTextStyle,
                        )
                    }

                    Text(
                        text = if (expanded) "[" else "[ ... ] (${element.size} items)",
                        style = CodeTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (expanded) {
                    element.forEachIndexed { index, item ->
                        JsonNode(key = index.toString(), element = item, depth = depth + 1)
                    }
                    Text(
                        text = "]",
                        style = CodeTextStyle,
                        modifier = Modifier.padding(start = (depth * 16).dp),
                    )
                }
            }
        }

        is JsonPrimitive -> {
            Row(modifier = Modifier.padding(start = (depth * 16 + 16).dp)) {
                if (key != null) {
                    Text(
                        text =
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = extendedColors.syntaxProperty)) {
                                    append("\"$key\"")
                                }
                                append(": ")
                            },
                        style = CodeTextStyle,
                    )
                }

                val valueColor =
                    when {
                        element.isString -> extendedColors.syntaxString
                        element.booleanOrNull != null -> extendedColors.syntaxBoolean
                        element.content == "null" -> extendedColors.syntaxNull
                        else -> extendedColors.syntaxNumber
                    }

                val valueText = if (element.isString) "\"${element.content}\"" else element.content

                Text(
                    text = valueText,
                    style = CodeTextStyle,
                    color = valueColor,
                )
            }
        }
    }
}
