# Request lifecycle

This page describes what happens between pressing **Send** and seeing a response.

## 1. Editing

Each tab holds an `HttpRequest`. Edits update the tab through `TabManager.updateRequest`, which marks the tab dirty and preserves drafts for other body formats. Workspace autosave observes the resulting state.

## 2. Sending

`RequestExecutionManager.sendRequest`:

1. takes the active tab's request;
2. cancels any request still running for that tab;
3. marks the tab as loading and clears the previous error;
4. starts a coroutine job tracked per tab.

## 3. Resolving the request

`ExecuteRequestUseCase` loads the active environment and passes it to `RequestExecutor`.

`RequestExecutor` then:

1. resolves `{{variables}}` in the URL, query parameters, and header keys/values;
2. validates the URL and headers;
3. loads a client certificate when one is configured, resolving paths and passphrase first;
4. applies authentication (Basic, Bearer, API key);
5. builds the body:
   - JSON and XML text
   - raw text with a resolved content type
   - form-url-encoded fields
   - multipart form fields
   - binary file content read from disk
   - GraphQL query and variables JSON
6. sends the request through Ktor CIO and measures timing.

Timeouts are 60 seconds for the request, 30 seconds to connect, and 60 seconds for the socket.

## 4. Response handling

The response is streamed into memory with a hard 10 MB limit. If the stream exceeds the limit, it is closed immediately, the body is truncated, and the response is marked as truncated instead of waiting for the server to finish. The result is converted to `HttpResponse` with status, headers, body, content type, size, and timing breakdown. Failures are returned as a failed `Result` with a fixed error message that omits URLs, hostnames, file paths, and exception details.

`RequestExecutionManager` stores either the response or the error in the tab, clears the loading flag, and adds a history entry containing the request, response/error, duration, and timestamp. History is pruned to the newest 200 entries.

## 5. Saving

The tab state change triggers the workspace autosave after a short debounce. Closing the app flushes the pending save.

## Cancellation

Cancelling a request cancels its coroutine job. The tab stops loading and no history entry is added. The active request job for a tab is also cancelled when a new request is sent from that tab.

## Related

- [Architecture](architecture.md)
- [Variables](../reference/variables.md)
- [Workspace sessions](workspace-sessions.md)
- [Security model](security-model.md)
