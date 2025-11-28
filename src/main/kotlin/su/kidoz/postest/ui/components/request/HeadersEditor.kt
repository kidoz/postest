package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import su.kidoz.postest.domain.model.KeyValue
import su.kidoz.postest.ui.components.common.HttpHeaders
import su.kidoz.postest.ui.components.common.KeyValueEditor

@Composable
fun HeadersEditor(
    headers: List<KeyValue>,
    onHeadersChange: (List<KeyValue>) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyValueEditor(
        items = headers,
        onItemsChange = onHeadersChange,
        keyPlaceholder = "Header name",
        valuePlaceholder = "Header value",
        keySuggestions = HttpHeaders.commonHeaders,
        valueSuggestionsProvider = { headerName ->
            HttpHeaders.getSuggestionsForHeader(headerName)
        },
        modifier = modifier.fillMaxSize(),
    )
}
