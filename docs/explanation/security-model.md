# Security model

Postest is a local desktop client. It does not sync collections, send telemetry, or route traffic through a service.

## Data at rest

All application data is stored in:

```
~/.postest/postest.db
```

This includes:

- collections and requests, including Basic/Bearer/API-key credentials
- client-certificate paths and passphrases
- environment variables, including values marked secret
- request history with request and response bodies
- the workspace session, including open requests and their last responses

The database is **not encrypted**. Secret variables are masked in the environment editor, but their values are stored like ordinary variables. Use full-disk encryption and protect access to your user account if this data is sensitive.

## Data in transit

- Requests go directly to the target server.
- HTTPS uses the platform's default certificate validation.
- Client certificates (PEM or PKCS#12) are supported for mTLS.
- Certificate pinning and a custom CA setting are not available.
- There is no proxy configuration.

## Logging

- Application logs are written to `~/.postest/logs/`.
- Request bodies and response bodies are not logged.
- Optional HTTP header logging is off by default. When enabled, `Authorization`, `Cookie`, and headers containing `token` or `secret` are sanitized. See [Configuration](../reference/configuration.md).
- Error messages can include the request URL (for example, an invalid-URL error), so avoid putting secrets directly in URLs when sharing logs.

## Network access

- Target requests are sent only when you press Send.
- **Check for Updates** makes an on-demand request to the GitHub Releases API.
- No analytics or crash-reporting service is used.

## Credentials and collections

Credentials are stored with the request or environment that uses them. Exporting a collection to Postman includes the request data, so review exported files before sharing them.

## Recommendations

- Use environment variables for tokens instead of hard-coding them in request URLs or headers.
- Store the database on an encrypted disk.
- Do not commit exported collections or logs that contain real credentials.
- Prefer short-lived tokens that can be rotated if a file is exposed.

## Related

- [Authenticate requests](../how-to/authenticate-requests.md)
- [Configuration, files, and limits](../reference/configuration.md)
- [Workspace sessions](workspace-sessions.md)
