# Use WebSocket connections

Postest can open WebSocket connections from a request tab and show the live message transcript.

## Open a WebSocket connection

1. In a request tab, open the method selector and choose **WebSocket**.
2. Enter a `ws://` or `wss://` URL. An `http://` or `https://` URL is converted to `ws://` or `wss://`.
3. Optionally configure **Params**, **Headers**, and **Auth**. Variables such as `{{baseUrl}}` and `{{token}}` are resolved when you connect. Certificate pinning configured on the **Auth** tab also applies to the WebSocket handshake.
4. Choose **Connect** (or press `Cmd/Ctrl + Enter` while the URL field is focused).

The button shows **Connecting** until the handshake completes, then changes to **Disconnect**.

## Send and receive messages

Use the message panel below the request:

- The header shows the current connection status and a **Clear** action for the transcript.
- Sent and received text frames appear as bubbles with a timestamp; system events such as a close reason appear as plain lines.
- Received binary frames are reported as a size notice; payloads are not rendered. Use the attach button next to **Send** to send a binary file as one frame.
- Type a message in the composer and choose **Send** or press `Cmd/Ctrl + Enter`.
- Select message text to copy it.

## Close the connection

Choose **Disconnect**, switch the tab back to an HTTP method, close the tab, or close the application. The connection is closed in each case.

## Reconnect automatically

Enable **Auto-reconnect** in the transcript header. If the server closes the connection or an attempt fails, Postest retries with exponential backoff from 1 second up to 30 seconds, for at most 10 attempts. Manual **Disconnect**, closing the tab, or switching back to HTTP stops the retry loop.

The setting applies to the current tab and is not restored after a restart.

## Limits and behavior

- The transcript keeps the last 500 messages in memory. Workspace snapshots keep the newest messages within a 1 MB / 200-message budget, so the transcript is restored after a restart.
- Each sent or received text message is truncated at 1 MB for display, and a single frame is limited to 10 MB.
- Binary messages can be sent from a file up to 10 MB. Received binary frames are summarized but not rendered.
- WebSocket traffic does not create request history entries.
- Client certificates (mTLS) are not applied to WebSocket connections; use headers or one of the header-based authentication schemes instead.
- WebSocket requests can be saved in collections. The protocol is restored when the request is reopened, but the Postman and OpenAPI formats have no WebSocket protocol field, so exports do not preserve it.

## Related

- [Authenticate requests](authenticate-requests.md)
- [Variables](../reference/variables.md)
- [Supported formats and body types](../reference/supported-formats.md)
