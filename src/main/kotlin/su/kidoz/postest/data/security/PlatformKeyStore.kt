package su.kidoz.postest.data.security

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.util.Base64
import java.util.Locale

/** Uses JDK native interop directly; no subprocess arguments, environment variables or plaintext files. */
class PlatformKeyStore : NativeKeyStore {
    // Development JVMs must never claim the packaged application's keychain identity.
    private val service = "su.kidoz.postest" + if (System.getProperty("jpackage.app-path") == null) ".development" else ""

    override fun read(vaultId: String): ByteArray? = native { it.read(service, vaultId) }

    override fun write(
        vaultId: String,
        key: ByteArray,
    ) = native { it.write(service, vaultId, key) }

    private fun <T> native(action: (PlatformVault) -> T): T =
        try {
            Arena.ofConfined().use { arena ->
                val vault =
                    when {
                        System.getProperty("os.name").lowercase(Locale.ROOT).contains("mac") -> MacVault(arena)
                        System.getProperty("os.name").lowercase(Locale.ROOT).contains("win") -> WindowsVault(arena)
                        System.getProperty("os.name").lowercase(Locale.ROOT).contains("linux") -> LinuxVault(arena)
                        else -> throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
                    }
                action(vault)
            }
        } catch (error: StorageException) {
            throw error
        } catch (_: Exception) {
            throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
        } catch (_: LinkageError) {
            throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
        }
}

private interface PlatformVault {
    fun read(
        service: String,
        account: String,
    ): ByteArray?

    fun write(
        service: String,
        account: String,
        key: ByteArray,
    )
}

/** All pointers and loaded libraries are confined to one blocking IO operation. */
internal class NativeLibrary(
    name: String,
    arena: Arena,
) {
    private val lookup = SymbolLookup.libraryLookup(name, arena)
    private val linker = Linker.nativeLinker()

    fun symbol(name: String): MemorySegment = lookup.find(name).orElseThrow()

    fun call(
        name: String,
        result: MemoryLayout?,
        layouts: List<MemoryLayout>,
        vararg args: Any,
    ): Any? {
        val descriptor =
            if (result == null) {
                FunctionDescriptor.ofVoid(*layouts.toTypedArray())
            } else {
                FunctionDescriptor.of(result, *layouts.toTypedArray())
            }
        return linker.downcallHandle(symbol(name), descriptor).invokeWithArguments(args.toList())
    }

    fun callWithWindowsError(
        arena: Arena,
        name: String,
        layouts: List<MemoryLayout>,
        vararg args: Any,
    ): Pair<Int, Int> {
        val layout = Linker.Option.captureStateLayout()
        val state = arena.allocate(layout)
        val descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT, *layouts.toTypedArray())
        val handle = linker.downcallHandle(symbol(name), descriptor, Linker.Option.captureCallState("GetLastError"))
        val result = handle.invokeWithArguments(listOf(state) + args.toList()) as Int
        val error = state.get(ValueLayout.JAVA_INT, layout.byteOffset(MemoryLayout.PathElement.groupElement("GetLastError")))
        return result to error
    }
}

private val address = ValueLayout.ADDRESS
private val int32 = ValueLayout.JAVA_INT
private val int64 = ValueLayout.JAVA_LONG
private val nullPointer = MemorySegment.NULL

private fun validKey(bytes: ByteArray): ByteArray {
    if (bytes.size != RecordCipher.KEY_BYTES) {
        bytes.fill(0)
        throw StorageException(StorageProblem.INVALID_DATA)
    }
    return bytes
}

private class MacVault(
    private val arena: Arena,
) : PlatformVault {
    private val security = NativeLibrary("/System/Library/Frameworks/Security.framework/Security", arena)
    private val core = NativeLibrary("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", arena)

    override fun read(
        service: String,
        account: String,
    ): ByteArray? {
        val query = query(service, account)
        try {
            set(query, "kSecReturnData", core.symbol("kCFBooleanTrue").reinterpret(address.byteSize()).get(address, 0))
            set(query, "kSecMatchLimit", constant("kSecMatchLimitOne"))
            val out = arena.allocate(address)
            val status = security.call("SecItemCopyMatching", int32, listOf(address, address), query, out) as Int
            if (status == -25300) return null // errSecItemNotFound is the only absence case.
            checkStatus(status)
            val data = out.get(address, 0)
            try {
                val size = core.call("CFDataGetLength", int64, listOf(address), data) as Long
                if (size != RecordCipher.KEY_BYTES.toLong()) throw StorageException(StorageProblem.INVALID_DATA)
                val bytes = core.call("CFDataGetBytePtr", address, listOf(address), data) as MemorySegment
                return bytes.reinterpret(size).toArray(ValueLayout.JAVA_BYTE)
            } finally {
                release(data)
            }
        } finally {
            release(query)
        }
    }

    override fun write(
        service: String,
        account: String,
        key: ByteArray,
    ) {
        val query = query(service, account)
        val values = dictionary()
        val bytes = arena.allocateFrom(ValueLayout.JAVA_BYTE, *key)
        val data =
            try {
                core.call("CFDataCreate", address, listOf(address, address, int64), nullPointer, bytes, key.size.toLong()) as MemorySegment
            } finally {
                bytes.fill(0)
            }
        try {
            set(values, "kSecValueData", data)
            val status = security.call("SecItemUpdate", int32, listOf(address, address), query, values) as Int
            if (status == -25300) {
                set(query, "kSecValueData", data)
                checkStatus(security.call("SecItemAdd", int32, listOf(address, address), query, nullPointer) as Int)
            } else {
                checkStatus(status)
            }
        } finally {
            release(data)
            release(values)
            release(query)
        }
    }

    private fun query(
        service: String,
        account: String,
    ): MemorySegment =
        dictionary().also { query ->
            set(query, "kSecClass", constant("kSecClassGenericPassword"))
            listOf("kSecAttrService" to service, "kSecAttrAccount" to account).forEach { (attribute, value) ->
                val string =
                    core.call(
                        "CFStringCreateWithCString",
                        address,
                        listOf(address, address, int32),
                        nullPointer,
                        arena.allocateFrom(value),
                        0x08000100,
                    ) as MemorySegment
                try {
                    set(query, attribute, string)
                } finally {
                    release(string)
                }
            }
        }

    private fun dictionary(): MemorySegment =
        core.call(
            "CFDictionaryCreateMutable",
            address,
            listOf(address, int64, address, address),
            nullPointer,
            0L,
            core.symbol("kCFTypeDictionaryKeyCallBacks"),
            core.symbol("kCFTypeDictionaryValueCallBacks"),
        ) as MemorySegment

    private fun set(
        dictionary: MemorySegment,
        key: String,
        value: MemorySegment,
    ) {
        core.call("CFDictionarySetValue", null, listOf(address, address, address), dictionary, constant(key), value)
    }

    private fun constant(name: String): MemorySegment = security.symbol(name).reinterpret(address.byteSize()).get(address, 0)

    private fun release(value: MemorySegment) {
        core.call("CFRelease", null, listOf(address), value)
    }

    private fun checkStatus(status: Int) {
        if (status != 0) throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
    }
}

/** CREDENTIALW's 64-bit Windows ABI. JDK 25 desktop distributions use 64-bit runtimes. */
private class WindowsVault(
    private val arena: Arena,
) : PlatformVault {
    private val credentials = NativeLibrary("Advapi32", arena)

    init {
        check(address.byteSize() == 8L)
    }

    override fun read(
        service: String,
        account: String,
    ): ByteArray? {
        val out = arena.allocate(address)
        val (success, error) =
            credentials.callWithWindowsError(
                arena,
                "CredReadW",
                listOf(address, int32, int32, address),
                wide("$service/$account"),
                1,
                0,
                out,
            )
        if (success == 0) {
            if (error == 1168) return null // ERROR_NOT_FOUND
            throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
        }
        val pointer = out.get(address, 0)
        try {
            val credential = pointer.reinterpret(80)
            val size = credential.get(int32, 32)
            if (size != RecordCipher.KEY_BYTES) throw StorageException(StorageProblem.INVALID_DATA)
            return credential.get(address, 40).reinterpret(size.toLong()).toArray(ValueLayout.JAVA_BYTE)
        } finally {
            credentials.call("CredFree", null, listOf(address), pointer)
        }
    }

    override fun write(
        service: String,
        account: String,
        key: ByteArray,
    ) {
        val credential = arena.allocate(80, 8).fill(0)
        val blob = arena.allocateFrom(ValueLayout.JAVA_BYTE, *key)
        try {
            credential.set(int32, 4, 1) // CRED_TYPE_GENERIC
            credential.set(address, 8, wide("$service/$account"))
            credential.set(int32, 32, key.size)
            credential.set(address, 40, blob)
            credential.set(int32, 48, 2) // CRED_PERSIST_LOCAL_MACHINE: this user's sessions on this computer.
            if (credentials.call("CredWriteW", int32, listOf(address, int32), credential, 0) as Int == 0) {
                throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
            }
        } finally {
            blob.fill(0)
        }
    }

    private fun wide(value: String): MemorySegment =
        arena.allocateFrom(
            ValueLayout.JAVA_BYTE,
            *(value + '\u0000').toByteArray(Charsets.UTF_16LE),
        )
}

private class LinuxVault(
    private val arena: Arena,
) : PlatformVault {
    private val secret = NativeLibrary("libsecret-1.so.0", arena)
    private val glib = NativeLibrary("libglib-2.0.so.0", arena)

    override fun read(
        service: String,
        account: String,
    ): ByteArray? =
        withAttributes(service, account) { schema, attributes, error ->
            val password =
                secret.call(
                    "secret_password_lookupv_sync",
                    address,
                    listOf(address, address, address, address),
                    schema,
                    attributes,
                    nullPointer,
                    error,
                ) as MemorySegment
            try {
                checkError(error)
                if (password == nullPointer) {
                    null
                } else {
                    // Only the fixed-size base64 key is accepted. Do not scan arbitrary native memory.
                    val encoded = password.reinterpret(45).getString(0)
                    validKey(Base64.getDecoder().decode(encoded))
                }
            } finally {
                if (password != nullPointer) secret.call("secret_password_free", null, listOf(address), password)
            }
        }

    override fun write(
        service: String,
        account: String,
        key: ByteArray,
    ) {
        withAttributes(service, account) { schema, attributes, error ->
            val password = arena.allocateFrom(Base64.getEncoder().encodeToString(key))
            try {
                val success =
                    secret.call(
                        "secret_password_storev_sync",
                        int32,
                        listOf(address, address, address, address, address, address, address),
                        schema,
                        attributes,
                        arena.allocateFrom("default"),
                        arena.allocateFrom("Postest database encryption key"),
                        password,
                        nullPointer,
                        error,
                    ) as Int
                checkError(error)
                if (success == 0) throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
            } finally {
                password.fill(0)
            }
        }
    }

    private fun <T> withAttributes(
        service: String,
        account: String,
        action: (MemorySegment, MemorySegment, MemorySegment) -> T,
    ): T {
        // SecretSchema has one string attribute, terminated by a null name (libsecret allocates the structure).
        val schema =
            Linker
                .nativeLinker()
                .downcallHandle(
                    secret.symbol("secret_schema_new"),
                    FunctionDescriptor.of(address, address, int32, address, int32, address),
                    Linker.Option.firstVariadicArg(2),
                ).invokeWithArguments(arena.allocateFrom(service), 0, arena.allocateFrom("vault"), 0, nullPointer) as MemorySegment
        val attributes =
            glib.call(
                "g_hash_table_new_full",
                address,
                listOf(address, address, address, address),
                glib.symbol("g_str_hash"),
                glib.symbol("g_str_equal"),
                nullPointer,
                nullPointer,
            ) as MemorySegment
        val error = arena.allocate(address).fill(0)
        try {
            glib.call(
                "g_hash_table_insert",
                int32,
                listOf(address, address, address),
                attributes,
                arena.allocateFrom("vault"),
                arena.allocateFrom(account),
            )
            return action(schema, attributes, error)
        } finally {
            val failure = error.get(address, 0)
            if (failure != nullPointer) glib.call("g_error_free", null, listOf(address), failure)
            glib.call("g_hash_table_unref", null, listOf(address), attributes)
            secret.call("secret_schema_unref", null, listOf(address), schema)
        }
    }

    private fun checkError(error: MemorySegment) {
        if (error.get(address, 0) != nullPointer) throw StorageException(StorageProblem.KEY_STORE_UNAVAILABLE)
    }
}
