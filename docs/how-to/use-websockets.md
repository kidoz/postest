# Use WebSocket connections

Postest can open WebSocket connections from a request tab and show the live message transcript.

## Open a WebSocket connection

1. In a request tab, open the method selector and choose **WebSocket**.
2. Enter a `ws://` or `wss://` URL. An `http://` or `https://` URL is converted to `ws://` or `wss://`.
3. Optionally configure **Params**, **Headers**, and **Auth**. Variables such as `{{baseUrl}}` and `{{token}}` are resolved when you connect.
4. Choose **Connect** (or press `Cmd/Ctrl + Enter` while the URL field is focused).

The button shows **Connecting** until the handshake completes, then changes to **Disconnect**.

## Send and receive messages

Use the message panel below the request:

- The header shows the current connection status and a **Clear** action for the transcript.
- Sent and received text frames appear as bubbles with a timestamp; system events such as a close reason appear as plain lines.
- Binary frames are reported as a size notice; binary payloads are not rendered.
- Type a message in the composer and choose **Send** or press `Cmd/Ctrl + Enter`.
- Select message text to copy it.

## Close the connection

Choose **Disconnect**, switch the tab back to an HTTP method, close the tab, or close the application. The connection is closed in each case.

## Limits and behavior

- The transcript keeps the last 500 messages in memory. It is not written to the workspace snapshot, so it does not survive a restart.
- Each sent or received text message is truncated at 1 MB for display, and a single incoming frame is limited to 10 MB.
- Only text messages can be sent. Received binary frames are summarized but not shown.
- Automatic reconnection is not provided.
- WebSocket traffic does not create request history entries.
- Client certificates (mTLS) are not applied to WebSocket connections; use headers or one of the header-based authentication schemes instead.
- WebSocket requests can be saved in collections. The protocol is restored when the request is reopened, but the Postman and OpenAPI formats have no WebSocket protocol field, so exports do not preserve it.

## Related

- [Authenticate requests](authenticate-requests.md)
- [Variables](../reference/variables.md)
- [Supported formats and body types](../reference/supported-formats.md)
