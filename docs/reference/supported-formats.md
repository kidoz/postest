# Supported formats and body types

## HTTP methods

`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS`

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
- Client certificate (mTLS), in addition to the schemes above:
  - PEM certificate plus private key
  - PKCS#12 (`.p12`, `.pfx`) with optional passphrase

## Import

| Format | Versions |
|--------|----------|
| Postman Collection | v1.0, v2.0, v2.1 |
| OpenAPI | 3.x, JSON or YAML |

Files with `.json`, `.yaml`, or `.yml` extensions are accepted. The format is detected automatically.

## Export

- Postman Collection v2.1 (`.json`)

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
