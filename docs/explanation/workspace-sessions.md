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

`SessionManager` observes a read-only projection of `WorkspaceStore` and saves after a 300 ms debounce. Changes to loading flags, execution identities, or unrelated repository/presentation data do not trigger draft writes. The session manager cannot edit live tabs.

Writes use one serialized writer with monotonically increasing revisions. An in-progress commit is allowed to finish rather than relying on cancellation to interrupt synchronous database work.

Closing waits for restoration, freezes workspace edits, cancels and joins request jobs, and flushes the latest authoritative workspace snapshot. The flush waits for any older commit before writing the final snapshot. The app exits only after success. On failure, command handling and autosave resume so the user can keep working and retry.

## Where it is stored

The snapshot is JSON, encrypted when the workspace uses encrypted storage, in the `WorkspaceSession` table of `~/.postest/posttest.db`:

| Row | Purpose |
|-----|---------|
| `current` | active workspace snapshot |
| `backup` | copy of a snapshot that failed validation |

Saving replaces the `current` row. On startup, `SessionRepository` reads it, checks the version and tab ids, and `MainViewModel` sends a typed restore command to `WorkspaceStore`.

If the stored JSON is invalid or unsupported, Postest copies the row to `backup`, reports that saved drafts could not be restored, and starts with a fresh workspace. The error message deliberately does not include request content.

## Restore merges

If the user edits the workspace while the saved session is still loading, the restored tabs are merged with the tabs created during startup rather than discarding either set. The active tab is kept only if it still exists after the merge.

## Trade-offs

- Responses are stored with the session using the persisted-response size limit. The live response remains separate from the bounded recovery copy.
- The session contains the same sensitive values as the request: auth credentials, client-certificate passphrases, and response bodies. The snapshot and its backup copy follow the workspace storage mode: encrypted or readable plaintext. See [Security model](security-model.md).
- The snapshot is local to the machine and is never uploaded.

## Related

- [Recover your workspace](../how-to/recover-your-workspace.md)
- [Configuration, files, and limits](../reference/configuration.md)
- [Architecture](architecture.md)
