package su.kidoz.postest.data.security

/** The complete inventory of persisted user text. Structural IDs, flags and ordering remain readable. */
internal val contentColumns =
    linkedMapOf(
        "Collection" to listOf("name", "description", "auth_json", "variables_json"),
        "CollectionItem" to listOf("name", "description", "request_json"),
        "Environment" to listOf("name"),
        "EnvironmentVariable" to listOf("key", "value"),
        "HistoryEntry" to listOf("request_json", "response_json", "error_message"),
        "WorkspaceSession" to listOf("snapshot_json"),
    )
