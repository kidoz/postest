package su.kidoz.postest.data.security

enum class StorageMode {
    ENCRYPTED,
    UNENCRYPTED,
}

/** Selected by authenticated vault metadata or an explicit, versioned plaintext database format. */
interface RecordCodec : AutoCloseable {
    val mode: StorageMode

    fun encode(
        table: String,
        rowId: String,
        column: String,
        value: String,
    ): String

    fun decode(
        table: String,
        rowId: String,
        column: String,
        value: String,
    ): String

    fun recoveryKey(): String?
}

/** Only used for databases explicitly created with the PlainStorage version 1 marker. */
internal class PlaintextRecordCodec : RecordCodec {
    override val mode = StorageMode.UNENCRYPTED
    private var closed = false

    override fun encode(
        table: String,
        rowId: String,
        column: String,
        value: String,
    ): String {
        check(!closed)
        return value
    }

    override fun decode(
        table: String,
        rowId: String,
        column: String,
        value: String,
    ): String {
        check(!closed)
        return value
    }

    override fun recoveryKey(): String? = null

    override fun close() {
        closed = true
    }
}

/** No database has been created yet; the startup UI must obtain an explicit choice. */
class StorageSetupRequiredException : Exception("Choose a storage mode")
