# Workspace sessions

Postest keeps a snapshot of the current workspace so a restart does not lose open work.

## The saved model

A `DraftSession` contains:

- `version`
- `activeTabId`
- a list of `DraftTab` entries

Each `DraftTab` stores:

- the tab id
- the request (including auth and client-certificate configuration)
- the dirty flag
- body drafts for formats that are not currently active
- the linked collection item id
- the last response and error

Loading state is not saved. A tab never restores as "loading" because no request is running after a restart.

## When it saves

`SessionManager` observes the application state and saves a snapshot after a 300 ms debounce. When the window closes, `prepareToClose` stops the observer, writes the latest snapshot, and only then lets the app exit. If the write fails, the app stays open and shows an error so the user can retry.

## Where it is stored

The snapshot is a JSON string in the `WorkspaceSession` table of `~/.postest/postest.db`:

| Row | Purpose |
|-----|---------|
| `current` | active workspace snapshot |
| `backup` | copy of a snapshot that failed validation |

Saving replaces the `current` row. On startup, `SessionRepository` reads it, checks the version and tab ids, and `SessionManager` restores the tabs.

If the stored JSON is invalid or unsupported, Postest copies the row to `backup`, reports that saved drafts could not be restored, and starts with a fresh workspace. The error message deliberately does not include request content.

## Restore merges

If the user edits the workspace while the saved session is still loading, the restored tabs are merged with the tabs created during startup rather than discarding either set. The active tab is kept only if it still exists after the merge.

## Trade-offs

- Responses are stored with the session, so large response bodies increase the size of the `WorkspaceSession` row.
- The session contains the same sensitive values as the request: auth credentials, client-certificate passphrases, and response bodies. The database is not encrypted. See [Security model](security-model.md).
- The snapshot is local to the machine and is never uploaded.

## Related

- [Recover your workspace](../how-to/recover-your-workspace.md)
- [Configuration, files, and limits](../reference/configuration.md)
- [Architecture](architecture.md)
