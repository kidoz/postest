package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.HttpMethod
import su.kidoz.postest.domain.model.RequestProtocol
import su.kidoz.postest.ui.theme.AppTheme

@Composable
fun MethodSelector(
    selected: HttpMethod,
    onSelect: (HttpMethod) -> Unit,
    modifier: Modifier = Modifier,
    protocol: RequestProtocol = RequestProtocol.HTTP,
    onProtocolSelect: (RequestProtocol) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    val extendedColors = AppTheme.extendedColors

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable(role = Role.Button) { expanded = true }
                    .semantics { contentDescription = "Select method" }
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (protocol == RequestProtocol.WEBSOCKET) "WS" else selected.name,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (protocol == RequestProtocol.WEBSOCKET) {
                        extendedColors.methodWebSocket
                    } else {
                        getMethodColor(selected, extendedColors)
                    },
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Select method",
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("WebSocket", color = extendedColors.methodWebSocket) },
                onClick = {
                    onProtocolSelect(RequestProtocol.WEBSOCKET)
                    expanded = false
                },
            )
            HorizontalDivider()
            HttpMethod.entries.forEach { method ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = method.name,
                            color = getMethodColor(method, extendedColors),
                        )
                    },
                    onClick = {
                        onProtocolSelect(RequestProtocol.HTTP)
                        onSelect(method)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun getMethodColor(
    method: HttpMethod,
    colors: su.kidoz.postest.ui.theme.ExtendedColors,
) = when (method) {
    HttpMethod.GET -> colors.methodGet
    HttpMethod.POST -> colors.methodPost
    HttpMethod.PUT -> colors.methodPut
    HttpMethod.PATCH -> colors.methodPatch
    HttpMethod.DELETE -> colors.methodDelete
    HttpMethod.HEAD -> colors.methodHead
    HttpMethod.OPTIONS -> colors.methodOptions
}
