# Supported formats and body types

## HTTP methods

`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`

## WebSocket connections

- URLs: `ws://` and `wss://`; `http://` and `https://` URLs are converted automatically.
- Text frames can be sent and received; binary files up to 10 MB can be sent, and received binary frames are shown as a size notice.
- Optional auto-reconnect retries at most 10 times with 1–30 second backoff.
- Query parameters, headers, Basic/Bearer/API-key/OAuth 2.0 authentication, and `{{variable}}` substitution are supported.
- Client certificates are not applied to WebSocket connections.
- The transcript keeps the last 500 messages in memory and restores the newest messages within a 1 MB / 200-message budget.

## Request body types

The body editor exposes:

| Type | Notes |
|------|-------|
| **None** | No request body |
| **JSON** | Syntax-highlighted editor with formatting |
| **XML** | Syntax-highlighted editor with formatting |
| **Form URL Encoded** | Key/value fields sent as `application/x-www-form-urlencoded` |
| **Form Data** | Multipart fields sent as `multipart/form-data` (text fields) |
| **Raw** | Text with a custom `Content-Type` |
| **Binary** | Reads a file from disk and sends `application/octet-stream` |

GraphQL request data is supported by the request model and executor, but the body editor does not currently expose a GraphQL mode.

The form-data editor lets you mark a field as a file and choose a path, but the current executor sends that field as text. Multipart file parts are not yet supported; use the Binary body type to upload a file.

## Authentication

- None
- Basic (username/password)
- Bearer token
- API key (header or query parameter)
- OAuth 2.0 Authorization Code with PKCE (system browser + loopback redirect)
- OAuth 2.0 Client Credentials

## Certificate pinning

- Pin the leaf certificate (DER) or public key (SubjectPublicKeyInfo) with a SHA-256 hash.
- Accepts Base64, hex, or `sha256/...` values and one pin per request.
- Applies to HTTP and WebSocket requests; normal TLS chain validation always runs first.
- Custom CA certificates and certificate-pinning bypass are not supported.
- Client certificate (mTLS), in addition to the schemes above:
  - PEM certificate plus private key
  - PKCS#12 (`.p12`, `.pfx`) with optional passphrase

## Import

| Format | Versions |
|--------|----------|
| Postman Collection | v1.0, v2.0, v2.1 |
| OpenAPI | 3.x, JSON or YAML |

Files with `.json`, `.yaml`, or `.yml` extensions are accepted. The format is detected automatically.

Neither import format represents WebSocket connections, so imported requests are always HTTP.

## Export

- Postman Collection v2.1 (`.json`)

Postman Collections have no WebSocket protocol field. A WebSocket request is exported with its URL, headers, and parameters as an HTTP request, and the protocol is not preserved.

## Response display

- Formatted JSON, XML, and HTML
- Raw response text
- Response headers
- Status code, response time, and size
- Timing breakdown: DNS, connect, TLS handshake, first byte, download

Large response bodies show the first 200 KB by default, with an option to load the full content. Response bodies are capped at 10 MB before display; larger responses are marked as truncated.

## Related

- [Import and export collections](../how-to/import-and-export.md)
- [Authenticate requests](../how-to/authenticate-requests.md)
- [Configuration, files, and limits](configuration.md)
