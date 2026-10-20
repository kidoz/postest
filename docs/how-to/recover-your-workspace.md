# Recover your workspace after a restart

Postest autosaves your open tabs and restores them the next time it starts.

## What is restored

- open tabs and their order
- the active tab
- request data (method, URL, headers, params, body, auth, client certificate)
- per-format body drafts kept when switching body types
- the dirty/edited marker for each tab
- the last response and error for each tab
- the collection item a tab was opened from

## What is not restored

- a request that was still loading (it is not resumed)
- the selected sidebar tab, scroll positions, and splitter positions
- window size and position
- the light/dark theme choice

## How saving works

Changes are saved automatically about 300 ms after the tab state changes. Closing the app flushes any pending save before exiting.

The workspace is stored in the same SQLite database as collections, environments, and history:

```
~/.postest/postest.db
```

The `WorkspaceSession` table keeps one `current` snapshot. A `backup` copy is made when a saved snapshot cannot be read.

## If your drafts are not restored

You may see **"Saved drafts could not be restored. A new workspace was opened."** This means the stored snapshot was invalid. Postest keeps the unreadable snapshot in the `backup` row and starts with a fresh workspace.

Check the application log for details:

```
~/.postest/logs/postest.log
```

## Start with a clean workspace

There is no separate reset command. Close the tabs you no longer need; the next save records only the remaining tabs.

To delete all application data, including collections, environments, history, and the saved workspace, close Postest and remove:

```
~/.postest/postest.db
```

**Warning:** this cannot be undone. Back up the file first if you may need the data.

## Related

- [Workspace sessions](../explanation/workspace-sessions.md)
- [Configuration, files, and limits](../reference/configuration.md)
- [Build and run from source](build-and-run.md)
