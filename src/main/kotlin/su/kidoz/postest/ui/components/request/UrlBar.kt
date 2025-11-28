package su.kidoz.postest.ui.components.request

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import su.kidoz.postest.domain.model.HttpMethod

@Composable
fun UrlBar(
    method: HttpMethod,
    url: String,
    onMethodChange: (HttpMethod) -> Unit,
    onUrlChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MethodSelector(
            selected = method,
            onSelect = onMethodChange,
        )

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = { Text("Enter URL or paste cURL") },
            modifier =
                Modifier
                    .weight(1f)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.key == Key.Enter &&
                            (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) &&
                            keyEvent.type == KeyEventType.KeyDown
                        ) {
                            onSend()
                            true
                        } else {
                            false
                        }
                    },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
        )

        Button(
            onClick = onSend,
            enabled = !isLoading && url.isNotBlank(),
            shape = RoundedCornerShape(4.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("Send")
        }
    }
}
