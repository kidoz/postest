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
Domain-owned repository / transport / format contracts
    ↑ implementations
SQLDelight repositories         Ktor RequestExecutor
    ↓                                ↓
SQLite                          HTTP
```

- **UI** - Composables under `ui/` render state and emit actions. There is no business logic in the UI beyond view state.
- **View model** - `MainViewModel` derives the public `AppState` from workspace, presentation, and repository flows and delegates commands to feature managers.
- **Managers** - `TabManager`, `RequestExecutionManager`, `CollectionManager`, `EnvironmentManager`, `HistoryManager`, `ImportExportManager`, and `SessionManager`.
- **Use cases** - `ExecuteRequestUseCase`, `ManageCollectionsUseCase`, `ManageEnvironmentsUseCase`, `ManageHistoryUseCase`, `ImportCollectionUseCase`, `ExportCollectionUseCase`, and `ManageSessionUseCase`.
- **Contracts** - `domain/port/` owns repository, session, transport, collection-format, and file-access interfaces. Domain use cases depend on these interfaces; adapters implement them and Koin supplies the bindings. Domain signatures do not expose SQLDelight or Ktor types.
- **Repositories** - SQLDelight-backed persistence for collections, environments, history, and workspace sessions.
- **Formats and files** - Format adapters decode/encode collections through domain contracts. A local file adapter performs bounded reads and writes off the UI thread.
- **HTTP** - `RequestExecutor` builds and sends requests with Ktor; `HttpClientFactory` configures the client.

## State and effects

`AppState` contains tabs, collections, environments, history, theme state, and dialog state. Each tab is a `TabState` with its request, response, loading flag, error, dirty flag, body drafts, and collection item link.

`WorkspaceStore`, owned by `MainViewModel`, is the sole mutable owner of open tabs, active-tab selection, pending closes, and request execution identities. Managers send typed `WorkspaceCommand` values; they cannot rewrite arbitrary application state. Pure workspace transitions update related fields atomically. Request completions carry an execution id, so results from superseded requests or removed tabs are ignored.

Theme and dialog state remain presentation concerns. Saved collections, environments, and history are projections of database observations. The public `AppState` combines these sources and is not another writable store. Loading and observation errors are explicit; sending waits for startup data to be available.

One-time events such as toasts and errors are sent through a side-effect channel. `SessionManager` observes a read-only workspace projection for autosave. Startup restoration is coordinated by `MainViewModel` and applied through one typed restore command.

## Collections

A collection is a tree of `CollectionItem.Request` and `CollectionItem.Folder` nodes. Each row in the `CollectionItem` table stores a `parent_folder_id`; repositories rebuild the nested tree from those rows.

Moving an item changes its `parent_folder_id` and position and updates the owning collection's timestamp in one transaction. Observers reconstruct the collection tree from the committed rows. The UI prevents dropping a folder into itself or its own subtree. Selection is tracked by collection item id, so two items that share the same request id stay distinguishable.

## Persistence

SQLDelight defines the schema and queries in `Postest.sq`. The database stores:

- collections and nested collection items (`parent_folder_id`)
- environments and variables
- history entries
- the saved workspace session

SQLite is the source of truth for saved data. SQLDelight query listeners invalidate collection observations for both parent and child table changes; complete trees are read in a transaction using bulk queries. Environment observations similarly include variable changes and publish the environment list and active selection together. One-shot environment reads query SQLite directly and do not require a cache to be initialized. History observations use the same 200-entry bound as retention.

## HTTP and threading

Network calls run through Ktor's CIO engine. Repositories move database work to `Dispatchers.IO`; the view model uses `Dispatchers.Main`. Binary request files are read before the request is built so file IO does not block the UI.

## Dependency injection

Koin modules in `di/AppModule.kt` bind domain contracts to database, transport, format, and local-file adapters and provide the use cases and view model. Architecture tests reject domain dependencies on adapters, presentation, Ktor, or SQLDelight.

## Testing

- JUnit/kotlin.test with Ktor MockEngine for HTTP behavior
- MockK for use-case and manager tests
- SQLDelight in-memory databases for repositories
- Compose UI tests for dialogs, controls, and the collection tree

## Related

- [Request lifecycle](request-lifecycle.md)
- [Workspace sessions](workspace-sessions.md)
- [Build and run from source](../how-to/build-and-run.md)
