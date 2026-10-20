# Changelog

All notable changes to Postest are documented in this file. Releases are listed newest first.

## [Unreleased]

**Encrypted local storage, safer imports, and request handling fixes.**

### Highlights

- **Encrypted Storage** - User content is encrypted at rest with AES-256-GCM; the key lives in the OS credential store, and startup requires unlocking it.
- **Safer Upgrades** - Existing databases move through a verified staging file, with encrypted `.bak` snapshots retained for rollback.
- **WebSockets** - Open `ws://` and `wss://` connections, send and receive text messages, and inspect the live transcript.
- **OAuth 2.0** - Authorize through the system browser with Authorization Code + PKCE, or fetch tokens with Client Credentials, and reuse stored tokens with in-memory refresh.
- **Import and Export Fidelity** - URL-embedded query parameters now survive a Postman round trip.

### Added

- AES-256-GCM encryption for stored collections, requests, environments, history, and workspace snapshots.
- OS credential-store integration: macOS Keychain, Windows Credential Manager, and Linux Secret Service via libsecret.
- Recovery key setup and display through **Storage → Show Recovery Key**.
- Startup unlock, retry, and recovery flow; autosave stays blocked while storage is inaccessible.
- Exclusive storage lock that prevents two instances from writing the same database.
- Verified migration of the existing plaintext database into encrypted storage, with `.bak` snapshots.
- Automatic adoption of earlier `postest-protected.db` installations.
- WebSocket request tabs with connect/disconnect, text message composer, and a timestamped live transcript.
- WebSocket URL conversion from `http(s)://`, variable substitution, query parameters, headers, and Basic/Bearer/API-key authentication.
- Automatic connection cleanup when disconnecting, closing a tab, switching back to HTTP, or closing the application.
- WebSocket binary sending from a file, up to 10 MB per message.
- Optional WebSocket auto-reconnect with exponential backoff (10 attempts, 1–30 seconds).
- WebSocket transcripts restored after a restart, within a 1 MB / 200-message persistence budget.
- OAuth 2.0 Authorization Code with PKCE, using the system browser and a temporary loopback redirect.
- OAuth 2.0 Client Credentials and in-memory refresh-token handling.
- OAuth 2.0 tokens stored with the request, with fixed error messages that omit endpoints and token values.
- Postman OAuth 2.0 auth import and export.
- SHA-256 certificate and public-key pinning for HTTP and WebSocket requests, with Base64, hex, and `sha256/` pin formats.
- Certificate pins support variable substitution and run in addition to normal TLS chain validation.

### Changed

- Editing an environment no longer changes which environment is active.
- Postman export includes query parameters embedded in the request URL; import decodes them once and ignores URL fragments.

### Fixed

- Oversized streaming responses complete as soon as the 10 MB limit is reached instead of waiting for the stream to close.
- Import failures no longer expose file content or parser causes in error messages or logs.

### Security

- User-content columns are encrypted before they reach SQLite; structural IDs, timestamps, and flags remain readable.
- Recovery keys are never written to files or logs.
- Execution errors use fixed messages without URLs, hostnames, file paths, or exception details.

### Known Limitations

- OAuth 2.0 Password and Implicit grants are not supported. Refresh tokens are cached in memory; providers that rotate refresh tokens may require reauthorization after a restart.
- Received WebSocket binary payloads are summarized as a size notice instead of being rendered.
- Client certificates are not applied to WebSocket connections.
- Certificate pinning supports one leaf pin per request; backup pins and custom CA settings are not supported.
- Automatic key rotation is not implemented.

## [1.1.0] - 2026-10-20

**Workspace recovery, drag-and-drop collections, and desktop UI refinements.**

### Highlights

- **Workspace Recovery** - Reopen with your tabs, selected request, per-format body drafts, and the last response/error for each tab intact; edits autosave locally.
- **Drag-and-Drop Collections** - Create nested folders and move requests or folders between folders, or back to the collection root, by dragging.
- **Client Certificates** - Authenticate with mTLS client certificates configured per request.
- **Refined Desktop UI** - Reworked environment manager and main screen, shared desktop controls, resizable splitters, per-method colors, and refreshed branding.

### Downloads

| Platform | File |
|----------|------|
| macOS | `Postest-1.1.0.dmg` |
| Linux | `postest_1.1.0_amd64.deb` |
| Windows | `Postest-1.1.0.msi` |

### System Requirements

- Java 21+
- macOS 12+ / Linux (GTK) / Windows 10+

### What's Changed

#### Workspace & Drafts
- Restore open tabs, tab order, the active tab, and dirty state after restarting.
- Keep per-format request body drafts when switching between body types.
- Restore the last response and error for each tab together with the workspace.
- Confirm before closing a tab with unsaved edits.
- Autosave workspace snapshots locally and flush pending edits when the app closes.
- Remember which collection item a tab was opened from across restarts.

#### Collections
- Create folders at the collection root or inside another folder.
- Move requests and folders by dragging:
  - drop on a folder to move the item inside it;
  - drop on the collection header to move the item to the collection root.
- Prevent moving a folder into itself or its own subtree.
- Persist collection timestamps for item changes so ordering survives reloads.
- Track selection by collection item instead of request id, fixing duplicate highlights.
- Use collection item names when opening requests.
- Fix request row menus being squeezed to zero width.

#### Requests & Responses
- Client certificate (mTLS) authentication.
- Request cancellation.
- Multipart form-data fields and binary body file uploads.
- Detailed timing breakdown (DNS, connect, TLS, first byte, download).
- Request history limit and database indexes for faster startup.
- Fix Postman export when the target file has no parent directory.
- Move file IO off the UI thread.

#### Desktop UI
- Reworked environment manager and main screen.
- Shared desktop controls, section tabs, and resizable split panes.
- Per-method color palette across light and dark themes.
- Keep editors in sync when switching requests and environments.
- Label unnamed requests by URL path and highlight the selected request.
- Refreshed app icon and branding artwork.

#### Build & Maintenance
- JDK 25 toolchain with Gradle running on JDK 21.
- Dependency and Gradle wrapper upgrades.
- ktlint and detekt static analysis.
- GitHub Actions CI workflow.
- `justfile` tasks for build, test, run, package, lint, and formatting.
- "Check for Updates" against GitHub Releases.
- Expanded unit, repository, and Compose UI test coverage.

### Security Note

- The legacy encrypted secret store was removed; environment variable values are now stored locally in the app database.

### Known Limitations

- OAuth 2.0 authentication is not yet supported.
- WebSocket connections are not yet supported.
- Certificate pinning is not yet supported.

## [1.0.0] - 2025-11-29

**Initial Release** - A modern, lightweight REST API client built with Kotlin and Compose Multiplatform.

### Highlights

- **Full HTTP Support** - GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
- **Multiple Body Types** - JSON, XML, Form Data, Multipart, Binary, GraphQL
- **Authentication** - Basic, Bearer Token, API Key
- **Collections & Folders** - Organize and manage your API requests
- **Import/Export** - Postman (v1/v2/v2.1) and OpenAPI 3.x support
- **Environments** - Variable substitution with `{{variable}}` syntax
- **Request History** - Track and replay previous requests
- **Dark/Light Theme** - Modern Material 3 design

### Downloads

| Platform | File |
|----------|------|
| macOS | `Postest-1.0.0.dmg` |
| Linux | `postest_1.0.0_amd64.deb` |
| Windows | `Postest-1.0.0.msi` |

### System Requirements

- Java 21+
- macOS 12+ / Linux (GTK) / Windows 10+

### Features

#### HTTP Client
- Full support for HTTP methods: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
- Request builder with headers, query parameters, and multiple body types
- Response viewer with formatted JSON/XML, headers, and detailed timing info
- Request cancellation support

#### Body Types
- **JSON** - Syntax highlighted editor with formatting
- **XML** - Syntax highlighted editor
- **Form URL Encoded** - Key-value pair editor
- **Multipart Form Data** - Support for text fields
- **Raw** - Plain text with custom content type
- **Binary** - File upload support
- **GraphQL** - Query and variables editor

#### Authentication
- Basic Auth (username/password)
- Bearer Token
- API Key (header or query parameter)

#### Collections
- Organize requests into collections and nested folders
- Import from Postman Collection (v1.0, v2.0, v2.1)
- Import from OpenAPI 3.x specifications (JSON/YAML)
- Export to Postman Collection v2.1 format
- Collection-level variables

#### Environments
- Create and manage multiple environments
- Variable substitution with `{{variable}}` syntax
- Variables work in URLs, headers, query params, and body
- Quick environment switching

#### History
- Automatic request/response history tracking
- Replay previous requests
- Error details preserved for debugging
- Clear history option

#### User Interface
- Modern Material 3 design
- Dark and light theme support
- Tabbed interface for multiple requests
- Resizable split panes
- Keyboard shortcuts (Cmd/Ctrl+Enter to send, Cmd/Ctrl+T new tab)

### Known Limitations

- OAuth 2.0 authentication not yet supported
- WebSocket connections not yet supported
- Certificate pinning not yet supported

[Unreleased]: https://github.com/kidoz/postest/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/kidoz/postest/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/kidoz/postest/commits/v1.0.0
