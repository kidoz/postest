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
~/.postest/posttest.db
```

The `WorkspaceSession` table keeps one `current` snapshot. A `backup` copy is made when a saved snapshot cannot be read.

## Unlocking and recovering protected storage

On first startup, save the recovery key in a password manager. You can display it again with **Storage → Show Recovery Key**.

If the OS credential store is locked, unlock it and choose **Retry unlocking**. If its key entry is missing or incorrect, enter your saved recovery key and choose **Recover storage**. Postest verifies the database before storing the recovered key. It does not start a new workspace or autosave while storage is inaccessible.

To recover on another computer, close Postest, place the backed-up database at the path above, then start it and enter the recovery key. Both the database file and the key are required. Do not replace a newer database without preserving a closed-app copy first.

## If your drafts are not restored

You may see **"Saved drafts could not be restored. A new workspace was opened."** This means the stored snapshot was invalid. Postest keeps the unreadable snapshot in the `backup` row and starts with a fresh workspace.

Check the application log for details:

```
~/.postest/logs/postest.log
```

## Start with a clean workspace

There is no separate reset command. Close the tabs you no longer need; the next save records only the remaining tabs.

To delete collections, environments, history, and the saved workspace, close Postest and remove:

```
~/.postest/posttest.db
```

Previous-version backups use `posttest.db.bak`, `posttest.db.bak.1`, and so on. Those files also contain saved data; remove them too if you intend to delete all saved copies.

**Warning:** this cannot be undone. Back up the file and save the recovery key first if you may need the data. The OS credential entry remains until removed through the operating system; a new database uses a new key and vault identifier.

## Related

- [Workspace sessions](../explanation/workspace-sessions.md)
- [Configuration, files, and limits](../reference/configuration.md)
- [Build and run from source](build-and-run.md)
