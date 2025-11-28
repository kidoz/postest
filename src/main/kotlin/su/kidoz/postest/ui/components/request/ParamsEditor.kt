package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import su.kidoz.postest.domain.model.KeyValue
import su.kidoz.postest.ui.components.common.KeyValueEditor

@Composable
fun ParamsEditor(
    params: List<KeyValue>,
    onParamsChange: (List<KeyValue>) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyValueEditor(
        items = params,
        onItemsChange = onParamsChange,
        keyPlaceholder = "Parameter name",
        valuePlaceholder = "Parameter value",
        modifier = modifier.fillMaxSize(),
    )
}
