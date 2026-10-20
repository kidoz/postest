# Security model

Postest is a local desktop client. It does not sync collections, send telemetry, or route traffic through a service.

## Data at rest

All application data is stored in:

```
~/.postest/posttest.db
```

This includes:

- collections and requests, including Basic/Bearer/API-key credentials
- client-certificate paths and passphrases
- environment variables, including values marked secret
- request history with request and response bodies
- the workspace session, including open requests and their last responses

All user-content columns are encrypted with AES-256-GCM before SQLite receives them. This includes names, descriptions, collection authentication and variables, request and response JSON, environment keys and values (including ordinary variables), history errors, and both workspace snapshots. The key is stored in macOS Keychain, Windows Credential Manager, or a Linux Secret Service provider through libsecret. No plaintext key-file fallback is used.

This is payload encryption, not whole-database encryption. SQLite schema, IDs and relationships, timestamps, ordering, record counts, flags, and approximate content sizes remain visible. Authentication binds each encrypted value to its vault, table, row ID, and column. It detects ciphertext changes and substitution between those contexts; it does not prevent row deletion, modification of unencrypted metadata, or rollback to an older valid database.

Repositories and autosave start only after the database key and all encrypted content have been authenticated. A locked/unavailable credential store, missing key, or authentication failure blocks startup instead of opening an empty workspace. Native operations run off the UI thread.

On first use, save the random recovery key in a password manager. **Storage → Show Recovery Key** displays it again. This is the database encryption key encoded for recovery, not a password. Anyone with it and the database can decrypt the content. Postest does not save this recovery text in a file or log. Losing both the OS credential entry and the recovery key makes the data inaccessible.

Close Postest before copying the database for backup. To move to another machine, restore it as `~/.postest/posttest.db`, start Postest, and enter the saved recovery key. Recovery authenticates the entire database before replacing the OS credential entry. A working OS credential store is still required.

### Existing installations

The active file is `posttest.db`; migration uses `posttest.db.pending`. Previous-version snapshots are saved as `posttest.db.bak`, then `.bak.1`, `.bak.2`, and so on without overwriting existing backups. These snapshots contain encrypted payloads, including when migrating from plaintext. Earlier encrypted installations using `postest-protected.db` (or a completed `postest-protected.pending.db`) are adopted automatically with the same vault and encryption key. Keep backup files and the recovery key separately.

Before upgrading, close all older Postest instances and database tools. On first startup, Postest copies `postest.db` into a fresh encrypted staging database. It checks authentication, row counts, SQLite integrity, and foreign-key relationships before an atomic handover. Unknown schemas block migration. Complete staging files can be recovered after an interruption; incomplete staging files are rebuilt from the retained source. Repositories never write live data to a staging file.

After successful handover, Postest removes the old database and its SQLite sidecars. If cleanup fails, startup stays blocked and can be retried. Ordinary file deletion cannot erase copies already present in SSD storage, filesystem snapshots, or external backups. Retire those plaintext backups separately.

### Platform requirements and limits

- macOS uses generic-password items in the default keychain. Packaged releases need stable code signing and keychain-access verification across upgrades. Unsigned development JVMs use a separate credential service identifier, so switching between source and packaged builds can require the recovery key.
- Windows stores a generic credential in the current user's credential set, persisted across that user's sessions on the same computer.
- Linux requires `libsecret-1.so.0`, GLib, a session D-Bus, and a compatible Secret Service provider such as GNOME Keyring. Install the distribution's libsecret runtime package; an unavailable service blocks startup.
- JDK native access must be enabled; the Gradle launcher and packaged launcher include `--enable-native-access=ALL-UNNAMED`.
- Encryption protects copied files and offline access. It does not isolate an unlocked application from a compromised user session. Plaintext exists in JVM memory while used, and immutable strings cannot be reliably erased.
- Exports, external certificate/private-key files, existing logs, and external backups have their own protection requirements. Database encryption does not encrypt them.
- Automatic key rotation is not implemented. Format versioning rejects unsupported formats instead of attempting a plaintext fallback.

### Verification

Automated tests use synthetic keys and fake credential stores. They cover cipher authentication, context substitution, missing/wrong/locked keys, verified recovery, exclusive opening, legacy migration, interrupted handover, plaintext scans, and startup gating. A native CoreFoundation smoke test exercises the macOS interop bridge without accessing credentials.

Before release, verify actual credential-store read/write and restart behavior on macOS, Windows, and Linux; locked/cancelled prompts; Linux without a provider; and access across signed macOS application upgrades. These platform interactions cannot be established by fake-store tests.

## Data in transit

- Requests go directly to the target server.
- HTTPS uses the platform's default certificate validation.
- Client certificates (PEM or PKCS#12) are supported for mTLS on HTTP requests.
- WebSocket connections use the same direct connection and platform TLS validation for `wss://`; client certificates are not applied to them.
- Certificate pinning and a custom CA setting are not available.
- There is no proxy configuration.

## Logging

- Application logs are written to `~/.postest/logs/`.
- Request bodies, response bodies, and WebSocket message contents are not logged.
- Optional HTTP header logging is off by default. When enabled, `Authorization`, `Proxy-Authorization`, `Cookie`, and headers containing `token`, `secret`, `api-key`, or `auth` are sanitized. See [Configuration](../reference/configuration.md).
- Execution errors use fixed messages. They omit resolved URLs, hostnames, file paths, and exception text; logs record only the exception type and the request host.
- Import errors and logs omit file content and parser exception chains, so a malformed collection or specification is not echoed into logs or error toasts.

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
