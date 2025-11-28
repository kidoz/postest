package su.kidoz.postest.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.KeyValue

@Composable
fun KeyValueEditor(
    items: List<KeyValue>,
    onItemsChange: (List<KeyValue>) -> Unit,
    keyPlaceholder: String = "Key",
    valuePlaceholder: String = "Value",
    keySuggestions: List<String> = emptyList(),
    valueSuggestionsProvider: ((String) -> List<String>)? = null,
    showFilter: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var filterText by remember { mutableStateOf("") }

    // Filter items based on search text
    val filteredItemsWithIndex =
        remember(items, filterText) {
            if (filterText.isBlank()) {
                items.mapIndexed { index, item -> index to item }
            } else {
                val lowerFilter = filterText.lowercase()
                items
                    .mapIndexed { index, item -> index to item }
                    .filter { (_, item) ->
                        item.key.lowercase().contains(lowerFilter) ||
                            item.value.lowercase().contains(lowerFilter)
                    }
            }
        }

    Column(modifier = modifier.padding(8.dp)) {
        // Filter field (only show if there are items to filter)
        if (showFilter && items.size > 3) {
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("Filter...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Filter",
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (filterText.isNotEmpty()) {
                        IconButton(
                            onClick = { filterText = "" },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear filter",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))

            // Show filter result count if filtering
            if (filterText.isNotBlank()) {
                Text(
                    text = "Showing ${filteredItemsWithIndex.size} of ${items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(
                filteredItemsWithIndex,
                key = { _, (originalIndex, _) -> originalIndex },
            ) { _, (originalIndex, item) ->
                KeyValueRow(
                    item = item,
                    keyPlaceholder = keyPlaceholder,
                    valuePlaceholder = valuePlaceholder,
                    keySuggestions = keySuggestions,
                    valueSuggestions = valueSuggestionsProvider?.invoke(item.key) ?: emptyList(),
                    onItemChange = { newItem ->
                        onItemsChange(
                            items.toMutableList().apply {
                                this[originalIndex] = newItem
                            },
                        )
                    },
                    onDelete = {
                        onItemsChange(
                            items.toMutableList().apply {
                                removeAt(originalIndex)
                            },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(
            onClick = {
                onItemsChange(items + KeyValue("", ""))
            },
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
    }
}

@Composable
private fun KeyValueRow(
    item: KeyValue,
    keyPlaceholder: String,
    valuePlaceholder: String,
    keySuggestions: List<String>,
    valueSuggestions: List<String>,
    onItemChange: (KeyValue) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.enabled,
            onCheckedChange = { checked ->
                onItemChange(item.copy(enabled = checked))
            },
        )

        if (keySuggestions.isNotEmpty()) {
            AutocompleteTextField(
                value = item.key,
                onValueChange = { newKey ->
                    onItemChange(item.copy(key = newKey))
                },
                suggestions = keySuggestions,
                placeholder = keyPlaceholder,
                modifier = Modifier.weight(1f),
            )
        } else {
            OutlinedTextField(
                value = item.key,
                onValueChange = { newKey ->
                    onItemChange(item.copy(key = newKey))
                },
                placeholder = { Text(keyPlaceholder) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.width(8.dp))

        if (valueSuggestions.isNotEmpty()) {
            AutocompleteTextField(
                value = item.value,
                onValueChange = { newValue ->
                    onItemChange(item.copy(value = newValue))
                },
                suggestions = valueSuggestions,
                placeholder = valuePlaceholder,
                modifier = Modifier.weight(1f),
            )
        } else {
            OutlinedTextField(
                value = item.value,
                onValueChange = { newValue ->
                    onItemChange(item.copy(value = newValue))
                },
                placeholder = { Text(valuePlaceholder) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
