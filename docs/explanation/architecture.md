# Architecture

Postest is a Kotlin and Compose Multiplatform desktop application using an MVI-style state model with clean-architecture layering.

## Layers

```
Compose UI
    ↓ actions
MainViewModel + feature managers
    ↓
Domain use cases
    ↓
Repositories                    RequestExecutor
    ↓                                ↓
SQLDelight / SQLite              Ktor CIO / HTTP
```

- **UI** - Composables under `ui/` render state and emit actions. There is no business logic in the UI beyond view state.
- **View model** - `MainViewModel` exposes a single `AppState` as a `StateFlow` and delegates work to feature managers.
- **Managers** - `TabManager`, `RequestExecutionManager`, `CollectionManager`, `EnvironmentManager`, `HistoryManager`, `ImportExportManager`, and `SessionManager`.
- **Use cases** - `ExecuteRequestUseCase`, `ManageCollectionsUseCase`, `ManageEnvironmentsUseCase`, `ManageHistoryUseCase`, `ImportCollectionUseCase`, `ExportCollectionUseCase`, and `ManageSessionUseCase`.
- **Repositories** - SQLDelight-backed persistence for collections, environments, history, and workspace sessions.
- **HTTP** - `RequestExecutor` builds and sends requests with Ktor; `HttpClientFactory` configures the client.

## State and effects

`AppState` contains tabs, collections, environments, history, theme state, and dialog state. Each tab is a `TabState` with its request, response, loading flag, error, dirty flag, body drafts, and collection item link.

One-time events such as toasts and errors are sent through a side-effect channel rather than stored in state. Managers share a `StateUpdater` so updates are applied atomically to the same `StateFlow`.

## Collections

A collection is a tree of `CollectionItem.Request` and `CollectionItem.Folder` nodes. Each row in the `CollectionItem` table stores a `parent_folder_id`; repositories rebuild the nested tree from those rows.

Moving an item changes its `parent_folder_id` and position, updates the owning collection's timestamp, and applies the same move to the in-memory tree without a reload. The UI prevents dropping a folder into itself or its own subtree. Selection is tracked by collection item id, so two items that share the same request id stay distinguishable.

## Persistence

SQLDelight defines the schema and queries in `Postest.sq`. The database stores:

- collections and nested collection items (`parent_folder_id`)
- environments and variables
- history entries
- the saved workspace session

Repositories apply incremental in-memory updates after writes, keeping the UI list consistent without a full reload. Changes to collection items also update the collection timestamp and keep the in-memory ordering aligned with the database ordering.

## HTTP and threading

Network calls run through Ktor's CIO engine. Repositories move database work to `Dispatchers.IO`; the view model uses `Dispatchers.Main`. Binary request files are read before the request is built so file IO does not block the UI.

## Dependency injection

Koin modules in `di/AppModule.kt` provide the database driver, repositories, use cases, HTTP client, and view model.

## Testing

- JUnit/kotlin.test with Ktor MockEngine for HTTP behavior
- MockK for use-case and manager tests
- SQLDelight in-memory databases for repositories
- Compose UI tests for dialogs, controls, and the collection tree

## Related

- [Request lifecycle](request-lifecycle.md)
- [Workspace sessions](workspace-sessions.md)
- [Build and run from source](../how-to/build-and-run.md)
