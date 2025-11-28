package su.kidoz.postest.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import su.kidoz.postest.ui.theme.AppTheme
import su.kidoz.postest.ui.theme.CodeTextStyle

@Composable
fun CodeEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    language: CodeLanguage = CodeLanguage.JSON,
    showLineNumbers: Boolean = true,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val extendedColors = AppTheme.extendedColors

    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }

    // Sync with external value changes
    LaunchedEffect(value) {
        if (textFieldValue.text != value) {
            textFieldValue = TextFieldValue(value)
        }
    }

    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
    ) {
        if (showLineNumbers) {
            val lineCount = textFieldValue.text.count { it == '\n' } + 1
            Column(
                modifier =
                    Modifier
                        .verticalScroll(verticalScrollState)
                        .padding(end = 8.dp),
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString().padStart(3),
                        style = CodeTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState),
        ) {
            if (readOnly) {
                Text(
                    text = highlightSyntax(value, language, extendedColors),
                    style = CodeTextStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // For editable mode, use BasicTextField with syntax-highlighted text
                val highlightedText = highlightSyntax(textFieldValue.text, language, extendedColors)
                BasicTextField(
                    value =
                        textFieldValue.copy(
                            annotatedString = highlightedText,
                        ),
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onValueChange(newValue.text)
                    },
                    textStyle = CodeTextStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 100.dp),
                )
            }
        }
    }
}

enum class CodeLanguage {
    JSON,
    XML,
    HTML,
    PLAIN,
}

@Composable
private fun highlightSyntax(
    text: String,
    language: CodeLanguage,
    colors: su.kidoz.postest.ui.theme.ExtendedColors,
): AnnotatedString =
    when (language) {
        CodeLanguage.JSON -> highlightJson(text, colors)
        CodeLanguage.XML, CodeLanguage.HTML -> highlightXml(text, colors)
        else -> AnnotatedString(text)
    }

private fun highlightJson(
    text: String,
    colors: su.kidoz.postest.ui.theme.ExtendedColors,
): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text[i] == '"' -> {
                    val endQuote = findEndQuote(text, i + 1)
                    val content = text.substring(i, endQuote + 1)

                    // Check if this is a property key (followed by colon)
                    val afterQuote = text.substring(endQuote + 1).trimStart()
                    val isProperty = afterQuote.startsWith(":")

                    withStyle(SpanStyle(color = if (isProperty) colors.syntaxProperty else colors.syntaxString)) {
                        append(content)
                    }
                    i = endQuote + 1
                }
                text[i].isDigit() || (text[i] == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val start = i

                    fun isNumberChar(c: Char) = c.isDigit() || c in ".eE+-"
                    while (i < text.length && isNumberChar(text[i])) {
                        i++
                    }
                    withStyle(SpanStyle(color = colors.syntaxNumber)) {
                        append(text.substring(start, i))
                    }
                }
                text.substring(i).startsWith("true") -> {
                    withStyle(SpanStyle(color = colors.syntaxBoolean)) {
                        append("true")
                    }
                    i += 4
                }
                text.substring(i).startsWith("false") -> {
                    withStyle(SpanStyle(color = colors.syntaxBoolean)) {
                        append("false")
                    }
                    i += 5
                }
                text.substring(i).startsWith("null") -> {
                    withStyle(SpanStyle(color = colors.syntaxNull)) {
                        append("null")
                    }
                    i += 4
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }

private fun findEndQuote(
    text: String,
    start: Int,
): Int {
    var i = start
    while (i < text.length) {
        if (text[i] == '"' && text[i - 1] != '\\') {
            return i
        }
        i++
    }
    return text.length - 1
}

private fun highlightXml(
    text: String,
    colors: su.kidoz.postest.ui.theme.ExtendedColors,
): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // XML Comment: <!-- ... -->
                text.substring(i).startsWith("<!--") -> {
                    val endComment = text.indexOf("-->", i)
                    val end = if (endComment != -1) endComment + 3 else text.length
                    withStyle(SpanStyle(color = colors.syntaxComment)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }
                // CDATA section: <![CDATA[ ... ]]>
                text.substring(i).startsWith("<![CDATA[") -> {
                    val endCdata = text.indexOf("]]>", i)
                    val end = if (endCdata != -1) endCdata + 3 else text.length
                    withStyle(SpanStyle(color = colors.syntaxString)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }
                // XML declaration or processing instruction: <?...?>
                text.substring(i).startsWith("<?") -> {
                    val endPi = text.indexOf("?>", i)
                    val end = if (endPi != -1) endPi + 2 else text.length
                    withStyle(SpanStyle(color = colors.syntaxKeyword)) {
                        append(text.substring(i, end))
                    }
                    i = end
                }
                // Opening or closing tag
                text[i] == '<' -> {
                    i = parseXmlTag(text, i, colors, this)
                }
                else -> {
                    // Regular text content
                    val nextTag = text.indexOf('<', i)
                    val end = if (nextTag != -1) nextTag else text.length
                    append(text.substring(i, end))
                    i = end
                }
            }
        }
    }

private fun parseXmlTag(
    text: String,
    start: Int,
    colors: su.kidoz.postest.ui.theme.ExtendedColors,
    builder: AnnotatedString.Builder,
): Int {
    var i = start

    // Find the end of the tag
    val tagEnd = text.indexOf('>', i)
    if (tagEnd == -1) {
        builder.append(text.substring(i))
        return text.length
    }

    val tagContent = text.substring(i, tagEnd + 1)

    builder.apply {
        var j = 0

        // Opening bracket '<' or '</'
        withStyle(SpanStyle(color = colors.syntaxKeyword)) {
            if (tagContent.startsWith("</")) {
                append("</")
                j = 2
            } else {
                append("<")
                j = 1
            }
        }

        // Tag name
        val nameStart = j
        while (j < tagContent.length && !tagContent[j].isWhitespace() && tagContent[j] != '>' && tagContent[j] != '/') {
            j++
        }
        withStyle(SpanStyle(color = colors.syntaxProperty)) {
            append(tagContent.substring(nameStart, j))
        }

        // Attributes and closing
        while (j < tagContent.length) {
            when {
                tagContent[j].isWhitespace() -> {
                    append(tagContent[j])
                    j++
                }
                tagContent[j] == '/' || tagContent[j] == '>' -> {
                    withStyle(SpanStyle(color = colors.syntaxKeyword)) {
                        append(tagContent[j])
                    }
                    j++
                }
                tagContent[j] == '"' || tagContent[j] == '\'' -> {
                    // Attribute value
                    val quote = tagContent[j]
                    val valueEnd = tagContent.indexOf(quote, j + 1)
                    val end = if (valueEnd != -1) valueEnd + 1 else tagContent.length
                    withStyle(SpanStyle(color = colors.syntaxString)) {
                        append(tagContent.substring(j, end))
                    }
                    j = end
                }
                tagContent[j] == '=' -> {
                    append("=")
                    j++
                }
                else -> {
                    // Attribute name
                    val attrStart = j
                    while (j < tagContent.length &&
                        !tagContent[j].isWhitespace() &&
                        tagContent[j] != '=' &&
                        tagContent[j] != '>' &&
                        tagContent[j] != '/'
                    ) {
                        j++
                    }
                    withStyle(SpanStyle(color = colors.syntaxNumber)) {
                        append(tagContent.substring(attrStart, j))
                    }
                }
            }
        }
    }

    return tagEnd + 1
}
