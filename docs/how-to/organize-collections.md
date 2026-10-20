# Organize collections and move items

Collections hold saved requests and folders. This guide covers creating, renaming, moving, and deleting them.

## Create a collection or folder

- **New collection** - click the button in the sidebar header.
- **New folder** - open a collection's `⋮` menu and choose **Add Folder**.
- **Nested folder** - open a folder's `⋮` menu and choose **Add Folder**.

## Add a request to a collection

1. Open the request in a tab.
2. Open the collection's `⋮` menu and choose **Add Request**.
3. Enter a name and confirm.

The request is added to the collection root. It keeps the request data from the active tab.

## Move requests and folders

Drag an item and drop it on a target:

- **Folder header** - moves the item into that folder.
- **Collection header** - moves the item to the collection root.

While dragging, valid targets are highlighted; the target under the pointer is highlighted more strongly. Release anywhere else to cancel.

Rules:

- Moves are within one collection. Dragging to another collection is not supported.
- The item is appended to the end of the target folder or root.
- A folder cannot be moved into itself or into one of its own descendants.
- Dropping an item on its current parent is a no-op.
- Manual reordering inside a folder is not supported.

Moves are saved immediately and confirmed with a toast.

## Rename or delete

- **Rename** - use the item's `⋮` menu.
- **Delete a request** - use the request's `⋮` menu and confirm.
- **Delete a folder** - use the folder's `⋮` menu. This deletes the folder and everything inside it.
- **Delete a collection** - use the collection's `⋮` menu. This deletes all of its requests and folders.

Deletion cannot be undone.

## Ordering and persistence

Changing an item updates the collection's `updated_at` timestamp. Collections are ordered by most recent update, and the order is preserved across restarts.

## Related

- [Import and export collections](import-and-export.md)
- [Collections and drag-and-drop in the architecture](../explanation/architecture.md)
- [Recover your workspace](recover-your-workspace.md)
