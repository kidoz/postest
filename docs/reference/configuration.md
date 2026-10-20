# Configuration, files, and limits

Postest has no configuration file. Settings are limited to what is stored in the application database and the optional environment variables below.

## Application files

Everything lives under the user's home directory:

| Path | Contents |
|------|----------|
| `~/.postest/postest.db` | SQLite database: collections, environments, history, workspace session |
| `~/.postest/logs/postest.log` | application log |
| `~/.postest/logs/postest-error.log` | error-only log |

Log rotation:

- `postest.log` keeps 7 days, up to 50 MB.
- `postest-error.log` keeps 30 days, up to 100 MB.

The database is a normal SQLite file. Back it up while the app is closed.

## HTTP timeouts

| Timeout | Value |
|---------|-------|
| Request | 60 seconds |
| Connect | 30 seconds |
| Socket | 60 seconds |

## History

- The database keeps the newest 200 history entries.
- The history list loads the newest 100 entries.
- Opening a history entry restores its request and response in a new tab.

## HTTP header logging

HTTP header logging is off by default. Enable it when starting the app from a shell:

```bash
POSTEST_HTTP_LOGGING=true ./gradlew run
```

The application also reads the `postest.httpLogging` system property when its JVM is started with `-Dpostest.httpLogging=true` (for example, from a custom launcher).

When enabled, Ktor logs request and response **headers only**. `Authorization`, `Cookie`, and headers containing `token` or `secret` are sanitized. Bodies are not logged.

## Network access

- Requests go directly to the target URL. There is no proxy configuration.
- **Check for Updates** contacts the GitHub Releases API for `kidoz/postest` when invoked from the application menu.

## Limits and unsupported settings

- No proxy, custom CA, redirect toggle, or request-timeout setting in the UI.
- No certificate pinning.
- No OAuth 2.0 or WebSocket support.
- The theme choice is not persisted between runs.

## Related

- [Security model](../explanation/security-model.md)
- [Workspace sessions](../explanation/workspace-sessions.md)
- [Build and run from source](../how-to/build-and-run.md)
